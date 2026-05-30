#pragma once

#include "audio/JuceAudioEngine.h"
#include "builder/TrackBuilder.h"
#include "scheduler/Transport.h"

#include <juce_audio_processors/juce_audio_processors.h>

#include <memory>

class RhythmEngineProcessor : public juce::AudioProcessor
{
public:
    RhythmEngineProcessor();
    ~RhythmEngineProcessor() override;

    void prepareToPlay (double sampleRate, int samplesPerBlock) override;
    void releaseResources() override;
    void processBlock (juce::AudioBuffer<float>&, juce::MidiBuffer&) override;

    juce::AudioProcessorEditor* createEditor() override;
    bool hasEditor() const override { return true; }

    const juce::String getName() const override { return "RhythmEngine"; }
    bool acceptsMidi()  const override { return false; }
    bool producesMidi() const override { return false; }
    bool isMidiEffect() const override { return false; }
    double getTailLengthSeconds() const override { return 0.0; }

    int getNumPrograms() override { return 1; }
    int getCurrentProgram() override { return 0; }
    void setCurrentProgram (int) override {}
    const juce::String getProgramName (int) override { return {}; }
    void changeProgramName (int, const juce::String&) override {}

    void getStateInformation (juce::MemoryBlock& destData) override;
    void setStateInformation (const void* data, int sizeInBytes) override;

    bool isBusesLayoutSupported (const BusesLayout& layouts) const override;

    rhythm::TrackBuilder&    builder()  { return *builder_; }
    rhythm::Transport&       transport() { return *transport_; }
    rhythm::JuceAudioEngine& audio()    { return *audio_; }

private:
    std::unique_ptr<rhythm::JuceAudioEngine> audio_;
    std::unique_ptr<rhythm::Transport>       transport_;
    std::unique_ptr<rhythm::TrackBuilder>    builder_;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (RhythmEngineProcessor)
};
