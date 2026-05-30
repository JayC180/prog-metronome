#include "BottomPanelComponent.h"

namespace rhythm
{

BottomPanelComponent::BottomPanelComponent (TrackBuilder& builder)
    : builder_ (builder),
      navPrevButton_      (juce::String::fromUTF8 (u8"←")),
      denomButton_        (juce::String::fromUTF8 (u8"÷4")),
      openBracketButton_  ("["),
      closeBracketButton_ ("]"),
      repeatButton_       (juce::String::fromUTF8 (u8"×N")),
      mmButton_           ("mm"),
      setBpmButton_       ("=bpm"),
      navNextButton_      (juce::String::fromUTF8 (u8"→"))
{
    auto addToolButton = [this] (ChipButton& b, std::function<void()> cb)
    {
        b.setFontSize (12.0f);
        b.setOnClick (std::move (cb));
        addAndMakeVisible (b);
    };

    addToolButton (navPrevButton_,      [this] { builder_.moveCursorPrev(); });
    addToolButton (denomButton_,        [this] { builder_.toggleDenomMode(); });
    addToolButton (openBracketButton_,  [this] { builder_.openBracket(); });
    addToolButton (closeBracketButton_, [this] { builder_.closeBracket(); });
    addToolButton (repeatButton_,       [this] { builder_.toggleRepeatMode(); });
    addToolButton (mmButton_,           [this] { if (onMmRequested)     onMmRequested(); });
    addToolButton (setBpmButton_,       [this] { if (onSetBpmRequested) onSetBpmRequested(); });
    addToolButton (navNextButton_,      [this] { builder_.moveCursorNext(); });

    // Edit panel labels
    auto makeStaticLabel = [this] (juce::Label& l, juce::String text)
    {
        l.setText (std::move (text), juce::dontSendNotification);
        l.setFont (juce::Font (juce::FontOptions (12.0f)));
        l.setColour (juce::Label::textColourId, RhythmColors::textMuted());
        addAndMakeVisible (l);
    };
    makeStaticLabel (onOffLabel_, "on/off");
    makeStaticLabel (volumeLabel_, "volume");
    makeStaticLabel (soundLabel_, "sound");
    onOffStatus_.setFont (juce::Font (juce::FontOptions (12.0f)));
    addAndMakeVisible (onOffStatus_);
    volumePercentLabel_.setFont (juce::Font (juce::FontOptions (12.0f)));
    volumePercentLabel_.setJustificationType (juce::Justification::centredRight);
    addAndMakeVisible (volumePercentLabel_);
    soundValueLabel_.setFont (juce::Font (juce::FontOptions (12.0f)));
    soundValueLabel_.setColour (juce::Label::textColourId, RhythmColors::textSecondary());
    addAndMakeVisible (soundValueLabel_);

    volumeSlider_.setRange (0.0, 1.0);
    volumeSlider_.setSliderStyle (juce::Slider::LinearHorizontal);
    volumeSlider_.setTextBoxStyle (juce::Slider::NoTextBox, false, 0, 0);
    volumeSlider_.onValueChange = [this]
    {
        const auto& s = builder_.state();
        if (! s.cursorIndex.has_value()) return;
        const auto* t = s.activeTrack();
        if (t == nullptr) return;
        const int idx = *s.cursorIndex;
        if (idx < 0 || idx >= (int) t->items.size()) return;
        if (! t->items[(size_t) idx].isBeat()) return;
        builder_.updateBeatAt (idx, [v = (float) volumeSlider_.getValue()]
                                    (const TrackItem::Beat& b)
                                    { auto c = b; c.volume = v; return c; });
    };
    addAndMakeVisible (volumeSlider_);

    changeSoundButton_.setStateColor (ChipButton::StateColor::Custom);
    changeSoundButton_.setColours (RhythmColors::accentBg(),
                                   RhythmColors::accentBorder(),
                                   RhythmColors::accent());
    changeSoundButton_.setOnClick ([this] { if (onChangeBeatSound) onChangeBeatSound(); });
    addAndMakeVisible (changeSoundButton_);

    // Numpad
    hintLabel_.setFont (juce::Font (juce::FontOptions (11.0f)));
    hintLabel_.setColour (juce::Label::textColourId, RhythmColors::caution());
    addAndMakeVisible (hintLabel_);

    for (int i = 0; i < 9; ++i)
    {
        const int n = i + 1;
        auto key = std::make_unique<NumKeyButton> (juce::String (n));
        key->setOnClick ([this, n] { onNumKey (n); });
        addAndMakeVisible (key.get());
        numKeys_[(size_t) i] = std::move (key);
    }
    customKey_.setFontSize (10.0f);
    customKey_.setOnClick ([this] { onCustom(); });
    editKey_  .setOnClick ([this] { onEditToggle(); });
    backspaceKey_.setOnClick ([this] { onBackspace(); });
    backspaceKey_.setFontSize (14.0f);
    addAndMakeVisible (customKey_);
    addAndMakeVisible (editKey_);
    addAndMakeVisible (backspaceKey_);
}

void BottomPanelComponent::rebuildToolbar()
{
    const auto& s = builder_.state();
    denomButton_.setLabel (juce::String::fromUTF8 (u8"÷") + juce::String (s.activeDenom()));
    denomButton_.setStateColor (s.isDenomMode() ? ChipButton::StateColor::Editing
                                                : ChipButton::StateColor::Normal);
    denomButton_.setEnabledLook (s.canEdit());

    openBracketButton_.setStateColor (s.canOpenBracket() ? ChipButton::StateColor::Normal
                                                         : ChipButton::StateColor::Dim);
    openBracketButton_.setEnabledLook (s.canOpenBracket());

    closeBracketButton_.setStateColor (s.canCloseBracket() ? ChipButton::StateColor::Normal
                                                           : ChipButton::StateColor::Dim);
    closeBracketButton_.setEnabledLook (s.canCloseBracket());

    if (s.isRepeatMode())        repeatButton_.setStateColor (ChipButton::StateColor::Editing);
    else if (s.canSetRepeat())   repeatButton_.setStateColor (ChipButton::StateColor::Normal);
    else                         repeatButton_.setStateColor (ChipButton::StateColor::Dim);
    repeatButton_.setEnabledLook (s.canSetRepeat() || s.isRepeatMode());

    mmButton_.setStateColor (s.canInsertTempo() ? ChipButton::StateColor::Normal
                                                : ChipButton::StateColor::Dim);
    mmButton_.setEnabledLook (s.canInsertTempo());

    setBpmButton_.setStateColor (s.canInsertTempo() ? ChipButton::StateColor::Normal
                                                    : ChipButton::StateColor::Dim);
    setBpmButton_.setEnabledLook (s.canInsertTempo());
}

void BottomPanelComponent::rebuildNumpad()
{
    const auto& s = builder_.state();
    const bool enabled = ! s.isPlaying();
    const auto* item = s.cursorItem();
    const bool beatSelected = item != nullptr && item->isBeat();

    juce::String hint;
    if (s.isDenomMode())       hint = "set subdivision — tap or use custom";
    else if (s.isRepeatMode()) hint = juce::String::fromUTF8 (u8"set repeat (1-9) or custom / ∞");
    else if (s.isEditMode())   hint = "edit value — tap a number or use custom";
    hintLabel_.setText (hint, juce::dontSendNotification);

    const bool customEnabled = s.isRepeatMode() || s.isEditMode() || s.isDenomMode()
                            || (item != nullptr && item->isBeat());
    customKey_.setColours (RhythmColors::bg3(),
                           RhythmColors::border1(),
                           customEnabled ? RhythmColors::textPrimary() : RhythmColors::textMuted());

    if (s.isEditMode())
        editKey_.setColours (RhythmColors::cautionBg(), RhythmColors::cautionBorder(), RhythmColors::caution());
    else if (s.canEditItem())
        editKey_.setColours (RhythmColors::accentBg(),  RhythmColors::accentBorder(),  RhythmColors::accent());
    else
        editKey_.setColours (RhythmColors::bg3(),       RhythmColors::border0(),       RhythmColors::textDim());

    backspaceKey_.setColours (RhythmColors::bg3(), RhythmColors::border1(), RhythmColors::textMuted());
    for (auto& k : numKeys_)
        k->setColours (RhythmColors::bg3(), RhythmColors::border1(),
                       enabled ? RhythmColors::thumbColor() : RhythmColors::textDim());

    // Edit panel (beat-only fields)
    onOffStatus_.setText (! enabled
                            ? juce::String ("select a beat")
                            : (beatSelected ? (item->getIf<TrackItem::Beat>()->active
                                                  ? juce::String ("active")
                                                  : juce::String ("rest"))
                                            : juce::String ("—")),
                          juce::dontSendNotification);
    onOffStatus_.setColour (juce::Label::textColourId,
        ! enabled || ! beatSelected ? RhythmColors::textDim()
            : (item->getIf<TrackItem::Beat>()->active ? RhythmColors::accent()
                                                     : RhythmColors::textMuted()));

    if (beatSelected)
    {
        const auto& b = *item->getIf<TrackItem::Beat>();
        volumeSlider_.setValue ((double) b.volume, juce::dontSendNotification);
        volumePercentLabel_.setText (juce::String ((int) (b.volume * 100.0f)) + "%",
                                     juce::dontSendNotification);
        soundValueLabel_.setText (b.soundId.has_value() ? juce::String (*b.soundId)
                                                        : juce::String ("—"),
                                  juce::dontSendNotification);
    }
    else
    {
        volumeSlider_.setValue (0.0, juce::dontSendNotification);
        volumePercentLabel_.setText ("—", juce::dontSendNotification);
        soundValueLabel_.setText ("—", juce::dontSendNotification);
    }
    volumeSlider_.setEnabled (enabled && beatSelected);
    changeSoundButton_.setEnabledLook (enabled && beatSelected);
    changeSoundButton_.setVisible (enabled && beatSelected);
}

void BottomPanelComponent::syncToState()
{
    rebuildToolbar();
    rebuildNumpad();
    repaint();
}

void BottomPanelComponent::paint (juce::Graphics& g)
{
    g.fillAll (RhythmColors::bg0());
    g.setColour (RhythmColors::bg2());
    g.fillRect (toolbarArea_);
    g.setColour (RhythmColors::border0());
    g.drawLine ((float) toolbarArea_.getX(), (float) toolbarArea_.getBottom() - 0.5f,
                (float) toolbarArea_.getRight(), (float) toolbarArea_.getBottom() - 0.5f, 0.5f);
    g.drawLine ((float) editArea_.getX(),    (float) editArea_.getBottom() - 0.5f,
                (float) editArea_.getRight(), (float) editArea_.getBottom() - 0.5f, 0.5f);
}

void BottomPanelComponent::resized()
{
    auto bounds = getLocalBounds();

    toolbarArea_ = bounds.removeFromTop (50);
    {
        auto r = toolbarArea_.reduced (8, 8);
        auto layout = [&r] (ChipButton& b, int w) { b.setBounds (r.removeFromLeft (w)); r.removeFromLeft (4); };
        layout (navPrevButton_,      36);
        r.removeFromLeft (4);
        layout (denomButton_,        44);
        layout (openBracketButton_,  32);
        layout (closeBracketButton_, 32);
        layout (repeatButton_,       44);
        layout (mmButton_,           44);
        layout (setBpmButton_,       54);
        navNextButton_.setBounds (r.removeFromRight (36));
    }

    editArea_ = bounds.removeFromTop (110);
    {
        auto r = editArea_.reduced (12, 10);
        const int labelW = 56;
        auto row1 = r.removeFromTop (28);
        onOffLabel_.setBounds (row1.removeFromLeft (labelW));
        row1.removeFromLeft (10);
        onOffStatus_.setBounds (row1);

        r.removeFromTop (4);
        auto row2 = r.removeFromTop (28);
        volumeLabel_.setBounds (row2.removeFromLeft (labelW));
        row2.removeFromLeft (10);
        volumePercentLabel_.setBounds (row2.removeFromRight (50));
        volumeSlider_.setBounds (row2.reduced (4, 4));

        r.removeFromTop (4);
        auto row3 = r.removeFromTop (28);
        soundLabel_.setBounds (row3.removeFromLeft (labelW));
        row3.removeFromLeft (10);
        changeSoundButton_.setBounds (row3.removeFromRight (80));
        row3.removeFromRight (8);
        soundValueLabel_.setBounds (row3);
    }

    numpadArea_ = bounds;
    {
        auto r = numpadArea_.reduced (8, 6);
        hintLabel_.setBounds (r.removeFromTop (16));
        r.removeFromTop (4);
        const int totalGap = 4 * 2; // 3 cols, 2 gaps
        const int colWidth = (r.getWidth() - totalGap) / 3;
        const int rowGap   = 4;
        const int rowHeight = (r.getHeight() - rowGap * 3) / 4;
        for (int row = 0; row < 3; ++row)
        {
            int x = r.getX();
            const int y = r.getY() + row * (rowHeight + rowGap);
            for (int col = 0; col < 3; ++col)
            {
                const int idx = row * 3 + col;
                numKeys_[(size_t) idx]->setBounds (x, y, colWidth, rowHeight);
                x += colWidth + 4;
            }
        }
        const int lastRowY = r.getY() + 3 * (rowHeight + rowGap);
        customKey_.setBounds   (r.getX(),                       lastRowY, colWidth, rowHeight);
        editKey_.setBounds     (r.getX() + colWidth + 4,         lastRowY, colWidth, rowHeight);
        backspaceKey_.setBounds(r.getX() + 2 * (colWidth + 4),   lastRowY, colWidth, rowHeight);
    }
}

void BottomPanelComponent::onNumKey (int n)
{
    const auto& s = builder_.state();
    if (s.isPlaying()) return;
    if      (s.isDenomMode())  builder_.setDenom (n);
    else if (s.isRepeatMode()) builder_.repeatDigit (n);
    else if (s.isEditMode())   builder_.editDigit (n);
    else                       builder_.enterBeat (n);
}

void BottomPanelComponent::onCustom()
{
    const auto& s = builder_.state();
    if (s.isEditMode())
    {
        if (s.cursorItem() != nullptr)
        {
            if (s.cursorItem()->isRepeat() && onRepeatCustomRequested) onRepeatCustomRequested();
            else if (s.cursorItem()->isSetBpm() && onSetBpmRequested)  onSetBpmRequested();
            else if (s.cursorItem()->isBeat()  && onCustomBeatRequested) onCustomBeatRequested();
        }
        return;
    }
    if (s.isRepeatMode()) { if (onRepeatCustomRequested) onRepeatCustomRequested(); return; }
    if (s.isDenomMode())  { if (onCustomDenomRequested)  onCustomDenomRequested();  return; }
    if (onCustomBeatRequested) onCustomBeatRequested();
}

void BottomPanelComponent::onEditToggle()
{
    const auto* item = builder_.state().cursorItem();
    if (item != nullptr && item->isModulation()) { if (onMmRequested)     onMmRequested();     return; }
    if (item != nullptr && item->isSetBpm())     { if (onSetBpmRequested) onSetBpmRequested(); return; }
    builder_.toggleEditMode();
}

void BottomPanelComponent::onBackspace()
{
    const auto& s = builder_.state();
    if (s.cursorIndex.has_value()) builder_.deleteSelected();
    else                           builder_.deleteLast();
}

} // namespace rhythm
