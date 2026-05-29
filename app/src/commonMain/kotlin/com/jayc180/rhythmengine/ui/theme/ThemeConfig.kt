package com.jayc180.rhythmengine.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Refactored by AI cuz i don't wanna do color stuff
 * Allow json imports so there's some ricing capabilities at least
 *
 * Derivation rules (uniform across all themes):
 *   *Bg     = base color at 12% alpha   (tinted background)
 *   *Border = base color at 28% alpha   (visible border)
 *   bracket/repeat/setBpm Bg/Border reuse bg0 and border0 (recessed item style)
 *   toggle and infinite reuse the accent family
 */
@Immutable
data class ThemeConfig(
    // prob want ID here instead to avoid imported theme's name colliding?
    val name: String,

    // ── Surfaces ──────────────────────────────────────────────────────────────
    // bg0: numpad bg, beat-rest background, recessed item backgrounds
    val bg0: Color,
    // bg1: track row background
    val bg1: Color,
    // bg2: dialog backgrounds
    val bg2: Color,
    // bg3: button/chip backgrounds
    val bg3: Color,

    // ── Borders ───────────────────────────────────────────────────────────────
    // border0: HorizontalDivider, very subtle separators
    val border0: Color,
    // border1: standard UI borders everywhere
    val border1: Color,

    // ── Text ──────────────────────────────────────────────────────────────────
    // textPrimary: dialog titles, BPM value, main content
    val textPrimary: Color,
    // textSecondary: subtitles, sound row labels
    val textSecondary: Color,
    // textMuted: button labels, TopBar "bpm" sub-label
    val textMuted: Color,
    // textDim: hint text, watermarks
    val textDim: Color,

    // ── Accent ────────────────────────────────────────────────────────────────
    // accent: active track name, active beat text, play-button icon, volume slider,
    //         toggle checked, OutlinedTextField focus, BPM pill text
    val accent: Color,
    // accentBright: playing-beat border+text, play-button triangle, ×∞ button text
    val accentBright: Color,

    // ── State colors ──────────────────────────────────────────────────────────
    // caution: BPM pill during playback, edit-mode indicators, mm item text
    val caution: Color,
    // danger: stop-button, error text
    val danger: Color,

    // ── Semantic one-offs ─────────────────────────────────────────────────────
    val muteColor: Color,   // M chip when muted
    val soloColor: Color,   // S chip when soloed

    // ── Beat item colors ──────────────────────────────────────────────────────
    val beatActiveBg: Color,       // normal (non-rest, non-selected) beat
    val beatActiveBorder: Color,
    val beatSelectedBg: Color,     // beat under cursor
    val beatSelectedBorder: Color,
    val beatSelectedText: Color,

    // ── Non-beat track item text colors ───────────────────────────────────────
    // Drive both selected (full) and unselected (55% alpha) states in TrackRow.
    val bracketText: Color,   // [ ] glyph
    val repeatText: Color,    // ×N text
    val setBpmText: Color,    // =bpm text

    // ── Miscellaneous ─────────────────────────────────────────────────────────
    val trackActiveBg: Color,   // entire TrackRow bg when active
    val deleteText: Color,      // "×" delete button glyph
    val thumbColor: Color,      // toggle thumb and VolumeSlider thumb

    val defaultPanelAlpha: Float = 1.0f,
) {
    // ── Derived colors ────────────────────────────────────────────────────────
    // Computed from seeds above — no per-theme tuning needed.

    val accentBg:     Color get() = accent.copy(alpha = 0.12f)
    val accentBorder: Color get() = accent.copy(alpha = 0.28f)

    val cautionBg:     Color get() = caution.copy(alpha = 0.12f)
    val cautionBorder: Color get() = caution.copy(alpha = 0.28f)

    val dangerBg:     Color get() = danger.copy(alpha = 0.12f)
    val dangerBorder: Color get() = danger.copy(alpha = 0.28f)

    val border2: Color get() = border1   // kept for API compat; same as border1

    val beatPlayingBg: Color get() = accent.copy(alpha = 0.16f)
    val beatRestBg:    Color get() = bg0

    val bracketBg:    Color get() = bg0
    val bracketBorder:Color get() = border0
    val repeatBg:     Color get() = bg0
    val repeatBorder: Color get() = border0
    val setBpmBg:     Color get() = bg0
    val setBpmBorder: Color get() = border0

    val toggleCheckedBg:     Color get() = accentBg
    val toggleCheckedBorder: Color get() = accentBorder

    val infiniteBg:    Color get() = bg0
    val infiniteBorder:Color get() = accentBorder
    val infiniteText:  Color get() = accentBright
}

// ── CompositionLocal ──────────────────────────────────────────────────────────

val LocalTheme = staticCompositionLocalOf<ThemeConfig> {
    error("No ThemeConfig provided — wrap with RhythmTheme")
}

val RhythmColors: ThemeConfig
    @Composable get() = LocalTheme.current

// ── JSON parser ───────────────────────────────────────────────────────────────
// Custom JSON themes supply only the 27 explicit color fields.
// Old derived-field keys (accentBg, border2, etc.) are silently ignored.

private val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }

fun parseThemeJson(jsonString: String): ThemeConfig {
    val obj: JsonObject = lenientJson.parseToJsonElement(jsonString).jsonObject

    fun str(key: String, fallback: String = "#000000"): String =
        obj[key]?.jsonPrimitive?.content ?: fallback

    fun color(key: String, fallback: String = "#000000"): Color {
        val raw  = str(key, fallback).trimStart('#')
        val argb = when (raw.length) {
            6    -> "FF$raw"
            8    -> raw
            else -> "FF000000"
        }
        return Color(argb.toLong(16).toInt())
    }

    fun float(key: String, fallback: Double = 1.0): Float =
        (obj[key]?.jsonPrimitive?.doubleOrNull ?: fallback).toFloat().coerceIn(0f, 1f)

    return ThemeConfig(
        name               = str("name", "Custom"),
        bg0                = color("bg0",               "0A0A0A"),
        bg1                = color("bg1",               "111111"),
        bg2                = color("bg2",               "161616"),
        bg3                = color("bg3",               "1A1A1A"),
        border0            = color("border0",           "181818"),
        border1            = color("border1",           "222222"),
        textPrimary        = color("textPrimary",       "E0E0E0"),
        textSecondary      = color("textSecondary",     "888888"),
        textMuted          = color("textMuted",         "444444"),
        textDim            = color("textDim",           "2A2A2A"),
        accent             = color("accent",            "4DAA7A"),
        accentBright       = color("accentBright",      "7EDE9A"),
        caution            = color("caution",           "B0A030"),
        danger             = color("danger",            "DE7A7A"),
        muteColor          = color("muteColor",         "E0843A"),
        soloColor          = color("soloColor",         "4DA6D4"),
        beatActiveBg       = color("beatActiveBg",      "1A2822"),
        beatActiveBorder   = color("beatActiveBorder",  "2A4A3A"),
        beatSelectedBg     = color("beatSelectedBg",    "1A2535"),
        beatSelectedBorder = color("beatSelectedBorder","4A7ABE"),
        beatSelectedText   = color("beatSelectedText",  "8AB4EE"),
        bracketText        = color("bracketText",       "254525"),
        repeatText         = color("repeatText",        "2A2A6A"),
        setBpmText         = color("setBpmText",        "3A3A10"),
        trackActiveBg      = color("trackActiveBg",     "0F1A0F"),
        deleteText         = color("deleteText",        "333333"),
        thumbColor         = color("thumbColor",        "CCCCCC"),
        defaultPanelAlpha  = float("defaultPanelAlpha", 1.0),
    )
}
