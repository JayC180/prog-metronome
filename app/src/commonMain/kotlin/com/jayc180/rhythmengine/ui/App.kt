package com.jayc180.rhythmengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jayc180.rhythmengine.audio.SoundInfo
import com.jayc180.rhythmengine.builder.*
import com.jayc180.rhythmengine.ui.components.*
import com.jayc180.rhythmengine.ui.theme.BuiltInThemes
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmTheme
import com.jayc180.rhythmengine.ui.theme.RhythmType
import com.jayc180.rhythmengine.ui.theme.ThemeConfig

/**
 * Sound picker context determines what happens when a sound is selected
 * Hoisted to App level so one SoundPickerDialog handles all three call sites
 */
sealed class SoundPickerTarget {
    // override in order
    // for a specifc beat item
    data class Beat(val trackIndex: Int, val itemIndex: Int) : SoundPickerTarget()
    // for a specific track
    data class TrackDefault(val trackIndex: Int) : SoundPickerTarget()
    // global default
    object GlobalDefault : SoundPickerTarget()
}

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun App(vm: AppViewModel) {
    val state     by vm.builder.state.collectAsState()
    val playheads by vm.playheads.collectAsState()
    val sounds    by vm.sounds.collectAsState()
    val buildErrors by vm.builder.lastBuildErrors.collectAsState()

    // dialog states
    var showMmDialog         by remember { mutableStateOf(false) }
    var showSetBpmDialog     by remember { mutableStateOf(false) }
    var showRepeatDialog     by remember { mutableStateOf(false) }
    var showCustomBeatDialog by remember { mutableStateOf(false) }
    var showCustomDenomDialog by remember { mutableStateOf(false) }
    var showBpmDialog        by remember { mutableStateOf(false) }
    var soundPickerTarget    by remember { mutableStateOf<SoundPickerTarget?>(null) }

    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) vm.builder.setCursor(null)
    }

    val cursorItem = state.cursorItem

    // callback
    val onEditToggle: () -> Unit = {
        when (cursorItem) {
            is TrackItem.Modulation -> showMmDialog = true
            is TrackItem.SetBpm     -> showSetBpmDialog = true
            else                    -> vm.builder.toggleEditMode()
        }
    }
    val onCustomBeat: () -> Unit = { showCustomBeatDialog = true }
    val onCustomDenom: () -> Unit = { showCustomDenomDialog = true }

    // in beat edit panel, track, and settings
    val openSoundPicker: (SoundPickerTarget) -> Unit = { target ->
        soundPickerTarget = target
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < 600.dp) {
            PortraitLayout(
                state, playheads, cursorItem, vm, sounds,
                onMmClick         = { showMmDialog = true },
                onSetBpmClick     = { showSetBpmDialog = true },
                onRepeatCustom    = { showRepeatDialog = true },
                onEditToggle      = onEditToggle,
                onCustomBeat      = onCustomBeat,
                onCustomDenom     = onCustomDenom,
                openSoundPicker   = openSoundPicker,
                onBpmClick        = { showBpmDialog = true},
                buildErrors       = buildErrors,
                    )
        } else {
            LandscapeLayout(
                state, playheads, cursorItem, vm, sounds,
                onMmClick         = { showMmDialog = true },
                onSetBpmClick     = { showSetBpmDialog = true },
                onRepeatCustom    = { showRepeatDialog = true },
                onEditToggle      = onEditToggle,
                onCustomBeat      = onCustomBeat,
                onCustomDenom     = onCustomDenom,
                openSoundPicker   = openSoundPicker,
                onBpmClick        = { showBpmDialog = true},
                buildErrors       = buildErrors,
            )
        }
    }

    // dialogs

    if (showMmDialog) {
        val existingMm = cursorItem as? TrackItem.Modulation
        MmDialog(
            initialP   = existingMm?.p,
            initialQ   = existingMm?.q,
            onConfirm  = { p, q ->
                if (existingMm != null && state.cursorIndex != null)
                    vm.builder.replaceModulation(state.cursorIndex!!, p, q)
                else vm.builder.commitModulation(p, q)
                showMmDialog = false; vm.builder.exitEditMode()
            },
            onDismiss  = { showMmDialog = false; vm.builder.exitEditMode() },
        )
    }

    if (showSetBpmDialog) {
        val existingBpm = cursorItem as? TrackItem.SetBpm
        SetBpmDialog(
            currentBpm = state.displayBpm,
            initialBpm = existingBpm?.bpm,
            onConfirm  = { bpm ->
                if (existingBpm != null && state.cursorIndex != null)
                    vm.builder.replaceSetBpm(state.cursorIndex!!, bpm)
                else vm.builder.commitSetBpm(bpm)
                showSetBpmDialog = false; vm.builder.exitEditMode()
            },
            onDismiss  = { showSetBpmDialog = false; vm.builder.exitEditMode() },
        )
    }

    if (showRepeatDialog) {
        RepeatDialog(
            onConfirm = { count ->
                val isEditingRepeat = state.isEditMode && state.cursorItem is TrackItem.Repeat
                when {
                    count == TrackItem.Repeat.INFINITE -> vm.builder.setRepeatInfinite()
                    isEditingRepeat                    -> vm.builder.replaceRepeat(count)
                    else                               -> vm.builder.setRepeatCustom(count)
                }
                showRepeatDialog = false
            },
            onDismiss = { showRepeatDialog = false },
        )
    }

    if (showCustomBeatDialog) {
        CustomNumberDialog(
            title     = "Beat value",
            hint      = "numerator — must be greater than 0",
            onConfirm = { n ->
                val isEditingBeat = state.isEditMode && state.cursorItem is TrackItem.Beat
                if (isEditingBeat) vm.builder.replaceBeat(n)
                else vm.builder.enterBeat(n)
                showCustomBeatDialog = false
            },
            onDismiss = { showCustomBeatDialog = false },
        )
    }

    if (showCustomDenomDialog) {
        CustomNumberDialog(
            title     = "Subdivision",
            hint      = "denominator — e.g. 4 = quarter note, 8 = eighth note",
            onConfirm = { n ->
                vm.builder.setDenom(n)
                showCustomDenomDialog = false
            },
            onDismiss = { showCustomDenomDialog = false },
        )
    }

    if (showBpmDialog) {
        BpmInputDialog(
            currentBpm = state.bpm,
            onConfirm  = { bpm ->
                vm.builder.setBpm(bpm)
                showBpmDialog = false
            },
            onDismiss = { showBpmDialog = false },
        )
    }

    soundPickerTarget?.let { target ->
        val currentSoundId = when (target) {
            is SoundPickerTarget.Beat ->
                (state.tracks.getOrNull(target.trackIndex)
                    ?.items?.getOrNull(target.itemIndex) as? TrackItem.Beat)?.soundId

            is SoundPickerTarget.TrackDefault ->
                state.tracks.getOrNull(target.trackIndex)?.defaultSoundId

            is SoundPickerTarget.GlobalDefault ->
                vm.globalDefaultSoundId
        }

        SoundPickerDialog(
            sounds = sounds,
            currentSoundId = currentSoundId,
            onSelect = { entry ->
                when (target) {
                    is SoundPickerTarget.Beat ->
                        vm.builder.setBeatSound(target.itemIndex, entry.id)

                    is SoundPickerTarget.TrackDefault ->
                        vm.builder.setTrackDefaultSound(target.trackIndex, entry.id)

                    is SoundPickerTarget.GlobalDefault ->
                        vm.setGlobalDefault(entry.id)
                }
                soundPickerTarget = null
            },
            onDismiss = { soundPickerTarget = null },
        )
    }
}

