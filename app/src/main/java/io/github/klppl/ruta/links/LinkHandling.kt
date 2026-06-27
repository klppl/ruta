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
 * Per-site control over whether ruta is offered as a handler for a wrapped site's links.
 *
 * Android can't register new `<data android:host>` filters at runtime, so each built-in site has a
 * static `<activity-alias>` in the manifest carrying its host filter. Toggling a site here just
 * enables/disables that alias via [PackageManager] — which is what makes the matching domain appear
 * or disappear under Settings → Apps → ruta → "Open by default → supported web addresses". (Custom
 * user-added sites can't take part: the manifest is fixed at build time.)
 */
@Singleton
class LinkHandling @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Built-in services that have a manifest alias, in launcher order. */
    val services: List<AppService> = Services.all.filterNot { it.isCustom }

    private fun component(serviceId: String) =
        ComponentName(context.packageName, ALIAS_PREFIX + serviceId)

    fun isEnabled(serviceId: String): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(serviceId))) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            // DEFAULT honours the manifest's android:enabled=true; ENABLED is an explicit opt-in.
            else -> true
        }

    fun setEnabled(serviceId: String, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            component(serviceId), state, PackageManager.DONT_KILL_APP,
        )
    }

    /** Snapshot of every built-in site's current handler state. */
    fun snapshot(): Map<String, Boolean> = services.associate { it.id to isEnabled(it.id) }

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
