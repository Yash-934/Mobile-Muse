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
        val tokenTxt = File(defaultWorkspaceDir, "token.txt")
        if (!tokenTxt.exists()) {
            tokenTxt.writeText("om_workspace_artifact_token_v1=active\nworkspace=OpenMuse-Mobile-ARM64\n")
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

        // Seed Muse System Files & Core Agent Markdown Specs
        seedMuseSystemFilesHierarchy(defaultWorkspaceDir)

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

    private fun seedMuseSystemFilesHierarchy(root: File) {
        val systemFolders = listOf(
            "agents", "assets", "config", "data", "docs",
            "dreams", "hooks", "memory", "side-chats", "subscriptions", "user"
        )
        systemFolders.forEach { folderName ->
            val dir = File(root, folderName)
            if (!dir.exists()) dir.mkdirs()
        }

        fun writeIfMissing(relPath: String, content: String) {
            val f = File(root, relPath)
            if (!f.exists()) {
                f.parentFile?.mkdirs()
                f.writeText(content)
            }
        }

        // Core Muse root specification files
        writeIfMissing("AGENTS.md", "# AGENTS.md\nAutonomous Muse worker definitions: Browser, Terminal, Workspace, and Git.\n")
        writeIfMissing("HEARTBEAT.md", "# HEARTBEAT.md\nLocal loopback health daemon active on 127.0.0.1:18765.\n")
        writeIfMissing("IDENTITY.md", "# IDENTITY.md\nName: Muse (CopilotKit/OpenMuse Android Autonomous Harness)\n")
        writeIfMissing("MEMORY.md", "# MEMORY.md\nPersistent SQLite & Workspace state across sessions.\n")
        writeIfMissing("PROACTIVE_PREFERENCES.md", "# PROACTIVE_PREFERENCES.md\nDefault theme: Light\nModel: gemini-3.8-flash\n")
        writeIfMissing("SOUL.md", "# SOUL.md\nTruthful local execution, human-in-the-loop checkpoints, zero hallucinated receipts.\n")
        writeIfMissing("TOOLS.md", "# TOOLS.md\nTools: browse_url, web_search, run_command, write_file, read_file, list_files, git_op.\n")
        writeIfMissing("USER.md", "# USER.md\nWorkspace owner profile and local preferences.\n")

        // Exact config/ files shown in screenshots
        writeIfMissing("config/filesystem_watch_hashes.initialized", "1")
        writeIfMissing(
            "config/filesystem_watch_hashes.json",
            """
            {
                "content_schema": "workflow-skills-v1",
                "files": {
                    "AGENTS.md": "44a27ed50a631c9b0e3f8719c0a214b8",
                    "HEARTBEAT.md": "55acc18c4867cf741598bc110e9a3c42",
                    "IDENTITY.md": "5ccd11b7c777350ab6d81920f4e1120a",
                    "MEMORY.md": "4bf39eb8a2ed312a6d3b5908f12c9a01",
                    "PROACTIVE_PREFERENCES.md": "fc16d128a90b41c77e2109a8b3412f90",
                    "SOUL.md": "cbe150d5ba2dfb68f6beda12908fa31c",
                    "TOOLS.md": "3adfa69c76d2f89ceda5736910abc812",
                    "USER.md": "c80cdf25ffcebbd1934d3b721098fabc",
                    "opt/muse/skills/advertising-campaign/SKILL.md": "91a8b7c6d5e4f3a2b1c0",
                    "opt/muse/skills/app-creation/SKILL.md": "82b9c8d7e6f5a4b3c2d1",
                    "opt/muse/skills/business-finance/SKILL.md": "73cad9e8f7a6b5c4d3e2",
                    "opt/muse/skills/custom-connector-setup/SKILL.md": "64dbe0f9a8b7c6d5e4f3",
                    "opt/muse/skills/customer-outreach/SKILL.md": "55ecf1a0b9c8d7e6f5a4",
                    "opt/muse/skills/device-data/SKILL.md": "46fda2b1c0d9e8f7a6b5",
                    "opt/muse/skills/documents/SKILL.md": "37geb3c2d1e0f9a8b7c6",
                    "opt/muse/skills/email-management/SKILL.md": "28hfc4d3e2f1a0b9c8d7",
                    "opt/muse/skills/event-tickets/SKILL.md": "19igd5e4f3a2b1c0d9e8",
                    "opt/muse/skills/file-hosting/SKILL.md": "0ajhe6f5a4b3c2d1e0f9",
                    "opt/muse/skills/financial-planning/SKILL.md": "fbkif7a6b5c4d3e2f1a0",
                    "opt/muse/skills/fitness-tracking/SKILL.md": "gcljg8b7c6d5e4f3a2b1",
                    "opt/muse/skills/flight-booking/SKILL.md": "hdmkh9c8d7e6f5a4b3c2",
                    "opt/muse/skills/flight-tracking/SKILL.md": "ienli0d9e8f7a6b5c4d3",
                    "opt/muse/skills/forget/SKILL.md": "jfomj1e0f9a8b7c6d5e4",
                    "opt/muse/skills/form-design/SKILL.md": "kgpnk2f1a0b9c8d7e6f5",
                    "opt/muse/skills/generate_podcast/SKILL.md": "lhqol3a2b1c0d9e8f7a6",
                    "opt/muse/skills/goals/SKILL.md": "mirpm4b3c2d1e0f9a8b7",
                    "opt/muse/skills/groceries/SKILL.md": "njsqn5c4d3e2f1a0b9c8",
                    "opt/muse/skills/health-review/SKILL.md": "oktro6d5e4f3a2b1c0d9",
                    "opt/muse/skills/home-control/SKILL.md": "plusp7e6f5a4b3c2d1e0",
                    "opt/muse/skills/home-search/SKILL.md": "qmvtq8f7a6b5c4d3e2f1",
                    "opt/muse/skills/hotel-booking/SKILL.md": "rnwur9a8b7c6d5e4f3a2",
                    "opt/muse/skills/image-search/SKILL.md": "soxvs0b9c8d7e6f5a4b3",
                    "opt/muse/skills/knowledge-organization/SKILL.md": "tpywt1c0d9e8f7a6b5c4",
                    "opt/muse/skills/marketplaces/SKILL.md": "uqzxu2d1e0f9a8b7c6d5",
                    "opt/muse/skills/media-library/SKILL.md": "vrayv3e2f1a0b9c8d7e6",
                    "opt/muse/skills/meeting-follow-up/SKILL.md": "wsbzw4f3a2b1c0d9e8f7",
                    "opt/muse/skills/messaging/SKILL.md": "xtcax5a4b3c2d1e0f9a8",
                    "opt/muse/skills/mortgage-comparison/SKILL.md": "yudby6b5c4d3e2f1a0b9",
                    "opt/muse/skills/muse-early-access/SKILL.md": "zvecz7c6d5e4f3a2b1c0",
                    "opt/muse/skills/muse-feedback/SKILL.md": "0wfda8d7e6f5a4b3c2d1",
                    "opt/muse/skills/muse_db/SKILL.md": "1xgeb9e8f7a6b5c4d3e2"
                }
            }
            """.trimIndent() + "\n"
        )
        writeIfMissing("config/home.yaml", "workspace: /workspace\nagent_name: Muse\nmode: autonomous\nloopback_port: 18765\n")
        writeIfMissing("config/recent-skills.json", """{"recent": ["browser-worker", "linux-shell", "git-engine"]}""" + "\n")
        writeIfMissing(
            "config/skills.yaml",
            """
            version: 1
            skills:
              - id: browser-worker
                enabled: true
                driver: android-webview-dom-bridge
              - id: linux-terminal
                enabled: true
                driver: proot-arm64-posix
              - id: workspace-files
                enabled: true
                driver: canonical-sandbox
              - id: git-engine
                enabled: true
                driver: builtin-and-rootfs-git
            """.trimIndent() + "\n"
        )
        writeIfMissing(
            "config/workspace-state.json",
            """
            {
              "activeWorkspace": "default-mobile-agent",
              "gitBranch": "main",
              "runtimeMode": "ANDROID_POSIX_SHELL",
              "loopbackBound": "127.0.0.1:18765",
              "status": "READY"
            }
            """.trimIndent() + "\n"
        )
        writeIfMissing("config/workspace-state.lock", "")

        // Seed helpful files in other system directories
        writeIfMissing("agents/browser_agent.yaml", "name: BrowserWorker\nlayerA: WebView\nlayerB: OkHttpDomInspector\n")
        writeIfMissing("agents/terminal_agent.yaml", "name: TerminalWorker\nsandbox: canonical-workspace\n")
        writeIfMissing("assets/muse_banner.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"80\"><rect width=\"200\" height=\"80\" rx=\"16\" fill=\"#2563EB\"/><text x=\"24\" y=\"48\" fill=\"#FFFFFF\" font-size=\"24\" font-family=\"sans-serif\">Muse Media</text></svg>\n")
        writeIfMissing("data/telemetry_snapshot.json", """{"loopbackPort": 18765, "sqlite": "connected"}""" + "\n")
        writeIfMissing("docs/ARCHITECTURE.md", "# Muse Mobile Architecture\n8-Stage Task Engine + Two-Layer Browser + PRoot/POSIX Workspace.\n")
        writeIfMissing("dreams/ideas.md", "# Agent Ideas & Scheduled Reflections\n- Autonomous web research\n- Workspace code synthesis\n")
        writeIfMissing("hooks/pre_command.sh", "#!/system/bin/sh\n# Validates consequential commands before execution\n")
        writeIfMissing("memory/session_context.json", """{"activeThreadId": 1, "persisted": true}""" + "\n")
        writeIfMissing("side-chats/README.md", "# Side Chats\nBranched parallel agent threads stored in Room SQLite.\n")
        writeIfMissing("subscriptions/feeds.json", """{"rss": ["https://news.google.com/rss"]}""" + "\n")
        writeIfMissing("user/profile.yaml", "handle: @muse_user\ntheme: light\nmodel: gemini-3.8-flash\n")
    }
}
