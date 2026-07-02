package com.jayc180.rhythmengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jayc180.rhythmengine.builder.*
import com.jayc180.rhythmengine.ui.theme.RhythmColors
import com.jayc180.rhythmengine.ui.theme.RhythmType
import com.jayc180.rhythmengine.ui.theme.surfaceBg1
import com.jayc180.rhythmengine.ui.theme.surfaceBg3

@Composable
fun TrackRow(
    draft:               TrackDraft,
    isActive:            Boolean,
    cursorIndex:         Int?,
    isPlaying:           Boolean,
    playingItemIndex:    Int?,
    globalDefaultSound:  String?,
    beatScrollToBeat:      Boolean = true,
    beatBlockSizeIndex:    Int = 2,
    beatStackedFractions:  Boolean = false,
    onTrackClick:        () -> Unit,
    onItemClick:         (Int) -> Unit,
    onMuteClick:         () -> Unit,
    onSoloClick:         () -> Unit,
    onDeleteClick:       () -> Unit,
    onTrackSoundClick:   () -> Unit,
    modifier:            Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density     = LocalDensity.current

    LaunchedEffect(draft.items.size, cursorIndex, playingItemIndex, isPlaying) {
        val targetIndex = when {
            isPlaying && playingItemIndex != null -> {
                if (beatScrollToBeat) {
                    // walk backward past structural items (], ×N, =bpm, ×p/q) to the beat
                    var idx = playingItemIndex
                    while (idx > 0 && draft.items.getOrNull(idx) !is TrackItem.Beat) idx--
                    if (draft.items.getOrNull(idx) is TrackItem.Beat) idx else playingItemIndex
                } else {
                    playingItemIndex
                }
            }
            !isPlaying && cursorIndex == null && draft.items.isNotEmpty() -> draft.items.lastIndex
            !isPlaying && cursorIndex != null -> cursorIndex
            else -> return@LaunchedEffect
        }
        if (targetIndex < 0 || draft.items.isEmpty()) return@LaunchedEffect
        with(density) {
            val itemWidthPx = 56.dp.toPx()
            val padPx       = 8.dp.toPx()
            val viewportPx  = scrollState.viewportSize.toFloat()
            val targetX     = (padPx + targetIndex * itemWidthPx - viewportPx / 2f)
                .toInt().coerceAtLeast(0)
            scrollState.animateScrollTo(targetX)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(if (isActive) RhythmColors.trackActiveBg else RhythmColors.bg1)
            .then(if (!isPlaying) Modifier.clickable(onClick = onTrackClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // left side track controls
        TrackControls(
            draft              = draft,
            isActive           = isActive,
            isPlaying          = isPlaying,
            globalDefaultSound = globalDefaultSound,
            onMuteClick        = onMuteClick,
            onSoloClick        = onSoloClick,
            onTrackSoundClick  = onTrackSoundClick,
        )

        // beat items
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            val beatWidth   = BEAT_WIDTHS.getOrElse(beatBlockSizeIndex) { 52.dp }
            val beatDynamic = beatBlockSizeIndex < 2
            if (draft.items.isEmpty()) {
                Text("tap a number to add beats",
                    style = RhythmType.label.copy(color = RhythmColors.textSecondary, fontSize = 12.sp))
            } else {
                draft.items.forEachIndexed { index, item ->
                    TrackItemView(
                        item               = item,
                        selected           = isActive && cursorIndex == index,
                        playing            = isPlaying && playingItemIndex == index,
                        onClick            = { if (!isPlaying) onItemClick(index) },
                        beatWidth          = beatWidth,
                        beatDynamic        = beatDynamic,
                        beatStackedFractions = beatStackedFractions,
                    )
                }
            }
        }

        // delete on right
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight()
                .then(if (!isPlaying) Modifier.clickable(onClick = onDeleteClick) else Modifier)
                .border(width = 0.5.dp, color = RhythmColors.border0,
                    shape = RoundedCornerShape(0.dp)),
        ) {
            Text("×", style = RhythmType.toolBtn.copy(fontSize = 24.sp, color = RhythmColors.deleteText))
        }
    }
}

@Composable
private fun TrackControls(
    draft:              TrackDraft,
    isActive:           Boolean,
    isPlaying:          Boolean,
    globalDefaultSound: String?,
    onMuteClick:        () -> Unit,
    onSoloClick:        () -> Unit,
    onTrackSoundClick:  () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // track name
        Text(
            text     = draft.label,
            style    = RhythmType.trackName.copy(
                fontSize = 12.sp,
                color    = if (isActive) RhythmColors.accent else RhythmColors.textSecondary,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))

        // M | S | Default-sound / Changed-sound
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            TrackChip("M", draft.muted,  RhythmColors.muteColor, { if (!isPlaying) onMuteClick() })
            TrackChip("S", draft.soloed, RhythmColors.soloColor, { if (!isPlaying) onSoloClick() })
            TrackChip(
                label = if (draft.defaultSoundId != null) "C" else "D",
                active = draft.defaultSoundId != null,
                color = RhythmColors.accent,
                onClick = { if (!isPlaying) onTrackSoundClick() },
            )
        }
    }
}

