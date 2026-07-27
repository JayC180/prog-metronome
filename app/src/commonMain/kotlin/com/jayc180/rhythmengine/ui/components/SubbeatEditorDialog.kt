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
import com.jayc180.rhythmengine.builder.TrackItem
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmType
import com.jayc180.rhythmengine.ui.theme.surfaceBg2
import com.jayc180.rhythmengine.ui.theme.surfaceBg3

private const val SUBBEAT_CELLS_PER_ROW = 4 

@Composable
fun SubbeatEditorDialog(
    beat:            TrackItem.Beat,
    onToggleSubbeat: (Int) -> Unit,
    onSetAll:        (Boolean) -> Unit,
    onDismiss:       () -> Unit,
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
                Column {
                    Text("Subbeat editor",
                        style = RhythmType.bpmValue.copy(fontSize = 15.sp, color = RhythmColors.textPrimary))
                    Text("${beat.label}  •  $n sub-beats",
                        style = RhythmType.label.copy(fontSize = 11.sp, color = RhythmColors.textSecondary))
                }
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendCircle(RhythmColors.accentBright)
                Text("base beat (locked)", style = RhythmType.label.copy(
                    fontSize = 10.sp, color = RhythmColors.textSecondary))
                Spacer(Modifier.width(4.dp))
                LegendCircle(RhythmColors.caution)
                Text("sub-beat on", style = RhythmType.label.copy(
                    fontSize = 10.sp, color = RhythmColors.textSecondary))
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
                            val isBase   = idx == 0
                            val isActive = subbeats[idx]
                            SubbeatCell(
                                index    = idx,
                                isBase   = isBase,
                                isActive = isActive,
                                onClick  = { if (!isBase) onToggleSubbeat(idx) },
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
    index:    Int,
    isBase:   Boolean,
    isActive: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        isBase   -> RhythmColors.accentBright.copy(alpha = 0.15f)
        isActive -> RhythmColors.caution.copy(alpha = 0.15f)
        else     -> surfaceBg3
    }
    val borderColor = when {
        isBase   -> RhythmColors.accentBright.copy(alpha = 0.5f)
        isActive -> RhythmColors.caution.copy(alpha = 0.5f)
        else     -> RhythmColors.border1
    }
    val textColor = when {
        isBase   -> RhythmColors.accentBright
        isActive -> RhythmColors.caution
        else     -> RhythmColors.textDim
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(if (isBase || isActive) 1.dp else 0.5.dp, borderColor, RoundedCornerShape(6.dp))
            .then(if (!isBase) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${index + 1}",
                style = RhythmType.beatValue.copy(fontSize = 16.sp, color = textColor))
            if (isBase) {
                Text("base", style = RhythmType.label.copy(fontSize = 9.sp,
                    color = RhythmColors.accentBright.copy(alpha = 0.7f)))
            }
        }
    }
}

@Composable
private fun SubbeatBtn(
    label:    String,
    onClick:  () -> Unit,
    muted:    Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bg     = if (muted) surfaceBg3 else RhythmColors.accentBg
    val border = if (muted) RhythmColors.border1 else RhythmColors.accentBorder
    val color  = if (muted) RhythmColors.textMuted else RhythmColors.accent
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
