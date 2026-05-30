#pragma once

#include <juce_graphics/juce_graphics.h>
#include <string>
#include <vector>

namespace rhythm
{

// Plain data — mirrors ui/theme/ThemeConfig.kt. One theme = one set of
// concrete colors. Theming is "swap the active ThemeConfig"; widgets
// read RhythmColors which is just a getter onto the active config.
struct ThemeConfig
{
    std::string    name;

    // surfaces
    juce::Colour   bg0;
    juce::Colour   bg1;
    juce::Colour   bg2;
    juce::Colour   bg3;
    juce::Colour   border0;
    juce::Colour   border1;

    // text
    juce::Colour   textPrimary;
    juce::Colour   textSecondary;
    juce::Colour   textMuted;
    juce::Colour   textDim;

    // semantic
    juce::Colour   accent;
    juce::Colour   accentBright;
    juce::Colour   caution;
    juce::Colour   danger;
    juce::Colour   muteColor;
    juce::Colour   soloColor;

    // beat states
    juce::Colour   beatActiveBg;
    juce::Colour   beatActiveBorder;
    juce::Colour   beatSelectedBg;
    juce::Colour   beatSelectedBorder;
    juce::Colour   beatSelectedText;

    // glyph-tinted text
    juce::Colour   bracketText;
    juce::Colour   repeatText;
    juce::Colour   setBpmText;

    juce::Colour   trackActiveBg;
    juce::Colour   deleteText;
    juce::Colour   thumbColor;

    float          defaultPanelAlpha { 1.0f };
};

namespace BuiltInThemes
{
    namespace detail
    {
        inline juce::Colour hex (const char* h)
        {
            return juce::Colour::fromString (juce::String ("FF") + h);
        }
    }

    inline ThemeConfig obsidianDark()
    {
        using detail::hex;
        ThemeConfig t;
        t.name              = "Obsidian";
        t.bg0               = hex ("0A0A0A");
        t.bg1               = hex ("111111");
        t.bg2               = hex ("161616");
        t.bg3               = hex ("1A1A1A");
        t.border0           = hex ("181818");
        t.border1           = hex ("222222");
        t.textPrimary       = hex ("E0E0E0");
        t.textSecondary     = hex ("888888");
        t.textMuted         = hex ("444444");
        t.textDim           = hex ("2A2A2A");
        t.accent            = hex ("4DAA7A");
        t.accentBright      = hex ("7EDE9A");
        t.caution           = hex ("B0A030");
        t.danger            = hex ("DE7A7A");
        t.muteColor         = hex ("E0843A");
        t.soloColor         = hex ("4DA6D4");
        t.beatActiveBg      = hex ("1A2822");
        t.beatActiveBorder  = hex ("2A4A3A");
        t.beatSelectedBg    = hex ("1A2535");
        t.beatSelectedBorder= hex ("4A7ABE");
        t.beatSelectedText  = hex ("8AB4EE");
        t.bracketText       = hex ("3D6A3D");
        t.repeatText        = hex ("5A5AAA");
        t.setBpmText        = hex ("7A7A20");
        t.trackActiveBg     = hex ("0F1A0F");
        t.deleteText        = hex ("333333");
        t.thumbColor        = hex ("CCCCCC");
        t.defaultPanelAlpha = 1.0f;
        return t;
    }

    inline ThemeConfig catppuccinMocha()
    {
        using detail::hex;
        ThemeConfig t;
        t.name              = "Mocha";
        t.bg0               = hex ("11111b");
        t.bg1               = hex ("181825");
        t.bg2               = hex ("1e1e2e");
        t.bg3               = hex ("313244");
        t.border0           = hex ("313244");
        t.border1           = hex ("45475a");
        t.textPrimary       = hex ("cdd6f4");
        t.textSecondary     = hex ("bac2de");
        t.textMuted         = hex ("a6adc8");
        t.textDim           = hex ("585b70");
        t.accent            = hex ("cba6f7");
        t.accentBright      = hex ("f5c2e7");
        t.caution           = hex ("fab387");
        t.danger            = hex ("f38ba8");
        t.muteColor         = hex ("eba0ac");
        t.soloColor         = hex ("89dceb");
        t.beatActiveBg      = hex ("313244");
        t.beatActiveBorder  = hex ("45475a");
        t.beatSelectedBg    = hex ("1e253d");
        t.beatSelectedBorder= hex ("89b4fa");
        t.beatSelectedText  = hex ("89b4fa");
        t.bracketText       = hex ("7f849c");
        t.repeatText        = hex ("b4befe");
        t.setBpmText        = hex ("f9e2af");
        t.trackActiveBg     = hex ("66547b");
        t.deleteText        = hex ("6c7086");
        t.thumbColor        = hex ("cdd6f4");
        t.defaultPanelAlpha = 0.92f;
        return t;
    }

