package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "project_workspaces")
data class ProjectWorkspaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rootPath: String,
    val gitBranch: String = "main",
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "conversation_threads")
data class ConversationThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long = 1L,
    val title: String,
    val isSideThread: Boolean = false,
    val parentThreadId: Long? = null,
    val isArchived: Boolean = false,
    val externalCopilotThreadId: String? = null,
    val linkedTaskId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val role: String, // USER, ASSISTANT, TOOL, SYSTEM
    val content: String,
    val cardType: String = "TEXT", // TEXT, PLAN, COMMAND_RECEIPT, FILE_DIFF, BROWSER_SNAPSHOT, APPROVAL_REQUEST
    val metadataJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "autonomous_tasks")
data class AutonomousTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val goal: String,
    val status: String, // PLANNING, RUNNING, AWAITING_APPROVAL, PAUSED, COMPLETED, FAILED, CANCELLED
    val currentStepIndex: Int = 0,
    val maxStepsBudget: Int = 8,
    val retryCount: Int = 0,
    val maxRetries: Int = 2,
    val timeoutMs: Long = 120_000L,
    val errorMessage: String? = null,
    val finalSummary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "task_steps")
data class TaskStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val stepIndex: Int,
    val title: String,
    val toolName: String, // run_command, write_file, read_file, list_files, git_op, browse_url, inspect_dom
    val toolArgsJson: String,
    val requiresApproval: Boolean = false,
    val approvalReason: String? = null,
    val status: String = "PENDING", // PENDING, AWAITING_APPROVAL, RUNNING, COMPLETED, FAILED, SKIPPED
    val observationOutput: String = "",
    val exitCode: Int? = null,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "command_receipts")
data class CommandReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: Long = 1L,
    val commandDisplay: String,
    val workingDir: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val executionMode: String, // PROOT_USERSPACE, ANDROID_POSIX_SHELL
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "browser_tabs")
data class BrowserTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val isAgentControlled: Boolean = false,
    val lastDomSummary: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "browser_bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val isHistoryEntry: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
