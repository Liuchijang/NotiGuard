package com.local.notiguard.data

import com.local.notiguard.Txt
import com.local.notiguard.tr

/**
 * The curated command set for fixing slow notifications, background delivery and removing
 * China-ROM bloatware on Xiaomi/HyperOS. Every command here was recovered from the original
 * Xiaomi Toolkit command vault (the real, device-tested commands) and regrouped cleanly.
 *
 * All commands are executed via Shizuku (shell uid), so no root is required.
 */
object TweakCatalog {

    /**
     * System-wide setups. Each one is an on/off switch: [Tweak.steps] turn it on (applied, then
     * read back), [Tweak.revert] turns it off (the "settings delete …" / enable commands from the
     * original vault, i.e. back to ROM defaults). Status = every verify matches its expect.
     */
    val system: List<Tweak> = listOf(
        Tweak(
            id = "kill_freezer",
            title = Txt("Tắt App Freezer", "Disable App Freezer"),
            desc = Txt("Không đóng băng app nền — nguyên nhân chính khiến thông báo về chậm.", "Stop freezing background apps, the main cause of late notifications."),
            battery = BatteryImpact.HIGH,
            // Only the global setting: ActivityManager observes it live and it overrides the
            // device_config default (verified on HyperOS 3 / Android 16: "use_freezer=false" in
            // `dumpsys activity`). The old device_config steps are rejected for the shell uid on
            // Android 16 ("must add flag to the allowlist"), so the switch could never read ON.
            steps = listOf(
                Step(
                    "settings put global cached_apps_freezer disabled",
                    "settings get global cached_apps_freezer", "disabled",
                ),
            ),
            revert = listOf("settings delete global cached_apps_freezer"),
        ),
        Tweak(
            id = "kill_adaptive_battery",
            title = Txt("Tắt pin thích ứng & App Standby", "Disable Adaptive Battery & App Standby"),
            desc = Txt("Hệ thống không tự đẩy app ít dùng vào ngủ/standby.", "The system stops putting rarely used apps to sleep/standby."),
            battery = BatteryImpact.MEDIUM,
            steps = listOf(
                Step(
                    "settings put global app_standby_enabled 0",
                    "settings get global app_standby_enabled", "0",
                ),
                Step(
                    "settings put global adaptive_battery_management_enabled 0",
                    "settings get global adaptive_battery_management_enabled", "0",
                ),
            ),
            revert = listOf(
                "settings delete global app_standby_enabled",
                "settings delete global adaptive_battery_management_enabled",
            ),
        ),
        Tweak(
            id = "disable_doze",
            title = Txt("Tắt Doze", "Disable Doze"),
            desc = Txt("Máy không vào nghỉ sâu khi tắt màn hình. Tự bật lại sau khi khởi động lại máy.", "No deep idle while the screen is off. Turns back on after a reboot."),
            battery = BatteryImpact.HIGH,
            steps = listOf(
                Step(
                    "dumpsys deviceidle disable",
                    "dumpsys deviceidle enabled", "0",
                ),
            ),
            revert = listOf("dumpsys deviceidle enable"),
        ),
        Tweak(
            id = "gms_wakelock",
            title = Txt("Giữ thức GMS & Điện thoại", "Keep GMS & Phone awake"),
            desc = Txt("Cho Google Play services và app Điện thoại giữ wakelock để nhận push/cuộc gọi đúng lúc.", "Let Google Play services and Phone hold wakelocks for on-time push and calls."),
            battery = BatteryImpact.LOW,
            steps = listOf(
                Step(
                    "cmd appops set com.google.android.gms WAKE_LOCK allow",
                    explicitAllow("com.google.android.gms", "WAKE_LOCK"), "OK",
                ),
                Step(
                    "cmd appops set com.android.phone WAKE_LOCK allow",
                    explicitAllow("com.android.phone", "WAKE_LOCK"), "OK",
                ),
            ),
            revert = listOf(
                "cmd appops set com.google.android.gms WAKE_LOCK default",
                "cmd appops set com.android.phone WAKE_LOCK default",
            ),
        ),
        Tweak(
            id = "lockscreen_notifications",
            title = Txt("Thông báo trên màn khóa", "Lock screen notifications"),
            desc = Txt("Hiện thông báo và nội dung riêng tư trên màn hình khóa.", "Show notifications and private content on the lock screen."),
            battery = BatteryImpact.NONE,
            steps = listOf(
                Step(
                    "settings put secure lock_screen_show_notifications 1",
                    "settings get secure lock_screen_show_notifications", "1",
                ),
                Step(
                    "settings put secure lock_screen_allow_private_notifications 1",
                    "settings get secure lock_screen_allow_private_notifications", "1",
                ),
            ),
            // Off = hide private content only; notifications stay visible on the lock screen.
            revert = listOf("settings put secure lock_screen_allow_private_notifications 0"),
        ),
        Tweak(
            id = "mobile_data_always_on",
            title = Txt("Tắt data di động luôn bật", "Disable mobile data always on"),
            desc = Txt("Không giữ mạng di động song song khi đang dùng Wi-Fi.", "Do not keep mobile data up while on Wi-Fi."),
            battery = BatteryImpact.SAVES,
            steps = listOf(
                Step(
                    "settings put global mobile_data_always_on 0",
                    "settings get global mobile_data_always_on", "0",
                ),
            ),
            revert = listOf("settings delete global mobile_data_always_on"),
        ),
    )

