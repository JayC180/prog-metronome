package com.jayc180.rhythmengine

import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.jayc180.rhythmengine.audio.SoundInfo
import com.jayc180.rhythmengine.persistence.ProjectSerializer
import com.jayc180.rhythmengine.persistence.ProjectStorage
import com.jayc180.rhythmengine.ui.App
import com.jayc180.rhythmengine.ui.AppViewModel
import com.jayc180.rhythmengine.ui.SoundPickerTarget
import com.jayc180.rhythmengine.ui.components.RhythmToggle
import com.jayc180.rhythmengine.ui.components.SoundPickerDialog
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmType
import com.jayc180.rhythmengine.ui.components.ThemeSettingsDialog
import com.jayc180.rhythmengine.ui.theme.BackgroundLayer
import com.jayc180.rhythmengine.ui.theme.RhythmTheme

class MainActivity : ComponentActivity() {

    private val vm: RhythmViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private val projectVm: ProjectViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        PlaybackService.onPlayCallback  = { vm.builder.play() }
        PlaybackService.onStopCallback  = { vm.builder.stop() }

        projectVm.startAutoSave(vm.builder.state)
        projectVm.refreshProjectList()
        handleIncomingIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent { MainContent() }
    }

    override fun onDestroy() {
        super.onDestroy()
        PlaybackService.onPlayCallback  = null
        PlaybackService.onStopCallback  = null
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                projectVm.loadFromRaw(stream.bufferedReader().readText())
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to open .rhy: ${e.message}")
        }
    }

    @Composable
    private fun MainContent() {
        val activeTheme     by vm.themeManager.activeTheme.collectAsState()
        val bgConfig        by vm.themeManager.bgConfig.collectAsState()
        val availableThemes by vm.themeManager.availableThemes.collectAsState()
        val hasSession      by projectVm.hasSession.collectAsState()
        val sounds          by vm.sounds.collectAsState()
        val importState     by vm.importState.collectAsState()

        val filePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { vm.importSoundFromUri(it) } }

        val themePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { vm.themeManager.importTheme(it) } }

        val bgPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { vm.themeManager.importBackground(it) } }

        var exportProjectName by remember { mutableStateOf("Untitled") }
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val json = ProjectSerializer.serialize(vm.builder.state.value, exportProjectName)
            try { contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            catch (e: Exception) { android.util.Log.e("MainActivity", "Export failed: ${e.message}") }
        }

        LaunchedEffect(Unit) {
            android.util.Log.d("ThemeDebug", "Active theme changed to: ${activeTheme.name}, accent=${activeTheme.accent}")
            projectVm.events.collect { event ->
                if (event is ProjectViewModel.StorageEvent.LoadedOk) {
                    vm.builder.restoreState(event.state)
                }
            }
        }

        // dialog stuff
        var sessionDialogDismissed by remember { mutableStateOf(false) }
        var showSettings           by remember { mutableStateOf(false) }
        var showThemeDialog        by remember { mutableStateOf(false) }
        var showHelpDialog         by remember { mutableStateOf(false) }
        var soundPickerTarget      by remember { mutableStateOf<SoundPickerTarget?>(null) }

        val appVm = remember(vm) {
            AppViewModel(
                builder               = vm.builder,
                audio                 = vm.audioEngine,
                playheads             = vm.playheads,
                sounds                = vm.sounds,
                onSettingsClick       = { showSettings = true },
                _globalDefaultSoundId = vm.globalDefaultSoundId,
                onSetGlobalDefault    = { id -> vm.setGlobalDefaultSound(id) },
            )
        }

        // anything using RhythmColors must be inside
        RhythmTheme(theme = activeTheme) {
            BackgroundLayer(theme = activeTheme, bgConfig = bgConfig) {
                Box(modifier = Modifier.fillMaxSize()) {
                    App(vm = appVm)

                    if (showSettings) {
                        SettingsOverlay(
                            projectVm       = projectVm,
                            rhythmVm        = vm,
                            sounds          = sounds,
                            importState     = importState,
                            onDismiss       = { showSettings = false },
                            openSoundPicker = { target -> soundPickerTarget = target },
                            onImportSound   = { filePicker.launch("audio/*") },
                            onShare         = { shareCurrentProject() },
                            onExport        = {
                                exportProjectName = projectVm.projectName.value
                                exportLauncher.launch("${projectVm.projectName.value}.rhy")
                            },
                            onAppearance    = { showThemeDialog = true },
                            onHelp          = { showHelpDialog = true },
                        )
                    }

                    if (showHelpDialog) {
                        com.jayc180.rhythmengine.ui.components.HelpDialog(
                            onDismiss = { showHelpDialog = false }
                        )
                    }
                }
            }

            // ── All dialogs inside RhythmTheme, outside BackgroundLayer ───────
            // (dialogs are full-screen overlays, don't need the background image)

            if (hasSession && !sessionDialogDismissed) {
                SessionRestoreDialog(
                    onRestore = { sessionDialogDismissed = true; projectVm.restoreSession() },
                    onDiscard = { sessionDialogDismissed = true; projectVm.discardSession() },
                )
            }

            if (showThemeDialog) {
                ThemeSettingsDialog(
                    availableThemes  = availableThemes,
                    activeTheme      = activeTheme,
                    bgConfig         = bgConfig,
                    onSelectTheme    = { vm.themeManager.setTheme(it) },
                    onImportTheme    = { themePicker.launch("application/json") },
                    onPickBackground = { bgPicker.launch("image/*") },
                    onSetFitMode     = { vm.themeManager.setFitMode(it) },
                    onSetDim         = { vm.themeManager.setDim(it) },
                    onSetPanX        = { vm.themeManager.setPanX(it) },
                    onSetPanY        = { vm.themeManager.setPanY(it) },
                    onSetPanScale    = { vm.themeManager.setPanScale(it) },
                    onRemoveBg       = { vm.themeManager.removeBackground() },
                    onDismiss        = { showThemeDialog = false },
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
                        vm.builder.state.value.tracks
                            .getOrNull(target.trackIndex)?.defaultSoundId
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

    private fun shareCurrentProject() {
        val state = vm.builder.state.value
        val name  = projectVm.projectName.value
        val json  = ProjectSerializer.serialize(state, name)
        val file  = java.io.File(cacheDir, "$name.rhy").also { it.writeText(json) }
        val uri   = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type    = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$name.rhy")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share $name.rhy",
        ))
    }
}

