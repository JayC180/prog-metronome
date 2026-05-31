#include "RhythmDialogs.h"

namespace rhythm
{

// ---------- DialogPanel ----------

DialogPanel::DialogPanel (juce::String title, juce::String hint)
    : title_ (std::move (title)), hint_ (std::move (hint))
{
    addAndMakeVisible (contentHost_);
}

void DialogPanel::addAction (juce::String label,
                             juce::Colour bg, juce::Colour border, juce::Colour text,
                             std::function<void()> cb)
{
    auto btn = std::make_unique<ChipButton> (std::move (label));
    btn->setStateColor (ChipButton::StateColor::Custom);
    btn->setColours (bg, border, text);
    btn->setFontSize (12.0f);
    btn->setOnClick (std::move (cb));
    addAndMakeVisible (btn.get());
    actionButtons_.push_back (std::move (btn));
}

void DialogPanel::paint (juce::Graphics& g)
{
    const auto r = getLocalBounds().toFloat().reduced (0.5f);
    g.setColour (RhythmColors::bg2());
    g.fillRoundedRectangle (r, 12.0f);
    g.setColour (RhythmColors::border2());
    g.drawRoundedRectangle (r, 12.0f, 1.0f);

    auto inner = getLocalBounds().reduced (22, 18);
    g.setColour (RhythmColors::textPrimary());
    g.setFont (juce::Font (juce::FontOptions (15.0f, juce::Font::bold)));
    g.drawText (title_, inner.removeFromTop (22), juce::Justification::left, false);

    if (! hint_.isEmpty())
    {
        g.setColour (RhythmColors::textSecondary());
        g.setFont (juce::Font (juce::FontOptions (11.0f)));
        inner.removeFromTop (4);
        g.drawText (hint_, inner.removeFromTop (16), juce::Justification::left, false);
    }
}

void DialogPanel::resized()
{
    auto inner = getLocalBounds().reduced (22, 18);
    inner.removeFromTop (22); // title
    if (! hint_.isEmpty()) inner.removeFromTop (4 + 16);

    inner.removeFromTop (12);
    const int buttonH = 36;
    auto buttonRow = inner.removeFromBottom (buttonH);
    inner.removeFromBottom (10);

    contentHost_.setBounds (inner);
    layoutContent (contentHost_.getLocalBounds());

    const int n = (int) actionButtons_.size();
    if (n > 0)
    {
        const int gap = 8;
        const int btnW = (buttonRow.getWidth() - gap * (n - 1)) / n;
        int x = buttonRow.getX();
        for (auto& b : actionButtons_)
        {
            b->setBounds (x, buttonRow.getY(), btnW, buttonH);
            x += btnW + gap;
        }
    }
}

// ---------- BpmInputDialog ----------

namespace
{
    void styleNumericField (juce::TextEditor& e)
    {
        e.setInputRestrictions (6, "0123456789.");
        e.setIndents (8, 6);
        e.setColour (juce::TextEditor::textColourId,            RhythmColors::textPrimary());
        e.setColour (juce::TextEditor::backgroundColourId,      RhythmColors::bg3());
        e.setColour (juce::TextEditor::outlineColourId,         RhythmColors::border1());
        e.setColour (juce::TextEditor::focusedOutlineColourId,  RhythmColors::accent());
        e.setColour (juce::TextEditor::highlightColourId,       RhythmColors::accent().withAlpha (0.35f));
        e.setColour (juce::CaretComponent::caretColourId,       RhythmColors::accent());
        e.setFont (juce::Font (juce::FontOptions (15.0f)));
    }
}

BpmInputDialog::BpmInputDialog (double currentBpm, std::function<void (double)> onConfirm)
    : DialogPanel ("Set BPM", {})
{
    styleNumericField (field_);
    field_.setText (juce::String ((int) currentBpm), juce::dontSendNotification);
    field_.selectAll();
    content().addAndMakeVisible (field_);

    addAction ("Cancel",
               RhythmColors::bg3(), RhythmColors::border1(), RhythmColors::textMuted(),
               [this] { if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (0); });

    addAction ("OK",
               RhythmColors::accentBg(), RhythmColors::accentBorder(), RhythmColors::accent(),
               [this, onConfirm]
               {
                   const double v = field_.getText().getDoubleValue();
                   if (v > 0.0 && onConfirm) onConfirm (v);
                   if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (1);
               });
}

void BpmInputDialog::layoutContent (juce::Rectangle<int> b) { field_.setBounds (b.reduced (0, 4)); }

// ---------- MmDialog ----------

MmDialog::MmDialog (std::optional<int> initialP,
                    std::optional<int> initialQ,
                    std::function<void (int, int)> onConfirm)
    : DialogPanel ("Metric modulation", juce::String::fromUTF8 (u8"new BPM = current BPM × p/q"))
{
    styleNumericField (p_);
    styleNumericField (q_);
    if (initialP.has_value()) p_.setText (juce::String (*initialP), juce::dontSendNotification);
    if (initialQ.has_value()) q_.setText (juce::String (*initialQ), juce::dontSendNotification);
    p_.selectAll();
    slash_.setText ("/", juce::dontSendNotification);
    slash_.setJustificationType (juce::Justification::centred);
    slash_.setFont (juce::Font (juce::FontOptions (18.0f)));
    slash_.setColour (juce::Label::textColourId, RhythmColors::textSecondary());

    content().addAndMakeVisible (p_);
    content().addAndMakeVisible (slash_);
    content().addAndMakeVisible (q_);

    addAction ("Cancel",
               RhythmColors::bg3(), RhythmColors::border1(), RhythmColors::textMuted(),
               [this] { if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (0); });
    addAction ("Insert",
               RhythmColors::accentBg(), RhythmColors::accentBorder(), RhythmColors::accent(),
               [this, onConfirm]
               {
                   const int p = p_.getText().getIntValue();
                   const int q = q_.getText().getIntValue();
                   if (p > 0 && q > 0 && onConfirm) onConfirm (p, q);
                   if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (1);
               });
}

void MmDialog::layoutContent (juce::Rectangle<int> b)
{
    auto r = b.reduced (0, 4);
    const int third = r.getWidth() / 2 - 14;
    p_.setBounds (r.removeFromLeft (third));
    r.removeFromLeft (4);
    slash_.setBounds (r.removeFromLeft (20));
    r.removeFromLeft (4);
    q_.setBounds (r);
}

// ---------- SetBpmDialog ----------

SetBpmDialog::SetBpmDialog (double currentBpm,
                            std::optional<double> initialBpm,
                            std::function<void (double)> onConfirm)
    : DialogPanel ("Set BPM (in track)",
                   "Jump to this BPM when reached during playback")
{
    styleNumericField (field_);
    field_.setText (juce::String ((int) initialBpm.value_or (currentBpm)),
                    juce::dontSendNotification);
    field_.selectAll();
    content().addAndMakeVisible (field_);

    addAction ("Cancel",
               RhythmColors::bg3(), RhythmColors::border1(), RhythmColors::textMuted(),
               [this] { if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (0); });
    addAction ("Insert",
               RhythmColors::accentBg(), RhythmColors::accentBorder(), RhythmColors::accent(),
               [this, onConfirm]
               {
                   const double v = field_.getText().getDoubleValue();
                   if (v > 0.0 && onConfirm) onConfirm (v);
                   if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (1);
               });
}

void SetBpmDialog::layoutContent (juce::Rectangle<int> b) { field_.setBounds (b.reduced (0, 4)); }

// ---------- RepeatDialog ----------

RepeatDialog::RepeatDialog (std::function<void (int)> onConfirm)
    : DialogPanel ("Repeat count", {})
{
    styleNumericField (field_);
    content().addAndMakeVisible (field_);

    addAction ("Cancel",
               RhythmColors::bg3(), RhythmColors::border1(), RhythmColors::textMuted(),
               [this] { if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (0); });
    addAction (juce::String::fromUTF8 (u8"∞ forever"),
               RhythmColors::infiniteBg(), RhythmColors::infiniteBorder(), RhythmColors::infiniteText(),
               [this, onConfirm]
               {
                   if (onConfirm) onConfirm (-1);
                   if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (1);
               });
    addAction ("OK",
               RhythmColors::accentBg(), RhythmColors::accentBorder(), RhythmColors::accent(),
               [this, onConfirm]
               {
                   const int v = field_.getText().getIntValue();
                   if (v >= 1 && onConfirm) onConfirm (v);
                   if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (1);
               });
}

void RepeatDialog::layoutContent (juce::Rectangle<int> b) { field_.setBounds (b.reduced (0, 4)); }

// ---------- CustomNumberDialog ----------

CustomNumberDialog::CustomNumberDialog (juce::String title, juce::String hint,
                                        std::function<void (int)> onConfirm)
    : DialogPanel (std::move (title), std::move (hint))
{
    styleNumericField (field_);
    content().addAndMakeVisible (field_);

    addAction ("Cancel",
               RhythmColors::bg3(), RhythmColors::border1(), RhythmColors::textMuted(),
               [this] { if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (0); });
    addAction ("OK",
               RhythmColors::accentBg(), RhythmColors::accentBorder(), RhythmColors::accent(),
               [this, onConfirm]
               {
                   const int v = field_.getText().getIntValue();
                   if (v > 0 && onConfirm) onConfirm (v);
                   if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (1);
               });
}

void CustomNumberDialog::layoutContent (juce::Rectangle<int> b) { field_.setBounds (b.reduced (0, 4)); }

// ---------- ListPickerDialog (generic) ----------

class ListPickerDialog::Row : public juce::Component
{
public:
    Row (Entry entry, bool selected, std::function<void()> onClick)
        : entry_ (std::move (entry)), selected_ (selected), onClick_ (std::move (onClick))
    {
        setMouseCursor (juce::MouseCursor::PointingHandCursor);
    }

