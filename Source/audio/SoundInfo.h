#pragma once

#include <string>

namespace rhythm
{

// Platform-agnostic sound model — UI list rows. Implementations of a
// SoundRepository on a given platform translate their internal entries
// into this type before handing them to the UI.
struct SoundInfo
{
    std::string id;     // resource key used in BeatNode / TrackItem
    std::string label;  // display name (no prefix, underscores → spaces)
    bool        isUser; // true = user-imported, false = bundled default
};

} // namespace rhythm
