#pragma once

#include "UiHelpers.h"
#include "../builder/TrackBuilder.h"
#include <juce_gui_basics/juce_gui_basics.h>

namespace rhythm
{

class TopBarComponent : public juce::Component
{
public:
    explicit TopBarComponent (TrackBuilder& builder);
    ~TopBarComponent() override = default;

    std::function<void()> onBpmClicked;
    std::function<void()> onSettingsClicked;
    std::function<void()> onProjectNameClicked;

    void syncToState();
    void setProjectName (const juce::String& name);

    void paint (juce::Graphics&) override;
    void resized() override;
    void mouseDown (const juce::MouseEvent&) override;

private:
    void paintBpmCard       (juce::Graphics&);
    void paintPlayStopButton(juce::Graphics&);

    TrackBuilder& builder_;
    ChipButton    settingsButton_  { juce::String::fromUTF8 (u8"⚙") };

    void paintProjectName (juce::Graphics&);

    // BPM card and play button are drawn directly inside paint() because their
    // visuals are highly bespoke (live-coloured BPM, triangle / square icons).
    juce::String         projectName_ { "Untitled" };
    juce::Rectangle<int> bpmCardArea_;
    juce::Rectangle<int> projectNameArea_;
    juce::Rectangle<int> playStopArea_;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (TopBarComponent)
};

} // namespace rhythm
