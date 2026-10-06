package com.local.notiguard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.notiguard.I18n
import com.local.notiguard.Lang
import com.local.notiguard.MainViewModel
import com.local.notiguard.data.BatteryImpact
import com.local.notiguard.data.CheckResult
import com.local.notiguard.data.CheckState
import com.local.notiguard.data.TweakCatalog
import com.local.notiguard.data.TweakStatus
import com.local.notiguard.fcmcore.FcmList
import com.local.notiguard.fcmguard.AutostartStatusReader
import com.local.notiguard.fcmguard.FcmAppScanner
import com.local.notiguard.shizuku.ShizukuManager
import com.local.notiguard.tr
import com.local.notiguard.ui.theme.LocalStatusColors
import com.local.notiguard.ui.theme.NothingRed

// Status text colors follow the theme (≥ 4.5:1 contrast); NothingRed is kept for fills only.
private val PassGreen @Composable get() = LocalStatusColors.current.ok
private val Amber @Composable get() = LocalStatusColors.current.warn
private val Bad @Composable get() = LocalStatusColors.current.bad

/** Bottom-navigation destinations: one job per tab instead of one long page. */
private enum class Tab(val icon: ImageVector) {
    GUARD(Icons.Outlined.Shield),
    TWEAKS(Icons.Outlined.Tune),
    APPS(Icons.Outlined.Apps),
    LOG(Icons.Outlined.Terminal);

    val label: String
        get() = when (this) {
            GUARD -> tr("Bảo vệ", "Guard")
            TWEAKS -> tr("Tối ưu", "Tweaks")
            APPS -> tr("Ứng dụng", "Apps")
            LOG -> tr("Nhật ký", "Log")
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.shizukuState.collectAsState()
    val ready = state == ShizukuManager.State.READY
    /** Shizuku-only setups are dimmed + untouchable until Shizuku is active. */
    val locked = !ready
    val enabled = ready && !vm.running
    var tab by rememberSaveable { mutableStateOf(Tab.GUARD) }
    var showPicker by remember { mutableStateOf(false) }
    var showMilletPicker by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    // Log lines the user has not seen yet (badge on the Log tab).
    var seenLog by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(tab, vm.log.size) {
        if (tab == Tab.LOG || vm.log.size < seenLog) seenLog = vm.log.size
    }
    // One scroll position per tab (plain remembered map; `key {}` returning a value confused the
    // Compose compiler's group layout here).
    val scrollStates = remember { Tab.entries.associateWith { ScrollState(0) } }
    val scrollState = scrollStates.getValue(tab)
    // Follow the log to the bottom while a run is in progress.
    LaunchedEffect(vm.log.size, tab) {
        if (tab == Tab.LOG) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { NothingTopBar("NotiGuard") { LangSwitch(I18n.lang) { vm.setLanguage(it) } } },
        bottomBar = {
            Column {
                val last = vm.log.lastOrNull()
                if (tab != Tab.LOG && last != null) RunStrip(last, vm.running) { tab = Tab.LOG }
                NothingNavBar(tab, unreadLog = vm.log.size - seenLog) { tab = it }
            }
        },
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
            when (tab) {
                Tab.GUARD -> {
                    ShizukuCard(state, vm)
                    SectionHeader("FCM Guard")
                    FcmGuardCard(vm)
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { FcmAppsBlock(vm) }
                    }
                    Text(
                        tr(
                            "FCM Guard dựa trên FCMGuard-HyperOS (MIT). Không cần root hay Shizuku.",
                            "FCM Guard is based on FCMGuard-HyperOS (MIT). No root or Shizuku needed.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Tab.TWEAKS -> {
                    if (locked) ShizukuBanner(state, vm)
                    SectionHeader(tr("Hệ thống", "System"))
                    Locked(locked) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (ready) HealthCard(vm)
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                TweakCatalog.system.forEachIndexed { i, tweak ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    TweakRow(
                                        title = tweak.title.text,
                                        desc = tweak.desc.text,
                                        battery = tweak.battery,
                                        status = vm.systemStatus[tweak.id] ?: TweakStatus.UNKNOWN,
                                        enabled = enabled,
                                        onToggle = { vm.setTweak(tweak, it) },
                                    )
                                }
                            }
                            Button(
                                onClick = { vm.enableAllSystem() },
                                enabled = enabled && vm.appliedCount < vm.systemTotal,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(tr("BẬT TẤT CẢ", "ENABLE ALL")) }
                        }
                    }
                }

                Tab.APPS -> {
                    // App check reads without Shizuku (public APIs, partial); only fixing needs it.
                    SectionHeader(tr("Kiểm tra app", "App check"))
                    AppCheckSection(vm, canFix = enabled, noShizuku = locked, onPick = { showPicker = true })

                    SectionHeader(tr("Không hạn chế nền (HyperOS)", "No background restriction (HyperOS)"))
                    MilletSection(vm, onPick = { showMilletPicker = true })

                    SectionHeader(tr("Gỡ app rác TQ", "Remove CN bloatware"))
                    if (locked) ShizukuBanner(state, vm)
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
                }

                Tab.LOG -> LogView(vm)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPicker) {
        AppPickerDialog(
            vm = vm,
            selected = vm.selected,
            onToggle = { vm.toggleSelected(it) },
            onDismiss = { showPicker = false },
        )
    }
    if (showMilletPicker) {
        AppPickerDialog(
            vm = vm,
            selected = vm.milletPkgs,
            onToggle = { vm.toggleMillet(it) },
            fixed = vm.milletPkgs,
            onDismiss = { showMilletPicker = false },
        )
    }
    if (confirmRemove) {
        RemoveBloatDialog(
            count = vm.bloatSelected.size,
            onConfirm = { confirmRemove = false; vm.runRemoveBloat() },
            onDismiss = { confirmRemove = false },
        )
    }
}

