#pragma once

#include "TopBarComponent.h"
#include "TrackRowComponent.h"
#include "BottomPanelComponent.h"
#include "RhythmLookAndFeel.h"
#include "../audio/SoundInfo.h"
#include "../builder/TrackBuilder.h"

#include <juce_gui_basics/juce_gui_basics.h>
#include <vector>

namespace rhythm
{

class MainComponent : public juce::Component,
                      private juce::AsyncUpdater
{
public:
    explicit MainComponent (TrackBuilder& builder);
    ~MainComponent() override;

    void paint (juce::Graphics&) override;
    void resized() override;

private:
    void handleAsyncUpdate() override;
    void rebuildFromState();

    void openBeatSoundPicker();

    TrackBuilder&            builder_;
    std::vector<SoundInfo>   availableSounds_;

    RhythmLookAndFeel     lookAndFeel_;
    TopBarComponent       topBar_;
    TrackListComponent    trackList_;
    BottomPanelComponent  bottomPanel_;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (MainComponent)
};

} // namespace rhythm
