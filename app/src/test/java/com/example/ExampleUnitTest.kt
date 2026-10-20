package com.example

import com.example.agent.GeminiModelProvider
import com.example.data.local.SecureCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `credential store encrypts and saves custom api key and strips secrets from subprocess env`() {
        val store = SecureCredentialStore()
        assertTrue(store.loopbackBearerToken.startsWith("om_local_"))

        store.saveUserApiKeyEncrypted("AIzaCustomTestKey9876")
        assertTrue(store.hasUserCustomKey())
        assertEquals("AIzaCustomTestKey9876", store.getGeminiApiKey())
        assertTrue(store.getMaskedGeminiKeyStatus().contains("••••9876"))

        val dirtyEnv = mapOf(
            "PATH" to "/system/bin:/usr/bin",
            "GEMINI_API_KEY" to "AIzaSecretKey123456",
            "COPILOTKIT_INTELLIGENCE_KEY" to "ck_secret_999",
            "CUSTOM_SECRET_TOKEN" to "top_secret"
        )
        val sanitized = store.sanitizeSubprocessEnvironment(
            baseEnv = dirtyEnv,
            workspacePath = "/data/user/0/com.aistudio.openmuse.xkpqzr/files/openmuse_workspace",
            linuxHomePath = "/data/user/0/com.aistudio.openmuse.xkpqzr/files/linux_rootfs/root"
        )
        assertFalse(sanitized.containsKey("GEMINI_API_KEY"))
        assertFalse(sanitized.containsKey("COPILOTKIT_INTELLIGENCE_KEY"))
        assertFalse(sanitized.containsKey("CUSTOM_SECRET_TOKEN"))
        assertEquals("/system/bin:/usr/bin", sanitized["PATH"])
        assertEquals("xterm-256color", sanitized["TERM"])

        store.clearUserApiKey()
        assertFalse(store.hasUserCustomKey())
    }

    @Test
    fun `gemini provider deterministic local plan and model support work accurately`() {
        val store = SecureCredentialStore()
        val provider = GeminiModelProvider(store)

        assertTrue(GeminiModelProvider.SUPPORTED_MODELS.contains("gemini-3.8-flash"))
        assertTrue(GeminiModelProvider.SUPPORTED_MODELS.contains("gemini-3.5-flash"))

        val shellPlan = provider.buildDeterministicLocalPlanIfApplicable("run: uname -a")
        assertEquals(1, shellPlan.size)
        assertEquals("run_command", shellPlan.first().toolName)
        assertEquals("uname -a", shellPlan.first().argument)

        val gitPlan = provider.buildDeterministicLocalPlanIfApplicable("git: status")
        assertEquals(1, gitPlan.size)
        assertEquals("git_op", gitPlan.first().toolName)

        val mapped = provider.mapApiException(IllegalArgumentException("Network unreachable"))
        assertTrue(mapped.message!!.contains("Gemini Provider Error"))
    }
}