    void paint (juce::Graphics& g) override
    {
        const auto r = getLocalBounds().toFloat().reduced (0.5f);
        const auto bg     = selected_ ? RhythmColors::accentBg()
                          : hovered_  ? RhythmColors::bg3().withAlpha (0.6f)
                                      : RhythmColors::bg3();
        const auto border = selected_ ? RhythmColors::accentBorder() : RhythmColors::border1();
        g.setColour (bg);
        g.fillRoundedRectangle (r, 4.0f);
        g.setColour (border);
        g.drawRoundedRectangle (r, 4.0f, 1.0f);

        auto text = getLocalBounds().reduced (12, 0);

        g.setColour (selected_ ? RhythmColors::accent() : RhythmColors::textDim());
        g.setFont (juce::Font (juce::FontOptions (13.0f, juce::Font::bold)));
        g.drawText (selected_ ? juce::String::fromUTF8 (u8"✓") : juce::String (" "),
                    text.removeFromLeft (16), juce::Justification::centredLeft, false);

        if (entry_.badge.isNotEmpty())
        {
            text.removeFromLeft (4);
            g.setColour (entry_.badgeColour);
            g.setFont (juce::Font (juce::FontOptions (10.0f)));
            g.drawText (entry_.badge, text.removeFromLeft (62),
                        juce::Justification::centredLeft, false);
        }

        g.setColour (selected_ ? RhythmColors::accent() : RhythmColors::textPrimary());
        g.setFont (juce::Font (juce::FontOptions (13.0f)));
        g.drawText (entry_.label, text, juce::Justification::centredLeft, false);
    }