    /**
     * Prints OK when [op] is *explicitly* set to allow for [pkg]. For ops whose default mode is
     * already "allow" (WAKE_LOCK…), `appops get` shows "allow" after a revert to default too, so the
     * switch would never read OFF. `query-op` lists only explicitly set packages (Android 9+);
     * older ROMs without it fall back to `appops get`.
     */
    private fun explicitAllow(pkg: String, op: String) = listOf(
        "q=\$(cmd appops query-op $op allow 2>&1)",
        "case \"\$q\" in",
        // Match only error text, not package names ("*sage*" would hit com.android.messages).
        "  Error:*|*nknown\\ command*|*nknown\\ op*) cmd appops get $pkg $op 2>/dev/null | grep -q allow && echo OK || echo NO ;;",
        "  *) echo \"\$q\" | grep -qx '$pkg' && echo OK || echo NO ;;",
        "esac",
    ).joinToString("\n")

    // MIUI-only app-op probe: allow → OK, ignore/deny/unset → NO, unknown op (non-MIUI) → NA.
    private fun miuiOpProbe(op: Int) = listOf(
        "m=\$(cmd appops get %s $op 2>&1)",
        "case \"\$m\" in",
        "  *nknown*|*rror*|*xception*|*nvalid*) echo 'NA unsupported' ;;",
        "  *allow*) echo OK ;;",
        "  *ignore*|*deny*) echo NO ;;",
        "  *) echo 'NO default_off' ;;",
        "esac",
    ).joinToString("\n")

    // Generic app-op probe: blocked only when explicitly ignore/deny (default = allowed).
    private fun opNotBlockedProbe(vararg ops: String) = listOf(
        "m=\"\"",
        "for op in ${ops.joinToString(separator = SPACE)}; do m=\"\$m \$(cmd appops get %s \$op 2>&1)\"; done",
        "case \"\$m\" in *ignore*|*deny*) echo 'NO blocked' ;; *) echo OK ;; esac",
    ).joinToString("\n")

    private const val SPACE = " "

