package io.github.klppl.ruta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.klppl.ruta.browser.BrowserEngine
import io.github.klppl.ruta.ui.RootScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var engine: BrowserEngine

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null

    // URL from an external ACTION_VIEW intent, consumed once by the composition then cleared.
    private val externalUrl = mutableStateOf<String?>(null)

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

        externalUrl.value = viewUrlOf(intent)

        setContent {
            RootScreen(
                externalUrl = externalUrl.value,
                onExternalUrlHandled = { externalUrl.value = null },
                onExit = { moveTaskToBack(true) },
            )
        }
    }

    // launchMode is singleTask, so links tapped while ruta is already running arrive here, not in
    // onCreate. Feed them into the same consumable state the composition observes.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewUrlOf(intent)?.let { externalUrl.value = it }
    }

    private fun viewUrlOf(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString?.takeIf { it.isNotBlank() }

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
