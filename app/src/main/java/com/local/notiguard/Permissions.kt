package com.local.notiguard

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * First-run setup state and the notification-permission gate.
 *
 * Why the gate: on Android 13+ a legacy app (targetSdk ≤ 32) that creates its first notification
 * channel gets the system "Allow notifications?" prompt, and because NotiGuard targets SDK 22,
 * granting it there makes PermissionController kill the app ("legacy apps are restarted on app-op
 * change"). So NotiGuard never creates the channel until notifications were turned on from the
 * Settings page (which does not kill the app), or the channel already exists.
 */
object Permissions {

    /** The first-run setup screen was finished or skipped. */
    fun setupDone(context: Context) = prefs(context).getBoolean(KEY_SETUP_DONE, false)

    fun markSetupDone(context: Context) = prefs(context).edit().putBoolean(KEY_SETUP_DONE, true).apply()

    fun notificationsEnabled(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true

    /** Safe to create notification channels without triggering the killing system prompt. */
    fun notificationsReady(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        if (nm.getNotificationChannel(channelId) != null) return true
        return prefs(context).getBoolean(KEY_NOTIF_CONFIRMED, false) && nm.areNotificationsEnabled()
    }

    /** Call when the user comes back from the app notification settings page. */
    fun confirmNotificationsFromSettings(context: Context) {
        if (notificationsEnabled(context)) prefs(context).edit().putBoolean(KEY_NOTIF_CONFIRMED, true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("notiguard_ui", Context.MODE_PRIVATE)

    private const val KEY_SETUP_DONE = "setup_done"
    private const val KEY_NOTIF_CONFIRMED = "notif_confirmed"
}