// ── SettingsOverlay and dialog composables ────────────────────────────────────
// (identical to previous version — omitted for brevity)
// Copy from previous MainActivity.kt

@Composable
private fun SettingsOverlay(
    projectVm:       ProjectViewModel,
    rhythmVm:        RhythmViewModel,
    sounds:          List<SoundInfo>,
    importState:     RhythmViewModel.ImportState,
    onDismiss:       () -> Unit,
    openSoundPicker: (SoundPickerTarget) -> Unit,
    onImportSound:   () -> Unit,
    onShare:         () -> Unit,
    onExport:        () -> Unit,
    onAppearance:    () -> Unit,
    onHelp:          () -> Unit,
) {
    val projectName by projectVm.projectName.collectAsState()
    val projects    by projectVm.projects.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    val defaultSoundLabel = sounds.firstOrNull { it.id == rhythmVm.globalDefaultSoundId }?.label ?: "—"
    val pairedBracketDelete by rhythmVm.builder.pairedBracketDeleteFlow
        .collectAsState()

    Box(modifier = Modifier.fillMaxSize()
        .background(Color.Black.copy(alpha = 0.6f))
        .clickable(onClick = onDismiss))

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RhythmColors.bg2)
            .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
            .clickable { }
            .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically) {
                Text("Settings", style = RhythmType.bpmValue.copy(
                    fontSize = 15.sp, color = RhythmColors.textPrimary))
                Text("✕", style = RhythmType.label.copy(
                    fontSize = 16.sp, color = RhythmColors.textMuted),
                    modifier = Modifier.clickable(onClick = onDismiss))
            }
            SDivider()
            Text("Project: $projectName", style = RhythmType.label.copy(
                fontSize = 11.sp, color = RhythmColors.textSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SBtn("Save", modifier = Modifier.weight(1f),
                    onClick = { projectVm.save(rhythmVm.builder.state.value); onDismiss() })
                SBtn("Save As…", modifier = Modifier.weight(1f),
                    onClick = { showSaveDialog = true })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SBtn("Open…", modifier = Modifier.weight(1f),
                    onClick = { projectVm.refreshProjectList(); showOpenDialog = true })
                SBtn("New Project", modifier = Modifier.weight(1f),
                    onClick = {
                        rhythmVm.builder.restoreState(
                            com.jayc180.rhythmengine.builder.TrackBuilderState(
                                tracks           = listOf(com.jayc180.rhythmengine.builder.TrackBuilder.newTrackDraft(0)),
                                activeTrackIndex = 0,
                                bpm              = 120.0,
                            )
                        )
                        onDismiss()
                    },
                    bg = RhythmColors.bg3, textColor = RhythmColors.textMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SBtn("Share", modifier = Modifier.weight(1f),
                    onClick = { onShare(); onDismiss() },
                    bg = RhythmColors.bg3, textColor = RhythmColors.textSecondary,
                    border = RhythmColors.border1)
                SBtn("Export…", modifier = Modifier.weight(1f),
                    onClick = { onExport(); onDismiss() },
                    bg = RhythmColors.bg3, textColor = RhythmColors.textSecondary,
                    border = RhythmColors.border1)
            }
            SDivider()
            // Paired bracket delete toggle
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Paired bracket delete",
                        style = RhythmType.label.copy(fontSize = 11.sp,
                            color = RhythmColors.textSecondary))
                    Text("deleting [ or ] removes both",
                        style = RhythmType.label.copy(fontSize = 9.sp,
                            color = RhythmColors.textDim))
                }
                RhythmToggle(
                    checked  = pairedBracketDelete,
                    onToggle = {
                        val newVal = !pairedBracketDelete
                        rhythmVm.builder.pairedBracketDelete = newVal
                        rhythmVm.savePref("paired_bracket_delete", newVal)
                    }
                )
            }
            SDivider()
            SBtn("Appearance…",
                onClick   = onAppearance,
                bg        = RhythmColors.bg3,
                textColor = RhythmColors.textSecondary,
                border    = RhythmColors.border1,
                modifier  = Modifier.fillMaxWidth()
            )
            val uriHandler = LocalUriHandler.current
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SBtn("Support",
                    onClick   = { uriHandler.openUri(com.jayc180.rhythmengine.ui.components.SUPPORT_URL) },
                    bg        = RhythmColors.bg3,
                    textColor = RhythmColors.textDim,
                    border    = RhythmColors.border0)
                SBtn("Help", modifier = Modifier.weight(1f),
                    onClick   = onHelp,
                    bg        = RhythmColors.bg3,
                    textColor = RhythmColors.textSecondary,
                    border    = RhythmColors.border1)
            }
            SDivider()
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Default sound", style = RhythmType.label.copy(
                    fontSize = 11.sp, color = RhythmColors.textSecondary))
                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    .background(RhythmColors.bg3)
                    .border(0.5.dp, RhythmColors.border1, RoundedCornerShape(4.dp))
                    .clickable { openSoundPicker(SoundPickerTarget.GlobalDefault) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(defaultSoundLabel, style = RhythmType.label.copy(
                        fontSize = 11.sp, color = RhythmColors.textPrimary))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SBtn("Import Sound…", modifier = Modifier.fillMaxWidth(),
                    onClick = onImportSound,
                    bg = RhythmColors.bg3, textColor = RhythmColors.textSecondary,
                    border = RhythmColors.border1)
                when (val s = importState) {
                    is RhythmViewModel.ImportState.Loading ->
                        Text("Importing…", style = RhythmType.label.copy(
                            fontSize = 11.sp, color = RhythmColors.textMuted))
                    is RhythmViewModel.ImportState.Success ->
                        Text("✓ Imported \"${s.label}\"", style = RhythmType.label.copy(
                            fontSize = 11.sp, color = RhythmColors.accent))
                    is RhythmViewModel.ImportState.Error ->
                        Text("✗ ${s.message}", style = RhythmType.label.copy(
                            fontSize = 11.sp, color = Color(0xFFDE7A7A)))
                    else -> Unit
                }
            }
        }
    }

    if (showSaveDialog) SaveAsDialog(
        initialName = projectName,
        onSave = { name -> projectVm.saveAs(rhythmVm.builder.state.value, name); showSaveDialog = false; onDismiss() },
        onCancel = { showSaveDialog = false })

    if (showOpenDialog) OpenDialog(
        projects = projects,
        onOpen = { info -> projectVm.load(info.file); showOpenDialog = false; onDismiss() },
        onDelete = { info -> projectVm.deleteProject(info) },
        onCancel = { showOpenDialog = false })
}

