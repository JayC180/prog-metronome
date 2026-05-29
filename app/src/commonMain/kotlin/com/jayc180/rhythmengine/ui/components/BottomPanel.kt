package com.jayc180.rhythmengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jayc180.rhythmengine.builder.*
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmType
import com.jayc180.rhythmengine.ui.theme.surfaceBg2
import com.jayc180.rhythmengine.ui.theme.surfaceBg3

/**
 * Bottom section:
 *   Row 1:  ←  |  ÷N  [  ]  ×N  mm  |  →
 *   Row 2:  Edit panel (always visible, grayed when no item selected)
 *   Row 3:  Numpad 1-9 | custom | ✎ | ⌫
 */
@Composable
fun BottomPanel(
    state:              TrackBuilderState,
    cursorItem:         TrackItem?,
    onNavPrev:          () -> Unit,
    onNavNext:          () -> Unit,
    onDenomToggle:      () -> Unit,
    onOpenBracket:      () -> Unit,
    onCloseBracket:     () -> Unit,
    onRepeatToggle:     () -> Unit,
    onMmClick:          () -> Unit,      // opens mm popup
    onSetBpmClick:      () -> Unit,      // opens =bpm popup
    onNumpad:           (Int) -> Unit,
    onEditToggle:             () -> Unit,
    onCustom:           () -> Unit,
    onBackspace:        () -> Unit,
    onBeatActiveToggle: () -> Unit,
    onSoundChange:      () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier:           Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(RhythmColors.bg0)) {
        ToolbarRow(state, onNavPrev, onNavNext, onDenomToggle,
            onOpenBracket, onCloseBracket, onRepeatToggle, onMmClick, onSetBpmClick)
        HorizontalDivider()
        EditPanel(cursorItem, enabled = cursorItem != null && !state.isPlaying,
            onBeatActiveToggle = onBeatActiveToggle, onSoundChange = onSoundChange,
            onVolumeChange = onVolumeChange)
        HorizontalDivider()
        NumpadSection(state, cursorItem, state.isPlaying, onNumpad, onCustom, onBackspace, onEditToggle, )
    }
}

@Composable
private fun ToolbarRow(
    state:          TrackBuilderState,
    onNavPrev:      () -> Unit,
    onNavNext:      () -> Unit,
    onDenomToggle:  () -> Unit,
    onOpenBracket:  () -> Unit,
    onCloseBracket: () -> Unit,
    onRepeatToggle: () -> Unit,
    onMmClick:      () -> Unit,
    onSetBpmClick:  () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(surfaceBg2)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth().height(50.dp)
                .background(surfaceBg2)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NavButton("←", onClick = onNavPrev)
            ToolDivider()

            ToolButton(
                "÷${state.activeDenom}",
                state = if (state.isDenomMode) ToolButtonState.Editing else ToolButtonState.On,
                onClick = onDenomToggle
            )

            ToolButton(
                "[",
                state = if (state.canOpenBracket) ToolButtonState.Normal else ToolButtonState.Dim,
                onClick = onOpenBracket
            )

            ToolButton(
                "]",
                state = if (state.canCloseBracket) ToolButtonState.Normal else ToolButtonState.Dim,
                onClick = onCloseBracket
            )

            ToolButton(
                "×N",
                state = when {
                    state.isRepeatMode -> ToolButtonState.Editing
                    state.canSetRepeat -> ToolButtonState.Normal
                    else -> ToolButtonState.Dim
                },
                onClick = onRepeatToggle
            )

            // mm — available wherever canInsertTempo
            ToolButton(
                "mm",
                state = if (state.canInsertTempo) ToolButtonState.Normal else ToolButtonState.Dim,
                onClick = onMmClick
            )

            // =bpm — same availability as mm
            ToolButton(
                "=bpm",
                state = if (state.canInsertTempo) ToolButtonState.Normal else ToolButtonState.Dim,
                onClick = onSetBpmClick
            )

            Spacer(Modifier.weight(1f))
            ToolDivider()
            NavButton("→", onClick = onNavNext)
        }
    }
}

