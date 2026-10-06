package com.local.notiguard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
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
import com.local.notiguard.data.expectMatches
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

    // --- Installed apps / app-check list ---
    var installedApps by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var appsLoading by mutableStateOf(true)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    // --- Per-app health check ---
    /** Apps picked for the app check (stored under the old per-app push key, so picks survive). */
    val checkPkgs: Set<String> get() = selected

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

    // Declared before init: init → refreshFcm() reads it.
    /** Entries that are also in the Doze whitelist (= "No restrictions" in HyperOS Settings). */
    var milletDoze by mutableStateOf<Set<String>>(emptySet())
        private set

    private val labelCache = HashMap<String, String>()


    /** Scanned FCM clients with their read-only Autostart state; null = not scanned yet. */
    var fcmApps by mutableStateOf<List<Pair<FcmAppScanner.AppEntry, AutostartStatusReader.Status>>?>(null)
        private set
    var fcmScanning by mutableStateOf(false)
        private set

    /** Set while the user is on our notification settings page; checked in [onResume]. */
    private var notifSettingsPending = false

    // --- Permissions (first-run setup screen + warnings) ---
    var showSetup by mutableStateOf(!Permissions.setupDone(app))
        private set
    var notifEnabled by mutableStateOf(Permissions.notificationsEnabled(app))
        private set
    /** Notifications allowed from Settings, so FCM Guard can show its persistent notification. */
    var notifReady by mutableStateOf(false)
        private set

    val log: SnapshotStateList<LogLine> = emptyList<LogLine>().toMutableStateList()

    private val prefs = app.getSharedPreferences("xnf", Context.MODE_PRIVATE)

    init {
        selected = prefs.getStringSet(KEY_SELECTED, emptySet()).orEmpty().toSet()
        prefs.edit().remove(KEY_CHECK).apply() // old separate app-check list, now shared with per-app push
        loadApps()
        refreshFcm()
        // Auto-probe status whenever Shizuku becomes ready.
        viewModelScope.launch {
            ShizukuManager.state.collect { st ->
                testShizuku()
                // Re-read on every change: READY → full shell read; lost → the Settings-readable part.
                if (!running) checkStatus()
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
        if (pkg !in selected) checkResults = checkResults - pkg
        prefs.edit().putStringSet(KEY_SELECTED, selected).apply()
    }


    fun toggleBloat(pkg: String) {
        bloatSelected = if (pkg in bloatSelected) bloatSelected - pkg else bloatSelected + pkg
    }

    fun selectAllBloat() { bloatSelected = bloatInstalled.map { it.pkg }.toSet() }
    fun clearBloatSelection() { bloatSelected = emptySet() }
    fun clearLog() = log.clear()

    // ---------- Smart status probing ----------

    /** Public, guarded: re-read the device state for every system tweak. */
    fun checkStatus() {
        if (running || statusChecking) return
        viewModelScope.launch {
            statusChecking = true
            try {
                probeSystemStatus()
            } finally {
                statusChecking = false
            }
        }
    }

    private val shizukuReady get() = ShizukuManager.state.value == ShizukuManager.State.READY

    /** Without Shizuku only the steps verified by a plain `settings get` can be read (see [readSettingLocal]). */
    private suspend fun probeSystemStatus() {
        val read: suspend (String) -> String? = if (shizukuReady) { c -> readShell(c) } else { c -> readSettingLocal(c) }
        systemStatus = TweakCatalog.system.associate { it.id to readStatus(it.steps, read) }
    }

    /** Output of [cmd] through Shizuku, or null when it could not run. */
    private suspend fun readShell(cmd: String): String? = ShizukuManager.exec(cmd).takeIf { it.ok }?.output

    /**
     * `settings get <global|secure|system> <key>` answered through the Settings provider, which any
     * app may read (this one targets SDK 22, so hidden keys are readable too). Unset prints "null"
     * like the shell does. Any other command (dumpsys, appops…) is unreadable without Shizuku → null.
     */
    private fun readSettingLocal(cmd: String): String? {
        val m = Regex("""^settings get (global|secure|system) (\S+)$""").find(cmd.trim()) ?: return null
        val (ns, key) = m.destructured
        val cr = getApplication<Application>().contentResolver
        return runCatching {
            when (ns) {
                "global" -> Settings.Global.getString(cr, key)
                "secure" -> Settings.Secure.getString(cr, key)
                else -> Settings.System.getString(cr, key)
            } ?: "null"
        }.getOrNull()
    }

    /**
     * APPLIED = every verify read back its expected value; NOT_APPLIED = at least one read a different
     * value; UNKNOWN = nothing to verify, or a read failed (Shizuku/command error) and nothing mismatched.
     * A failed read is never shown as OFF.
     */
    private suspend fun readStatus(steps: List<Step>, read: suspend (String) -> String? = ::readShell): TweakStatus {
        var checked = 0
        var unreadable = false
        for (st in steps) {
            val v = st.verify ?: continue
            if (st.expect == null) continue
            val out = read(v)
            if (out == null) { unreadable = true; continue }
            checked++
            if (!st.matches(out)) return TweakStatus.NOT_APPLIED
        }
        return if (checked == 0 || unreadable) TweakStatus.UNKNOWN else TweakStatus.APPLIED
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
        notifEnabled = Permissions.notificationsEnabled(ctx)
        notifReady = FcmGuardService.notificationsReady(ctx)
        fcmValue = FcmGuard.read(ctx)
        fcmChecked = true
        refreshMilletDoze()
        fcmCanWrite = FcmGuard.canWrite(ctx)
        fcmNotifBlocked = fcmGuardOn && fcmPersistent && !FcmGuardService.canShowNotification(ctx)
        // Re-arm the watcher in case HyperOS killed it while we were away.
        if (fcmGuardOn) FcmGuardService.start(ctx)
    }

    // ---------- HyperOS no-restrict list (MILLET_NO_RESTRICT_APP), edited by the user ----------

    val milletPkgs: Set<String> get() = FcmList.parse(fcmValue)

    private fun refreshMilletDoze() {
        val power = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
        milletDoze = milletPkgs.filter { runCatching { power.isIgnoringBatteryOptimizations(it) }.getOrDefault(false) }.toSet()
    }

    /** Picker toggle: checked → add to the list, unchecked → remove. Each change is written at once. */
    fun toggleMillet(pkg: String) {
        val add = pkg !in milletPkgs
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val r = if (add) FcmGuard.add(ctx, pkg) else FcmGuard.remove(ctx, pkg)
            fcmValue = r.value
            refreshMilletDoze()
            val verb = if (add) tr("+ Thêm ", "+ Add ") else tr("− Bỏ ", "− Remove ")
            log.add(LogLine("  ${if (r.ok) "✓" else "✗"} $verb${labelFor(pkg)} → ${FcmList.KEY}: ${r.message}", r.ok))
        }
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

    /** Notifications are turned on from Settings, never the in-app system prompt (see [Permissions]). */
    fun openNotificationSettings() {
        val ctx = getApplication<Application>()
        if (AppSettings.open(ctx, "notif", ctx.packageName, "NotiGuard")) notifSettingsPending = true
    }


    fun finishSetup() {
        Permissions.markSetupDone(getApplication())
        showSetup = false
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
        if (ShizukuManager.state.value != ShizukuManager.State.READY) {
            log.add(LogLine(tr("  (không có Shizuku: bỏ qua standby bucket, data nền)", "  (no Shizuku: standby bucket and background data skipped)")))
        }
        var good = 0
        pkgs.forEach { pkg ->
            val res = probeApp(pkg)
            checkResults = checkResults + (pkg to res)
            val scored = res.filterKeys { it in TweakCatalog.scoredChecks }.values
            val ok = scored.count { it.state == CheckState.OK }
            val fail = scored.count { it.state == CheckState.FAIL }
            if (fail == 0) good++
            log.add(LogLine("  ${labelFor(pkg)}: $ok/${ok + fail} " + tr("mục đã bật", "items on"), fail == 0))
        }
        log.add(LogLine(tr("— KẾT QUẢ: $good/${pkgs.size} app đã bật đủ", "— RESULT: $good/${pkgs.size} apps fully on"), good == pkgs.size))
    }

    fun fixApp(pkg: String) = launchRun { fixAndRecheck(listOf(pkg)) }

    fun fixAllChecked() = launchRun { fixAndRecheck(sortedCheckPkgs().filter { fixableFor(it).isNotEmpty() }) }

    private suspend fun fixAndRecheck(pkgs: List<String>) {
        if (ShizukuManager.state.value != ShizukuManager.State.READY) {
            appendHeading(tr("⚠ Sửa cần Shizuku đang chạy", "⚠ Fixing needs Shizuku running")); return
        }
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
        // Settings may have changed outside the app (system Settings, adb, Doze back on after reboot):
        // re-read so every switch matches the device.
        if (!running) checkStatus()
        if (notifSettingsPending) {
            notifSettingsPending = false
            Permissions.confirmNotificationsFromSettings(getApplication())
        }
        refreshFcm() // restarts the guard, which now goes foreground if notifications are ready
        // Back from HyperOS settings → re-read Autostart for an already shown scan (like FCMGuard).
        if (fcmApps != null) scanFcmApps()
        val pkg = pendingRecheck ?: return
        if (running) return
        pendingRecheck = null
        viewModelScope.launch { checkResults = checkResults + (pkg to probeApp(pkg)) }
    }

    /** Shell probe through Shizuku when ready; otherwise the read-only public-API subset. */
    private suspend fun probeApp(pkg: String): Map<String, CheckResult> {
        val ctx = getApplication<Application>()
        val local = withContext(Dispatchers.IO) { LocalAppProbe.probe(ctx, pkg) }
        if (ShizukuManager.state.value != ShizukuManager.State.READY) return local
        val shell = TweakCatalog.parseAppCheck(ShizukuManager.exec(TweakCatalog.appCheckScript(pkg)).output)
        // Debug builds: report where the no-Shizuku path disagrees with the shell probe.
        if (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            shell.forEach { (id, s) ->
                val l = local[id] ?: return@forEach
                if (l.state != CheckState.NA && s.state != CheckState.NA && l.state != s.state) {
                    Log.w("NotiGuard", "app check mismatch $pkg/$id: shell=${s.state} local=${l.state} ${l.detail}")
                }
            }
        }
        return shell
    }

    private fun fixableFor(pkg: String) = TweakCatalog.appChecks.filter {
        !it.optional && it.fix != null && checkResults[pkg]?.get(it.id)?.state == CheckState.FAIL
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

    /** Launcher apps from the loaded list; other packages (services, list entries) via PackageManager, cached. */
    fun labelFor(pkg: String) = installedApps.firstOrNull { it.pkg == pkg }?.label ?: labelCache.getOrPut(pkg) {
        val pm = getApplication<Application>().packageManager
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }
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
        val ok = step.matches(verifyRes.output)
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