@Composable
fun TrackItemView(
    item:                TrackItem,
    selected:            Boolean,
    playing:             Boolean,
    onClick:             () -> Unit,
    beatWidth:           Dp = 52.dp,
    beatDynamic:         Boolean = false,
    beatStackedFractions:Boolean = false,
) {
    when (item) {
        is TrackItem.Beat         -> BeatView(item, selected, playing, onClick, beatWidth, beatDynamic, beatStackedFractions)
        is TrackItem.BracketOpen  -> BracketView("[", selected, onClick)
        is TrackItem.BracketClose -> BracketView("]", selected, onClick)
        is TrackItem.Repeat       -> RepeatView(item, selected, onClick)
        is TrackItem.Modulation   -> ModView(item, selected, onClick)
        is TrackItem.SetBpm       -> SetBpmView(item, selected, onClick)
    }
}

private val BEAT_WIDTHS = listOf(32.dp, 38.dp, 44.dp)
private val ITEM_HEIGHT = 42.dp

@Composable
private fun BeatView(
    beat:    TrackItem.Beat,
    selected:Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    width:   Dp,
    dynamic: Boolean,
    stacked: Boolean = false,
) {
    val (bg, border, textColor) = when {
        playing     -> Triple(RhythmColors.beatPlayingBg, RhythmColors.accentBright, RhythmColors.accentBright)
        selected    -> Triple(RhythmColors.beatSelectedBg, RhythmColors.beatSelectedBorder, RhythmColors.beatSelectedText)
        beat.isRest -> Triple(RhythmColors.beatRestBg, RhythmColors.bg3, RhythmColors.textDim)
        else        -> Triple(RhythmColors.beatActiveBg, RhythmColors.beatActiveBorder, RhythmColors.accent)
    }
    val stackedFontSp = (width.value * 0.33f).coerceIn(11f, 16f)
    val baseFontSp    = (width.value / 3f).coerceIn(8f, 18f)
    val innerPad      = (width.value * 0.08f).coerceIn(3f, 7f).dp

    // Box has no padding — padding lives on the content so it counts toward natural width
    // for dynamic expansion but doesn't widen the block's footprint in the parent Row
    val sizeModifier = if (dynamic) Modifier.widthIn(min = width) else Modifier.width(width)

    Box(contentAlignment = Alignment.Center,
        modifier = sizeModifier.height(ITEM_HEIGHT)
            .clip(RoundedCornerShape(4.dp)).background(bg)
            .border(if (selected || playing) 1.dp else 0.5.dp, border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)) {
        if (stacked && beat.displayDenom != 1) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = innerPad),
            ) {
                Text("${beat.displayNum}",
                    softWrap = false,
                    style = RhythmType.beatValue.copy(
                        color = textColor,
                        fontSize = stackedFontSp.sp,
                        lineHeight = (stackedFontSp + 2f).sp,
                        fontWeight = FontWeight.Bold))
                Text("${beat.displayDenom}",
                    softWrap = false,
                    style = RhythmType.beatValue.copy(
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = (stackedFontSp * 0.85f).sp,
                        lineHeight = (stackedFontSp * 0.85f + 2f).sp,
                        fontWeight = FontWeight.Bold))
            }
        } else {
            val labelFontSp = if (beat.label.length > 5) (baseFontSp * 0.7f).coerceAtLeast(8f) else baseFontSp
            Text(beat.label,
                softWrap = false,
                modifier = Modifier.padding(horizontal = innerPad),
                style = RhythmType.beatValue.copy(
                    color = textColor, fontSize = labelFontSp.sp))
        }
    }
}

