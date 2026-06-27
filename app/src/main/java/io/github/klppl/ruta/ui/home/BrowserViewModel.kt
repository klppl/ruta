package io.github.klppl.ruta.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klppl.ruta.browser.BrowserEngine
import io.github.klppl.ruta.browser.MediaResolver
import io.github.klppl.ruta.browser.TabEvents
import io.github.klppl.ruta.blocking.BlocklistRepository
import io.github.klppl.ruta.data.bookmarks.BookmarkRepository
import io.github.klppl.ruta.data.settings.SettingsRepository
import io.github.klppl.ruta.data.tabs.TabRepository
import io.github.klppl.ruta.model.AppService
import io.github.klppl.ruta.model.ContextTarget
import io.github.klppl.ruta.model.Services
import io.github.klppl.ruta.model.Tab
import io.github.klppl.ruta.profile.Profile
import io.github.klppl.ruta.profile.ProfileManager
import io.github.klppl.ruta.util.Hosts
import io.github.klppl.ruta.util.UrlSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class BrowserViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val engine: BrowserEngine,
    private val profileManager: ProfileManager,
    private val mediaResolver: MediaResolver,
    private val tabRepository: TabRepository,
    private val settingsRepository: SettingsRepository,
    private val bookmarkRepository: BookmarkRepository,
    blocklistRepository: BlocklistRepository,
) : ViewModel(), TabEvents {

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _contextTarget = MutableStateFlow<ContextTarget?>(null)
    val contextTarget: StateFlow<ContextTarget?> = _contextTarget.asStateFlow()

    private val _showTabSwitcher = MutableStateFlow(false)
    val showTabSwitcher: StateFlow<Boolean> = _showTabSwitcher.asStateFlow()

    private val _dockVisible = MutableStateFlow(true)
    val dockVisible: StateFlow<Boolean> = _dockVisible.asStateFlow()

    private val _selectedProfileId = MutableStateFlow(Tab.DEFAULT_PROFILE_ID)
    val selectedProfileId: StateFlow<String> = _selectedProfileId.asStateFlow()

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, io.github.klppl.ruta.data.settings.AppSettings())

    val blocklistStatus = blocklistRepository.status

    val customServices: StateFlow<List<AppService>> = bookmarkRepository.bookmarks
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addBookmark(name: String, url: String) {
        viewModelScope.launch { bookmarkRepository.add(name, url) }
    }

    fun removeBookmark(id: String) {
        viewModelScope.launch { bookmarkRepository.remove(id) }
    }

    val profiles: StateFlow<List<Profile>> = profileManager.userProfiles
        .map { list -> listOf(Profile.Default) + list }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(Profile.Default))

    val activeTab: StateFlow<Tab?> = combine(_tabs, _activeId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { restoreSession() }
        // Persist lightweight tab state after changes settle.
        viewModelScope.launch {
            combine(_tabs, _activeId) { t, a -> t to a }
                .drop(1)
                .debounce(600)
                .collect { (t, a) -> tabRepository.save(t, a) }
        }
    }

    private suspend fun restoreSession() {
        val session = tabRepository.load()
        if (session == null || session.tabs.isEmpty()) {
            addNewTab()
            return
        }
        _tabs.value = session.tabs.map {
            Tab(
                id = it.id,
                url = it.url,
                title = it.title,
                profileId = it.profileId,
                desktopMode = it.desktopMode,
                isNewTab = false,
            )
        }
        _activeId.value = session.activeId ?: _tabs.value.firstOrNull()?.id
    }

    // ---------------------------------------------------------------- tabs ---

    fun addNewTab(profileId: String = _selectedProfileId.value) {
        val tab = Tab(id = newId(), url = "", profileId = profileId, isNewTab = true)
        _tabs.value = _tabs.value + tab
        _activeId.value = tab.id
        _showTabSwitcher.value = false
        _dockVisible.value = true
    }

    fun selectTab(id: String) {
        _activeId.value = id
        _showTabSwitcher.value = false
        _dockVisible.value = true
    }

    /** Reorder the dock: [from]/[to] are indices within the visible (non-new) dock tabs. */
    fun moveTab(from: Int, to: Int) {
        val current = _tabs.value
        val dock = current.filter { !it.isNewTab }
        if (from !in dock.indices || to !in dock.indices || from == to) return
        val reordered = dock.toMutableList().apply { add(to, removeAt(from)) }
        val iter = reordered.iterator()
        _tabs.value = current.map { if (!it.isNewTab) iter.next() else it }
    }

    fun closeTab(id: String) {
        engine.destroyTab(id)
        val remaining = _tabs.value.filterNot { it.id == id }
        _tabs.value = remaining
        if (_activeId.value == id) {
            _activeId.value = remaining.lastOrNull()?.id
        }
        if (remaining.isEmpty()) addNewTab()
    }

    /** @return true if the back press was consumed; false means the host may exit. */
    fun onBack(): Boolean {
        val id = _activeId.value ?: return false
        if (_showTabSwitcher.value) {
            _showTabSwitcher.value = false
            return true
        }
        if (engine.canGoBack(id)) {
            engine.goBack(id)
            return true
        }
        if (_tabs.value.size > 1) {
            closeTab(id)
            return true
        }
        return false
    }

    // -------------------------------------------------------------- loading ---

    fun openInput(raw: String) {
        val url = toUrlOrSearch(raw)
        loadInActiveTab(url)
    }

    /**
     * Open a link handed to us from outside the app (an `ACTION_VIEW` intent). Unlike [openInput],
     * which loads into the current tab, this routes the URL into the matching service's profile so
     * tapping an x.com link elsewhere lands in the X "instance" — reusing an existing tab on that
     * profile when one is already open.
     */
    fun openExternalLink(raw: String) {
        val url = toUrlOrSearch(raw)
        if (url.isBlank()) return
        val service = Services.forHost(Hosts.hostOf(url))
        val profileId = if (settings.value.separateProfilePerSite && service != null) {
            Profile.siteId(service.host)
        } else {
            _selectedProfileId.value
        }
        _dockVisible.value = true
        _showTabSwitcher.value = false

        val reusable = _tabs.value.firstOrNull {
            it.profileId == profileId &&
                service != null && Services.forHost(Hosts.hostOf(it.url))?.host == service.host
        }
        val active = activeTab.value
        when {
            reusable != null -> {
                _activeId.value = reusable.id
                _tabs.value = _tabs.value.map {
                    if (it.id == reusable.id) it.copy(url = url, isNewTab = false) else it
                }
                engine.loadUrl(reusable.id, url)
            }
            active != null && active.isNewTab -> {
                _tabs.value = _tabs.value.map {
                    if (it.id == active.id) it.copy(url = url, profileId = profileId, isNewTab = false) else it
                }
                engine.loadUrl(active.id, url)
            }
            else -> {
                val tab = Tab(id = newId(), url = url, profileId = profileId, isNewTab = false)
                _tabs.value = _tabs.value + tab
                _activeId.value = tab.id
            }
        }
    }

    fun openService(service: AppService) {
        val profileId = if (settings.value.separateProfilePerSite) {
            Profile.siteId(service.host)
        } else {
            _selectedProfileId.value
        }
        val active = activeTab.value
        if (active != null && active.isNewTab) {
            _tabs.value = _tabs.value.map {
                if (it.id == active.id) it.copy(url = service.url, profileId = profileId, isNewTab = false) else it
            }
            engine.loadUrl(active.id, service.url)
        } else {
            val tab = Tab(id = newId(), url = service.url, profileId = profileId, isNewTab = false)
            _tabs.value = _tabs.value + tab
            _activeId.value = tab.id
        }
        _showTabSwitcher.value = false
        _dockVisible.value = true
    }

    private fun loadInActiveTab(url: String) {
        _dockVisible.value = true
        val active = activeTab.value
        if (active == null) {
            val tab = Tab(id = newId(), url = url, profileId = _selectedProfileId.value, isNewTab = false)
            _tabs.value = _tabs.value + tab
            _activeId.value = tab.id
            return
        }
        _tabs.value = _tabs.value.map {
            if (it.id == active.id) it.copy(url = url, isNewTab = false) else it
        }
        engine.loadUrl(active.id, url)
    }

    fun reloadActive() = _activeId.value?.let(engine::reload)

    /** Tapping the already-active dock icon resets that tab to the service's home URL. */
    fun resetToHome(tabId: String) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val host = Hosts.hostOf(tab.url)
        val home = Services.forHost(host)?.url ?: host?.let { "https://$it/" } ?: return
        engine.loadUrl(tabId, home)
        _dockVisible.value = true
    }

    fun toggleDesktopMode() {
        val tab = activeTab.value ?: return
        val next = !tab.desktopMode
        updateTab(tab.id) { it.copy(desktopMode = next) }
        engine.setDesktopMode(tab.id, next)
    }

    fun downloadMedia() = _activeId.value?.let(engine::requestMediaDownload)

    fun setAdBlockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAdBlock(enabled) }
    }

    // --------------------------------------------------------- context menu ---

    fun openContextLinkInNewTab(newProfile: Boolean) {
        val target = _contextTarget.value ?: return
        val url = target.link ?: return
        dismissContext()
        viewModelScope.launch {
            val profileId = if (newProfile) {
                profileManager.createProfile(Hosts.hostOf(url) ?: "Account").id
            } else {
                activeTab.value?.profileId ?: Tab.DEFAULT_PROFILE_ID
            }
            val tab = Tab(id = newId(), url = url, profileId = profileId, isNewTab = false)
            _tabs.value = _tabs.value + tab
            _activeId.value = tab.id
        }
    }

    fun downloadContextImage() {
        val target = _contextTarget.value ?: return
        val url = target.image ?: target.video ?: return
        dismissContext()
        mediaResolver.downloadUrl(url, activeTab.value?.url, activeTab.value?.desktopMode == true)
    }

    fun copyContextLink() {
        val target = _contextTarget.value ?: return
        val url = target.link ?: target.image ?: return
        dismissContext()
        copyToClipboard(UrlSanitizer.sanitize(url))
    }

    fun copyCurrentUrl() {
        val url = activeTab.value?.url ?: return
        copyToClipboard(UrlSanitizer.sanitize(url))
    }

    fun dismissContext() {
        _contextTarget.value = null
    }

    private fun copyToClipboard(text: String) {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("url", text))
    }

    // ------------------------------------------------------------- profiles ---

    fun setSelectedProfile(id: String) {
        _selectedProfileId.value = id
    }

    fun createProfile(name: String) {
        viewModelScope.launch { profileManager.createProfile(name) }
    }

    fun deleteProfile(id: String) {
        if (id == Tab.DEFAULT_PROFILE_ID) return
        viewModelScope.launch {
            _tabs.value.filter { it.profileId == id }.forEach { engine.destroyTab(it.id) }
            _tabs.value = _tabs.value.filterNot { it.profileId == id }
            if (_selectedProfileId.value == id) _selectedProfileId.value = Tab.DEFAULT_PROFILE_ID
            if (_tabs.value.none { it.id == _activeId.value }) _activeId.value = _tabs.value.lastOrNull()?.id
            if (_tabs.value.isEmpty()) addNewTab()
            profileManager.deleteProfile(id)
        }
    }

    fun setShowTabSwitcher(show: Boolean) {
        _showTabSwitcher.value = show
    }

    // ----------------------------------------------------------- TabEvents ---

    override fun onTitle(tabId: String, title: String) = updateTab(tabId) { it.copy(title = title) }

    override fun onProgress(tabId: String, progress: Int) =
        updateTab(tabId) { it.copy(progress = progress) }

    override fun onUrlChanged(tabId: String, url: String, canGoBack: Boolean) =
        updateTab(tabId) {
            it.copy(url = url, canGoBack = canGoBack, isNewTab = it.isNewTab && url.isBlank())
        }

    override fun onFaviconResolved(tabId: String, faviconUrl: String?) =
        updateTab(tabId) { it.copy(faviconUrl = faviconUrl) }

    override fun onOpenPopup(childTabId: String, profileId: String, desktopMode: Boolean) {
        val tab = Tab(
            id = childTabId,
            url = "",
            profileId = profileId,
            desktopMode = desktopMode,
            isNewTab = false,
        )
        _tabs.value = _tabs.value + tab
        _activeId.value = childTabId
    }

    override fun onCloseTab(tabId: String) = closeTab(tabId)

    override fun onChromeVisibility(visible: Boolean) {
        _dockVisible.value = visible
    }

    override fun onContextTarget(target: ContextTarget) {
        // Merge so the async JS probe can enrich the immediate HitTestResult.
        val current = _contextTarget.value
        _contextTarget.value = if (current != null && current.tabId == target.tabId) {
            current.copy(
                link = target.link ?: current.link,
                image = target.image ?: current.image,
                video = target.video ?: current.video,
                text = target.text ?: current.text,
            )
        } else {
            target
        }
    }

    // ----------------------------------------------------------- helpers ---

    private fun updateTab(id: String, transform: (Tab) -> Tab) {
        _tabs.value = _tabs.value.map { if (it.id == id) transform(it) else it }
    }

    private fun newId(): String = "tab:" + UUID.randomUUID().toString().take(8)

    private fun toUrlOrSearch(raw: String): String {
        val input = raw.trim()
        if (input.isEmpty()) return input
        if (URLUtil.isValidUrl(input)) return input
        val looksLikeHost = !input.contains(' ') && input.contains('.') &&
            !input.endsWith(".")
        return if (looksLikeHost) {
            if (input.startsWith("http")) input else "https://$input"
        } else {
            "https://duckduckgo.com/?q=" + android.net.Uri.encode(input)
        }
    }
}
