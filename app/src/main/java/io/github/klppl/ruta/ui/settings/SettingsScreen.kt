package io.github.klppl.ruta.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.klppl.ruta.profile.Profile
import io.github.klppl.ruta.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val globalCss by viewModel.globalCss.collectAsStateWithLifecycle()
    val status by viewModel.blocklistStatus.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val filterLists by viewModel.filterLists.collectAsStateWithLifecycle()
    val linkHandlers by viewModel.linkHandlers.collectAsStateWithLifecycle()
    var showAddFilterList by remember { mutableStateOf(false) }

    // Re-read handler state when returning from the system "open by default" screen.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshLinkHandling()
        onPauseOrDispose { }
    }

    if (showAddFilterList) {
        AddFilterListDialog(
            onAdd = { name, url ->
                viewModel.addFilterList(name, url)
                showAddFilterList = false
            },
            onDismiss = { showAddFilterList = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Section("Appearance")
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            SettingSwitch("Dynamic color (Material You)", settings.dynamicColor, viewModel::setDynamicColor)
            SettingSwitch("Force dark mode on websites", settings.forceDarkWebsites, viewModel::setForceDarkWebsites)

            Section("Layout")
            SettingSwitch("Show address bar", settings.showAddressBar, viewModel::setShowAddressBar)
            Text(
                "Address bar position",
                style = MaterialTheme.typography.bodyLarge,
                color = if (settings.showAddressBar) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.addressBarAtTop,
                    enabled = settings.showAddressBar,
                    onClick = { viewModel.setAddressBarAtTop(true) },
                    label = { Text("Top") },
                )
                FilterChip(
                    selected = !settings.addressBarAtTop,
                    enabled = settings.showAddressBar,
                    onClick = { viewModel.setAddressBarAtTop(false) },
                    label = { Text("Bottom") },
                )
            }
            SettingSwitch("Auto-hide dock on scroll", settings.autoHideDock, viewModel::setAutoHideDock)

            Section("Privacy & blocking")
            SettingSwitch("Block ads & trackers", settings.adBlockEnabled, viewModel::setAdBlock)
            SettingSwitch("Cosmetic filtering (hide elements)", settings.cosmeticFilteringEnabled, viewModel::setCosmetic)
            SettingSwitch("Strip tracking params on copy", settings.stripTrackingParams, viewModel::setStripParams)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (status.ready) "${status.networkRules} block rules loaded" else "Filter lists not loaded yet",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (status.error != null) {
                        Text(
                            "Last error: ${status.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Button(onClick = viewModel::refreshLists, enabled = !status.refreshing) {
                    Text(if (status.refreshing) "Updating…" else "Update")
                }
            }

            Text(
                "Filter lists",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            filterLists.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            entry.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    if (!entry.builtIn) {
                        IconButton(onClick = { viewModel.removeFilterList(entry.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove list")
                        }
                    }
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = { viewModel.setFilterListEnabled(entry.id, it) },
                    )
                }
            }
            TextButton(onClick = { showAddFilterList = true }) { Text("+ Add filter list") }

            Section("Security")
            SettingSwitch("Lock ruta behind fingerprint / screen lock", settings.appLock, viewModel::setAppLock)
            Text(
                "Asks for your fingerprint (or device PIN) when ruta is opened or brought back " +
                    "from the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("Browsing")
            SettingSwitch("Pull down to refresh", settings.pullToRefresh, viewModel::setPullToRefresh)
            SettingSwitch("Open external links in your browser", settings.openLinksExternally, viewModel::setOpenLinksExternally)
            SettingSwitch("Separate profile per site", settings.separateProfilePerSite, viewModel::setPerSiteProfile)
            SettingSwitch("Double-tap back to exit", settings.doubleBackToExit, viewModel::setDoubleBack)

            Section("Open links in ruta")
            Text(
                "Pick which sites ruta offers to open when you tap their links in other apps. " +
                    "Disabling one removes it from Android's \"supported web addresses\" list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            viewModel.linkServices.forEach { service ->
                SettingSwitch(
                    title = service.name,
                    checked = linkHandlers[service.id] != false,
                    onCheckedChange = { viewModel.setLinkHandling(service.id, it) },
                )
            }
            TextButton(onClick = { viewModel.openLinkSystemSettings() }) {
                Text("Open Android link settings")
            }
            Text(
                "Android still asks you to allow ruta to open these links the first time — use the " +
                    "button above, then turn on \"Open supported links\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("Profiles")
            if (!viewModel.multiProfileSupported) {
                Text(
                    "This WebView doesn't support multiple profiles; all tabs share one account store.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var profileToClear by remember { mutableStateOf<Profile?>(null) }
            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    onClearData = { profileToClear = profile },
                    onDelete = { viewModel.deleteProfile(profile.id) },
                )
            }
            profileToClear?.let { profile ->
                AlertDialog(
                    onDismissRequest = { profileToClear = null },
                    title = { Text("Clear ${profile.name}'s data?") },
                    text = { Text("All cookies and site data in this profile are deleted — you'll be signed out of every site it holds. The profile itself is kept.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.clearProfileData(profile.id)
                            profileToClear = null
                        }) { Text("Clear") }
                    },
                    dismissButton = { TextButton(onClick = { profileToClear = null }) { Text("Cancel") } },
                )
            }

            Section("Custom CSS")
            var cssDraft by remember(globalCss) { mutableStateOf(globalCss) }
            OutlinedTextField(
                value = cssDraft,
                onValueChange = { cssDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Applied to every page") },
                minLines = 3,
            )
            TextButton(onClick = { viewModel.setGlobalCss(cssDraft) }) { Text("Save CSS") }

            Section("Proxy")
            SettingSwitch("Route traffic through proxy", settings.proxyEnabled, viewModel::setProxyEnabled)
            var proxyDraft by remember(settings.proxyUrl) { mutableStateOf(settings.proxyUrl) }
            OutlinedTextField(
                value = proxyDraft,
                onValueChange = { proxyDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("host:port or scheme://host:port") },
                singleLine = true,
            )
            TextButton(onClick = { viewModel.setProxyUrl(proxyDraft) }) { Text("Save proxy") }

            Section("Backup")
            Text(
                "Sites, dashboard order, filter lists and settings. Logins and cookies can't be " +
                    "exported — you'll sign in again after a restore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri -> uri?.let(viewModel::exportSettings) }
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(viewModel::importSettings) }
            Row {
                TextButton(onClick = { exportLauncher.launch("ruta-backup.json") }) { Text("Export…") }
                TextButton(onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text("Import…") }
            }

            Section("About")
            TextButton(onClick = onOpenAbout) { Text("Licenses & attribution") }
            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProfileRow(profile: Profile, onClearData: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(profile.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onClearData) { Text("Clear data") }
        if (!profile.isDefault) {
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun AddFilterListDialog(onAdd: (name: String, url: String) -> Unit, onDismiss: () -> Unit) {
    var url by remember {
        mutableStateOf(TextFieldValue("https://", selection = TextRange("https://".length)))
    }
    var name by remember { mutableStateOf("") }
    val hasHost = url.text.trim().removePrefix("https://").removePrefix("http://").isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add filter list") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("List URL (ABP or hosts format)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, url.text) }, enabled = hasHost) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
