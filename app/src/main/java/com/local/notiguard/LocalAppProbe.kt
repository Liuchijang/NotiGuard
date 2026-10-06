package com.local.notiguard

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.local.notiguard.data.CheckResult
import com.local.notiguard.data.CheckState
import com.local.notiguard.data.TweakCatalog

/**
 * App check without Shizuku: the same check ids as [TweakCatalog.appChecks], read through public
 * APIs a normal app may call for other packages (AppOps, PowerManager, PackageManager). Read-only.
 * Standby bucket and background data have no such API, so they report NA "needs_shizuku".
 * Results follow the shell probes' rules so both paths agree.
 */
object LocalAppProbe {

    private const val OP_MIUI_AUTOSTART = 10008
    private const val OP_MIUI_LOCKSCREEN = 10020
    private const val OP_MIUI_POPUP = 10021
    private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    fun probe(context: Context, pkg: String): Map<String, CheckResult> {
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS) }.getOrNull()
            ?: return TweakCatalog.appChecks.associate { it.id to CheckResult(CheckState.NA, "unreadable") }
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val uid = info.uid

        fun blocked(mode: Int?) = mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED
        fun op(name: String): Int? = runCatching {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(name, uid, pkg)
        }.getOrNull()

        // Generic op: blocked only when explicitly ignore/deny (default = allowed).
        fun notBlocked(vararg names: String): CheckResult {
            val modes = names.map { op(it) }
            return when {
                modes.any { blocked(it) } -> CheckResult(CheckState.FAIL, "blocked")
                modes.all { it == null } -> CheckResult(CheckState.NA, "unreadable")
                else -> CheckResult(CheckState.OK)
            }
        }

        // MIUI vendor op: allow → OK, ignore/deny → NO, anything else → NO default_off, unknown → NA.
        fun miui(code: Int): CheckResult = when (miuiOp(ops, code, uid, pkg)) {
            null -> CheckResult(CheckState.NA, "unsupported")
            AppOpsManager.MODE_ALLOWED -> CheckResult(CheckState.OK)
            AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> CheckResult(CheckState.FAIL)
            else -> CheckResult(CheckState.FAIL, "default_off")
        }

        val notif = run {
            val permOk = Build.VERSION.SDK_INT < 33 ||
                pm.checkPermission(POST_NOTIFICATIONS, pkg) == PackageManager.PERMISSION_GRANTED
            val opOk = !blocked(op("android:post_notification"))
            if (permOk && opOk) CheckResult(CheckState.OK) else CheckResult(CheckState.FAIL, "notif_off")
        }
        val alive = when {
            !info.enabled -> CheckResult(CheckState.FAIL, "disabled")
            info.flags and ApplicationInfo.FLAG_SUSPENDED != 0 -> CheckResult(CheckState.FAIL, "suspended")
            info.flags and ApplicationInfo.FLAG_STOPPED != 0 -> CheckResult(CheckState.FAIL, "stopped")
            else -> CheckResult(CheckState.OK)
        }
        val battery = if (power.isIgnoringBatteryOptimizations(pkg)) CheckResult(CheckState.OK)
        else CheckResult(CheckState.FAIL, "optimized")

        val shellOnly = CheckResult(CheckState.NA, "needs_shizuku")
        val byId = mapOf(
            "notif" to notif,
            "autostart" to miui(OP_MIUI_AUTOSTART),
            "background" to notBlocked("android:run_in_background", "android:run_any_in_background"),
            "battery" to battery,
            "bucket" to shellOnly,
            "wakelock" to notBlocked("android:wake_lock"),
            "bgdata" to shellOnly,
            "alive" to alive,
            "popup" to miui(OP_MIUI_POPUP),
            "lockscreen" to miui(OP_MIUI_LOCKSCREEN),
        )
        return TweakCatalog.appChecks.associate { it.id to (byId[it.id] ?: shellOnly) }
    }

    // Vendor op codes have no public name; same reflective read as AutostartStatusReader.
    private fun miuiOp(ops: AppOpsManager, code: Int, uid: Int, pkg: String): Int? = runCatching {
        val m = AppOpsManager::class.java.getDeclaredMethod(
            "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
        )
        m.isAccessible = true
        m.invoke(ops, code, uid, pkg) as? Int
    }.getOrNull()
}
