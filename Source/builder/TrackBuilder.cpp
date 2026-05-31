#include "TrackBuilder.h"

#include <algorithm>
#include <regex>

namespace rhythm
{

int TrackBuilder::nextCounter_ = 0;

TrackDraft TrackBuilder::newTrackDraft (int index)
{
    TrackDraft d;
    d.id    = "track_" + std::to_string (index) + "_" + std::to_string (nextCounter_++);
    d.label = "Track " + std::to_string (index + 1);
    return d;
}

TrackBuilder::TrackBuilder (double initialBpm, Transport* transport)
    : transport_ (transport)
{
    state_.tracks.push_back (newTrackDraft (0));
    state_.activeTrackIndex = 0;
    state_.bpm              = initialBpm;
}

void TrackBuilder::emit (TrackBuilderState newState)
{
    state_ = std::move (newState);
    if (onStateChanged_) onStateChanged_ (state_);
}

int TrackBuilder::insertionPoint() const
{
    if (state_.cursorIndex.has_value()) return *state_.cursorIndex + 1;
    const auto* t = state_.activeTrack();
    return t != nullptr ? (int) t->items.size() : 0;
}

void TrackBuilder::insertItem (TrackItem item)
{
    if (state_.activeTrack() == nullptr) return;
    auto next = state_;
    auto& track = next.tracks[(size_t) next.activeTrackIndex];
    const int idx = (state_.cursorIndex.has_value() ? *state_.cursorIndex + 1
                                                    : (int) track.items.size());
    track.items.insert (track.items.begin() + idx, std::move (item));
    next.cursorIndex = idx;
    emit (std::move (next));
}

void TrackBuilder::updateActiveTrack (const std::function<TrackDraft (const TrackDraft&)>& fn)
{
    updateTrack (state_.activeTrackIndex, fn);
}

void TrackBuilder::updateTrack (int index, const std::function<TrackDraft (const TrackDraft&)>& fn)
{
    if (index < 0 || index >= (int) state_.tracks.size()) return;
    auto next = state_;
    next.tracks[(size_t) index] = fn (next.tracks[(size_t) index]);
    emit (std::move (next));
}

void TrackBuilder::setCursor (std::optional<int> index)
{
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    std::optional<int> clamped = index;
    if (clamped.has_value() && ! t->items.empty())
        clamped = std::clamp (*clamped, 0, (int) t->items.size() - 1);
    else if (clamped.has_value())
        clamped.reset();
    auto next = state_;
    next.cursorIndex = clamped;
    if (state_.isEditMode()) next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::moveCursorPrev()
{
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    auto next = state_;
    if (! state_.cursorIndex.has_value())
        next.cursorIndex = (int) t->items.size() - 1;
    else if (*state_.cursorIndex <= 0)
        next.cursorIndex.reset();
    else
        next.cursorIndex = *state_.cursorIndex - 1;
    if (state_.isEditMode()) next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::moveCursorNext()
{
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    auto next = state_;
    if (! state_.cursorIndex.has_value())
        next.cursorIndex.reset();
    else if (*state_.cursorIndex >= (int) t->items.size() - 1)
        next.cursorIndex.reset();
    else
        next.cursorIndex = *state_.cursorIndex + 1;
    if (state_.isEditMode()) next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::addTrack()
{
    const int idx = (int) state_.tracks.size();
    auto next = state_;
    next.tracks.push_back (newTrackDraft (idx));
    next.activeTrackIndex = idx;
    next.cursorIndex.reset();
    next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::copyTrack()
{
    const auto* src = state_.activeTrack();
    if (src == nullptr) return;
    const int idx = (int) state_.tracks.size();
    TrackDraft copy = newTrackDraft (idx); // new id + "Track N" label
    copy.items          = src->items;
    copy.denom          = src->denom;
    copy.defaultSoundId = src->defaultSoundId;
    auto next = state_;
    next.tracks.push_back (std::move (copy));
    next.activeTrackIndex = idx;
    next.cursorIndex.reset();
    next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::deleteTrack (int index)
{
    if ((int) state_.tracks.size() <= 1) return;
    if (index < 0 || index >= (int) state_.tracks.size()) return;
    auto next = state_;
    next.tracks.erase (next.tracks.begin() + index);
    // renumber tracks whose labels are still auto-generated "Track N"
    static const std::regex autoLabel ("Track [0-9]+");
    for (int i = 0; i < (int) next.tracks.size(); ++i)
        if (std::regex_match (next.tracks[(size_t) i].label, autoLabel))
            next.tracks[(size_t) i].label = "Track " + std::to_string (i + 1);
    next.activeTrackIndex = std::min (next.activeTrackIndex, (int) next.tracks.size() - 1);
    next.cursorIndex.reset();
    emit (std::move (next));
}

void TrackBuilder::setActiveTrack (int index)
{
    if (index < 0 || index >= (int) state_.tracks.size()) return;
    auto next = state_;
    next.activeTrackIndex = index;
    next.cursorIndex.reset();
    next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::setTrackMuted (int index, bool muted)
{
    updateTrack (index, [muted] (const TrackDraft& d) { auto c = d; c.muted = muted; return c; });
    if (transport_ != nullptr
        && index >= 0 && index < (int) state_.tracks.size())
    {
        const auto& d = state_.tracks[(size_t) index];
        transport_->setTrackFlags (d.id, d.muted, d.soloed);
    }
}
void TrackBuilder::setTrackSoloed (int index, bool soloed)
{
    updateTrack (index, [soloed] (const TrackDraft& d) { auto c = d; c.soloed = soloed; return c; });
    if (transport_ != nullptr
        && index >= 0 && index < (int) state_.tracks.size())
    {
        const auto& d = state_.tracks[(size_t) index];
        transport_->setTrackFlags (d.id, d.muted, d.soloed);
    }
}
void TrackBuilder::setTrackLabel (int index, const std::string& label)
{
    updateTrack (index, [&label] (const TrackDraft& d) { auto c = d; c.label = label; return c; });
}
void TrackBuilder::setTrackDefaultSound (int index, const std::string& soundId)
{
    updateTrack (index, [&soundId] (const TrackDraft& d) { auto c = d; c.defaultSoundId = soundId; return c; });
}
void TrackBuilder::clearTrackDefaultSound (int index)
{
    updateTrack (index, [] (const TrackDraft& d) { auto c = d; c.defaultSoundId.reset(); return c; });
}

void TrackBuilder::setBpm (double bpm)
{
    if (bpm <= 0) return;
    auto next = state_;
    next.bpm = std::clamp (bpm, 1.0, 999.0);
    emit (std::move (next));
}

void TrackBuilder::toggleDenomMode()
{
    if (! state_.canEdit()) return;
    auto next = state_;
    if      (state_.inputMode.kind == InputMode::Kind::Denom)  next.inputMode = InputMode::normal();
    else if (state_.inputMode.kind == InputMode::Kind::Normal) next.inputMode = InputMode::denom();
    else return;
    emit (std::move (next));
}

void TrackBuilder::setDenom (int n)
{
    if (n <= 0) return;
    updateActiveTrack ([n] (const TrackDraft& d) { auto c = d; c.denom = n; return c; });
    auto next = state_;
    next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::enterBeat (int numerator)
{
    if (! state_.canEnterBeats() || numerator <= 0) return;
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    const std::string soundId = t->defaultSoundId.has_value() ? *t->defaultSoundId : defaultSoundId_;
    TrackItem::Beat b;
    b.displayNum   = numerator;
    b.displayDenom = t->denom;
    b.soundId      = soundId;
    insertItem (TrackItem (std::move (b)));
}

void TrackBuilder::replaceBeat (int numerator)
{
    if (numerator <= 0) return;
    if (! state_.cursorIndex.has_value()) return;
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    const int idx = *state_.cursorIndex;
    if (idx < 0 || idx >= (int) t->items.size()) return;
    const auto* b = t->items[(size_t) idx].getIf<TrackItem::Beat>();
    if (b == nullptr) return;
    auto newItems = t->items;
    auto copy = *b;
    copy.displayNum   = numerator;
    copy.displayDenom = t->denom;
    newItems[(size_t) idx] = TrackItem (copy);
    updateActiveTrack ([&newItems] (const TrackDraft& d) { auto c = d; c.items = newItems; return c; });
    auto next = state_;
    next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::setBeatSound (int beatIndex, const std::string& soundId)
{
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    if (beatIndex < 0 || beatIndex >= (int) t->items.size()) return;
    const auto* b = t->items[(size_t) beatIndex].getIf<TrackItem::Beat>();
    if (b == nullptr) return;
    auto newItems = t->items;
    auto copy = *b; copy.soundId = soundId;
    newItems[(size_t) beatIndex] = TrackItem (copy);
    updateActiveTrack ([&newItems] (const TrackDraft& d) { auto c = d; c.items = newItems; return c; });
}

void TrackBuilder::updateBeatAt (int index,
                                 const std::function<TrackItem::Beat (const TrackItem::Beat&)>& update)
{
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    if (index < 0 || index >= (int) t->items.size()) return;
    const auto* b = t->items[(size_t) index].getIf<TrackItem::Beat>();
    if (b == nullptr) return;
    auto newItems = t->items;
    newItems[(size_t) index] = TrackItem (update (*b));
    updateActiveTrack ([&newItems] (const TrackDraft& d) { auto c = d; c.items = newItems; return c; });
}

void TrackBuilder::openBracket()
{
    if (! state_.canOpenBracket()) return;
    insertItem (TrackItem (TrackItem::BracketOpen {}));
}

void TrackBuilder::closeBracket()
{
    if (! state_.canCloseBracket()) return;
    insertItem (TrackItem (TrackItem::BracketClose {}));
}

void TrackBuilder::toggleRepeatMode()
{
    if (! state_.canEdit()) return;
    auto next = state_;
    if (state_.inputMode.kind == InputMode::Kind::RepeatCount)
        next.inputMode = InputMode::normal();
    else if (state_.inputMode.kind == InputMode::Kind::Normal)
    {
        if (! state_.canSetRepeat()) return;
        next.inputMode = InputMode::repeatCount();
    }
    else return;
    emit (std::move (next));
}

void TrackBuilder::repeatDigit (int n)
{
    if (state_.inputMode.kind != InputMode::Kind::RepeatCount) return;
    if (n < 1 || n > 9) return;
    commitRepeatInternal (n);
}

void TrackBuilder::setRepeatCustom (int n)
{
    if (n < 1) return;
    commitRepeatInternal (n);
}

std::optional<int> TrackBuilder::findRelevantClose (const TrackDraft& t) const
{
    int searchIdx = (int) t.items.size() - 1;
    while (searchIdx >= 0)
    {
        const auto& it = t.items[(size_t) searchIdx];
        if (it.isBracketClose()) return searchIdx;
        if (it.isRepeat() || it.isModulation() || it.isSetBpm()) { --searchIdx; continue; }
        return std::nullopt;
    }
    return std::nullopt;
}

void TrackBuilder::setRepeatInfinite()
{
    const auto* tp = state_.activeTrack();
    if (tp == nullptr) return;
    const auto& t = *tp;

    // case 1: editing an existing repeat → replace
    const auto* cur = state_.cursorItem();
    if (cur != nullptr)
        if (const auto* r = cur->getIf<TrackItem::Repeat>(); r != nullptr && ! r->isInfinite())
        {
            replaceRepeatInfinite();
            return;
        }

    // case 2: inserting new infinite after ]
    const auto close = findRelevantClose (t);
    if (! close.has_value()) return;
    for (int i = *close + 1; i < (int) t.items.size(); ++i)
    {
        const auto& it = t.items[(size_t) i];
        if (! (it.isRepeat() || it.isModulation() || it.isSetBpm())) return;
    }
    const auto open = t.matchingOpenIndex (*close);
    if (! open.has_value()) return;
    for (int i = *open + 1; i < *close; ++i)
        if (t.items[(size_t) i].isModulation()) return;
    commitRepeatInternal (TrackItem::Repeat::INFINITE_COUNT);
}

void TrackBuilder::replaceRepeat (int count)
{
    if (! state_.cursorIndex.has_value()) return;
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    const int idx = *state_.cursorIndex;
    if (idx < 0 || idx >= (int) t->items.size()) return;
    if (! t->items[(size_t) idx].isRepeat()) return;
    auto newItems = t->items;
    newItems[(size_t) idx] = TrackItem (TrackItem::Repeat { count });
    updateActiveTrack ([&newItems] (const TrackDraft& d) { auto c = d; c.items = newItems; return c; });
    auto next = state_;
    next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::replaceRepeatInfinite()
{
    if (! state_.cursorIndex.has_value()) return;
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    const int idx = *state_.cursorIndex;
    if (idx < 0 || idx >= (int) t->items.size()) return;
    if (! t->items[(size_t) idx].isRepeat()) return;
    const int closeIdx = idx - 1;
    if (closeIdx < 0 || ! t->items[(size_t) closeIdx].isBracketClose()) return;
    const auto open = t->matchingOpenIndex (closeIdx);
    if (! open.has_value()) return;
    for (int i = *open + 1; i < closeIdx; ++i)
        if (t->items[(size_t) i].isModulation()) return;
    for (int i = idx + 1; i < (int) t->items.size(); ++i)
    {
        const auto& it = t->items[(size_t) i];
        if (! (it.isModulation() || it.isSetBpm())) return;
    }
    auto newItems = t->items;
    newItems[(size_t) idx] = TrackItem (TrackItem::Repeat { TrackItem::Repeat::INFINITE_COUNT });
    updateActiveTrack ([&newItems] (const TrackDraft& d) { auto c = d; c.items = newItems; return c; });
    auto next = state_;
    next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::commitRepeatInternal (int count)
{
    if (state_.activeTrack() == nullptr) return;
    auto next = state_;
    auto& track = next.tracks[(size_t) next.activeTrackIndex];
    const int idx = insertionPoint();
    track.items.insert (track.items.begin() + idx, TrackItem (TrackItem::Repeat { count }));
    next.cursorIndex = idx;
    next.inputMode   = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::commitModulation (int p, int q)
{
    if (! state_.canInsertTempo() || p <= 0 || q <= 0) return;
    insertItem (TrackItem (TrackItem::Modulation { p, q }));
}

void TrackBuilder::commitSetBpm (double bpm)
{
    if (! state_.canInsertTempo() || bpm <= 0) return;
    insertItem (TrackItem (TrackItem::SetBpm { std::clamp (bpm, 1.0, 999.0) }));
}

void TrackBuilder::replaceModulation (int index, int p, int q)
{
    if (p <= 0 || q <= 0) return;
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    if (index < 0 || index >= (int) t->items.size()) return;
    if (! t->items[(size_t) index].isModulation()) return;
    auto newItems = t->items;
    newItems[(size_t) index] = TrackItem (TrackItem::Modulation { p, q });
    updateActiveTrack ([&newItems] (const TrackDraft& d) { auto c = d; c.items = newItems; return c; });
    auto next = state_;
    next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::replaceSetBpm (int index, double bpm)
{
    if (bpm <= 0) return;
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    if (index < 0 || index >= (int) t->items.size()) return;
    if (! t->items[(size_t) index].isSetBpm()) return;
    auto newItems = t->items;
    newItems[(size_t) index] = TrackItem (TrackItem::SetBpm { std::clamp (bpm, 1.0, 999.0) });
    updateActiveTrack ([&newItems] (const TrackDraft& d) { auto c = d; c.items = newItems; return c; });
    auto next = state_;
    next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::toggleEditMode()
{
    if (! state_.canEdit()) return;
    if (state_.inputMode.kind == InputMode::Kind::Edit)
    {
        auto next = state_;
        next.inputMode = InputMode::normal();
        emit (std::move (next));
        return;
    }
    if (state_.inputMode.kind == InputMode::Kind::Normal)
    {
        const auto* item = state_.cursorItem();
        if (item != nullptr && (item->isModulation() || item->isSetBpm())) return;
        auto next = state_;
        next.inputMode = InputMode::edit (true);
        emit (std::move (next));
    }
}

bool TrackBuilder::cursorIsModulation() const
{
    const auto* item = state_.cursorItem();
    return item != nullptr && (item->isModulation() || item->isSetBpm());
}

void TrackBuilder::editDigit (int n)
{
    if (state_.inputMode.kind != InputMode::Kind::Edit) return;
    if (! state_.cursorIndex.has_value()) return;
    const auto* t = state_.activeTrack();
    if (t == nullptr) return;
    const int idx = *state_.cursorIndex;
    if (idx < 0 || idx >= (int) t->items.size()) return;

    auto newItems = t->items;
    const auto& item = newItems[(size_t) idx];

    if (const auto* b = item.getIf<TrackItem::Beat>())
    {
        auto copy = *b;
        copy.displayNum   = n;
        copy.displayDenom = t->denom;
        newItems[(size_t) idx] = TrackItem (copy);
        updateActiveTrack ([&newItems] (const TrackDraft& d) { auto c = d; c.items = newItems; return c; });
        auto next = state_; next.inputMode = InputMode::normal();
        emit (std::move (next));
        return;
    }
    if (const auto* r = item.getIf<TrackItem::Repeat>())
    {
        if (n < 1 || n > 9) return;
        auto copy = *r; copy.count = n;
        newItems[(size_t) idx] = TrackItem (copy);
        updateActiveTrack ([&newItems] (const TrackDraft& d) { auto c = d; c.items = newItems; return c; });
        auto next = state_; next.inputMode = InputMode::normal();
        emit (std::move (next));
        return;
    }
    if (const auto* sb = item.getIf<TrackItem::SetBpm>())
    {
        const bool firstDigit = state_.inputMode.editFirstDigit;
        const double newBpm = firstDigit
            ? (double) n
            : std::min (((int) sb->bpm) * 10 + n, 999) * 1.0;
        auto copy = *sb; copy.bpm = newBpm;
        newItems[(size_t) idx] = TrackItem (copy);
        updateActiveTrack ([&newItems] (const TrackDraft& d) { auto c = d; c.items = newItems; return c; });
        auto next = state_; next.inputMode = InputMode::edit (false);
        emit (std::move (next));
        return;
    }
}

void TrackBuilder::exitEditMode()
{
    if (! state_.isEditMode()) return;
    auto next = state_;
    next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::deleteAt (int index)
{
    const auto* tp = state_.activeTrack();
    if (tp == nullptr) return;
    const auto& t = *tp;
    if (index < 0 || index >= (int) t.items.size()) return;

    std::vector<int> toRemove { index };

    if (pairedBracketDelete_)
    {
        const auto& item = t.items[(size_t) index];
        if (item.isBracketOpen())
        {
            const auto closeIdx = t.matchingCloseIndex (index);
            if (closeIdx.has_value())
            {
                toRemove.push_back (*closeIdx);
                int i = *closeIdx + 1;
                while (i < (int) t.items.size())
                {
                    const auto& it = t.items[(size_t) i];
                    if (! (it.isRepeat() || it.isModulation() || it.isSetBpm())) break;
                    toRemove.push_back (i);
                    ++i;
                }
            }
        }
        else if (item.isBracketClose())
        {
            const auto openIdx = t.matchingOpenIndex (index);
            if (openIdx.has_value()) toRemove.push_back (*openIdx);
            int i = index + 1;
            while (i < (int) t.items.size())
            {
                const auto& it = t.items[(size_t) i];
                if (! (it.isRepeat() || it.isModulation() || it.isSetBpm())) break;
                toRemove.push_back (i);
                ++i;
            }
        }
    }

    std::sort (toRemove.begin(), toRemove.end());
    toRemove.erase (std::unique (toRemove.begin(), toRemove.end()), toRemove.end());

    auto next = state_;
    auto& track = next.tracks[(size_t) next.activeTrackIndex];
    for (auto it = toRemove.rbegin(); it != toRemove.rend(); ++it)
        track.items.erase (track.items.begin() + *it);

    const int maxRemoved = toRemove.back();
    if (track.items.empty())                next.cursorIndex.reset();
    else if (maxRemoved < (int) track.items.size())
                                            next.cursorIndex = maxRemoved;
    else                                    next.cursorIndex = (int) track.items.size() - 1;

    next.inputMode = InputMode::normal();
    emit (std::move (next));
}

void TrackBuilder::deleteLast()
{
    const auto* t = state_.activeTrack();
    if (t == nullptr || t->items.empty()) return;
    deleteAt ((int) t->items.size() - 1);
}

void TrackBuilder::deleteSelected()
{
    if (! state_.cursorIndex.has_value()) return;
    deleteAt (*state_.cursorIndex);
}

void TrackBuilder::play()
{
    if (state_.isPlaying()) return;
    auto errors = state_.buildErrors();
    if (! errors.empty())
    {
        lastBuildErrors_ = std::move (errors);
        if (onBuildErrors_) onBuildErrors_ (lastBuildErrors_);
        return;
    }
    lastBuildErrors_.clear();
    if (onBuildErrors_) onBuildErrors_ (lastBuildErrors_);

    if (transport_ == nullptr)
    {
        auto next = state_;
        next.playbackState = PlaybackState::Playing;
        next.inputMode     = InputMode::normal();
        emit (std::move (next));
        return;
    }

    std::vector<InterpretResult> results;
    results.reserve (state_.tracks.size());
    for (const auto& d : state_.tracks)
    {
        auto r = interpretTrackDraft (d, state_.bpm, defaultSoundId_);
        r.muted  = d.muted;
        r.soloed = d.soloed;
        results.push_back (std::move (r));
    }
    lastBuildResults_ = results;
    if (onBuildResults_) onBuildResults_ (lastBuildResults_);

    transport_->updateTracks (results);
    transport_->start();

    auto next = state_;
    next.playbackState = PlaybackState::Playing;
    next.inputMode     = InputMode::normal();
    next.cursorIndex.reset();
    next.runningBpm.reset();
    emit (std::move (next));
}

void TrackBuilder::stop()
{
    if (state_.isStopped()) return;
    if (transport_ != nullptr) transport_->stop();
    auto next = state_;
    next.playbackState = PlaybackState::Stopped;
    next.runningBpm.reset();
    emit (std::move (next));
}

void TrackBuilder::togglePlayStop()
{
    if (state_.isPlaying()) stop();
    else                    play();
}

void TrackBuilder::restoreState (const TrackBuilderState& s)
{
    auto next = s;
    next.playbackState = PlaybackState::Stopped;
    next.inputMode     = InputMode::normal();
    next.cursorIndex.reset();
    next.runningBpm.reset();
    emit (std::move (next));
}

void TrackBuilder::clearBuildErrors()
{
    lastBuildErrors_.clear();
    if (onBuildErrors_) onBuildErrors_ (lastBuildErrors_);
}

} // namespace rhythm
