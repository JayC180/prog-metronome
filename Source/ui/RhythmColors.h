#pragma once

#include "ThemeConfig.h"

namespace rhythm
{

// Active-theme singleton. Widgets read `RhythmColors::active()` to look up
// the colour they need. Calling setActive() rebinds the theme — listeners
// repaint themselves on the next paint cycle.
class RhythmColors
{
public:
    static ThemeConfig& active()
    {
        static ThemeConfig instance = BuiltInThemes::obsidianDark();
        return instance;
    }

    static void setActive (ThemeConfig cfg) { active() = std::move (cfg); }

    // Derived colours that the Kotlin code declares as helper computations.
    static juce::Colour accentBg()        { return active().accent.withAlpha (0.15f); }
    static juce::Colour accentBorder()    { return active().accent.withAlpha (0.45f); }
    static juce::Colour cautionBg()       { return active().caution.withAlpha (0.15f); }
    static juce::Colour cautionBorder()   { return active().caution.withAlpha (0.45f); }
    static juce::Colour dangerBg()        { return active().danger.withAlpha (0.18f); }
    static juce::Colour dangerBorder()    { return active().danger.withAlpha (0.45f); }
    static juce::Colour infiniteBg()      { return active().repeatText.withAlpha (0.15f); }
    static juce::Colour infiniteBorder()  { return active().repeatText.withAlpha (0.5f); }
    static juce::Colour infiniteText()    { return active().repeatText; }

    static juce::Colour bracketBg()       { return active().bg3; }
    static juce::Colour bracketBorder()   { return active().border1; }
    static juce::Colour repeatBg()        { return active().bg3.withAlpha (0.6f); }
    static juce::Colour setBpmBg()        { return active().setBpmText.withAlpha (0.10f); }
    static juce::Colour beatRestBg()      { return active().bg2; }
    static juce::Colour beatPlayingBg()   { return active().accentBright.withAlpha (0.18f); }

    static juce::Colour border2()         { return active().border1.brighter (0.2f); }

    // top-level shortcuts to spare typing inside paint() blocks
    static juce::Colour bg0()              { return active().bg0; }
    static juce::Colour bg1()              { return active().bg1; }
    static juce::Colour bg2()              { return active().bg2; }
    static juce::Colour bg3()              { return active().bg3; }
    static juce::Colour border0()          { return active().border0; }
    static juce::Colour border1()          { return active().border1; }
    static juce::Colour textPrimary()      { return active().textPrimary; }
    static juce::Colour textSecondary()    { return active().textSecondary; }
    static juce::Colour textMuted()        { return active().textMuted; }
    static juce::Colour textDim()          { return active().textDim; }
    static juce::Colour accent()           { return active().accent; }
    static juce::Colour accentBright()     { return active().accentBright; }
    static juce::Colour caution()          { return active().caution; }
    static juce::Colour danger()           { return active().danger; }
    static juce::Colour muteColor()        { return active().muteColor; }
    static juce::Colour soloColor()        { return active().soloColor; }
    static juce::Colour beatActiveBg()     { return active().beatActiveBg; }
    static juce::Colour beatActiveBorder() { return active().beatActiveBorder; }
    static juce::Colour beatSelectedBg()   { return active().beatSelectedBg; }
    static juce::Colour beatSelectedBorder(){ return active().beatSelectedBorder; }
    static juce::Colour beatSelectedText() { return active().beatSelectedText; }
    static juce::Colour bracketText()      { return active().bracketText; }
    static juce::Colour repeatText()       { return active().repeatText; }
    static juce::Colour setBpmText()       { return active().setBpmText; }
    static juce::Colour trackActiveBg()    { return active().trackActiveBg; }
    static juce::Colour deleteText()       { return active().deleteText; }
    static juce::Colour thumbColor()       { return active().thumbColor; }
};

} // namespace rhythm
