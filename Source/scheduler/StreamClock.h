#pragma once

#include "PrecomputedEvent.h"
#include "ScheduledEvent.h"
#include <cstdint>
#include <queue>
#include <vector>

namespace rhythm
{

// Min-heap on ScheduledEvent.absoluteNanos.
using EventQueue = std::priority_queue<ScheduledEvent,
                                       std::vector<ScheduledEvent>,
                                       std::greater<ScheduledEvent>>;

// All timing is pre-computed. This class just iterates a vector of
// PrecomputedEvents and schedules them at (originNanos + event.offsetNanos),
// looping by advancing loopOriginNanos by loopNanos each iteration.
//
// For tracks with an infinite group, the non-infinite prefix plays once,
// then the infinite pass loops forever.
class StreamClock
{
public:
    explicit StreamClock (InterpretResult result);

    const std::string& trackId() const { return result_.trackId; }
    bool isRunning() const { return phase_ != Phase::Stopped; }

    void fillUntil (int64_t horizonNanos, int64_t originNanos, EventQueue& queue);
    void reset();

    std::string debugState() const;

private:
    enum class Phase { Prefix, Infinite, Stopped };

    void advancePhase();

    InterpretResult result_;
    Phase           phase_           { Phase::Prefix };
    int             eventIndex_      { 0 };
    int64_t         loopOriginNanos_ { 0 };
    int64_t         firedTotal_      { 0 };
};

} // namespace rhythm
