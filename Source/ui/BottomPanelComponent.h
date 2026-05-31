#pragma once

#include "UiHelpers.h"
#include "../builder/TrackBuilder.h"
#include <juce_gui_basics/juce_gui_basics.h>

namespace rhythm
{

class BottomPanelComponent : public juce::Component
{
public:
    explicit BottomPanelComponent (TrackBuilder& builder);
    ~BottomPanelComponent() override = default;

    void syncToState();

    // External hooks so MainComponent can pop dialogs.
    std::function<void()> onMmRequested;
    std::function<void()> onSetBpmRequested;
    std::function<void()> onCustomBeatRequested;
    std::function<void()> onCustomDenomRequested;
    std::function<void()> onRepeatCustomRequested;
    std::function<void()> onChangeBeatSound;

    void paint (juce::Graphics&) override;
    void resized() override;

private:
    void rebuildToolbar();
    void rebuildNumpad();

    void onNumKey  (int n);
    void onCustom();
    void onEditToggle();
    void onBackspace();

    TrackBuilder& builder_;

    // toolbar row
    ChipButton navPrevButton_;
    ChipButton denomButton_;
    ChipButton openBracketButton_;
    ChipButton closeBracketButton_;
    ChipButton repeatButton_;
    ChipButton mmButton_;
    ChipButton setBpmButton_;
    ChipButton navNextButton_;

    // edit panel
    juce::Label onOffLabel_;
    juce::Label onOffStatus_;
    juce::Label volumeLabel_;
    juce::Slider volumeSlider_;
    juce::Label volumePercentLabel_;
    juce::Label soundLabel_;
    juce::Label soundValueLabel_;
    ChipButton  changeSoundButton_  { juce::String::fromUTF8 (u8"change ›") };

    // numpad
    juce::Label                                  hintLabel_;
    std::array<std::unique_ptr<NumKeyButton>, 9> numKeys_ {};
    NumKeyButton                                 customKey_   { "custom" };
    NumKeyButton                                 editKey_     { juce::String::fromUTF8 (u8"✎") };
    NumKeyButton                                 backspaceKey_{ juce::String::fromUTF8 (u8"⌫") };

    juce::Rectangle<int> toolbarArea_, editArea_, numpadArea_;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (BottomPanelComponent)
};

} // namespace rhythm
