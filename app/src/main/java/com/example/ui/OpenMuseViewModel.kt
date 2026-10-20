package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.AgentForegroundService
import com.example.agent.AutonomousTaskEngine
import com.example.agent.GeminiModelProvider
import com.example.backend.LocalHealthStatus
import com.example.backend.LocalLoopbackServer
import com.example.backend.LoopbackServerUiState
import com.example.browser.BrowserUiState
import com.example.browser.TwoLayerBrowserController
import com.example.data.local.AutonomousTaskEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserTabEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CommandReceiptEntity
import com.example.data.local.ConversationThreadEntity
import com.example.data.local.OpenMuseDatabase
import com.example.data.local.OpenMuseRepository
import com.example.data.local.ProjectWorkspaceEntity
import com.example.data.local.SecureCredentialStore
import com.example.data.local.TaskStepEntity
import com.example.proot.AndroidProotComputerProvider
import com.example.proot.CommandExecutionResult
import com.example.proot.ProotRuntimeManager
import com.example.proot.ProotRuntimeState
import com.example.proot.WorkspaceFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

enum class OpenMuseDestination {
    CHAT,
    TASKS,
    BROWSER,
    WORKSPACE,
    LINUX_ENV,
    SETTINGS
}

data class PendingShellConfirmation(
    val rawCommand: String,
    val workingSubdir: String,
    val reason: String
)

data class RuntimeMetricsSnapshot(
    val jvmUsedMemoryMb: Long = 0L,
    val jvmMaxMemoryMb: Long = 0L,
    val lastLoopbackPingResult: String = "Not tested yet"
)

@OptIn(ExperimentalCoroutinesApi::class)
class OpenMuseViewModel(application: Application) : AndroidViewModel(application) {

    val credentialStore = SecureCredentialStore(application).apply {
        attachContext(application)
    }
    private val database = OpenMuseDatabase.getInstance(application)

    val runtimeManager = ProotRuntimeManager(
        appFilesDir = application.filesDir,
        nativeLibDir = application.applicationInfo.nativeLibraryDir?.let { java.io.File(it) },
        scope = viewModelScope
    )

    val repository = OpenMuseRepository(
        dao = database.dao(),
        defaultWorkspaceDir = runtimeManager.workspaceDir
    )

    val computerProvider = AndroidProotComputerProvider(
        runtimeManager = runtimeManager,
        repository = repository,
        credentialStore = credentialStore
    )

    val loopbackServer = LocalLoopbackServer(
        repository = repository,
        runtimeManager = runtimeManager,
        credentialStore = credentialStore,
        scope = viewModelScope,
        computerProvider = computerProvider
    )

    val browserController = TwoLayerBrowserController(
        repository = repository,
        computerProvider = computerProvider
    )

    val geminiProvider = GeminiModelProvider(credentialStore = credentialStore)

    val taskEngine = AutonomousTaskEngine(
        repository = repository,
        computerProvider = computerProvider,
        browserController = browserController,
        geminiProvider = geminiProvider,
        scope = viewModelScope
    )

    // Light Theme by default (false = Light mode, true = Dark mode)
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _geminiKeyMaskedStatus = MutableStateFlow(credentialStore.getMaskedGeminiKeyStatus())
    val geminiKeyMaskedStatus: StateFlow<String> = _geminiKeyMaskedStatus.asStateFlow()

    private val _currentDestination = MutableStateFlow(OpenMuseDestination.CHAT)
    val currentDestination: StateFlow<OpenMuseDestination> = _currentDestination.asStateFlow()

    private val _selectedThreadId = MutableStateFlow(1L)
    val selectedThreadId: StateFlow<Long> = _selectedThreadId.asStateFlow()

    private val _showArchivedThreads = MutableStateFlow(false)
    val showArchivedThreads: StateFlow<Boolean> = _showArchivedThreads.asStateFlow()

    private val _workspaceFiles = MutableStateFlow<List<WorkspaceFileItem>>(emptyList())
    val workspaceFiles: StateFlow<List<WorkspaceFileItem>> = _workspaceFiles.asStateFlow()