    // `pm list packages -U` first; dumpsys fallback. Android 16 renamed dumpsys "userId=" to "appId=".
    private const val UID_OF =
        "uid=\$(cmd package list packages -U '%s' 2>/dev/null | sed -n 's/^package:%s uid:\\([0-9][0-9]*\\).*/\\1/p' | head -n 1); " +
            "[ -n \"\$uid\" ] || uid=\$(dumpsys package '%s' | sed -n -e 's/.*userId=\\([0-9][0-9]*\\).*/\\1/p' -e 's/.*appId=\\([0-9][0-9]*\\).*/\\1/p' | head -n 1)"

    /**
     * Per-app health checks: read-only probes that tell whether each setting needed for
     * on-time notifications is turned on, plus an optional fix. "%s" = package.
     */
    val appChecks: List<AppCheck> = listOf(
        AppCheck(
            id = "notif",
            title = Txt("Quyền thông báo", "Notification permission"),
            probe = listOf(
                "p=1",
                "if [ \"\$(getprop ro.build.version.sdk)\" -ge 33 ]; then",
                "  dumpsys package %s | grep -q 'POST_NOTIFICATIONS: granted=true' || p=0",
                "fi",
                "case \"\$(cmd appops get %s POST_NOTIFICATION 2>&1)\" in *ignore*|*deny*) p=0 ;; esac",
                "[ \$p = 1 ] && echo OK || echo 'NO notif_off'",
            ).joinToString("\n"),
            fix = "pm grant %s android.permission.POST_NOTIFICATIONS >/dev/null 2>&1; " +
                "cmd appops set %s POST_NOTIFICATION allow",
        ),
        AppCheck(
            id = "autostart",
            title = Txt("Tự khởi chạy (Autostart)", "Autostart"),
            probe = miuiOpProbe(10008),
            fix = "cmd appops set %s 10008 allow",
        ),
        AppCheck(
            id = "background",
            title = Txt("Chạy nền", "Background run"),
            probe = opNotBlockedProbe("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND"),
            fix = "cmd appops set %s RUN_IN_BACKGROUND allow; cmd appops set %s RUN_ANY_IN_BACKGROUND allow",
        ),
        AppCheck(
            id = "battery",
            title = Txt("Bỏ tối ưu pin (Doze whitelist)", "No battery optimization (Doze whitelist)"),
            probe = "dumpsys deviceidle whitelist | grep -q ',%s,' && echo OK || echo 'NO optimized'",
            fix = "dumpsys deviceidle whitelist +%s",
        ),
        AppCheck(
            id = "bucket",
            title = Txt("Standby bucket", "Standby bucket"),
            probe = listOf(
                "b=\$(am get-standby-bucket %s 2>/dev/null | tr -d '\\r\\n ')",
                "case \"\$b\" in",
                "  5) echo 'OK exempted' ;; 10) echo 'OK active' ;;",
                "  20) echo 'NO working_set' ;; 30) echo 'NO frequent' ;; 40) echo 'NO rare' ;;",
                "  45) echo 'NO restricted' ;; 50) echo 'NO never' ;;",
                "  '') echo NA ;; *) echo \"NO \$b\" ;;",
                "esac",
            ).joinToString("\n"),
            fix = "am set-standby-bucket %s active",
        ),
        AppCheck(
            id = "wakelock",
            title = Txt("Giữ wakelock", "Wakelock"),
            probe = opNotBlockedProbe("WAKE_LOCK"),
            fix = "cmd appops set %s WAKE_LOCK allow",
        ),
        AppCheck(
            id = "bgdata",
            title = Txt("Dữ liệu nền", "Background data"),
            probe = listOf(
                UID_OF,
                "if [ -z \"\$uid\" ]; then echo NA",
                "elif cmd netpolicy list restrict-background-blacklist 2>/dev/null | grep -qw \"\$uid\"; then echo 'NO bgdata_blocked'",
                "elif cmd netpolicy list restrict-background-whitelist 2>/dev/null | grep -qw \"\$uid\"; then echo 'OK whitelist'",
                "elif cmd netpolicy get restrict-background 2>/dev/null | grep -q enabled; then echo 'NO data_saver'",
                "else echo OK",
                "fi",
            ).joinToString("\n"),
            fix = "$UID_OF; [ -n \"\$uid\" ] && " +
                "{ cmd netpolicy remove restrict-background-blacklist \"\$uid\" >/dev/null 2>&1; " +
                "cmd netpolicy add restrict-background-whitelist \"\$uid\"; }",
        ),
        AppCheck(
            id = "alive",
            title = Txt("Không bị dừng / tắt", "Not stopped / disabled"),
            probe = listOf(
                "l=\$(dumpsys package %s | grep -m 1 'User 0:')",
                "if pm list packages -d %s 2>/dev/null | grep -qx 'package:%s'; then echo 'NO disabled'",
                "else case \"\$l\" in",
                "  *suspended=true*) echo 'NO suspended' ;;",
                "  *stopped=true*) echo 'NO stopped' ;;",
                "  *) echo OK ;;",
                "esac; fi",
            ).joinToString("\n"),
            fix = "pm unsuspend --user 0 %s >/dev/null 2>&1; pm enable --user 0 %s >/dev/null 2>&1; true",
        ),
        AppCheck(
            id = "popup",
            title = Txt("Mở cửa sổ khi chạy nền (MIUI)", "Open windows from background (MIUI)"),
            probe = miuiOpProbe(10021),
            fix = "cmd appops set %s 10021 allow",
            optional = true,
        ),
        AppCheck(
            id = "lockscreen",
            title = Txt("Hiện trên màn khóa (MIUI)", "Show on lock screen (MIUI)"),
            probe = miuiOpProbe(10020),
            fix = "cmd appops set %s 10020 allow",
            optional = true,
        ),
    )

