#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <utility>
#include <vector>

namespace rhythm
{

// Single schedulable event with fire time pre-computed in nanoseconds from
// track start. No tempo context needed at runtime — everything is baked in.
//
//   offsetNanos    : absolute offset from track origin
//   soundId        : nullopt = rest (still tracked for playhead)
//   trackItemIndex : which TrackItem in the flat list this event came from
//                    (for UI highlight — maps directly, no conversion needed)
//   firedCount     : index within one loop pass (monotonic over a pass)
struct PrecomputedEvent
{
    int64_t                    offsetNanos    { 0 };
    std::optional<std::string> soundId        {};
    int                        trackItemIndex { 0 };
    int                        firedCount     { 0 };
    float                      volume         { 1.0f };

    bool isRest()    const { return ! soundId.has_value(); }
    bool isAudible() const { return soundId.has_value(); }
};

// Complete result of interpreting one TrackDraft.
struct InterpretResult
{
    std::string                            trackId;
    std::vector<PrecomputedEvent>          events;
    int64_t                                loopNanos { 0 };

    // if the track ends with an infinite group, the prefix runs once and this
    // is the event list for one pass of the infinite group. Empty otherwise.
    std::vector<PrecomputedEvent>          infinitePassEvents;
    int64_t                                infinitePassNanos { 0 };

    // (offsetNanos, effectiveBpm) — for display during playback
    std::vector<std::pair<int64_t, double>> tempoMapNanos;

    bool muted  { false };
    bool soloed { false };
};

} // namespace rhythm
