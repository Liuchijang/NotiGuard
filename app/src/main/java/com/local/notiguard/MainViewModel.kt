package com.local.notiguard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.notiguard.data.Bloat
import com.local.notiguard.data.CheckResult
import com.local.notiguard.data.CheckState
import com.local.notiguard.data.InstalledApp
import com.local.notiguard.data.Step
import com.local.notiguard.data.Tweak
import com.local.notiguard.data.TweakCatalog
import com.local.notiguard.data.TweakStatus
import com.local.notiguard.fcmcore.FcmList
import com.local.notiguard.fcmguard.AutostartStatusReader
import com.local.notiguard.fcmguard.FcmAppScanner
import com.local.notiguard.fcmguard.FcmGuard
import com.local.notiguard.fcmguard.FcmGuardService
import com.local.notiguard.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    /** One line in the output log. [ok] null = heading, true/false = result. */
    data class LogLine(val text: String, val ok: Boolean? = null)

    val shizukuState = ShizukuManager.state

    // --- Per-app push ---
    var installedApps by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var appsLoading by mutableStateOf(true)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Packages currently in the Doze whitelist (smart "already optimized" hint). */
    var whitelisted by mutableStateOf<Set<String>>(emptySet())
        private set

    // --- Per-app health check ---
    var checkPkgs by mutableStateOf<Set<String>>(emptySet())
        private set

    /** pkg → (check id → result). Absent = not checked yet. */
    var checkResults by mutableStateOf<Map<String, Map<String, CheckResult>>>(emptyMap())
        private set

    /** Number of failed checks that have a fix, across all checked apps. */
    val fixableCount
        get() = checkPkgs.sumOf { fixableFor(it).size }

    // --- Bloatware ---
    var bloatInstalled by mutableStateOf<List<Bloat>>(emptyList())
        private set
    var bloatSelected by mutableStateOf<Set<String>>(emptySet())
        private set

    // --- Smart status ---
    var systemStatus by mutableStateOf<Map<String, TweakStatus>>(emptyMap())
        private set
    var statusChecking by mutableStateOf(false)
        private set

    val appliedCount get() = systemStatus.values.count { it == TweakStatus.APPLIED }
    val systemTotal get() = TweakCatalog.system.size

    var running by mutableStateOf(false)
        private set

    // --- Shizuku connection test ---
    var shizukuDiag by mutableStateOf<ShizukuManager.Diagnostics?>(null)
        private set
    var shizukuTesting by mutableStateOf(false)
        private set

    // --- FCM Guard (MILLET_NO_RESTRICT_APP) — works without Shizuku ---
    /** Current whitelist value; null = key unset/unreadable on this ROM. */
    var fcmValue by mutableStateOf<String?>(null)
        private set
    var fcmChecked by mutableStateOf(false)
        private set
    var fcmGuardOn by mutableStateOf(FcmGuard.prefs(app).enabled)
        private set
    var fcmPersistent by mutableStateOf(FcmGuard.prefs(app).persistentNotification)
        private set
    var fcmCanWrite by mutableStateOf(true)
        private set
    /** Persistent mode is on but Android/HyperOS blocks our notifications. */
    var fcmNotifBlocked by mutableStateOf(false)
        private set
    val fcmHasGms get() = FcmList.hasGms(fcmValue)

    /** Scanned FCM clients with their read-only Autostart state; null = not scanned yet. */
    var fcmApps by mutableStateOf<List<Pair<FcmAppScanner.AppEntry, AutostartStatusReader.Status>>?>(null)
        private set
    var fcmScanning by mutableStateOf(false)
        private set

    /** Per-app push setup is ON when every picked app is in the Doze whitelist. */
    val perAppStatus: TweakStatus
        get() = when {
            selected.isEmpty() || shizukuState.value != ShizukuManager.State.READY -> TweakStatus.UNKNOWN
            selected.all { it in whitelisted } -> TweakStatus.APPLIED
            else -> TweakStatus.NOT_APPLIED
        }

    val log: SnapshotStateList<LogLine> = emptyList<LogLine>().toMutableStateList()

    private val prefs = app.getSharedPreferences("xnf", Context.MODE_PRIVATE)

    init {
        selected = prefs.getStringSet(KEY_SELECTED, emptySet()).orEmpty().toSet()
        checkPkgs = prefs.getStringSet(KEY_CHECK, emptySet()).orEmpty().toSet()
        loadApps()
        refreshFcm()
        // Auto-probe status whenever Shizuku becomes ready.
        viewModelScope.launch {
            ShizukuManager.state.collect { st ->
                testShizuku()
                if (st == ShizukuManager.State.READY && !running) checkStatus()
            }
        }
    }

    fun refreshShizuku() = ShizukuManager.refresh()

    fun setLanguage(lang: Lang) = I18n.set(getApplication(), lang)

    /** Explicit connection check: binder, permission, server uid/version, `id` round-trip. */
    fun testShizuku() {
        if (shizukuTesting) return
        viewModelScope.launch {
            shizukuTesting = true
            try { shizukuDiag = ShizukuManager.diagnose() } finally { shizukuTesting = false }
        }
    }

    fun openShizukuApp() {
        val ctx = getApplication<Application>()
        val intent = ctx.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (intent == null) { appendHeading(tr("⚠ Chưa cài app Shizuku", "⚠ Shizuku app is not installed") + " ($SHIZUKU_PACKAGE)"); return }
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun requestPermission() = ShizukuManager.requestPermission()

    private fun loadApps() {
        viewModelScope.launch {
            appsLoading = true
            val (apps, bloat) = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val self = getApplication<Application>().packageName
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val launchable = pm.queryIntentActivities(intent, 0)
                    .asSequence()
                    .map { it.activityInfo.packageName }
                    .filter { it != self }
                    .distinct()
                    .map { pkg ->
                        val label = runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        }.getOrDefault(pkg)
                        InstalledApp(pkg, label)
                    }
                    .sortedBy { it.label.lowercase() }
                    .toList()
                val bloat = TweakCatalog.bloatware.filter { isInstalled(pm, it.pkg) }
                launchable to bloat
            }
            installedApps = apps
            bloatInstalled = bloat
            appsLoading = false
        }
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    fun toggleSelected(pkg: String) {
        selected = if (pkg in selected) selected - pkg else selected + pkg
        prefs.edit().putStringSet(KEY_SELECTED, selected).apply()
    }

    fun toggleCheckPkg(pkg: String) {
        checkPkgs = if (pkg in checkPkgs) checkPkgs - pkg else checkPkgs + pkg
        if (pkg !in checkPkgs) checkResults = checkResults - pkg
        prefs.edit().putStringSet(KEY_CHECK, checkPkgs).apply()
    }

    fun toggleBloat(pkg: String) {
        bloatSelected = if (pkg in bloatSelected) bloatSelected - pkg else bloatSelected + pkg
    }

    fun selectAllBloat() { bloatSelected = bloatInstalled.map { it.pkg }.toSet() }
    fun clearBloatSelection() { bloatSelected = emptySet() }
    fun clearLog() = log.clear()

    // ---------- Smart status probing ----------

    /** Public, guarded: re-read the device state for every system tweak + Doze whitelist. */
    fun checkStatus() {
        if (running || statusChecking) return
        viewModelScope.launch {
            statusChecking = true
            try {
                probeSystemStatus()
                probeWhitelist()
            } finally {
                statusChecking = false
            }
        }
    }

    private suspend fun probeSystemStatus() {
        if (ShizukuManager.state.value != ShizukuManager.State.READY) {
            systemStatus = TweakCatalog.system.associate { it.id to TweakStatus.UNKNOWN }
            return
        }
        val result = LinkedHashMap<String, TweakStatus>()
        for (tw in TweakCatalog.system) {
            var known = false
            var applied = true
            for (st in tw.steps) {
                val v = st.verify ?: continue
                val e = st.expect ?: continue
                known = true
                val r = ShizukuManager.exec(v)
                if (!(r.ok && r.output.trim().contains(e))) applied = false
            }
            result[tw.id] = when {
                !known -> TweakStatus.UNKNOWN
                applied -> TweakStatus.APPLIED
                else -> TweakStatus.NOT_APPLIED
            }
        }
        systemStatus = result
    }

    private suspend fun probeWhitelist() {
        if (ShizukuManager.state.value != ShizukuManager.State.READY) return
        val r = ShizukuManager.exec("dumpsys deviceidle whitelist")
        if (!r.ok) return
        val out = r.output
        whitelisted = installedApps.map { it.pkg }.filter { out.contains(it) }.toSet() +
            selected.filter { out.contains(it) }.toSet()
    }

    // ---------- Setups (on/off) ----------

    /** ON = apply + verify every step; OFF = run the revert commands. Then re-read real state. */
    fun setTweak(tweak: Tweak, on: Boolean) = launchRun {
        appendHeading((if (on) tr("▶ BẬT ", "▶ ON ") else tr("▶ TẮT ", "▶ OFF ")) + tweak.title)
        if (on) {
            var ok = 0
            tweak.steps.forEach { if (runStep(it)) ok++ }
            appendSummary(ok, tweak.steps.size)
        } else {
            runRevert(tweak.revert)
        }
        probeSystemStatus()
    }

    fun enableAllSystem() = launchRun {
        var ok = 0; var tot = 0
        TweakCatalog.system.filter { systemStatus[it.id] != TweakStatus.APPLIED }.forEach { tweak ->
            appendHeading(tr("▶ BẬT ", "▶ ON ") + tweak.title)
            tweak.steps.forEach { tot++; if (runStep(it)) ok++ }
        }
        appendSummary(ok, tot)
        probeSystemStatus()
    }

    fun setPerApp(on: Boolean) = launchRun {
        val pkgs = selected.toList()
        if (pkgs.isEmpty()) { appendHeading(tr("⚠ Chưa chọn app nào", "⚠ No app selected")); return@launchRun }
        if (on) {
            var ok = 0; var tot = 0
            pkgs.forEach { pkg ->
                appendHeading(tr("▶ BẬT đẩy thông báo: ", "▶ ON push: ") + "${labelFor(pkg)} ($pkg)")
                TweakCatalog.stepsFor(pkg).forEach { tot++; if (runStep(it)) ok++ }
            }
            appendSummary(ok, tot)
        } else {
            pkgs.forEach { pkg ->
                appendHeading(tr("▶ TẮT đẩy thông báo: ", "▶ OFF push: ") + "${labelFor(pkg)} ($pkg)")
                runRevert(TweakCatalog.revertFor(pkg))
            }
        }
        probeWhitelist()
        // Keep health-check cards in sync for apps that were already checked.
        pkgs.filter { it in checkResults }.forEach { checkResults = checkResults + (it to probeApp(it)) }
    }

    private suspend fun runRevert(commands: List<String>) {
        var ok = 0
        commands.forEach { cmd ->
            val r = ShizukuManager.exec(cmd)
            if (r.ok) ok++
            val short = cmd.substringBefore('\n').let { if (it.length > 54) it.take(51) + "…" else it }
            log.add(LogLine(if (r.ok) "  ✓ $short" else "  ✗ $short [${r.exitCode}] ${r.output.trim()}", r.ok))
        }
        appendSummary(ok, commands.size)
    }

    // ---------- FCM Guard (port of FCMGuard-HyperOS) ----------

    fun refreshFcm() {
        val ctx = getApplication<Application>()
        fcmValue = FcmGuard.read(ctx)
        fcmChecked = true
        fcmCanWrite = FcmGuard.canWrite(ctx)
        fcmNotifBlocked = fcmGuardOn && fcmPersistent && !FcmGuardService.canShowNotification(ctx)
        // Re-arm the watcher in case HyperOS killed it while we were away.
        if (fcmGuardOn) FcmGuardService.start(ctx)
    }

    fun repairFcm() = launchRun {
        appendHeading(tr("▶ FCM Guard: kiểm tra ", "▶ FCM Guard: checking ") + FcmList.KEY)
        val r = FcmGuard.repair(getApplication())
        fcmValue = r.value
        fcmChecked = true
        log.add(LogLine("  ${if (r.ok) "✓" else "✗"} ${r.message}", r.ok))
    }

    fun reconnectFcm() {
        val sent = FcmList.reconnect(getApplication())
        log.add(LogLine(if (sent) tr("  ✓ Đã gửi heartbeat FCM/MCS tới GMS", "  ✓ FCM/MCS heartbeat sent to GMS") else tr("  ✗ Không gửi được heartbeat", "  ✗ Could not send heartbeat"), sent))
    }

    /** ON = start the watcher and repair right away; OFF = stop it (the list is left as is). */
    fun setFcmGuard(enabled: Boolean) {
        val ctx = getApplication<Application>()
        FcmGuard.prefs(ctx).enabled = enabled
        fcmGuardOn = enabled
        if (enabled) {
            FcmGuardService.start(ctx)
            repairFcm()
        } else {
            FcmGuardService.stop(ctx)
            appendHeading(tr("▶ FCM Guard: TẮT bảo vệ nền", "▶ FCM Guard: background guard OFF"))
        }
        refreshFcm()
    }

    fun updateFcmPersistent(on: Boolean) {
        val ctx = getApplication<Application>()
        FcmGuard.prefs(ctx).persistentNotification = on
        fcmPersistent = on
        if (fcmGuardOn) {
            // Restart so the service switches between foreground and quiet mode.
            FcmGuardService.stop(ctx)
            FcmGuardService.start(ctx)
        }
        refreshFcm()
    }

    fun openFcmDiagnostics() {
        if (!FcmList.openDiagnostics(getApplication())) appendHeading(tr("⚠ Không mở được chẩn đoán FCM của GMS", "⚠ Could not open GMS FCM diagnostics"))
    }

    fun openWriteSettings() {
        if (!AppSettings.openWriteSettings(getApplication())) appendHeading(tr("⚠ Không mở được trang Sửa cài đặt hệ thống", "⚠ Could not open Modify system settings"))
    }

    fun openNotificationSettings() {
        val ctx = getApplication<Application>()
        AppSettings.open(ctx, "notif", ctx.packageName, "NotiGuard")
    }

    fun scanFcmApps() {
        if (fcmScanning) return
        viewModelScope.launch {
            fcmScanning = true
            try {
                val ctx = getApplication<Application>()
                fcmApps = withContext(Dispatchers.IO) {
                    FcmAppScanner.scan(ctx).map { it to AutostartStatusReader.check(ctx, it.packageName) }
                }
            } finally { fcmScanning = false }
        }
    }

    /** Opens HyperOS Autostart management; the scan result is re-checked on return ([onResume]). */
    fun openAutostartManager() {
        if (!AppSettings.openAutostartManager(getApplication())) appendHeading(tr("⚠ Không mở được trang Tự khởi chạy", "⚠ Could not open Autostart settings"))
    }

    // ---------- Per-app health check ----------

    fun runAppChecks() = launchRun {
        val pkgs = sortedCheckPkgs()
        if (pkgs.isEmpty()) { appendHeading(tr("⚠ Chưa chọn app nào để kiểm tra", "⚠ No app selected to check")); return@launchRun }
        appendHeading(tr("▶ Kiểm tra ${pkgs.size} app", "▶ Checking ${pkgs.size} apps"))
        var good = 0
        pkgs.forEach { pkg ->
            val res = probeApp(pkg)
            checkResults = checkResults + (pkg to res)
            val ok = res.values.count { it.state == CheckState.OK }
            val fail = res.values.count { it.state == CheckState.FAIL }
            if (fail == 0) good++
            log.add(LogLine("  ${labelFor(pkg)}: $ok/${ok + fail} " + tr("mục đã bật", "items on"), fail == 0))
        }
        log.add(LogLine(tr("— KẾT QUẢ: $good/${pkgs.size} app đã bật đủ", "— RESULT: $good/${pkgs.size} apps fully on"), good == pkgs.size))
    }

    fun fixApp(pkg: String) = launchRun { fixAndRecheck(listOf(pkg)) }

    fun fixAllChecked() = launchRun { fixAndRecheck(sortedCheckPkgs().filter { fixableFor(it).isNotEmpty() }) }

    private suspend fun fixAndRecheck(pkgs: List<String>) {
        var ok = 0; var tot = 0
        pkgs.forEach { pkg ->
            appendHeading(tr("▶ Sửa ", "▶ Fix ") + "${labelFor(pkg)} ($pkg)")
            val before = fixableFor(pkg)
            before.forEach { c -> ShizukuManager.exec(c.fix!!.replace("%s", pkg)) }
            val after = probeApp(pkg)
            checkResults = checkResults + (pkg to after)
            before.forEach { c ->
                tot++
                val r = after[c.id]
                val fixed = r?.state == CheckState.OK
                if (fixed) ok++
                log.add(
                    LogLine(
                        if (fixed) "  ✓ ${c.title}"
                        else "  ✗ ${c.title}${r?.detail?.takeIf { it.isNotEmpty() }?.let { " (${TweakCatalog.detailText(it)})" } ?: ""}",
                        fixed,
                    )
                )
            }
        }
        appendSummary(ok, tot)
    }

    /** App whose settings page we sent the user to; re-checked when they come back. */
    private var pendingRecheck: String? = null

    /** Open the settings page for one failed check; it is re-checked on return ([onResume]). */
    fun openSettings(checkId: String, pkg: String) {
        val opened = AppSettings.open(getApplication(), checkId, pkg, labelFor(pkg))
        if (opened) pendingRecheck = pkg
        else appendHeading(tr("⚠ Không mở được trang cài đặt của ", "⚠ Could not open settings for ") + labelFor(pkg))
    }

    fun onResume() {
        refreshFcm()
        // Back from HyperOS settings → re-read Autostart for an already shown scan (like FCMGuard).
        if (fcmApps != null) scanFcmApps()
        val pkg = pendingRecheck ?: return
        if (running || ShizukuManager.state.value != ShizukuManager.State.READY) return
        pendingRecheck = null
        viewModelScope.launch { checkResults = checkResults + (pkg to probeApp(pkg)) }
    }

    private suspend fun probeApp(pkg: String): Map<String, CheckResult> {
        val r = ShizukuManager.exec(TweakCatalog.appCheckScript(pkg))
        return TweakCatalog.parseAppCheck(r.output)
    }

    private fun fixableFor(pkg: String) = TweakCatalog.appChecks.filter {
        it.fix != null && checkResults[pkg]?.get(it.id)?.state == CheckState.FAIL
    }

    private fun sortedCheckPkgs() = checkPkgs.sortedBy { labelFor(it).lowercase() }

    fun runRemoveBloat() = launchRun {
        val pkgs = bloatSelected.toList()
        if (pkgs.isEmpty()) { appendHeading(tr("⚠ Chưa chọn app rác nào", "⚠ No bloatware selected")); return@launchRun }
        pkgs.forEach { pkg ->
            appendHeading(tr("▶ Gỡ ", "▶ Remove ") + "${bloatLabelFor(pkg)} ($pkg)")
            execScriptAndLog(TweakCatalog.removeCommand(pkg))
        }
        refreshBloatInstalled()
    }

    fun runRestoreBloat() = launchRun {
        val pkgs = bloatSelected.toList()
        if (pkgs.isEmpty()) { appendHeading(tr("⚠ Chưa chọn app nào để khôi phục", "⚠ No app selected to restore")); return@launchRun }
        pkgs.forEach { pkg ->
            appendHeading(tr("▶ Khôi phục ", "▶ Restore ") + "${bloatLabelFor(pkg)} ($pkg)")
            execScriptAndLog(TweakCatalog.restoreCommand(pkg))
        }
        refreshBloatInstalled()
    }

    private suspend fun refreshBloatInstalled() {
        val bloat = withContext(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            TweakCatalog.bloatware.filter { isInstalled(pm, it.pkg) }
        }
        bloatInstalled = bloat
        bloatSelected = bloatSelected.intersect(bloat.map { it.pkg }.toSet())
    }

    fun labelFor(pkg: String) = installedApps.firstOrNull { it.pkg == pkg }?.label ?: pkg
    private fun bloatLabelFor(pkg: String) =
        TweakCatalog.bloatware.firstOrNull { it.pkg == pkg }?.label?.text ?: pkg

    private fun launchRun(block: suspend () -> Unit) {
        if (running) return
        viewModelScope.launch {
            running = true
            try { block() } finally { running = false }
        }
    }

    /** Apply a step, then (if defined) read its state back and confirm it matches. Returns success. */
    private suspend fun runStep(step: Step): Boolean {
        val applyRes = ShizukuManager.exec(step.apply)
        val short = step.apply.substringBefore('\n').let { if (it.length > 54) it.take(51) + "…" else it }

        if (step.verify == null || step.expect == null) {
            log.add(
                LogLine(
                    if (applyRes.ok) "  ✓ $short"
                    else "  ✗ $short [${applyRes.exitCode}] ${applyRes.output.trim()}",
                    applyRes.ok,
                )
            )
            return applyRes.ok
        }
        if (!applyRes.ok) {
            log.add(LogLine("  ✗ $short [${applyRes.exitCode}] ${applyRes.output.trim()}", false))
            return false
        }
        val verifyRes = ShizukuManager.exec(step.verify)
        val got = verifyRes.output.trim().replace("\n", " ")
            .let { if (it.length > 60) it.take(57) + "…" else it }
        val ok = verifyRes.output.trim().contains(step.expect)
        log.add(
            LogLine(
                if (ok) "  ✓ $short → $got"
                else "  ✗ $short " + tr("(cần \"${step.expect}\", nhận \"$got\")", "(expected \"${step.expect}\", got \"$got\")"),
                ok,
            )
        )
        return ok
    }

    private suspend fun execScriptAndLog(script: String) {
        val r = ShizukuManager.exec(script)
        val out = r.output.trim().ifEmpty { if (r.ok) "OK" else tr("lỗi", "error") }
        log.add(LogLine(text = if (r.ok) "  ✓ $out" else "  ✗ [${r.exitCode}] $out", ok = r.ok))
    }

    private fun appendHeading(text: String) = log.add(LogLine(text, null))
    private fun appendSummary(ok: Int, total: Int) =
        log.add(LogLine(tr("— KẾT QUẢ: $ok/$total bước OK", "— RESULT: $ok/$total steps OK"), ok == total))

    private companion object {
        const val KEY_SELECTED = "selected_pkgs"
        const val KEY_CHECK = "check_pkgs"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