@Composable
private fun EditPanel(
    cursorItem:         TrackItem?,
    enabled:            Boolean,
    onBeatActiveToggle: () -> Unit,
    onSoundChange:      () -> Unit,
    onVolumeChange:     (Float) -> Unit
) {
    val alpha = if (enabled) 1f else 0.25f
    val beat  = cursorItem as? TrackItem.Beat

    Column(
        modifier = Modifier.fillMaxWidth().background(RhythmColors.bg0)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // on/off
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("on/off", style = RhythmType.label.copy(
                color = RhythmColors.textMuted.copy(alpha = alpha), fontSize = 13.sp),
                modifier = Modifier.width(64.dp))
            RhythmToggle(checked = beat?.active ?: false, onToggle = onBeatActiveToggle,
                enabled = enabled && beat != null)
            Text(
                text = when {
                    !enabled     -> "select a beat"
                    beat == null -> cursorItem?.let { itemTypeLabel(it) } ?: "—"
                    beat.active  -> "active"
                    else         -> "rest"
                },
                style = RhythmType.label.copy(fontSize = 13.sp, color = when {
                    !enabled || beat == null -> RhythmColors.textDim
                    beat.active             -> RhythmColors.accent
                    else                    -> RhythmColors.textMuted
                })
            )
        }

        // volume
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("volume", style = RhythmType.label.copy(
                color = RhythmColors.textMuted.copy(alpha = alpha), fontSize = 13.sp),
                modifier = Modifier.width(64.dp))
            VolumeSlider(
                value         = beat?.volume ?: 0f,
                onValueChange = { newVol -> onVolumeChange(newVol) },
                enabled       = enabled && beat != null,
                modifier      = Modifier.weight(1f),
            )
            Text("${((beat?.volume ?: 0f) * 100).toInt()}%",
                style = RhythmType.label.copy(fontSize = 13.sp,
                    color = RhythmColors.textMuted.copy(alpha = alpha)))
        }

        // sound
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("sound", style = RhythmType.label.copy(
                color = RhythmColors.textMuted.copy(alpha = alpha), fontSize = 13.sp),
                modifier = Modifier.width(64.dp))
            Box(modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(surfaceBg3.copy(alpha = alpha))
                .border(0.5.dp, RhythmColors.border1.copy(alpha = alpha), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(beat?.soundId ?: "—", style = RhythmType.label.copy(
                    fontSize = 13.sp, color = RhythmColors.textSecondary.copy(alpha = alpha)))
            }
            if (enabled && beat != null) {
                Box(modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(RhythmColors.accentBg)
                    .border(0.5.dp, RhythmColors.accentBorder, RoundedCornerShape(4.dp))
                    .clickable(onClick = onSoundChange)
                    .padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("change ›", style = RhythmType.label.copy(fontSize = 13.sp, color = RhythmColors.accent))
                }
            }
        }
    }
}

private fun itemTypeLabel(item: TrackItem): String = when (item) {
    is TrackItem.BracketOpen  -> "bracket ["
    is TrackItem.BracketClose -> "bracket ]"
    is TrackItem.Repeat       -> if (item.isInfinite) "repeat ×∞" else "repeat ×${item.count}"
    is TrackItem.Modulation   -> "modulation ×${item.p}/${item.q}"
    is TrackItem.SetBpm       -> "set bpm =${item.bpm.toInt()}"
    else -> "—"
}