    inline ThemeConfig catppuccinLatte()
    {
        using detail::hex;
        ThemeConfig t;
        t.name              = "Latte";
        t.bg0               = hex ("dce0e8");
        t.bg1               = hex ("e6e9ef");
        t.bg2               = hex ("eff1f5");
        t.bg3               = hex ("ccd0da");
        t.border0           = hex ("bcc0cc");
        t.border1           = hex ("9ca0b0");
        t.textPrimary       = hex ("4c4f69");
        t.textSecondary     = hex ("6c6f85");
        t.textMuted         = hex ("9ca0b0");
        t.textDim           = hex ("acb0be");
        t.accent            = hex ("8839ef");
        t.accentBright      = hex ("7287fd");
        t.caution           = hex ("df8e1d");
        t.danger            = hex ("d20f39");
        t.muteColor         = hex ("fe640b");
        t.soloColor         = hex ("04a5e5");
        t.beatActiveBg      = hex ("eff1f5");
        t.beatActiveBorder  = hex ("9ca0b0");
        t.beatSelectedBg    = hex ("ddd8f5");
        t.beatSelectedBorder= hex ("8839ef");
        t.beatSelectedText  = hex ("4c4f69");
        t.bracketText       = hex ("6c6f85");
        t.repeatText        = hex ("7a5010");
        t.setBpmText        = hex ("5a3898");
        t.trackActiveBg     = hex ("e8e5f8");
        t.deleteText        = hex ("9ca0b0");
        t.thumbColor        = hex ("4c4f69");
        t.defaultPanelAlpha = 1.0f;
        return t;
    }

    inline ThemeConfig dracula()
    {
        using detail::hex;
        ThemeConfig t;
        t.name              = "Dracula";
        t.bg0               = hex ("13141e");
        t.bg1               = hex ("1e1f2e");
        t.bg2               = hex ("282a36");
        t.bg3               = hex ("44475a");
        t.border0           = hex ("1e1f2e");
        t.border1           = hex ("44475a");
        t.textPrimary       = hex ("f8f8f2");
        t.textSecondary     = hex ("6272a4");
        t.textMuted         = hex ("8898c8");
        t.textDim           = hex ("8e93b8");
        t.accent            = hex ("e06ab8");
        t.accentBright      = hex ("f598d4");
        t.caution           = hex ("f1fa8c");
        t.danger            = hex ("ff5555");
        t.muteColor         = hex ("ffb86c");
        t.soloColor         = hex ("8be9fd");
        t.beatActiveBg      = hex ("2c2240");
        t.beatActiveBorder  = hex ("60305a");
        t.beatSelectedBg    = hex ("362050");
        t.beatSelectedBorder= hex ("ff79c6");
        t.beatSelectedText  = hex ("f8f8f2");
        t.bracketText       = hex ("6272a4");
        t.repeatText        = hex ("c8c860");
        t.setBpmText        = hex ("a08fc8");
        t.trackActiveBg     = hex ("26183a");
        t.deleteText        = hex ("6272a4");
        t.thumbColor        = hex ("f8f8f2");
        t.defaultPanelAlpha = 0.88f;
        return t;
    }

    inline ThemeConfig solarizedLight()
    {
        using detail::hex;
        ThemeConfig t;
        t.name              = "Solarized Light";
        t.bg0               = hex ("ccc6af");
        t.bg1               = hex ("eee8d5");
        t.bg2               = hex ("fdf6e3");
        t.bg3               = hex ("d8d2bc");
        t.border0           = hex ("d5cfba");
        t.border1           = hex ("b8b3a0");
        t.textPrimary       = hex ("073642");
        t.textSecondary     = hex ("586e75");
        t.textMuted         = hex ("657b83");
        t.textDim           = hex ("93a1a1");
        t.accent            = hex ("268bd2");
        t.accentBright      = hex ("2aa198");
        t.caution           = hex ("b58900");
        t.danger            = hex ("dc322f");
        t.muteColor         = hex ("cb4b16");
        t.soloColor         = hex ("6c71c4");
        t.beatActiveBg      = hex ("fdf6e3");
        t.beatActiveBorder  = hex ("c0baa8");
        t.beatSelectedBg    = hex ("d5e5f5");
        t.beatSelectedBorder= hex ("268bd2");
        t.beatSelectedText  = hex ("073642");
        t.bracketText       = hex ("586e75");
        t.repeatText        = hex ("8a6900");
        t.setBpmText        = hex ("4a5898");
        t.trackActiveBg     = hex ("e5eaf5");
        t.deleteText        = hex ("93a1a1");
        t.thumbColor        = hex ("073642");
        t.defaultPanelAlpha = 1.0f;
        return t;
    }

    inline std::vector<ThemeConfig> all()
    {
        return { catppuccinMocha(), obsidianDark(), dracula(), catppuccinLatte(), solarizedLight() };
    }
} // namespace BuiltInThemes

} // namespace rhythm
