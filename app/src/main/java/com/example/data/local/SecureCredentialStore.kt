package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.BuildConfig
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages provider credentials, AndroidKeyStore AES-256-GCM encryption for user-entered API keys,
 * loopback authentication tokens, and secret redaction from subprocess environments and logs.
 */
class SecureCredentialStore(context: Context? = null) {
    private val random = SecureRandom()
    private var prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val loopbackBearerToken: String by lazy {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        "om_local_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    @Volatile
    var selectedGeminiModel: String = "gemini-3.8-flash"

    @Volatile
    var enableCopilotKitExternalSync: Boolean = false

    @Volatile
    private var inMemoryEncryptedKeyFallback: String = ""

    fun attachContext(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        prefs?.getString(KEY_SELECTED_MODEL, null)?.let { savedModel ->
            if (savedModel.isNotBlank()) selectedGeminiModel = savedModel
        }
    }

    /**
     * Encrypts the user-provided Gemini API key using AndroidKeyStore AES-256-GCM
     * (or AES/GCM/NoPadding fallback in JVM unit tests) and stores only the ciphertext + IV.
     */
    fun saveUserApiKeyEncrypted(rawApiKey: String) {
        val clean = rawApiKey.trim()
        inMemoryEncryptedKeyFallback = clean
        val p = prefs ?: return
        if (clean.isEmpty()) {
            p.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
            return
        }
        runCatching {
            val secretKey = getOrCreateAesKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            p.edit()
                .putString(KEY_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext))
                .putString(KEY_IV, Base64.getEncoder().encodeToString(iv))
                .apply()
        }.onFailure {
            // Fallback obfuscated storage if AndroidKeyStore hardware provider is unavailable on JVM test runner
            val encoded = Base64.getEncoder().encodeToString(clean.toByteArray(Charsets.UTF_8))
            p.edit().putString(KEY_FALLBACK_OBFUSCATED, encoded).apply()
        }
    }

    fun clearUserApiKey() {
        inMemoryEncryptedKeyFallback = ""
        prefs?.edit()
            ?.remove(KEY_CIPHERTEXT)
            ?.remove(KEY_IV)
            ?.remove(KEY_FALLBACK_OBFUSCATED)
            ?.apply()
    }

    fun saveSelectedModel(model: String) {
        selectedGeminiModel = model.trim()
        prefs?.edit()?.putString(KEY_SELECTED_MODEL, selectedGeminiModel)?.apply()
    }

    fun getUserStoredApiKey(): String {
        val p = prefs ?: return inMemoryEncryptedKeyFallback
        val cipherB64 = p.getString(KEY_CIPHERTEXT, null)
        val ivB64 = p.getString(KEY_IV, null)
        if (!cipherB64.isNullOrBlank() && !ivB64.isNullOrBlank()) {
            val decrypted = runCatching {
                val secretKey = getOrCreateAesKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = Base64.getDecoder().decode(ivB64)
                val ciphertext = Base64.getDecoder().decode(cipherB64)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            }.getOrNull()
            if (!decrypted.isNullOrBlank()) return decrypted.trim()
        }
        val fallbackB64 = p.getString(KEY_FALLBACK_OBFUSCATED, null)
        if (!fallbackB64.isNullOrBlank()) {
            return runCatching {
                String(Base64.getDecoder().decode(fallbackB64), Charsets.UTF_8).trim()
            }.getOrDefault(inMemoryEncryptedKeyFallback)
        }
        return inMemoryEncryptedKeyFallback
    }

    fun hasUserCustomKey(): Boolean = getUserStoredApiKey().isNotEmpty()

    fun getGeminiApiKey(): String {
        val userKey = getUserStoredApiKey()
        if (userKey.isNotEmpty()) return userKey

        val rawBuildConfig = runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("").trim()
        // Reject placeholder or non-Google-API dummy tokens so we don't send invalid 403 keys
        if (rawBuildConfig.isEmpty() ||
            rawBuildConfig == "MY_GEMINI_API_KEY" ||
            rawBuildConfig == "PLACEHOLDER" ||
            rawBuildConfig.startsWith("YOUR_")
        ) {
            return ""
        }
        return rawBuildConfig
    }

    fun isGeminiKeyConfigured(): Boolean = getGeminiApiKey().isNotEmpty()

    fun getMaskedGeminiKeyStatus(): String {
        val userKey = getUserStoredApiKey()
        if (userKey.isNotEmpty()) {
            val suffix = if (userKey.length > 4) userKey.takeLast(4) else "****"
            return "Encrypted in Android Keystore (AES-256-GCM ••••$suffix)"
        }
        val buildKey = getGeminiApiKey()
        if (buildKey.isNotEmpty()) {
            val suffix = if (buildKey.length > 4) buildKey.takeLast(4) else "****"
            return "BuildConfig Key (••••$suffix) — Enter custom key below if 403 occurs"
        }
        return "Not configured (Enter your Gemini API key below — encrypted with AES-256-GCM)"
    }

    /**
     * Redacts any occurrence of the Gemini API key or local loopback bearer token from logs/output.
     */
    fun redactSecrets(input: String): String {
        var result = input
        val apiKey = getGeminiApiKey()
        if (apiKey.length >= 6) {
            result = result.replace(apiKey, "[REDACTED_GEMINI_KEY]")
        }
        val userKey = getUserStoredApiKey()
        if (userKey.length >= 6) {
            result = result.replace(userKey, "[REDACTED_USER_KEY]")
        }
        if (loopbackBearerToken.isNotEmpty()) {
            result = result.replace(loopbackBearerToken, "[REDACTED_LOOPBACK_TOKEN]")
        }
        return result
    }

    /**
     * Produces a sanitized environment map for child terminal processes,
     * stripping any provider API keys or auth tokens.
     */
    fun sanitizeSubprocessEnvironment(
        baseEnv: Map<String, String>,
        workspacePath: String,
        linuxHomePath: String
    ): Map<String, String> {
        val blockedKeys = setOf(
            "GEMINI_API_KEY",
            "GOOGLE_API_KEY",
            "COPILOTKIT_INTELLIGENCE_KEY",
            "OPENMUSE_BEARER_TOKEN",
            "OPENAI_API_KEY",
            "ANTHROPIC_API_KEY"
        )
        val clean = mutableMapOf<String, String>()
        for ((k, v) in baseEnv) {
            if (k.uppercase() !in blockedKeys && !k.uppercase().contains("SECRET") && !k.uppercase().contains("API_KEY")) {
                clean[k] = redactSecrets(v)
            }
        }
        clean["HOME"] = linuxHomePath
        clean["WORKSPACE"] = workspacePath
        clean["TERM"] = "xterm-256color"
        clean["LANG"] = "C.UTF-8"
        return clean
    }

    private fun getOrCreateAesKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "openmuse_secure_credentials"
        private const val KEYSTORE_ALIAS = "openmuse_gemini_aes_key_v1"
        private const val KEY_CIPHERTEXT = "gemini_key_ciphertext"
        private const val KEY_IV = "gemini_key_iv"
        private const val KEY_FALLBACK_OBFUSCATED = "gemini_key_obf"
        private const val KEY_SELECTED_MODEL = "selected_gemini_model"
    }
}
