package com.jayc180.rhythmengine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import com.jayc180.rhythmengine.persistence.ProjectStorageIos
import com.jayc180.rhythmengine.ui.App
import com.jayc180.rhythmengine.ui.DocumentPickerHelper
import com.jayc180.rhythmengine.ui.PhotosPickerHelper
import com.jayc180.rhythmengine.ui.SoundPickerTarget
import com.jayc180.rhythmengine.ui.components.RhythmToggle
import com.jayc180.rhythmengine.ui.components.SoundPickerDialog
import com.jayc180.rhythmengine.ui.components.ThemeSettingsDialog
import com.jayc180.rhythmengine.ui.theme.BackgroundLayer
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmTheme
import com.jayc180.rhythmengine.ui.theme.RhythmType
import platform.Foundation.*
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val vm     = remember { RhythmViewModelIos() }
    val picker = remember { DocumentPickerHelper() }
    val photosPicker = remember { PhotosPickerHelper() }

    DisposableEffect(Unit) { onDispose { vm.dispose() } }

    val activeTheme      by vm.themeManager.activeTheme.collectAsState()
    val bgConfig         by vm.themeManager.bgConfig.collectAsState()
    val availableThemes  by vm.themeManager.availableThemes.collectAsState()
    val sounds           by vm.sounds.collectAsState()
    val hasSession       by vm.hasSession.collectAsState()
    val projects         by vm.projects.collectAsState()
    val projectName      by vm.projectName.collectAsState()
    val importState      by vm.importState.collectAsState()

    var showSettings     by remember { mutableStateOf(false) }
    var showThemeDialog  by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showOpenDialog   by remember { mutableStateOf(false) }
    var showHelpDialog   by remember { mutableStateOf(false) }
    var sessionHandled   by remember { mutableStateOf(false) }
    var soundPickerTarget by remember { mutableStateOf<SoundPickerTarget?>(null) }

    val appVm = remember(vm) {
        vm.buildAppViewModel(onSettingsClick = { showSettings = true })
    }

    RhythmTheme(theme = activeTheme) {
        BackgroundLayer(theme = activeTheme, bgConfig = bgConfig) {
            Box(modifier = Modifier.fillMaxSize()) {
                App(vm = appVm)

                if (showSettings) {
                    IosSettingsOverlay(
                        vm              = vm,
                        sounds          = sounds,
                        projectName     = projectName,
                        importState     = importState,
                        picker          = picker,
                        onDismiss       = { showSettings = false },
                        onAppearance    = { showThemeDialog = true },
                        onSaveAs        = { showSaveAsDialog = true; showSettings = false },
                        onSave          = { vm.save(); showSettings = false },
                        onOpenProjects  = { vm.refreshProjects(); showOpenDialog = true; showSettings = false },
                        onNewProject    = { vm.newProject(); showSettings = false },
                        onHelp          = { showHelpDialog = true },
                        openSoundPicker = { target -> soundPickerTarget = target },
                    )
                }

                if (showHelpDialog) {
                    com.jayc180.rhythmengine.ui.components.HelpDialog(
                        onDismiss = { showHelpDialog = false }
                    )
                }

                // Session restore prompt
                if (hasSession && !sessionHandled) {
                    SessionRestoreDialog(
                        onRestore = { vm.restoreSession(); sessionHandled = true },
                        onDiscard = { vm.discardSession(); sessionHandled = true },
                    )
                }
            }
        }

        if (showThemeDialog) {
            val themeUiVC = LocalUIViewController.current
            ThemeSettingsDialog(
                availableThemes  = availableThemes,
                activeTheme      = activeTheme,
                bgConfig         = bgConfig,
                onSelectTheme    = { vm.themeManager.setTheme(it) },
                onImportTheme    = { picker.pickJson(themeUiVC) { path -> vm.themeManager.importTheme(path) } },
                onPickBackground = { photosPicker.pickImage(themeUiVC) { path -> vm.themeManager.importBackground(path) } },
                onSetFitMode     = { vm.themeManager.setFitMode(it) },
                onSetDim         = { vm.themeManager.setDim(it) },
                onSetPanX        = { vm.themeManager.setPanX(it) },
                onSetPanY        = { vm.themeManager.setPanY(it) },
                onSetPanScale    = { vm.themeManager.setPanScale(it) },
                onRemoveBg       = { vm.themeManager.removeBackground() },
                onDismiss        = { showThemeDialog = false },
            )
        }

        if (showSaveAsDialog) {
            SaveAsDialog(
                initialName = projectName,
                onSave      = { name -> vm.saveAs(name); showSaveAsDialog = false },
                onDismiss   = { showSaveAsDialog = false },
            )
        }

        if (showOpenDialog) {
            OpenProjectDialog(
                projects  = projects,
                picker    = picker,
                onOpen    = { info -> vm.loadProject(info.path); showOpenDialog = false },
                onDelete  = { info -> vm.deleteProject(info) },
                onImport  = { path -> vm.importProjectFromPath(path); showOpenDialog = false },
                onDismiss = { showOpenDialog = false },
            )
        }

        soundPickerTarget?.let { target ->
            val currentSoundId = when (target) {
                is SoundPickerTarget.Beat ->
                    (vm.builder.state.value.tracks
                        .getOrNull(target.trackIndex)
                        ?.items?.getOrNull(target.itemIndex)
                            as? com.jayc180.rhythmengine.builder.TrackItem.Beat)?.soundId
                is SoundPickerTarget.TrackDefault ->
                    vm.builder.state.value.tracks.getOrNull(target.trackIndex)?.defaultSoundId
                is SoundPickerTarget.GlobalDefault ->
                    vm.globalDefaultSoundId
            }
            SoundPickerDialog(
                sounds         = sounds,
                currentSoundId = currentSoundId,
                onSelect       = { info ->
                    when (target) {
                        is SoundPickerTarget.Beat ->
                            vm.builder.setBeatSound(target.itemIndex, info.id)
                        is SoundPickerTarget.TrackDefault ->
                            vm.builder.setTrackDefaultSound(target.trackIndex, info.id)
                        is SoundPickerTarget.GlobalDefault ->
                            vm.setGlobalDefaultSound(info.id)
                    }
                    soundPickerTarget = null
                },
                onDismiss    = { soundPickerTarget = null },
                onDeleteUser = { soundId -> vm.deleteUserSound(soundId) },
            )
        }
    }
}

