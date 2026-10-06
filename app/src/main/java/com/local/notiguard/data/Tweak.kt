package com.local.notiguard.data

import com.local.notiguard.Txt

/**
 * One optimization step = an [apply] command plus an optional verification.
 *
 * If [verify] is set, after running [apply] we run [verify] and consider the step successful
 * only when its output contains [expect]. This turns "fire and hope" into a checked result.
 */
data class Step(
    val apply: String,
    val verify: String? = null,
    val expect: String? = null,
) {
    /** True when [output] of [verify] shows [expect] as a whole token ("0" must not match "10"). */
    fun matches(output: String): Boolean = expect != null && expectMatches(output, expect)
}

/**
 * Whole-token match: [expect] must not be glued to letters, digits, '_' or '.' on either side,
 * so "0" ≠ "10", "allow" matches "WAKE_LOCK: allow;", and a package matches "user,pkg,10123"
 * but not "pkg.extra".
 */
fun expectMatches(output: String, expect: String): Boolean =
    Regex("(^|[^A-Za-z0-9_.])" + Regex.escape(expect) + "($|[^A-Za-z0-9_.])").containsMatchIn(output.trim())

/** How much a setup costs in battery once it is ON — shown as a tag on every setup card. */
enum class BatteryImpact(val label: Txt) {
    SAVES(Txt("TIẾT KIỆM PIN", "SAVES BATTERY")),
    NONE(Txt("PIN: ~0", "BATTERY: ~0")),
    LOW(Txt("PIN: THẤP", "BATTERY: LOW")),
    MEDIUM(Txt("PIN: TRUNG BÌNH", "BATTERY: MEDIUM")),
    HIGH(Txt("PIN: CAO", "BATTERY: HIGH")),
}

/**
 * One on/off setup.
 * - [steps] turn it ON (each applied then verified); [revert] commands turn it OFF.
 */
data class Tweak(
    val id: String,
    val title: Txt,
    val desc: Txt,
    val steps: List<Step>,
    val revert: List<String>,
    val battery: BatteryImpact,
)

/** An app installed on the device, offered for the per-app push optimization. */
data class InstalledApp(
    val pkg: String,
    val label: String,
)

/** A known China-ROM bloatware package. */
data class Bloat(
    val pkg: String,
    val label: Txt,
)

/** Whether a tweak's current on-device state matches what it would apply. */
enum class TweakStatus { APPLIED, NOT_APPLIED, UNKNOWN }

/**
 * One per-app health check (notifications, autostart, background, battery…).
 *
 * [probe] is a shell snippet ("%s" = package) that prints exactly one line:
 * `OK [detail]`, `NO [detail]` or `NA [detail]` (not supported / unreadable on this ROM).
 * [fix] (optional, "%s" = package) turns the setting on.
 */
data class AppCheck(
    val id: String,
    val title: Txt,
    val probe: String,
    val fix: String? = null,
    /** Not needed for on-time notifications (only for call screens): not scored, not in FIX / FIX ALL. */
    val optional: Boolean = false,
)

enum class CheckState { OK, FAIL, NA }

data class CheckResult(val state: CheckState, val detail: String = "")
