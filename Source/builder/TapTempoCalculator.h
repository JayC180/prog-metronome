#pragma once

#include <deque>
#include <cstdint>
#include <optional>

namespace rhythm
{

// Records tap timestamps and computes BPM from the mean interval between
// all consecutive taps in the current sequence. Resets automatically if
// the gap since the last tap exceeds resetAfterMs. Minimum 2 taps required.
class TapTempoCalculator
{
public:
    explicit TapTempoCalculator (int64_t resetAfterMs = 3000, int maxTaps = 8)
        : resetAfterMs_ (resetAfterMs), maxTaps_ (maxTaps) {}

    // Record a tap at the current time. Returns the new BPM, or nullopt if
    // fewer than 2 taps recorded yet.
    std::optional<double> tap();
    std::optional<double> tapAt (int64_t nowMs);

    void reset() { taps_.clear(); }
    int  tapCount() const { return (int) taps_.size(); }

private:
    int64_t                  resetAfterMs_;
    int                      maxTaps_;
    std::deque<int64_t>      taps_; // timestamps in ms
};

} // namespace rhythm