// ── Session restore ───────────────────────────────────────────────────────────

@Composable
private fun SessionRestoreDialog(onRestore: () -> Unit, onDiscard: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RhythmColors.bg2)
                .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Restore session?", style = RhythmType.bpmValue.copy(
                fontSize = 15.sp, color = RhythmColors.textPrimary))
            Text("You have unsaved work from the last session.",
                style = RhythmType.label.copy(fontSize = 11.sp, color = RhythmColors.textSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IosBtn("Discard", modifier = Modifier.weight(1f),
                    onClick = onDiscard,
                    bg = RhythmColors.bg3, textColor = RhythmColors.textMuted)
                IosBtn("Restore", modifier = Modifier.weight(1f),
                    onClick = onRestore,
                    bg = RhythmColors.accentBg, textColor = RhythmColors.accent)
            }
        }
    }
}

// ── Save As dialog ────────────────────────────────────────────────────────────

@Composable
private fun SaveAsDialog(initialName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RhythmColors.bg2)
                .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
                .clickable { }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Save As", style = RhythmType.bpmValue.copy(
                fontSize = 15.sp, color = RhythmColors.textPrimary))

            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                singleLine    = true,
                label         = { Text("Project name", style = RhythmType.label.copy(
                    color = RhythmColors.textMuted)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = RhythmColors.accent,
                    unfocusedBorderColor = RhythmColors.border2,
                    focusedTextColor     = RhythmColors.textPrimary,
                    unfocusedTextColor   = RhythmColors.textPrimary,
                    cursorColor          = RhythmColors.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IosBtn("Cancel", modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                    bg = RhythmColors.bg3, textColor = RhythmColors.textMuted)
                IosBtn("Save", modifier = Modifier.weight(1f),
                    onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                    bg = RhythmColors.accentBg, textColor = RhythmColors.accent)
            }
        }
    }
}

