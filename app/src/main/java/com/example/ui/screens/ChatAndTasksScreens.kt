package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.backend.LocalHealthStatus
import com.example.data.local.AutonomousTaskEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationThreadEntity
import com.example.data.local.TaskStepEntity
import com.example.ui.theme.AmberCheckpoint
import com.example.ui.theme.EmeraldHealthy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Composable
fun ChatScreen(
    health: LocalHealthStatus,
    threads: List<ConversationThreadEntity>,
    selectedThreadId: Long,
    showArchived: Boolean,
    messages: List<ChatMessageEntity>,
    onSelectThread: (Long) -> Unit,
    onCreateThread: (String, Boolean) -> Unit,
    onRenameThread: (String) -> Unit,
    onToggleArchive: (Long) -> Unit,
    onToggleShowArchived: (Boolean) -> Unit,
    onSendPrompt: (String) -> Unit,
    onApproveCheckpoint: (Long, Int) -> Unit,
    onRejectCheckpoint: (Long, Int) -> Unit
) {
    var promptInput by remember { mutableStateOf("") }
    var isRenaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    val visibleThreads = remember(threads, showArchived) {
        threads.filter { showArchived || !it.isArchived }
    }
    val activeThread = threads.firstOrNull { it.id == selectedThreadId }

    // Auto-scroll to latest message whenever a message is sent, received, updated, or keyboard opens
    LaunchedEffect(messages.size, messages.lastOrNull()?.id, messages.lastOrNull()?.content?.length, isImeVisible) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("chat_screen")
    ) {
        // Collapse telemetry header while soft keyboard is open so messages & input box have maximum room
        AnimatedVisibility(visible = !isImeVisible) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Hero Banner & Live Local Telemetry Strip (responsive IntrinsicSize.Min so background never clips)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.bg_agent_hero),
                            contentDescription = "OpenMuse Autonomous Studio Telemetry Banner",
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "OPENMUSE // LOCAL AGENT",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF22D3EE),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                StatusPill(
                                    label = if (health.apiRunning) "API :${health.port}" else "API OFFLINE",
                                    healthy = health.apiRunning
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusPill(
                                    label = "Runtime: ${health.executionMode}",
                                    healthy = health.linuxRuntimeStatus == "RUNNING"
                                )
                                StatusPill(
                                    label = "SQLite DB: LOCAL",
                                    healthy = health.databaseConnected
                                )
                                StatusPill(
                                    label = if (health.geminiConfigured) "Gemini: READY" else "Gemini: LOCAL TOOLS",
                                    healthy = true
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Thread Bar (Main + Side Conversations + Archive/Rename)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleThreads.forEach { thread ->
                        FilterChip(
                            selected = thread.id == selectedThreadId,
                            onClick = { onSelectThread(thread.id) },
                            label = {
                                Text(
                                    text = (if (thread.isSideThread) "↳ " else "") + thread.title + (if (thread.isArchived) " [Archived]" else ""),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("thread_chip_${thread.id}")
                        )
                    }
                    AssistChip(
                        onClick = { onCreateThread("Main Session ${threads.size + 1}", false) },
                        label = { Text("New", maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = "New Conversation", modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("new_thread_button")
                    )
                    AssistChip(
                        onClick = { onCreateThread("Side Branch ${threads.size + 1}", true) },
                        label = { Text("Side Thread", maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = "Branch Side Conversation", modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("new_side_thread_button")
                    )
                    IconButton(
                        onClick = {
                            renameText = activeThread?.title ?: ""
                            isRenaming = !isRenaming
                        },
                        modifier = Modifier.testTag("rename_thread_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename Active Conversation")
                    }
                    if (activeThread != null) {
                        IconButton(
                            onClick = { onToggleArchive(activeThread.id) },
                            modifier = Modifier.testTag("archive_thread_button")
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = "Archive or Restore Thread")
                        }
                    }
                    FilterChip(
                        selected = showArchived,
                        onClick = { onToggleShowArchived(!showArchived) },
                        label = { Text("Archived", maxLines = 1, softWrap = false) }
                    )
                }

                if (isRenaming) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("Rename Thread") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                onRenameThread(renameText)
                                isRenaming = false
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }

                // Quick Local Agent Action Starters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { onSendPrompt("Verify environment, kernel, workspace files, and git status") },
                        label = { Text("Verify Local Env", maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("quick_action_verify_env")
                    )
                    AssistChip(
                        onClick = { onSendPrompt("git: status") },
                        label = { Text("Git Status", maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { onSendPrompt("browse: https://developer.android.com/kotlin") },
                        label = { Text("Inspect Android Docs", maxLines = 1, softWrap = false) }
                    )
                    AssistChip(
                        onClick = { onSendPrompt("run: rm -rf /tmp/test_danger") },
                        label = { Text("Test Approval Gate", maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("quick_action_approval_gate")
                    )
                }
            }
        }

        // Messages & Inline Generative UI (AG-UI) Chat Bubbles
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("chat_message_list"),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                AgUiMessageCard(
                    message = msg,
                    onApproveCheckpoint = onApproveCheckpoint,
                    onRejectCheckpoint = onRejectCheckpoint
                )
            }
        }

        // Chat Composer Row (Always stays visible directly above the keyboard)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = {
                        Text(
                            text = "Message OpenMuse or use 'run:', 'git:', 'browse:'...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (promptInput.isNotBlank()) {
                                val textToSend = promptInput
                                promptInput = ""
                                onSendPrompt(textToSend)
                                coroutineScope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.lastIndex)
                                    }
                                }
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_prompt_input"),
                    maxLines = 3
                )
                Button(
                    onClick = {
                        if (promptInput.isNotBlank()) {
                            val textToSend = promptInput
                            promptInput = ""
                            onSendPrompt(textToSend)
                            coroutineScope.launch {
                                if (messages.isNotEmpty()) {
                                    listState.animateScrollToItem(messages.lastIndex)
                                }
                            }
                        }
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("chat_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Task Prompt")
                }
            }
        }
    }
}

