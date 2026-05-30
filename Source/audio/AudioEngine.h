#pragma once

#include <cstdint>
#include <string>

namespace rhythm
{

// Polymorphic audio engine — the dispatcher calls trigger() when an event
// is due. Implementations are responsible for resolving soundId to an audio
// resource and rendering it. expectedFireNanos is the timestamp the
// scheduler originally targeted (System nanoTime semantics).
class AudioEngine
{
public:
    virtual ~AudioEngine() = default;

    virtual bool open() = 0;
    virtual void close() = 0;
    virtual void trigger (const std::string& soundId, float volume, int64_t expectedFireNanos) = 0;
    virtual void stopAll() = 0;

    virtual int activeVoiceCount() const = 0;
};

} // namespace rhythm
