package com.local.notiguard.fcmguard

import android.content.Context
import android.provider.Settings
import com.local.notiguard.fcmcore.FcmGuardPrefs
import com.local.notiguard.fcmcore.FcmList
import com.local.notiguard.fcmcore.RepairResult
import com.local.notiguard.shizuku.ShizukuManager
import com.local.notiguard.tr
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * FCM Guard — the core of FCMGuard-HyperOS (ReedGAOOO, MIT) inside NotiGuard.
 *
 * Primary path needs **no Shizuku**: the app targets SDK 22 (see app/build.gradle.kts), so
 * it holds WRITE_SETTINGS via the legacy path and may write the non-public key
 * [FcmList.KEY] directly. If the ROM rejects that write and Shizuku is running, the same
 * value is written through `settings put system` as the shell uid instead.
 */
object FcmGuard {

    private val repairLock = Mutex()

    fun prefs(context: Context) = FcmGuardPrefs(context)

    fun canWrite(context: Context) = Settings.System.canWrite(context)

    fun read(context: Context): String? =
        runCatching { Settings.System.getString(context.contentResolver, FcmList.KEY) }.getOrNull()

    /** Checks the list and appends GMS if it was dropped. A no-op pass performs no write. */
    suspend fun repair(context: Context): RepairResult = repairLock.withLock {
        val prefs = prefs(context)
        val current = read(context)
        val target = FcmList.merged(current, prefs.lastGood)
        if (target == null) {
            prefs.remember(current)
            return RepairResult(true, false, current, tr("GMS đã có trong danh sách", "GMS is already in the list"))
        }

        val (how, error) = write(context, target)
        val after = read(context)
        if (error != null || !FcmList.hasGms(after)) {
            return RepairResult(false, false, after, tr("Ghi thất bại", "Write failed") + " — ${error ?: tr("đọc lại không thấy GMS", "GMS missing on read-back")}")
        }
        prefs.remember(after)
        FcmList.reconnect(context)
        RepairResult(true, true, after, tr("Đã thêm lại GMS ($how) + kích kết nối FCM", "Re-added GMS ($how) + FCM reconnect sent"))
    }

    /** Adds [pkg] to the list (user's choice from the app UI); every other entry is kept. */
    suspend fun add(context: Context, pkg: String): RepairResult =
        edit(context) { current, lastGood -> FcmList.withAdded(current, lastGood, pkg) }

    /** Removes [pkg] from the list; GMS cannot be removed while FCM Guard owns it. */
    suspend fun remove(context: Context, pkg: String): RepairResult =
        edit(context) { current, _ -> FcmList.withRemoved(current, pkg) }

    /** One user edit under the same lock as [repair], written and read back like a repair. */
    private suspend fun edit(context: Context, change: (String?, String?) -> String?): RepairResult = repairLock.withLock {
        val prefs = prefs(context)
        val current = read(context)
        val target = change(current, prefs.lastGood)
            ?: return RepairResult(true, false, current, tr("Không có gì thay đổi", "Nothing to change"))
        val (how, error) = write(context, target)
        val after = read(context)
        if (error != null || FcmList.parse(after) != FcmList.parse(target)) {
            return RepairResult(false, false, after, tr("Ghi thất bại", "Write failed") + " — ${error ?: tr("đọc lại không khớp", "read-back mismatch")}")
        }
        prefs.remember(after)
        RepairResult(true, true, after, tr("Đã ghi", "Written") + " ($how)")
    }

    /** Direct write first, then `settings put` through Shizuku. Returns (how, error or null). */
    private suspend fun write(context: Context, value: String): Pair<String, String?> {
        val error = writeDirect(context, value)
        if (error == null || ShizukuManager.state.value != ShizukuManager.State.READY) return tr("trực tiếp", "direct") to error
        val w = ShizukuManager.exec("settings put system ${FcmList.KEY} ${FcmList.shellQuote(value)}")
        return tr("qua Shizuku", "via Shizuku") to (if (w.ok) null else "[${w.exitCode}] ${w.output.trim()}")
    }

    /** Returns null on success, otherwise why the direct write failed. */
    private fun writeDirect(context: Context, value: String): String? {
        if (!canWrite(context)) return tr("chưa có quyền Sửa cài đặt hệ thống", "no Modify system settings permission")
        return runCatching { Settings.System.putString(context.contentResolver, FcmList.KEY, value) }
            .fold(
                onSuccess = { ok -> if (ok) null else tr("ROM từ chối ghi", "ROM refused the write") },
                onFailure = { "${it.javaClass.simpleName}: ${it.message}" },
            )
    }
}