@Composable
private fun AgUiMessageCard(
    message: ChatMessageEntity,
    onApproveCheckpoint: (Long, Int) -> Unit,
    onRejectCheckpoint: (Long, Int) -> Unit
) {
    val isUser = message.role == "USER"
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    }

    val borderColor = when {
        isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        message.cardType == "APPROVAL_REQUEST" -> AmberCheckpoint
        message.cardType == "COMMAND_RECEIPT" || message.cardType == "FILE_DIFF" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        message.cardType == "PLAN" || message.cardType == "BROWSER_SNAPSHOT" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .then(
                    if (isUser) Modifier.widthIn(min = 80.dp, max = 320.dp)
                    else Modifier.fillMaxWidth(0.94f)
                )
                .border(1.dp, borderColor, bubbleShape),
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "${message.role} • ${message.cardType}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isUser -> MaterialTheme.colorScheme.primary
                            message.cardType == "APPROVAL_REQUEST" -> AmberCheckpoint
                            message.cardType == "COMMAND_RECEIPT" -> EmeraldHealthy
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = if (message.cardType == "COMMAND_RECEIPT" || message.cardType == "FILE_DIFF") {
                        MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )

                if (message.cardType == "APPROVAL_REQUEST") {
                    val parsed = remember(message.metadataJson) {
                        runCatching {
                            val obj = Json.parseToJsonElement(message.metadataJson).jsonObject
                            val tId = obj["taskId"]?.jsonPrimitive?.longOrNull ?: 0L
                            val sIdx = obj["stepIndex"]?.jsonPrimitive?.intOrNull ?: 0
                            tId to sIdx
                        }.getOrDefault(0L to 0)
                    }
                    if (parsed.first > 0L) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onApproveCheckpoint(parsed.first, parsed.second) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldHealthy),
                                modifier = Modifier.testTag("approve_checkpoint_${parsed.first}_${parsed.second}")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve & Execute", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onRejectCheckpoint(parsed.first, parsed.second) },
                                modifier = Modifier.testTag("reject_checkpoint_${parsed.first}_${parsed.second}")
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TasksActivityScreen(
    tasks: List<AutonomousTaskEntity>,
    allSteps: List<TaskStepEntity>,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onApprove: (Long, Int) -> Unit,
    onReject: (Long, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("tasks_screen")
    ) {
        Text(
            text = "Autonomous Task Engine & Checkpoints",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Cycle: Understand → Plan → Select Approved Tool → Execute → Observe → Checkpoint → Complete",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (tasks.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "No autonomous tasks recorded yet.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Submit a goal in the Chat screen to build an execution plan and observe live tool receipts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    val stepsForTask = allSteps.filter { it.taskId == task.id }.sortedBy { it.stepIndex }
                    TaskCardItem(
                        task = task,
                        steps = stepsForTask,
                        onPause = { onPause(task.id) },
                        onResume = { onResume(task.id) },
                        onCancel = { onCancel(task.id) },
                        onRetry = { onRetry(task.id) },
                        onApprove = { stepIdx -> onApprove(task.id, stepIdx) },
                        onReject = { stepIdx -> onReject(task.id, stepIdx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCardItem(
    task: AutonomousTaskEntity,
    steps: List<TaskStepEntity>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onApprove: (Int) -> Unit,
    onReject: (Int) -> Unit
) {
    val badgeColor = when (task.status) {
        "COMPLETED" -> EmeraldHealthy
        "AWAITING_APPROVAL", "PAUSED" -> AmberCheckpoint
        "FAILED", "CANCELLED" -> RoseError
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .testTag("task_item_card_${task.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Task #${task.id} • ${task.status}",
                    style = MaterialTheme.typography.labelLarge,
                    color = badgeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(
                    text = "Budget: ${steps.size}/${task.maxStepsBudget} steps • Retries: ${task.retryCount}/${task.maxRetries}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = task.goal,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!task.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Alert: ${task.errorMessage}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoseError
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            steps.forEach { step ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${step.stepIndex + 1}. [${step.toolName}] ${step.title}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = step.status,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                                color = when (step.status) {
                                    "COMPLETED" -> EmeraldHealthy
                                    "AWAITING_APPROVAL" -> AmberCheckpoint
                                    "FAILED" -> RoseError
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        if (step.observationOutput.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = step.observationOutput.take(360),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (step.status == "AWAITING_APPROVAL") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onApprove(step.stepIndex) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldHealthy)
                                ) {
                                    Text("Approve Step", color = Color.Black)
                                }
                                OutlinedButton(onClick = { onReject(step.stepIndex) }) {
                                    Text("Reject")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.status == "RUNNING" || task.status == "PLANNING") {
                    OutlinedButton(onClick = onPause, modifier = Modifier.testTag("pause_task_${task.id}")) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause")
                    }
                }
                if (task.status == "PAUSED") {
                    Button(onClick = onResume, modifier = Modifier.testTag("resume_task_${task.id}")) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume")
                    }
                }
                if (task.status == "FAILED" || task.status == "CANCELLED" || task.status == "PAUSED") {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("retry_task_${task.id}")) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry")
                    }
                }
                if (task.status == "RUNNING" || task.status == "PLANNING" || task.status == "AWAITING_APPROVAL" || task.status == "PAUSED") {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("cancel_task_${task.id}")) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(label: String, healthy: Boolean) {
    val dotColor = if (healthy) EmeraldHealthy else AmberCheckpoint
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            .border(1.dp, dotColor.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
