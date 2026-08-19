package com.jayc180.rhythmengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jayc180.rhythmengine.builder.SubbeatState
import com.jayc180.rhythmengine.builder.TrackItem
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmType
import com.jayc180.rhythmengine.ui.theme.surfaceBg3

private const val SUBBEAT_CELLS_PER_ROW = 4

@Composable
fun SubbeatEditorDialog(
    beat:              TrackItem.Beat,
    onCycleSubbeat:    (Int) -> Unit,
    onSetAll:          (Boolean) -> Unit,
    onOpenSubdivSound: (() -> Unit)? = null,
    onDismiss:         () -> Unit,
) {
    val subbeats = beat.subbeats ?: return
    val n        = subbeats.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(14.dp))
                .background(RhythmColors.bg2)
                .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(14.dp)),
        ) {
            // header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Subbeat Editor",
                    style = RhythmType.bpmValue.copy(fontSize = 15.sp, color = RhythmColors.textPrimary))
                Text("✕", style = RhythmType.label.copy(fontSize = 16.sp, color = RhythmColors.textMuted),
                    modifier = Modifier.clickable(onClick = onDismiss))
            }

            HorizontalDivider()

            // all on/off
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubbeatBtn("All on",  onClick = { onSetAll(true) },  modifier = Modifier.weight(1f))
                SubbeatBtn("All off", onClick = { onSetAll(false) }, muted = true, modifier = Modifier.weight(1f))
            }

            HorizontalDivider()

            // legend
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendCircle(RhythmColors.accentBright)
                Text("base beat", style = RhythmType.label.copy(fontSize = 10.sp, color = RhythmColors.textSecondary))
                Spacer(Modifier.width(2.dp))
                LegendCircle(RhythmColors.caution)
                Text("sub-beat", style = RhythmType.label.copy(fontSize = 10.sp, color = RhythmColors.textSecondary))
                Spacer(Modifier.width(2.dp))
                LegendCircle(RhythmColors.border1)
                Text("off", style = RhythmType.label.copy(fontSize = 10.sp, color = RhythmColors.textSecondary))
                Spacer(Modifier.weight(1f))
                if (onOpenSubdivSound != null) {
                    val hasOverride = beat.subdivisionSoundId != null
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (hasOverride) RhythmColors.cautionBg else RhythmColors.bg3)
                            .border(
                                0.5.dp,
                                if (hasOverride) RhythmColors.cautionBorder else RhythmColors.border1,
                                RoundedCornerShape(4.dp),
                            )
                            .clickable(onClick = onOpenSubdivSound)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("sound", style = RhythmType.label.copy(
                            fontSize = 9.sp,
                            color = if (hasOverride) RhythmColors.caution else RhythmColors.textMuted,
                        ))
                    }
                }
            }

            HorizontalDivider()

            // subbeat cell grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val rows = (n + SUBBEAT_CELLS_PER_ROW - 1) / SUBBEAT_CELLS_PER_ROW
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val from = row * SUBBEAT_CELLS_PER_ROW
                        val to   = minOf(from + SUBBEAT_CELLS_PER_ROW, n)
                        for (idx in from until to) {
                            SubbeatCell(
                                index   = idx,
                                state   = subbeats[idx],
                                onClick = { onCycleSubbeat(idx) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(SUBBEAT_CELLS_PER_ROW - (to - from)) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubbeatCell(
    index:   Int,
    state:   SubbeatState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (bg, borderColor, textColor) = when (state) {
        SubbeatState.BEAT -> Triple(
            RhythmColors.accentBright.copy(alpha = 0.15f),
            RhythmColors.accentBright.copy(alpha = 0.5f),
            RhythmColors.accentBright,
        )
        SubbeatState.SUBBEAT -> Triple(
            RhythmColors.caution.copy(alpha = 0.15f),
            RhythmColors.caution.copy(alpha = 0.5f),
            RhythmColors.caution,
        )
        SubbeatState.OFF -> Triple(
            surfaceBg3,
            RhythmColors.border1,
            RhythmColors.textDim,
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(if (state != SubbeatState.OFF) 1.dp else 0.5.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Text("${index + 1}",
            style = RhythmType.beatValue.copy(fontSize = 20.sp, color = textColor))
    }
}


@Composable
private fun SubbeatBtn(
    label:    String,
    onClick:  () -> Unit,
    muted:    Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bg = if (muted) surfaceBg3 else RhythmColors.accentBg
    val border = if (muted) RhythmColors.border1 else RhythmColors.accentBorder
    val color = if (muted) RhythmColors.textMuted else RhythmColors.accent
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Text(label, style = RhythmType.label.copy(fontSize = 12.sp, color = color))
    }
}

@Composable
private fun LegendCircle(color: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}
