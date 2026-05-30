#pragma once

#include "TrackBuilderState.h"
#include "Interpreter.h"
#include "../scheduler/Transport.h"

#include <functional>
#include <memory>
#include <optional>
#include <string>
#include <vector>

namespace rhythm
{

// Controller around the live TrackBuilderState. Replicates the Kotlin
// TrackBuilder API. Reactive flows in the Kotlin source are translated
// to listener callbacks: register with setOnStateChanged() to be notified
// whenever the state mutates.
class TrackBuilder
{
public:
    explicit TrackBuilder (double initialBpm = 120.0,
                           Transport* transport = nullptr);

    const TrackBuilderState& state() const { return state_; }

    using StateListener = std::function<void (const TrackBuilderState&)>;
    void setOnStateChanged (StateListener cb) { onStateChanged_ = std::move (cb); }

    using BuildErrorListener = std::function<void (const std::vector<std::string>&)>;
    void setOnBuildErrors (BuildErrorListener cb) { onBuildErrors_ = std::move (cb); }

    using BuildResultsListener = std::function<void (const std::vector<InterpretResult>&)>;
    void setOnBuildResults (BuildResultsListener cb) { onBuildResults_ = std::move (cb); }

    const std::vector<InterpretResult>& lastBuildResults() const { return lastBuildResults_; }
    const std::vector<std::string>&     lastBuildErrors()  const { return lastBuildErrors_; }

    void setDefaultSoundId (std::string id) { defaultSoundId_ = std::move (id); }
    const std::string& defaultSoundId() const { return defaultSoundId_; }

    bool pairedBracketDelete() const { return pairedBracketDelete_; }
    void setPairedBracketDelete (bool v) { pairedBracketDelete_ = v; }

    // cursor
    void setCursor (std::optional<int> index);
    void moveCursorPrev();
    void moveCursorNext();

    // tracks
    void addTrack();
    void copyTrack();
    void deleteTrack (int index);
    void setActiveTrack (int index);
    void setTrackMuted  (int index, bool muted);
    void setTrackSoloed (int index, bool soloed);
    void setTrackLabel  (int index, const std::string& label);
    void setTrackDefaultSound (int index, const std::string& soundId);
    void clearTrackDefaultSound (int index);

    // bpm
    void setBpm (double bpm);

    // denom
    void toggleDenomMode();
    void setDenom (int n);

    // beats
    void enterBeat (int numerator);
    void replaceBeat (int numerator);
    void setBeatSound (int beatIndex, const std::string& soundId);
    void updateBeatAt (int index, const std::function<TrackItem::Beat (const TrackItem::Beat&)>& update);

    // brackets
    void openBracket();
    void closeBracket();

    // repeat
    void toggleRepeatMode();
    void repeatDigit (int n);
    void setRepeatCustom (int n);
    void setRepeatInfinite();
    void replaceRepeat (int count);
    void replaceRepeatInfinite();

    // tempo
    void commitModulation (int p, int q);
    void commitSetBpm (double bpm);
    void replaceModulation (int index, int p, int q);
    void replaceSetBpm (int index, double bpm);

    // edit mode
    void toggleEditMode();
    void editDigit (int n);
    void exitEditMode();
    bool cursorIsModulation() const;

    // deletion
    void deleteAt (int index);
    void deleteLast();
    void deleteSelected();

    // playback
    void play();
    void stop();
    void togglePlayStop();

    void restoreState (const TrackBuilderState& s);
    void clearBuildErrors();
    void updateRunningBpm (double bpm) { (void) bpm; /* no-op: pre-computed */ }

    // factory
    static TrackDraft newTrackDraft (int index);

private:
    void emit (TrackBuilderState newState);
    int  insertionPoint() const;
    void insertItem (TrackItem item);
    void updateActiveTrack (const std::function<TrackDraft (const TrackDraft&)>& fn);
    void updateTrack       (int index, const std::function<TrackDraft (const TrackDraft&)>& fn);
    void commitRepeatInternal (int count);

    std::optional<int> findRelevantClose (const TrackDraft& t) const;

    TrackBuilderState              state_;
    Transport*                     transport_ { nullptr };
    std::string                    defaultSoundId_ { "default" };
    bool                           pairedBracketDelete_ { true };

    std::vector<InterpretResult>   lastBuildResults_;
    std::vector<std::string>       lastBuildErrors_;

    StateListener                  onStateChanged_;
    BuildErrorListener             onBuildErrors_;
    BuildResultsListener           onBuildResults_;

    static int                     nextCounter_;
};

} // namespace rhythm
