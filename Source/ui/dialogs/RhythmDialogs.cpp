#include "RhythmDialogs.h"

namespace rhythm {

// ---------- DialogPanel ----------

DialogPanel::DialogPanel(juce::String title, juce::String hint)
    : title_(std::move(title)), hint_(std::move(hint)) {
    addAndMakeVisible(contentHost_);
}

void DialogPanel::addAction(juce::String label, juce::Colour bg,
                            juce::Colour border, juce::Colour text,
                            std::function<void()> cb) {
    auto btn = std::make_unique<ChipButton>(std::move(label));
    btn->setStateColor(ChipButton::StateColor::Custom);
    btn->setColours(bg, border, text);
    btn->setFontSize(12.0f);
    btn->setOnClick(std::move(cb));
    addAndMakeVisible(btn.get());
    actionButtons_.push_back(std::move(btn));
}

void DialogPanel::paint(juce::Graphics &g) {
    const auto r = getLocalBounds().toFloat().reduced(0.5f);
    g.setColour(RhythmColors::bg2());
    g.fillRoundedRectangle(r, 12.0f);
    g.setColour(RhythmColors::border2());
    g.drawRoundedRectangle(r, 12.0f, 1.0f);

    auto inner = getLocalBounds().reduced(22, 18);
    g.setColour(RhythmColors::textPrimary());
    g.setFont(juce::Font(juce::FontOptions(15.0f, juce::Font::bold)));
    g.drawText(title_, inner.removeFromTop(22), juce::Justification::left,
               false);

    if (!hint_.isEmpty()) {
        g.setColour(RhythmColors::textSecondary());
        g.setFont(juce::Font(juce::FontOptions(11.0f)));
        inner.removeFromTop(4);
        g.drawText(hint_, inner.removeFromTop(16), juce::Justification::left,
                   false);
    }
}

void DialogPanel::resized() {
    auto inner = getLocalBounds().reduced(22, 18);
    inner.removeFromTop(22); // title
    if (!hint_.isEmpty())
        inner.removeFromTop(4 + 16);

    inner.removeFromTop(12);
    const int buttonH = 36;
    auto buttonRow = inner.removeFromBottom(buttonH);
    inner.removeFromBottom(10);

    contentHost_.setBounds(inner);
    layoutContent(contentHost_.getLocalBounds());

    const int n = (int)actionButtons_.size();
    if (n > 0) {
        const int gap = 8;
        const int btnW = (buttonRow.getWidth() - gap * (n - 1)) / n;
        int x = buttonRow.getX();
        for (auto &b : actionButtons_) {
            b->setBounds(x, buttonRow.getY(), btnW, buttonH);
            x += btnW + gap;
        }
    }
}

// ---------- BpmInputDialog ----------

namespace {
void styleNumericField(juce::TextEditor &e) {
    e.setInputRestrictions(6, "0123456789.");
    e.setIndents(8, 6);
    e.setColour(juce::TextEditor::textColourId, RhythmColors::textPrimary());
    e.setColour(juce::TextEditor::backgroundColourId, RhythmColors::bg3());
    e.setColour(juce::TextEditor::outlineColourId, RhythmColors::border1());
    e.setColour(juce::TextEditor::focusedOutlineColourId,
                RhythmColors::accent());
    e.setColour(juce::TextEditor::highlightColourId,
                RhythmColors::accent().withAlpha(0.35f));
    e.setColour(juce::CaretComponent::caretColourId, RhythmColors::accent());
    e.setFont(juce::Font(juce::FontOptions(15.0f)));
}
} // namespace

BpmInputDialog::BpmInputDialog(double currentBpm,
                               std::function<void(double)> onConfirm)
    : DialogPanel("Set BPM", {}) {
    styleNumericField(field_);
    field_.setText(juce::String((int)currentBpm), juce::dontSendNotification);
    field_.selectAll();
    content().addAndMakeVisible(field_);

    addAction("Cancel", RhythmColors::bg3(), RhythmColors::border1(),
              RhythmColors::textMuted(), [this] {
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(0);
              });

    addAction("OK", RhythmColors::accentBg(), RhythmColors::accentBorder(),
              RhythmColors::accent(), [this, onConfirm] {
                  const double v = field_.getText().getDoubleValue();
                  if (v > 0.0 && onConfirm)
                      onConfirm(v);
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(1);
              });
}

void BpmInputDialog::layoutContent(juce::Rectangle<int> b) {
    field_.setBounds(b.reduced(0, 4));
}

// ---------- MmDialog ----------

MmDialog::MmDialog(std::optional<int> initialP, std::optional<int> initialQ,
                   std::function<void(int, int)> onConfirm)
    : DialogPanel("Metric modulation",
                  juce::String::fromUTF8(u8"new BPM = current BPM × p/q")) {
    styleNumericField(p_);
    styleNumericField(q_);
    if (initialP.has_value())
        p_.setText(juce::String(*initialP), juce::dontSendNotification);
    if (initialQ.has_value())
        q_.setText(juce::String(*initialQ), juce::dontSendNotification);
    p_.selectAll();
    slash_.setText("/", juce::dontSendNotification);
    slash_.setJustificationType(juce::Justification::centred);
    slash_.setFont(juce::Font(juce::FontOptions(18.0f)));
    slash_.setColour(juce::Label::textColourId, RhythmColors::textSecondary());

    content().addAndMakeVisible(p_);
    content().addAndMakeVisible(slash_);
    content().addAndMakeVisible(q_);

    addAction("Cancel", RhythmColors::bg3(), RhythmColors::border1(),
              RhythmColors::textMuted(), [this] {
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(0);
              });
    addAction("Insert", RhythmColors::accentBg(), RhythmColors::accentBorder(),
              RhythmColors::accent(), [this, onConfirm] {
                  const int p = p_.getText().getIntValue();
                  const int q = q_.getText().getIntValue();
                  if (p > 0 && q > 0 && onConfirm)
                      onConfirm(p, q);
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(1);
              });
}

void MmDialog::layoutContent(juce::Rectangle<int> b) {
    auto r = b.reduced(0, 4);
    const int third = r.getWidth() / 2 - 14;
    p_.setBounds(r.removeFromLeft(third));
    r.removeFromLeft(4);
    slash_.setBounds(r.removeFromLeft(20));
    r.removeFromLeft(4);
    q_.setBounds(r);
}

// ---------- SetBpmDialog ----------

SetBpmDialog::SetBpmDialog(double currentBpm, std::optional<double> initialBpm,
                           std::function<void(double)> onConfirm)
    : DialogPanel("Set BPM (in track)",
                  "Jump to this BPM when reached during playback") {
    styleNumericField(field_);
    field_.setText(juce::String((int)initialBpm.value_or(currentBpm)),
                   juce::dontSendNotification);
    field_.selectAll();
    content().addAndMakeVisible(field_);

    addAction("Cancel", RhythmColors::bg3(), RhythmColors::border1(),
              RhythmColors::textMuted(), [this] {
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(0);
              });
    addAction("Insert", RhythmColors::accentBg(), RhythmColors::accentBorder(),
              RhythmColors::accent(), [this, onConfirm] {
                  const double v = field_.getText().getDoubleValue();
                  if (v > 0.0 && onConfirm)
                      onConfirm(v);
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(1);
              });
}

void SetBpmDialog::layoutContent(juce::Rectangle<int> b) {
    field_.setBounds(b.reduced(0, 4));
}

// ---------- RepeatDialog ----------

RepeatDialog::RepeatDialog(std::function<void(int)> onConfirm)
    : DialogPanel("Repeat count", {}) {
    styleNumericField(field_);
    content().addAndMakeVisible(field_);

    addAction("Cancel", RhythmColors::bg3(), RhythmColors::border1(),
              RhythmColors::textMuted(), [this] {
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(0);
              });
    addAction(juce::String::fromUTF8(u8"∞ forever"), RhythmColors::infiniteBg(),
              RhythmColors::infiniteBorder(), RhythmColors::infiniteText(),
              [this, onConfirm] {
                  if (onConfirm)
                      onConfirm(-1);
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(1);
              });
    addAction("OK", RhythmColors::accentBg(), RhythmColors::accentBorder(),
              RhythmColors::accent(), [this, onConfirm] {
                  const int v = field_.getText().getIntValue();
                  if (v >= 1 && onConfirm)
                      onConfirm(v);
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(1);
              });
}

void RepeatDialog::layoutContent(juce::Rectangle<int> b) {
    field_.setBounds(b.reduced(0, 4));
}

// ---------- CustomNumberDialog ----------

CustomNumberDialog::CustomNumberDialog(juce::String title, juce::String hint,
                                       std::function<void(int)> onConfirm)
    : DialogPanel(std::move(title), std::move(hint)) {
    styleNumericField(field_);
    content().addAndMakeVisible(field_);

    addAction("Cancel", RhythmColors::bg3(), RhythmColors::border1(),
              RhythmColors::textMuted(), [this] {
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(0);
              });
    addAction("OK", RhythmColors::accentBg(), RhythmColors::accentBorder(),
              RhythmColors::accent(), [this, onConfirm] {
                  const int v = field_.getText().getIntValue();
                  if (v > 0 && onConfirm)
                      onConfirm(v);
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(1);
              });
}

void CustomNumberDialog::layoutContent(juce::Rectangle<int> b) {
    field_.setBounds(b.reduced(0, 4));
}

// ---------- ListPickerDialog (generic) ----------

class ListPickerDialog::Row : public juce::Component {
  public:
    Row(Entry entry, bool selected, std::function<void()> onClick)
        : entry_(std::move(entry)), selected_(selected),
          onClick_(std::move(onClick)) {
        setMouseCursor(juce::MouseCursor::PointingHandCursor);
    }

