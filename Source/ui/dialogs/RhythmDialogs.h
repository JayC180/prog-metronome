#pragma once

#include "../UiHelpers.h"
#include "../../audio/SoundInfo.h"
#include <juce_gui_basics/juce_gui_basics.h>
#include <functional>
#include <vector>

namespace rhythm
{

// Shared dialog chrome — a rounded panel with a title, hint, content area,
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

    // Sub-classes implement layout in resized() for the content area; default
    // layout is: title (auto) + hint (auto) + content (flex) + buttons (auto)
    virtual void layoutContent (juce::Rectangle<int> /*contentBounds*/) {}

    int preferredWidth { 360 };

private:
    juce::String                                  title_;
    juce::String                                  hint_;
    juce::Component                               contentHost_;
    std::vector<std::unique_ptr<ChipButton>>      actionButtons_;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (DialogPanel)
};

// BPM input — used both for global tempo and for editing existing SetBpm items.
class BpmInputDialog : public DialogPanel
{
public:
    BpmInputDialog (double currentBpm, std::function<void (double)> onConfirm);
    void layoutContent (juce::Rectangle<int>) override;

private:
    juce::TextEditor field_;
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

// =bpm dialog — for inserting or editing a SetBpm marker inside a track.
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

// Generic single-column list picker — used by SoundPicker and ThemePicker.
// Each entry has a label and an opaque payload identifier.
class ListPickerDialog : public DialogPanel
{
public:
    struct Entry
    {
        juce::String label;
        juce::String badge;       // optional "user" / "built-in" tag, may be empty
        juce::Colour badgeColour; // text colour for badge
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

// Sound picker — a scrollable list of SoundInfo entries with the current
// selection highlighted. Clicking an entry fires onSelect with that sound's id.
class SoundPickerDialog : public DialogPanel
{
public:
    SoundPickerDialog (std::vector<SoundInfo> sounds,
                       std::optional<std::string> currentSoundId,
                       std::function<void (const std::string&)> onSelect);
    ~SoundPickerDialog() override;
    void layoutContent (juce::Rectangle<int>) override;

private:
    class Row;
    juce::Viewport                          viewport_;
    juce::Component                         listContent_;
    std::vector<std::unique_ptr<Row>>       rows_;
};

// Helper: shows a DialogPanel inside a DialogWindow on top of `parent`.
// Returns nothing — the panel owns its callback and self-destructs when closed.
void showRhythmDialog (juce::Component* parent,
                       std::unique_ptr<DialogPanel> panel);

} // namespace rhythm
