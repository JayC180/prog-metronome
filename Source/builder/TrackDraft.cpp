#include "TrackDraft.h"

namespace rhythm
{

int TrackDraft::bracketDepth() const
{
    int d = 0;
    for (const auto& it : items)
    {
        if      (it.isBracketOpen())  ++d;
        else if (it.isBracketClose()) --d;
    }
    return d;
}

int TrackDraft::bracketDepthAt (int index) const
{
    int d = 0;
    const int limit = std::min (index + 1, (int) items.size());
    for (int i = 0; i < limit; ++i)
    {
        if      (items[(size_t) i].isBracketOpen())  ++d;
        else if (items[(size_t) i].isBracketClose()) --d;
    }
    return d;
}

std::optional<int> TrackDraft::matchingOpenIndex (int closeIndex) const
{
    int depth = 0;
    for (int i = closeIndex; i >= 0; --i)
    {
        if (items[(size_t) i].isBracketClose())
            ++depth;
        else if (items[(size_t) i].isBracketOpen())
        {
            --depth;
            if (depth == 0) return i;
        }
    }
    return std::nullopt;
}

std::optional<int> TrackDraft::matchingCloseIndex (int openIndex) const
{
    int depth = 0;
    for (int i = openIndex; i < (int) items.size(); ++i)
    {
        if (items[(size_t) i].isBracketOpen())
            ++depth;
        else if (items[(size_t) i].isBracketClose())
        {
            --depth;
            if (depth == 0) return i;
        }
    }
    return std::nullopt;
}

bool TrackDraft::hasInfiniteRepeat() const
{
    for (const auto& it : items)
        if (const auto* r = it.getIf<TrackItem::Repeat>(); r != nullptr && r->isInfinite())
            return true;
    return false;
}

} // namespace rhythm