@Composable private fun SessionRestoreDialog(onRestore: () -> Unit, onDiscard: () -> Unit) {
    Dialog(onDismissRequest = onDiscard) {
        Column(modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(RhythmColors.bg2)
            .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
            .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Restore session?", style = RhythmType.bpmValue.copy(
                fontSize = 16.sp, color = RhythmColors.textPrimary))
            Text("You have unsaved work from a previous session.",
                style = RhythmType.label.copy(fontSize = 12.sp, color = RhythmColors.textSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SBtn("Discard", onClick = onDiscard, bg = RhythmColors.bg3, textColor = RhythmColors.textMuted)
                SBtn("Restore", onClick = onRestore, bg = RhythmColors.accentBg,
                    textColor = RhythmColors.accent, border = RhythmColors.accentBorder)
            }
        }
    }
}

@Composable private fun SaveAsDialog(initialName: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(if (initialName == "Untitled") "" else initialName) }
    Dialog(onDismissRequest = onCancel) {
        Column(modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(RhythmColors.bg2)
            .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
            .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Save As", style = RhythmType.bpmValue.copy(
                fontSize = 15.sp, color = RhythmColors.textPrimary))
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Project name", style = RhythmType.label.copy(color = RhythmColors.textMuted)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSave(name.trim()) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RhythmColors.accent, unfocusedBorderColor = RhythmColors.border2,
                    focusedTextColor = RhythmColors.textPrimary, unfocusedTextColor = RhythmColors.textPrimary,
                    cursorColor = RhythmColors.accent),
                modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SBtn("Cancel", onClick = onCancel, bg = RhythmColors.bg3, textColor = RhythmColors.textMuted)
                SBtn("Save", onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                    bg = RhythmColors.accentBg, textColor = RhythmColors.accent, border = RhythmColors.accentBorder)
            }
        }
    }
}

