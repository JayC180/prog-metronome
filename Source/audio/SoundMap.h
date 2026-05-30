#pragma once

#include "SoundConfig.h"
#include <unordered_map>
#include <vector>

namespace rhythm
{

// The active sound map: soundId → SoundConfig. One per session/project.
// The audio dispatcher holds a reference to this.
class SoundMap
{
public:
    SoundMap() = default;
    explicit SoundMap (const std::vector<SoundConfig>& configs);

    const SoundConfig* get (const std::string& soundId) const;
    void               set (const SoundConfig& config);
    void               remove (const std::string& soundId);

    std::vector<SoundConfig> all() const;

    bool contains (const std::string& soundId) const { return map_.count (soundId) != 0; }

private:
    std::unordered_map<std::string, SoundConfig> map_;
};

} // namespace rhythm
