#pragma once

#include "AudioEngine.h"
#include "SoundMap.h"

#include <juce_audio_basics/juce_audio_basics.h>
#include <juce_audio_formats/juce_audio_formats.h>

#include <atomic>
#include <memory>
#include <unordered_map>
#include <vector>

namespace rhythm
{

// Sample-playback audio engine living inside an AudioProcessor.
//
// Architecture:
//   - SoundMap (config) → file path
//   - AudioFormatManager loads each unique resource into a shared AudioBuffer
//   - trigger() pushes a Trigger onto a lock-free SPSC FIFO (called from
//     the dispatcher thread); processBlock drains the FIFO and assigns
//     triggers to free voices; voices render into the output buffer
class JuceAudioEngine : public AudioEngine
{
public:
    JuceAudioEngine();
    ~JuceAudioEngine() override;

    bool open() override;
    void close() override;
    void trigger (const std::string& soundId, float volume, int64_t expectedFireNanos) override;
    void stopAll() override;
    int  activeVoiceCount() const override;

    // wiring from the AudioProcessor
    void prepareToPlay (double sampleRate, int /*samplesPerBlock*/);
    void processBlock  (juce::AudioBuffer<float>& buffer);
    void releaseResources();

    // sample loading
    void setSoundMap (std::shared_ptr<SoundMap> map);
    bool loadSample  (const std::string& soundId, const juce::File& file);

    // Procedurally-generated built-in clicks (default, accent, hi, lo, hat).
    // Regenerated automatically at the current device sample rate by
    // prepareToPlay() so playbackRatio stays at 1.0.
    void generateBuiltInClicks (double sampleRate);

private:
    struct Sample
    {
        juce::AudioBuffer<float> buffer;
        double                   sourceSampleRate { 44100.0 };
        float                    baseVolume       { 1.0f };
        float                    pitch            { 1.0f };
    };

    struct Voice
    {
        std::atomic<bool> active { false };
        const Sample*     sample { nullptr };
        double            position { 0.0 }; // fractional read index into sample
        float             volume  { 1.0f };
        double            playbackRatio { 1.0 }; // sample SR / device SR × pitch
    };

    struct Trigger
    {
        std::string soundId;
        float       volume;
    };

    void render (juce::AudioBuffer<float>& buffer);
    void startVoice (const Trigger& t);

    juce::AudioFormatManager                                       formatManager_;
    std::unordered_map<std::string, std::shared_ptr<Sample>>       samples_;

    std::shared_ptr<SoundMap>                                      soundMap_;

    static constexpr int                                           kVoiceCount = 16;
    std::array<Voice, kVoiceCount>                                 voices_ {};

    // SPSC trigger queue. The dispatcher pushes; processBlock pops.
    // Lock-free abi from juce::AbstractFifo.
    juce::AbstractFifo                                             triggerFifo_ { 128 };
    std::array<Trigger, 128>                                       triggerBuffer_ {};

    std::atomic<double>                                            deviceSampleRate_ { 44100.0 };
    std::atomic<bool>                                              isOpen_ { false };
};

} // namespace rhythm
