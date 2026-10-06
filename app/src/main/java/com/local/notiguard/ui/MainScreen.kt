package com.local.notiguard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.notiguard.I18n
import com.local.notiguard.Lang
import com.local.notiguard.MainViewModel
import com.local.notiguard.tr
import com.local.notiguard.data.BatteryImpact
import com.local.notiguard.data.CheckResult
import com.local.notiguard.data.CheckState
import com.local.notiguard.data.TweakStatus
import com.local.notiguard.data.TweakCatalog
import com.local.notiguard.fcmcore.FcmList
import com.local.notiguard.fcmguard.AutostartStatusReader
import com.local.notiguard.shizuku.ShizukuManager
import com.local.notiguard.ui.theme.NothingRed

private val PassGreen = Color(0xFF00C853)
private val Amber = Color(0xFFFFAB00)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.shizukuState.collectAsState()
    val ready = state == ShizukuManager.State.READY
    /** Shizuku-only setups are dimmed + untouchable until Shizuku is active. */
    val locked = !ready
    val enabled = ready && !vm.running
    var showPicker by remember { mutableStateOf(false) }
    var showCheckPicker by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Smart: follow the log to the bottom while a run is in progress.
    LaunchedEffect(vm.log.size) {
        if (vm.running) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { NothingTopBar("NotiGuard") { LangSwitch(I18n.lang, vm::setLanguage) } },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            ShizukuCard(state, vm)

            // ---------- FCM Guard (no Shizuku needed) ----------
            SectionHeader("FCM Guard")
            FcmGuardCard(vm)

            // ---------- System ----------
            SectionHeader(tr("Tối ưu hệ thống", "System tweaks"))
            if (ready) HealthCard(vm)
            TweakCatalog.system.forEach { tweak ->
                SetupCard(
                    title = tweak.title.text,
                    desc = tweak.desc.text,
                    battery = tweak.battery,
                    status = vm.systemStatus[tweak.id] ?: TweakStatus.UNKNOWN,
                    locked = locked,
                    enabled = enabled,
                    onToggle = { vm.setTweak(tweak, it) },
                )
            }
            Locked(locked) {
                Button(
                    onClick = { vm.enableAllSystem() },
                    enabled = enabled && vm.appliedCount < vm.systemTotal,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(tr("BẬT TẤT CẢ", "ENABLE ALL")) }
            }

            // ---------- Per-app push ----------
            SectionHeader(tr("Đẩy thông báo theo app", "Per-app push"))
            SetupCard(
                title = TweakCatalog.perApp.title.text,
                desc = TweakCatalog.perApp.desc.text,
                battery = TweakCatalog.perApp.battery,
                status = vm.perAppStatus,
                locked = locked,
                enabled = enabled && vm.selected.isNotEmpty(),
                onToggle = { vm.setPerApp(it) },
            ) {
                OutlinedButton(
                    onClick = { showPicker = true },
                    enabled = !locked && !vm.running,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (vm.appsLoading) tr("ĐANG TẢI DANH SÁCH APP…", "LOADING APPS…") else tr("CHỌN APP", "PICK APPS") + "  [${vm.selected.size}]") }
                if (vm.selected.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.selected.forEach { pkg ->
                            AssistChip(
                                onClick = { vm.toggleSelected(pkg) },
                                enabled = !locked,
                                label = { Text(vm.labelFor(pkg)) },
                                leadingIcon = if (pkg in vm.whitelisted) {
                                    { Icon(Icons.Filled.Check, tr("đã bật", "enabled"), Modifier.height(16.dp), tint = PassGreen) }
                                } else null,
                                trailingIcon = { Icon(Icons.Filled.Close, tr("Bỏ", "Remove"), Modifier.height(16.dp)) },
                            )
                        }
                    }
                }
            }

            // ---------- Per-app health check ----------
            SectionHeader(tr("Kiểm tra app", "App check"))
            Locked(locked) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        tr(
                            "Đọc trạng thái thật (chỉ đọc): thông báo, tự khởi chạy, chạy nền, tối ưu pin, " +
                                "data nền… Mục chưa bật có thể sửa ngay.",
                            "Reads the real state (read-only): notifications, autostart, background, battery, " +
                                "background data… Items that are off can be fixed right away.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { showCheckPicker = true },
                        enabled = !locked && !vm.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (vm.appsLoading) tr("ĐANG TẢI DANH SÁCH APP…", "LOADING APPS…") else tr("CHỌN APP", "PICK APPS") + "  [${vm.checkPkgs.size}]") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.runAppChecks() },
                            enabled = enabled && vm.checkPkgs.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text(tr("KIỂM TRA", "CHECK") + " [${vm.checkPkgs.size}]") }
                        OutlinedButton(
                            onClick = { vm.fixAllChecked() },
                            enabled = enabled && vm.fixableCount > 0,
                            modifier = Modifier.weight(1f),
                        ) { Text(tr("SỬA TẤT CẢ", "FIX ALL") + " [${vm.fixableCount}]") }
                    }
                    vm.checkPkgs.sortedBy { vm.labelFor(it).lowercase() }.forEach { pkg ->
                        AppCheckCard(
                            label = vm.labelFor(pkg),
                            pkg = pkg,
                            results = vm.checkResults[pkg],
                            enabled = enabled,
                            onFix = { vm.fixApp(pkg) },
                            onOpenSettings = { checkId -> vm.openSettings(checkId, pkg) },
                            onRemove = { vm.toggleCheckPkg(pkg) },
                        )
                    }
                }
            }

            // ---------- Bloatware ----------
            SectionHeader(tr("Gỡ app rác TQ", "Remove CN bloatware"))
            Locked(locked) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr(
                                "Gỡ cho user hiện tại (ROM chặn thì tắt/đình chỉ thay thế). KHÔI PHỤC để cài lại.",
                                "Removes for the current user (disables/suspends if the ROM blocks it). RESTORE reinstalls.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        BatteryTag(BatteryImpact.SAVES)
                    }
                    BloatList(vm, enabled, onRemove = { confirmRemove = true })
                }
            }

            // ---------- Log ----------
            SectionHeader("Output")
            LogView(vm)
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPicker) {
        AppPickerDialog(
            vm = vm,
            selected = vm.selected,
            onToggle = vm::toggleSelected,
            onDismiss = { showPicker = false },
        )
    }
    if (showCheckPicker) {
        AppPickerDialog(
            vm = vm,
            selected = vm.checkPkgs,
            onToggle = vm::toggleCheckPkg,
            onDismiss = { showCheckPicker = false },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; vm.runRemoveBloat() }) {
                    Text(tr("GỠ", "REMOVE"), color = NothingRed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(tr("HỦY", "CANCEL")) } },
            title = { Text(tr("Gỡ ${vm.bloatSelected.size} app?", "Remove ${vm.bloatSelected.size} apps?"), style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    tr(
                        "Các app đã chọn sẽ bị gỡ cho user hiện tại. Nếu lỡ gỡ nhầm app hệ thống " +
                            "(bàn phím, trình duyệt…) máy có thể hoạt động bất thường — có thể dùng " +
                            "nút KHÔI PHỤC để cài lại.",
                        "The selected apps will be removed for the current user. Removing a needed system app " +
                            "(keyboard, browser…) can break things; use RESTORE to reinstall it.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
    }
}

@Composable
private fun BloatList(vm: MainViewModel, enabled: Boolean, onRemove: () -> Unit) {
    when {
        vm.appsLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.height(18.dp), color = NothingRed)
            Text(tr("  ĐANG QUÉT…", "  SCANNING…"), style = MaterialTheme.typography.labelMedium)
        }
        vm.bloatInstalled.isEmpty() -> Text(
            tr("Không phát hiện app rác TQ nào còn tồn tại.", "No known CN bloatware found."),
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.selectAllBloat() }, enabled = enabled) {
                    Text(tr("CHỌN TẤT CẢ", "SELECT ALL") + " [${vm.bloatInstalled.size}]")
                }
                TextButton(onClick = { vm.clearBloatSelection() }, enabled = enabled) {
                    Text(tr("BỎ CHỌN", "CLEAR"))
                }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    vm.bloatInstalled.forEach { bloat ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) { vm.toggleBloat(bloat.pkg) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = bloat.pkg in vm.bloatSelected,
                                onCheckedChange = { vm.toggleBloat(bloat.pkg) },
                                enabled = enabled,
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bloat.label.text, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    bloat.pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRemove,
                    enabled = enabled && vm.bloatSelected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed),
                    modifier = Modifier.weight(1f),
                ) { Text(tr("GỠ", "REMOVE") + " [${vm.bloatSelected.size}]") }
                OutlinedButton(
                    onClick = { vm.runRestoreBloat() },
                    enabled = enabled && vm.bloatSelected.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text(tr("KHÔI PHỤC", "RESTORE")) }
            }
        }
    }
}

