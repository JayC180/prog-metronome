package com.jayc180.rhythmengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmType

const val SUPPORT_URL = "https://ko-fi.com/prog_metronome"

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.80f)
                .clip(RoundedCornerShape(14.dp))
                .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(14.dp)),
        ) {
            // header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RhythmColors.bg2)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Help", style = RhythmType.bpmValue.copy(
                    fontSize = 15.sp, color = RhythmColors.textPrimary))
                Text("✕", style = RhythmType.label.copy(
                    fontSize = 16.sp, color = RhythmColors.textMuted),
                    modifier = Modifier.clickable(onClick = onDismiss))
            }
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(RhythmColors.bg1)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HelpSection("Beat") {
                    HelpBody("The numpad enters beat values. The ÷N button shows the current " +
                            "subdivision for the next input. For example, ÷4 matches " +
                            "\"16th notes\" in classical western theory, then entering 1 on " +
                            "the numpad will give you an item with duration of 1 16th note in " +
                            "relation to the bpm. Entering 3 is equivalent to a dotted 8th note. " +
                            "For anything above 9, press the \"custom\" button at the bottom left " +
                            "and enter the desired value. A beat can be turned on/off, its volume " +
                            "and sound adjusted in the editor above the numpad.")
                }
                HelpSection("Subdivision") {
                    HelpBody("The default subdivision is ÷4. To change the subdivision, click " +
                            "on the ÷N button then press on the numpad or click on \"custom\" for " +
                            "any subdivision larger than 9. This allows easy access to all sorts of " +
                            "tuplets. For nested tuplets, you need to do the calculation. For " +
                            "example, the duration of a triplet out of two-notes from " +
                            "triplet-eighth-notes can be represented as 2/9 in relation to the " +
                            "pulse, so you can set subdivision to 9 then press 2 on the numpad.")
                }
                HelpSection("Brackets and Repeats") {
                    HelpBody("[ and ] wrap a section. Press ×N after the closing bracket to " +
                            "then click the numpad to repeat it that many times, or click on " +
                            "\"custom\" to repeat it more than 9 times. Infinite repeat is also an " +
                            "option. This is useful for simulating meters, hypermeters, or any " +
                            "repeating patterns. Brackets can be nested. Example: " +
                            "[ 3 3 2 ]×4 [ 5 ]×∞ plays a 3+3+2 group four times, then loops 5 " +
                            "indefinitely.")
                }
                HelpSection("Editing") {
                    HelpBody("The pencil button in the numpad is used for editing. When an " +
                            "appropriate item on the track is selected, clicking the edit button will " +
                            "go into edit mode, which can be toggled off by clicking edit again. " +
                            "While in edit mode, number inputs will change the value of the selected " +
                            "item instead of appending a new item. For some items, a popup will appear " +
                            "for the edit. ⌫ deletes the selected item, or the last item if nothing " +
                            "is selected. Paired bracket delete (in settings) removes both brackets " +
                            "together when you delete either one.")
                }
                HelpSection("Tempo Changes") {
                    HelpBody("mm inserts a metric modulation. After clicking on it, a popup " +
                            "will appear, prompting you to enter 2 numbers. For example, if the " +
                            "current bpm is 120, mm of ×3/2 means the new tempo is 3/2 of the " +
                            "current BPM, which will be 180. =bpm sets an absolute tempo at that " +
                            "point. Both take effect at that position during playback at that position " +
                            "in the track. It won't affect the global bpm. Tempo will also reset when " +
                            "the track reaches back to the beginning. You cannot put mm within a group " +
                            "that is infinitely repeated.")
                }
                HelpSection("Tracks") {
                    HelpBody("Each track is a row. Multiple tracks can be played simultaneously, " +
                            "which is useful for polyrhythm and polymeters. Tap on a track to select " +
                            "it and make edits. Each track will repeat from start when the end is " +
                            "reached (except when there's infinite repeat).")
                }
                HelpSection("Mute, Solo, Default Sound") {
                    HelpBody("Under track number there are buttons for mute, solo, and default " +
                            "sound for the track. If the default sound for a track is changed, " +
                            "\"D\" will change to \"C\" and be highlighted. Next inputs to this " +
                            "specific track will then use this sound instead of the global default " +
                            "sound.")
                }
                HelpSection("Projects") {
                    HelpBody("Project auto-saves. On relaunch you'll be offered to restore " +
                            "your last session. Use Save As in settings for named projects. Projects " +
                            "can be exported as .rhy files for sharing.")
                }
                HelpSection("About Prog Metronome") {
                    HelpBody("Prog Metronome is a free and open source project. Source code " +
                            "is available at \n\nhttps://github.com/JayC180/prog-metronome\n\n" +
                            "If you have the ability, consider contributing to the project or " +
                            "supporting the developer at " + SUPPORT_URL)
                }
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, body: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = RhythmType.bpmValue.copy(
            fontSize = 13.sp, color = RhythmColors.accent))
        body()
    }
}

@Composable
private fun HelpBody(text: String) {
    Text(text, style = RhythmType.label.copy(
        fontSize = 12.sp, color = RhythmColors.textSecondary,
        lineHeight = 18.sp))
}
