#include "RhythmLookAndFeel.h"

namespace rhythm
{

RhythmLookAndFeel::RhythmLookAndFeel()
{
    refreshDefaults();
}

void RhythmLookAndFeel::refreshDefaults()
{
    setColour (juce::ResizableWindow::backgroundColourId, RhythmColors::bg0());
    setColour (juce::Label::textColourId,            RhythmColors::textPrimary());
    setColour (juce::Label::backgroundColourId,      juce::Colours::transparentBlack);
    setColour (juce::Slider::backgroundColourId,     RhythmColors::bg3());
    setColour (juce::Slider::trackColourId,          RhythmColors::accent());
    setColour (juce::Slider::thumbColourId,          RhythmColors::thumbColor());
    setColour (juce::TextEditor::textColourId,       RhythmColors::textPrimary());
    setColour (juce::TextEditor::backgroundColourId, RhythmColors::bg2());
    setColour (juce::TextEditor::outlineColourId,    RhythmColors::border1());
    setColour (juce::TextEditor::focusedOutlineColourId, RhythmColors::accent());
    setColour (juce::TextEditor::highlightColourId,  RhythmColors::accent().withAlpha (0.35f));
    setColour (juce::CaretComponent::caretColourId,  RhythmColors::accent());
    setColour (juce::TextButton::buttonColourId,     RhythmColors::bg3());
    setColour (juce::TextButton::buttonOnColourId,   RhythmColors::accentBg());
    setColour (juce::TextButton::textColourOffId,    RhythmColors::textPrimary());
    setColour (juce::TextButton::textColourOnId,     RhythmColors::accent());
    setColour (juce::AlertWindow::backgroundColourId, RhythmColors::bg2());
    setColour (juce::AlertWindow::textColourId,       RhythmColors::textPrimary());
    setColour (juce::AlertWindow::outlineColourId,    RhythmColors::border2());
}

void RhythmLookAndFeel::drawLabel (juce::Graphics& g, juce::Label& label)
{
    g.fillAll (label.findColour (juce::Label::backgroundColourId));
    if (! label.isBeingEdited())
    {
        g.setColour (label.findColour (juce::Label::textColourId).withAlpha (label.isEnabled() ? 1.0f : 0.5f));
        g.setFont (juce::Font (juce::FontOptions ((float) label.getFont().getHeight())));
        g.drawFittedText (label.getText(),
                          label.getLocalBounds(),
                          label.getJustificationType(),
                          juce::jmax (1, label.getHeight() / (int) label.getFont().getHeight()),
                          label.getMinimumHorizontalScale());
    }
}

void RhythmLookAndFeel::drawLinearSlider (juce::Graphics& g, int x, int y, int w, int h,
                                          float sliderPos, float /*minPos*/, float /*maxPos*/,
                                          juce::Slider::SliderStyle, juce::Slider& s)
{
    const auto fullBounds  = juce::Rectangle<float> ((float) x, (float) y, (float) w, (float) h);
    const float trackY     = fullBounds.getCentreY() - 2.0f;
    const auto trackBg     = juce::Rectangle<float> (fullBounds.getX(), trackY, fullBounds.getWidth(), 4.0f);
    g.setColour (RhythmColors::bg3());
    g.fillRoundedRectangle (trackBg, 2.0f);

    const auto trackFg     = juce::Rectangle<float> (fullBounds.getX(), trackY,
                                                     sliderPos - fullBounds.getX(), 4.0f);
    g.setColour (s.isEnabled() ? RhythmColors::accent() : RhythmColors::textDim());
    g.fillRoundedRectangle (trackFg, 2.0f);

    g.setColour (s.isEnabled() ? RhythmColors::thumbColor() : RhythmColors::textDim());
    g.fillEllipse (juce::Rectangle<float> (sliderPos - 6.0f, fullBounds.getCentreY() - 6.0f, 12.0f, 12.0f));
}

void RhythmLookAndFeel::fillTextEditorBackground (juce::Graphics& g, int w, int h, juce::TextEditor&)
{
    g.setColour (RhythmColors::bg2());
    g.fillRoundedRectangle (0.0f, 0.0f, (float) w, (float) h, 4.0f);
}

void RhythmLookAndFeel::drawTextEditorOutline (juce::Graphics& g, int w, int h, juce::TextEditor& te)
{
    g.setColour (te.hasKeyboardFocus (false) ? RhythmColors::accent() : RhythmColors::border1());
    g.drawRoundedRectangle (0.5f, 0.5f, (float) w - 1.0f, (float) h - 1.0f, 4.0f, 1.0f);
}

} // namespace rhythm