// ---------- Language ----------

/** Compact VI | EN toggle for the top bar; the active language is filled red. */
@Composable
private fun LangSwitch(current: Lang, onSelect: (Lang) -> Unit) {
    Row(
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
    ) {
        Lang.entries.forEach { lang ->
            val active = lang == current
            Text(
                lang.code.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(if (active) NothingRed else Color.Transparent)
                    .clickable(enabled = !active) { onSelect(lang) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

// ---------- Setup building blocks ----------

/** Battery-cost tag shown on every setup. */
@Composable
fun BatteryTag(impact: BatteryImpact) {
    val color = when (impact) {
        BatteryImpact.SAVES -> PassGreen
        BatteryImpact.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        BatteryImpact.LOW -> MaterialTheme.colorScheme.onSurface
        BatteryImpact.MEDIUM -> Amber
        BatteryImpact.HIGH -> NothingRed
    }
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, color),
        color = Color.Transparent,
    ) {
        Text(
            impact.label.text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Dims [content] and swallows every touch while [locked] (Shizuku not active), with a
 * "NEEDS SHIZUKU" hint. When unlocked it is shown normally and fully interactive.
 */
@Composable
fun Locked(locked: Boolean, content: @Composable () -> Unit) {
    Column {
        if (locked) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(Icons.Filled.Lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(
                    tr("CẦN SHIZUKU ĐANG CHẠY", "NEEDS SHIZUKU RUNNING"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            Box(Modifier.alpha(if (locked) 0.35f else 1f)) { content() }
            if (locked) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                )
            }
        }
    }
}

/** One on/off setup: title + battery tag + switch, short description, optional extra content. */
@Composable
fun SetupCard(
    title: String,
    desc: String,
    battery: BatteryImpact,
    status: TweakStatus,
    locked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Locked(locked) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BatteryTag(battery)
                            Spacer(Modifier.width(8.dp))
                            StatusBadge(status)
                        }
                    }
                    Switch(
                        checked = status == TweakStatus.APPLIED,
                        onCheckedChange = onToggle,
                        enabled = enabled && !locked,
                    )
                }
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                extra()
            }
        }
    }
}

