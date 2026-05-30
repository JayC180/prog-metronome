#include "MainComponent.h"
#include "dialogs/RhythmDialogs.h"

namespace rhythm
{

MainComponent::MainComponent (TrackBuilder& builder)
    : builder_ (builder),
      availableSounds_ {
          { "default", "default", false },
          { "hi",      "hi click", false },
          { "lo",      "lo click", false },
          { "accent",  "accent",   false },
          { "hat",     "hat",      false },
      },
      topBar_      (builder),
      trackList_   (builder),
      bottomPanel_ (builder)
{
    setLookAndFeel (&lookAndFeel_);

    addAndMakeVisible (topBar_);
    addAndMakeVisible (trackList_);
    addAndMakeVisible (bottomPanel_);

    builder_.setOnStateChanged ([this] (const TrackBuilderState&) { triggerAsyncUpdate(); });

    topBar_.onBpmClicked = [this]
    {
        auto cb = [this] (double v) { builder_.setBpm (v); };
        showRhythmDialog (this, std::make_unique<BpmInputDialog> (builder_.state().bpm, cb));
    };
    topBar_.onSettingsClicked = [this]
    {
        std::vector<ListPickerDialog::Entry> entries;
        for (const auto& t : BuiltInThemes::all())
            entries.push_back ({ juce::String (t.name), {}, {}, juce::String (t.name) });

        showRhythmDialog (this,
            std::make_unique<ListPickerDialog> (
                juce::String ("Theme"),
                entries,
                juce::String (RhythmColors::active().name),
                [this] (const juce::String& name)
                {
                    for (const auto& t : BuiltInThemes::all())
                        if (juce::String (t.name) == name) { RhythmColors::setActive (t); break; }
                    rebuildFromState();
                    repaint();
                }));
    };

    bottomPanel_.onMmRequested = [this]
    {
        const auto* item = builder_.state().cursorItem();
        std::optional<int> p, q;
        if (item != nullptr)
            if (const auto* m = item->getIf<TrackItem::Modulation>()) { p = m->p; q = m->q; }
        showRhythmDialog (this,
            std::make_unique<MmDialog> (p, q, [this, idx = builder_.state().cursorIndex,
                                                editing = item != nullptr && item->isModulation()]
                                              (int pp, int qq)
            {
                if (editing && idx.has_value()) builder_.replaceModulation (*idx, pp, qq);
                else                            builder_.commitModulation (pp, qq);
            }));
    };

    bottomPanel_.onSetBpmRequested = [this]
    {
        const auto* item = builder_.state().cursorItem();
        std::optional<double> initial;
        bool editing = false;
        if (item != nullptr)
            if (const auto* sb = item->getIf<TrackItem::SetBpm>()) { initial = sb->bpm; editing = true; }
        showRhythmDialog (this,
            std::make_unique<SetBpmDialog> (builder_.state().bpm, initial,
                [this, idx = builder_.state().cursorIndex, editing] (double v)
                {
                    if (editing && idx.has_value()) builder_.replaceSetBpm (*idx, v);
                    else                            builder_.commitSetBpm (v);
                }));
    };

    bottomPanel_.onCustomBeatRequested = [this]
    {
        const bool editingBeat = builder_.state().isEditMode()
                              && builder_.state().cursorItem() != nullptr
                              && builder_.state().cursorItem()->isBeat();
        showRhythmDialog (this,
            std::make_unique<CustomNumberDialog> ("Beat value",
                                                  "numerator — must be greater than 0",
                [this, editingBeat] (int n)
                {
                    if (editingBeat) builder_.replaceBeat (n);
                    else             builder_.enterBeat (n);
                }));
    };

    bottomPanel_.onCustomDenomRequested = [this]
    {
        showRhythmDialog (this,
            std::make_unique<CustomNumberDialog> ("Subdivision",
                                                  "denominator — e.g. 4 = quarter, 8 = eighth",
                [this] (int n) { builder_.setDenom (n); }));
    };

    bottomPanel_.onRepeatCustomRequested = [this]
    {
        showRhythmDialog (this,
            std::make_unique<RepeatDialog> (
                [this, editing = builder_.state().isEditMode()
                                && builder_.state().cursorItem() != nullptr
                                && builder_.state().cursorItem()->isRepeat()] (int count)
                {
                    if (count == TrackItem::Repeat::INFINITE_COUNT) builder_.setRepeatInfinite();
                    else if (editing)                              builder_.replaceRepeat (count);
                    else                                           builder_.setRepeatCustom (count);
                }));
    };

    bottomPanel_.onChangeBeatSound = [this] { openBeatSoundPicker(); };
    trackList_.onChooseTrackDefaultSound = [this] (int idx) { openTrackDefaultSoundPicker (idx); };

    rebuildFromState();
}

void MainComponent::openBeatSoundPicker()
{
    const auto& s = builder_.state();
    if (! s.cursorIndex.has_value()) return;
    const auto* t = s.activeTrack();
    if (t == nullptr) return;
    const int idx = *s.cursorIndex;
    if (idx < 0 || idx >= (int) t->items.size()) return;
    const auto* beat = t->items[(size_t) idx].getIf<TrackItem::Beat>();
    if (beat == nullptr) return;

    showRhythmDialog (this,
        std::make_unique<SoundPickerDialog> (
            availableSounds_,
            beat->soundId,
            [this, idx] (const std::string& soundId)
            {
                builder_.setBeatSound (idx, soundId);
            }));
}

void MainComponent::openTrackDefaultSoundPicker (int trackIndex)
{
    const auto& s = builder_.state();
    if (trackIndex < 0 || trackIndex >= (int) s.tracks.size()) return;
    const auto& t = s.tracks[(size_t) trackIndex];

    showRhythmDialog (this,
        std::make_unique<SoundPickerDialog> (
            availableSounds_,
            t.defaultSoundId,
            [this, trackIndex] (const std::string& soundId)
            {
                builder_.setTrackDefaultSound (trackIndex, soundId);
            }));
}

MainComponent::~MainComponent()
{
    builder_.setOnStateChanged (nullptr);
    setLookAndFeel (nullptr);
}

void MainComponent::handleAsyncUpdate() { rebuildFromState(); }

void MainComponent::rebuildFromState()
{
    topBar_.syncToState();
    trackList_.syncToState();
    bottomPanel_.syncToState();
}

void MainComponent::paint (juce::Graphics& g)
{
    g.fillAll (RhythmColors::bg0());
}

void MainComponent::resized()
{
    auto bounds = getLocalBounds();
    topBar_.setBounds (bounds.removeFromTop (52));

    if (bounds.getWidth() >= 600)
    {
        // Landscape: tracks left, bottom panel right.
        const int rightW = juce::jmin (bounds.getWidth() * 5 / 12, 420);
        bottomPanel_.setBounds (bounds.removeFromRight (rightW));
        trackList_.setBounds (bounds);
    }
    else
    {
        bottomPanel_.setBounds (bounds.removeFromBottom (340));
        trackList_.setBounds (bounds);
    }
}

} // namespace rhythm
