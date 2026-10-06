package com.local.notiguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/** Custom header: dot texture + mono uppercase title + optional [actions] + red marker. */
@Composable
fun NothingTopBar(title: String, actions: @Composable () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp)
    ) {
        DotField(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(NothingRed))
            Spacer(Modifier.width(8.dp))
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