    void paint(juce::Graphics &g) override {
        const auto r = getLocalBounds().toFloat().reduced(0.5f);
        const auto bg = selected_  ? RhythmColors::accentBg()
                        : hovered_ ? RhythmColors::bg3().withAlpha(0.6f)
                                   : RhythmColors::bg3();
        const auto border =
            selected_ ? RhythmColors::accentBorder() : RhythmColors::border1();
        g.setColour(bg);
        g.fillRoundedRectangle(r, 4.0f);
        g.setColour(border);
        g.drawRoundedRectangle(r, 4.0f, 1.0f);

        auto text = getLocalBounds().reduced(12, 0);

        g.setColour(selected_ ? RhythmColors::accent()
                              : RhythmColors::textDim());
        g.setFont(juce::Font(juce::FontOptions(13.0f, juce::Font::bold)));
        g.drawText(
            selected_ ? juce::String::fromUTF8(u8"✓") : juce::String(" "),
            text.removeFromLeft(16), juce::Justification::centredLeft, false);

        if (entry_.badge.isNotEmpty()) {
            text.removeFromLeft(4);
            g.setColour(entry_.badgeColour);
            g.setFont(juce::Font(juce::FontOptions(10.0f)));
            g.drawText(entry_.badge, text.removeFromLeft(62),
                       juce::Justification::centredLeft, false);
        }

        g.setColour(selected_ ? RhythmColors::accent()
                              : RhythmColors::textPrimary());
        g.setFont(juce::Font(juce::FontOptions(13.0f)));
        g.drawText(entry_.label, text, juce::Justification::centredLeft, false);
    }

