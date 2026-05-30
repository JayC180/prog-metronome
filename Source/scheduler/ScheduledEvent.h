#pragma once

#include <cstdint>
#include <optional>
#include <string>

namespace rhythm
{

// One fire-able event — produced by StreamClock, consumed by AudioDispatcher and UI.
//
// soundId is the opaque key into SoundMap. nullopt = rest slot (UI tracking only).
// The audio dispatcher resolves soundId → SoundConfig → play. This type knows
// nothing about audio resources, volume curves, or anything else in SoundConfig.
struct ScheduledEvent
{
    int64_t                    absoluteNanos  { 0 };   // wall-clock fire time
    std::optional<std::string> soundId        {};      // nullopt = rest
    std::string                trackId        {};
    int                        trackItemIndex { 0 };   // direct index into TrackDraft.items
    int64_t                    firedTotal     { 0 };   // monotonic across all loops
    int                        passIndex      { 0 };   // position within current loop pass
    int                        passSize       { 0 };   // total events in current loop pass
    float                      volume         { 1.0f };

    bool isRest()    const { return ! soundId.has_value(); }
    bool isActive()  const { return soundId.has_value(); }
    bool isAudible() const { return soundId.has_value(); }

    // Comparator that gives a min-heap on absoluteNanos when used with
    // std::priority_queue<..., std::vector<...>, std::greater<...>>.
    bool operator< (const ScheduledEvent& o) const { return absoluteNanos <  o.absoluteNanos; }
    bool operator> (const ScheduledEvent& o) const { return absoluteNanos >  o.absoluteNanos; }
};

} // namespace rhythm
