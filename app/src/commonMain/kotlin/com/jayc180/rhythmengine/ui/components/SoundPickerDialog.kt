package com.jayc180.rhythmengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jayc180.rhythmengine.audio.SoundInfo
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmType

@Composable
fun SoundPickerDialog(
    sounds:         List<SoundInfo>,
    currentSoundId: String?,
    onSelect:       (SoundInfo) -> Unit,
    onDismiss:      () -> Unit,
    onDeleteUser:   ((String) -> Unit)? = null,   // null = delete not available
    bg:        Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
    border:    Color = Color.Unspecified,
) {
    val userSounds    = remember(sounds) { sounds.filter { it.isUser } }
    val bundledSounds = remember(sounds) { sounds.filter { !it.isUser } }

    var searchText    by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<SoundInfo?>(null) }

    val filteredUser    = if (searchText.isEmpty()) userSounds
    else userSounds.filter { it.label.contains(searchText, ignoreCase = true) }
    val filteredBundled = if (searchText.isEmpty()) bundledSounds
    else bundledSounds.filter { it.label.contains(searchText, ignoreCase = true) }

    // confirm delete
    confirmDelete?.let { toDelete ->
        Dialog(onDismissRequest = { confirmDelete = null }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(RhythmColors.bg2)
                    .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Delete \"${toDelete.label}\"?",
                    style = RhythmType.bpmValue.copy(fontSize = 14.sp,
                        color = RhythmColors.textPrimary))
                Text("This cannot be undone.",
                    style = RhythmType.label.copy(fontSize = 11.sp,
                        color = RhythmColors.textSecondary))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoundDialogBtn("Cancel", onClick = { confirmDelete = null },
                        bg = RhythmColors.bg3, textColor = RhythmColors.textMuted,
                        border = RhythmColors.border1)
                    SoundDialogBtn("Delete", onClick = {
                        onDeleteUser?.invoke(toDelete.id)
                        confirmDelete = null
                    }, bg = RhythmColors.dangerBg, textColor = RhythmColors.danger,
                        border = RhythmColors.dangerBorder)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.78f)
                .clip(RoundedCornerShape(14.dp))
                .background(RhythmColors.bg2)
                .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(14.dp)),
        ) {
            // header
            Row(modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically) {
                Text("Select Sound", style = RhythmType.bpmValue.copy(
                    fontSize = 15.sp, color = RhythmColors.textPrimary))
                Text("✕", style = RhythmType.label.copy(
                    fontSize = 16.sp, color = RhythmColors.textMuted),
                    modifier = Modifier.clickable(onClick = onDismiss))
            }

            HorizontalDivider()

            // search
            OutlinedTextField(
                value         = searchText,
                onValueChange = { searchText = it },
                placeholder   = { Text("search…", style = RhythmType.label.copy(
                    color = RhythmColors.textDim)) },
                singleLine    = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = RhythmColors.accent,
                    unfocusedBorderColor = RhythmColors.border1,
                    focusedTextColor     = RhythmColors.textPrimary,
                    unfocusedTextColor   = RhythmColors.textPrimary,
                    cursorColor          = RhythmColors.accent),
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            HorizontalDivider()

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (filteredUser.isNotEmpty()) {
                    item { SectionHeader("Your sounds (${filteredUser.size})") }
                    items(filteredUser, key = { it.id }) { info ->
                        SoundRow(
                            info      = info,
                            selected  = info.id == currentSoundId,
                            showDelete = onDeleteUser != null,
                            onClick   = { onSelect(info); onDismiss() },
                            onDelete  = { confirmDelete = info },
                        )
                    }
                }
                if (filteredBundled.isNotEmpty()) {
                    item { SectionHeader("Built-in sounds (${filteredBundled.size})") }
                    items(filteredBundled, key = { it.id }) { info ->
                        SoundRow(
                            info       = info,
                            selected   = info.id == currentSoundId,
                            showDelete = false,
                            onClick    = { onSelect(info); onDismiss() },
                            onDelete   = {},
                        )
                    }
                }
                if (filteredUser.isEmpty() && filteredBundled.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center) {
                            Text("No sounds match \"$searchText\"",
                                style = RhythmType.label.copy(color = RhythmColors.textDim))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Box(modifier = Modifier.fillMaxWidth().background(RhythmColors.bg0)
        .padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(title, style = RhythmType.label.copy(
            fontSize = 10.sp, color = RhythmColors.textMuted))
    }
}

@Composable
private fun SoundRow(
    info:       SoundInfo,
    selected:   Boolean,
    showDelete: Boolean,
    onClick:    () -> Unit,
    onDelete:   () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) RhythmColors.trackActiveBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = if (showDelete) 6.dp else 14.dp,
                top = 11.dp, bottom = 11.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // selection dot
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
            .background(if (selected) RhythmColors.accent else Color.Transparent))

        // label
        Text(info.label,
            style    = RhythmType.label.copy(
                fontSize = 13.sp,
                color    = if (selected) RhythmColors.accent else RhythmColors.textPrimary),
            modifier = Modifier.weight(1f))

        // custom badge
        if (info.isUser) {
            Box(modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(RhythmColors.cautionBg)
                .border(0.5.dp, RhythmColors.cautionBorder, RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)) {
                Text("custom", style = RhythmType.label.copy(
                    fontSize = 9.sp, color = RhythmColors.caution))
            }
        }

        // delete for user imported sound
        if (showDelete) {
            Box(modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(RhythmColors.dangerBg)
                .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center) {
                Text("🗑", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SoundDialogBtn(
    label: String, onClick: () -> Unit,
    bg: Color, textColor: Color,
    border: Color,
) {
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg).border(0.5.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp)) {
        Text(label, style = RhythmType.label.copy(fontSize = 12.sp, color = textColor))
    }
}