/**
 * View/edit HyperOS's hidden MILLET_NO_RESTRICT_APP list. Written like FCM Guard (direct, then
 * Shizuku), so it works without Shizuku once Modify system settings is granted. GMS is pinned.
 */
@Composable
private fun MilletSection(vm: MainViewModel, onPick: () -> Unit) {
    val pkgs = vm.milletPkgs.sortedBy { vm.labelFor(it).lowercase() }
    val milletScroll = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            tr(
                "Danh sách ẩn MILLET_NO_RESTRICT_APP: HyperOS không đóng băng / tắt app trong này khi chạy nền. Khác với \"Không hạn chế\" trong Cài đặt (= Doze whitelist, nhãn DOZE).",
                "Hidden MILLET_NO_RESTRICT_APP list: HyperOS does not freeze or kill these apps in the background. Not the same as \"No restrictions\" in Settings (= Doze whitelist, DOZE tag).",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!vm.fcmCanWrite) {
            WarnRow(tr("Chưa có quyền Sửa cài đặt hệ thống", "Modify system settings not granted"), tr("CẤP QUYỀN", "GRANT")) { vm.openWriteSettings() }
        }
        // Add only: removing is the ✕ on each row.
        OutlinedButton(onClick = onPick, enabled = !vm.running, modifier = Modifier.fillMaxWidth()) {
            Text(tr("+ THÊM APP", "+ ADD APPS"))
        }
        Text(
            "// ${pkgs.size} app" + if (pkgs.size > 5) tr(" · cuộn trong khung để xem hết", " · scroll inside the box") else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pkgs.isEmpty()) {
            Text(tr("> danh sách trống hoặc ROM không có khóa này", "> list empty or key missing on this ROM"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else OutlinedCard(Modifier.fillMaxWidth()) {
            // Fixed-height box that scrolls on its own (about 5 rows), so a long list doesn't stretch the tab.
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(milletScroll)) {
            pkgs.forEachIndexed { i, pkg ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(vm.labelFor(pkg), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (pkg in vm.milletDoze) {
                        Text("DOZE", style = MaterialTheme.typography.labelSmall, color = PassGreen)
                        Spacer(Modifier.width(4.dp))
                    }
                    if (pkg == FcmList.GMS) {
                        // Pinned: FCM Guard would put it straight back.
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Lock, tr("FCM Guard giữ mục này", "Kept by FCM Guard"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        IconButton(onClick = { vm.toggleMillet(pkg) }, enabled = !vm.running) {
                            Icon(Icons.Filled.Close, tr("Bỏ ", "Remove ") + vm.labelFor(pkg), Modifier.size(18.dp))
                        }
                    }
                }
            }
            }
        }
    }
}

/** Confirmation before removing the selected bloatware (own composable = own slot groups). */
@Composable
private fun RemoveBloatDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(tr("GỠ", "REMOVE"), color = Bad) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("HỦY", "CANCEL")) } },
        title = { Text(tr("Gỡ $count app?", "Remove $count apps?"), style = MaterialTheme.typography.titleMedium) },
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

