package com.jayc180.rhythmengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmType
import com.jayc180.rhythmengine.ui.theme.surfaceBg2
import com.jayc180.rhythmengine.ui.theme.surfaceBg3

internal fun formatBpm(bpm: Double): String {
    val cents = (bpm * 100).toLong()
    return when {
        cents % 100 == 0L -> "${cents / 100}"
        cents % 10  == 0L -> "${cents / 100}.${(cents % 100) / 10}"
        else              -> "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }
}

internal fun isValidBpmInput(s: String): Boolean {
    if (s.isEmpty()) return true
    val dots = s.count { it == '.' }
    if (dots > 1) return false
    val dotIdx = s.indexOf('.')
    return if (dotIdx >= 0) {
        val intPart = s.take(dotIdx)
        val decPart = s.drop(dotIdx + 1)
        intPart.length <= 3 && decPart.length <= 2 &&
                intPart.all { it.isDigit() } && decPart.all { it.isDigit() }
    } else {
        s.length <= 3 && s.all { it.isDigit() }
    }
}

enum class ToolButtonState { Normal, On, Editing, Dim }

@Composable
fun ToolButton(
    label:    String,
    state:    ToolButtonState = ToolButtonState.Normal,
    onClick:  () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val (bg, border, textColor) = when (state) {
        ToolButtonState.On      -> Triple(RhythmColors.accentBg,   RhythmColors.accentBorder, RhythmColors.accent)
        ToolButtonState.Editing -> Triple(RhythmColors.cautionBg,   RhythmColors.cautionBorder, RhythmColors.caution)
        ToolButtonState.Dim     -> Triple(surfaceBg2,       RhythmColors.border0,     RhythmColors.textDim)
        ToolButtonState.Normal  -> Triple(surfaceBg2,       RhythmColors.border1,     RhythmColors.textMuted)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val clickable = state != ToolButtonState.Dim

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(5.dp))
            .then(
                if (clickable) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication        = null,
                    onClick           = onClick,
                ) else Modifier
            )
            .padding(horizontal = 9.dp),
    ) {
        Text(label, style = RhythmType.toolBtn.copy(color = textColor))
    }
}

@Composable
fun NavButton(
    label:    String,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(32.dp)
            .widthIn(min = 38.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(surfaceBg3)
            .border(0.5.dp, RhythmColors.border1, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
    ) {
        Text(label, style = RhythmType.toolBtn.copy(fontSize = 15.sp,
            color = RhythmColors.textSecondary))
    }
}

@Composable
fun ToolDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(0.5.dp)
            .height(22.dp)
            .background(RhythmColors.border1),
    )
}

@Composable
fun RhythmToggle(
    checked:  Boolean,
    onToggle: () -> Unit,
    enabled:  Boolean  = true,
    modifier: Modifier = Modifier,
) {
    val bg     = if (checked) RhythmColors.toggleCheckedBg else surfaceBg3
    val border = if (checked) RhythmColors.toggleCheckedBorder else RhythmColors.border2
    val alpha  = if (enabled) 1f else 0.3f

    Box(
        modifier = modifier
            .width(36.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg.copy(alpha = alpha))
            .border(0.5.dp, border.copy(alpha = alpha), RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .size(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(RhythmColors.thumbColor.copy(alpha = alpha)),
        )
    }
}

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(RhythmColors.border0),
    )
}