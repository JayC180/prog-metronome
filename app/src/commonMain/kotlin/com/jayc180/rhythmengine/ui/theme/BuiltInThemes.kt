package com.jayc180.rhythmengine.ui.theme

import androidx.compose.ui.graphics.Color

private fun hex(value: String): Color {
    val h = value.trimStart('#')
    val argb = if (h.length == 6) "FF$h" else h
    return Color(argb.toLong(16).toInt())
}

/**
 * general principle: each has one dominant surface tone and onestrong accent color
 * that contrasts clearly against it. Secondary colors (caution/danger/mute/solo)
 * are subordinate; they signal state without competing with the main contrast
 */
object BuiltInThemes {
    // first default theme; reference for other themes
    val ObsidianDark = ThemeConfig(
        name              = "Obsidian",
        bg0               = hex("0A0A0A"),
        bg1               = hex("111111"),
        bg2               = hex("161616"),
        bg3               = hex("1A1A1A"),
        border0           = hex("181818"),
        border1           = hex("222222"),
        textPrimary       = hex("E0E0E0"),
        textSecondary     = hex("888888"),
        textMuted         = hex("444444"),
        textDim           = hex("2A2A2A"),
        accent            = hex("4DAA7A"),
        accentBright      = hex("7EDE9A"), // bright green for playing state
        caution           = hex("B0A030"), // edit/tempo
        danger            = hex("DE7A7A"), // stop/delete
        muteColor         = hex("E0843A"),
        soloColor         = hex("4DA6D4"),
        beatActiveBg      = hex("1A2822"),
        beatActiveBorder  = hex("2A4A3A"),
        beatSelectedBg    = hex("1A2535"),
        beatSelectedBorder= hex("4A7ABE"),
        beatSelectedText  = hex("8AB4EE"),
        bracketText       = hex("3D6A3D"),
        repeatText        = hex("5A5AAA"),
        setBpmText        = hex("7A7A20"),
        trackActiveBg     = hex("0F1A0F"),
        deleteText        = hex("333333"),
        thumbColor        = hex("CCCCCC"),
        defaultPanelAlpha = 1.0f,
    )

    // catppuccin mocha; dark purple blue with mauve as accent
    // blue for cursor selection; sky for solo
    val CatppuccinMocha = ThemeConfig(
        name              = "Mocha",
        bg0               = hex("11111b"), // Crust
        bg1               = hex("181825"), // Mantle
        bg2               = hex("1e1e2e"), // Base — dialogs
        bg3               = hex("313244"), // Surface0
        border0           = hex("313244"), // Surface0
        border1           = hex("45475a"), // Surface1
        textPrimary       = hex("cdd6f4"), // Text
        textSecondary     = hex("bac2de"), // Subtext1
        textMuted         = hex("a6adc8"), // Subtext0
        textDim           = hex("585b70"), // Surface2
        accent            = hex("cba6f7"), // Mauve
        accentBright      = hex("f5c2e7"), // Flamingo — playing state
        caution           = hex("fab387"), // Peach — edit/tempo
        danger            = hex("f38ba8"), // Red
        muteColor         = hex("eba0ac"), // Maroon
        soloColor         = hex("89dceb"), // Sky
        beatActiveBg      = hex("313244"), // Surface0
        beatActiveBorder  = hex("45475a"), // Surface1
        beatSelectedBg    = hex("1e253d"), // Blue - cursor
        beatSelectedBorder= hex("89b4fa"),
        beatSelectedText  = hex("89b4fa"),
        bracketText       = hex("7f849c"),
        repeatText        = hex("b4befe"),
        setBpmText        = hex("f9e2af"), // Yellow
        trackActiveBg     = hex("66547b"), // Darker mauve
        deleteText        = hex("6c7086"), // Overlay0
        thumbColor        = hex("cdd6f4"), // Text
        defaultPanelAlpha = 0.92f,
    )

    // catppuccin latte; mauve/purple accent
    // AI DO THIS I DONT WANNA MANUALLY DO COLORS
    val CatppuccinLatte = ThemeConfig(
        name              = "Latte",
        bg0               = hex("dce0e8"),   // Crust — slightly darker, for numpad bg
        bg1               = hex("e6e9ef"),   // Mantle — track rows
        bg2               = hex("eff1f5"),   // Base — dialogs (lightest = most "elevated")
        bg3               = hex("ccd0da"),   // Surface0 — button chips (darker than tracks)
        border0           = hex("bcc0cc"),   // Surface1 — subtle dividers
        border1           = hex("9ca0b0"),   // Overlay0 — standard borders
        textPrimary       = hex("4c4f69"),   // Text — dark purple-gray
        textSecondary     = hex("6c6f85"),   // Subtext0
        textMuted         = hex("9ca0b0"),   // Overlay0
        textDim           = hex("acb0be"),   // between Overlay0 and Surface1
        accent            = hex("8839ef"),   // Mauve — rich purple, max contrast on light
        accentBright      = hex("7287fd"),   // Lavender — playing state highlight
        caution           = hex("df8e1d"),   // Yellow
        danger            = hex("d20f39"),   // Red — saturated for legibility on light bg
        muteColor         = hex("fe640b"),   // Peach
        soloColor         = hex("04a5e5"),   // Sky
        beatActiveBg      = hex("eff1f5"),   // Base — lightest surface, beats pop above gray track
        beatActiveBorder  = hex("9ca0b0"),   // Overlay0 — clear border on light surface
        beatSelectedBg    = hex("ddd8f5"),   // mauve-tinted for cursor
        beatSelectedBorder= hex("8839ef"),   // Mauve — full accent
        beatSelectedText  = hex("4c4f69"),   // Text — dark for readability on light bg
        bracketText       = hex("6c6f85"),   // Subtext0 — readable secondary
        repeatText        = hex("7a5010"),   // muted peach-amber — readable on light bg
        setBpmText        = hex("5a3898"),   // muted mauve
        trackActiveBg     = hex("e8e5f8"),   // subtle lavender tint on active track row
        deleteText        = hex("9ca0b0"),   // Overlay0
        thumbColor        = hex("4c4f69"),   // Text — dark thumb visible on light slider
        defaultPanelAlpha = 1.0f,
    )

