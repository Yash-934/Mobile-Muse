package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.browser.BrowserUiState
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserTabEntity
import com.example.data.local.CommandReceiptEntity
import com.example.proot.WorkspaceFileItem
import com.example.ui.PendingShellConfirmation
import com.example.ui.theme.AmberCheckpoint
import com.example.ui.theme.EmeraldHealthy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseError

/**
 * Full-screen Modern Browser (Brave / Microsoft Edge Mobile style)
 * - Compact single-row top Omnibox with Shield/Agent Inspector button, lock/address pill,
 *   Tab Counter badge [N], and overflow menu (⋮).
 * - Full-bleed edge-to-edge WebView occupying 100% of the viewport below the Omnibox.
 * - Clean modal bottom sheets for Tabs, Shields & Autonomous Layer B DOM Inspector / PRoot Probe,
 *   and Bookmarks/History.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TwoLayerBrowserScreen(
    browserState: BrowserUiState,
    tabs: List<BrowserTabEntity>,
    bookmarks: List<BookmarkEntity>,
    onNavigateUrl: (String) -> Unit,
    onAddTab: () -> Unit,
    onCloseTab: (Long) -> Unit,
    onToggleJs: (Boolean) -> Unit,
    onToggleHumanControl: (Boolean) -> Unit,
    onBookmarkCurrent: () -> Unit,
    onAutonomousInspect: (String) -> Unit,
    onRunChromiumProbe: () -> Unit,
    onWebViewDomCaptured: (String, String, String, Int) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var urlInput by remember(browserState.currentUrl) { mutableStateOf(browserState.currentUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Modal sheets & menus state
    var showTabsSheet by remember { mutableStateOf(false) }
    var showShieldsAndAgentSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("browser_screen")
    ) {
        // Modern Brave / Edge Style Top Omnibox Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Back button
                    IconButton(
                        onClick = {
                            if (webViewRef?.canGoBack() == true) webViewRef?.goBack()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Brave Shield / Autonomous Agent Inspector Trigger
                    IconButton(
                        onClick = { showShieldsAndAgentSheet = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (browserState.isAgentControlActive) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .testTag("browser_tab_layer_b")
                    ) {
                        Icon(
                            imageVector = if (browserState.isAgentControlActive) Icons.Default.SmartToy else Icons.Default.Security,
                            contentDescription = "Shields & Autonomous Inspector",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Pill-shaped Omnibox Address & Search Bar
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                if (browserState.isAgentControlActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (urlInput.startsWith("https")) Icons.Default.Lock else Icons.Default.Search,
                            contentDescription = null,
                            tint = if (urlInput.startsWith("https")) EmeraldHealthy else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    focusManager.clearFocus()
                                    onNavigateUrl(urlInput)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("browser_url_input")
                        )
                        if (urlInput.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear URL",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { urlInput = "" }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "Go",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    focusManager.clearFocus()
                                    onNavigateUrl(urlInput)
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("browser_go_button")
                        )
                    }

                    // Reload button
                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reload Page",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Edge / Brave Style Square Tab Counter Badge [N]
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
                            .clickable { showTabsSheet = true }
                            .testTag("browser_new_tab_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${tabs.size.coerceAtLeast(1)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Overflow Menu (⋮)
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("browser_more_menu_button")
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Browser Menu",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Tab") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onAddTab()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bookmark This Page") },
                                leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onBookmarkCurrent()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bookmarks & History (${bookmarks.size})") },
                                leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showBookmarksSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (browserState.isAgentControlActive) "Switch to Human Mode"
                                        else "Enable Agent Takeover"
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleHumanControl(!browserState.isAgentControlActive)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Shields, DOM Inspector & PRoot Probe") },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showShieldsAndAgentSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Forward") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    if (webViewRef?.canGoForward() == true) webViewRef?.goForward()
                                }
                            )
                        }
                    }
                }

                // Thin 2dp Loading Progress Bar or Agent Control Banner
                if (browserState.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                } else {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }

                if (browserState.isAgentControlActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🤖 Autonomous Agent Mode Active • Untrusted DOM Guard ON",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Take Control",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onToggleHumanControl(false) }
                                .testTag("browser_takeover_toggle")
                        )
                    }
                }
            }
        }

        // Full-Screen Edge-to-Edge WebView Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        settings.javaScriptEnabled = browserState.javaScriptEnabled
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val loadedUrl = url ?: browserState.currentUrl
                                val loadedTitle = view?.title ?: "Web Page"
                                if (browserState.javaScriptEnabled) {
                                    view?.evaluateJavascript(
                                        "(function(){ return document.querySelectorAll('a,button,input').length + '|' + (document.body ? document.body.innerText.substring(0, 1500) : ''); })();"
                                    ) { raw ->
                                        val unquoted = raw?.trim('"')?.replace("\\n", "\n") ?: ""
                                        val count = unquoted.substringBefore("|").toIntOrNull() ?: 0
                                        val bodyText = unquoted.substringAfter("|", "")
                                        onWebViewDomCaptured(loadedUrl, loadedTitle, bodyText, count)
                                    }
                                } else {
                                    onWebViewDomCaptured(loadedUrl, loadedTitle, "JavaScript disabled by user.", 0)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                            }
                        }
                        loadUrl(browserState.currentUrl)
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    webView.settings.javaScriptEnabled = browserState.javaScriptEnabled
                    if (webView.url != browserState.currentUrl) {
                        webView.loadUrl(browserState.currentUrl)
                    }
                }
            )
        }
    }

    // --- Modal Bottom Sheet 1: Brave/Edge Style Tabs Switcher ---
    if (showTabsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTabsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Open Tabs (${tabs.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Button(
                        onClick = {
                            onAddTab()
                            showTabsSheet = false
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Tab")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    urlInput = tab.url
                                    onNavigateUrl(tab.url)
                                    showTabsSheet = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (tab.id == browserState.activeTabId) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tab.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (tabs.size > 1) {
                                    IconButton(onClick = { onCloseTab(tab.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close Tab")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // --- Modal Bottom Sheet 2: Brave Shields, Autonomous DOM Inspector & PRoot Chromium Probe ---
    if (showShieldsAndAgentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShieldsAndAgentSheet = false }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Browser Shields & Autonomous Agent Layer",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Site: ${browserState.currentUrl}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("JavaScript Execution", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Local file:// & content:// access strictly blocked",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldHealthy
                                    )
                                }
                                Switch(
                                    checked = browserState.javaScriptEnabled,
                                    onCheckedChange = onToggleJs,
                                    modifier = Modifier.testTag("browser_js_switch")
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Autonomous Agent Control", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Allow agent to inspect DOM or switch to manual human control",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Switch(
                                    checked = browserState.isAgentControlActive,
                                    onCheckedChange = onToggleHumanControl
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Layer B: Autonomous DOM Snapshot (Untrusted Input Guard)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { onAutonomousInspect(urlInput) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("browser_inspect_dom_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Capture Untrusted DOM Snapshot")
                            }

                            val dom = browserState.lastDomInspection
                            if (dom != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Snapshot: ${dom.title} (HTTP ${dom.statusCode})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldHealthy
                                )
                                dom.interactiveElementsSummary.take(5).forEach { elem ->
                                    Text(text = "• $elem", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dom.sanitizedUntrustedText.take(600),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    val probe = browserState.chromiumProbeReport
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "PRoot ARM64 Chromium / Playwright Probe",
                                style = MaterialTheme.typography.titleMedium,
                                color = AmberCheckpoint
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Chromium ELF: ${probe.chromiumBinaryFound} | Playwright: ${probe.playwrightCliFound} | /dev/shm: ${probe.devShmAvailable}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "Active Driver: ${probe.fallbackDriverStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldHealthy
                            )
                            if (probe.rawProbeOutput.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = probe.rawProbeOutput, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onRunChromiumProbe,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("run_chromium_probe_button")
                            ) {
                                Text("Run Real PRoot Chromium Probe")
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // --- Modal Bottom Sheet 3: Bookmarks & History ---
    if (showBookmarksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarksSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bookmarks & Browsing History", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = onBookmarkCurrent) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Current")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookmarks, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    urlInput = item.url
                                    onNavigateUrl(item.url)
                                    showBookmarksSheet = false
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = (if (item.isHistoryEntry) "[History] " else "★ ") + item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun WorkspaceAndTerminalScreen(
    files: List<WorkspaceFileItem>,
    selectedFilePath: String,
    selectedFileContent: String,
    terminalWorkingSubdir: String,
    receipts: List<CommandReceiptEntity>,
    pendingConfirmation: PendingShellConfirmation?,
    lastGitOutput: String,
    onOpenFile: (String) -> Unit,
    onSaveFile: (String, String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onImportSaf: (android.content.Context, android.net.Uri, String) -> Unit,
    onExportSaf: (android.content.Context, String, android.net.Uri) -> Unit,
    onSetWorkingSubdir: (String) -> Unit,
    onRunCommand: (String) -> Unit,
    onConfirmCommand: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onClearReceipts: () -> Unit,
    onRunGitOp: (String, String, Boolean) -> Unit,
    currentFolderSubdir: String = "",
    workspaceAbsolutePath: String = "/data/user/0/com.aistudio.openmuse/files/workspace",
    lastOpenedMap: Map<String, Long> = emptyMap(),
    onNavigateFolder: (String) -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onRenameItem: (String, String) -> Unit = { _, _ -> },
    onDownloadFileToDevice: (android.content.Context, String) -> Unit = { _, _ -> },
    onShareFile: (android.content.Context, String) -> Unit = { _, _ -> },
    onCopyText: (android.content.Context, String, String) -> Unit = { _, _, _ -> },
    isDarkTheme: Boolean = false
) {
    val context = LocalContext.current
    var activeSubTab by remember { mutableIntStateOf(1) } // Default to Files & Folder Manager (1) for instant file manager experience
    var filesInnerTab by remember { mutableIntStateOf(0) } // 0 = Files (N), 1 = Recent (N), 2 = Workspace
    var commandInput by remember { mutableStateOf("uname -a && ls -la") }
    var filePathInput by remember(selectedFilePath) { mutableStateOf(selectedFilePath) }
    var fileContentInput by remember(selectedFileContent) { mutableStateOf(selectedFileContent) }
    var gitCommitMessage by remember { mutableStateOf("Update workspace files") }

    // Full-Screen File Viewer & Editor screen state (opens when tapping any file or "+ New File")
    var isFullScreenFileViewerOpen by remember { mutableStateOf(false) }
    var isPreviewModeInFullScreen by remember { mutableStateOf(false) }

    // Folder Management System state
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("NAME") } // NAME, SIZE, DATE
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    var itemToRename by remember { mutableStateOf<WorkspaceFileItem?>(null) }
    var renameInput by remember { mutableStateOf("") }

    if (isFullScreenFileViewerOpen) {
        BackHandler {
            isFullScreenFileViewerOpen = false
        }
    } else if (activeSubTab == 1 && currentFolderSubdir.isNotBlank()) {
        BackHandler {
            val parent = currentFolderSubdir.substringBeforeLast("/", "")
            onNavigateFolder(parent)
        }
    }

    val safImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImportSaf(context, uri, "imported_${System.currentTimeMillis() % 10000}.txt")
        }
    }

    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            onExportSaf(context, filePathInput, uri)
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderNameInput,
                    onValueChange = { newFolderNameInput = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderNameInput.isNotBlank()) {
                            onCreateFolder(newFolderNameInput.trim())
                            newFolderNameInput = ""
                            showNewFolderDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (itemToRename != null) {
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename '${itemToRename?.name}'") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = itemToRename
                        if (target != null && renameInput.isNotBlank()) {
                            onRenameItem(target.relativePath, renameInput.trim())
                            itemToRename = null
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingConfirmation != null) {
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AmberCheckpoint) },
            title = { Text("Confirm Consequential Action") },
            text = {
                Column {
                    Text(pendingConfirmation.reason)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Command: ${pendingConfirmation.rawCommand}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "Security Note: PRoot/POSIX executes under your app UID and is not a VM boundary.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmCommand,
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                    modifier = Modifier.testTag("confirm_destructive_command_button")
                ) {
                    Text("Execute Anyway", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissConfirmation) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Dedicated Full-Screen File Viewer & Editor Screen when a file is clicked ---
    if (isFullScreenFileViewerOpen) {
        val extBadge = filePathInput.substringAfterLast('.', "TXT").uppercase().take(4)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp)
                .testTag("fullscreen_file_viewer_screen")
        ) {
            // Top Header Bar with Back button, File Badge, Title, and Preview/Edit Toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = { isFullScreenFileViewerOpen = false },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Folder Manager",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFDE8D7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = extBadge,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFD97736)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = filePathInput.substringAfterLast("/").ifBlank { "Untitled File" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Workspace / $filePathInput • ${fileContentInput.toByteArray().size} bytes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Preview vs Edit Source Toggle Button
                        OutlinedButton(
                            onClick = { isPreviewModeInFullScreen = !isPreviewModeInFullScreen },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isPreviewModeInFullScreen) Icons.Default.Edit else Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPreviewModeInFullScreen) "Edit Code" else "Preview MD")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Editable File Path Input + Save Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = filePathInput,
                            onValueChange = { filePathInput = it },
                            label = { Text("Workspace File Path") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("workspace_file_path_input"),
                            singleLine = true
                        )
                        Button(
                            onClick = { onSaveFile(filePathInput, fileContentInput) },
                            modifier = Modifier.testTag("workspace_save_file_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Full File Actions Bar: Download to Device | Share | Copy | SAF Save As | Delete
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onSaveFile(filePathInput, fileContentInput)
                                onDownloadFileToDevice(context, filePathInput)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97736)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Download to Device", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download to Device", color = Color.White, maxLines = 1, softWrap = false)
                        }

                        OutlinedButton(
                            onClick = {
                                onSaveFile(filePathInput, fileContentInput)
                                onShareFile(context, filePathInput)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share File", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share File", maxLines = 1, softWrap = false)
                        }

                        OutlinedButton(
                            onClick = {
                                onCopyText(context, filePathInput, fileContentInput)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy File Content", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy All", maxLines = 1, softWrap = false)
                        }

                        OutlinedButton(
                            onClick = { safExportLauncher.launch(filePathInput.substringAfterLast("/")) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Save As to Device Storage", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save As (SAF)", maxLines = 1, softWrap = false)
                        }

                        OutlinedButton(
                            onClick = {
                                onDeleteFile(filePathInput)
                                isFullScreenFileViewerOpen = false
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete File", tint = RoseError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", color = RoseError, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Full-Screen Content Area (either Rich Markdown/Textbook Formula Preview or Full-Screen Code Editor)
            if (isPreviewModeInFullScreen) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        com.example.ui.components.MuseMarkdownAndMathView(
                            content = fileContentInput,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            isDark = isDarkTheme,
                            onCopyText = { text -> onCopyText(context, "Snippet", text) },
                            onDownloadCodeSnippet = { name, code ->
                                onSaveFile(name, code)
                                onDownloadFileToDevice(context, name)
                            }
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = fileContentInput,
                    onValueChange = { fileContentInput = it },
                    label = { Text("Full-Screen File Editor ($filePathInput)") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("workspace_file_content_input"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
            }
        }
        return
    }

    val filteredSortedFiles = remember(files, searchQuery, sortBy) {
        val q = searchQuery.trim().lowercase()
        val matching = if (q.isEmpty()) files else files.filter {
            it.name.lowercase().contains(q) || it.fileTypeBadge.lowercase().contains(q)
        }
        when (sortBy) {
            "SIZE" -> matching.sortedWith(compareBy<WorkspaceFileItem> { !it.isDirectory }.thenByDescending { it.sizeBytes })
            "DATE" -> matching.sortedWith(compareBy<WorkspaceFileItem> { !it.isDirectory }.thenByDescending { it.lastModified })
            else -> matching.sortedWith(compareBy<WorkspaceFileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    val recentFiles = remember(files, lastOpenedMap) {
        files.filter { !it.isDirectory }
            .sortedByDescending { lastOpenedMap[it.relativePath] ?: it.lastModified }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
            .testTag("workspace_screen")
    ) {
        TabRow(selectedTabIndex = activeSubTab) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("Terminal (${receipts.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("workspace_tab_terminal")
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("Files (${files.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("workspace_tab_files")
            )
            Tab(
                selected = activeSubTab == 2,
                onClick = { activeSubTab = 2 },
                text = { Text("Git Control", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("workspace_tab_git")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (activeSubTab) {
            0 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = terminalWorkingSubdir,
                        onValueChange = onSetWorkingSubdir,
                        label = { Text("Subdir (inside isolated workspace)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedButton(onClick = onClearReceipts) {
                        Text("Clear Log")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        label = { Text("POSIX / Linux Shell Command") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("terminal_command_input"),
                        singleLine = true
                    )
                    Button(
                        onClick = { onRunCommand(commandInput) },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("terminal_execute_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Execute Command")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("terminal_receipts_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(receipts, key = { it.id }) { r ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (r.exitCode == 0) EmeraldHealthy.copy(alpha = 0.4f) else RoseError.copy(alpha = 0.6f),
                                    RoundedCornerShape(10.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "$ ${r.commandDisplay}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    Text(
                                        text = "exit=${r.exitCode} • ${r.durationMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = if (r.exitCode == 0) EmeraldHealthy else RoseError
                                    )
                                }
                                if (r.stdout.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = r.stdout.trim(),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                                    )
                                }
                                if (r.stderr.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = r.stderr.trim(),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = RoseError
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // --- Muse Folder Management System (Full-Screen File Manager like Screenshots 5, 6, 7) ---
                // Sub-Header Tabs: Files (N) | Recent (N) | Workspace
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MuseFilesSubTabButton(
                            label = "Files (${files.size})",
                            selected = filesInnerTab == 0,
                            onClick = { filesInnerTab = 0 }
                        )
                        MuseFilesSubTabButton(
                            label = "Recent (${recentFiles.size})",
                            selected = filesInnerTab == 1,
                            onClick = { filesInnerTab = 1 }
                        )
                        MuseFilesSubTabButton(
                            label = "Workspace",
                            selected = filesInnerTab == 2,
                            onClick = { filesInnerTab = 2 }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = { showNewFolderDialog = true },
                            label = { Text("+ Folder", maxLines = 1, softWrap = false) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = "New Folder", modifier = Modifier.size(15.dp)) }
                        )
                        AssistChip(
                            onClick = {
                                val newPath = if (currentFolderSubdir.isBlank()) "new_note.md" else "$currentFolderSubdir/new_note.md"
                                filePathInput = newPath
                                fileContentInput = "# New Note\n\nWrite your markdown, formulas, or code here.\n"
                                isPreviewModeInFullScreen = false
                                isFullScreenFileViewerOpen = true
                            },
                            label = { Text("+ File", maxLines = 1, softWrap = false) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = "New File", modifier = Modifier.size(15.dp)) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))

                // Breadcrumb Path + Search & Sort Controls (when in Files view)
                if (filesInnerTab == 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (currentFolderSubdir.isNotBlank()) {
                            AssistChip(
                                onClick = {
                                    val parent = currentFolderSubdir.substringBeforeLast("/", "")
                                    onNavigateFolder(parent)
                                },
                                label = { Text("← Up", fontWeight = FontWeight.Bold) }
                            )
                        }
                        AssistChip(
                            onClick = { onNavigateFolder("") },
                            label = { Text("Workspace Root", fontWeight = if (currentFolderSubdir.isBlank()) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        )
                        if (currentFolderSubdir.isNotBlank()) {
                            val segments = currentFolderSubdir.split("/").filter { it.isNotBlank() }
                            segments.forEachIndexed { idx, seg ->
                                val targetSub = segments.take(idx + 1).joinToString("/")
                                Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AssistChip(
                                    onClick = { onNavigateFolder(targetSub) },
                                    label = { Text(seg, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        listOf("NAME" to "A-Z", "SIZE" to "Size", "DATE" to "Recent").forEach { (key, label) ->
                            AssistChip(
                                onClick = { sortBy = key },
                                label = {
                                    Text(
                                        text = if (sortBy == key) "✓ $label" else label,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            )
                        }
                        AssistChip(
                            onClick = { safImportLauncher.launch("*/*") },
                            label = { Text("Import Device File", maxLines = 1, softWrap = false) },
                            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        )
                    }

                    // Search Filter Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search folders, files, or extensions (MD, SH, PY)...", maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear Search", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // --- Full-Height File Manager List (Tapping a folder enters it; tapping a file opens it on a Full-Screen Viewer/Editor!) ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (filesInnerTab) {
                        0 -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredSortedFiles, key = { it.relativePath }) { item ->
                                    MuseFileRowItem(
                                        item = item,
                                        subtitle = if (item.isDirectory) {
                                            "Folder  •  Tap to open folder"
                                        } else {
                                            "${formatFileSize(item.sizeBytes)}  •  ${formatDateShort(item.lastModified)}  •  Tap for Full Screen"
                                        },
                                        isSelected = !item.isDirectory && item.relativePath == filePathInput,
                                        onClick = {
                                            if (item.isDirectory) {
                                                onNavigateFolder(item.relativePath)
                                            } else {
                                                filePathInput = item.relativePath
                                                onOpenFile(item.relativePath)
                                                isPreviewModeInFullScreen = item.fileTypeBadge == "MD"
                                                isFullScreenFileViewerOpen = true
                                            }
                                        },
                                        onDownload = {
                                            if (!item.isDirectory) {
                                                onDownloadFileToDevice(context, item.relativePath)
                                            }
                                        },
                                        onShare = {
                                            if (!item.isDirectory) {
                                                onShareFile(context, item.relativePath)
                                            }
                                        },
                                        onRename = {
                                            renameInput = item.name
                                            itemToRename = item
                                        },
                                        onDelete = { onDeleteFile(item.relativePath) }
                                    )
                                }
                            }
                        }

                        1 -> {
                            // Recent tab matching Screenshot 6 ("84B • Opened 2m ago")
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(recentFiles, key = { it.relativePath }) { item ->
                                    val openedAt = lastOpenedMap[item.relativePath] ?: item.lastModified
                                    val minsAgo = ((System.currentTimeMillis() - openedAt) / 60_000L).coerceAtLeast(1L)
                                    MuseFileRowItem(
                                        item = item,
                                        subtitle = "${formatFileSize(item.sizeBytes)}  •  Opened ${minsAgo}m ago  •  Tap for Full Screen",
                                        isSelected = item.relativePath == filePathInput,
                                        onClick = {
                                            filePathInput = item.relativePath
                                            onOpenFile(item.relativePath)
                                            isPreviewModeInFullScreen = item.fileTypeBadge == "MD"
                                            isFullScreenFileViewerOpen = true
                                        },
                                        onDownload = { onDownloadFileToDevice(context, item.relativePath) },
                                        onShare = { onShareFile(context, item.relativePath) },
                                        onRename = {
                                            renameInput = item.name
                                            itemToRename = item
                                        },
                                        onDelete = { onDeleteFile(item.relativePath) }
                                    )
                                }
                            }
                        }

                        2 -> {
                            // Agent Workspace Dedicated View matching Screenshot 7
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(18.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(Color(0xFFFDE8D7)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = "Agent Workspace Folder",
                                                    tint = Color(0xFFD97736),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "Agent Workspace",
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Agent's dedicated folder for creating and editing files",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = workspaceAbsolutePath,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = { filesInnerTab = 0 },
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Open in Files (${files.size})")
                                            }
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text(
                                                text = "Quick Create & Open Full Screen",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                listOf(
                                                    "notes.md" to "# Muse Notes\n\n- Created in Agent Workspace\n",
                                                    "todo.txt" to "[ ] Review tasks\n[ ] Run Linux verification\n",
                                                    "ideas.md" to "# Project Ideas\n\n1. Autonomous mobile workflows\n",
                                                    "script.py" to "print('Hello from OpenMuse Workspace!')\n"
                                                ).forEach { (quickName, starterText) ->
                                                    OutlinedButton(
                                                        onClick = {
                                                            filePathInput = quickName
                                                            fileContentInput = starterText
                                                            onSaveFile(quickName, starterText)
                                                            isPreviewModeInFullScreen = false
                                                            isFullScreenFileViewerOpen = true
                                                        },
                                                        shape = RoundedCornerShape(12.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(quickName, maxLines = 1, softWrap = false)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { onRunGitOp("init", "", true) }, modifier = Modifier.testTag("git_init_button")) {
                        Text("git init")
                    }
                    Button(onClick = { onRunGitOp("status", "", true) }, modifier = Modifier.testTag("git_status_button")) {
                        Text("git status")
                    }
                    OutlinedButton(onClick = { onRunGitOp("diff", "", true) }) {
                        Text("git diff")
                    }
                    OutlinedButton(onClick = { onRunGitOp("branch", "", true) }) {
                        Text("git branch")
                    }
                    OutlinedButton(onClick = { onRunGitOp("log", "", true) }, modifier = Modifier.testTag("git_log_button")) {
                        Text("git log")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = gitCommitMessage,
                        onValueChange = { gitCommitMessage = it },
                        label = { Text("Commit Message") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = { onRunGitOp("commit", gitCommitMessage, true) },
                        modifier = Modifier.testTag("git_commit_button")
                    ) {
                        Text("Commit")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Workspace Git Receipt Output",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = lastGitOutput,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MuseFilesSubTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(if (selected) 48.dp else 0.dp)
                .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(2.dp))
        )
    }
}

/**
 * Matches Screenshot 5 & 6 file rows:
 * - Left rounded square badge with color-coded extension ("MD" in warm orange, "SH" in green, "PY" in blue, Folder icon for directories)
 * - Bold filename + muted size & date subtitle
 * - Direct one-tap Download to Device, Share, Rename, and Delete buttons on the right
 */
@Composable
private fun MuseFileRowItem(
    item: WorkspaceFileItem,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit = {},
    onShare: () -> Unit = {},
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val badgeBg = when {
        item.isDirectory -> Color(0xFFFEF3C7)
        item.fileTypeBadge == "MD" -> Color(0xFFFDE8D7)
        item.fileTypeBadge == "SH" -> Color(0xFFDCFCE7)
        item.fileTypeBadge == "PY" || item.fileTypeBadge == "KT" -> Color(0xFFDBEAFE)
        item.fileTypeBadge == "HTML" || item.fileTypeBadge == "JS" -> Color(0xFFE0E7FF)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeFg = when {
        item.isDirectory -> Color(0xFFB45309)
        item.fileTypeBadge == "MD" -> Color(0xFFD97736)
        item.fileTypeBadge == "SH" -> Color(0xFF15803D)
        item.fileTypeBadge == "PY" || item.fileTypeBadge == "KT" -> Color(0xFF1D4ED8)
        item.fileTypeBadge == "HTML" || item.fileTypeBadge == "JS" -> Color(0xFF4338CA)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("file_row_${item.relativePath}"),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isDirectory) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Folder",
                            tint = badgeFg,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = item.fileTypeBadge.take(4),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = badgeFg,
                            maxLines = 1
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (!item.isDirectory) {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Download ${item.name} to Device",
                            tint = Color(0xFFD97736),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share ${item.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename ${item.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete ${item.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "${bytes}B"
        bytes < 1024L * 1024L -> "${bytes / 1024L}KB"
        else -> String.format(java.util.Locale.US, "%.1fMB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatDateShort(epochMs: Long): String {
    if (epochMs <= 0L) return "Today"
    return java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(epochMs))
}

