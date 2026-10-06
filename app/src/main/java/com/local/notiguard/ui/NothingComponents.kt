package com.local.notiguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.notiguard.Lang
import com.local.notiguard.ui.theme.NothingRed

/** Dot-matrix fill — the signature Nothing/Glyph texture. */
@Composable
fun DotField(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    step: Dp = 9.dp,
    radius: Dp = 1.1.dp,
) {
    Canvas(modifier) {
        val s = step.toPx()
        val r = radius.toPx()
        var y = s / 2f
        while (y < size.height) {
            var x = s / 2f
            while (x < size.width) {
                drawCircle(color = color, radius = r, center = Offset(x, y))
                x += s
            }
            y += s
        }
    }
}

/** Small filled dot status indicator. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(10.dp)) { drawCircle(color = color) }
}

/**
 * The app's one loading indicator: small, thin, red, no track. Always sized on both axes —
 * a height-only modifier leaves the default 40dp width and the ring renders oversized and off-center.
 */
@Composable
fun NSpinner(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    CircularProgressIndicator(
        modifier.size(size),
        color = NothingRed,
        strokeWidth = 2.dp,
        trackColor = Color.Transparent,
    )
}

/** Custom header: dot texture + mono uppercase title + optional [actions] + red marker. */
@Composable
fun NothingTopBar(title: String, actions: @Composable () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        DotField(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            actions()
            Spacer(Modifier.width(12.dp))
            StatusDot(NothingRed)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** Section label: red tick + mono uppercase heading + underline. */
@Composable
fun SectionHeader(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { heading() }) {
            Box(Modifier.size(9.dp).background(NothingRed))
            Spacer(Modifier.width(8.dp))
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/**
 * VI | EN toggle for the top bar: radio-button semantics, 48dp touch target per segment,
 * the active language filled red.
 */
@Composable
fun LangSwitch(current: Lang, onSelect: (Lang) -> Unit) {
    Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        Lang.entries.forEach { lang ->
            val active = lang == current
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .selectable(selected = active, role = Role.RadioButton) { onSelect(lang) }
                    .semantics { contentDescription = if (lang == Lang.VI) "Tiếng Việt" else "English" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    lang.code.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(if (active) NothingRed else Color.Transparent, RoundedCornerShape(2.dp))
                        .border(1.dp, if (active) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Switch with a visible "off" state: the default unchecked border/thumb use the outline color,
 * which is nearly invisible on the black surface. Grey here meets the 3:1 component contrast.
 */
@Composable
fun NSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