    void mouseEnter (const juce::MouseEvent&) override { hovered_ = true;  repaint(); }
    void mouseExit  (const juce::MouseEvent&) override { hovered_ = false; repaint(); }
    void mouseDown  (const juce::MouseEvent&) override { if (onClick_) onClick_(); }

private:
    Entry                 entry_;
    bool                  selected_;
    bool                  hovered_ { false };
    std::function<void()> onClick_;
};

ListPickerDialog::ListPickerDialog (juce::String title,
                                    std::vector<Entry> entries,
                                    juce::String currentId,
                                    std::function<void (const juce::String&)> onSelect)
    : DialogPanel (std::move (title), {})
{
    preferredWidth = 400;
    addAndMakeVisible (viewport_);
    viewport_.setViewedComponent (&listContent_, false);
    viewport_.setScrollBarsShown (true, false);

    for (auto& entry : entries)
    {
        const bool isSelected = entry.payloadId == currentId;
        const juce::String payload = entry.payloadId;
        auto row = std::make_unique<Row> (entry, isSelected,
            [this, payload, onSelect]
            {
                if (onSelect) onSelect (payload);
                if (auto* w = findParentComponentOfClass<juce::DialogWindow>())
                    w->exitModalState (1);
            });
        listContent_.addAndMakeVisible (row.get());
        rows_.push_back (std::move (row));
    }
    content().addAndMakeVisible (viewport_);

    addAction ("Cancel",
               RhythmColors::bg3(), RhythmColors::border1(), RhythmColors::textMuted(),
               [this] { if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (0); });
}

ListPickerDialog::~ListPickerDialog() = default;

void ListPickerDialog::layoutContent (juce::Rectangle<int> b)
{
    viewport_.setBounds (b.reduced (0, 4));
    const int rowH = 36;
    const int gap  = 4;
    const int w    = viewport_.getWidth();
    int y = 0;
    for (auto& r : rows_) { r->setBounds (0, y, w, rowH); y += rowH + gap; }
    listContent_.setSize (w, y);
}

// ---------- SoundPickerDialog ----------

class SoundPickerDialog::Row : public juce::Component
{
public:
    Row (SoundInfo info, bool selected, std::function<void()> onClick)
        : info_ (std::move (info)), selected_ (selected), onClick_ (std::move (onClick))
    {
        setMouseCursor (juce::MouseCursor::PointingHandCursor);
    }

