package io.github.klppl.ruta.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.klppl.ruta.model.AppService
import io.github.klppl.ruta.ui.components.AddressPill
import io.github.klppl.ruta.ui.components.ContextSheet
import io.github.klppl.ruta.ui.components.ControlsSheet
import io.github.klppl.ruta.ui.tabswitcher.TabSwitcher
import io.github.klppl.ruta.ui.theme.accentForProfile
import io.github.klppl.ruta.util.Hosts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: BrowserViewModel,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val showSwitcher by viewModel.showTabSwitcher.collectAsStateWithLifecycle()
    val contextTarget by viewModel.contextTarget.collectAsStateWithLifecycle()
    val blockStatus by viewModel.blocklistStatus.collectAsStateWithLifecycle()
    val dockVisible by viewModel.dockVisible.collectAsStateWithLifecycle()
    val dashboardServices by viewModel.dashboardServices.collectAsStateWithLifecycle()
    val addableServices by viewModel.addableServices.collectAsStateWithLifecycle()
    val blockStats by viewModel.blockStats.collectAsStateWithLifecycle()
    val findState by viewModel.findState.collectAsStateWithLifecycle()
    val dockTabs = remember(tabs) { tabs.filterNot { it.isNewTab } }

    var showControls by remember { mutableStateOf(false) }
    var editingUrl by remember { mutableStateOf(false) }
    var showAddCustom by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var bookmarkToRemove by remember { mutableStateOf<AppService?>(null) }
    var bookmarkToEdit by remember { mutableStateOf<AppService?>(null) }
    var dockEditMode by remember { mutableStateOf(false) }
    var gridEditMode by remember { mutableStateOf(false) }
    var showClearSiteData by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (dockEditMode) dockEditMode = false
        else if (gridEditMode) gridEditMode = false
        else if (editingUrl) editingUrl = false
        else if (findState != null) viewModel.closeFind()
        else if (!viewModel.onBack()) onExit()
    }

    val current = activeTab
    val accent = accentForProfile(current?.profileId ?: "")
    val topBar = settings.showAddressBar && settings.addressBarAtTop
    val bottomBar = settings.showAddressBar && !settings.addressBarAtTop

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Optional address bar at the top.
            if (topBar) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    AddressBarRow(
                        title = current?.title.orEmpty(),
                        url = current?.url.orEmpty(),
                        progress = current?.progress ?: 0,
                        accent = accent,
                        onClick = { dockEditMode = false; editingUrl = true },
                        modifier = Modifier.statusBarsPadding(),
                    )
                }
            }

            findState?.let { find ->
                FindBar(
                    state = find,
                    onQueryChange = viewModel::updateFindQuery,
                    onNext = viewModel::findNext,
                    onClose = viewModel::closeFind,
                    modifier = if (topBar) Modifier else Modifier.statusBarsPadding(),
                )
            }

            // Content sits between the bars (never behind them).
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val contentModifier = if (topBar || findState != null) Modifier else Modifier.statusBarsPadding()
                if (current == null || current.isNewTab) {
                    ServiceLauncher(
                        profiles = profiles,
                        selectedProfileId = selectedProfile,
                        onSelectProfile = viewModel::setSelectedProfile,
                        onAddProfile = { viewModel.createProfile("Account ${profiles.size}") },
                        onOpenService = { gridEditMode = false; viewModel.openService(it) },
                        onSearch = { editingUrl = true },
                        dashboardServices = dashboardServices,
                        stats = blockStats,
                        rulesLoaded = blockStatus.networkRules,
                        editMode = gridEditMode,
                        onEnterEdit = { gridEditMode = true },
                        onExitEdit = { gridEditMode = false },
                        onCommitOrder = viewModel::saveDashboardOrder,
                        onRemoveService = { bookmarkToRemove = it },
                        onEditService = { bookmarkToEdit = it },
                        onAddCustom = { showAddCustom = true },
                        onOpenCatalog = { showAddSheet = true },
                        modifier = contentModifier,
                    )
                } else {
                    val webView = remember(current.id, settings.forceDarkWebsites) {
                        viewModel.engine.getOrCreateWebView(current, viewModel)
                    }
                    WebViewHost(
                        webView = webView,
                        loading = current.progress in 1..99,
                        pullToRefreshEnabled = settings.pullToRefresh,
                        pageAtTop = { viewModel.isPageAtTop(current.id) },
                        onRefresh = { viewModel.reloadActive() },
                        modifier = Modifier.fillMaxSize().then(contentModifier),
                    )
                }
            }

            // Opaque bottom bar: collapsible dock + menu, plus the optional bottom address bar.
            // Pad by the larger of the navigation-bar and IME insets: normally it clears the nav
            // bar, but when the keyboard opens the bar grows to sit just above it. Since the
            // WebView lives in the weight(1f) slot above, it shrinks to match — so a focused page
            // input ends up above the keyboard (edge-to-edge neutralizes manifest adjustResize).
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
                ) {
                    if (!settings.showAddressBar) {
                        LoadingLine(progress = current?.progress ?: 0, accent = accent)
                    }
                    AnimatedVisibility(
                        visible = dockVisible || !settings.autoHideDock || dockEditMode,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (dockTabs.isNotEmpty()) {
                                AccountDock(
                                    tabs = dockTabs,
                                    activeId = current?.id,
                                    editMode = dockEditMode,
                                    onSelect = viewModel::selectTab,
                                    onReset = viewModel::resetToHome,
                                    onClose = viewModel::closeTab,
                                    onNewTab = { viewModel.addNewTab() },
                                    onMove = viewModel::moveTab,
                                    onEnterEdit = { dockEditMode = true },
                                    onExitEdit = { dockEditMode = false },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            FilledTonalIconButton(
                                onClick = { dockEditMode = false; showControls = true },
                                modifier = Modifier.padding(end = 10.dp),
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
                            }
                        }
                    }
                    if (bottomBar) {
                        AddressBarRow(
                            title = current?.title.orEmpty(),
                            url = current?.url.orEmpty(),
                            progress = current?.progress ?: 0,
                            accent = accent,
                            onClick = { dockEditMode = false; editingUrl = true },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = showSwitcher, enter = fadeIn(), exit = fadeOut()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                TabSwitcher(
                    tabs = tabs,
                    activeId = current?.id,
                    onSelect = viewModel::selectTab,
                    onClose = viewModel::closeTab,
                    onNewTab = { viewModel.addNewTab() },
                    modifier = Modifier.systemBarsPadding(),
                )
            }
        }

        if (editingUrl) {
            UrlEditOverlay(
                initial = current?.url.orEmpty(),
                onSubmit = {
                    viewModel.openInput(it)
                    editingUrl = false
                },
                onDismiss = { editingUrl = false },
            )
        }
    }

    if (showControls) {
        ModalBottomSheet(
            onDismissRequest = { showControls = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ControlsSheet(
                host = Hosts.hostOf(activeTab?.url),
                desktopMode = activeTab?.desktopMode == true,
                adBlockEnabled = settings.adBlockEnabled,
                blockedRules = blockStatus.networkRules,
                onNewTab = { showControls = false; viewModel.addNewTab() },
                onReload = { showControls = false; viewModel.reloadActive() },
                onCopyLink = { showControls = false; viewModel.copyCurrentUrl() },
                onToggleDesktop = { showControls = false; viewModel.toggleDesktopMode() },
                onDownloadMedia = { showControls = false; viewModel.downloadMedia() },
                onFindInPage = { showControls = false; viewModel.startFind() },
                onClearSiteData = { showControls = false; showClearSiteData = true },
                onToggleAdBlock = viewModel::setAdBlockEnabled,
                onShowTabs = { showControls = false; viewModel.setShowTabSwitcher(true) },
                onOpenSettings = { showControls = false; onOpenSettings() },
            )
        }
    }

    val ctx = contextTarget
    if (ctx != null) {
        ModalBottomSheet(onDismissRequest = viewModel::dismissContext) {
            ContextSheet(
                target = ctx,
                onOpenNewTab = { viewModel.openContextLinkInNewTab(newProfile = false) },
                onOpenNewProfile = { viewModel.openContextLinkInNewTab(newProfile = true) },
                onDownloadImage = viewModel::downloadContextImage,
                onCopyLink = viewModel::copyContextLink,
            )
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AddServiceSheet(
                addable = addableServices,
                onOpenService = {
                    showAddSheet = false
                    viewModel.openService(it)
                },
                onAddCustom = {
                    showAddSheet = false
                    showAddCustom = true
                },
            )
        }
    }

    if (showAddCustom) {
        AddCustomDialog(
            onAdd = { name, url ->
                viewModel.addBookmark(name, url)
                showAddCustom = false
            },
            onDismiss = { showAddCustom = false },
        )
    }

    bookmarkToEdit?.let { editing ->
        AddCustomDialog(
            title = "Edit site",
            confirmLabel = "Save",
            initialName = editing.name,
            initialUrl = editing.url,
            onAdd = { name, url ->
                viewModel.updateBookmark(editing.id, name, url)
                bookmarkToEdit = null
            },
            onDismiss = { bookmarkToEdit = null },
        )
    }

    if (showClearSiteData) {
        val site = Hosts.hostOf(activeTab?.url) ?: "this site"
        AlertDialog(
            onDismissRequest = { showClearSiteData = false },
            title = { Text("Clear data for $site?") },
            text = { Text("Cookies and stored data for this site are deleted and the page reloads. You'll be signed out of it. Other sites are unaffected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearActiveSiteData()
                    showClearSiteData = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearSiteData = false }) { Text("Cancel") } },
        )
    }

    bookmarkToRemove?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { bookmarkToRemove = null },
            title = { Text("Remove ${bookmark.name}?") },
            text = {
                Text(
                    if (bookmark.isCustom) "This custom site will be deleted."
                    else "It stays available under the + button.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFromDashboard(bookmark)
                    bookmarkToRemove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { bookmarkToRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddressBarRow(
    title: String,
    url: String,
    progress: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddressPill(
            title = title,
            url = url,
            progress = progress,
            accent = accent,
            onClick = onClick,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Thin loading indicator shown on the bar when the address pill (which carries its own) is hidden. */
@Composable
private fun LoadingLine(progress: Int, accent: Color) {
    if (progress in 1..99) {
        Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress / 100f)
                    .height(2.dp)
                    .background(accent),
            )
        }
    }
}