// ── Open project dialog ───────────────────────────────────────────────────────

@Composable
private fun OpenProjectDialog(
    projects:  List<ProjectStorageIos.ProjectInfo>,
    picker:    DocumentPickerHelper,
    onOpen:    (ProjectStorageIos.ProjectInfo) -> Unit,
    onDelete:  (ProjectStorageIos.ProjectInfo) -> Unit,
    onImport:  (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiVC = LocalUIViewController.current

    Box(
        modifier = Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RhythmColors.bg2)
                .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
                .clickable { },
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Open Project", style = RhythmType.bpmValue.copy(
                    fontSize = 15.sp, color = RhythmColors.textPrimary))
                Text("✕", style = RhythmType.label.copy(fontSize = 16.sp, color = RhythmColors.textMuted),
                    modifier = Modifier.clickable(onClick = onDismiss))
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(RhythmColors.border1))

            // Project list
            if (projects.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No saved projects yet.",
                        style = RhythmType.label.copy(fontSize = 12.sp, color = RhythmColors.textDim))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(projects) { info ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onOpen(info) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(info.name,
                                    style = RhythmType.label.copy(fontSize = 13.sp, color = RhythmColors.textPrimary),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                info.bpm?.let { bpm ->
                                    Text("${bpm.toInt()} bpm",
                                        style = RhythmType.label.copy(fontSize = 10.sp, color = RhythmColors.textDim))
                                }
                            }
                            Text("✕", style = RhythmType.label.copy(fontSize = 14.sp, color = RhythmColors.deleteText),
                                modifier = Modifier.clickable { onDelete(info) }.padding(start = 12.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(RhythmColors.border0))
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(0.5.dp).background(RhythmColors.border1))
            // Import from Files
            IosBtn(
                label    = "Import from Files…",
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                onClick  = { picker.pickAny(uiVC) { path -> onImport(path) } },
            )
        }
    }
}

// ── Main settings overlay ─────────────────────────────────────────────────────

