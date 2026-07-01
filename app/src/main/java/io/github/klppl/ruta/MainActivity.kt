package io.github.klppl.ruta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.github.klppl.ruta.browser.BrowserEngine
import io.github.klppl.ruta.data.settings.SettingsRepository
import io.github.klppl.ruta.ui.RootScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

// FragmentActivity (not ComponentActivity) because androidx.biometric's prompt requires it.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var engine: BrowserEngine
    @Inject lateinit var settingsRepository: SettingsRepository

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null

    // App-lock state: locked shows the in-app lock overlay; appLockEnabled mirrors the setting.
    private val locked = mutableStateOf(false)
    private var appLockEnabled = false
    private var promptShowing = false

    // URL from an external ACTION_VIEW intent, consumed once by the composition then cleared.
    private val externalUrl = mutableStateOf<String?>(null)

    // Bumped each time the launcher icon re-enters an already-running task, so the composition
    // can return to the dashboard. A plain counter (not a flag) re-triggers on every relaunch.
    private val homeRequest = mutableIntStateOf(0)

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            pendingFileCallback?.onReceiveValue(uris)
            pendingFileCallback = null
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val request = pendingPermission
            pendingPermission = null
            if (request == null) return@registerForActivityResult
            val granted = request.resources.filter { resource ->
                val perm = androidPermissionFor(resource) ?: return@filter false
                grants[perm] == true || hasPermission(perm)
            }.toTypedArray()
            if (granted.isNotEmpty()) request.grant(granted) else request.deny()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Bind WebView creation to this Activity so system autofill (Bitwarden etc.) works.
        engine.attachActivity(this)
        wireEngineHandlers()

        // Synchronous read so a cold start can't flash unlocked content before the setting
        // arrives (DataStore's first read is a few ms).
        appLockEnabled = runBlocking { settingsRepository.settings.first().appLock }
        locked.value = appLockEnabled
        lifecycleScope.launch {
            settingsRepository.settings.collect { appLockEnabled = it.appLock }
        }

        externalUrl.value = viewUrlOf(intent)

        setContent {
            RootScreen(
                externalUrl = externalUrl.value,
                onExternalUrlHandled = { externalUrl.value = null },
                homeRequest = homeRequest.intValue,
                onExit = { moveTaskToBack(true) },
                locked = locked.value,
                onUnlockRequest = { promptUnlock() },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (locked.value) promptUnlock()
    }

    override fun onStop() {
        super.onStop()
        // Re-lock whenever the app leaves the foreground (the biometric prompt itself only
        // pauses the activity, so it doesn't retrigger this).
        if (appLockEnabled) locked.value = true
    }

    private fun promptUnlock() {
        if (promptShowing || !locked.value) return
        promptShowing = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    locked.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancelled / too many attempts: stay locked; the overlay's Unlock button retries.
                    promptShowing = false
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ruta")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        prompt.authenticate(info)
    }

    // launchMode is singleTask, so links tapped while ruta is already running arrive here, not in
    // onCreate. Feed them into the same consumable state the composition observes.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val viewUrl = viewUrlOf(intent)
        when {
            viewUrl != null -> externalUrl.value = viewUrl
            // Tapping the launcher icon on a backgrounded (still-running) task arrives here as the
            // MAIN/LAUNCHER intent — return to the dashboard. (Resuming from Recents delivers no
            // new intent, so it keeps whatever site the user left open.)
            isLauncherIntent(intent) -> homeRequest.intValue++
        }
    }

    private fun viewUrlOf(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString?.takeIf { it.isNotBlank() }

    private fun isLauncherIntent(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_MAIN &&
            intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true

    override fun onDestroy() {
        // Don't leak this Activity into the singleton engine after teardown.
        if (isFinishing) engine.detachActivity()
        super.onDestroy()
    }

    private fun wireEngineHandlers() {
        engine.fileChooserHandler = { callback, params ->
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = callback
            val intent = params.createIntent()
            runCatching { fileChooserLauncher.launch(intent); true }.getOrElse {
                pendingFileCallback = null
                false
            }
        }
        engine.permissionHandler = { request -> handlePermission(request) }
    }

    private fun handlePermission(request: PermissionRequest) {
        val needed = request.resources.mapNotNull { androidPermissionFor(it) }.distinct()
        val missing = needed.filterNot { hasPermission(it) }
        if (missing.isEmpty()) {
            val granted = request.resources.filter { androidPermissionFor(it) != null }.toTypedArray()
            if (granted.isNotEmpty()) request.grant(granted) else request.deny()
        } else {
            pendingPermission = request
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun androidPermissionFor(resource: String): String? = when (resource) {
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
        else -> null
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
