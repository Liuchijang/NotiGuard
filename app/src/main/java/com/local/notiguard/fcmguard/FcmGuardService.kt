package com.local.notiguard.fcmguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.local.notiguard.MainActivity
import com.local.notiguard.Permissions
import com.local.notiguard.fcmcore.FcmList
import com.local.notiguard.shizuku.ShizukuManager
import com.local.notiguard.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Watches [FcmList.KEY] and re-adds GMS whenever HyperOS rebuilds the list
 * (port of FCMGuard-HyperOS `GuardService`).
 *
 * Event-driven: one ContentObserver on that exact key (400 ms debounce) plus an in-process
 * 30-minute fallback — no AlarmManager / WakeLock, so a sleeping phone is not woken.
 * Execution mode follows [com.local.notiguard.fcmcore.FcmGuardPrefs.persistentNotification]:
 * foreground with a silent notification, or a quiet background service (allowed because the
 * app targets SDK 22).
 */
class FcmGuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observer: ContentObserver? = null
    private var foreground = false

    private val repairNow = Runnable { repair() }
    private val fallback = object : Runnable {
        override fun run() {
            repair()
            handler.postDelayed(this, FALLBACK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        applyExecutionMode()
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(repairNow)
                handler.postDelayed(repairNow, DEBOUNCE_MS)
            }
        }.also { contentResolver.registerContentObserver(FcmList.uri(), false, it) }
        // If a direct write was ever rejected, Shizuku coming up is another chance to repair.
        scope.launch {
            ShizukuManager.state.drop(1).filter { it == ShizukuManager.State.READY }.collect { repair() }
        }
        handler.post(fallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!FcmGuard.prefs(this).enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        applyExecutionMode() // the persistent-notification switch may have changed
        return START_STICKY
    }

    private fun repair() {
        scope.launch {
            val r = FcmGuard.repair(this@FcmGuardService)
            when {
                r.changed -> notify(tr("Đã thêm lại GMS lúc ", "GMS re-added at ") + SimpleDateFormat("HH:mm", Locale.ROOT).format(Date()))
                !r.ok -> notify(r.message)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyExecutionMode() {
        // Quiet until notifications were allowed from Settings: creating the channel earlier makes
        // Android 13+ show its prompt, and granting there kills this legacy-target app.
        if (FcmGuard.prefs(this).persistentNotification && notificationsReady(this)) {
            ensureChannel(this)
            startForeground(NOTIFICATION_ID, build(tr("Đang theo dõi danh sách không hạn chế", "Watching the no-restrict list")))
            foreground = true
        } else if (foreground) {
            stopForeground(true)
            foreground = false
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        observer?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notify(text: String) {
        if (!foreground) return
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(text))
    }

    private fun build(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("FCM Guard")
            .setContentText(text)
            .setContentIntent(pi)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "fcm_guard"
        private const val NOTIFICATION_ID = 426
        private const val DEBOUNCE_MS = 400L
        private const val FALLBACK_MS = 30L * 60L * 1000L

        fun start(context: Context) {
            val i = Intent(context, FcmGuardService::class.java)
            runCatching {
                if (FcmGuard.prefs(context).persistentNotification) context.startForegroundService(i)
                else context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FcmGuardService::class.java))
        }

        /** Persistent mode may create its channel now (see [Permissions.notificationsReady]). */
        fun notificationsReady(context: Context) = Permissions.notificationsReady(context, CHANNEL_ID)

        /** True when Android will actually show the foreground notification in the shade. */
        fun canShowNotification(context: Context): Boolean {
            if (!notificationsReady(context)) return false
            val nm = context.getSystemService(NotificationManager::class.java) ?: return false
            if (!nm.areNotificationsEnabled()) return false
            val channel = nm.getNotificationChannel(CHANNEL_ID) ?: return true
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "FCM Guard", NotificationManager.IMPORTANCE_LOW).apply {
                    description = tr("Thông báo thường trực im lặng giữ FCM Guard chạy nền", "Silent persistent notification that keeps FCM Guard running")
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        }
    }
}
