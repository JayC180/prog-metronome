package com.jayc180.rhythmengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
    globalDefaultSoundId: String?,
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

    LaunchedEffect(draft.items.size, cursorIndex, playingItemIndex, isPlaying, beatBlockSizeIndex) {
        if (draft.items.isEmpty()) return@LaunchedEffect
        with(density) {
            val beatWidthPx    = BEAT_WIDTHS.getOrElse(beatBlockSizeIndex) { 40.dp }.toPx()
            val bracketWidthPx = 24.dp.toPx()
            val structWidthPx  = 40.dp.toPx()  // repeat, mod, setbpm
            val gapPx          = 4.dp.toPx()
            val padPx          = 8.dp.toPx()
            val vpPx           = scrollState.viewportSize.toFloat()
            val scrollX        = scrollState.value.toFloat()

            fun widthOf(i: Int): Float = when (val item = draft.items.getOrNull(i)) {
                is TrackItem.Beat -> if (item.subbeats != null) BEAT_WIDTHS[2].toPx() else beatWidthPx
                is TrackItem.BracketOpen, is TrackItem.BracketClose -> bracketWidthPx
                else -> structWidthPx
            }
            fun leftOf(idx: Int): Float {
                var x = padPx
                for (i in 0 until idx) x += widthOf(i) + gapPx
                return x
            }

            if (isPlaying && playingItemIndex != null) {
                var idx = playingItemIndex
                while (idx > 0 && draft.items.getOrNull(idx) !is TrackItem.Beat) idx--
                val beatIndex = if (draft.items.getOrNull(idx) is TrackItem.Beat) idx else playingItemIndex
                // to leftmost
                val targetX = (leftOf(beatIndex) - padPx).toInt().coerceAtLeast(0)
                if (scrollState.value != targetX) scrollState.animateScrollTo(targetX)
            } else {
                val cursorTarget = cursorIndex ?: draft.items.lastIndex
                if (cursorTarget < 0) return@with
                val itemLeft  = leftOf(cursorTarget)
                val itemRight = itemLeft + widthOf(cursorTarget)
                if (itemLeft >= scrollX && itemRight <= scrollX + vpPx) return@with
                val targetX = if (itemLeft < scrollX)
                    (itemLeft - padPx).toInt().coerceAtLeast(0)
                else
                    (itemRight - vpPx + padPx).toInt().coerceAtLeast(0)
                scrollState.animateScrollTo(targetX)
            }
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
            draft                = draft,
            isActive             = isActive,
            isPlaying            = isPlaying,
            globalDefaultSoundId = globalDefaultSoundId,
            onMuteClick          = onMuteClick,
            onSoloClick          = onSoloClick,
            onTrackSoundClick    = onTrackSoundClick,
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
    draft:                TrackDraft,
    isActive:             Boolean,
    isPlaying:            Boolean,
    globalDefaultSoundId: String?,
    onMuteClick:          () -> Unit,
    onSoloClick:          () -> Unit,
    onTrackSoundClick:    () -> Unit,
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
                label  = "T",
                active = draft.defaultSoundId != null && draft.defaultSoundId != globalDefaultSoundId,
                color  = RhythmColors.accent,
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

private const val SUBBEAT_DOT_MAX = 12
private const val SUBBEAT_DOTS_PER_ROW = 4

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
    // use large block when subbeat active
    val effectiveWidth = if (beat.subbeats != null) BEAT_WIDTHS[2] else width
    val hasDots = beat.subbeats != null

    val (bg, border, textColor) = when {
        playing     -> Triple(RhythmColors.beatPlayingBg, RhythmColors.accentBright, RhythmColors.accentBright)
        selected    -> Triple(RhythmColors.beatSelectedBg, RhythmColors.beatSelectedBorder, RhythmColors.beatSelectedText)
        beat.isRest -> Triple(RhythmColors.beatRestBg, RhythmColors.bg3, RhythmColors.textDim)
        else        -> Triple(RhythmColors.beatActiveBg, RhythmColors.beatActiveBorder, RhythmColors.accent)
    }
    val stackedFontSp = (effectiveWidth.value * 0.33f).coerceIn(11f, 16f)
    val baseFontSp    = (effectiveWidth.value / 3f).coerceIn(8f, 18f)
    val innerPad      = (effectiveWidth.value * 0.08f).coerceIn(3f, 7f).dp

    val sizeModifier = if (dynamic && beat.subbeats == null) Modifier.widthIn(min = effectiveWidth)
                       else Modifier.width(effectiveWidth)

    Box(contentAlignment = Alignment.Center,
        modifier = sizeModifier.height(ITEM_HEIGHT)
            .clip(RoundedCornerShape(4.dp)).background(bg)
            .border(if (selected || playing) 1.dp else 0.5.dp, border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)) {
        if (stacked && beat.displayDenom != 1) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = innerPad)
                    .then(if (hasDots) Modifier.padding(bottom = 10.dp) else Modifier),
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
                modifier = Modifier.padding(horizontal = innerPad)
                    .then(if (hasDots) Modifier.padding(bottom = 10.dp) else Modifier),
                style = RhythmType.beatValue.copy(
                    color = textColor, fontSize = labelFontSp.sp))
        }

        if (hasDots) {
            SubbeatDots(
                subbeats = beat.subbeats!!,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
        }
    }
}

@Composable
private fun SubbeatDots(subbeats: List<SubbeatState>, modifier: Modifier = Modifier) {
    val count = subbeats.size.coerceAtMost(SUBBEAT_DOT_MAX)
    val rows  = (count + SUBBEAT_DOTS_PER_ROW - 1) / SUBBEAT_DOTS_PER_ROW
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val from = row * SUBBEAT_DOTS_PER_ROW
                val to   = minOf(from + SUBBEAT_DOTS_PER_ROW, count)
                for (idx in from until to) {
                    val state = subbeats.getOrElse(idx) { SubbeatState.OFF }
                    Box(modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(when (state) {
                            SubbeatState.BEAT    -> RhythmColors.accentBright
                            SubbeatState.SUBBEAT -> RhythmColors.caution
                            SubbeatState.OFF     -> RhythmColors.border1
                        }))
                }
            }
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
        Text("=${formatBpm(item.bpm)}", style = RhythmType.beatValue.copy(
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