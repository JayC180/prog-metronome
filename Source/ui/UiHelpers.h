#pragma once

#include "RhythmColors.h"
#include <juce_gui_basics/juce_gui_basics.h>
#include <functional>

namespace rhythm
{

// A rounded-rectangle button with bg / border / text-colour overrides.
// Acts like a flat material chip. Used for toolbar buttons, dialog buttons,
// edit-panel "change ›" badges, etc.
class ChipButton : public juce::Component, public juce::SettableTooltipClient
{
public:
    enum class StateColor { Normal, Editing, Dim, Custom };

    ChipButton (juce::String label = {});
    ~ChipButton() override = default;

    void setLabel (juce::String s) { label_ = std::move (s); repaint(); }
    void setStateColor (StateColor s) { state_ = s; repaint(); }

    void setColours (juce::Colour bg, juce::Colour border, juce::Colour text)
    {
        bg_ = bg; border_ = border; text_ = text;
        state_ = StateColor::Custom;
        repaint();
    }

    void setCornerRadius (float r)   { corner_ = r; repaint(); }
    void setFontSize     (float pts) { fontSize_ = pts; repaint(); }
    void setEnabledLook  (bool b)    { enabledLook_ = b; setInterceptsMouseClicks (b, b); repaint(); }

    void setOnClick (std::function<void()> cb) { onClick_ = std::move (cb); }

    void paint (juce::Graphics&) override;
    void mouseDown (const juce::MouseEvent&) override;

private:
    juce::String           label_;
    StateColor             state_       { StateColor::Normal };
    juce::Colour           bg_, border_, text_;
    float                  corner_      { 6.0f };
    float                  fontSize_    { 12.0f };
    bool                   enabledLook_ { true };
    std::function<void()>  onClick_;

    void resolveColours (juce::Colour& bg, juce::Colour& border, juce::Colour& text) const;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (ChipButton)
};

// Big 3x3-grid number key with optional custom colours.
class NumKeyButton : public juce::Component
{
public:
    NumKeyButton (juce::String label);
    ~NumKeyButton() override = default;

    void setLabel (juce::String s) { label_ = std::move (s); repaint(); }
    void setColours (juce::Colour bg, juce::Colour border, juce::Colour text)
    {
        bg_ = bg; border_ = border; text_ = text;
        repaint();
    }
    void setFontSize (float pts) { fontSize_ = pts; repaint(); }
    void setOnClick (std::function<void()> cb) { onClick_ = std::move (cb); }

    void paint (juce::Graphics&) override;
    void mouseDown (const juce::MouseEvent&) override;

private:
    juce::String          label_;
    juce::Colour          bg_     { RhythmColors::bg3() };
    juce::Colour          border_ { RhythmColors::border1() };
    juce::Colour          text_   { RhythmColors::thumbColor() };
    float                 fontSize_ { 18.0f };
    std::function<void()> onClick_;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (NumKeyButton)
};

// Small M/S/D chip for TrackRow controls.
class TrackChip : public juce::Component
{
public:
    TrackChip (juce::String label, juce::Colour activeColour);
    ~TrackChip() override = default;

    void setActive (bool a) { active_ = a; repaint(); }
    bool isActive() const   { return active_; }
    void setOnClick (std::function<void()> cb) { onClick_ = std::move (cb); }

    void paint (juce::Graphics&) override;
    void mouseDown (const juce::MouseEvent&) override;

private:
    juce::String          label_;
    juce::Colour          activeColour_;
    bool                  active_ { false };
    std::function<void()> onClick_;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (TrackChip)
};

} // namespace rhythm
