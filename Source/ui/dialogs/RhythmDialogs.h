#pragma once

#include "../UiHelpers.h"
#include "../../audio/SoundInfo.h"
#include "../../builder/TapTempoCalculator.h"
#include <juce_gui_basics/juce_gui_basics.h>
#include <array>
#include <functional>
#include <memory>
#include <vector>

namespace rhythm
{

// Shared dialog chrome - a rounded panel with a title, hint, content area,
// and a horizontal row of DialogButtons.
class DialogPanel : public juce::Component
{
public:
    explicit DialogPanel (juce::String title, juce::String hint = {});
    ~DialogPanel() override = default;

    juce::Component& content() { return contentHost_; }

    void addAction (juce::String label,
                    juce::Colour bg, juce::Colour border, juce::Colour text,
                    std::function<void()> cb);

    void paint (juce::Graphics&) override;
    void resized() override;

    virtual void layoutContent (juce::Rectangle<int> /*contentBounds*/) {}

    int preferredWidth  { 360 };
    int preferredHeight { 200 };

private:
    juce::String                                  title_;
    juce::String                                  hint_;
    juce::Component                               contentHost_;
    std::vector<std::unique_ptr<ChipButton>>      actionButtons_;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (DialogPanel)
};

// BPM input - used both for global tempo and for editing existing SetBpm items.
class BpmInputDialog : public DialogPanel
{
public:
    BpmInputDialog (double currentBpm, std::function<void (double)> onConfirm);
    void layoutContent (juce::Rectangle<int>) override;

private:
    juce::TextEditor field_;
    std::array<std::unique_ptr<ChipButton>, 6> nudgeButtons_;
    TapTempoCalculator tapCalc_;
    ChipButton tapButton_{"tap bpm"};
    juce::Label tapHintLabel_;
};

// p/q metric modulation dialog.
class MmDialog : public DialogPanel
{
public:
    MmDialog (std::optional<int> initialP,
              std::optional<int> initialQ,
              std::function<void (int, int)> onConfirm);
    void layoutContent (juce::Rectangle<int>) override;

private:
    juce::TextEditor p_, q_;
    juce::Label      slash_;
};

// =bpm dialog - for inserting or editing a SetBpm marker inside a track.
class SetBpmDialog : public DialogPanel
{
public:
    SetBpmDialog (double currentBpm,
                  std::optional<double> initialBpm,
                  std::function<void (double)> onConfirm);
    void layoutContent (juce::Rectangle<int>) override;

private:
    juce::TextEditor field_;
};

// Repeat count dialog with explicit "∞ forever" action.
class RepeatDialog : public DialogPanel
{
public:
    explicit RepeatDialog (std::function<void (int)> onConfirm);
    void layoutContent (juce::Rectangle<int>) override;

private:
    juce::TextEditor field_;
};

// Generic numeric input dialog (custom beat numerator, custom denominator).
class CustomNumberDialog : public DialogPanel
{
public:
    CustomNumberDialog (juce::String title,
                        juce::String hint,
                        std::function<void (int)> onConfirm);
    void layoutContent (juce::Rectangle<int>) override;

private:
    juce::TextEditor field_;
};

// Generic single-column list picker.
class ListPickerDialog : public DialogPanel
{
public:
    struct Entry
    {
        juce::String label;
        juce::String badge;
        juce::Colour badgeColour;
        juce::String payloadId;
    };

    ListPickerDialog (juce::String title,
                      std::vector<Entry> entries,
                      juce::String currentId,
                      std::function<void (const juce::String&)> onSelect);
    ~ListPickerDialog() override;
    void layoutContent (juce::Rectangle<int>) override;

private:
    class Row;
    juce::Viewport                          viewport_;
    juce::Component                         listContent_;
    std::vector<std::unique_ptr<Row>>       rows_;
};

// Sound picker with optional default-volume slider above the list.
class SoundPickerDialog : public DialogPanel
{
public:
    SoundPickerDialog (std::vector<SoundInfo> sounds,
                       std::optional<std::string> currentSoundId,
                       std::function<void (const std::string&)> onSelect,
                       std::optional<float> currentVolume = std::nullopt,
                       std::function<void (float)> onVolumeChange = nullptr);
    ~SoundPickerDialog() override;
    void layoutContent (juce::Rectangle<int>) override;

private:
    class Row;
    bool                                    hasVolume_{false};
    juce::Label                             volumeLabel_;
    juce::Slider                            volumeSlider_;
    juce::Label                             volumePercent_;
    std::function<void(float)>              onVolumeChange_;
    juce::Viewport                          viewport_;
    juce::Component                         listContent_;
    std::vector<std::unique_ptr<Row>>       rows_;
};

// Scrollable help reference with section headings.
class HelpDialog : public DialogPanel
{
public:
    HelpDialog();
    ~HelpDialog() override;
    void layoutContent (juce::Rectangle<int>) override;

private:
    struct HelpEntry { juce::String title; juce::String body; };
    static std::vector<HelpEntry> makeEntries();

    class ContentComp : public juce::Component
    {
    public:
        explicit ContentComp (std::vector<HelpEntry> entries);
        void relayout (int width);
        void paint (juce::Graphics&) override;
    private:
        std::vector<HelpEntry> entries_;
        std::vector<int>       sectionY_;
        int                    totalH_{0};
        int                    cachedWidth_{0};
    };

    juce::Viewport viewport_;
    ContentComp    content_;
};

// Helper: shows a DialogPanel inside a DialogWindow on top of `parent`.
void showRhythmDialog (juce::Component* parent,
                       std::unique_ptr<DialogPanel> panel);

} // namespace rhythm
