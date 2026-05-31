#include "UiHelpers.h"

namespace rhythm
{

ChipButton::ChipButton (juce::String label)
    : label_ (std::move (label))
{
    setMouseCursor (juce::MouseCursor::PointingHandCursor);
    setInterceptsMouseClicks (true, true);
}

void ChipButton::resolveColours (juce::Colour& bg, juce::Colour& border, juce::Colour& text) const
{
    switch (state_)
    {
        case StateColor::Normal:
            bg = RhythmColors::bg3();
            border = RhythmColors::border1();
            text = RhythmColors::textPrimary();
            break;
        case StateColor::Editing:
            bg = RhythmColors::cautionBg();
            border = RhythmColors::cautionBorder();
            text = RhythmColors::caution();
            break;
        case StateColor::Dim:
            bg = RhythmColors::bg3();
            border = RhythmColors::border0();
            text = RhythmColors::textDim();
            break;
        case StateColor::Custom:
            bg = bg_; border = border_; text = text_;
            break;
    }
    if (! enabledLook_)
    {
        const float a = 0.35f;
        bg = bg.withAlpha (a);
        border = border.withAlpha (a);
        text = text.withAlpha (a);
    }
}

void ChipButton::paint (juce::Graphics& g)
{
    juce::Colour bg, border, text;
    resolveColours (bg, border, text);

    const auto r = getLocalBounds().toFloat().reduced (0.5f);
    g.setColour (bg);
    g.fillRoundedRectangle (r, corner_);
    g.setColour (border);
    g.drawRoundedRectangle (r, corner_, 1.0f);

    g.setColour (text);
    g.setFont (juce::Font (juce::FontOptions (fontSize_)));
    g.drawText (label_, getLocalBounds(), juce::Justification::centred, false);
}

void ChipButton::mouseDown (const juce::MouseEvent&)
{
    if (enabledLook_ && onClick_) onClick_();
}

// ----- NumKeyButton -----

NumKeyButton::NumKeyButton (juce::String label)
    : label_ (std::move (label))
{
    setMouseCursor (juce::MouseCursor::PointingHandCursor);
}

void NumKeyButton::paint (juce::Graphics& g)
{
    const auto r = getLocalBounds().toFloat().reduced (0.5f);
    g.setColour (bg_);
    g.fillRoundedRectangle (r, 8.0f);
    g.setColour (border_);
    g.drawRoundedRectangle (r, 8.0f, 1.0f);

    g.setColour (text_);
    g.setFont (juce::Font (juce::FontOptions (fontSize_)));
    g.drawText (label_, getLocalBounds(), juce::Justification::centred, false);
}

void NumKeyButton::mouseDown (const juce::MouseEvent&)
{
    if (onClick_) onClick_();
}

// ----- TrackChip -----

TrackChip::TrackChip (juce::String label, juce::Colour activeColour)
    : label_ (std::move (label)), activeColour_ (activeColour)
{
    setMouseCursor (juce::MouseCursor::PointingHandCursor);
}

void TrackChip::paint (juce::Graphics& g)
{
    const auto r = getLocalBounds().toFloat().reduced (0.5f);
    const auto bg = active_ ? activeColour_.withAlpha (0.15f) : RhythmColors::bg3();
    const auto border = active_ ? activeColour_.withAlpha (0.4f) : RhythmColors::border1();
    const auto text = active_ ? activeColour_ : RhythmColors::textDim();

    g.setColour (bg);
    g.fillRoundedRectangle (r, 3.0f);
    g.setColour (border);
    g.drawRoundedRectangle (r, 3.0f, 1.0f);

    g.setColour (text);
    g.setFont (juce::Font (juce::FontOptions (11.0f)));
    g.drawText (label_, getLocalBounds(), juce::Justification::centred, false);
}

void TrackChip::mouseDown (const juce::MouseEvent&)
{
    if (onClick_) onClick_();
}

} // namespace rhythm
