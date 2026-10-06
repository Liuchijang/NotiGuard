package com.local.notiguard.fcmguard

import android.app.AppOpsManager
import android.content.Context
import com.local.notiguard.Txt

/**
 * Best-effort, read-only probe for Xiaomi/HyperOS Autostart AppOps (`10008` + `10053`).
 * Ported from FCMGuard-HyperOS (`AutostartStatusReader.java`, MIT). Never changes AppOps;
 * when HyperOS blocks the vendor query the answer is UNKNOWN — never guessed as DISABLED.
 */
object AutostartStatusReader {

    private const val OP_MIUI_AUTOSTART = 10008
    private const val OP_MIUI_AUTOSTART_SWITCH = 10053

    enum class Status(val label: Txt) {
        ENABLED(Txt("BẬT", "ON")),
        PARTIAL(Txt("MỘT PHẦN", "PARTIAL")),
        DISABLED(Txt("TẮT", "OFF")),
        UNKNOWN(Txt("?", "?")),
    }

    fun check(context: Context, packageName: String): Status = runCatching {
        val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
        val primary = checkOp(context, OP_MIUI_AUTOSTART, uid, packageName)
        val switchOp = checkOp(context, OP_MIUI_AUTOSTART_SWITCH, uid, packageName)
        when {
            primary.allowed && switchOp.allowed -> Status.ENABLED
            primary.ignored && switchOp.ignored -> Status.DISABLED
            (primary.allowed && switchOp.ignored) || (primary.ignored && switchOp.allowed) -> Status.PARTIAL
            else -> Status.UNKNOWN
        }
    }.getOrDefault(Status.UNKNOWN)

    private val Int?.allowed get() = this == AppOpsManager.MODE_ALLOWED
    private val Int?.ignored get() = this == AppOpsManager.MODE_IGNORED

    // Xiaomi keeps these vendor AppOps outside the public SDK constants; reflection keeps the
    // call compileSdk-clean while staying strictly read-only.
    private fun checkOp(context: Context, op: Int, uid: Int, pkg: String): Int? {
        val manager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return null
        return runCatching {
            val m = AppOpsManager::class.java.getDeclaredMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
            )
            m.isAccessible = true
            m.invoke(manager, op, uid, pkg) as? Int
        }.getOrNull()
    }
}