    void mouseEnter(const juce::MouseEvent &) override {
        hovered_ = true;
        repaint();
    }
    void mouseExit(const juce::MouseEvent &) override {
        hovered_ = false;
        repaint();
    }
    void mouseDown(const juce::MouseEvent &) override {
        if (onClick_)
            onClick_();
    }

  private:
    Entry entry_;
    bool selected_;
    bool hovered_{false};
    std::function<void()> onClick_;
};

ListPickerDialog::ListPickerDialog(
    juce::String title, std::vector<Entry> entries, juce::String currentId,
    std::function<void(const juce::String &)> onSelect)
    : DialogPanel(std::move(title), {}) {
    preferredWidth = 400;
    preferredHeight = juce::jmin(116 + (int)entries.size() * 40, 520);
    addAndMakeVisible(viewport_);
    viewport_.setViewedComponent(&listContent_, false);
    viewport_.setScrollBarsShown(true, false);

    for (auto &entry : entries) {
        const bool isSelected = entry.payloadId == currentId;
        const juce::String payload = entry.payloadId;
        auto row =
            std::make_unique<Row>(entry, isSelected, [this, payload, onSelect] {
                if (onSelect)
                    onSelect(payload);
                if (auto *w = findParentComponentOfClass<juce::DialogWindow>())
                    w->exitModalState(1);
            });
        listContent_.addAndMakeVisible(row.get());
        rows_.push_back(std::move(row));
    }
    content().addAndMakeVisible(viewport_);

    addAction("Cancel", RhythmColors::bg3(), RhythmColors::border1(),
              RhythmColors::textMuted(), [this] {
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(0);
              });
}

ListPickerDialog::~ListPickerDialog() = default;

void ListPickerDialog::layoutContent(juce::Rectangle<int> b) {
    viewport_.setBounds(b.reduced(0, 4));
    const int rowH = 36;
    const int gap = 4;
    const int w = viewport_.getWidth();
    int y = 0;
    for (auto &r : rows_) {
        r->setBounds(0, y, w, rowH);
        y += rowH + gap;
    }
    listContent_.setSize(w, y);
}

// ---------- SoundPickerDialog ----------

class SoundPickerDialog::Row : public juce::Component {
  public:
    Row(SoundInfo info, bool selected, std::function<void()> onClick)
        : info_(std::move(info)), selected_(selected),
          onClick_(std::move(onClick)) {
        setMouseCursor(juce::MouseCursor::PointingHandCursor);
    }