    private val _currentFolderSubdir = MutableStateFlow("")
    val currentFolderSubdir: StateFlow<String> = _currentFolderSubdir.asStateFlow()

    private val _lastOpenedMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastOpenedMap: StateFlow<Map<String, Long>> = _lastOpenedMap.asStateFlow()

    private val _selectedFilePath = MutableStateFlow("README.md")
    val selectedFilePath: StateFlow<String> = _selectedFilePath.asStateFlow()

    private val _selectedFileContent = MutableStateFlow("")
    val selectedFileContent: StateFlow<String> = _selectedFileContent.asStateFlow()

    private val _terminalWorkingSubdir = MutableStateFlow("")
    val terminalWorkingSubdir: StateFlow<String> = _terminalWorkingSubdir.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingShellConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingShellConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _lastGitOutput = MutableStateFlow("Run a Git operation below to inspect repository state.")
    val lastGitOutput: StateFlow<String> = _lastGitOutput.asStateFlow()

    private val _selectedModel = MutableStateFlow(credentialStore.selectedGeminiModel)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _externalSyncEnabled = MutableStateFlow(credentialStore.enableCopilotKitExternalSync)
    val externalSyncEnabled: StateFlow<Boolean> = _externalSyncEnabled.asStateFlow()

    private val _runtimeMetrics = MutableStateFlow(RuntimeMetricsSnapshot())
    val runtimeMetrics: StateFlow<RuntimeMetricsSnapshot> = _runtimeMetrics.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    val prootState: StateFlow<ProotRuntimeState> = runtimeManager.state
    val serverState: StateFlow<LoopbackServerUiState> = loopbackServer.uiState
    val browserState: StateFlow<BrowserUiState> = browserController.uiState

