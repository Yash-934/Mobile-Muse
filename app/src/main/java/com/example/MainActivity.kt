package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.OpenMuseDestination
import com.example.ui.OpenMuseViewModel
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.LinuxEnvironmentScreen
import com.example.ui.screens.SettingsAndDiagnosticsScreen
import com.example.ui.screens.TasksActivityScreen
import com.example.ui.screens.TwoLayerBrowserScreen
import com.example.ui.screens.WorkspaceAndTerminalScreen
import com.example.ui.theme.EmeraldHealthy
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: OpenMuseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                OpenMuseMobileApp(viewModel = viewModel)
            }
        }
    }
}

private data class NavItem(
    val destination: OpenMuseDestination,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

// Concise labels ("Files", "Config") prevent any text wrapping/overflow in 6-item bottom NavigationBar
private val NAV_ITEMS = listOf(
    NavItem(OpenMuseDestination.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat, "nav_chat"),
    NavItem(OpenMuseDestination.TASKS, "Tasks", Icons.Default.CheckCircle, "nav_tasks"),
    NavItem(OpenMuseDestination.BROWSER, "Browser", Icons.Default.Language, "nav_browser"),
    NavItem(OpenMuseDestination.WORKSPACE, "Files", Icons.Default.Folder, "nav_workspace"),
    NavItem(OpenMuseDestination.LINUX_ENV, "Linux", Icons.Default.Memory, "nav_linux"),
    NavItem(OpenMuseDestination.SETTINGS, "Config", Icons.Default.Settings, "nav_settings")
)

@Composable
fun OpenMuseMobileApp(viewModel: OpenMuseViewModel) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val geminiKeyStatus by viewModel.geminiKeyMaskedStatus.collectAsStateWithLifecycle()
    val destination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val selectedThreadId by viewModel.selectedThreadId.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchivedThreads.collectAsStateWithLifecycle()
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val allSteps by viewModel.allTaskSteps.collectAsStateWithLifecycle()
    val browserState by viewModel.browserState.collectAsStateWithLifecycle()
    val browserTabs by viewModel.browserTabs.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarksAndHistory.collectAsStateWithLifecycle()
    val workspaceFiles by viewModel.workspaceFiles.collectAsStateWithLifecycle()
    val currentFolderSubdir by viewModel.currentFolderSubdir.collectAsStateWithLifecycle()
    val lastOpenedMap by viewModel.lastOpenedMap.collectAsStateWithLifecycle()
    val selectedFilePath by viewModel.selectedFilePath.collectAsStateWithLifecycle()
    val selectedFileContent by viewModel.selectedFileContent.collectAsStateWithLifecycle()
    val terminalWorkingSubdir by viewModel.terminalWorkingSubdir.collectAsStateWithLifecycle()
    val receipts by viewModel.commandReceipts.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val lastGitOutput by viewModel.lastGitOutput.collectAsStateWithLifecycle()
    val prootState by viewModel.prootState.collectAsStateWithLifecycle()
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val externalSyncEnabled by viewModel.externalSyncEnabled.collectAsStateWithLifecycle()
    val metrics by viewModel.runtimeMetrics.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    if (destination != OpenMuseDestination.CHAT) {
        BackHandler {
            viewModel.navigateTo(OpenMuseDestination.CHAT)
        }
    }

    val health = viewModel.getHealthSnapshot()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isExpandedScreen && !isImeVisible) {
                    NavigationBar {
                        NAV_ITEMS.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item.destination,
                                onClick = { viewModel.navigateTo(item.destination) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp
                                    )
                                },
                                alwaysShowLabel = true,
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        NAV_ITEMS.forEach { item ->
                            NavigationRailItem(
                                selected = destination == item.destination,
                                onClick = { viewModel.navigateTo(item.destination) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    LaunchedEffect(statusBanner) {
                        if (statusBanner != null) {
                            kotlinx.coroutines.delay(3500L)
                            viewModel.clearStatusBanner()
                        }
                    }
                    AnimatedVisibility(visible = statusBanner != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.clearStatusBanner() }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = statusBanner ?: "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldHealthy,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Dismiss",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (destination) {
                            OpenMuseDestination.CHAT -> ChatScreen(
                                health = health,
                                threads = threads,
                                selectedThreadId = selectedThreadId,
                                showArchived = showArchived,
                                messages = messages,
                                onSelectThread = viewModel::selectThread,
                                onCreateThread = viewModel::createNewThread,
                                onRenameThread = viewModel::renameCurrentThread,
                                onToggleArchive = viewModel::toggleArchiveThread,
                                onToggleShowArchived = viewModel::setShowArchivedThreads,
                                onSendPrompt = viewModel::submitUserPrompt,
                                onApproveCheckpoint = viewModel::approveTaskCheckpoint,
                                onRejectCheckpoint = viewModel::rejectTaskCheckpoint,
                                onOpenBrowser = { viewModel.navigateTo(OpenMuseDestination.BROWSER) },
                                onOpenWorkspace = { viewModel.navigateTo(OpenMuseDestination.WORKSPACE) },
                                isDarkTheme = isDarkTheme,
                                onAssignThreadGroup = viewModel::assignThreadGroup,
                                onCopyText = viewModel::copyTextToClipboard,
                                onShareText = viewModel::shareTextOrFile,
                                onDownloadSnippet = viewModel::downloadSnippetToWorkspaceAndDevice
                            )

                            OpenMuseDestination.TASKS -> TasksActivityScreen(
                                tasks = tasks,
                                allSteps = allSteps,
                                onPause = viewModel::pauseTask,
                                onResume = viewModel::resumeTask,
                                onCancel = viewModel::cancelTask,
                                onRetry = viewModel::retryTask,
                                onApprove = viewModel::approveTaskCheckpoint,
                                onReject = viewModel::rejectTaskCheckpoint
                            )

                            OpenMuseDestination.BROWSER -> TwoLayerBrowserScreen(
                                browserState = browserState,
                                tabs = browserTabs,
                                bookmarks = bookmarks,
                                onNavigateUrl = viewModel::navigateBrowserTo,
                                onAddTab = { viewModel.addNewBrowserTab() },
                                onCloseTab = viewModel::closeBrowserTab,
                                onToggleJs = viewModel.browserController::setJavaScriptEnabled,
                                onToggleHumanControl = viewModel.browserController::toggleHumanTakeover,
                                onBookmarkCurrent = viewModel::bookmarkCurrentPage,
                                onAutonomousInspect = viewModel::inspectCurrentBrowserPageAutonomously,
                                onRunChromiumProbe = viewModel::runPlaywrightChromiumProbe,
                                onWebViewDomCaptured = viewModel.browserController::updateFromWebViewJsBridge
                            )

                            OpenMuseDestination.WORKSPACE -> WorkspaceAndTerminalScreen(
                                files = workspaceFiles,
                                selectedFilePath = selectedFilePath,
                                selectedFileContent = selectedFileContent,
                                terminalWorkingSubdir = terminalWorkingSubdir,
                                receipts = receipts,
                                pendingConfirmation = pendingConfirmation,
                                lastGitOutput = lastGitOutput,
                                onOpenFile = viewModel::openWorkspaceFile,
                                onSaveFile = viewModel::saveWorkspaceFile,
                                onDeleteFile = viewModel::deleteWorkspaceFile,
                                onImportSaf = viewModel::importFileFromSaf,
                                onExportSaf = viewModel::exportFileToSaf,
                                onSetWorkingSubdir = viewModel::setTerminalWorkingSubdir,
                                onRunCommand = { cmd -> viewModel.runTerminalCommand(cmd, confirmedByUser = false) },
                                onConfirmCommand = viewModel::confirmPendingTerminalCommand,
                                onDismissConfirmation = viewModel::dismissPendingTerminalCommand,
                                onClearReceipts = viewModel::clearTerminalReceipts,
                                onRunGitOp = viewModel::runGitCommand,
                                currentFolderSubdir = currentFolderSubdir,
                                workspaceAbsolutePath = prootState.workspacePath,
                                lastOpenedMap = lastOpenedMap,
                                onNavigateFolder = viewModel::navigateToWorkspaceFolder,
                                onCreateFolder = viewModel::createWorkspaceFolder,
                                onRenameItem = viewModel::renameWorkspaceItem,
                                onDownloadFileToDevice = viewModel::downloadWorkspaceFileToDevice,
                                onShareFile = viewModel::shareWorkspaceFile,
                                onCopyText = viewModel::copyTextToClipboard,
                                isDarkTheme = isDarkTheme
                            )

                            OpenMuseDestination.LINUX_ENV -> LinuxEnvironmentScreen(
                                state = prootState,
                                onInstallLocal = { viewModel.installLinuxUserspace(downloadArchive = false) },
                                onDownloadArm64Archive = { viewModel.installLinuxUserspace(downloadArchive = true) },
                                onCancelDownload = viewModel::cancelLinuxDownload,
                                onStart = viewModel::startLinuxRuntime,
                                onStop = viewModel::stopLinuxRuntime,
                                onRestart = viewModel::restartLinuxRuntime,
                                onRepair = viewModel::repairLinuxRuntime,
                                onUninstall = viewModel::uninstallLinuxRuntime
                            )

                            OpenMuseDestination.SETTINGS -> SettingsAndDiagnosticsScreen(
                                isDarkTheme = isDarkTheme,
                                geminiKeyStatus = geminiKeyStatus,
                                selectedModel = selectedModel,
                                externalSyncEnabled = externalSyncEnabled,
                                serverState = serverState,
                                metrics = metrics,
                                onToggleDarkTheme = viewModel::setDarkTheme,
                                onSaveEncryptedApiKey = viewModel::saveEncryptedGeminiApiKey,
                                onClearEncryptedApiKey = viewModel::clearEncryptedGeminiApiKey,
                                onSelectModel = viewModel::selectGeminiModel,
                                onToggleExternalSync = viewModel::setExternalCopilotSync,
                                onPingLoopbackHealth = viewModel::verifyLoopbackHealthEndpoint
                            )
                        }
                    }
                }
            }
        }
    }
}