    void paint(juce::Graphics &g) override {
        const auto r = getLocalBounds().toFloat().reduced(0.5f);
        const auto bg = selected_  ? RhythmColors::accentBg()
                        : hovered_ ? RhythmColors::bg3().withAlpha(0.5f)
                                   : RhythmColors::bg3();
        const auto border =
            selected_ ? RhythmColors::accentBorder() : RhythmColors::border1();
        g.setColour(bg);
        g.fillRoundedRectangle(r, 4.0f);
        g.setColour(border);
        g.drawRoundedRectangle(r, 4.0f, 1.0f);

        auto text = getLocalBounds().reduced(12, 0);

        // selected check mark
        const auto markCol =
            selected_ ? RhythmColors::accent() : RhythmColors::textDim();
        g.setColour(markCol);
        g.setFont(juce::Font(juce::FontOptions(13.0f, juce::Font::bold)));
        g.drawText(
            selected_ ? juce::String::fromUTF8(u8"✓") : juce::String(" "),
            text.removeFromLeft(16), juce::Justification::centredLeft, false);

        // user-vs-builtin badge
        text.removeFromLeft(4);
        g.setColour(info_.isUser ? RhythmColors::caution()
                                 : RhythmColors::textMuted());
        g.setFont(juce::Font(juce::FontOptions(10.0f)));
        g.drawText(info_.isUser ? "user" : "built-in", text.removeFromLeft(52),
                   juce::Justification::centredLeft, false);

        g.setColour(selected_ ? RhythmColors::accent()
                              : RhythmColors::textPrimary());
        g.setFont(juce::Font(juce::FontOptions(13.0f)));
        g.drawText(info_.label, text, juce::Justification::centredLeft, false);
    }

    void mouseEnter(const juce::MouseEvent &) override {
        hovered_ = true;
        repaint();
    }
    void mouseExit(const juce::MouseEvent &) override {
        hovered_ = false;
        repaint();
    }
    void mouseDown(const juce::MouseEvent &) override {
        if (onClick_)
            onClick_();
    }

