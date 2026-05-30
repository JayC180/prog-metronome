#pragma once

#include "PrecomputedEvent.h"
#include "ScheduledEvent.h"
#include "StreamClock.h"
#include "../audio/AudioEngine.h"
#include "../audio/SoundMap.h"

#include <atomic>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

namespace rhythm
{

// All timing pre-computed by the interpreter. Transport coordinates
// StreamClocks and fires events at their pre-computed nanosecond targets.
class Transport
{
public:
    static constexpr int64_t LOOKAHEAD_NANOS       = 150'000'000;   // 150 ms
    static constexpr int     SCHEDULER_INTERVAL_MS = 50;
    static constexpr int64_t DISPATCHER_SPIN_NANOS = 500'000;       // 0.5 ms

    explicit Transport (AudioEngine* engine = nullptr);
    ~Transport();

    void start();
    void stop();

    // Swap in a new set of interpret results — automatically stops + reconfigures.
    void updateTracks (std::vector<InterpretResult> results);

    void setSoundMap (std::shared_ptr<SoundMap> map) { soundMap_ = std::move (map); }
    std::shared_ptr<SoundMap> soundMap() const      { return soundMap_; }

    void setAudioEngine (AudioEngine* engine) { audioEngine_.store (engine); }

    // Optional UI hook — invoked for every fired event on the dispatcher thread.
    void setOnFired (std::function<void (const ScheduledEvent&)> cb) { onFired_ = std::move (cb); }

    std::string debugState() const;

private:
    void runScheduler();
    void runDispatcher();
    void fillQueueUntil (int64_t horizonNanos);

    static int64_t nowNanos();

    std::atomic<AudioEngine*>                     audioEngine_;
    std::shared_ptr<SoundMap>                     soundMap_;

    std::vector<InterpretResult>                  results_;
    std::vector<std::unique_ptr<StreamClock>>     clocks_;

    EventQueue                                    eventQueue_;
    std::mutex                                    queueMutex_;

    int64_t                                       originNanos_         { 0 };
    int64_t                                       scheduledUpToNanos_  { 0 };
    std::atomic<bool>                             isPlaying_           { false };

    std::thread                                   schedulerThread_;
    std::thread                                   dispatcherThread_;

    std::function<void (const ScheduledEvent&)>   onFired_;
};

} // namespace rhythm
