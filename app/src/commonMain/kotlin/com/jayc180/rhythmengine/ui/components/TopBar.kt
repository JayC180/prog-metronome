package com.jayc180.rhythmengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jayc180.rhythmengine.builder.PlaybackState
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmType
import com.jayc180.rhythmengine.ui.theme.surfaceBg2
import com.jayc180.rhythmengine.ui.theme.surfaceBg3

@Composable
fun TopBar(
    bpm:             Double,  // displayed bpm
    isPlaying:       Boolean,
    playbackState:   PlaybackState,
    onSettingsClick: () -> Unit,
    onBpmClick:      () -> Unit,
    onPlayStopClick: () -> Unit,
    modifier:        Modifier = Modifier,
) {
    // bpm border changes during playback as it is live value
    val bpmBorder = if (isPlaying) RhythmColors.cautionBorder else RhythmColors.border1
    val bpmTextColor = if (isPlaying) RhythmColors.caution else RhythmColors.textPrimary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(surfaceBg2)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // settings
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(surfaceBg3)
                .border(0.5.dp, RhythmColors.border1, RoundedCornerShape(6.dp))
                .clickable(onClick = onSettingsClick)
        ) {
            Text("⚙", style = RhythmType.toolBtn.copy(fontSize = 18.sp, color = RhythmColors.textMuted))
        }

        // bpm
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(surfaceBg3)
                .border(0.5.dp,
                    if (isPlaying) RhythmColors.cautionBorder else RhythmColors.border1,
                    RoundedCornerShape(6.dp))
                .clickable(onClick = onBpmClick)
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("bpm", style = RhythmType.bpmLabel.copy(color = RhythmColors.textMuted))
                Text("${bpm.toInt()}",
                    style = RhythmType.bpmValue.copy(
                        color = if (isPlaying) RhythmColors.caution else RhythmColors.textPrimary))
            }
        }

        Spacer(Modifier.weight(1f))

        // play/stop
        val isPlaying = playbackState == PlaybackState.Playing
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isPlaying) RhythmColors.dangerBg else RhythmColors.accentBg)
                .border(
                    0.5.dp,
                    if (isPlaying) RhythmColors.dangerBorder else RhythmColors.accentBorder,
                    RoundedCornerShape(6.dp)
                )
                .clickable(onClick = onPlayStopClick)
        ) {
            if (isPlaying) {
                // stop
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(RhythmColors.danger)
                )
            } else {
                // play
                val playIconColor = RhythmColors.accentBright
                // triangle
                androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp)) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(2f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(2f, size.height)
                        close()
                    }
                    drawPath(path, color = playIconColor)
                }
            }
        }
    }
}