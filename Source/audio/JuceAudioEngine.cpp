#include "JuceAudioEngine.h"

namespace rhythm
{

JuceAudioEngine::JuceAudioEngine()
    : soundMap_ (std::make_shared<SoundMap>())
{
    formatManager_.registerBasicFormats();
}

JuceAudioEngine::~JuceAudioEngine() = default;

bool JuceAudioEngine::open()
{
    isOpen_.store (true);
    return true;
}

void JuceAudioEngine::close()
{
    stopAll();
    isOpen_.store (false);
}

void JuceAudioEngine::setSoundMap (std::shared_ptr<SoundMap> map)
{
    soundMap_ = std::move (map);
}

bool JuceAudioEngine::loadSample (const std::string& soundId, const juce::File& file)
{
    std::unique_ptr<juce::AudioFormatReader> reader (
        formatManager_.createReaderFor (file));
    if (reader == nullptr) return false;

    auto s = std::make_shared<Sample>();
    s->sourceSampleRate = reader->sampleRate;
    s->buffer.setSize ((int) reader->numChannels, (int) reader->lengthInSamples);
    reader->read (&s->buffer, 0, (int) reader->lengthInSamples, 0, true, true);

    if (auto* cfg = soundMap_->get (soundId))
    {
        s->baseVolume = cfg->volume;
        s->pitch      = cfg->pitch;
    }
    samples_[soundId] = std::move (s);
    return true;
}

void JuceAudioEngine::trigger (const std::string& soundId, float volume, int64_t /*expectedFireNanos*/)
{
    int start1 = 0, size1 = 0, start2 = 0, size2 = 0;
    triggerFifo_.prepareToWrite (1, start1, size1, start2, size2);
    if (size1 + size2 == 0) return; // queue full — drop trigger

    if (size1 > 0)
        triggerBuffer_[(size_t) start1] = { soundId, volume };
    else
        triggerBuffer_[(size_t) start2] = { soundId, volume };

    triggerFifo_.finishedWrite (1);
}

void JuceAudioEngine::stopAll()
{
    for (auto& v : voices_) v.active.store (false);
    // drain trigger fifo
    int start1 = 0, size1 = 0, start2 = 0, size2 = 0;
    const int ready = triggerFifo_.getNumReady();
    triggerFifo_.prepareToRead (ready, start1, size1, start2, size2);
    triggerFifo_.finishedRead (ready);
}

int JuceAudioEngine::activeVoiceCount() const
{
    int n = 0;
    for (const auto& v : voices_) if (v.active.load()) ++n;
    return n;
}

void JuceAudioEngine::prepareToPlay (double sampleRate, int /*samplesPerBlock*/)
{
    deviceSampleRate_.store (sampleRate);
    for (auto& v : voices_) v.active.store (false);
    generateBuiltInClicks (sampleRate);
}

void JuceAudioEngine::generateBuiltInClicks (double sampleRate)
{
    auto makeClick = [sampleRate] (double freqHz, double decay, int lengthMs, float gain)
    {
        const int N = juce::jmax (1, (int) (lengthMs * sampleRate / 1000.0));
        auto s = std::make_shared<Sample>();
        s->sourceSampleRate = sampleRate;
        s->baseVolume       = 1.0f;
        s->pitch            = 1.0f;
        s->buffer.setSize (1, N);
        auto* out = s->buffer.getWritePointer (0);
        const double twoPi = juce::MathConstants<double>::twoPi;
        for (int i = 0; i < N; ++i)
        {
            const double t   = (double) i / sampleRate;
            const float  env = (float) std::exp (-t * decay);
            const float  smp = (float) std::sin (twoPi * freqHz * t);
            out[i] = smp * env * gain;
        }
        return s;
    };

    samples_["default"] = makeClick (1000.0,  60.0,  80, 0.6f);
    samples_["hi"]      = makeClick (1500.0,  70.0,  60, 0.55f);
    samples_["lo"]      = makeClick ( 500.0,  50.0, 100, 0.65f);
    samples_["accent"]  = makeClick ( 650.0,  45.0, 120, 0.75f);
    samples_["hat"]     = makeClick (6000.0, 140.0,  35, 0.45f);
}

void JuceAudioEngine::releaseResources()
{
    stopAll();
}

void JuceAudioEngine::startVoice (const Trigger& t)
{
    auto sampleIt = samples_.find (t.soundId);
    if (sampleIt == samples_.end())
        sampleIt = samples_.find ("default"); // graceful fallback
    if (sampleIt == samples_.end()) return;
    const Sample* sample = sampleIt->second.get();
    if (sample == nullptr || sample->buffer.getNumSamples() == 0) return;

    // pick a free voice; if none, steal the oldest (just overwrite voice 0)
    int slot = -1;
    for (int i = 0; i < (int) voices_.size(); ++i)
        if (! voices_[(size_t) i].active.load()) { slot = i; break; }
    if (slot < 0) slot = 0;

    Voice& v = voices_[(size_t) slot];
    v.sample        = sample;
    v.position      = 0.0;
    v.volume        = t.volume * sample->baseVolume;
    v.playbackRatio = (sample->sourceSampleRate / deviceSampleRate_.load()) * sample->pitch;
    v.active.store (true);
}

void JuceAudioEngine::processBlock (juce::AudioBuffer<float>& buffer)
{
    // Drain triggers from the dispatcher thread.
    int start1 = 0, size1 = 0, start2 = 0, size2 = 0;
    const int ready = triggerFifo_.getNumReady();
    triggerFifo_.prepareToRead (ready, start1, size1, start2, size2);
    for (int i = 0; i < size1; ++i) startVoice (triggerBuffer_[(size_t) (start1 + i)]);
    for (int i = 0; i < size2; ++i) startVoice (triggerBuffer_[(size_t) (start2 + i)]);
    triggerFifo_.finishedRead (ready);

    render (buffer);
}

void JuceAudioEngine::render (juce::AudioBuffer<float>& buffer)
{
    const int numChannels = buffer.getNumChannels();
    const int numSamples  = buffer.getNumSamples();

    for (auto& v : voices_)
    {
        if (! v.active.load()) continue;
        const Sample* s = v.sample;
        if (s == nullptr) { v.active.store (false); continue; }
        const int   sourceSamples  = s->buffer.getNumSamples();
        const int   sourceChannels = s->buffer.getNumChannels();
        const double ratio         = v.playbackRatio;
        const float  vol           = v.volume;

        double pos = v.position;
        for (int n = 0; n < numSamples; ++n)
        {
            if (pos >= (double) sourceSamples - 1.0) { v.active.store (false); break; }
            const int    i0 = (int) pos;
            const float  frac = (float) (pos - (double) i0);
            for (int ch = 0; ch < numChannels; ++ch)
            {
                const int sCh = juce::jmin (ch, sourceChannels - 1);
                const float* src = s->buffer.getReadPointer (sCh);
                const float sample = src[i0] + (src[i0 + 1] - src[i0]) * frac;
                buffer.addSample (ch, n, sample * vol);
            }
            pos += ratio;
        }
        v.position = pos;
    }
}

} // namespace rhythm
