#include "SoundMap.h"

namespace rhythm
{

SoundMap::SoundMap (const std::vector<SoundConfig>& configs)
{
    for (const auto& c : configs)
        map_[c.soundId] = c;
}

const SoundConfig* SoundMap::get (const std::string& soundId) const
{
    auto it = map_.find (soundId);
    return it == map_.end() ? nullptr : &it->second;
}

void SoundMap::set (const SoundConfig& config)
{
    map_[config.soundId] = config;
}

void SoundMap::remove (const std::string& soundId)
{
    map_.erase (soundId);
}

std::vector<SoundConfig> SoundMap::all() const
{
    std::vector<SoundConfig> out;
    out.reserve (map_.size());
    for (const auto& kv : map_) out.push_back (kv.second);
    return out;
}

} // namespace rhythm
