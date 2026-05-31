#pragma once

#include "TrackDraft.h"
#include "../scheduler/PrecomputedEvent.h"
#include <string>

namespace rhythm
{

// Interpreter — flat TrackItem list → pre-computed nanosecond events.
//
// Tempo semantics:
//
//   Inline tempo (mm/=bpm inside []):
//     Threads forward through all passes. No per-pass restore.
//
//   Modifier tempo (mm/=bpm on ], e.g. ]×4 mm(3/2)):
//     Applied ONCE after all passes of that group complete. PERSISTS outward.
//     If an outer group repeats N times, the modifier compounds N times.
//
//   =bpm modifier sets absolute tempo, so each pass resets to the same value
//   — no compounding. That is the distinction between mm and =bpm.
//
//   Inline =bpm and inline mm inside [] thread forward through passes (no restore).
//   Use modifier position on ] for compound modulation.
InterpretResult interpretTrackDraft (const TrackDraft& draft,
                                     double baseBpm,
                                     const std::string& defaultSound = "default");

} // namespace rhythm