@Composable private fun OpenDialog(
    projects: List<ProjectStorage.ProjectInfo>,
    onOpen: (ProjectStorage.ProjectInfo) -> Unit,
    onDelete: (ProjectStorage.ProjectInfo) -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Column(modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(RhythmColors.bg2)
            .border(0.5.dp, RhythmColors.border2, RoundedCornerShape(12.dp))
            .padding(20.dp).heightIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Open Project", style = RhythmType.bpmValue.copy(
                fontSize = 15.sp, color = RhythmColors.textPrimary))
            if (projects.isEmpty()) {
                Text("No saved projects yet.", style = RhythmType.label.copy(
                    color = RhythmColors.textDim, fontSize = 12.sp))
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(projects) { info ->
                        Row(modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp)).background(RhythmColors.bg3)
                            .border(0.5.dp, RhythmColors.border1, RoundedCornerShape(6.dp))
                            .clickable { onOpen(info) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(info.name, style = RhythmType.label.copy(
                                    fontSize = 12.sp, color = RhythmColors.textPrimary))
                                info.bpm?.let { Text("${it.toInt()} bpm", style = RhythmType.label.copy(
                                    fontSize = 10.sp, color = RhythmColors.textMuted)) }
                            }
                            Text("🗑", fontSize = 14.sp,
                                modifier = Modifier.clickable { onDelete(info) }.padding(start = 12.dp))
                        }
                    }
                }
            }
            SBtn("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth(),
                bg = RhythmColors.bg3, textColor = RhythmColors.textMuted)
        }
    }
}

@Composable private fun SDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(RhythmColors.border1))
}

@Composable private fun SBtn(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    bg: Color = RhythmColors.accentBg, textColor: Color = RhythmColors.accent,
    border: Color = RhythmColors.accentBorder,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.height(36.dp)
        .clip(RoundedCornerShape(6.dp)).background(bg)
        .border(0.5.dp, border, RoundedCornerShape(6.dp))
        .clickable(onClick = onClick).padding(horizontal = 16.dp)) {
        Text(label, style = RhythmType.label.copy(fontSize = 12.sp, color = textColor))
    }
}