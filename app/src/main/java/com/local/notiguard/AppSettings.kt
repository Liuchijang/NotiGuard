package com.local.notiguard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens the system settings page where the user can turn on one health check by hand.
 * MIUI/HyperOS pages are tried first, then the stock Android page, then the app-info page.
 */
object AppSettings {

    private const val SECURITY_CENTER = "com.miui.securitycenter"
    private const val POWER_KEEPER = "com.miui.powerkeeper"

    /** Opens the best page for check [checkId] of [pkg]. Returns false if nothing could be opened. */
    fun open(context: Context, checkId: String, pkg: String, label: String): Boolean =
        (candidates(checkId, pkg, label) + appDetails(pkg)).any { tryStart(context, it) }

    private fun candidates(checkId: String, pkg: String, label: String): List<Intent> = when (checkId) {
        "notif" -> listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
        )
        "autostart" -> listOf(
            component(SECURITY_CENTER, "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        // Battery saver per app ("Không hạn chế") covers background, Doze, bucket and wakelock.
        "background", "battery", "bucket", "wakelock" -> listOf(
            component(POWER_KEEPER, "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                .putExtra("package_name", pkg)
                .putExtra("package_label", label),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        "bgdata" -> listOf(
            Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:$pkg")),
        )
        "popup", "lockscreen" -> listOf(
            component(SECURITY_CENTER, "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .setAction("miui.intent.action.APP_PERM_EDITOR")
                .putExtra("extra_pkgname", pkg),
        )
        else -> emptyList() // "alive" and unknown ids → app-info page
    }

    /** "Modify system settings" page for this app (FCM Guard's direct write needs it). */
    fun openWriteSettings(context: Context): Boolean = listOf(
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
        appDetails(context.packageName),
    ).any { tryStart(context, it) }

    /** HyperOS Autostart management list (from FCMGuard's HyperOsSettings), stock fallback. */
    fun openAutostartManager(context: Context): Boolean = listOf(
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
        component(SECURITY_CENTER, "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        Intent(Settings.ACTION_APPLICATION_SETTINGS),
    ).any { tryStart(context, it) }

    private fun appDetails(pkg: String) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    // Some MIUI pages are missing or not exported on certain ROMs → fall through to the next one.
    private fun tryStart(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
