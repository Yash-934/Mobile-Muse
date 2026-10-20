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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.backend.LocalHealthStatus
import com.example.data.local.AutonomousTaskEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationThreadEntity
import com.example.data.local.TaskStepEntity
import com.example.ui.components.MuseMarkdownAndMathView
import com.example.ui.theme.AmberCheckpoint
import com.example.ui.theme.EmeraldHealthy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MuseWarmTerracotta
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private val CHAT_GROUPS = listOf("All", "Study", "Coding", "Work", "Saved")

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
    onRejectCheckpoint: (Long, Int) -> Unit,
    onOpenBrowser: () -> Unit = {},
    onOpenWorkspace: () -> Unit = {},
    isDarkTheme: Boolean = false,
    onAssignThreadGroup: (Long, String) -> Unit = { _, _ -> },
    onCopyText: (android.content.Context, String, String) -> Unit = { _, _, _ -> },
    onShareText: (android.content.Context, String, String) -> Unit = { _, _, _ -> },
    onDownloadSnippet: (android.content.Context, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var promptInput by remember { mutableStateOf("") }
    var isRenaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showThreadsPanel by remember { mutableStateOf(true) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }
    var showPlusQuickMenu by remember { mutableStateOf(false) }
    var showGroupAssignMenu by remember { mutableStateOf(false) }
    var selectedGroupFilter by remember { mutableStateOf("All") }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    val visibleThreads = remember(threads, showArchived, selectedGroupFilter) {
        threads.filter { t ->
            val archiveMatch = showArchived || !t.isArchived
            val groupMatch = selectedGroupFilter == "All" ||
                t.title.startsWith("[$selectedGroupFilter]", ignoreCase = true) ||
                t.id == selectedThreadId
            archiveMatch && groupMatch
        }
    }
    val activeThread = threads.firstOrNull { it.id == selectedThreadId }
    val hasUserSentMessages = remember(messages) { messages.any { it.role == "USER" } }

    // Auto-scroll to latest message whenever a message is sent, received, updated, or keyboard opens
    LaunchedEffect(messages.size, messages.lastOrNull()?.id, messages.lastOrNull()?.content?.length, isImeVisible) {
        if (messages.isNotEmpty() && hasUserSentMessages) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("chat_screen")
    ) {
        // --- Muse Top Header Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Circular Drawer/Threads Button with Warm Orange Badge Dot
            Box(contentAlignment = Alignment.TopEnd) {
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { showThreadsPanel = !showThreadsPanel },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Toggle Conversations & Groups",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .offset(x = (-2).dp, y = 2.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(MuseWarmTerracotta)
                )
            }

            // Center Status / Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = activeThread?.title ?: "Muse",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (health.apiRunning && health.pocShellVerified) EmeraldHealthy else AmberCheckpoint)
                    )
                    Text(
                        text = "${health.executionMode} • :${health.port}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Three-Dot Menu (Copy Full Chat, Download Chat .md, Share Chat, Group Assign)
            Box {
                IconButton(
                    onClick = { showTopOverflowMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Conversation Options",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = showTopOverflowMenu,
                    onDismissRequest = { showTopOverflowMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy Entire Chat") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            showTopOverflowMenu = false
                            val fullTranscript = messages.joinToString("\n\n---\n\n") { m ->
                                "**${m.role} (${m.cardType}):**\n${m.content}"
                            }
                            onCopyText(context, "Full Chat Transcript", fullTranscript)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Download Chat as .md") },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            showTopOverflowMenu = false
                            val fullTranscript = messages.joinToString("\n\n---\n\n") { m ->
                                "### ${m.role} (${m.cardType})\n${m.content}"
                            }
                            onDownloadSnippet(context, "chat_thread_${selectedThreadId}.md", fullTranscript)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share Conversation") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showTopOverflowMenu = false
                            val fullTranscript = messages.joinToString("\n\n") { m ->
                                "${m.role}: ${m.content}"
                            }
                            onShareText(context, activeThread?.title ?: "Muse Chat", fullTranscript)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Group (Study/Coding/Work)") },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        onClick = {
                            showTopOverflowMenu = false
                            showGroupAssignMenu = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New Conversation") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            showTopOverflowMenu = false
                            onCreateThread("Main Session ${threads.size + 1}", false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Branch Side Thread") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null) },
                        onClick = {
                            showTopOverflowMenu = false
                            onCreateThread("Side Branch ${threads.size + 1}", true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename Active Thread") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showTopOverflowMenu = false
                            renameText = activeThread?.title ?: ""
                            isRenaming = true
                        }
                    )
                }

                DropdownMenu(
                    expanded = showGroupAssignMenu,
                    onDismissRequest = { showGroupAssignMenu = false }
                ) {
                    listOf("General", "Study", "Coding", "Work", "Saved").forEach { grp ->
                        DropdownMenuItem(
                            text = { Text("Group: $grp") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = {
                                showGroupAssignMenu = false
                                onAssignThreadGroup(selectedThreadId, grp)
                            }
                        )
                    }
                }
            }
        }

        // Collapsible Thread Groups, Threads & Quick Action Strip
        AnimatedVisibility(visible = !isImeVisible && showThreadsPanel) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Chat Group Filter Bar (All | Study | Coding | Work | Saved) + Assign Group Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Groups:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CHAT_GROUPS.forEach { groupName ->
                        FilterChip(
                            selected = selectedGroupFilter == groupName,
                            onClick = { selectedGroupFilter = groupName },
                            label = { Text(groupName, maxLines = 1, softWrap = false, fontSize = 11.sp) }
                        )
                    }
                    AssistChip(
                        onClick = { showGroupAssignMenu = true },
                        label = { Text("Set Group", maxLines = 1, softWrap = false, fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

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
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("rename_thread_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename Active Conversation", modifier = Modifier.size(18.dp))
                    }
                    if (activeThread != null) {
                        IconButton(
                            onClick = { onToggleArchive(activeThread.id) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("archive_thread_button")
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = "Archive or Restore Thread", modifier = Modifier.size(18.dp))
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

                // Quick Agent Verification & Textbook Formula Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { onSendPrompt("प्रतिशत (Percentage) सूत्र और उदाहरण समझाओ") },
                        label = { Text("📐 प्रतिशत / Formula", maxLines = 1, softWrap = false) }
                    )
                    AssistChip(
                        onClick = { onSendPrompt("open cricbuzz and check live cricket score") },
                        label = { Text("Live Cricket Score", maxLines = 1, softWrap = false) }
                    )
                    AssistChip(
                        onClick = { onSendPrompt("Verify environment, kernel, workspace files, and git status") },
                        label = { Text("Verify Local Env", maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        modifier = Modifier.testTag("quick_action_verify_env")
                    )
                    AssistChip(
                        onClick = { onSendPrompt("git: status") },
                        label = { Text("Git Status", maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    )
                    AssistChip(
                        onClick = { onSendPrompt("run: rm -rf /tmp/test_danger") },
                        label = { Text("Test Approval Gate", maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        modifier = Modifier.testTag("quick_action_approval_gate")
                    )
                }
            }
        }

        // --- Main Content Area: Muse Hero Welcome + Chat Stream ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("chat_message_list"),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasUserSentMessages && !isImeVisible) {
                item(key = "muse_hero_welcome") {
                    MuseHeroWelcomeSection(onSendPrompt = onSendPrompt)
                }
            }

            items(messages, key = { it.id }) { msg ->
                AgUiMessageCard(
                    message = msg,
                    isDarkTheme = isDarkTheme,
                    onApproveCheckpoint = onApproveCheckpoint,
                    onRejectCheckpoint = onRejectCheckpoint,
                    onOpenBrowser = onOpenBrowser,
                    onOpenWorkspace = onOpenWorkspace,
                    onCopyMessage = { text -> onCopyText(context, "Message", text) },
                    onShareMessage = { text -> onShareText(context, "Muse Chat Message", text) },
                    onDownloadSnippet = { fileName, code -> onDownloadSnippet(context, fileName, code) }
                )
            }
        }

        // --- Muse Pill-Shaped Bottom Composer ("Message Muse...") ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left '+' Button for Quick Agent Tools
                Box {
                    IconButton(
                        onClick = { showPlusQuickMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Quick Agent Actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showPlusQuickMenu,
                        onDismissRequest = { showPlusQuickMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("📐 प्रतिशत (Percentage) Textbook Formula") },
                            onClick = {
                                showPlusQuickMenu = false
                                onSendPrompt("प्रतिशत (Percentage) सूत्र और उदाहरण समझाओ")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🏏 Check Live Cricket Score") },
                            onClick = {
                                showPlusQuickMenu = false
                                onSendPrompt("open cricbuzz and check live cricket score")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📝 Create Notes File (notes.md)") },
                            onClick = {
                                showPlusQuickMenu = false
                                onSendPrompt("Help me organize my files or create a new note")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("💻 Check Device & Linux System Info") },
                            onClick = {
                                showPlusQuickMenu = false
                                onSendPrompt("Show me my device info and storage usage")
                            }
                        )
                    }
                }

                // Center Text Input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (promptInput.isEmpty()) {
                        Text(
                            text = "Message Muse...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_prompt_input")
                    )
                }

                // Right Circular Send Button
                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
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
                        .testTag("chat_send_button"),
                    shape = CircleShape,
                    color = if (promptInput.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (promptInput.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.ArrowUpward,
                            contentDescription = "Send Task Prompt",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MuseHeroWelcomeSection(
    onSendPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(108.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.12f)),
            shape = CircleShape,
            color = Color.White
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_muse_plush_avatar),
                contentDescription = "Muse Mascot Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Hi, I'm Muse",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Happy to see you! Let's conquer your tasks together.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Row 1 of 2x2 Starter Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MuseSuggestionCard(
                title = "Check Today's News",
                subtitle = "Find the top 3 news stories from today and summarize them",
                onClick = { onSendPrompt("Find the top 3 news stories from today and summarize them") },
                modifier = Modifier.weight(1f)
            )
            MuseSuggestionCard(
                title = "Textbook Math & Code",
                subtitle = "प्रतिशत (Percentage) सूत्र, भिन्न और कोड उदाहरण देखें",
                onClick = { onSendPrompt("प्रतिशत (Percentage) सूत्र और उदाहरण समझाओ") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2 of 2x2 Starter Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MuseSuggestionCard(
                title = "Explore Files",
                subtitle = "Help me organize my files or create a new note",
                onClick = { onSendPrompt("Help me organize my files or create a new note") },
                modifier = Modifier.weight(1f)
            )
            MuseSuggestionCard(
                title = "System Info",
                subtitle = "Show me my device info and storage usage",
                onClick = { onSendPrompt("Show me my device info and storage usage") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MuseSuggestionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(104.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun AgUiMessageCard(
    message: ChatMessageEntity,
    isDarkTheme: Boolean,
    onApproveCheckpoint: (Long, Int) -> Unit,
    onRejectCheckpoint: (Long, Int) -> Unit,
    onOpenBrowser: () -> Unit = {},
    onOpenWorkspace: () -> Unit = {},
    onCopyMessage: (String) -> Unit = {},
    onShareMessage: (String) -> Unit = {},
    onDownloadSnippet: (String, String) -> Unit = { _, _ -> }
) {
    val isUser = message.role == "USER"
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    }

    val borderColor = when {
        isUser -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        message.cardType == "APPROVAL_REQUEST" -> AmberCheckpoint
        message.cardType == "COMMAND_RECEIPT" || message.cardType == "FILE_DIFF" -> MuseWarmTerracotta.copy(alpha = 0.5f)
        message.cardType == "PLAN" || message.cardType == "BROWSER_SNAPSHOT" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .then(
                    if (isUser) Modifier.widthIn(min = 80.dp, max = 330.dp)
                    else Modifier.fillMaxWidth(0.96f)
                )
                .border(1.dp, borderColor, bubbleShape),
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "Muse • ${message.cardType}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                            message.cardType == "APPROVAL_REQUEST" -> AmberCheckpoint
                            message.cardType == "COMMAND_RECEIPT" -> EmeraldHealthy
                            else -> MuseWarmTerracotta
                        }
                    )

                    // Quick Message Action Icons (Copy, Download as .md, Share)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Message",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable { onCopyMessage(message.content) }
                        )
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Save Message to Device",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    onDownloadSnippet("message_${message.id}.md", message.content)
                                }
                        )
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Message",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable { onShareMessage(message.content) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (message.cardType == "COMMAND_RECEIPT" || message.cardType == "FILE_DIFF") {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = textColor
                    )
                } else {
                    // Rich Markdown, Textbook Math Formula, and Codebox Renderer
                    MuseMarkdownAndMathView(
                        content = message.content,
                        textColor = textColor,
                        isDark = isDarkTheme,
                        onCopyText = onCopyMessage,
                        onDownloadCodeSnippet = onDownloadSnippet
                    )
                }

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

                if (message.cardType == "BROWSER_SNAPSHOT") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenBrowser,
                        modifier = Modifier.testTag("open_browser_from_card_${message.id}")
                    ) {
                        Text("Open Live in Browser Tab", maxLines = 1, softWrap = false)
                    }
                } else if (message.cardType == "FILE_DIFF" || message.cardType == "COMMAND_RECEIPT") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenWorkspace,
                        modifier = Modifier.testTag("open_workspace_from_card_${message.id}")
                    ) {
                        Text(
                            text = if (message.cardType == "FILE_DIFF") "Open in Files Manager" else "Open in Terminal",
                            maxLines = 1,
                            softWrap = false
                        )
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
