package com.example.data.local

import kotlinx.coroutines.flow.Flow
import java.io.File

class OpenMuseRepository(
    private val dao: OpenMuseDao,
    private val defaultWorkspaceDir: File
) {
    val workspaces: Flow<List<ProjectWorkspaceEntity>> = dao.observeWorkspaces()
    val threads: Flow<List<ConversationThreadEntity>> = dao.observeAllThreads()
    val tasks: Flow<List<AutonomousTaskEntity>> = dao.observeTasks()
    val allTaskSteps: Flow<List<TaskStepEntity>> = dao.observeAllTaskSteps()
    val commandReceipts: Flow<List<CommandReceiptEntity>> = dao.observeCommandReceipts()
    val browserTabs: Flow<List<BrowserTabEntity>> = dao.observeBrowserTabs()
    val bookmarksAndHistory: Flow<List<BookmarkEntity>> = dao.observeBookmarksAndHistory()

    fun observeMessages(threadId: Long): Flow<List<ChatMessageEntity>> =
        dao.observeMessagesForThread(threadId)

    fun observeStepsForTask(taskId: Long): Flow<List<TaskStepEntity>> =
        dao.observeStepsForTask(taskId)

    suspend fun ensureInitialized(): Long {
        if (!defaultWorkspaceDir.exists()) {
            defaultWorkspaceDir.mkdirs()
        }
        val readme = File(defaultWorkspaceDir, "README.md")
        if (!readme.exists()) {
            readme.writeText(
                """
                # OpenMuse Mobile Workspace
                
                This directory is the persistent local workspace managed by `AndroidProotComputerProvider`.
                - Ordinary file operations, Git repositories, and shell commands execute here.
                - External Android storage is isolated by default; use SAF Import/Export to transfer files outside this workspace.
                """.trimIndent() + "\n"
            )
        }
        val sampleScript = File(defaultWorkspaceDir, "verify_env.sh")
        if (!sampleScript.exists()) {
            sampleScript.writeText(
                """
                #!/system/bin/sh
                echo "=== OpenMuse Mobile Runtime Verification ==="
                uname -a
                id
                pwd
                ls -la
                """.trimIndent() + "\n"
            )
            sampleScript.setExecutable(true)
        }

        val existingWorkspaces = dao.getWorkspaces()
        val workspaceId = if (existingWorkspaces.isEmpty()) {
            dao.insertWorkspace(
                ProjectWorkspaceEntity(
                    name = "default-mobile-agent",
                    rootPath = defaultWorkspaceDir.absolutePath,
                    gitBranch = "main"
                )
            )
        } else {
            existingWorkspaces.first().id
        }

        val existingTabs = dao.getBrowserTabs()
        if (existingTabs.isEmpty()) {
            dao.insertBrowserTab(
                BrowserTabEntity(
                    title = "OpenMuse Local Docs",
                    url = "https://developer.android.com/kotlin",
                    isAgentControlled = false,
                    lastDomSummary = "Ready for interactive browsing or autonomous DOM inspection."
                )
            )
        }

        return workspaceId
    }

    suspend fun createThread(
        title: String,
        projectId: Long = 1L,
        isSideThread: Boolean = false,
        parentThreadId: Long? = null
    ): Long {
        val threadId = dao.insertThread(
            ConversationThreadEntity(
                projectId = projectId,
                title = title,
                isSideThread = isSideThread,
                parentThreadId = parentThreadId
            )
        )
        dao.insertMessage(
            ChatMessageEntity(
                threadId = threadId,
                role = "SYSTEM",
                content = if (isSideThread) {
                    "Side conversation branched. Local SQLite persistence active (no CopilotKit Intelligence key required)."
                } else {
                    "OpenMuse Mobile ready. Assigned tasks execute locally via AndroidProotComputerProvider, Loopback Task Worker, and Two-Layer Browser."
                },
                cardType = "TEXT"
            )
        )
        return threadId
    }

    suspend fun renameThread(threadId: Long, newTitle: String) {
        val thread = dao.getThreadById(threadId) ?: return
        dao.updateThread(thread.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleArchiveThread(threadId: Long) {
        val thread = dao.getThreadById(threadId) ?: return
        dao.updateThread(thread.copy(isArchived = !thread.isArchived, updatedAt = System.currentTimeMillis()))
    }

    suspend fun linkTaskToThread(threadId: Long, taskId: Long) {
        val thread = dao.getThreadById(threadId) ?: return
        dao.updateThread(thread.copy(linkedTaskId = taskId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun addMessage(
        threadId: Long,
        role: String,
        content: String,
        cardType: String = "TEXT",
        metadataJson: String = "{}"
    ): Long {
        val id = dao.insertMessage(
            ChatMessageEntity(
                threadId = threadId,
                role = role,
                content = content,
                cardType = cardType,
                metadataJson = metadataJson
            )
        )
        dao.getThreadById(threadId)?.let {
            dao.updateThread(it.copy(updatedAt = System.currentTimeMillis()))
        }
        return id
    }

    suspend fun getMessages(threadId: Long): List<ChatMessageEntity> =
        dao.getMessagesForThread(threadId)

    suspend fun createTask(
        threadId: Long,
        goal: String,
        maxStepsBudget: Int = 8,
        timeoutMs: Long = 120_000L
    ): Long {
        val taskId = dao.insertTask(
            AutonomousTaskEntity(
                threadId = threadId,
                goal = goal,
                status = "PLANNING",
                maxStepsBudget = maxStepsBudget,
                timeoutMs = timeoutMs
            )
        )
        linkTaskToThread(threadId, taskId)
        return taskId
    }

    suspend fun getTask(taskId: Long): AutonomousTaskEntity? = dao.getTaskById(taskId)

    suspend fun getAllTasksSnapshot(): List<AutonomousTaskEntity> = dao.getAllTasks()

    suspend fun updateTask(task: AutonomousTaskEntity) =
        dao.updateTask(task.copy(updatedAt = System.currentTimeMillis()))

    suspend fun insertTaskStep(step: TaskStepEntity): Long = dao.insertTaskStep(step)

    suspend fun updateTaskStep(step: TaskStepEntity) = dao.updateTaskStep(step)

    suspend fun getTaskSteps(taskId: Long): List<TaskStepEntity> = dao.getStepsForTask(taskId)

    suspend fun recordCommandReceipt(receipt: CommandReceiptEntity): Long =
        dao.insertCommandReceipt(receipt)

    suspend fun getRecentReceiptsSnapshot(): List<CommandReceiptEntity> =
        dao.getRecentCommandReceipts()

    suspend fun clearCommandReceipts() = dao.clearCommandReceipts()

    suspend fun addBrowserTab(title: String, url: String, isAgentControlled: Boolean = false): Long =
        dao.insertBrowserTab(
            BrowserTabEntity(
                title = title,
                url = url,
                isAgentControlled = isAgentControlled
            )
        )

    suspend fun updateBrowserTab(tab: BrowserTabEntity) =
        dao.updateBrowserTab(tab.copy(updatedAt = System.currentTimeMillis()))

    suspend fun closeBrowserTab(tabId: Long) = dao.deleteBrowserTab(tabId)

    suspend fun recordBookmarkOrHistory(title: String, url: String, isHistory: Boolean) {
        dao.insertBookmark(
            BookmarkEntity(
                title = title.ifBlank { url },
                url = url,
                isHistoryEntry = isHistory
            )
        )
    }

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmark(id)
}
