#pragma once

#include "TrackDraft.h"
#include <optional>
#include <string>
#include <variant>
#include <vector>

namespace rhythm
{

enum class PlaybackState { Stopped, Playing };

// Input mode discriminator.
struct InputMode
{
    enum class Kind { Normal, Denom, RepeatCount, Edit };
    Kind kind { Kind::Normal };
    // optional payloads
    std::optional<int> pendingRepeat {};      // RepeatCount.pending
    bool               editFirstDigit { true };// Edit.firstDigit

    static InputMode normal()                    { return { Kind::Normal,      std::nullopt, true }; }
    static InputMode denom()                     { return { Kind::Denom,       std::nullopt, true }; }
    static InputMode repeatCount (std::optional<int> p = std::nullopt)
                                                  { return { Kind::RepeatCount, p,            true }; }
    static InputMode edit (bool firstDigit = true){ return { Kind::Edit,        std::nullopt, firstDigit }; }
};

class TrackBuilderState
{
public:
    std::vector<TrackDraft> tracks;
    int                     activeTrackIndex { 0 };
    double                  bpm              { 120.0 };
    InputMode               inputMode        { InputMode::normal() };
    PlaybackState           playbackState    { PlaybackState::Stopped };
    std::optional<int>      cursorIndex      {};
    std::optional<double>   runningBpm       {};

    const TrackDraft* activeTrack() const
    {
        if (activeTrackIndex < 0 || activeTrackIndex >= (int) tracks.size()) return nullptr;
        return &tracks[(size_t) activeTrackIndex];
    }
    TrackDraft* activeTrack()
    {
        if (activeTrackIndex < 0 || activeTrackIndex >= (int) tracks.size()) return nullptr;
        return &tracks[(size_t) activeTrackIndex];
    }

    bool isPlaying() const { return playbackState == PlaybackState::Playing; }
    bool isStopped() const { return playbackState == PlaybackState::Stopped; }
    bool canEdit()   const { return ! isPlaying(); }

    bool isDenomMode()  const { return inputMode.kind == InputMode::Kind::Denom; }
    bool isRepeatMode() const { return inputMode.kind == InputMode::Kind::RepeatCount; }
    bool isEditMode()   const { return inputMode.kind == InputMode::Kind::Edit; }

    int activeDenom() const
    {
        const auto* t = activeTrack();
        return t != nullptr ? t->denom : 4;
    }

    const TrackItem* cursorItem() const
    {
        if (! cursorIndex.has_value()) return nullptr;
        const auto* t = activeTrack();
        if (t == nullptr) return nullptr;
        const int i = *cursorIndex;
        if (i < 0 || i >= (int) t->items.size()) return nullptr;
        return &t->items[(size_t) i];
    }

    int insertionIndex() const
    {
        if (cursorIndex.has_value()) return *cursorIndex + 1;
        const auto* t = activeTrack();
        return t != nullptr ? (int) t->items.size() : 0;
    }

    double displayBpm() const
    {
        if (isPlaying() && runningBpm.has_value()) return *runningBpm;
        return bpm;
    }

    bool canOpenBracket()  const;
    bool canCloseBracket() const;
    bool canSetRepeat()    const;
    bool canSetInfiniteRepeat() const;
    bool canInsertTempo()  const;
    bool canEnterBeats()   const;

    // available for beat, repeat (non-infinite), mod, setbpm
    bool canEditItem() const;

    std::vector<std::string> buildErrors() const;

private:
    bool insertionBlockedByInfinite() const;
    std::optional<int> findRelevantClose() const;
    bool hasRepeatAfterClose (const TrackDraft& t, int closeIndex) const;
};

} // namespace rhythm
