#pragma once

#include "TrackItem.h"
#include <optional>
#include <string>
#include <vector>

namespace rhythm
{

class TrackDraft
{
public:
    std::string                id;
    std::string                label;
    int                        denom { 4 };
    std::vector<TrackItem>     items {};
    bool                       muted  { false };
    bool                       soloed { false };
    std::optional<std::string> defaultSoundId {}; // nullopt = use global default

    int bracketDepth() const;
    int bracketDepthAt (int index) const;

    std::optional<int> matchingOpenIndex  (int closeIndex) const;
    std::optional<int> matchingCloseIndex (int openIndex) const;

    bool hasInfiniteRepeat() const;
    bool isEmpty() const { return items.empty(); }
    int  size()    const { return (int) items.size(); }
};

} // namespace rhythm