    // ── 4. Dracula ────────────────────────────────────────────────────────────
    // The iconic dark theme. Deep near-black purple bg, electric PINK accent.
    // Bright foreground text for maximum contrast — high-energy, no ambiguity.
    // Cyan solo, orange mute, yellow caution all coexist without clashing because
    // they live in different areas and pink is clearly the primary signal.
    val Dracula = ThemeConfig(
        name              = "Dracula",
        bg0               = hex("13141e"),   // darker than Background
        bg1               = hex("1e1f2e"),   // track rows — darker than original for contrast
        bg2               = hex("282a36"),   // Dracula Background — dialogs / top bar
        bg3               = hex("44475a"),   // Current Line — buttons/chips
        border0           = hex("1e1f2e"),   // matches bg1 — very subtle dividers
        border1           = hex("44475a"),   // Current Line
        textPrimary       = hex("f8f8f2"),   // Foreground — very bright, high contrast
        textSecondary     = hex("6272a4"),   // Comment — purple-gray
        textMuted         = hex("525770"),   // between Comment and Current Line
        textDim           = hex("6e7194"),   // visible on bg3 chips — raised from bg3 level
        accent            = hex("e06ab8"),   // Pink — Dracula signature, toned to not overpower UI chrome
        accentBright      = hex("f598d4"),   // lighter pink — playing state highlight
        caution           = hex("f1fa8c"),   // Yellow
        danger            = hex("ff5555"),   // Red
        muteColor         = hex("ffb86c"),   // Orange
        soloColor         = hex("8be9fd"),   // Cyan — classic Dracula complement
        beatActiveBg      = hex("2c2240"),   // pink-purple tinted, above bg1
        beatActiveBorder  = hex("60305a"),   // muted pink border
        beatSelectedBg    = hex("362050"),   // deeper mauve-pink
        beatSelectedBorder= hex("ff79c6"),   // Pink — full accent
        beatSelectedText  = hex("f8f8f2"),   // Foreground — max contrast
        bracketText       = hex("6272a4"),   // Comment
        repeatText        = hex("787820"),   // muted yellow
        setBpmText        = hex("7060a0"),   // muted purple
        trackActiveBg     = hex("26183a"),   // very dark pink-purple tint
        deleteText        = hex("6272a4"),   // Comment
        thumbColor        = hex("f8f8f2"),   // Foreground
        defaultPanelAlpha = 0.88f,
    )

    // ── 5. Solarized Light ────────────────────────────────────────────────────
    // The famous cream theme. Warm base3 (#fdf6e3) cream as the primary surface
    // with base2 (#eee8d5) for track rows — beats appear as lighter cream cards
    // elevated above the slightly-tan track. Blue accent, same as the dark variant,
    // reads cleanly against cream. Dark teal-gray text for precise contrast.
    val SolarizedLight = ThemeConfig(
        name              = "Solarized Light",
        bg0               = hex("ccc6af"),   // darker than base2 — numpad bg, recessed items
        bg1               = hex("eee8d5"),   // base2 — track rows
        bg2               = hex("fdf6e3"),   // base3 (cream) — dialogs, most elevated
        bg3               = hex("d8d2bc"),   // button chips — slightly darker, recessed feel
        border0           = hex("d5cfba"),   // very subtle separator
        border1           = hex("b8b3a0"),   // standard border
        textPrimary       = hex("073642"),   // base02 — dark teal, high contrast on cream
        textSecondary     = hex("586e75"),   // base01
        textMuted         = hex("657b83"),   // base00
        textDim           = hex("93a1a1"),   // base1 — lightest readable text
        accent            = hex("268bd2"),   // Blue — Solarized's primary interactive color
        accentBright      = hex("2aa198"),   // Cyan — playing state highlight, distinct hue
        caution           = hex("b58900"),   // Yellow
        danger            = hex("dc322f"),   // Red
        muteColor         = hex("cb4b16"),   // Orange
        soloColor         = hex("6c71c4"),   // Violet — cool complement to blue
        beatActiveBg      = hex("fdf6e3"),   // base3 — beats as elevated cream cards above tan track
        beatActiveBorder  = hex("c0baa8"),   // subtle warm border on cream beats
        beatSelectedBg    = hex("d5e5f5"),   // blue-tinted for cursor
        beatSelectedBorder= hex("268bd2"),   // Blue — accent
        beatSelectedText  = hex("073642"),   // dark — readable on light selected bg
        bracketText       = hex("586e75"),   // base01 — secondary readable text
        repeatText        = hex("8a6900"),   // muted amber — readable on cream
        setBpmText        = hex("4a5898"),   // muted blue-violet — readable on cream
        trackActiveBg     = hex("e5eaf5"),   // subtle blue tint on active track row
        deleteText        = hex("93a1a1"),   // base1 — visible but unobtrusive
        thumbColor        = hex("073642"),   // dark thumb visible on light slider
        defaultPanelAlpha = 1.0f,
    )

    val all: List<ThemeConfig> = listOf(
        ObsidianDark,
        CatppuccinMocha,
        CatppuccinLatte,
        Dracula,
        SolarizedLight,
    )
}