@Composable
private fun NumpadSection(
    state:      TrackBuilderState,
    cursorItem: TrackItem?,
    isPlaying:   Boolean,
    onNumpad:   (Int) -> Unit,
    onCustom:   () -> Unit,
    onBackspace: () -> Unit,
    onEditToggle: () -> Unit,
) {
    val beatSelected = cursorItem is TrackItem.Beat
    val numpadEnabled = !isPlaying
    val hint: String? = when {
        state.isDenomMode  -> "set subdivision — tap or use custom"
        state.isRepeatMode -> "set repeat (1-9) or custom / ∞"
        state.isEditMode   -> "edit value — tap a number or use custom"
        else               -> null
    }

    val editBtnAvailable = state.canEdit_item || state.cursorItem is TrackItem.Modulation
    val customEnabled = state.isRepeatMode || state.isEditMode

    Column(modifier = Modifier.fillMaxWidth().background(RhythmColors.bg0)
        .padding(horizontal = 8.dp)) {
        hint?.let {
            Text(it, style = RhythmType.label.copy(color = RhythmColors.caution, fontSize = 11.sp),
                modifier = Modifier.padding(top = 5.dp, bottom = 2.dp))
        }
        Spacer(Modifier.height(5.dp))
        val gap = 4.dp
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier.fillMaxWidth()) {
                    for (col in 1..3) {
                        val n = row * 3 + col
                        NumKey("$n", onClick = { if (numpadEnabled) onNumpad(n) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                NumKey(
                    label  = "custom",
                    onClick = { if (numpadEnabled) onCustom()},
                    textSize = 10.sp,
                    color  = if (customEnabled) RhythmColors.textPrimary else RhythmColors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                NumKey(
                    label  = "✎",
                    onClick = { if (numpadEnabled) onEditToggle()},
                    color  = when {
                        state.isEditMode  -> RhythmColors.caution          // active edit
                        state.canEdit_item -> RhythmColors.accent         // can edit
                        else              -> RhythmColors.textDim        // unavailable
                    },
                    bg = when {
                        state.isEditMode  -> RhythmColors.cautionBg
                        state.canEdit_item -> RhythmColors.accentBg
                        else              -> surfaceBg3
                    },
                    border = when {
                        state.isEditMode  -> RhythmColors.cautionBorder
                        state.canEdit_item -> RhythmColors.accentBorder
                        else              -> RhythmColors.border0
                    },
                    modifier = Modifier.weight(1f),
                )
                NumKey("⌫", onClick = { if (numpadEnabled) onBackspace()}, textSize = 14.sp,
                    color = RhythmColors.textMuted, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NumKey(
    label:    String,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
    textSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    color:    Color = Color.Unspecified,
    bg:       Color = Color.Unspecified,
    border:   Color = Color.Unspecified,
) {
    val resolvedColor  = if (color  == Color.Unspecified) RhythmColors.thumbColor else color
    val resolvedBg     = if (bg     == Color.Unspecified) surfaceBg3              else bg
    val resolvedBorder = if (border == Color.Unspecified) RhythmColors.border1    else border
    Box(contentAlignment = Alignment.Center, modifier = modifier
        .height(48.dp).clip(RoundedCornerShape(8.dp))
        .background(resolvedBg)
        .border(0.5.dp, resolvedBorder, RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)) {
        Text(label, style = RhythmType.numpad.copy(fontSize = textSize, color = resolvedColor))
    }
}

// metric modulation popup
@Composable
fun MmDialog(
    initialP: Int? = null,
    initialQ: Int? = null,
    onConfirm:  (p: Int, q: Int) -> Unit,
    onDismiss:  () -> Unit,
) {
    var pText by remember { mutableStateOf(initialP?.toString() ?: "") }
    var qText by remember { mutableStateOf(initialQ?.toString() ?: "") }
    val p = pText.toIntOrNull()
    val q = qText.toIntOrNull()
    val isValid = p != null && q != null && p > 0 && q > 0

    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceBg2)
            .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
            .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Text("Metric modulation", style = RhythmType.bpmValue.copy(
                fontSize = 15.sp, color = RhythmColors.textPrimary))
            Text("new BPM = current BPM × p/q", style = RhythmType.label.copy(
                fontSize = 11.sp, color = RhythmColors.textSecondary))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TempoTextField(value = pText, onValueChange = { pText = it },
                    label = "p", modifier = Modifier.weight(1f))
                Text("/", style = RhythmType.bpmValue.copy(color = RhythmColors.textSecondary))
                TempoTextField(value = qText, onValueChange = { qText = it },
                    label = "q", modifier = Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogBtn("Cancel", onClick = onDismiss,
                    bg = surfaceBg3, textColor = RhythmColors.textMuted, border = RhythmColors.border1)
                DialogBtn("Insert", onClick = { if (isValid) onConfirm(p!!, q!!) },
                    bg = if (isValid) RhythmColors.accentBg else surfaceBg3,
                    textColor = if (isValid) RhythmColors.accent else RhythmColors.textDim,
                    border = if (isValid) RhythmColors.accentBorder else RhythmColors.border0)
            }
        }
    }
}

// =bpm popup
@Composable
fun SetBpmDialog(
    currentBpm: Double,
    initialBpm: Double? = null,
    onConfirm:  (bpm: Double) -> Unit,
    onDismiss:  () -> Unit,
) {
    var bpmText by remember { mutableStateOf(initialBpm?.toInt()?.toString() ?: "${currentBpm.toInt()}") }
    val bpmVal  = bpmText.toDoubleOrNull()
    val isValid = bpmVal != null && bpmVal in 1.0..999.0

    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceBg2)
            .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
            .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Text("Set BPM", style = RhythmType.bpmValue.copy(
                fontSize = 15.sp, color = RhythmColors.textPrimary))
            Text("Jump to this BPM when reached during playback",
                style = RhythmType.label.copy(fontSize = 11.sp, color = RhythmColors.textSecondary))

            TempoTextField(value = bpmText, onValueChange = { bpmText = it },
                label = "bpm", modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogBtn("Cancel", onClick = onDismiss,
                    bg = surfaceBg3, textColor = RhythmColors.textMuted, border = RhythmColors.border1)
                DialogBtn("Insert", onClick = { if (isValid) onConfirm(bpmVal!!) },
                    bg = if (isValid) RhythmColors.accentBg else surfaceBg3,
                    textColor = if (isValid) RhythmColors.accent else RhythmColors.textDim,
                    border = if (isValid) RhythmColors.accentBorder else RhythmColors.border0)
            }
        }
    }
}

// repeat popup
@Composable
fun RepeatDialog(
    onConfirm:  (count: Int) -> Unit,  // -1 = infinite
    onDismiss:  () -> Unit,
) {
    var countText by remember { mutableStateOf("") }
    val countVal  = countText.toIntOrNull()
    val isValid   = countVal != null && countVal >= 1

    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceBg2)
            .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
            .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Text("Repeat count", style = RhythmType.bpmValue.copy(
                fontSize = 15.sp, color = RhythmColors.textPrimary))

            TempoTextField(value = countText, onValueChange = { countText = it },
                label = "number of repeats", modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogBtn("Cancel", onClick = onDismiss,
                    bg = surfaceBg3, textColor = RhythmColors.textMuted, border = RhythmColors.border1)
                // Infinite button
                DialogBtn("∞ forever", onClick = { onConfirm(-1) },
                    bg = RhythmColors.infiniteBg, textColor = RhythmColors.infiniteText,
                    border = RhythmColors.infiniteBorder)
                DialogBtn("OK", onClick = { if (isValid) onConfirm(countVal!!) },
                    bg = if (isValid) RhythmColors.accentBg else surfaceBg3,
                    textColor = if (isValid) RhythmColors.accent else RhythmColors.textDim,
                    border = if (isValid) RhythmColors.accentBorder else RhythmColors.border0)
            }
        }
    }
}

