#pragma once

#include <string>

namespace rhythm
{

// Complete audio configuration for one sound.
//
// Entirely separate from the rhythm tree. TrackItem::Beat and BeatNode::Leaf
// hold a soundId (string key). At audio dispatch time, the engine looks up
// that key in the active SoundMap to find this config and know how to play it.
//
// resourceUri is intentionally flexible — typically a file path, but may be
// a URL or a bundled-asset reference depending on the host platform.
struct SoundConfig
{
    std::string soundId;
    std::string resourceUri;
    float       volume { 1.0f };   // 0.0 – 1.0
    float       pitch  { 1.0f };   // playback speed multiplier (1.0 = original)
    std::string label  {};         // display name in UI; defaults to soundId
};

} // namespace rhythm
