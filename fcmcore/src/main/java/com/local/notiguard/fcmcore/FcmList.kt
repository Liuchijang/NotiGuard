package com.local.notiguard.fcmcore

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * HyperOS's private PowerKeeper/Greezer whitelist `Settings.System.MILLET_NO_RESTRICT_APP`
 * (comma-separated package names). If Google Play services drops out of it, GMS is treated
 * as an ordinary background process and its FCM/MCS connection gets cut.
 *
 * Rules shared by every writer: preserve every entry already in the list (order kept,
 * nothing filtered), append GMS only when missing, never write when nothing would change.
 */
object FcmList {

    const val KEY = "MILLET_NO_RESTRICT_APP"
    const val GMS = "com.google.android.gms"
    private const val GSF = "com.google.android.gsf"

    fun uri() = Settings.System.getUriFor(KEY)!!

    fun parse(value: String?): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        if (value == null || value == "null") return out
        value.split(',').map { it.trim() }.filterTo(out) { it.isNotEmpty() }
        return out
    }

    fun hasGms(value: String?): Boolean = GMS in parse(value)

    /**
     * New list to write, or null when [current] already contains GMS.
     * An empty/missing list is rebuilt from [lastGood] before GMS is appended.
     */
    fun merged(current: String?, lastGood: String?): String? {
        if (hasGms(current)) return null
        val pkgs = parse(current).ifEmpty { parse(lastGood) }
        pkgs += GMS
        return pkgs.joinToString(",")
    }

    /**
     * List with [pkg] appended (order kept), or null when it is already listed. An emptied list
     * is rebuilt from [lastGood] first, so adding one app never drops the ROM's own entries.
     */
    fun withAdded(current: String?, lastGood: String?, pkg: String): String? {
        val pkgs = parse(current).ifEmpty { parse(lastGood) }
        if (!pkgs.add(pkg) && parse(current).isNotEmpty()) return null
        return pkgs.joinToString(",")
    }

    /** List without [pkg], or null when it is not listed. GMS is never removed: FCM Guard owns it. */
    fun withRemoved(current: String?, pkg: String): String? {
        if (pkg == GMS) return null
        val pkgs = parse(current)
        if (!pkgs.remove(pkg)) return null
        return pkgs.joinToString(",")
    }

    /** Single-quote for `sh -c`; vendor entries are kept verbatim, never filtered. */
    fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /**
     * Best-effort FCM/MCS reconnect: the heartbeat broadcasts GMS/GSF listen for
     * (same intents as HeartbeatFixerForFCM). Send only after a real repair or on request.
     */
    fun reconnect(context: Context): Boolean {
        var sent = false
        for (target in listOf(GMS, GSF)) {
            for (action in HEARTBEATS) {
                runCatching { context.sendBroadcast(Intent(action).setPackage(target)); sent = true }
            }
        }
        return sent
    }

    /** Opens Google Play services' FCM diagnostics (current activity, then the legacy one). */
    fun openDiagnostics(context: Context): Boolean = DIAGNOSTICS.any { cls ->
        runCatching {
            context.startActivity(
                Intent().setComponent(ComponentName(GMS, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }

    private val HEARTBEATS = listOf(
        "com.google.android.intent.action.GTALK_HEARTBEAT",
        "com.google.android.intent.action.MCS_HEARTBEAT",
    )

    private val DIAGNOSTICS = listOf(
        "com.google.android.gms.gcm.GcmDiagnostics",
        "com.google.android.gms.gtalkservice.diagnostics.GTalkServiceDiagnostics",
    )
}

/** Per-app guard state: on/off switch + last list that contained real entries. */
class FcmGuardPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("fcm_guard", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    /** Foreground service with a silent notification (stronger survival) vs. quiet background mode. */
    var persistentNotification: Boolean
        get() = prefs.getBoolean("persistent_notification", true)
        set(v) = prefs.edit().putBoolean("persistent_notification", v).apply()

    val lastGood: String? get() = prefs.getString("last_good", null)

    /** Stores [value] as the fallback for an emptied list; writes only when it changed. */
    fun remember(value: String?) {
        val pkgs = FcmList.parse(value)
        if (pkgs.isEmpty()) return
        val normalized = pkgs.joinToString(",")
        if (prefs.getString("last_good", null) != normalized) {
            prefs.edit().putString("last_good", normalized).apply()
        }
    }
}

/** Outcome of one check/repair pass. */
data class RepairResult(
    val ok: Boolean,
    val changed: Boolean,
    val value: String?,
    val message: String,
)
