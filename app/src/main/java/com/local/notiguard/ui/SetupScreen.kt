package com.local.notiguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.local.notiguard.I18n
import com.local.notiguard.MainViewModel
import com.local.notiguard.shizuku.ShizukuManager
import com.local.notiguard.tr
import com.local.notiguard.ui.theme.LocalStatusColors

/**
 * First-run setup in the app's own Nothing style. Every permission is granted on its system
 * settings page instead of a system prompt over the app: for this targetSdk-22 app Android kills
 * the process after a grant made in its permission dialog (see [com.local.notiguard.Permissions]).
 * State is re-read when the user comes back ([MainViewModel.onResume]).
 */
@Composable
fun SetupScreen(vm: MainViewModel) {
    val shizuku by vm.shizukuState.collectAsState()
    val status = LocalStatusColors.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { NothingTopBar("NotiGuard") { LangSwitch(I18n.lang) { vm.setLanguage(it) } } },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(tr("Thiết lập ban đầu", "First-run setup"))
            Text(
                tr(
                    "Mỗi quyền được bật trong trang cài đặt của hệ thống. Quay lại đây là NotiGuard tự cập nhật trạng thái.",
                    "Each permission is turned on in its system settings page. Come back here and NotiGuard updates the status.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SetupStep(
                number = "01",
                title = tr("Thông báo", "Notifications"),
                desc = tr(
                    "Cho FCM Guard hiện thông báo thường trực (im lặng) để HyperOS không tắt nó.",
                    "Lets FCM Guard show a silent persistent notification so HyperOS does not kill it.",
                ),
                done = vm.notifReady,
                optional = false,
                action = tr("MỞ CÀI ĐẶT THÔNG BÁO", "OPEN NOTIFICATION SETTINGS"),
                onAction = { vm.openNotificationSettings() },
                okColor = status.ok,
            )
            SetupStep(
                number = "02",
                title = tr("Sửa cài đặt hệ thống", "Modify system settings"),
                desc = tr(
                    "FCM Guard ghi danh sách không hạn chế nền mà không cần root hay Shizuku.",
                    "FCM Guard writes the background no-restrict list without root or Shizuku.",
                ),
                done = vm.fcmCanWrite,
                optional = false,
                action = tr("CẤP QUYỀN", "GRANT"),
                onAction = { vm.openWriteSettings() },
                okColor = status.ok,
            )
            SetupStep(
                number = "03",
                title = "Shizuku",
                desc = tr(
                    "Cần cho các tối ưu hệ thống và kiểm tra app. Shizuku hiện hộp thoại riêng — chọn Cho phép.",
                    "Needed for system tweaks and app checks. Shizuku shows its own dialog — choose Allow.",
                ),
                done = shizuku == ShizukuManager.State.READY,
                optional = true,
                action = when (shizuku) {
                    ShizukuManager.State.NEEDS_PERMISSION -> tr("CẤP QUYỀN SHIZUKU", "GRANT SHIZUKU")
                    ShizukuManager.State.UNAVAILABLE -> tr("MỞ SHIZUKU", "OPEN SHIZUKU")
                    ShizukuManager.State.READY -> null
                },
                onAction = {
                    if (shizuku == ShizukuManager.State.NEEDS_PERMISSION) vm.requestPermission() else vm.openShizukuApp()
                },
                okColor = status.ok,
            )

            Spacer(Modifier.height(4.dp))
            Button(onClick = { vm.finishSetup() }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("VÀO APP", "CONTINUE"))
            }
            Text(
                tr(
                    "Có thể làm sau — các thẻ trong app sẽ nhắc lại mục còn thiếu.",
                    "You can do this later — cards in the app point out what is missing.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetupStep(
    number: String,
    title: String,
    desc: String,
    done: Boolean,
    optional: Boolean,
    action: String?,
    onAction: () -> Unit,
    okColor: Color,
) {
    val (label, color) = when {
        done -> tr("ĐÃ BẬT", "DONE") to okColor
        optional -> tr("TUỲ CHỌN", "OPTIONAL") to MaterialTheme.colorScheme.onSurfaceVariant
        else -> tr("CHƯA BẬT", "NOT SET") to LocalStatusColors.current.bad
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = label },
            ) {
                Text(number, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusDot(color, Modifier.height(8.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            }
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!done && action != null) {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
            }
        }
    }
}
