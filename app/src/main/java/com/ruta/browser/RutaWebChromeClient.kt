package com.ruta.browser

import android.net.Uri
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.ruta.BuildConfig

class RutaWebChromeClient(
    private val onProgress: (Int) -> Unit,
    private val onTitle: (String) -> Unit,
    private val onCloseWindow: () -> Unit,
    private val onCreateNewWindow: (Message) -> Boolean,
    private val onFileChooser: (ValueCallback<Array<Uri>>, FileChooserParams) -> Boolean,
    private val onPermission: (PermissionRequest) -> Unit,
) : WebChromeClient() {

    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        if (BuildConfig.DEBUG && message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
            Log.d("rutaConsole", "${message.message()} @ ${message.sourceId()}:${message.lineNumber()}")
        }
        return super.onConsoleMessage(message)
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onProgress(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        if (!title.isNullOrBlank()) onTitle(title)
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean = onCreateNewWindow(resultMsg)

    override fun onCloseWindow(window: WebView) {
        onCloseWindow()
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = onFileChooser(filePathCallback, fileChooserParams)

    override fun onPermissionRequest(request: PermissionRequest) {
        onPermission(request)
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        // Privacy default: deny geolocation; profiles isolate it but we don't auto-grant.
        callback.invoke(origin, false, false)
    }
}
