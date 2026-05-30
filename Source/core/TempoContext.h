#pragma once

#include "Rational.h"
#include <cstdint>

namespace rhythm
{

// Encapsulates all tempo-related state for a single stream.
//
// Key designs:
//   1. pulseUnit decouples "what 1 pulse means" from BPM.
//   2. modulation accumulates multiplicatively. Each metric modulation
//      is a ratio applied to the effective pulse length.
//   3. toNanos() is the ONLY place floats appear in the engine. All
//      other arithmetic stays in Rational.
//
// Immutable — all mutations return a new TempoContext.
class TempoContext
{
public:
    TempoContext (double bpm,
                  Rational pulseUnit  = Rational::ONE,
                  Rational modulation = Rational::ONE)
        : bpm_ (bpm), pulseUnit_ (pulseUnit), modulation_ (modulation) {}

    double   bpm()         const { return bpm_; }
    Rational pulseUnit()   const { return pulseUnit_; }
    Rational modulation()  const { return modulation_; }

    // Effective pulse length in nanoseconds after all modulation applied.
    int64_t pulseNanos() const
    {
        const double basePulseNanos = (60.0 / bpm_) * 1'000'000'000.0;
        const double effectiveRatio = (pulseUnit_ * modulation_).toDouble();
        return (int64_t) (basePulseNanos * effectiveRatio);
    }

    // BPM for UI display.
    double effectiveBpm() const { return bpm_ / (pulseUnit_ * modulation_).toDouble(); }

    // Convert a rational duration (in pulse units) to nanoseconds.
    // Called once per event at schedule time, never repeatedly at runtime.
    int64_t toNanos (const Rational& duration) const
    {
        const int64_t base = pulseNanos();
        return (base * duration.num()) / duration.den();
    }

    // Convert a tick offset from stream start to absolute nanoseconds.
    int64_t tickOffsetToNanos (int64_t ticks) const
    {
        return (int64_t) ((double) pulseNanos() * (double) ticks / (double) TICKS_PER_PULSE);
    }

    // The ratio describes what happens to the pulse LENGTH (not BPM).
    // ratio < 1 → shorter pulse; ratio > 1 → longer pulse.
    TempoContext withModulation (const Rational& ratio) const
    {
        return TempoContext (bpm_, pulseUnit_, (modulation_ * ratio).reduced());
    }

    // Hard BPM override — resets modulation accumulator.
    TempoContext withBpm (double newBpm) const
    {
        return TempoContext (newBpm, pulseUnit_, Rational::ONE);
    }

    TempoContext withPulseUnit (const Rational& unit) const
    {
        return TempoContext (bpm_, unit, modulation_);
    }

private:
    double   bpm_;
    Rational pulseUnit_;
    Rational modulation_;
};

// Pulse-unit presets — referenced by upper layers but the engine doesn't enforce them.
namespace PulseUnit
{
    inline const Rational QUARTER         { 1, 1 };
    inline const Rational DOTTED_QUARTER  { 3, 2 };
    inline const Rational HALF            { 2, 1 };
    inline const Rational EIGHTH          { 1, 2 };

    // carnatic
    inline const Rational MATRA_CHATUSRA  { 1, 4 };
    inline const Rational MATRA_TISRA     { 1, 3 };
    inline const Rational MATRA_KHANDA    { 1, 5 };
    inline const Rational MATRA_MISRA     { 1, 7 };
    inline const Rational MATRA_SANKIRNA  { 1, 9 };
}

} // namespace rhythm
