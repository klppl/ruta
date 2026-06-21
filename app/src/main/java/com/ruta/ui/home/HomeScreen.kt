package com.ruta.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    var showControls by remember { mutableStateOf(false) }
    var editingUrl by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (editingUrl) editingUrl = false
        else if (!viewModel.onBack()) onExit()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val current = activeTab
        if (current == null || current.isNewTab) {
            ServiceLauncher(
                profiles = profiles,
                selectedProfileId = selectedProfile,
                onSelectProfile = viewModel::setSelectedProfile,
                onAddProfile = { viewModel.createProfile("Account ${profiles.size}") },
                onOpenService = viewModel::openService,
                modifier = Modifier.statusBarsPadding(),
            )
        } else {
            val webView = remember(current.id) { viewModel.engine.getOrCreateWebView(current, viewModel) }
            WebViewHost(
                webView = webView,
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            )
        }

        BottomBar(
            title = current?.title.orEmpty(),
            url = current?.url.orEmpty(),
            progress = current?.progress ?: 0,
            accent = accentForProfile(current?.profileId ?: ""),
            tabCount = tabs.size,
            onPillClick = { editingUrl = true },
            onTabs = { viewModel.setShowTabSwitcher(true) },
            onMenu = { showControls = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = showSwitcher,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
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
}

@Composable
private fun BottomBar(
    title: String,
    url: String,
    progress: Int,
    accent: androidx.compose.ui.graphics.Color,
    tabCount: Int,
    onPillClick: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddressPill(
            title = title,
            url = url,
            progress = progress,
            accent = accent,
            onClick = onPillClick,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = onTabs, modifier = Modifier.padding(start = 8.dp)) {
            TabCountIcon(tabCount)
        }
        FilledTonalIconButton(onClick = onMenu, modifier = Modifier.padding(start = 4.dp)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
        }
    }
}

@Composable
private fun TabCountIcon(count: Int) {
    if (count in 1..99) {
        Box(
            modifier = Modifier
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(count.toString(), style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Icon(Icons.Rounded.Dashboard, contentDescription = "Tabs")
    }
}
