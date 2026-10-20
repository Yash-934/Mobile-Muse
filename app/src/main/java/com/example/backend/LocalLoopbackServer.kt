package com.example.backend

import com.example.data.local.OpenMuseRepository
import com.example.data.local.SecureCredentialStore
import com.example.proot.ProotRuntimeManager
import com.example.proot.RuntimeInstallStatus
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

@Serializable
data class LocalHealthStatus(
    val apiRunning: Boolean,
    val boundAddress: String,
    val port: Int,
    val taskWorkerReady: Boolean,
    val linuxRuntimeStatus: String,
    val executionMode: String,
    val databaseConnected: Boolean,
    val geminiConfigured: Boolean,
    val activeTaskCount: Int,
    val timestamp: Long = System.currentTimeMillis()
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
 */
class LocalLoopbackServer(
    private val repository: OpenMuseRepository,
    private val runtimeManager: ProotRuntimeManager,
    private val credentialStore: SecureCredentialStore,
    private val scope: CoroutineScope
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

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
            taskWorkerReady = true,
            linuxRuntimeStatus = rt.status.name,
            executionMode = rt.executionMode,
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
            sock.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(sock.getOutputStream()), true)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val path = parts.getOrNull(1) ?: "/"

            var authHeader: String? = null
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                if (line!!.startsWith("Authorization:", ignoreCase = true)) {
                    authHeader = line!!.substringAfter(":").trim()
                }
            }

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
                else -> {
                    sendHttpResponse(writer, 404, "Not Found", """{"error":"Unknown endpoint $path"}""")
                }
            }
        }
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