@Composable
private fun IosSettingsOverlay(
    vm:              RhythmViewModelIos,
    sounds:          List<com.jayc180.rhythmengine.audio.SoundInfo>,
    projectName:     String,
    importState:     RhythmViewModelIos.ImportState,
    picker:          DocumentPickerHelper,
    onDismiss:       () -> Unit,
    onAppearance:    () -> Unit,
    onSaveAs:        () -> Unit,
    onSave:          () -> Unit,
    onOpenProjects:  () -> Unit,
    onNewProject:    () -> Unit,
    onHelp:          () -> Unit,
    openSoundPicker: (SoundPickerTarget) -> Unit,
) {
    val uiVC             = LocalUIViewController.current
    val defaultSoundLabel = sounds.firstOrNull { it.id == vm.globalDefaultSoundId }?.label ?: "—"

    Box(
        modifier = Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RhythmColors.bg2)
                .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
                .clickable { }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Title + close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", style = RhythmType.bpmValue.copy(
                    fontSize = 15.sp, color = RhythmColors.textPrimary))
                Text("✕", style = RhythmType.label.copy(
                    fontSize = 16.sp, color = RhythmColors.textMuted),
                    modifier = Modifier.clickable(onClick = onDismiss))
            }

            Divider()

            // Current project name
            Text(
                "Project: $projectName",
                style = RhythmType.label.copy(fontSize = 11.sp, color = RhythmColors.textSecondary),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )

            // Save / Save As row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IosBtn("Save", modifier = Modifier.weight(1f), onClick = onSave,
                    bg = RhythmColors.accentBg, textColor = RhythmColors.accent)
                IosBtn("Save As…", modifier = Modifier.weight(1f), onClick = onSaveAs)
            }

            // Open / New row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IosBtn("Open…", modifier = Modifier.weight(1f), onClick = onOpenProjects)
                IosBtn("New Project", modifier = Modifier.weight(1f), onClick = onNewProject,
                    bg = RhythmColors.bg3, textColor = RhythmColors.textMuted)
            }

            // Export
            IosBtn("Export .rhy…", modifier = Modifier.fillMaxWidth(),
                onClick = { exportProject(vm, projectName, uiVC) })

            Divider()

            val pairedBracketDelete by vm.builder.pairedBracketDeleteFlow.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Paired bracket delete", style = RhythmType.label.copy(
                        fontSize = 11.sp, color = RhythmColors.textSecondary))
                    Text("deleting [ or ] removes both", style = RhythmType.label.copy(
                        fontSize = 9.sp, color = RhythmColors.textDim))
                }
                RhythmToggle(
                    checked  = pairedBracketDelete,
                    onToggle = { vm.setPairedBracketDelete(!pairedBracketDelete) },
                )
            }

            Divider()

            // Sound import
            IosBtn("Import Sound (.wav)…", modifier = Modifier.fillMaxWidth(),
                onClick = { picker.pickWav(uiVC) { path -> vm.importSoundFromPath(path) } })

            // Import state feedback
            when (val s = importState) {
                is RhythmViewModelIos.ImportState.Loading ->
                    Text("Importing…", style = RhythmType.label.copy(
                        fontSize = 11.sp, color = RhythmColors.textSecondary))
                is RhythmViewModelIos.ImportState.Success ->
                    Text("Imported: ${s.label}", style = RhythmType.label.copy(
                        fontSize = 11.sp, color = RhythmColors.accent))
                is RhythmViewModelIos.ImportState.Error ->
                    Text(s.msg, style = RhythmType.label.copy(
                        fontSize = 11.sp, color = RhythmColors.caution))
                else -> Unit
            }

            Divider()

            // Appearance + default sound
            IosBtn("Appearance…", modifier = Modifier.fillMaxWidth(), onClick = onAppearance)

            Divider()

            val uriHandler = LocalUriHandler.current
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IosBtn("Support",
                    onClick = { uriHandler.openUri(com.jayc180.rhythmengine.ui.components.SUPPORT_URL) },
                    textColor = RhythmColors.textDim)
                IosBtn("Help", modifier = Modifier.weight(1f), onClick = onHelp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Default sound", style = RhythmType.label.copy(
                    fontSize = 11.sp, color = RhythmColors.textSecondary))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(RhythmColors.bg3)
                        .border(0.5.dp, RhythmColors.border1, RoundedCornerShape(4.dp))
                        .clickable { openSoundPicker(SoundPickerTarget.GlobalDefault) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(defaultSoundLabel, style = RhythmType.label.copy(
                        fontSize = 11.sp, color = RhythmColors.textPrimary))
                }
            }
        }
    }
}

// ── Share helper ──────────────────────────────────────────────────────────────

private fun exportProject(vm: RhythmViewModelIos, name: String, from: UIViewController) {
    val json     = com.jayc180.rhythmengine.persistence.ProjectSerializer
        .serialize(vm.builder.state.value, name)
    val safeName = name.replace(Regex("[^a-zA-Z0-9 _-]"), "_").trim().take(40)
    val tmpPath  = "${NSTemporaryDirectory()}${safeName}.rhy"
    vm.storage.safeWrite(tmpPath, json)
    val url      = NSURL.fileURLWithPath(tmpPath)
    // UIDocumentPickerViewController manages its own iPad popover — no manual config needed
    val picker   = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
    from.presentViewController(picker, animated = true, completion = null)
}

// ── Reusable button / divider ─────────────────────────────────────────────────

@Composable
private fun IosBtn(
    label:     String,
    onClick:   () -> Unit,
    modifier:  Modifier = Modifier,
    bg:        androidx.compose.ui.graphics.Color = RhythmColors.bg3,
    textColor: androidx.compose.ui.graphics.Color = RhythmColors.textSecondary,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.5.dp, RhythmColors.border1, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Text(label, maxLines = 1, style = RhythmType.label.copy(
            fontSize = 12.sp, color = textColor))
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(RhythmColors.border1))
}