// ---------- FCM Guard ----------

@Composable
private fun FcmGuardCard(vm: MainViewModel) {
    val busy = vm.running
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Bảo vệ kết nối FCM", "Protect FCM connection"), style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BatteryTag(BatteryImpact.LOW)
                        Spacer(Modifier.width(8.dp))
                        val (txt, color) = when {
                            !vm.fcmChecked -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
                            vm.fcmHasGms -> tr("CÓ GMS", "GMS PRESENT") to PassGreen
                            else -> tr("THIẾU GMS", "GMS MISSING") to NothingRed
                        }
                        StatusDot(color, Modifier.height(8.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(txt, style = MaterialTheme.typography.labelMedium, color = color)
                    }
                }
                Switch(checked = vm.fcmGuardOn, onCheckedChange = { vm.setFcmGuard(it) }, enabled = !busy)
            }
            Text(
                tr(
                    "Giữ Google Play services trong ${FcmList.KEY} và tự thêm lại khi HyperOS xoá. " +
                        "Không cần Shizuku. (Port từ FCMGuard-HyperOS)",
                    "Keeps Google Play services in ${FcmList.KEY} and re-adds it when HyperOS drops it. " +
                        "No Shizuku needed. (Ported from FCMGuard-HyperOS)",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.fcmChecked) {
                Text(
                    "> " + (vm.fcmValue ?: tr("(chưa có giá trị trên ROM này)", "(not set on this ROM)")),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!vm.fcmCanWrite) {
                WarnRow(tr("Chưa có quyền Sửa cài đặt hệ thống", "Modify system settings not granted"), tr("CẤP QUYỀN", "GRANT")) { vm.openWriteSettings() }
            }
            if (vm.fcmNotifBlocked) {
                WarnRow(tr("Thông báo thường trực đang bị chặn", "Persistent notification is blocked"), tr("MỞ CÀI ĐẶT", "OPEN SETTINGS")) { vm.openNotificationSettings() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Thông báo thường trực", "Persistent notification"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        tr("Im lặng, giúp dịch vụ không bị HyperOS tắt", "Silent; keeps HyperOS from killing the service"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.fcmPersistent, onCheckedChange = { vm.updateFcmPersistent(it) }, enabled = !busy)
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.openFcmDiagnostics() }) { Text(tr("CHẨN ĐOÁN", "DIAGNOSE")) }
                TextButton(onClick = { vm.reconnectFcm() }) { Text(tr("KẾT NỐI LẠI", "RECONNECT")) }
                OutlinedButton(onClick = { vm.repairFcm() }, enabled = !busy) { Text(tr("SỬA NGAY", "REPAIR NOW")) }
            }
            FcmAppsBlock(vm)
        }
    }
}