    void paint (juce::Graphics& g) override
    {
        const auto r = getLocalBounds().toFloat().reduced (0.5f);
        const auto bg     = selected_ ? RhythmColors::accentBg()
                          : hovered_  ? RhythmColors::bg3().withAlpha (0.5f)
                                      : RhythmColors::bg3();
        const auto border = selected_ ? RhythmColors::accentBorder() : RhythmColors::border1();
        g.setColour (bg);
        g.fillRoundedRectangle (r, 4.0f);
        g.setColour (border);
        g.drawRoundedRectangle (r, 4.0f, 1.0f);

        auto text = getLocalBounds().reduced (12, 0);

        // selected check mark
        const auto markCol = selected_ ? RhythmColors::accent() : RhythmColors::textDim();
        g.setColour (markCol);
        g.setFont (juce::Font (juce::FontOptions (13.0f, juce::Font::bold)));
        g.drawText (selected_ ? juce::String::fromUTF8 (u8"✓") : juce::String (" "),
                    text.removeFromLeft (16), juce::Justification::centredLeft, false);

        // user-vs-builtin badge
        text.removeFromLeft (4);
        g.setColour (info_.isUser ? RhythmColors::caution() : RhythmColors::textMuted());
        g.setFont (juce::Font (juce::FontOptions (10.0f)));
        g.drawText (info_.isUser ? "user" : "built-in",
                    text.removeFromLeft (52), juce::Justification::centredLeft, false);

        g.setColour (selected_ ? RhythmColors::accent() : RhythmColors::textPrimary());
        g.setFont (juce::Font (juce::FontOptions (13.0f)));
        g.drawText (info_.label, text, juce::Justification::centredLeft, false);
    }

    void mouseEnter (const juce::MouseEvent&) override { hovered_ = true;  repaint(); }
    void mouseExit  (const juce::MouseEvent&) override { hovered_ = false; repaint(); }
    void mouseDown  (const juce::MouseEvent&) override { if (onClick_) onClick_(); }

private:
    SoundInfo             info_;
    bool                  selected_;
    bool                  hovered_ { false };
    std::function<void()> onClick_;
};

SoundPickerDialog::SoundPickerDialog (std::vector<SoundInfo> sounds,
                                      std::optional<std::string> currentSoundId,
                                      std::function<void (const std::string&)> onSelect)
    : DialogPanel ("Choose sound", {})
{
    preferredWidth = 400;
    addAndMakeVisible (viewport_);
    viewport_.setViewedComponent (&listContent_, false);
    viewport_.setScrollBarsShown (true, false);

    for (auto& info : sounds)
    {
        const bool isSelected = currentSoundId.has_value() && *currentSoundId == info.id;
        auto row = std::make_unique<Row> (info, isSelected,
            [this, info, onSelect]
            {
                if (onSelect) onSelect (info.id);
                if (auto* w = findParentComponentOfClass<juce::DialogWindow>())
                    w->exitModalState (1);
            });
        listContent_.addAndMakeVisible (row.get());
        rows_.push_back (std::move (row));
    }
    content().addAndMakeVisible (viewport_);

    addAction ("Cancel",
               RhythmColors::bg3(), RhythmColors::border1(), RhythmColors::textMuted(),
               [this] { if (auto* w = findParentComponentOfClass<juce::DialogWindow>()) w->exitModalState (0); });
}

SoundPickerDialog::~SoundPickerDialog() = default;

void SoundPickerDialog::layoutContent (juce::Rectangle<int> b)
{
    viewport_.setBounds (b.reduced (0, 4));
    const int rowH = 36;
    const int gap  = 4;
    const int w    = viewport_.getWidth();
    int y = 0;
    for (auto& r : rows_)
    {
        r->setBounds (0, y, w, rowH);
        y += rowH + gap;
    }
    listContent_.setSize (w, y);
}

// ---------- showRhythmDialog ----------

void showRhythmDialog (juce::Component* parent, std::unique_ptr<DialogPanel> panel)
{
    auto* raw = panel.release();
    raw->setSize (raw->preferredWidth, 200);

    juce::DialogWindow::LaunchOptions opts;
    opts.dialogTitle                   = {};
    opts.content.setOwned (raw);
    opts.componentToCentreAround       = parent;
    opts.escapeKeyTriggersCloseButton  = true;
    opts.useNativeTitleBar             = false;
    opts.resizable                     = false;
    opts.dialogBackgroundColour        = juce::Colours::transparentBlack;
    opts.launchAsync();
}

} // namespace rhythm
