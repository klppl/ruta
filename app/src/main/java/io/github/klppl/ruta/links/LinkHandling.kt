package io.github.klppl.ruta.links

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.klppl.ruta.model.AppService
import io.github.klppl.ruta.model.Services
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers ruta as a handler for its wrapped sites' links and routes the user to Android's own
 * per-domain "open by default" screen to manage them.
 *
 * Android can't register new `<data android:host>` filters at runtime, so each built-in site has a
 * static `<activity-alias>` in the manifest carrying its host filter. [ensureAllEnabled] enables
 * every alias so all their domains show up under Settings → Apps → ruta → "Open by default →
 * supported web addresses", which is where the user actually chooses what opens in ruta. (Custom
 * user-added sites can't take part: the manifest is fixed at build time.)
 */
@Singleton
class LinkHandling @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Built-in services that have a manifest alias, in launcher order. */
    private val services: List<AppService> = Services.all.filterNot { it.isCustom }

    private fun component(serviceId: String) =
        ComponentName(context.packageName, ALIAS_PREFIX + serviceId)

    /**
     * Enable every site's link alias. A disabled alias drops its domain from Android's supported-
     * web-addresses list entirely, so enabling them all is what lets the user manage every site
     * from the system screen. Idempotent; doesn't override the user's per-domain "open by default"
     * choices (those are a separate app-link preference, not the component's enabled state).
     */
    fun ensureAllEnabled() {
        services.forEach { service ->
            context.packageManager.setComponentEnabledSetting(
                component(service.id),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    /** Open the system screen that hosts the per-domain "open by default" toggles. */
    fun openSystemSettings() {
        val pkg = Uri.fromParts("package", context.packageName, null)
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(primary) }.onFailure {
            // Some OEM builds don't honour the open-by-default action; fall back to app details.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    companion object {
        // Alias classes live in the manifest namespace, not the (possibly -debug) applicationId.
        private const val ALIAS_PREFIX = "io.github.klppl.ruta.LinkAlias_"
    }
}
