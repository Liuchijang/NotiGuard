package com.local.notiguard.fcmguard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.local.notiguard.fcmcore.FcmList

/**
 * Best-effort detector for installed apps that declare standard FCM/GCM receive components.
 * Ported from FCMGuard-HyperOS (`FcmAppScanner.java`, MIT). Runs only when requested; a match
 * means "likely FCM client", not proof that every notification of that app uses FCM.
 */
object FcmAppScanner {

    private const val ACTION_FCM = "com.google.firebase.MESSAGING_EVENT"
    private const val ACTION_GCM_RECEIVE = "com.google.android.c2dm.intent.RECEIVE"

    data class AppEntry(val packageName: String, val label: String)

    fun scan(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val packages = LinkedHashSet<String>()
        runCatching {
            pm.queryIntentServices(Intent(ACTION_FCM), PackageManager.MATCH_ALL)
                .mapNotNullTo(packages) { it.serviceInfo?.packageName }
        }
        // Legacy GCM / compatibility path still used by some apps and libraries.
        runCatching {
            pm.queryBroadcastReceivers(Intent(ACTION_GCM_RECEIVE), PackageManager.MATCH_ALL)
                .mapNotNullTo(packages) { it.activityInfo?.packageName }
        }
        val skip = setOf(context.packageName, FcmList.GMS, "com.android.vending")
        return packages.filter { it !in skip }.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                // Keep the list focused on enabled, user-facing apps that can actually be opened.
                if (!info.enabled || pm.getLaunchIntentForPackage(pkg) == null) return@runCatching null
                AppEntry(pkg, pm.getApplicationLabel(info).toString().trim().ifEmpty { pkg })
            }.getOrNull()
        }.sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
    }
}