    val workspaces: StateFlow<List<ProjectWorkspaceEntity>> = repository.workspaces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val threads: StateFlow<List<ConversationThreadEntity>> = repository.threads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentMessages: StateFlow<List<ChatMessageEntity>> = _selectedThreadId
        .flatMapLatest { id -> repository.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<AutonomousTaskEntity>> = repository.tasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTaskSteps: StateFlow<List<TaskStepEntity>> = repository.allTaskSteps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val commandReceipts: StateFlow<List<CommandReceiptEntity>> = repository.commandReceipts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browserTabs: StateFlow<List<BrowserTabEntity>> = repository.browserTabs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarksAndHistory: StateFlow<List<BookmarkEntity>> = repository.bookmarksAndHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
            recoverInterruptedTasksFromLastSession()
            val currentThreads = database.dao().getThreadById(1L)
            if (currentThreads == null) {
                val id = repository.createThread(title = "Main Agent Session", isSideThread = false)
                _selectedThreadId.value = id
            }
            runtimeManager.startRuntime()
            loopbackServer.start(18765)
            refreshWorkspaceFiles()
            openWorkspaceFile("README.md")
            refreshMemoryMetrics()
            _geminiKeyMaskedStatus.value = credentialStore.getMaskedGeminiKeyStatus()
        }
        viewModelScope.launch {
            taskEngine.runningTaskIds.collect { runningIds ->
                if (runningIds.isNotEmpty()) {
                    AgentForegroundService.startOrUpdate(
                        getApplication(),
                        "Executing ${runningIds.size} autonomous task(s)..."
                    )
                } else {
                    AgentForegroundService.stop(getApplication())
                }
            }
        }
    }

    private suspend fun recoverInterruptedTasksFromLastSession() {
        val existingTasks = repository.getAllTasksSnapshot()
        for (task in existingTasks) {
            if (task.status == "RUNNING" || task.status == "PLANNING") {
                repository.updateTask(
                    task.copy(
                        status = "PAUSED",
                        errorMessage = "Recovered after Android process restart. Tap Resume or Retry to continue from step ${task.currentStepIndex + 1}."
                    )
                )
            }
        }
    }

    fun setDarkTheme(dark: Boolean) {
        _isDarkTheme.value = dark
    }

    fun saveEncryptedGeminiApiKey(rawKey: String) {
        val clean = rawKey.trim()
        if (clean.isEmpty()) return
        credentialStore.saveUserApiKeyEncrypted(clean)
        _geminiKeyMaskedStatus.value = credentialStore.getMaskedGeminiKeyStatus()
        _statusBannerMessage.value = "Gemini API Key encrypted (AES-256-GCM) and saved securely."
    }

    fun clearEncryptedGeminiApiKey() {
        credentialStore.clearUserApiKey()
        _geminiKeyMaskedStatus.value = credentialStore.getMaskedGeminiKeyStatus()
        _statusBannerMessage.value = "Cleared custom encrypted Gemini API Key."
    }

    fun navigateTo(destination: OpenMuseDestination) {
        _currentDestination.value = destination
        if (destination == OpenMuseDestination.WORKSPACE) {
            refreshWorkspaceFiles()
        } else if (destination == OpenMuseDestination.LINUX_ENV || destination == OpenMuseDestination.SETTINGS) {
            runtimeManager.refreshStorageAndState()
            refreshMemoryMetrics()
            _geminiKeyMaskedStatus.value = credentialStore.getMaskedGeminiKeyStatus()
        }
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    // --- Thread & Chat Actions ---

    fun selectThread(threadId: Long) {
        _selectedThreadId.value = threadId
    }

    fun createNewThread(title: String, isSideThread: Boolean = false) {
        viewModelScope.launch {
            val parentId = if (isSideThread) _selectedThreadId.value else null
            val newId = repository.createThread(
                title = title.ifBlank { if (isSideThread) "Side Thread" else "Agent Conversation" },
                isSideThread = isSideThread,
                parentThreadId = parentId
            )
            _selectedThreadId.value = newId
        }
    }

    fun renameCurrentThread(newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.renameThread(_selectedThreadId.value, newTitle)
        }
    }

    fun toggleArchiveThread(threadId: Long) {
        viewModelScope.launch {
            repository.toggleArchiveThread(threadId)
        }
    }

    fun setShowArchivedThreads(show: Boolean) {
        _showArchivedThreads.value = show
    }

    fun assignThreadGroup(threadId: Long, groupTag: String) {
        viewModelScope.launch {
            val existing = threads.value.firstOrNull { it.id == threadId } ?: return@launch
            val strippedTitle = existing.title.replace(Regex("^\\[(Study|Coding|Work|Saved)]\\s*"), "").trim()
            val updatedTitle = if (groupTag.isBlank() || groupTag.equals("All", ignoreCase = true) || groupTag.equals("General", ignoreCase = true)) {
                strippedTitle
            } else {
                "[$groupTag] $strippedTitle"
            }
            repository.renameThread(threadId, updatedTitle)
            _statusBannerMessage.value = "Moved conversation to group '${groupTag.ifBlank { "General" }}'"
        }
    }

    fun copyTextToClipboard(context: Context, label: String, text: String) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
            _statusBannerMessage.value = "Copied $label to clipboard."
        }
    }

    fun shareTextOrFile(context: Context, title: String, content: String) {
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Share $title").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }.onFailure { err ->
            _statusBannerMessage.value = "Share failed: ${err.message}"
        }
    }

    fun downloadSnippetToWorkspaceAndDevice(context: Context, fileName: String, content: String) {
        val cleanName = fileName.trim().replace("/", "_").ifBlank { "muse_export_${System.currentTimeMillis() % 10000}.txt" }
        saveWorkspaceFile(cleanName, content)
        downloadWorkspaceFileToDevice(context, cleanName)
    }

    fun submitUserPrompt(promptText: String) {
        val clean = promptText.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val threadId = _selectedThreadId.value
            repository.addMessage(
                threadId = threadId,
                role = "USER",
                content = clean,
                cardType = "TEXT"
            )
            taskEngine.startNewTask(threadId = threadId, goal = clean)
        }
    }

    // --- Task Engine Controls ---

    fun pauseTask(taskId: Long) {
        viewModelScope.launch { taskEngine.pauseTask(taskId) }
    }

    fun resumeTask(taskId: Long) {
        viewModelScope.launch { taskEngine.resumeTask(taskId) }
    }

    fun cancelTask(taskId: Long) {
        viewModelScope.launch { taskEngine.cancelTask(taskId) }
    }

    fun retryTask(taskId: Long) {
        viewModelScope.launch { taskEngine.retryTask(taskId) }
    }

    fun approveTaskCheckpoint(taskId: Long, stepIndex: Int) {
        viewModelScope.launch { taskEngine.approveTaskCheckpoint(taskId, stepIndex) }
    }

    fun rejectTaskCheckpoint(taskId: Long, stepIndex: Int) {
        viewModelScope.launch { taskEngine.rejectTaskCheckpoint(taskId, stepIndex) }
    }

    // --- Terminal & Workspace Actions ---

    fun setTerminalWorkingSubdir(subdir: String) {
        _terminalWorkingSubdir.value = subdir.trim()
    }

    fun runTerminalCommand(rawCommand: String, confirmedByUser: Boolean = false) {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return
        viewModelScope.launch {
            val res = computerProvider.executeShellLine(
                rawCommandLine = cmd,
                workingSubdir = _terminalWorkingSubdir.value,
                confirmedByUser = confirmedByUser
            )
            if (res.requiresConfirmation && !confirmedByUser) {
                _pendingConfirmation.value = PendingShellConfirmation(
                    rawCommand = cmd,
                    workingSubdir = _terminalWorkingSubdir.value,
                    reason = res.confirmationReason ?: "Consequential command requires confirmation."
                )
            } else {
                _pendingConfirmation.value = null
                refreshWorkspaceFiles()
            }
        }
    }

    fun confirmPendingTerminalCommand() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        runTerminalCommand(pending.rawCommand, confirmedByUser = true)
    }

    fun dismissPendingTerminalCommand() {
        _pendingConfirmation.value = null
    }

    fun clearTerminalReceipts() {
        viewModelScope.launch { repository.clearCommandReceipts() }
    }

    fun refreshWorkspaceFiles(subdir: String = _currentFolderSubdir.value) {
        _currentFolderSubdir.value = subdir
        _workspaceFiles.value = computerProvider.listWorkspaceFiles(subdir)
    }

    fun navigateToWorkspaceFolder(subdir: String) {
        val clean = subdir.trim().removePrefix("/").removeSuffix("/")
        _currentFolderSubdir.value = clean
        _workspaceFiles.value = computerProvider.listWorkspaceFiles(clean)
    }

    fun openWorkspaceFile(relativePath: String) {
        _selectedFilePath.value = relativePath
        _lastOpenedMap.update { it + (relativePath to System.currentTimeMillis()) }
        computerProvider.readWorkspaceFile(relativePath).onSuccess { content ->
            _selectedFileContent.value = content
        }.onFailure { err ->
            _selectedFileContent.value = "# Unable to read $relativePath: ${err.message}"
        }
    }

    fun saveWorkspaceFile(relativePath: String, content: String) {
        val cleanPath = relativePath.trim().ifBlank { "untitled.txt" }
        computerProvider.writeWorkspaceFile(cleanPath, content).onSuccess {
            _selectedFilePath.value = cleanPath
            _selectedFileContent.value = content
            _lastOpenedMap.update { map -> map + (cleanPath to System.currentTimeMillis()) }
            refreshWorkspaceFiles(_currentFolderSubdir.value)
            _statusBannerMessage.value = "Saved '$cleanPath' (${it.length()} bytes) in workspace."
        }.onFailure { err ->
            _statusBannerMessage.value = "Failed to save '$cleanPath': ${err.message}"
        }
    }

    fun createWorkspaceFolder(folderName: String) {
        val cleanName = folderName.trim().replace("/", "")
        if (cleanName.isBlank()) return
        val rel = if (_currentFolderSubdir.value.isBlank()) cleanName else "${_currentFolderSubdir.value}/$cleanName"
        computerProvider.createWorkspaceFolder(rel).onSuccess {
            refreshWorkspaceFiles(_currentFolderSubdir.value)
            _statusBannerMessage.value = "Created folder '$rel'"
        }.onFailure { err ->
            _statusBannerMessage.value = "Failed to create folder: ${err.message}"
        }
    }

    fun renameWorkspaceItem(oldRelativePath: String, newName: String) {
        computerProvider.renameWorkspaceItem(oldRelativePath, newName).onSuccess {
            refreshWorkspaceFiles(_currentFolderSubdir.value)
            _statusBannerMessage.value = "Renamed '$oldRelativePath' to '${it.name}'"
        }.onFailure { err ->
            _statusBannerMessage.value = "Rename failed: ${err.message}"
        }
    }

    fun deleteWorkspaceFile(relativePath: String) {
        computerProvider.deleteWorkspaceFile(relativePath).onSuccess {
            refreshWorkspaceFiles(_currentFolderSubdir.value)
            _statusBannerMessage.value = "Deleted '$relativePath' from workspace."
        }
    }

    fun importFileFromSaf(context: Context, uri: Uri, targetName: String) {
        viewModelScope.launch {
            computerProvider.importExternalFileViaSaf(context, uri, targetName).onSuccess { file ->
                refreshWorkspaceFiles()
                openWorkspaceFile(file.name)
                _statusBannerMessage.value = "Imported '${file.name}' (${file.length()} bytes) via SAF."
            }.onFailure { err ->
                _statusBannerMessage.value = "SAF import failed: ${err.message}"
            }
        }
    }

    fun exportFileToSaf(context: Context, relativePath: String, targetUri: Uri) {
        viewModelScope.launch {
            computerProvider.exportWorkspaceFileViaSaf(context, relativePath, targetUri).onSuccess { bytes ->
                _statusBannerMessage.value = "Exported '$relativePath' ($bytes bytes) via SAF."
            }.onFailure { err ->
                _statusBannerMessage.value = "SAF export failed: ${err.message}"
            }
        }
    }

    fun shareWorkspaceFile(context: Context, relativePath: String) {
        computerProvider.readWorkspaceFile(relativePath).onSuccess { content ->
            shareTextOrFile(context, relativePath.substringAfterLast("/"), content)
        }.onFailure { err ->
            _statusBannerMessage.value = "Unable to share '$relativePath': ${err.message}"
        }
    }

    fun downloadWorkspaceFileToDevice(context: Context, relativePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            computerProvider.readWorkspaceFile(relativePath).onSuccess { content ->
                val fileName = relativePath.substringAfterLast("/").ifBlank { "workspace_file.txt" }
                val bytes = content.toByteArray(Charsets.UTF_8)
                val savedLocation = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val resolver = context.contentResolver
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, guessMimeType(fileName))
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OpenMuse")
                        }
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
                            "Downloads/OpenMuse/$fileName"
                        } else null
                    } else null
                }.getOrNull() ?: run {
                    val fallbackDir = java.io.File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                        "OpenMuse_Downloads"
                    )
                    fallbackDir.mkdirs()
                    val outFile = java.io.File(fallbackDir, fileName)
                    outFile.writeBytes(bytes)
                    outFile.absolutePath
                }
                _statusBannerMessage.value = "Saved '$fileName' (${bytes.size} bytes) to Device: $savedLocation"
            }.onFailure { err ->
                _statusBannerMessage.value = "Download failed for '$relativePath': ${err.message}"
            }
        }
    }

    private fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "md", "markdown" -> "text/markdown"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "py" -> "text/x-python"
            "sh" -> "application/x-sh"
            "js" -> "application/javascript"
            else -> "text/plain"
        }
    }

    fun runGitCommand(operation: String, extraArg: String = "", confirmedByUser: Boolean = false) {
        viewModelScope.launch {
            val args = if (extraArg.isBlank()) emptyList() else listOf(extraArg)
            val res: CommandExecutionResult = computerProvider.executeGitOperation(
                operation = operation,
                args = args,
                confirmedByUser = confirmedByUser
            )
            _lastGitOutput.value = buildString {
                appendLine("$ ${res.commandDisplay}")
                if (res.stdout.isNotBlank()) appendLine(res.stdout.trim())
                if (res.stderr.isNotBlank()) appendLine(res.stderr.trim())
                append("[exit=${res.exitCode}, ${res.durationMs}ms]")
            }
            refreshWorkspaceFiles()
        }
    }

    // --- Browser Actions ---

    fun navigateBrowserTo(rawInput: String) {
        val url = browserController.normalizeUrl(rawInput)
        browserController.onPageStarted(url)
    }

    fun inspectCurrentBrowserPageAutonomously(urlInput: String) {
        viewModelScope.launch {
            browserController.inspectUrlAutonomously(urlInput)
        }
    }

    fun runPlaywrightChromiumProbe() {
        viewModelScope.launch {
            browserController.runChromiumPlaywrightProbe()
        }
    }

    fun addNewBrowserTab(url: String = "https://developer.android.com") {
        viewModelScope.launch {
            val norm = browserController.normalizeUrl(url)
            repository.addBrowserTab(title = "New Tab", url = norm)
            browserController.onPageStarted(norm)
        }
    }

    fun closeBrowserTab(tabId: Long) {
        viewModelScope.launch {
            repository.closeBrowserTab(tabId)
        }
    }

    fun bookmarkCurrentPage() {
        val b = browserState.value
        viewModelScope.launch {
            repository.recordBookmarkOrHistory(title = b.pageTitle, url = b.currentUrl, isHistory = false)
            _statusBannerMessage.value = "Bookmarked ${b.currentUrl}"
        }
    }

    // --- Linux Runtime & Settings Actions ---

    fun installLinuxUserspace(downloadArchive: Boolean) {
        runtimeManager.installUserspace(downloadRemoteArchive = downloadArchive)
    }

    fun cancelLinuxDownload() {
        runtimeManager.cancelDownload()
    }

    fun startLinuxRuntime() {
        viewModelScope.launch { runtimeManager.startRuntime() }
    }

    fun stopLinuxRuntime() {
        runtimeManager.stopRuntime()
    }

    fun restartLinuxRuntime() {
        viewModelScope.launch { runtimeManager.restartRuntime() }
    }

    fun repairLinuxRuntime() {
        viewModelScope.launch { runtimeManager.repairRuntime() }
    }

    fun uninstallLinuxRuntime() {
        viewModelScope.launch { runtimeManager.uninstallRuntime() }
    }

    fun selectGeminiModel(model: String) {
        val clean = model.trim()
        if (clean.isEmpty()) return
        credentialStore.saveSelectedModel(clean)
        _selectedModel.value = clean
        _statusBannerMessage.value = "Active Gemini model set to '$clean'"
    }

    fun setExternalCopilotSync(enabled: Boolean) {
        credentialStore.enableCopilotKitExternalSync = enabled
        _externalSyncEnabled.value = enabled
    }

    fun refreshMemoryMetrics() {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val maxMb = rt.maxMemory() / (1024 * 1024)
        _runtimeMetrics.update { it.copy(jvmUsedMemoryMb = usedMb, jvmMaxMemoryMb = maxMb) }
    }

    fun verifyLoopbackHealthEndpoint() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val port = serverState.value.port
                val client = OkHttpClient()
                val req = Request.Builder()
                    .url("http://127.0.0.1:$port/health")
                    .header("Authorization", "Bearer ${credentialStore.loopbackBearerToken}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    _runtimeMetrics.update {
                        it.copy(lastLoopbackPingResult = "HTTP ${resp.code} OK — ${body.replace("\n", " ").take(180)}")
                    }
                }
            } catch (e: Exception) {
                _runtimeMetrics.update {
                    it.copy(lastLoopbackPingResult = "Loopback ping failed: ${e.message}")
                }
            }
        }
    }

    fun getHealthSnapshot(): LocalHealthStatus =
        loopbackServer.buildHealthSnapshot(
            activeTasks = tasks.value.count { it.status == "RUNNING" || it.status == "PLANNING" }
        )

    override fun onCleared() {
        super.onCleared()
        loopbackServer.stop()
        computerProvider.cancelAllRunningCommands()
    }
}