// custom popup
@Composable
fun CustomNumberDialog(
    title:     String,
    hint:      String = "",
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val value = text.toIntOrNull()
    val valid = value != null && value > 0

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceBg2)
                .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = RhythmType.bpmValue.copy(
                fontSize = 15.sp, color = RhythmColors.textPrimary))

            if (hint.isNotEmpty()) {
                Text(hint, style = RhythmType.label.copy(
                    fontSize = 11.sp, color = RhythmColors.textSecondary))
            }

            OutlinedTextField(
                value         = text,
                onValueChange = { if (it.length <= 6) text = it },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = RhythmColors.accent,
                    unfocusedBorderColor = RhythmColors.border2,
                    focusedTextColor     = RhythmColors.textPrimary,
                    unfocusedTextColor   = RhythmColors.textPrimary,
                    cursorColor          = RhythmColors.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogBtn("Cancel", onClick = onDismiss,
                    bg = surfaceBg3, textColor = RhythmColors.textMuted,
                    border = RhythmColors.border1)
                DialogBtn("OK",
                    onClick  = { if (valid) onConfirm(value!!) },
                    enabled  = valid,
                    bg       = RhythmColors.accentBg,
                    textColor = RhythmColors.accent,
                    border   = RhythmColors.accentBorder)
            }
        }
    }
}

// dialog helpers
@Composable
private fun TempoTextField(
    value: String, onValueChange: (String) -> Unit,
    label: String, modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = { if (it.length <= 6) onValueChange(it) },
        label         = { Text(label, style = RhythmType.label.copy(color = RhythmColors.textMuted)) },
        singleLine    = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = RhythmColors.accent,
            unfocusedBorderColor = RhythmColors.border2,
            focusedTextColor     = RhythmColors.textPrimary,
            unfocusedTextColor   = RhythmColors.textPrimary,
            cursorColor          = RhythmColors.accent,
        ),
        modifier = modifier,
    )
}

@Composable
private fun DialogBtn(
    label:   String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    bg:      Color,
    textColor: Color,
    border:  Color,
) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg.copy(alpha = alpha))
            .border(0.5.dp, border.copy(alpha = alpha), RoundedCornerShape(6.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
    ) {
        Text(label, style = RhythmType.label.copy(fontSize = 12.sp, color = textColor.copy(alpha = alpha)))
    }
}