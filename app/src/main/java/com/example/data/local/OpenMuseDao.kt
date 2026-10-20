package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenMuseDao {
    // Workspaces
    @Query("SELECT * FROM project_workspaces ORDER BY lastOpenedAt DESC")
    fun observeWorkspaces(): Flow<List<ProjectWorkspaceEntity>>

    @Query("SELECT * FROM project_workspaces ORDER BY lastOpenedAt DESC")
    suspend fun getWorkspaces(): List<ProjectWorkspaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspace(workspace: ProjectWorkspaceEntity): Long

    @Update
    suspend fun updateWorkspace(workspace: ProjectWorkspaceEntity)

    // Conversation Threads
    @Query("SELECT * FROM conversation_threads ORDER BY updatedAt DESC")
    fun observeAllThreads(): Flow<List<ConversationThreadEntity>>

    @Query("SELECT * FROM conversation_threads WHERE id = :threadId LIMIT 1")
    suspend fun getThreadById(threadId: Long): ConversationThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ConversationThreadEntity): Long

    @Update
    suspend fun updateThread(thread: ConversationThreadEntity)

    @Query("DELETE FROM conversation_threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: Long)

    // Chat Messages
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeMessagesForThread(threadId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    suspend fun getMessagesForThread(threadId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    // Autonomous Tasks
    @Query("SELECT * FROM autonomous_tasks ORDER BY updatedAt DESC")
    fun observeTasks(): Flow<List<AutonomousTaskEntity>>

    @Query("SELECT * FROM autonomous_tasks ORDER BY updatedAt DESC")
    suspend fun getAllTasks(): List<AutonomousTaskEntity>

    @Query("SELECT * FROM autonomous_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): AutonomousTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: AutonomousTaskEntity): Long

    @Update
    suspend fun updateTask(task: AutonomousTaskEntity)

    // Task Steps
    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY stepIndex ASC")
    fun observeStepsForTask(taskId: Long): Flow<List<TaskStepEntity>>

    @Query("SELECT * FROM task_steps ORDER BY timestamp DESC")
    fun observeAllTaskSteps(): Flow<List<TaskStepEntity>>

    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY stepIndex ASC")
    suspend fun getStepsForTask(taskId: Long): List<TaskStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskStep(step: TaskStepEntity): Long

    @Update
    suspend fun updateTaskStep(step: TaskStepEntity)

    // Command Receipts
    @Query("SELECT * FROM command_receipts ORDER BY timestamp DESC LIMIT 100")
    fun observeCommandReceipts(): Flow<List<CommandReceiptEntity>>

    @Query("SELECT * FROM command_receipts ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentCommandReceipts(): List<CommandReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommandReceipt(receipt: CommandReceiptEntity): Long

    @Query("DELETE FROM command_receipts")
    suspend fun clearCommandReceipts()

    // Browser Tabs & Bookmarks
    @Query("SELECT * FROM browser_tabs ORDER BY id ASC")
    fun observeBrowserTabs(): Flow<List<BrowserTabEntity>>

    @Query("SELECT * FROM browser_tabs ORDER BY id ASC")
    suspend fun getBrowserTabs(): List<BrowserTabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrowserTab(tab: BrowserTabEntity): Long

    @Update
    suspend fun updateBrowserTab(tab: BrowserTabEntity)

    @Query("DELETE FROM browser_tabs WHERE id = :tabId")
    suspend fun deleteBrowserTab(tabId: Long)

    @Query("SELECT * FROM browser_bookmarks ORDER BY timestamp DESC LIMIT 100")
    fun observeBookmarksAndHistory(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM browser_bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)
}