@Composable
private fun PortraitLayout(
    state: TrackBuilderState,
    playheads: Map<String, Int>,
    cursorItem: TrackItem?,
    vm: AppViewModel,
    sounds: List<SoundInfo>,
    onMmClick: () -> Unit,
    onSetBpmClick: () -> Unit,
    onRepeatCustom: () -> Unit,
    onEditToggle: () -> Unit,
    onCustomBeat: () -> Unit,
    onCustomDenom: () -> Unit,
    onBpmClick: () -> Unit,
    openSoundPicker: (SoundPickerTarget) -> Unit,
    buildErrors: List<String>,
) {
    val globalDefaultLabel = sounds.firstOrNull { it.id == vm.globalDefaultSoundId }?.label

    Column(modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopBar(
            bpm             = state.displayBpm,
            isPlaying       = state.isPlaying,
            playbackState   = state.playbackState,
            onSettingsClick = vm.onSettingsClick,
            onBpmClick      = onBpmClick,
            onPlayStopClick = { vm.builder.togglePlayStop() },
        )
        HorizontalDivider()

        if (buildErrors.isNotEmpty()) {
            BuildErrorBanner(errors = buildErrors, onDismiss = { vm.builder.clearBuildErrors() })
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(state.tracks) { index, draft ->
                TrackRow(
                    draft               = draft,
                    isActive            = index == state.activeTrackIndex,
                    cursorIndex         = if (index == state.activeTrackIndex) state.cursorIndex else null,
                    isPlaying           = state.isPlaying,
                    playingItemIndex    = playheads[draft.id],
                    globalDefaultSound  = globalDefaultLabel,
                    onTrackClick        = { vm.builder.setActiveTrack(index) },
                    onItemClick         = { itemIdx ->
                        vm.builder.setActiveTrack(index)
                        vm.builder.setCursor(itemIdx)
                    },
                    onMuteClick         = { vm.builder.setTrackMuted(index, !draft.muted) },
                    onSoloClick         = { vm.builder.setTrackSoloed(index, !draft.soloed) },
                    onDeleteClick       = { vm.builder.deleteTrack(index) },
                    onTrackSoundClick   = { openSoundPicker(SoundPickerTarget.TrackDefault(index)) },
                )
                HorizontalDivider()
            }
            item { AddTrackRow(onAdd = { vm.builder.addTrack() }, onCopy = { vm.builder.copyTrack() }); HorizontalDivider() }
        }

        HorizontalDivider()
        BottomPanelWired(
            state, cursorItem, vm, sounds,
            onMmClick, onSetBpmClick, onRepeatCustom, onEditToggle, onCustomBeat,
            onCustomDenom, openSoundPicker,
        )
    }
}