  private:
    SoundInfo info_;
    bool selected_;
    bool hovered_{false};
    std::function<void()> onClick_;
};

SoundPickerDialog::SoundPickerDialog(
    std::vector<SoundInfo> sounds, std::optional<std::string> currentSoundId,
    std::function<void(const std::string &)> onSelect,
    std::optional<float> currentVolume,
    std::function<void(float)> onVolumeChange)
    : DialogPanel("Choose sound", {}) {
    preferredWidth = 400;
    preferredHeight = juce::jmin(116 + (int)sounds.size() * 40, 520);

    if (currentVolume.has_value()) {
        hasVolume_ = true;
        onVolumeChange_ = std::move(onVolumeChange);
        preferredHeight = juce::jmin(preferredHeight + 44, 564);

        volumeLabel_.setText("Default volume", juce::dontSendNotification);
        volumeLabel_.setFont(juce::Font(juce::FontOptions(11.0f)));
        volumeLabel_.setColour(juce::Label::textColourId, RhythmColors::textSecondary());

        volumeSlider_.setRange(0.0, 1.0);
        volumeSlider_.setSliderStyle(juce::Slider::LinearHorizontal);
        volumeSlider_.setTextBoxStyle(juce::Slider::NoTextBox, false, 0, 0);
        volumeSlider_.setValue((double)*currentVolume, juce::dontSendNotification);
        volumeSlider_.onValueChange = [this] {
            const auto v = (float)volumeSlider_.getValue();
            volumePercent_.setText(juce::String((int)(v * 100.0f)) + "%",
                                   juce::dontSendNotification);
            if (onVolumeChange_)
                onVolumeChange_(v);
        };

        volumePercent_.setText(
            juce::String((int)(*currentVolume * 100.0f)) + "%",
            juce::dontSendNotification);
        volumePercent_.setFont(juce::Font(juce::FontOptions(11.0f)));
        volumePercent_.setColour(juce::Label::textColourId, RhythmColors::textSecondary());
        volumePercent_.setJustificationType(juce::Justification::centredRight);

        content().addAndMakeVisible(volumeLabel_);
        content().addAndMakeVisible(volumeSlider_);
        content().addAndMakeVisible(volumePercent_);
    }

    addAndMakeVisible(viewport_);
    viewport_.setViewedComponent(&listContent_, false);
    viewport_.setScrollBarsShown(true, false);

    for (auto &info : sounds) {
        const bool isSelected =
            currentSoundId.has_value() && *currentSoundId == info.id;
        auto row =
            std::make_unique<Row>(info, isSelected, [this, info, onSelect] {
                if (onSelect)
                    onSelect(info.id);
                if (auto *w = findParentComponentOfClass<juce::DialogWindow>())
                    w->exitModalState(1);
            });
        listContent_.addAndMakeVisible(row.get());
        rows_.push_back(std::move(row));
    }
    content().addAndMakeVisible(viewport_);

    addAction("Cancel", RhythmColors::bg3(), RhythmColors::border1(),
              RhythmColors::textMuted(), [this] {
                  if (auto *w =
                          findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(0);
              });
}

SoundPickerDialog::~SoundPickerDialog() = default;

void SoundPickerDialog::layoutContent(juce::Rectangle<int> b) {
    if (hasVolume_) {
        auto volRow = b.removeFromTop(36);
        b.removeFromTop(4);
        volumeLabel_.setBounds(volRow.removeFromLeft(96));
        volRow.removeFromLeft(4);
        volumePercent_.setBounds(volRow.removeFromRight(44));
        volumeSlider_.setBounds(volRow.reduced(0, 4));
    }
    viewport_.setBounds(b.reduced(0, 4));
    const int rowH = 36;
    const int gap = 4;
    const int w = viewport_.getWidth();
    int y = 0;
    for (auto &r : rows_) {
        r->setBounds(0, y, w, rowH);
        y += rowH + gap;
    }
    listContent_.setSize(w, y);
}

// help
std::vector<HelpDialog::HelpEntry> HelpDialog::makeEntries()
{
    return {
        { "Beat",
          "The numpad enters beat values. The /4 button shows the current subdivision for "
          "the next input. For example, /4 matches \"16th notes\" in classical western theory, "
          "then entering 1 on the numpad gives an item with duration of 1/16th note relative to "
          "the bpm. Entering 3 is equivalent to a dotted 8th note. For values above 9 press the "
          "\"custom\" button. A beat can be turned on/off, its volume and sound adjusted in the "
          "edit panel above the numpad." },
        { "Subdivision",
          "The default subdivision is /4. To change it, click the /N button then press "
          "the numpad or \"custom\" for any value larger than 9. This gives easy access to all "
          "tuplets. For nested tuplets you need to do the calculation. Example: a triplet out of "
          "two notes from triplet-eighth-notes is 2/9 of the pulse, so set subdivision to 9 "
          "then press 2." },
        { "Brackets and Repeats",
          "[ and ] wrap a section. Press xN after the closing bracket then click the numpad "
          "to repeat that many times, or \"custom\" for more than 9. Infinite repeat is also an "
          "option. Useful for simulating meters, hypermeters, or any repeating pattern. Brackets "
          "can be nested. Example: [ 3 3 2 ]x4 [ 5 ]xinf plays a 3+3+2 group four "
          "times, then loops 5 indefinitely." },
        { "Editing",
          "The E button in the numpad is used for editing. When an item is selected, click E "
          "to enter edit mode -- number inputs will change the selected item instead of appending. "
          "Del deletes the selected item, or the last item if nothing is selected. "
          "Paired bracket delete (in settings) removes both brackets together when you delete either one." },
        { "Keyboard Shortcuts",
          "Space: play/stop. Left/Right arrows: move cursor. Up/Down arrows: switch tracks. "
          "1-9: enter beat. Backspace/Delete: delete. M: mute active track. S: solo active track." },
        { "Tempo Changes",
          "mm inserts a metric modulation. After clicking it a popup prompts for two numbers. "
          "For example at 120 bpm, mm of x3/2 gives 180. =bpm sets an absolute tempo at that "
          "point. Both take effect at that position during playback and won't affect the global "
          "bpm. You cannot put mm inside an infinitely-repeated group." },
        { "Tracks",
          "Each track is a row. Multiple tracks play simultaneously, useful for polyrhythm and "
          "polymeters. Tap a track to select it and edit. Each track repeats from the start when "
          "the end is reached (except when there is an infinite repeat)." },
        { "Mute, Solo, Default Sound",
          "Each track row has M (mute), S (solo), and snd (default sound/volume) chips. "
          "The snd chip opens a picker to set the default sound and volume for new beats on that "
          "track; the chip highlights when a custom sound is configured. "
          "Item-level sound/volume overrides the track default, which overrides the global default "
          "(set via Default sound... in the settings menu)." },
        { "Projects",
          "Projects auto-save every 30 seconds. Use Save As in the settings menu for named "
          ".rhy project files that can be shared across devices. Open Project loads a .rhy file." },
        { "About Prog Metronome",
          "Prog Metronome is a free and open source project. Source code is available at\n"
          "https://github.com/JayC180/prog-metronome\n\n"
          "If you have the ability, consider supporting the developer at\n"
          "https://ko-fi.com/prog_metronome" },
    };
}

HelpDialog::ContentComp::ContentComp(std::vector<HelpEntry> entries)
    : entries_(std::move(entries)) {}

void HelpDialog::ContentComp::relayout(int width)
{
    if (width == cachedWidth_ || width <= 0)
        return;
    cachedWidth_ = width;

    const juce::Font titleFont(juce::FontOptions(20.0f, juce::Font::bold));
    const juce::Font bodyFont(juce::FontOptions(18.0f));
    const float fw = (float)width;

    sectionY_.clear();
    int y = 4;
    for (const auto &e : entries_) {
        sectionY_.push_back(y);
        y += 24; // title line

        juce::AttributedString as;
        as.setText(e.body);
        as.setFont(bodyFont);
        as.setColour(RhythmColors::textSecondary());
        as.setWordWrap(juce::AttributedString::byWord);
        juce::TextLayout tl;
        tl.createLayout(as, fw);
        y += (int)std::ceil(tl.getHeight()) + 14; // body + gap
    }
    totalH_ = y;
    setSize(width, totalH_);
}

void HelpDialog::ContentComp::paint(juce::Graphics &g)
{
    if (cachedWidth_ <= 0 || sectionY_.size() != entries_.size())
        return;

    const juce::Font titleFont(juce::FontOptions(20.0f, juce::Font::bold));
    const juce::Font bodyFont(juce::FontOptions(18.0f));
    const float fw = (float)getWidth();

    for (int i = 0; i < (int)entries_.size(); ++i) {
        const int y = sectionY_[(size_t)i];
        const auto &e = entries_[(size_t)i];

        g.setFont(titleFont);
        g.setColour(RhythmColors::accent());
        g.drawText(e.title, 0, y, getWidth(), 24, juce::Justification::left, false);

        juce::AttributedString as;
        as.setText(e.body);
        as.setFont(bodyFont);
        as.setColour(RhythmColors::textSecondary());
        as.setWordWrap(juce::AttributedString::byWord);
        juce::TextLayout tl;
        tl.createLayout(as, fw);
        tl.draw(g, juce::Rectangle<float>(0.0f, (float)(y + 25), fw, (float)totalH_));
    }
}

HelpDialog::HelpDialog()
    : DialogPanel("Help", {}), content_(makeEntries())
{
    preferredWidth  = 420;
    preferredHeight = 500;
    viewport_.setViewedComponent(&content_, false);
    viewport_.setScrollBarsShown(true, false);
    content().addAndMakeVisible(viewport_);

    addAction("Close", RhythmColors::bg3(), RhythmColors::border1(),
              RhythmColors::textMuted(), [this] {
                  if (auto *w = findParentComponentOfClass<juce::DialogWindow>())
                      w->exitModalState(0);
              });
}

HelpDialog::~HelpDialog() = default;

void HelpDialog::layoutContent(juce::Rectangle<int> b)
{
    content_.relayout(b.getWidth() - 4);
    viewport_.setBounds(b);
}

// ---------- showRhythmDialog ----------

void showRhythmDialog(juce::Component *parent,
                      std::unique_ptr<DialogPanel> panel) {
    auto *raw = panel.release();
    raw->setSize(raw->preferredWidth, raw->preferredHeight);

    juce::DialogWindow::LaunchOptions opts;
    opts.dialogTitle = {};
    opts.content.setOwned(raw);
    opts.componentToCentreAround = parent;
    opts.escapeKeyTriggersCloseButton = true;
    opts.useNativeTitleBar = false;
    opts.resizable = false;
    opts.dialogBackgroundColour = juce::Colours::transparentBlack;
    opts.launchAsync();
}

} // namespace rhythm
