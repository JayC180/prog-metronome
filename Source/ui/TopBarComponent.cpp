#include "TopBarComponent.h"

namespace rhythm
{

TopBarComponent::TopBarComponent (TrackBuilder& builder)
    : builder_ (builder)
{
    settingsButton_.setStateColor (ChipButton::StateColor::Custom);
    settingsButton_.setColours (RhythmColors::bg3(),
                                RhythmColors::border1(),
                                RhythmColors::textMuted());
    settingsButton_.setFontSize (18.0f);
    settingsButton_.setOnClick ([this]{ if (onSettingsClicked) onSettingsClicked(); });
    addAndMakeVisible (settingsButton_);
}

void TopBarComponent::syncToState() { repaint(); }

void TopBarComponent::paint (juce::Graphics& g)
{
    g.fillAll (RhythmColors::bg2());
    g.setColour (RhythmColors::border0());
    g.drawLine (0.0f, (float) getHeight() - 0.5f, (float) getWidth(), (float) getHeight() - 0.5f, 0.5f);

    paintBpmCard       (g);
    paintPlayStopButton(g);
}

void TopBarComponent::paintBpmCard (juce::Graphics& g)
{
    // Operate on a local copy — never mutate bpmCardArea_, since paint() is
    // called repeatedly and `removeFromTop` is destructive.
    auto       cardArea = bpmCardArea_;
    const auto r        = cardArea.toFloat().reduced (0.5f);
    const bool playing  = builder_.state().isPlaying();
    const auto border   = playing ? RhythmColors::cautionBorder() : RhythmColors::border1();
    const auto bpmCol   = playing ? RhythmColors::caution() : RhythmColors::textPrimary();

    g.setColour (RhythmColors::bg3());
    g.fillRoundedRectangle (r, 6.0f);
    g.setColour (border);
    g.drawRoundedRectangle (r, 6.0f, 1.0f);

    g.setColour (RhythmColors::textMuted());
    g.setFont (juce::Font (juce::FontOptions (10.0f)));
    g.drawText ("bpm",
                cardArea.removeFromTop (16),
                juce::Justification::centred, false);

    g.setColour (bpmCol);
    g.setFont (juce::Font (juce::FontOptions (20.0f, juce::Font::bold)));
    g.drawText (juce::String ((int) builder_.state().displayBpm()),
                cardArea,
                juce::Justification::centred, false);
}

void TopBarComponent::paintPlayStopButton (juce::Graphics& g)
{
    const auto r       = playStopArea_.toFloat().reduced (0.5f);
    const bool playing = builder_.state().isPlaying();
    const auto bg      = playing ? RhythmColors::dangerBg() : RhythmColors::accentBg();
    const auto border  = playing ? RhythmColors::dangerBorder() : RhythmColors::accentBorder();
    g.setColour (bg);
    g.fillRoundedRectangle (r, 6.0f);
    g.setColour (border);
    g.drawRoundedRectangle (r, 6.0f, 1.0f);

    if (playing)
    {
        g.setColour (RhythmColors::danger());
        const auto sq = juce::Rectangle<float> ((float) playStopArea_.getCentreX() - 5.0f,
                                                (float) playStopArea_.getCentreY() - 5.0f,
                                                10.0f, 10.0f);
        g.fillRoundedRectangle (sq, 1.0f);
    }
    else
    {
        juce::Path tri;
        const float cx = (float) playStopArea_.getCentreX();
        const float cy = (float) playStopArea_.getCentreY();
        tri.startNewSubPath (cx - 4.0f, cy - 6.0f);
        tri.lineTo          (cx + 6.0f, cy);
        tri.lineTo          (cx - 4.0f, cy + 6.0f);
        tri.closeSubPath();
        g.setColour (RhythmColors::accentBright());
        g.fillPath (tri);
    }
}

void TopBarComponent::resized()
{
    auto bounds = getLocalBounds().reduced (10, 6);
    settingsButton_.setBounds (bounds.removeFromLeft (40));
    bounds.removeFromLeft (8);
    bpmCardArea_ = bounds.removeFromLeft (90);
    bpmCardArea_.reduce (0, 2);
    playStopArea_ = bounds.removeFromRight (40);
}

void TopBarComponent::mouseDown (const juce::MouseEvent& e)
{
    if (bpmCardArea_.contains (e.getPosition()))
    {
        if (onBpmClicked) onBpmClicked();
        return;
    }
    if (playStopArea_.contains (e.getPosition()))
    {
        builder_.togglePlayStop();
        repaint();
    }
}

} // namespace rhythm