    /** One script probing every [appChecks] entry for [pkg]; output = "@@id" + one result line each. */
    fun appCheckScript(pkg: String): String = appChecks.joinToString("\n") {
        "echo '@@${it.id}'\n{\n${it.probe.replace("%s", pkg)}\n} 2>/dev/null | head -n 1"
    }

    /** Parse [appCheckScript] output into a result per check id (missing → NA). */
    fun parseAppCheck(output: String): Map<String, CheckResult> {
        val raw = HashMap<String, String>()
        var cur: String? = null
        output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("@@") -> cur = line.removePrefix("@@")
                cur != null -> { raw[cur!!] = line; cur = null }
            }
        }
        return appChecks.associate { c ->
            val line = raw[c.id]
            val token = line?.substringBefore(' ')
            val detail = line?.substringAfter(' ', "")?.trim().orEmpty()
            c.id to when (token) {
                "OK" -> CheckResult(CheckState.OK, detail)
                "NO" -> CheckResult(CheckState.FAIL, detail)
                else -> CheckResult(CheckState.NA, detail.ifEmpty { "unreadable" })
            }
        }
    }

    /** ids of [appChecks] that count toward the score and FIX (everything except optional ones). */
    val scoredChecks: Set<String> = appChecks.filter { !it.optional }.map { it.id }.toSet()

    /** Display text for a [CheckResult.detail] key, in the current language. */
    fun detailText(detail: String): String = DETAILS[detail]?.text ?: detail

    /** Probe detail keys → display text. Unlisted details (bucket names…) are shown as is. */
    private val DETAILS = mapOf(
        "unsupported" to Txt("ROM không hỗ trợ", "not supported by ROM"),
        "unreadable" to Txt("không đọc được", "unreadable"),
        "needs_shizuku" to Txt("cần Shizuku để đọc", "needs Shizuku to read"),
        "default_off" to Txt("mặc định (tắt)", "default (off)"),
        "blocked" to Txt("bị chặn", "blocked"),
        "notif_off" to Txt("thông báo đang bị tắt", "notifications are off"),
        "optimized" to Txt("đang bị tối ưu pin", "battery optimized"),
        "bgdata_blocked" to Txt("bị chặn data nền", "background data blocked"),
        "data_saver" to Txt("Tiết kiệm dữ liệu đang chặn", "blocked by Data Saver"),
        "disabled" to Txt("app đang bị tắt", "app is disabled"),
        "suspended" to Txt("bị đình chỉ", "suspended"),
        "stopped" to Txt("đang bị force-stop — mở app 1 lần", "force-stopped, open the app once"),
    )

    /**
     * 38 China-ROM bloatware packages with Vietnamese/English labels, recovered from the original
     * app's catalog (same order/pairing as the original).
     */
    val bloatware: List<Bloat> = listOf(
        Bloat("com.miui.yellowpage", Txt("Trang vàng Xiaomi", "Xiaomi Yellow Pages")),
        Bloat("com.miui.systemAdSolution", Txt("Quảng cáo MIUI", "MIUI ads")),
        Bloat("com.miui.analytics", Txt("Thu thập dữ liệu", "Analytics")),
        Bloat("com.miui.bugreport", Txt("Báo cáo lỗi", "Bug report")),
        Bloat("com.miui.miservice", Txt("Services + Feedback", "Services + Feedback")),
        Bloat("com.miui.smsextra", Txt("SMS Extra", "SMS Extra")),
        Bloat("com.miui.userguide", Txt("Hướng dẫn TQ", "User guide (CN)")),
        Bloat("com.miui.translation.youdao", Txt("Dịch thuật Youdao", "Youdao translation")),
        Bloat("com.miui.translation.kingsoft", Txt("Dịch thuật Kingsoft", "Kingsoft translation")),
        Bloat("com.miui.translation.xmcloud", Txt("Dịch thuật Xiaomi Cloud", "Xiaomi Cloud translation")),
        Bloat("com.miui.translationservice", Txt("Service dịch thuật", "Translation service")),
        Bloat("com.miui.voicetrigger", Txt("Wake word Mi AI", "Mi AI wake word")),
        Bloat("com.iflytek.inputmethod.miui", Txt("Bàn phím iFlytek", "iFlytek keyboard")),
        Bloat("com.xiaomi.aiasst.service", Txt("Mi AI service nền", "Mi AI background service")),
        Bloat("com.xiaomi.mibrain.speech", Txt("Nhận dạng giọng nói TQ", "Speech recognition (CN)")),
        Bloat("com.sohu.inputmethod.sogou.xiaomi", Txt("Bàn phím Sogou", "Sogou keyboard")),
        Bloat("com.miui.nextpay", Txt("Thanh toán Xiaomi", "Xiaomi payments")),
        Bloat("com.android.browser", Txt("Mi Browser", "Mi Browser")),
        Bloat("com.miui.tsmclient", Txt("TSM NFC TQ", "NFC TSM (CN)")),
        Bloat("com.miui.vsimcore", Txt("Virtual SIM TQ", "Virtual SIM (CN)")),
        Bloat("com.xiaomi.gamecenter.sdk.service", Txt("Game Center SDK", "Game Center SDK")),
        Bloat("com.xiaomi.payment", Txt("Mi Pay", "Mi Pay")),
        Bloat("com.xiaomi.simactivate.service", Txt("Kích hoạt SIM TQ", "SIM activation (CN)")),
        Bloat("com.xiaomi.miplay_client", Txt("Mi Play TQ", "Mi Play (CN)")),
        Bloat("com.mipay.wallet", Txt("Mi Wallet", "Mi Wallet")),
        Bloat("com.miui.newhome", Txt("Feed tin tức TQ", "News feed (CN)")),
        Bloat("com.miui.scanner", Txt("AI Scanner Xiaomi", "Xiaomi AI Scanner")),
        Bloat("com.baidu.input_mi", Txt("Bàn phím Baidu", "Baidu keyboard")),
        Bloat("com.xiaomi.translator", Txt("Dịch thuật Xiaomi", "Xiaomi Translator")),
        Bloat("com.xiaomi.youpin", Txt("Youpin (Thương mại điện tử)", "Youpin (shopping)")),
        Bloat("com.xiaomi.smarthome", Txt("Mi Home (Nhà thông minh)", "Mi Home (smart home)")),
        Bloat("com.xiaomi.vipaccount", Txt("Mi Community", "Mi Community")),
        Bloat("com.xiaomi.jr", Txt("Mi Finance (Tiền Xing)", "Mi Finance")),
        Bloat("com.miui.compass", Txt("La bàn", "Compass")),
        Bloat("com.miui.virtualsim", Txt("Mi Roaming", "Mi Roaming")),
        Bloat("com.android.email", Txt("Mail (ứng dụng email MIUI)", "Mail (MIUI email)")),
        Bloat("com.duokan.reader", Txt("Reader (Đọc sách)", "Reader (books)")),
        Bloat("com.unionpay.tsmservice.mi", Txt("UnionPay TSM (MIUI)", "UnionPay TSM (MIUI)")),
    )

    // Safe uninstall: gỡ cho user 0; nếu MIUI chặn thì disable; nếu không disable được thì
    // suspend + chặn nền. Chạy nguyên khối như một script sh. "%1$s" = package.
    private fun uninstallScript() = listOf(
        "pm uninstall -k --user 0 %1\$s >/dev/null 2>&1",
        "if pm list packages %1\$s 2>/dev/null | grep -qx \"package:%1\$s\"; then",
        "  pm disable-user --user 0 %1\$s >/dev/null 2>&1",
        "  if pm list packages -d %1\$s 2>/dev/null | grep -qx \"package:%1\$s\"; then",
        "    echo '~ ${tr("MIUI chan go -> da disable thay the", "MIUI blocked uninstall -> disabled instead")}'",
        "  else",
        "    pm suspend --user 0 %1\$s >/dev/null 2>&1",
        "    cmd appops set %1\$s RUN_IN_BACKGROUND ignore >/dev/null 2>&1",
        "    cmd appops set %1\$s WAKE_LOCK ignore >/dev/null 2>&1",
        "    am set-standby-bucket %1\$s restricted >/dev/null 2>&1",
        "    am force-stop %1\$s >/dev/null 2>&1",
        "    echo '~ ${tr("Ca disable lan suspend that bai -> da appops+restrict", "Disable and suspend failed -> appops+restricted")}'",
        "  fi",
        "else",
        "  echo '${tr("Da go", "Removed")}'",
        "fi",
    ).joinToString("\n")

    // Restore: cài lại bằng install-existing + bật lại + trả appops về mặc định.
    private fun restoreScript() = listOf(
        "cmd package install-existing %1\$s >/dev/null 2>&1 || pm install-existing --user 0 %1\$s >/dev/null 2>&1",
        "pm enable --user 0 %1\$s >/dev/null 2>&1",
        "pm unsuspend --user 0 %1\$s >/dev/null 2>&1",
        "cmd appops set %1\$s RUN_IN_BACKGROUND default >/dev/null 2>&1",
        "cmd appops set %1\$s WAKE_LOCK default >/dev/null 2>&1",
        "am set-standby-bucket %1\$s active >/dev/null 2>&1",
        "if pm list packages %1\$s 2>/dev/null | grep -qx \"package:%1\$s\"; then echo '${tr("Da khoi phuc", "Restored")}'; else echo '${tr("Khong khoi phuc duoc", "Restore failed")}'; fi",
    ).joinToString("\n")

    /** One shell script that safely removes [pkg] for the current user. */
    fun removeCommand(pkg: String): String = uninstallScript().replace("%1\$s", pkg)

    /** One shell script that restores a previously removed [pkg]. */
    fun restoreCommand(pkg: String): String = restoreScript().replace("%1\$s", pkg)
}
