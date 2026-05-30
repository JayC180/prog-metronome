#include "PluginEditor.h"

RhythmEngineEditor::RhythmEngineEditor (RhythmEngineProcessor& p)
    : juce::AudioProcessorEditor (&p),
      main_ (p.builder())
{
    setSize (900, 680);
    addAndMakeVisible (main_);
    setResizable (true, true);
    setResizeLimits (600, 480, 1800, 1400);
}

void RhythmEngineEditor::paint (juce::Graphics& g)
{
    g.fillAll (rhythm::RhythmColors::bg0());
}

void RhythmEngineEditor::resized()
{
    main_.setBounds (getLocalBounds());
}