@Composable
private fun BracketView(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.width(24.dp).height(ITEM_HEIGHT)
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) RhythmColors.beatSelectedBg else RhythmColors.bracketBg)
            .border(if (selected) 1.dp else 0.5.dp,
                if (selected) RhythmColors.beatSelectedBorder else RhythmColors.bracketBorder, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)) {
        Text(label, style = RhythmType.beatValue.copy(
            fontSize = 18.sp,
            color = if (selected) RhythmColors.beatSelectedText else RhythmColors.bracketText))
    }
}

@Composable
private fun RepeatView(item: TrackItem.Repeat, selected: Boolean, onClick: () -> Unit) {
    val label = if (item.isInfinite) "×∞" else "×${item.count}"
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.height(ITEM_HEIGHT)
            .clip(RoundedCornerShape(3.dp))
            .background(RhythmColors.repeatBg)
            .border(if (selected) 1.dp else 0.5.dp,
                if (selected) RhythmColors.repeatText.copy(alpha = 0.5f) else RhythmColors.border1,
                RoundedCornerShape(3.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp)) {
        Text(label, style = RhythmType.beatValue.copy(
            fontSize = 12.sp,
            color = if (selected) RhythmColors.repeatText else RhythmColors.repeatText.copy(alpha = 0.75f)))
    }
}

@Composable
private fun ModView(item: TrackItem.Modulation, selected: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.height(ITEM_HEIGHT)
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) RhythmColors.cautionBg else RhythmColors.bg0)
            .border(if (selected) 1.dp else 0.5.dp,
                if (selected) RhythmColors.cautionBorder else RhythmColors.border1,
                RoundedCornerShape(3.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp)) {
        Text("×${item.p}/${item.q}", style = RhythmType.beatValue.copy(
            fontSize = 11.sp,
            color = if (selected) RhythmColors.caution else RhythmColors.caution.copy(alpha = 0.75f)))
    }
}

@Composable
private fun SetBpmView(item: TrackItem.SetBpm, selected: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.height(ITEM_HEIGHT)
            .clip(RoundedCornerShape(3.dp))
            .background(RhythmColors.setBpmBg)
            .border(if (selected) 1.dp else 0.5.dp,
                if (selected) RhythmColors.setBpmText.copy(alpha = 0.5f) else RhythmColors.border1,
                RoundedCornerShape(3.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp)) {
        Text("=${item.bpm.toInt()}", style = RhythmType.beatValue.copy(
            fontSize = 11.sp,
            color = if (selected) RhythmColors.setBpmText else RhythmColors.setBpmText.copy(alpha = 0.75f)))
    }
}

@Composable
fun TrackChip(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (active) color.copy(alpha = 0.15f) else surfaceBg3)
            .border(0.5.dp, if (active) color.copy(alpha = 0.4f) else RhythmColors.border1,
                RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(label, style = RhythmType.label.copy(
            fontSize = 11.sp,
            color = if (active) color else RhythmColors.textDim))
    }
}