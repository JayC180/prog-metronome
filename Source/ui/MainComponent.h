#pragma once

#include "TopBarComponent.h"
#include "TrackRowComponent.h"
#include "BottomPanelComponent.h"
#include "RhythmLookAndFeel.h"
#include "../audio/SoundInfo.h"
#include "../builder/TrackBuilder.h"
#include "../builder/TapTempoCalculator.h"
#include "../scheduler/ScheduledEvent.h"

#include <juce_gui_basics/juce_gui_basics.h>
#include <memory>
#include <vector>

class RhythmEngineProcessor;

namespace rhythm
{

class MainComponent : public juce::Component,
                      private juce::AsyncUpdater,
                      private juce::Timer
{
public:
    explicit MainComponent (RhythmEngineProcessor& processor);
    ~MainComponent() override;

    void paint    (juce::Graphics&) override;
    void resized  () override;
    bool keyPressed (const juce::KeyPress&) override;

private:
    void handleAsyncUpdate() override;
    void timerCallback()     override;
    void rebuildFromState();

    void openBeatSoundPicker();
    void openTrackSoundPicker(int trackIdx);
    void openGlobalSoundPicker();

    void loadSettings();
    void saveSettings();

    void newProject();
    void openProject();
    void saveProject();
    void saveProjectAs();
    void renameProject();
    void importSound();
    void confirmIfDirty (std::function<void()> onProceed);
    void updateProjectNameDisplay();
    void rebuildAvailableSounds();
    void loadUserSoundsFromDisk();

    static juce::File autosavePath();
    static juce::File userSoundsDir();

    RhythmEngineProcessor&   processor_;
    TrackBuilder&            builder_;
    TapTempoCalculator       tapTempo_;
    std::vector<SoundInfo>   availableSounds_;
    int                      beatBlockSizeIndex_{1};
    bool                     stackedFractions_{false};

    RhythmLookAndFeel     lookAndFeel_;
    TopBarComponent       topBar_;
    TrackListComponent    trackList_;
    BottomPanelComponent  bottomPanel_;

    std::unique_ptr<juce::FileChooser> fileChooser_;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (MainComponent)
};

} // namespace rhythm