/** Monochrome bottom navigation; the Log tab carries a red badge with unseen lines. */
@Composable
private fun NothingNavBar(current: Tab, unreadLog: Int, onSelect: (Tab) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
            Tab.entries.forEach { t ->
                NavigationBarItem(
                    selected = t == current,
                    onClick = { onSelect(t) },
                    icon = {
                        if (t == Tab.LOG && unreadLog > 0) {
                            BadgedBox(badge = { Badge(containerColor = NothingRed, contentColor = Color.White) { Text("$unreadLog") } }) {
                                Icon(t.icon, contentDescription = null)
                            }
                        } else {
                            Icon(t.icon, contentDescription = null)
                        }
                    },
                    label = { Text(t.label.uppercase(), style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onBackground,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

/** One-line terminal ticker above the nav bar: what is running / the last result. Tap → Log tab. */
@Composable
private fun RunStrip(last: MainViewModel.LogLine, running: Boolean, onOpenLog: () -> Unit) {
    val color = when {
        running -> MaterialTheme.colorScheme.onSurface
        last.ok == true -> PassGreen
        last.ok == false -> Bad
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpenLog)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            NSpinner(size = 14.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            last.text.trim(),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.ChevronRight, contentDescription = tr("Mở nhật ký", "Open log"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shown once at the top of a Shizuku-only tab instead of a lock label on every item. */
@Composable
private fun ShizukuBanner(state: ShizukuManager.State, vm: MainViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(
                tr("Bật/tắt các mục dưới đây cần Shizuku. Trạng thái chỉ đọc được một phần.", "Changing items below needs Shizuku. Status is partly readable."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (state == ShizukuManager.State.NEEDS_PERMISSION) {
                TextButton(onClick = { vm.requestPermission() }) { Text(tr("CẤP QUYỀN", "GRANT")) }
            } else {
                TextButton(onClick = { vm.openShizukuApp() }) { Text(tr("MỞ SHIZUKU", "OPEN SHIZUKU")) }
            }
        }
    }
}

@Composable
private fun BloatList(vm: MainViewModel, enabled: Boolean, onRemove: () -> Unit) {
    when {
        vm.appsLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
            NSpinner()
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
                                .toggleable(
                                    value = bloat.pkg in vm.bloatSelected,
                                    enabled = enabled,
                                    role = Role.Checkbox,
                                ) { vm.toggleBloat(bloat.pkg) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = bloat.pkg in vm.bloatSelected,
                                onCheckedChange = null,
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

// ---------- Setup building blocks ----------

/** Battery-cost tag shown on every setup. */
@Composable
fun BatteryTag(impact: BatteryImpact) {
    val color = when (impact) {
        BatteryImpact.SAVES -> PassGreen
        BatteryImpact.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        BatteryImpact.LOW -> MaterialTheme.colorScheme.onSurface
        BatteryImpact.MEDIUM -> Amber
        BatteryImpact.HIGH -> Bad
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
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Dims [content] and swallows every touch while [locked] (Shizuku not active); see [ShizukuBanner]. */
@Composable
fun Locked(locked: Boolean, content: @Composable () -> Unit) {
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

/** One on/off setup as a list row: title + switch, one-line description, battery/status tags. */
@Composable
fun TweakRow(
    title: String,
    desc: String,
    battery: BatteryImpact,
    status: TweakStatus,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            NSwitch(
                checked = status == TweakStatus.APPLIED,
                onCheckedChange = onToggle,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BatteryTag(battery)
            Spacer(Modifier.width(10.dp))
            StatusBadge(status)
        }
        extra()
    }
}

// ---------- FCM Guard ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FcmGuardCard(vm: MainViewModel) {
    val busy = vm.running
    var showList by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Bảo vệ kết nối FCM", "Protect FCM connection"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr(
                            "Giữ Google Play services trong danh sách không hạn chế nền, tự thêm lại khi HyperOS xoá.",
                            "Keeps Google Play services in the no-restrict list and re-adds it when HyperOS drops it.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                NSwitch(
                    checked = vm.fcmGuardOn,
                    onCheckedChange = { vm.setFcmGuard(it) },
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "FCM Guard" },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BatteryTag(BatteryImpact.LOW)
                Spacer(Modifier.width(10.dp))
                val (txt, color) = when {
                    !vm.fcmChecked -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
                    vm.fcmHasGms -> tr("CÓ GMS", "GMS PRESENT") to PassGreen
                    else -> tr("THIẾU GMS", "GMS MISSING") to Bad
                }
                StatusDot(color, Modifier.height(8.dp))
                Spacer(Modifier.width(5.dp))
                Text(txt, style = MaterialTheme.typography.labelMedium, color = color)
            }
            if (!vm.fcmCanWrite) {
                WarnRow(tr("Chưa có quyền Sửa cài đặt hệ thống", "Modify system settings not granted"), tr("CẤP QUYỀN", "GRANT")) { vm.openWriteSettings() }
            }
            if (vm.fcmNotifBlocked) {
                WarnRow(tr("Bật thông báo trong Cài đặt để chạy thường trực", "Turn on notifications in Settings for persistent mode"), tr("MỞ CÀI ĐẶT", "OPEN SETTINGS")) { vm.openNotificationSettings() }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Thông báo thường trực", "Persistent notification"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        tr("Im lặng, giúp HyperOS không tắt dịch vụ", "Silent; keeps HyperOS from killing the service"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                NSwitch(
                    checked = vm.fcmPersistent,
                    onCheckedChange = { vm.updateFcmPersistent(it) },
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = tr("Thông báo thường trực", "Persistent notification") },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            // The raw list is long and rarely needed: collapsed behind a toggle.
            if (vm.fcmChecked) {
                val entries = vm.fcmValue?.split(',')?.count { it.isNotBlank() } ?: 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showList = !showList }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr("Danh sách không hạn chế", "No-restrict list") + " [$entries]",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (showList) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showList) tr("Thu gọn", "Collapse") else tr("Mở rộng", "Expand"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showList) {
                    Text(
                        vm.fcmValue?.replace(",", ", ") ?: tr("(chưa có giá trị trên ROM này)", "(not set on this ROM)"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // One primary action; the rarely used ones live in the overflow menu.
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.repairFcm() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(tr("SỬA NGAY", "REPAIR NOW"))
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = tr("Thêm", "More"))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("Kết nối lại FCM", "Reconnect FCM")) },
                            onClick = { menu = false; vm.reconnectFcm() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Chẩn đoán FCM (GMS)", "FCM diagnostics (GMS)")) },
                            onClick = { menu = false; vm.openFcmDiagnostics() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarnRow(text: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(Bad, Modifier.height(8.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Bad, modifier = Modifier.weight(1f))
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
        if (vm.fcmScanning) SpinnerSlot()
        else TextButton(onClick = { vm.scanFcmApps() }) { Text(if (apps == null) tr("QUÉT", "SCAN") else tr("QUÉT LẠI", "RESCAN")) }
    }
    if (apps != null) {
        FcmAppsList(apps)
        OutlinedButton(onClick = { vm.openAutostartManager() }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("CẤU HÌNH TỰ KHỞI CHẠY TRONG HYPEROS", "CONFIGURE AUTOSTART IN HYPEROS"))
        }
    }
}

/** Scan result in its own scroll box (about 6 rows): Autostart off/partial first, then on. */
@Composable
private fun FcmAppsList(apps: List<Pair<FcmAppScanner.AppEntry, AutostartStatusReader.Status>>) {
    // Never treat Unknown as Disabled: if HyperOS hides every state, show only the count.
    val readable = apps.any { it.second != AutostartStatusReader.Status.UNKNOWN }
    if (!readable) {
        Text(
            tr("HyperOS không cho đọc trạng thái Tự khởi chạy — hãy kiểm tra trong cài đặt.", "HyperOS hides the Autostart state. Check it in settings."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        val order = listOf(
            AutostartStatusReader.Status.DISABLED, AutostartStatusReader.Status.PARTIAL,
            AutostartStatusReader.Status.UNKNOWN, AutostartStatusReader.Status.ENABLED,
        )
        val sorted = apps.sortedWith(compareBy({ order.indexOf(it.second) }, { it.first.label.lowercase() }))
        val off = apps.count { it.second == AutostartStatusReader.Status.DISABLED || it.second == AutostartStatusReader.Status.PARTIAL }
        val listScroll = rememberScrollState()
        Text(
            "// " + (if (off > 0) tr("$off app chưa bật Tự khởi chạy", "$off apps without Autostart") else tr("tất cả đã bật Tự khởi chạy", "Autostart on for all")) +
                if (apps.size > 6) tr(" · cuộn trong khung", " · scroll inside the box") else "",
            style = MaterialTheme.typography.labelMedium,
            color = if (off > 0) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .heightIn(max = 260.dp)
                .verticalScroll(listScroll)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            sorted.forEach { (app, st) ->
                val color = when (st) {
                    AutostartStatusReader.Status.ENABLED -> PassGreen
                    AutostartStatusReader.Status.PARTIAL -> Amber
                    AutostartStatusReader.Status.DISABLED -> Bad
                    AutostartStatusReader.Status.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color, Modifier.height(8.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(app.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(st.label.text, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }
    }
}

// ---------- Shizuku ----------

@Composable
private fun ShizukuCard(state: ShizukuManager.State, vm: MainViewModel) {
    val d = vm.shizukuDiag
    var details by rememberSaveable { mutableStateOf(false) }
    val (status, dot) = when {
        state == ShizukuManager.State.READY && d != null && d.permission && !d.execOk && !vm.shizukuTesting ->
            tr("Đã cấp quyền · lỗi chạy lệnh", "Granted · commands fail") to Amber
        else -> when (state) {
        ShizukuManager.State.READY -> tr("Đang hoạt động", "Active") +
            (d?.takeIf { it.execOk }?.let { " · ${it.mode} · ${it.execMs} ms" } ?: "") to PassGreen
        ShizukuManager.State.NEEDS_PERMISSION -> tr("Cần cấp quyền cho NotiGuard", "NotiGuard needs permission") to Amber
        ShizukuManager.State.UNAVAILABLE -> tr("Chưa chạy", "Not running") to Bad
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(dot)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Shizuku", style = MaterialTheme.typography.titleMedium)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = dot)
                }
                when {
                    vm.shizukuTesting -> SpinnerSlot()
                    state == ShizukuManager.State.NEEDS_PERMISSION ->
                        Button(onClick = { vm.requestPermission() }) { Text(tr("CẤP QUYỀN", "GRANT")) }
                    state == ShizukuManager.State.UNAVAILABLE ->
                        OutlinedButton(onClick = { vm.openShizukuApp() }) { Text(tr("MỞ SHIZUKU", "OPEN SHIZUKU")) }
                    else -> TextButton(onClick = { vm.testShizuku() }) { Text(tr("KIỂM TRA", "TEST")) }
                }
            }
            if (state == ShizukuManager.State.UNAVAILABLE) {
                Text(
                    tr(
                        "Mở app Shizuku và khởi động dịch vụ (Wireless debugging hoặc root) để dùng tab Tối ưu và Ứng dụng.",
                        "Start the service in the Shizuku app (Wireless debugging or root) to use the Tweaks and Apps tabs.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Permission granted but the UserService never starts: on HyperOS/MIUI the shell uid
            // cannot grant permissions (Shizuku's starter fails) unless this developer option is on.
            if (state == ShizukuManager.State.READY && d != null && d.permission && !d.execOk && !vm.shizukuTesting) {
                Text(
                    tr(
                        "Shizuku chạy nhưng không thực thi được lệnh. Trên HyperOS: bật Tuỳ chọn nhà phát triển → " +
                            "\"Gỡ lỗi USB (Cài đặt bảo mật)\", rồi khởi động lại Shizuku.",
                        "Shizuku is running but cannot run commands. On HyperOS: turn on Developer options → " +
                            "\"USB debugging (Security settings)\", then restart Shizuku.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                )
            }
            if (d != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { details = !details }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr("Chi tiết kết nối", "Connection details"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (details) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (details) tr("Thu gọn", "Collapse") else tr("Mở rộng", "Expand"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (details) {
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
                    TextButton(onClick = { vm.testShizuku() }, enabled = !vm.shizukuTesting) {
                        Text(tr("KIỂM TRA LẠI", "TEST AGAIN"))
                    }
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
        Text(value, style = MaterialTheme.typography.labelMedium, color = if (ok) PassGreen else Bad)
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
                SpinnerSlot()
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

/**
 * App check on the per-app push list: one line per app (issues first) instead of a full checklist
 * per app. Tapping a line shows only what is not OK; healthy apps stay one line.
 */
@Composable
private fun AppCheckSection(vm: MainViewModel, canFix: Boolean, noShizuku: Boolean, onPick: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val listScroll = rememberScrollState()
    fun failsOf(pkg: String) =
        vm.checkResults[pkg]?.count { (id, r) -> id in TweakCatalog.scoredChecks && r.state == CheckState.FAIL }
    // Issues first (most failing on top), then not checked yet, then healthy; by name within a group.
    val pkgs = vm.checkPkgs.sortedWith(
        compareBy<String>({ val f = failsOf(it); if (f == null) 1 else if (f == 0) 2 else -f }, { vm.labelFor(it).lowercase() }),
    )
    val checked = pkgs.count { vm.checkResults[it] != null }
    val needFix = pkgs.count { (failsOf(it) ?: 0) > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            tr("Chọn app cần nhận thông báo đúng giờ (nhắn tin, ngân hàng…).", "Pick the apps whose notifications must arrive on time (chat, banking…).") +
                if (noShizuku) tr(" Không có Shizuku: chỉ đọc một phần, không sửa được.", " Without Shizuku: partial read, no fixing.") else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPick, enabled = !vm.running, modifier = Modifier.weight(1f)) {
                Text(if (vm.appsLoading) tr("ĐANG TẢI…", "LOADING…") else tr("CHỌN APP", "PICK APPS") + " [${pkgs.size}]")
            }
            Button(onClick = { vm.runAppChecks() }, enabled = !vm.running && pkgs.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text(tr("KIỂM TRA", "CHECK"))
            }
        }
        if (pkgs.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (checked == 0) "// " + tr("chưa kiểm tra", "not checked yet")
                    else "// ${pkgs.size} app · " + when {
                        needFix > 0 -> tr("$needFix cần sửa", "$needFix need fixing")
                        checked < pkgs.size -> tr("đã kiểm tra $checked/${pkgs.size}", "checked $checked/${pkgs.size}")
                        else -> tr("tất cả đã đủ", "all good")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (needFix > 0) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (vm.fixableCount > 0) {
                    TextButton(onClick = { vm.fixAllChecked() }, enabled = canFix) {
                        Text(tr("SỬA TẤT CẢ", "FIX ALL") + " [${vm.fixableCount}]")
                    }
                }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                // Own scroll box so a long app list never stretches the tab.
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(listScroll)) {
                    pkgs.forEachIndexed { i, pkg ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        AppCheckRow(
                            label = vm.labelFor(pkg),
                            results = vm.checkResults[pkg],
                            expanded = expanded == pkg,
                            onToggle = { expanded = if (expanded == pkg) null else pkg },
                            canFix = canFix,
                            onFix = { vm.fixApp(pkg) },
                            onOpenSettings = { checkId -> vm.openSettings(checkId, pkg) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCheckRow(
    label: String,
    results: Map<String, CheckResult>?,
    expanded: Boolean,
    onToggle: () -> Unit,
    canFix: Boolean,
    onFix: () -> Unit,
    onOpenSettings: (checkId: String) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val scoredFails = TweakCatalog.appChecks.filter { !it.optional && results?.get(it.id)?.state == CheckState.FAIL }
    val ok = results?.count { (id, r) -> id in TweakCatalog.scoredChecks && r.state == CheckState.OK } ?: 0
    val fixable = scoredFails.count { it.fix != null }
    val good = results != null && scoredFails.isEmpty()
    val dot = if (results == null) muted else if (good) PassGreen else Bad
    val summary = when {
        results == null -> tr("chưa kiểm tra", "not checked")
        good -> tr("đủ", "all good")
        else -> scoredFails.joinToString(", ") { it.title.text }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = results != null, onClick = onToggle)
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(dot, Modifier.height(8.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = if (results != null && !good) Bad else muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (results != null) {
                Text("$ok/${ok + scoredFails.size}", style = MaterialTheme.typography.labelLarge, color = if (good) PassGreen else Bad)
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) tr("Thu gọn", "Collapse") else tr("Chi tiết", "Details"),
                    tint = muted,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        if (expanded && results != null) {
            // Only what is not OK; the OK items are summed up in one line.
            Column(Modifier.padding(start = 32.dp, end = 8.dp, bottom = 8.dp)) {
                TweakCatalog.appChecks.filter { results[it.id]?.state != CheckState.OK }.forEach { check ->
                    val r = results[check.id] ?: CheckResult(CheckState.NA)
                    val important = r.state == CheckState.FAIL && !check.optional
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(check.title.text, style = MaterialTheme.typography.bodyMedium, color = if (important) Bad else muted)
                            val note = listOfNotNull(
                                tr("tuỳ chọn · cho cuộc gọi", "optional · for calls").takeIf { check.optional },
                                TweakCatalog.detailText(r.detail).takeIf { r.detail.isNotEmpty() },
                            ).joinToString(" · ")
                            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = muted)
                        }
                        if (r.state == CheckState.FAIL) {
                            IconButton(onClick = { onOpenSettings(check.id) }) {
                                Icon(Icons.Filled.Settings, contentDescription = tr("Mở cài đặt", "Open settings"), modifier = Modifier.size(18.dp), tint = muted)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("+ $ok mục đã bật", "+ $ok items on"),
                        style = MaterialTheme.typography.bodySmall,
                        color = PassGreen,
                        modifier = Modifier.weight(1f),
                    )
                    if (fixable > 0) {
                        OutlinedButton(onClick = onFix, enabled = canFix) { Text(tr("SỬA", "FIX") + " [$fixable]") }
                    }
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
    /** Shown checked and not toggleable (add-only pickers: already in the list). */
    fixed: Set<String> = emptySet(),
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
                                .toggleable(value = app.pkg in selected, enabled = app.pkg !in fixed, role = Role.Checkbox) { onToggle(app.pkg) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = app.pkg in selected,
                                onCheckedChange = null,
                                enabled = app.pkg !in fixed,
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
                        NSpinner()
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

/** Spinner in the 48dp slot of the button it replaces, so the row keeps its height and alignment. */
@Composable
private fun SpinnerSlot() {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { NSpinner() }
}
