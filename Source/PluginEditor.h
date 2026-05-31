#pragma once

#include "PluginProcessor.h"
#include "ui/MainComponent.h"
#include <juce_gui_basics/juce_gui_basics.h>

class RhythmEngineEditor : public juce::AudioProcessorEditor
{
public:
    explicit RhythmEngineEditor (RhythmEngineProcessor&);
    ~RhythmEngineEditor() override = default;

    void paint  (juce::Graphics&) override;
    void resized() override;

private:
    rhythm::MainComponent  main_;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (RhythmEngineEditor)
};
