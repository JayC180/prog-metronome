#pragma once

#include "UiHelpers.h"
#include "../builder/TrackBuilder.h"
#include "../builder/TrackBuilderState.h"
#include <juce_gui_basics/juce_gui_basics.h>

namespace rhythm
{

// One row in the track list. Self-paints the scrollable item strip with
// state-driven colouring per TrackItem. Click anywhere on the row to
// activate the track; click an item to set the cursor.
class TrackRowComponent : public juce::Component
{
public:
    TrackRowComponent (TrackBuilder& builder, int trackIndex);
    ~TrackRowComponent() override;

    void setTrackIndex (int idx) { trackIndex_ = idx; syncToState(); }
    int  trackIndex() const      { return trackIndex_; }

    // Update from current state. Cheap — repaints + lays out controls.
    void syncToState();

    // For ensuring the cursor / playhead is visible. The active strip
    // implements its own auto-scroll.
    void scrollIntoView (int itemIndex);

    std::function<void (int /*trackIdx*/)>                     onDeleteTrack;
    std::function<void (int /*trackIdx*/)>                     onChooseDefaultSound;

    void paint (juce::Graphics&) override;
    void resized() override;
    void mouseDown (const juce::MouseEvent&) override;

private:
    class ItemStrip;

    TrackBuilder&             builder_;
    int                       trackIndex_;

    juce::Label               nameLabel_;
    TrackChip                 muteChip_   { "M", RhythmColors::muteColor() };
    TrackChip                 soloChip_   { "S", RhythmColors::soloColor() };
    TrackChip                 soundChip_  { "D", RhythmColors::accent() };
    ChipButton                deleteButton_;
    std::unique_ptr<ItemStrip> strip_;

    const TrackDraft* draft() const;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (TrackRowComponent)
};

// Vertical stack of TrackRowComponents + a footer with Add/Copy.
class TrackListComponent : public juce::Component
{
public:
    explicit TrackListComponent (TrackBuilder& builder);
    ~TrackListComponent() override;

    void syncToState();

    // Forwarded by each row's onChooseDefaultSound callback so MainComponent
    // can pop a sound picker for that track.
    std::function<void (int /*trackIdx*/)>                 onChooseTrackDefaultSound;

    void paint (juce::Graphics&) override;
    void resized() override;

private:
    TrackBuilder&                                          builder_;
    juce::Viewport                                         viewport_;
    juce::Component                                        content_;
    std::vector<std::unique_ptr<TrackRowComponent>>        rows_;
    ChipButton                                             addRowButton_  { "+ add track" };
    ChipButton                                             copyRowButton_ { "copy track" };

    void rebuildRows();

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (TrackListComponent)
};

} // namespace rhythm