@Composable
private fun WarnRow(text: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(NothingRed, Modifier.height(8.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = NothingRed, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action) }
    }
}

/** FCMGuard's "FCM app assistant": likely FCM clients + read-only HyperOS Autostart state. */
@Composable
private fun FcmAppsBlock(vm: MainViewModel) {
    val apps = vm.fcmApps
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            tr("App dùng FCM", "Apps using FCM") + if (apps == null) "" else " [${apps.size}]",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (vm.fcmScanning) CircularProgressIndicator(Modifier.size(16.dp), color = NothingRed)
        else TextButton(onClick = { vm.scanFcmApps() }) { Text(if (apps == null) tr("QUÉT", "SCAN") else tr("QUÉT LẠI", "RESCAN")) }
    }
    if (apps == null) return
    // Never treat Unknown as Disabled: if HyperOS hides every state, show only the count.
    val readable = apps.any { it.second != AutostartStatusReader.Status.UNKNOWN }
    if (readable) {
        apps.forEach { (app, st) ->
            val color = when (st) {
                AutostartStatusReader.Status.ENABLED -> PassGreen
                AutostartStatusReader.Status.PARTIAL -> Amber
                AutostartStatusReader.Status.DISABLED -> NothingRed
                AutostartStatusReader.Status.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color, Modifier.height(8.dp))
                Spacer(Modifier.width(8.dp))
                Text(app.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("AUTOSTART ${st.label.text}", style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    } else {
        Text(
            tr("HyperOS không cho đọc trạng thái Tự khởi chạy — hãy kiểm tra trong cài đặt.", "HyperOS hides the Autostart state. Check it in settings."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(onClick = { vm.openAutostartManager() }, modifier = Modifier.fillMaxWidth()) {
        Text(tr("CẤU HÌNH TỰ KHỞI CHẠY TRONG HYPEROS", "CONFIGURE AUTOSTART IN HYPEROS"))
    }
}

// ---------- Shizuku ----------

@Composable
private fun ShizukuCard(state: ShizukuManager.State, vm: MainViewModel) {
    val (msg, dot) = when (state) {
        ShizukuManager.State.READY -> "SHIZUKU · ACTIVE" to PassGreen
        ShizukuManager.State.NEEDS_PERMISSION -> tr("SHIZUKU · CẦN CẤP QUYỀN", "SHIZUKU · NEEDS PERMISSION") to Amber
        ShizukuManager.State.UNAVAILABLE -> tr("SHIZUKU · CHƯA CHẠY", "SHIZUKU · NOT RUNNING") to NothingRed
    }
    val d = vm.shizukuDiag
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(dot)
                Spacer(Modifier.width(10.dp))
                Text(msg, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (vm.shizukuTesting) CircularProgressIndicator(Modifier.size(16.dp), color = NothingRed)
            }
            if (d != null) {
                DiagRow(tr("Dịch vụ (binder)", "Service (binder)"), if (d.binder) tr("kết nối", "connected") else tr("không phản hồi", "no response"), d.binder)
                DiagRow(tr("Quyền của NotiGuard", "NotiGuard permission"), if (d.permission) tr("đã cấp", "granted") else tr("chưa cấp", "not granted"), d.permission)
                if (d.binder) {
                    DiagRow(tr("Chạy dưới quyền", "Running as"), d.mode, d.uid != null)
                    DiagRow(tr("Phiên bản API", "API version"), d.version?.toString() ?: "—", d.version != null)
                }
                if (d.binder && d.permission) {
                    DiagRow(
                        tr("Lệnh thử `id`", "Test command `id`"),
                        if (d.execOk) "OK · ${d.execMs} ms" else tr("lỗi: ", "error: ") + d.execOutput.take(40),
                        d.execOk,
                    )
                }
            }
            when (state) {
                ShizukuManager.State.UNAVAILABLE -> Text(
                    tr(
                        "Mở app Shizuku và khởi động dịch vụ (Wireless debugging hoặc root). " +
                            "Các mục cần Shizuku bên dưới đang bị khoá.",
                        "Open the Shizuku app and start the service (Wireless debugging or root). " +
                            "Items below that need Shizuku are locked.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ShizukuManager.State.NEEDS_PERMISSION -> Text(
                    tr("Shizuku đang chạy nhưng NotiGuard chưa được cấp quyền.", "Shizuku is running but NotiGuard has no permission yet."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ShizukuManager.State.READY -> {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.testShizuku() }, enabled = !vm.shizukuTesting) { Text(tr("KIỂM TRA KẾT NỐI", "TEST CONNECTION")) }
                when (state) {
                    ShizukuManager.State.NEEDS_PERMISSION ->
                        Button(onClick = { vm.requestPermission() }) { Text(tr("CẤP QUYỀN", "GRANT")) }
                    ShizukuManager.State.UNAVAILABLE ->
                        TextButton(onClick = { vm.openShizukuApp() }) { Text(tr("MỞ SHIZUKU", "OPEN SHIZUKU")) }
                    ShizukuManager.State.READY -> {}
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.labelMedium, color = if (ok) PassGreen else NothingRed)
    }
}

@Composable
private fun HealthCard(vm: MainViewModel) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("TÌNH TRẠNG TỐI ƯU", "OPTIMIZATION STATUS"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${vm.appliedCount}/${vm.systemTotal} " + tr("nhóm hệ thống", "system tweaks"),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (vm.appliedCount == vm.systemTotal && vm.systemTotal > 0) PassGreen
                    else MaterialTheme.colorScheme.onBackground,
                )
            }
            if (vm.statusChecking) {
                CircularProgressIndicator(Modifier.height(18.dp), color = NothingRed)
            } else {
                TextButton(onClick = { vm.checkStatus() }) { Text(tr("KIỂM TRA", "CHECK")) }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: TweakStatus) {
    val (txt, color) = when (status) {
        TweakStatus.APPLIED -> tr("ĐÃ ÁP DỤNG", "APPLIED") to PassGreen
        TweakStatus.NOT_APPLIED -> tr("CHƯA", "OFF") to MaterialTheme.colorScheme.onSurfaceVariant
        TweakStatus.UNKNOWN -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color, Modifier.height(8.dp))
        Spacer(Modifier.width(5.dp))
        Text(txt, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun AppCheckCard(
    label: String,
    pkg: String,
    results: Map<String, CheckResult>?,
    enabled: Boolean,
    onFix: () -> Unit,
    onOpenSettings: (checkId: String) -> Unit,
    onRemove: () -> Unit,
) {
    val ok = results?.values?.count { it.state == CheckState.OK } ?: 0
    val fail = results?.values?.count { it.state == CheckState.FAIL } ?: 0
    val fixable = TweakCatalog.appChecks.count { it.fix != null && results?.get(it.id)?.state == CheckState.FAIL }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        pkg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (results != null) {
                    Text(
                        "$ok/${ok + fail}",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (fail == 0) PassGreen else NothingRed,
                    )
                }
            }
            if (results == null) {
                Text(
                    tr("> chưa kiểm tra", "> not checked yet"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TweakCatalog.appChecks.forEach { check ->
                    val r = results[check.id] ?: CheckResult(CheckState.NA)
                    val (txt, color) = when (r.state) {
                        CheckState.OK -> tr("BẬT", "ON") to PassGreen
                        CheckState.FAIL -> tr("CHƯA", "OFF") to NothingRed
                        CheckState.NA -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(color, Modifier.height(8.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(check.title.text, style = MaterialTheme.typography.bodyMedium)
                            if (r.state != CheckState.OK && r.detail.isNotEmpty()) {
                                Text(
                                    TweakCatalog.detailText(r.detail),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(txt, style = MaterialTheme.typography.labelMedium, color = color)
                        if (r.state == CheckState.FAIL) {
                            IconButton(onClick = { onOpenSettings(check.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = tr("Mở cài đặt", "Open settings"),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Spacer(Modifier.width(32.dp))
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRemove, enabled = enabled) { Text(tr("BỎ", "REMOVE")) }
                if (fixable > 0) {
                    OutlinedButton(onClick = onFix, enabled = enabled) { Text(tr("SỬA", "FIX") + " [$fixable]") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerDialog(
    vm: MainViewModel,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, vm.installedApps) {
        if (query.isBlank()) vm.installedApps
        else vm.installedApps.filter {
            it.label.contains(query, true) || it.pkg.contains(query, true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("XONG", "DONE")) } },
        title = { Text(tr("CHỌN APP", "PICK APPS") + "  [${selected.size}]", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(tr("Tìm app…", "Search apps…")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.pkg }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(app.pkg) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = app.pkg in selected,
                                onCheckedChange = { onToggle(app.pkg) },
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    app.pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun LogView(vm: MainViewModel) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vm.running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(16.dp), color = NothingRed)
                        Text(tr("  ĐANG CHẠY…", "  RUNNING…"), style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Text("// ${vm.log.size} " + tr("dòng", "lines"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { vm.clearLog() }, enabled = vm.log.isNotEmpty()) { Text(tr("XÓA", "CLEAR")) }
            }
            if (vm.log.isEmpty()) {
                Text(
                    tr("> chưa có kết quả. cấp quyền shizuku rồi chạy một tối ưu.", "> no output yet. grant shizuku, then run a tweak."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vm.log.forEach { line ->
                val c = when (line.ok) {
                    true -> PassGreen
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurface
                }
                val icon = when (line.ok) {
                    true -> Icons.Filled.CheckCircle
                    false -> Icons.Filled.Error
                    null -> null
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, tint = c, modifier = Modifier.height(14.dp))
                    }
                    Text(
                        line.text,
                        color = c,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
