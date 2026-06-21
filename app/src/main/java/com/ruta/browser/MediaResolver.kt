package com.ruta.browser

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a page-resolved media descriptor into an actual saved file. Direct http(s) URLs go
 * through [DownloadManager]; in-page `blob:` data arrives base64-encoded and is written via
 * [MediaStore] (API 29+) or the public Downloads dir on older devices.
 */
@Singleton
class MediaResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userAgentProvider: UserAgentProvider,
) {
    private val main = Handler(Looper.getMainLooper())

    /** Handles a {type:"media"} payload from the page script. */
    fun onMediaMessage(data: JSONObject?, pageUrl: String?, desktop: Boolean) {
        if (data == null || !data.optBoolean("ok", false)) {
            toast("No downloadable media found")
            return
        }
        when (data.optString("kind")) {
            "url" -> downloadUrl(data.optString("url"), pageUrl, desktop)
            "blob" -> saveBase64(
                base64 = data.optString("base64"),
                mime = data.optString("mime", "video/mp4"),
            )
            else -> toast("No downloadable media found")
        }
    }

    fun downloadUrl(url: String?, referer: String?, desktop: Boolean) {
        if (url.isNullOrBlank() || !url.startsWith("http")) {
            toast("No downloadable media found")
            return
        }
        try {
            val uri = Uri.parse(url)
            val name = fileNameFor(uri, fallbackExt = "mp4")
            val request = DownloadManager.Request(uri).apply {
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                addRequestHeader("User-Agent", userAgentProvider.forMode(desktop))
                referer?.let { addRequestHeader("Referer", it) }
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            toast("Downloading…")
        } catch (t: Throwable) {
            toast("Download failed: ${t.message}")
        }
    }

    fun saveBase64(base64: String?, mime: String) {
        if (base64.isNullOrBlank()) {
            toast("No downloadable media found")
            return
        }
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp4"
            val name = "ruta_${System.currentTimeMillis()}.$ext"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val item = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("could not create MediaStore entry")
                resolver.openOutputStream(item)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(item, values, null, null)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                File(dir, name).outputStream().use { it.write(bytes) }
            }
            toast("Saved to Downloads")
        } catch (t: Throwable) {
            toast("Save failed: ${t.message}")
        }
    }

    private fun fileNameFor(uri: Uri, fallbackExt: String): String {
        val last = uri.lastPathSegment?.substringBefore("?").orEmpty()
        val clean = last.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return when {
            clean.isBlank() -> "ruta_${System.currentTimeMillis()}.$fallbackExt"
            clean.contains('.') -> clean
            else -> "$clean.$fallbackExt"
        }
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }
}
