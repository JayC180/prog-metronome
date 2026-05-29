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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jayc180.rhythmengine.ui.theme.*

@Composable
fun ThemeSettingsDialog(
    availableThemes:  List<ThemeConfig>,
    activeTheme:      ThemeConfig,
    bgConfig:         BackgroundConfig,
    onSelectTheme:    (ThemeConfig) -> Unit,
    onImportTheme:    (() -> Unit)? = null,
    onPickBackground: (() -> Unit)? = null,
    onSetFitMode:     (BgFitMode) -> Unit,
    onSetDim:         (Float) -> Unit,
    onSetPanX:        (Float) -> Unit,
    onSetPanY:        (Float) -> Unit,
    onSetPanScale:    (Float) -> Unit,
    onRemoveBg:       () -> Unit,
    onDismiss:        () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(14.dp))
                .background(RhythmColors.bg2)
                .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(14.dp)),
        ) {
            // header
            Row(modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically) {
                Text("Appearance", style = RhythmType.bpmValue.copy(
                    fontSize = 15.sp, color = RhythmColors.textPrimary))
                Text("✕", style = RhythmType.label.copy(
                    fontSize = 16.sp, color = RhythmColors.textMuted),
                    modifier = Modifier.clickable(onClick = onDismiss))
            }

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Spacer(Modifier.height(2.dp))

                // theme picker
                TSection("COLOR THEME")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement   = Arrangement.spacedBy(9.dp),
                ) {
                    availableThemes.forEach { theme ->
                        ThemeSwatch(
                            theme    = theme,
                            selected = theme.name == activeTheme.name,
                            onClick  = { onSelectTheme(theme) },
                        )
                    }
                    if (onImportTheme != null) ImportSwatch(onClick = onImportTheme)
                }

                // background img for you to put anime wifu
                TSection("BACKGROUND IMAGE")

                if (!bgConfig.hasImage) {
                    if (onPickBackground != null) TBtn("Pick image…", onClick = onPickBackground)
                } else {
                    // fit mode buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BgFitMode.entries.forEach { mode ->
                            val sel = bgConfig.fitMode == mode
                            Box(contentAlignment = Alignment.Center,
                                modifier = Modifier.height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) RhythmColors.accentBg else RhythmColors.bg3)
                                    .border(0.5.dp,
                                        if (sel) RhythmColors.accentBorder else RhythmColors.border1,
                                        RoundedCornerShape(6.dp))
                                    .clickable { onSetFitMode(mode) }
                                    .padding(horizontal = 12.dp)) {
                                Text(mode.name, style = RhythmType.label.copy(
                                    fontSize = 11.sp,
                                    color = if (sel) RhythmColors.accent else RhythmColors.textMuted))
                            }
                        }
                    }

                    // move mode stuff
                    if (bgConfig.fitMode == BgFitMode.Move) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TLabel("left")
                                VolumeSlider(
                                    value         = bgConfig.panX,
                                    onValueChange = onSetPanX,
                                    modifier      = Modifier.weight(1f),
                                )
                                TLabel("right")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TLabel("top")
                                VolumeSlider(
                                    value         = bgConfig.panY,
                                    onValueChange = onSetPanY,
                                    modifier      = Modifier.weight(1f),
                                )
                                TLabel("bottom")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TLabel("20%")
                                VolumeSlider(
                                    value         = (bgConfig.panScale - 0.2f) / 2.8f,
                                    onValueChange = { onSetPanScale(it * 2.8f + 0.2f) },
                                    modifier      = Modifier.weight(1f),
                                )
                                TLabel("300%")
                            }
                            TLabel("${(bgConfig.panScale * 100 + 0.5f).toInt()}% zoom")
                        }
                    }

                    // dim slider
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TLabel("no dim")
                        VolumeSlider(
                            value         = bgConfig.dim,
                            onValueChange = onSetDim,
                            modifier      = Modifier.weight(1f),
                        )
                        TLabel("dark")
                    }
                    TLabel("dim ${(bgConfig.dim * 100).toInt()}%")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onPickBackground != null) TBtn("Change image", onClick = onPickBackground)
                        TBtn("Remove",
                            onClick   = onRemoveBg,
                            bg        = RhythmColors.dangerBg,
                            textColor = RhythmColors.danger,
                            border    = RhythmColors.dangerBorder)
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: ThemeConfig, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.bg1)
            .border(if (selected) 2.dp else 0.5.dp,
                if (selected) theme.accent else theme.border1,
                RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)) {
            Column(modifier = Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(3.dp)).background(theme.bg3))
                Row(Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(theme.accent))
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(theme.caution))
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(theme.beatSelectedBorder))
                }
            }
        }
        Text(theme.name, style = RhythmType.label.copy(
            fontSize = 9.sp,
            color    = if (selected) RhythmColors.accent else RhythmColors.textMuted),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 64.dp))
    }
}

@Composable
private fun ImportSwatch(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RhythmColors.bg3)
                .border(0.5.dp, RhythmColors.border1, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", style = RhythmType.bpmValue.copy(
                    fontSize = 22.sp, color = RhythmColors.textMuted))
                Text("import", style = RhythmType.label.copy(
                    fontSize = 9.sp, color = RhythmColors.textDim))
            }
        }
        Text("custom", style = RhythmType.label.copy(
            fontSize = 9.sp, color = RhythmColors.textDim))
    }
}

@Composable private fun TSection(text: String) {
    Text(text, style = RhythmType.label.copy(fontSize = 9.sp, color = RhythmColors.textDim))
}

@Composable private fun TLabel(text: String) {
    Text(text, style = RhythmType.label.copy(fontSize = 10.sp, color = RhythmColors.textDim))
}

@Composable private fun TBtn(
    label: String, onClick: () -> Unit,
    bg: Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
    border: Color = Color.Unspecified,
) {
    val resolvedBg        = if (bg == Color.Unspecified) RhythmColors.bg3 else bg
    val resolvedTextColor = if (textColor == Color.Unspecified) RhythmColors.textSecondary else textColor
    val resolvedBorder    = if (border == Color.Unspecified) RhythmColors.border1 else border
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.height(32.dp)
            .clip(RoundedCornerShape(6.dp)).background(resolvedBg)
            .border(0.5.dp, resolvedBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp)) {
        Text(label, style = RhythmType.label.copy(fontSize = 11.sp, color = resolvedTextColor))
    }
}
