package com.jayc180.rhythmengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.jayc180.rhythmengine.ui.theme.RhythmColors

/**
 * Touch or drag anywhere on the track to set volume
 * Touch position maps directly to 0-1 value
 * No delta accumulation
 */
@Composable
fun VolumeSlider(
    value:         Float,
    onValueChange: (Float) -> Unit,
    enabled:       Boolean = true,
    modifier:      Modifier = Modifier,
) {
    val alpha   = if (enabled) 1f else 0.2f
    val clamped = value.coerceIn(0f, 1f)

    var trackWidthPx by remember { mutableStateOf(1f) }
    var trackLeftPx  by remember { mutableStateOf(0f) }

    fun xToValue(xPx: Float): Float =
        ((xPx - trackLeftPx) / trackWidthPx).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(24.dp)
            .onGloballyPositioned { coords ->
                trackWidthPx = coords.size.width.toFloat().coerceAtLeast(1f)
                trackLeftPx  = coords.positionInRoot().x
            }
            .then(
                if (enabled) Modifier.pointerInput(Unit) {
                    // tap or drag
                    detectDragGestures(
                        onDragStart = { offset ->
                            onValueChange(xToValue(offset.x + trackLeftPx))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onValueChange(xToValue(change.position.x + trackLeftPx))
                        },
                    )
                } else Modifier
            )
            .then(
                if (enabled) Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onValueChange(xToValue(offset.x + trackLeftPx))
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(2.dp))
                .background(RhythmColors.border2.copy(alpha = alpha)),
        )

        // filled portion
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RhythmColors.accent.copy(alpha = alpha)),
            )
        }

        // safe weight calculation
        Row(
            modifier          = Modifier.fillMaxWidth().align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val before = clamped.coerceAtLeast(0.001f)
            val after  = (1f - clamped).coerceAtLeast(0.001f)
            Spacer(Modifier.weight(before))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(RhythmColors.accent.copy(alpha = alpha))
            )
            Spacer(Modifier.weight(after))
        }
    }
}