// for tablet and later desktop
@Composable
private fun LandscapeLayout(
    state:           TrackBuilderState,
    playheads:       Map<String, Int>,
    cursorItem:      TrackItem?,
    vm:              AppViewModel,
    sounds:          List<SoundInfo>,
    onMmClick:       () -> Unit,
    onSetBpmClick:   () -> Unit,
    onRepeatCustom:  () -> Unit,
    onEditToggle:    () -> Unit,
    onCustomBeat:    () -> Unit,
    onCustomDenom:   () -> Unit,
    onBpmClick: () -> Unit,
    openSoundPicker: (SoundPickerTarget) -> Unit,
    buildErrors: List<String>,
) {
    val globalDefaultLabel = sounds.firstOrNull { it.id == vm.globalDefaultSoundId }?.label

    Column(modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopBar(
            bpm             = state.displayBpm,
            isPlaying       = state.isPlaying,
            playbackState   = state.playbackState,
            onSettingsClick = vm.onSettingsClick,
            onBpmClick      = onBpmClick,
            onPlayStopClick = { vm.builder.togglePlayStop() },
        )

        if (buildErrors.isNotEmpty()) {
            BuildErrorBanner(errors = buildErrors, onDismiss = { vm.builder.clearBuildErrors() })
        }

        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1.4f).fillMaxHeight()) {
                itemsIndexed(state.tracks) { index, draft ->
                    TrackRow(
                        draft               = draft,
                        isActive            = index == state.activeTrackIndex,
                        cursorIndex         = if (index == state.activeTrackIndex) state.cursorIndex else null,
                        isPlaying           = state.isPlaying,
                        playingItemIndex    = playheads[draft.id],
                        globalDefaultSound  = globalDefaultLabel,
                        onTrackClick        = { vm.builder.setActiveTrack(index) },
                        onItemClick         = { itemIdx ->
                            vm.builder.setActiveTrack(index)
                            vm.builder.setCursor(itemIdx)
                        },
                        onMuteClick         = { vm.builder.setTrackMuted(index, !draft.muted) },
                        onSoloClick         = { vm.builder.setTrackSoloed(index, !draft.soloed) },
                        onDeleteClick       = { vm.builder.deleteTrack(index) },
                        onTrackSoundClick   = { openSoundPicker(SoundPickerTarget.TrackDefault(index)) },
                    )
                    HorizontalDivider()
                }
                item { AddTrackRow(onAdd = { vm.builder.addTrack() }, onCopy = { vm.builder.copyTrack() }) }
            }
            Box(Modifier.width(0.5.dp).fillMaxHeight().background(RhythmColors.border0))
            BottomPanelWired(
                state, cursorItem, vm, sounds,
                onMmClick, onSetBpmClick, onRepeatCustom, onEditToggle, onCustomBeat, onCustomDenom,
                openSoundPicker,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun BottomPanelWired(
    state:           TrackBuilderState,
    cursorItem:      TrackItem?,
    vm:              AppViewModel,
    sounds:          List<SoundInfo>,
    onMmClick:       () -> Unit,
    onSetBpmClick:   () -> Unit,
    onRepeatCustom:  () -> Unit,
    onEditToggle:    () -> Unit,
    onCustomBeat:    () -> Unit,
    onCustomDenom:   () -> Unit,
    openSoundPicker: (SoundPickerTarget) -> Unit,
    modifier:        Modifier = Modifier,
) {
    com.jayc180.rhythmengine.ui.components.BottomPanel(
        state              = state,
        cursorItem         = cursorItem,
        onNavPrev          = { vm.builder.moveCursorPrev() },
        onNavNext          = { vm.builder.moveCursorNext() },
        onDenomToggle      = { vm.builder.toggleDenomMode() },
        onOpenBracket      = { vm.builder.openBracket() },
        onCloseBracket     = { vm.builder.closeBracket() },
        onRepeatToggle     = { vm.builder.toggleRepeatMode() },
        onMmClick          = onMmClick,
        onSetBpmClick      = onSetBpmClick,
        onNumpad           = { n -> handleNumpad(n, state, vm) },
        onEditToggle       = onEditToggle,
        onCustom           = { handleCustom(state, vm, onRepeatCustom, onSetBpmClick, onCustomBeat, onCustomDenom) },
        onBackspace        = { handleBackspace(state, vm) },
        onBeatActiveToggle = {
            val idx  = state.cursorIndex ?: return@BottomPanel
            val beat = cursorItem as? TrackItem.Beat ?: return@BottomPanel
            vm.builder.updateBeatAt(idx) { it.copy(active = !it.active) }
        },
        onSoundChange = {
            // "change ›" in edit panel — assigns to the selected beat
            val idx = state.cursorIndex ?: return@BottomPanel
            if (cursorItem !is TrackItem.Beat) return@BottomPanel
            openSoundPicker(SoundPickerTarget.Beat(state.activeTrackIndex, idx))
        },
        onVolumeChange = { vol ->
            // Read cursorIndex fresh — don't use captured state.cursorIndex
            val currentIdx = vm.builder.state.value.cursorIndex ?: return@BottomPanel
            val currentItem = vm.builder.state.value.activeTrack?.items?.getOrNull(currentIdx)
            if (currentItem is TrackItem.Beat) {
                vm.builder.updateBeatAt(currentIdx) { it.copy(volume = vol) }
            }
        },
        modifier = modifier,
    )
}

// input routing
private fun handleNumpad(n: Int, state: TrackBuilderState, vm: AppViewModel) {
    when (state.inputMode) {
        is InputMode.Denom       -> vm.builder.setDenom(n)
        is InputMode.RepeatCount -> vm.builder.repeatDigit(n)
        is InputMode.Edit        -> vm.builder.editDigit(n)
        is InputMode.Normal      -> vm.builder.enterBeat(n)
    }
}

private fun handleCustom(
    state:         TrackBuilderState,
    vm:            AppViewModel,
    onRepeatCustom:() -> Unit,
    onSetBpmClick: () -> Unit,
    onCustomBeat:  () -> Unit,
    onCustomDenom:  () -> Unit,
) {
    when {
        state.isEditMode -> when (state.cursorItem) {
            is TrackItem.Repeat -> onRepeatCustom()
            is TrackItem.SetBpm -> onSetBpmClick()
            is TrackItem.Beat   -> onCustomBeat()
            else                -> Unit
        }
        state.isRepeatMode -> onRepeatCustom()
        state.isDenomMode  -> onCustomDenom()
        else               -> onCustomBeat()
    }
}

private fun handleBackspace(state: TrackBuilderState, vm: AppViewModel) {
    if (state.cursorIndex != null) vm.builder.deleteSelected()
    else vm.builder.deleteLast()
}

@Composable
private fun AddTrackRow(onAdd: () -> Unit, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp).background(RhythmColors.bg1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onCopy)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("⎘", style = RhythmType.label.copy(color = RhythmColors.beatActiveBorder, fontSize = 14.sp))
            Text("copy track", style = RhythmType.label.copy(color = RhythmColors.textDim, fontSize = 11.sp))
        }
        Box(Modifier.width(0.5.dp).fillMaxHeight().background(RhythmColors.border0))
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onAdd)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("+", style = RhythmType.label.copy(color = RhythmColors.beatActiveBorder, fontSize = 14.sp))
            Text("add track", style = RhythmType.label.copy(color = RhythmColors.textDim, fontSize = 11.sp))
        }
    }
}