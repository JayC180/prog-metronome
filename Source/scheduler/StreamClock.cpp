#include "StreamClock.h"
#include <sstream>

namespace rhythm
{

StreamClock::StreamClock (InterpretResult result)
    : result_ (std::move (result)) {}

void StreamClock::fillUntil (int64_t horizonNanos, int64_t originNanos, EventQueue& queue)
{
    if (! isRunning()) return;

    // Hard upper bound to avoid pathological infinite loops in case both lists are empty.
    int guard = 500'000;

    while (guard-- > 0)
    {
        const std::vector<PrecomputedEvent>* current = nullptr;
        switch (phase_)
        {
            case Phase::Prefix:   current = &result_.events;             break;
            case Phase::Infinite: current = &result_.infinitePassEvents; break;
            case Phase::Stopped:  return;
        }

        if (current->empty())
        {
            advancePhase();
            // If after advancing we are stopped, bail.
            if (phase_ == Phase::Stopped) return;
            // If both phases are empty we would spin forever; the guard catches that.
            continue;
        }

        if (eventIndex_ >= (int) current->size())
        {
            advancePhase();
            if (phase_ == Phase::Stopped) return;
            continue;
        }

        const auto& event = (*current)[(size_t) eventIndex_];
        const int64_t fireNanos = originNanos + loopOriginNanos_ + event.offsetNanos;

        if (fireNanos >= horizonNanos) return;

        ScheduledEvent se;
        se.absoluteNanos  = fireNanos;
        se.soundId        = event.soundId;
        se.trackId        = result_.trackId;
        se.trackItemIndex = event.trackItemIndex;
        se.firedTotal     = firedTotal_;
        se.passIndex      = eventIndex_;
        se.passSize       = (int) current->size();
        se.volume         = event.volume;
        queue.push (std::move (se));

        ++firedTotal_;
        ++eventIndex_;
    }
}

void StreamClock::advancePhase()
{
    eventIndex_ = 0;
    switch (phase_)
    {
        case Phase::Prefix:
            loopOriginNanos_ += result_.loopNanos;
            phase_ = (! result_.infinitePassEvents.empty()) ? Phase::Infinite : Phase::Prefix;
            break;
        case Phase::Infinite:
            loopOriginNanos_ += result_.infinitePassNanos;
            // remain in Infinite
            break;
        case Phase::Stopped:
            break;
    }
}

void StreamClock::reset()
{
    phase_           = Phase::Prefix;
    eventIndex_      = 0;
    loopOriginNanos_ = 0;
    firedTotal_      = 0;
}

std::string StreamClock::debugState() const
{
    const char* phase = phase_ == Phase::Prefix   ? "PREFIX"
                      : phase_ == Phase::Infinite ? "INFINITE"
                                                  : "STOPPED";
    std::ostringstream os;
    os << "StreamClock(" << result_.trackId << " phase=" << phase
       << " idx=" << eventIndex_
       << " loopOrigin=" << loopOriginNanos_ << "ns"
       << " fired=" << firedTotal_ << ")";
    return os.str();
}

} // namespace rhythm
