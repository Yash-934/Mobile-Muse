package com.example.backend

import com.example.data.local.CommandReceiptEntity
import com.example.data.local.OpenMuseRepository
import com.example.data.local.SecureCredentialStore
import com.example.proot.AndroidProotComputerProvider
import com.example.proot.ProotRuntimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

@Serializable
data class LocalHealthStatus(
    val apiRunning: Boolean,
    val boundAddress: String,
    val port: Int,
    val taskWorkerReady: Boolean,
    val linuxRuntimeStatus: String,
    val executionMode: String,
    val pocShellVerified: Boolean = false,
    val sha256Verified: Boolean = false,
    val databaseConnected: Boolean,
    val geminiConfigured: Boolean,
    val activeTaskCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class LoopbackFileDto(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val fileType: String
)

@Serializable
data class LoopbackFilesResponse(
    val directory: String,
    val files: List<LoopbackFileDto>,
    val readPath: String? = null,
    val readContent: String? = null
)

@Serializable
data class LoopbackTerminalResponse(
    val executionMode: String,
    val linuxRuntimeStatus: String,
    val executedCommand: String? = null,
    val stdout: String? = null,
    val stderr: String? = null,
    val exitCode: Int? = null,
    val durationMs: Long? = null,
    val recentReceipts: List<CommandReceiptEntity> = emptyList()
)

data class LoopbackServerUiState(
    val isRunning: Boolean = false,
    val host: String = "127.0.0.1",
    val port: Int = 18765,
    val authProtected: Boolean = true,
    val requestsServed: Int = 0,
    val lastError: String? = null,
    val serverLogs: List<String> = emptyList()
)

/**
 * Local OpenMuse Backend & Health Server bound strictly to loopback (127.0.0.1).
 * Never exposes terminal or task APIs to external network interfaces.
 * Requires Bearer token authentication for all app-to-server calls.
 * Supports /health, /tasks, /receipts, /terminal, and /files endpoints.
 */
class LocalLoopbackServer(
    private val repository: OpenMuseRepository,
    private val runtimeManager: ProotRuntimeManager,
    private val credentialStore: SecureCredentialStore,
    private val scope: CoroutineScope,
    private val computerProvider: AndroidProotComputerProvider? = null
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; explicitNulls = false }

    private val _uiState = MutableStateFlow(LoopbackServerUiState())
    val uiState: StateFlow<LoopbackServerUiState> = _uiState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun start(preferredPort: Int = 18765) {
        if (acceptJob?.isActive == true && serverSocket?.isClosed == false) return
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val loopbackAddr = InetAddress.getByName("127.0.0.1")
                val socket = runCatching {
                    ServerSocket(preferredPort, 50, loopbackAddr)
                }.getOrElse {
                    ServerSocket(0, 50, loopbackAddr)
                }
                serverSocket = socket
                val actualPort = socket.localPort
                log("Loopback API bound exclusively to 127.0.0.1:$actualPort (Bearer Auth enabled)")
                _uiState.update {
                    it.copy(
                        isRunning = true,
                        host = "127.0.0.1",
                        port = actualPort,
                        lastError = null
                    )
                }

                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                val err = "Loopback server startup error: ${e.message}"
                log("ERROR: $err")
                _uiState.update { it.copy(isRunning = false, lastError = err) }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        acceptJob?.cancel()
        serverSocket = null
        log("Loopback API server stopped cleanly.")
        _uiState.update { it.copy(isRunning = false) }
    }

    fun buildHealthSnapshot(activeTasks: Int = 0): LocalHealthStatus {
        val rt = runtimeManager.state.value
        return LocalHealthStatus(
            apiRunning = _uiState.value.isRunning,
            boundAddress = _uiState.value.host,
            port = _uiState.value.port,
            taskWorkerReady = rt.pocShellVerified,
            linuxRuntimeStatus = rt.status.name,
            executionMode = rt.executionMode,
            pocShellVerified = rt.pocShellVerified,
            sha256Verified = rt.sha256Verified,
            databaseConnected = true,
            geminiConfigured = credentialStore.isGeminiKeyConfigured(),
            activeTaskCount = activeTasks
        )
    }

    /**
     * Verifies a bearer token against the local loopback server credential.
     */
    fun verifyAuthHeader(authHeader: String?): Boolean {
        if (authHeader.isNullOrBlank()) return false
        val token = authHeader.removePrefix("Bearer ").trim()
        return token == credentialStore.loopbackBearerToken
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(sock.getOutputStream()), true)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val fullUri = parts.getOrNull(1) ?: "/"
            val path = fullUri.substringBefore("?")
            val queryParams = parseQueryParams(fullUri.substringAfter("?", ""))

            var authHeader: String? = null
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                if (line!!.startsWith("Authorization:", ignoreCase = true)) {
                    authHeader = line!!.substringAfter(":").trim()
                } else if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val requestBody = if (contentLength in 1..65536) {
                val buf = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val r = reader.read(buf, totalRead, contentLength - totalRead)
                    if (r == -1) break
                    totalRead += r
                }
                String(buf, 0, totalRead)
            } else ""

            if (!verifyAuthHeader(authHeader)) {
                log("Rejected unauthorized request to $method $path (401)")
                sendHttpResponse(
                    writer,
                    statusCode = 401,
                    statusText = "Unauthorized",
                    body = """{"error":"Missing or invalid local loopback Bearer token"}"""
                )
                return
            }

            _uiState.update { it.copy(requestsServed = it.requestsServed + 1) }

            when {
                path == "/health" -> {
                    val tasks = runBlocking { repository.getAllTasksSnapshot() }
                    val activeCount = tasks.count { it.status == "RUNNING" || it.status == "PLANNING" }
                    val health = buildHealthSnapshot(activeCount)
                    sendHttpResponse(writer, 200, "OK", json.encodeToString(health))
                }
                path == "/tasks" -> {
                    val tasks = runBlocking { repository.getAllTasksSnapshot() }
                    sendHttpResponse(writer, 200, "OK", json.encodeToString(tasks))
                }
                path == "/receipts" -> {
                    val receipts = runBlocking { repository.getRecentReceiptsSnapshot() }
                    sendHttpResponse(writer, 200, "OK", json.encodeToString(receipts))
                }
                path == "/terminal" -> {
                    val cmdFromQuery = queryParams["cmd"]
                    val cmdFromBody = if (requestBody.isNotBlank()) {
                        runCatching {
                            json.parseToJsonElement(requestBody).jsonObject["command"]?.jsonPrimitive?.content
                        }.getOrNull()
                    } else null
                    val cmdToRun = cmdFromBody ?: cmdFromQuery

                    val rt = runtimeManager.state.value
                    if (!cmdToRun.isNullOrBlank() && computerProvider != null) {
                        val execRes = runBlocking {
                            computerProvider.executeShellLine(cmdToRun, confirmedByUser = false)
                        }
                        val receipts = runBlocking { repository.getRecentReceiptsSnapshot() }
                        val resp = LoopbackTerminalResponse(
                            executionMode = execRes.executionMode,
                            linuxRuntimeStatus = rt.status.name,
                            executedCommand = execRes.commandDisplay,
                            stdout = execRes.stdout,
                            stderr = execRes.stderr,
                            exitCode = execRes.exitCode,
                            durationMs = execRes.durationMs,
                            recentReceipts = receipts.take(15)
                        )
                        sendHttpResponse(writer, 200, "OK", json.encodeToString(resp))
                    } else {
                        val receipts = runBlocking { repository.getRecentReceiptsSnapshot() }
                        val resp = LoopbackTerminalResponse(
                            executionMode = rt.executionMode,
                            linuxRuntimeStatus = rt.status.name,
                            recentReceipts = receipts.take(20)
                        )
                        sendHttpResponse(writer, 200, "OK", json.encodeToString(resp))
                    }
                }
                path == "/files" -> {
                    val subdir = queryParams["dir"] ?: ""
                    val readTarget = queryParams["read"]
                    if (method == "POST" && requestBody.isNotBlank()) {
                        val parsed = runCatching { json.parseToJsonElement(requestBody).jsonObject }.getOrNull()
                        val writePath = parsed?.get("path")?.jsonPrimitive?.content ?: ""
                        val writeContent = parsed?.get("content")?.jsonPrimitive?.content ?: ""
                        if (writePath.isNotBlank()) {
                            val res = writeSafeWorkspaceFile(writePath, writeContent)
                            if (res.isSuccess) {
                                sendHttpResponse(writer, 200, "OK", """{"status":"written","path":"$writePath"}""")
                            } else {
                                sendHttpResponse(writer, 400, "Bad Request", """{"error":"${res.exceptionOrNull()?.message}"}""")
                            }
                            return
                        }
                    }

                    val listed = listSafeWorkspaceFiles(subdir)
                    val readContent = if (!readTarget.isNullOrBlank()) {
                        readSafeWorkspaceFile(readTarget).getOrNull()
                    } else null

                    val resp = LoopbackFilesResponse(
                        directory = subdir.ifBlank { "/" },
                        files = listed,
                        readPath = readTarget,
                        readContent = readContent
                    )
                    sendHttpResponse(writer, 200, "OK", json.encodeToString(resp))
                }
                else -> {
                    sendHttpResponse(writer, 404, "Not Found", """{"error":"Unknown endpoint $path"}""")
                }
            }
        }
    }

    private fun listSafeWorkspaceFiles(subdir: String): List<LoopbackFileDto> {
        val base = runtimeManager.workspaceDir.canonicalFile
        if (!base.exists()) base.mkdirs()
        val target = if (subdir.isBlank()) base else File(base, subdir.removePrefix("/")).canonicalFile
        if (!target.path.startsWith(base.path) || !target.exists() || !target.isDirectory) {
            return emptyList()
        }
        return (target.listFiles() ?: emptyArray())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { f ->
                val rel = f.canonicalPath.removePrefix(base.path).removePrefix("/")
                val ext = if (f.isDirectory) "FOLDER" else f.extension.uppercase().ifBlank { "FILE" }
                LoopbackFileDto(
                    name = f.name,
                    relativePath = rel,
                    isDirectory = f.isDirectory,
                    sizeBytes = if (f.isFile) f.length() else 0L,
                    lastModified = f.lastModified(),
                    fileType = ext
                )
            }
    }

    private fun readSafeWorkspaceFile(relPath: String): Result<String> = runCatching {
        val base = runtimeManager.workspaceDir.canonicalFile
        val f = File(base, relPath.removePrefix("/")).canonicalFile
        require(f.path.startsWith(base.path)) { "Path escapes workspace root" }
        require(f.exists() && f.isFile) { "File not found: $relPath" }
        f.readText()
    }

    private fun writeSafeWorkspaceFile(relPath: String, content: String): Result<File> = runCatching {
        val base = runtimeManager.workspaceDir.canonicalFile
        val f = File(base, relPath.removePrefix("/")).canonicalFile
        require(f.path.startsWith(base.path)) { "Path escapes workspace root" }
        f.parentFile?.mkdirs()
        f.writeText(content)
        f
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                k to v
            } else null
        }.toMap()
    }

    private fun sendHttpResponse(
        writer: PrintWriter,
        statusCode: Int,
        statusText: String,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Cache-Control: no-store\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(body)
        writer.flush()
    }

    private fun log(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        _uiState.update { state ->
            state.copy(serverLogs = (state.serverLogs + "[$ts] $msg").takeLast(80))
        }
    }
}
