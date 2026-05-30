#include "TapTempoCalculator.h"
#include <chrono>

namespace rhythm
{

std::optional<double> TapTempoCalculator::tap()
{
    const auto nowMs = std::chrono::duration_cast<std::chrono::milliseconds> (
                          std::chrono::steady_clock::now().time_since_epoch())
                       .count();
    return tapAt (nowMs);
}

std::optional<double> TapTempoCalculator::tapAt (int64_t nowMs)
{
    if (! taps_.empty() && nowMs - taps_.back() > resetAfterMs_)
        taps_.clear();

    taps_.push_back (nowMs);
    while ((int) taps_.size() > maxTaps_) taps_.pop_front();

    if (taps_.size() < 2) return std::nullopt;

    int64_t sum = 0;
    for (size_t i = 1; i < taps_.size(); ++i)
        sum += taps_[i] - taps_[i - 1];
    const double meanMs = (double) sum / (double) (taps_.size() - 1);
    return 60'000.0 / meanMs;
}

} // namespace rhythm
