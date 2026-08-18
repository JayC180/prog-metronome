package com.jayc180.rhythmengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jayc180.rhythmengine.builder.TapTempoCalculator
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmType

/**
 * two ways to set BPM:
 * 1. input num directly into the text field
 * 2. tap "Tap" button repeatedly to detect tempo by mean interval
 *
 * The live preview updates as the user taps
 */
@Composable
fun BpmInputDialog(
    currentBpm: Double,
    onConfirm:  (Double) -> Unit,
    onDismiss:  () -> Unit,
) {
    val calculator = remember { TapTempoCalculator() }

    var textBpm   by remember { mutableStateOf(formatBpm(currentBpm)) }
    var tapBpm    by remember { mutableStateOf<Double?>(null) }
    var tapCount  by remember { mutableStateOf(0) }

    val textParsed: Double? = textBpm.toDoubleOrNull()?.takeIf { it in 1.0..999.99 }
    // take textbox val
    val effectiveBpm: Double? = tapBpm ?: textParsed
    val isValid = textParsed != null

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(RhythmColors.bg2)
                .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(14.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Set BPM", style = RhythmType.bpmValue.copy(
                fontSize = 16.sp, color = RhythmColors.textPrimary))

            // direct number input
            OutlinedTextField(
                value         = textBpm,
                onValueChange = { new ->
                    if (new != textBpm) { tapBpm = null; calculator.reset(); tapCount = 0 }
                    if (isValidBpmInput(new)) textBpm = new
                },
                label         = { Text("BPM", style = RhythmType.label.copy(
                    color = RhythmColors.textMuted)) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = RhythmColors.accent,
                    unfocusedBorderColor = RhythmColors.border2,
                    focusedTextColor     = RhythmColors.textPrimary,
                    unfocusedTextColor   = RhythmColors.textPrimary,
                    cursorColor          = RhythmColors.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            // nudge buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                fun nudge(delta: Double) {
                    val base = tapBpm ?: textBpm.toDoubleOrNull() ?: return
                    tapBpm = null; calculator.reset(); tapCount = 0
                    val next = delta.let {
                        if (it == 2.0 || it == 0.5) (base * it) else (base + it)
                    }.coerceIn(1.0, 999.99)
                    textBpm = formatBpm(next)
                }
                for ((label, delta) in listOf("÷2" to 0.5, "-5" to -5.0, "-1" to -1.0,
                                               "+1" to 1.0, "+5" to 5.0, "×2" to 2.0)) {
                    BpmBtn(label, onClick = { nudge(delta) },
                        bg        = RhythmColors.bg3,
                        textColor = RhythmColors.textSecondary,
                        border    = RhythmColors.border1,
                        modifier  = Modifier.weight(1f))
                }
            }

            // tap tempo
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("or tap the beat:", style = RhythmType.label.copy(
                    fontSize = 11.sp, color = RhythmColors.textSecondary))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // tap button
                    val tapInteractionSource = remember { MutableInteractionSource() }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(56.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(RhythmColors.accentBg)
                            .border(1.dp, RhythmColors.accentBorder, RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = tapInteractionSource,
                                indication        = null,
                            ) {
                                val result = calculator.tap()
                                tapCount = calculator.tapCount
                                if (result != null) {
                                    tapBpm = result.coerceIn(1.0, 999.99)
                                }
                            },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TAP", style = RhythmType.bpmValue.copy(
                                fontSize = 18.sp, color = RhythmColors.accent))
                            if (tapCount >= 2) {
                                Text("${tapCount} taps", style = RhythmType.label.copy(
                                    fontSize = 9.sp, color = RhythmColors.textMuted))
                            }
                        }
                    }

                    // bpm preview while tap
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier.width(84.dp),
                    ) {
                        Text(
                            effectiveBpm?.let { formatBpm(it) } ?: "—",
                            style = RhythmType.bpmValue.copy(
                                fontSize = 28.sp,
                                color    = if (tapBpm != null) RhythmColors.accent
                                else RhythmColors.textPrimary,
                            ),
                        )
                        Text("bpm", style = RhythmType.bpmLabel.copy(color = RhythmColors.textMuted))
                    }
                }

                // hint
                Text(
                    when {
                        tapCount == 0 -> "tap to detect tempo from timing"
                        tapCount == 1 -> "keep tapping…"
                        else          -> "mean of $tapCount taps — keep tapping to refine"
                    },
                    style = RhythmType.label.copy(
                        fontSize = 10.sp, color = RhythmColors.textDim),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BpmBtn("Cancel", onClick = onDismiss,
                    bg = RhythmColors.bg3, textColor = RhythmColors.textMuted,
                    border = RhythmColors.border1)
                if (tapCount >= 2) {
                    BpmBtn("Reset taps",
                        onClick = { tapBpm = null; calculator.reset(); tapCount = 0 },
                        bg      = RhythmColors.bg3,
                        textColor = RhythmColors.textMuted,
                        border  = RhythmColors.border1)
                }
                BpmBtn(
                    label     = "Set",
                    onClick   = { textParsed?.let { onConfirm(it) } },
                    enabled   = isValid,
                    bg        = if (isValid) RhythmColors.accentBg else RhythmColors.bg3,
                    textColor = if (isValid) RhythmColors.border1 else RhythmColors.textDim,
                    border    = if (isValid) RhythmColors.accentBorder else RhythmColors.border0,
                )
            }
        }
    }
}

@Composable
private fun BpmBtn(
    label:     String,
    onClick:   () -> Unit,
    enabled:   Boolean = true,
    bg:        Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
    border:    Color = Color.Unspecified,
    modifier:  Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg.copy(alpha = alpha))
            .border(0.5.dp, border.copy(alpha = alpha), RoundedCornerShape(6.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp),
    ) {
        Text(label, maxLines = 1, style = RhythmType.label.copy(
            fontSize = 12.sp, color = textColor.copy(alpha = alpha)))
    }
}