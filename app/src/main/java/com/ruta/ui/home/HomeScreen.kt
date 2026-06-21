package com.ruta.ui.home

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
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
import com.ruta.model.AppService
import com.ruta.ui.components.AddressPill
import com.ruta.ui.components.ContextSheet
import com.ruta.ui.components.ControlsSheet
import com.ruta.ui.tabswitcher.TabSwitcher
import com.ruta.ui.theme.accentForProfile
import com.ruta.util.Hosts

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
    val customServices by viewModel.customServices.collectAsStateWithLifecycle()
    val dockTabs = remember(tabs) { tabs.filterNot { it.isNewTab } }

    var showControls by remember { mutableStateOf(false) }
    var editingUrl by remember { mutableStateOf(false) }
    var showAddCustom by remember { mutableStateOf(false) }
    var bookmarkToRemove by remember { mutableStateOf<AppService?>(null) }

    BackHandler(enabled = true) {
        if (editingUrl) editingUrl = false
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
                        onClick = { editingUrl = true },
                        modifier = Modifier.statusBarsPadding(),
                    )
                }
            }

            // Content sits between the bars (never behind them).
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val contentModifier = if (topBar) Modifier else Modifier.statusBarsPadding()
                if (current == null || current.isNewTab) {
                    ServiceLauncher(
                        profiles = profiles,
                        selectedProfileId = selectedProfile,
                        onSelectProfile = viewModel::setSelectedProfile,
                        onAddProfile = { viewModel.createProfile("Account ${profiles.size}") },
                        onOpenService = viewModel::openService,
                        onSearch = { editingUrl = true },
                        customServices = customServices,
                        onAddCustom = { showAddCustom = true },
                        onRemoveCustom = { bookmarkToRemove = it },
                        modifier = contentModifier,
                    )
                } else {
                    val webView = remember(current.id, settings.forceDarkWebsites) {
                        viewModel.engine.getOrCreateWebView(current, viewModel)
                    }
                    WebViewHost(webView = webView, modifier = Modifier.fillMaxSize().then(contentModifier))
                }
            }

            // Opaque bottom bar: collapsible dock + menu, plus the optional bottom address bar.
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                    if (!settings.showAddressBar) {
                        LoadingLine(progress = current?.progress ?: 0, accent = accent)
                    }
                    AnimatedVisibility(
                        visible = dockVisible,
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
                                    onSelect = viewModel::selectTab,
                                    onReset = viewModel::resetToHome,
                                    onClose = viewModel::closeTab,
                                    onNewTab = { viewModel.addNewTab() },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            FilledTonalIconButton(
                                onClick = { showControls = true },
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
                            onClick = { editingUrl = true },
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

    if (showAddCustom) {
        AddCustomDialog(
            onAdd = { name, url ->
                viewModel.addBookmark(name, url)
                showAddCustom = false
            },
            onDismiss = { showAddCustom = false },
        )
    }

    bookmarkToRemove?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { bookmarkToRemove = null },
            title = { Text("Remove ${bookmark.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeBookmark(bookmark.id)
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
