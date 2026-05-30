#include "TrackBuilderState.h"

namespace rhythm
{

bool TrackBuilderState::canOpenBracket() const
{
    return canEdit() && activeTrack() != nullptr
        && inputMode.kind == InputMode::Kind::Normal
        && ! insertionBlockedByInfinite();
}

bool TrackBuilderState::canCloseBracket() const
{
    if (! canEdit() || activeTrack() == nullptr || inputMode.kind != InputMode::Kind::Normal) return false;
    if (insertionBlockedByInfinite()) return false;
    const auto& t = *activeTrack();
    if (t.items.empty()) return false;
    const int prevIdx = insertionIndex() - 1;
    if (prevIdx >= 0 && prevIdx < (int) t.items.size() && t.items[(size_t) prevIdx].isBracketOpen())
        return false;
    const int upTo = std::min ((int) t.items.size() - 1, std::max (prevIdx, 0));
    return t.bracketDepthAt (upTo) > 0;
}

bool TrackBuilderState::canSetRepeat() const
{
    if (! canEdit() || activeTrack() == nullptr || inputMode.kind != InputMode::Kind::Normal) return false;
    if (insertionBlockedByInfinite()) return false;
    const auto& t = *activeTrack();
    const auto close = findRelevantClose();
    if (! close.has_value()) return false;
    return ! hasRepeatAfterClose (t, *close);
}

bool TrackBuilderState::canSetInfiniteRepeat() const
{
    if (! canSetRepeat()) return false;
    const auto& t = *activeTrack();
    const auto close = findRelevantClose();
    if (! close.has_value()) return false;
    // only modifiers allowed after ]
    for (int i = *close + 1; i < (int) t.items.size(); ++i)
    {
        const auto& it = t.items[(size_t) i];
        if (! (it.isRepeat() || it.isModulation() || it.isSetBpm())) return false;
    }
    // no mm inside the bracket
    const auto open = t.matchingOpenIndex (*close);
    if (! open.has_value()) return false;
    for (int i = *open + 1; i < *close; ++i)
        if (t.items[(size_t) i].isModulation()) return false;
    return true;
}

bool TrackBuilderState::canInsertTempo() const
{
    if (! canEdit() || activeTrack() == nullptr || inputMode.kind != InputMode::Kind::Normal) return false;
    if (insertionBlockedByInfinite()) return false;
    const auto& t = *activeTrack();
    if (t.items.empty()) return true;
    const int prevIdx = insertionIndex() - 1;
    if (prevIdx < 0 || prevIdx >= (int) t.items.size()) return true;
    const auto& prev = t.items[(size_t) prevIdx];
    return ! prev.isModulation() && ! prev.isSetBpm();
}

bool TrackBuilderState::canEnterBeats() const
{
    return canEdit() && activeTrack() != nullptr
        && inputMode.kind == InputMode::Kind::Normal
        && ! insertionBlockedByInfinite();
}

bool TrackBuilderState::canEditItem() const
{
    if (! canEdit() || isPlaying()) return false;
    const auto* item = cursorItem();
    if (item == nullptr) return false;
    if (item->isBeat() || item->isModulation() || item->isSetBpm()) return true;
    if (const auto* r = item->getIf<TrackItem::Repeat>(); r != nullptr && ! r->isInfinite()) return true;
    return false;
}

bool TrackBuilderState::insertionBlockedByInfinite() const
{
    const auto* tp = activeTrack();
    if (tp == nullptr) return false;
    const auto& t = *tp;
    int infIdx = -1;
    for (int i = 0; i < (int) t.items.size(); ++i)
    {
        const auto* r = t.items[(size_t) i].getIf<TrackItem::Repeat>();
        if (r != nullptr && r->isInfinite()) { infIdx = i; break; }
    }
    if (infIdx < 0) return false;
    int closeIdx = infIdx - 1;
    while (closeIdx >= 0)
    {
        const auto& it = t.items[(size_t) closeIdx];
        if (! (it.isModulation() || it.isSetBpm())) break;
        --closeIdx;
    }
    if (closeIdx < 0 || ! t.items[(size_t) closeIdx].isBracketClose()) return false;
    return insertionIndex() > closeIdx;
}

std::optional<int> TrackBuilderState::findRelevantClose() const
{
    const auto* tp = activeTrack();
    if (tp == nullptr) return std::nullopt;
    const auto& t = *tp;

    if (cursorIndex.has_value())
    {
        const int idx = *cursorIndex;
        if (idx >= 0 && idx < (int) t.items.size() && t.items[(size_t) idx].isBracketClose())
            return idx;
    }
    int searchIdx = insertionIndex() - 1;
    while (searchIdx >= 0 && searchIdx < (int) t.items.size())
    {
        const auto& it = t.items[(size_t) searchIdx];
        if (it.isBracketClose()) return searchIdx;
        if (it.isRepeat() || it.isModulation() || it.isSetBpm()) { --searchIdx; continue; }
        break;
    }
    return std::nullopt;
}

bool TrackBuilderState::hasRepeatAfterClose (const TrackDraft& t, int closeIndex) const
{
    int i = closeIndex + 1;
    while (i < (int) t.items.size())
    {
        const auto& it = t.items[(size_t) i];
        if (! (it.isRepeat() || it.isModulation() || it.isSetBpm())) break;
        if (it.isRepeat()) return true;
        ++i;
    }
    return false;
}

std::vector<std::string> TrackBuilderState::buildErrors() const
{
    std::vector<std::string> out;
    for (int ti = 0; ti < (int) tracks.size(); ++ti)
    {
        const auto& t = tracks[(size_t) ti];
        const int depth = t.bracketDepth();
        if (depth > 0)
            out.push_back ("Track " + std::to_string (ti + 1) + ": "
                           + std::to_string (depth) + " unclosed bracket(s)");
        if (depth < 0)
            out.push_back ("Track " + std::to_string (ti + 1) + ": unmatched ']'");
        for (int idx = 0; idx < (int) t.items.size(); ++idx)
        {
            const auto& it = t.items[(size_t) idx];
            if (it.isBracketOpen())
            {
                const auto close = t.matchingCloseIndex (idx);
                if (close.has_value() && *close == idx + 1)
                    out.push_back ("Track " + std::to_string (ti + 1)
                                   + ": empty bracket at position " + std::to_string (idx));
            }
        }
        int infIdx = -1;
        for (int i = 0; i < (int) t.items.size(); ++i)
        {
            const auto* r = t.items[(size_t) i].getIf<TrackItem::Repeat>();
            if (r != nullptr && r->isInfinite()) { infIdx = i; break; }
        }
        if (infIdx >= 0)
        {
            if (infIdx != (int) t.items.size() - 1)
                out.push_back ("Track " + std::to_string (ti + 1) + ": ×∞ must be the last item");
            const int closeIdx = infIdx - 1;
            const auto open = (closeIdx >= 0) ? t.matchingOpenIndex (closeIdx) : std::nullopt;
            if (open.has_value())
            {
                bool hasMm = false;
                for (int i = *open + 1; i < closeIdx; ++i)
                    if (t.items[(size_t) i].isModulation()) { hasMm = true; break; }
                if (hasMm) out.push_back ("Track " + std::to_string (ti + 1)
                                          + ": mm not allowed inside ×∞ (use =bpm)");
            }
        }
    }
    return out;
}

} // namespace rhythm
