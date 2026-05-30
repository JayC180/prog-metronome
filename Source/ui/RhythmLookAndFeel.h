#pragma once

#include "RhythmColors.h"
#include <juce_gui_basics/juce_gui_basics.h>

namespace rhythm
{

// LookAndFeel that pulls colours from the active RhythmColors theme.
// Most custom widgets in this project paint themselves directly, but this
// LookAndFeel provides sane defaults for stock Slider / Label / TextEditor
// instances embedded inside our dialogs.
class RhythmLookAndFeel : public juce::LookAndFeel_V4
{
public:
    RhythmLookAndFeel();
    ~RhythmLookAndFeel() override = default;

    void drawLabel (juce::Graphics&, juce::Label&) override;

    void drawLinearSlider (juce::Graphics&, int x, int y, int w, int h,
                           float sliderPos, float min, float max,
                           juce::Slider::SliderStyle, juce::Slider&) override;

    void fillTextEditorBackground (juce::Graphics&, int, int, juce::TextEditor&) override;
    void drawTextEditorOutline    (juce::Graphics&, int, int, juce::TextEditor&) override;

private:
    void refreshDefaults();
};

} // namespace rhythm
