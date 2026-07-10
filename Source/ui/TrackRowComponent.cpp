#include "TrackRowComponent.h"

namespace rhythm {

class TrackRowComponent::ItemStrip : public juce::Component {
  public:
    ItemStrip(TrackBuilder &b, int &trackIdx)
        : builder_(b), trackIndex_(trackIdx) {
        addAndMakeVisible(viewport_);
        viewport_.setViewedComponent(&content_, false);
        viewport_.setScrollBarsShown(false, true, false, true);
    }

    void syncToState() {
        layoutItems();
        const auto &st = builder_.state();
        const auto *draft =
            (trackIndex_ >= 0 && trackIndex_ < (int)st.tracks.size())
                ? &st.tracks[(size_t)trackIndex_]
                : nullptr;
        if (draft == nullptr)
            return;

        if (st.isPlaying()) {
            int idx = lastPlayingIndex_;
            while (idx > 0 && !draft->items[(size_t)idx].isBeat())
                --idx;
            autoScrollTo(idx, /*anchor=*/true);
        } else if (!st.cursorIndex.has_value()) {
            autoScrollTo((int)draft->items.size() - 1, /*anchor=*/false);
        } else if (st.activeTrackIndex == trackIndex_) {
            autoScrollTo(*st.cursorIndex, /*anchor=*/false);
        }
    }

    void setPlayingItemIndex(int idx) {
        if (lastPlayingIndex_ == idx)
            return;
        lastPlayingIndex_ = idx;
        layoutItems(); // updates colourise() for every item

        // auto scroll to beat
        if (idx < 0) return;
        const auto &st = builder_.state();
        if (!st.isPlaying()) return;
        const auto *draft = (trackIndex_ >= 0 && trackIndex_ < (int)st.tracks.size())
            ? &st.tracks[(size_t)trackIndex_] : nullptr;
        if (draft == nullptr || draft->items.empty()) return;
        int beatIdx = idx;
        while (beatIdx > 0 && !draft->items[(size_t)beatIdx].isBeat())
            --beatIdx;
        autoScrollTo(beatIdx, /*anchor=*/true);
    }

    void setBeatWidthPublic(int w) {
        if (beatWidth_ == w) return;
        beatWidth_ = w;
        layoutItems();
    }

    void setStackedFractionsPublic(bool s) {
        if (stackedFractions_ == s) return;
        stackedFractions_ = s;
        layoutItems();
    }

    void resized() override {
        viewport_.setBounds(getLocalBounds());
        layoutItems();
    }

    void mouseDown(const juce::MouseEvent &e) override {
        if (builder_.state().isPlaying()) return;
        // Activate the track on a click anywhere on the strip background
        builder_.setActiveTrack(trackIndex_);
        const auto local = e.eventComponent == this
                               ? e.getPosition()
                               : e.getEventRelativeTo(this).getPosition();
        // Forward to item hit-testing inside content
        auto inContent = local + viewport_.getViewPosition();
        if (auto *hit = content_.getComponentAt(
                inContent.x - content_.getX(), inContent.y - content_.getY())) {
            if (auto *idxComp = dynamic_cast<ItemHit *>(hit)) {
                builder_.setCursor(idxComp->index);
            }
        }
    }

  private:
    struct ItemHit : public juce::Component {
        int index{0};
        TrackItem::Kind kind{TrackItem::Kind::Beat};

        void mouseDown(const juce::MouseEvent &) override {
            if (onClick)
                onClick();
        }
        std::function<void()> onClick;
        // colour fields driven by parent
        juce::Colour bg, border, text;
        float corner{4.0f};
        juce::String label;
        float fontSize{11.0f};
        float borderWidth{0.5f};
        int displayNum{1}, displayDenom{1};
        bool stacked{false};

        void paint(juce::Graphics &g) override {
            const auto r = getLocalBounds().toFloat().reduced(0.5f);
            g.setColour(bg);
            g.fillRoundedRectangle(r, corner);
            g.setColour(border);
            g.drawRoundedRectangle(r, corner, borderWidth);
            g.setColour(text);
            if (stacked && displayDenom != 1) {
                const float w = (float)getWidth();
                const float numSz   = juce::jlimit(11.0f, 16.0f, w * 0.33f);
                const float denomSz = numSz * 0.85f;
                const float totalH  = numSz + denomSz + 2.0f;
                const float startY  = ((float)getHeight() - totalH) * 0.5f;
                g.setFont(juce::Font(juce::FontOptions(numSz)).boldened());
                g.drawText(juce::String(displayNum),
                           getLocalBounds().withTrimmedBottom((int)std::round(denomSz + 2.0f)),
                           juce::Justification::centredBottom, false);
                g.setColour(text.withAlpha(0.6f));
                g.setFont(juce::Font(juce::FontOptions(denomSz)).boldened());
                g.drawText(juce::String(displayDenom),
                           getLocalBounds().withTrimmedTop((int)std::round(startY + numSz + 2.0f)),
                           juce::Justification::centredTop, false);
                (void)startY;
            } else {
                g.setFont(juce::Font(juce::FontOptions(fontSize)));
                g.drawText(label, getLocalBounds(), juce::Justification::centred, false);
            }
        }
    };

    void layoutItems() {
        const auto &st = builder_.state();
        if (trackIndex_ < 0 || trackIndex_ >= (int)st.tracks.size()) {
            content_.removeAllChildren();
            items_.clear();
            return;
        }
        const auto &draft = st.tracks[(size_t)trackIndex_];

        const int bracketW = 24, glyphW = 56;
        const int height = 42, pad = 6, gap = 4;
        const int contentH = height + pad * 2;

        // rebuild only when structure or beat width change
        bool needsRebuild = items_.size() != draft.items.size() ||
                            lastBeatWidth_ != beatWidth_;
        if (!needsRebuild) {
            for (int i = 0; i < (int)draft.items.size(); ++i)
                if (draft.items[(size_t)i].kind() != items_[(size_t)i]->kind) {
                    needsRebuild = true;
                    break;
                }
        }

        if (needsRebuild) {
            lastBeatWidth_ = beatWidth_;
            const int savedX = viewport_.getViewPositionX();
            content_.removeAllChildren();
            items_.clear();
            int x = pad;
            for (int i = 0; i < (int)draft.items.size(); ++i) {
                const auto &item = draft.items[(size_t)i];
                int width = item.isBeat() ? beatWidth_
                            : (item.isBracketOpen() || item.isBracketClose())
                                ? bracketW
                                : glyphW;
                auto hit = std::make_unique<ItemHit>();
                hit->index = i;
                hit->kind = item.kind();
                hit->onClick = [this, i] {
                    if (!builder_.state().isPlaying())
                        builder_.setActiveTrackAndCursor(trackIndex_, i);
                };
                hit->setBounds(x, pad, width, height);
                content_.addAndMakeVisible(hit.get());
                items_.push_back(std::move(hit));
                x += width + gap;
            }
            content_.setSize(juce::jmax(x + pad, viewport_.getWidth()),
                             contentH);
            viewport_.setViewPosition(savedX, 0);
        }

        // Always update colours/labels (cheap).
        const bool isActive = st.activeTrackIndex == trackIndex_;
        const auto cursorIdx = isActive ? st.cursorIndex : std::nullopt;
        for (int i = 0; i < (int)draft.items.size(); ++i) {
            const bool selected = cursorIdx.has_value() && *cursorIdx == i;
            const bool playing = st.isPlaying() && lastPlayingIndex_ == i;
            colourise(draft.items[(size_t)i], selected, playing, stackedFractions_,
                      *items_[(size_t)i]);
            items_[(size_t)i]->repaint();
        }
    }

    static void colourise(const TrackItem &item, bool selected, bool playing,
                          bool stacked, ItemHit &hit) {
        if (const auto *beat = item.getIf<TrackItem::Beat>()) {
            hit.label = juce::String(beat->label());
            hit.corner = 4.0f;
            hit.displayNum   = beat->displayNum;
            hit.displayDenom = beat->displayDenom;
            hit.stacked      = stacked;
            const float w = (float)hit.getWidth();
            const float baseSz = juce::jlimit(8.0f, 18.0f, w / 3.0f);
            hit.fontSize = beat->label().length() > 5 ? baseSz * 0.7f : baseSz;
            if (playing) {
                hit.bg = RhythmColors::beatPlayingBg();
                hit.border = RhythmColors::accentBright();
                hit.text = RhythmColors::accentBright();
                hit.borderWidth = 1.0f;
            } else if (selected) {
                hit.bg = RhythmColors::beatSelectedBg();
                hit.border = RhythmColors::beatSelectedBorder();
                hit.text = RhythmColors::beatSelectedText();
                hit.borderWidth = 1.0f;
            } else if (beat->isRest()) {
                hit.bg = RhythmColors::beatRestBg();
                hit.border = RhythmColors::bg3();
                hit.text = RhythmColors::textDim();
            } else {
                hit.bg = RhythmColors::beatActiveBg();
                hit.border = RhythmColors::beatActiveBorder();
                hit.text = RhythmColors::accent();
            }
        } else if (item.isBracketOpen() || item.isBracketClose()) {
            hit.label = item.isBracketOpen() ? "[" : "]";
            hit.corner = 3.0f;
            hit.fontSize = 18.0f;
            hit.bg = selected ? RhythmColors::beatSelectedBg()
                              : RhythmColors::bracketBg();
            hit.border = selected ? RhythmColors::beatSelectedBorder()
                                  : RhythmColors::bracketBorder();
            hit.text = selected ? RhythmColors::beatSelectedText()
                                : RhythmColors::bracketText();
            hit.borderWidth = selected ? 1.0f : 0.5f;
        } else if (const auto *r = item.getIf<TrackItem::Repeat>()) {
            hit.label = r->isInfinite() ? juce::String::fromUTF8(u8"×∞")
                                        : juce::String::fromUTF8(u8"×") +
                                              juce::String(r->count);
            hit.corner = 3.0f;
            hit.fontSize = 12.0f;
            hit.bg = RhythmColors::repeatBg();
            hit.border = selected ? RhythmColors::repeatText().withAlpha(0.5f)
                                  : RhythmColors::border1();
            hit.text = selected ? RhythmColors::repeatText()
                                : RhythmColors::repeatText().withAlpha(0.75f);
            hit.borderWidth = selected ? 1.0f : 0.5f;
        } else if (const auto *m = item.getIf<TrackItem::Modulation>()) {
            hit.label = juce::String::fromUTF8(u8"×") + juce::String(m->p) +
                        "/" + juce::String(m->q);
            hit.corner = 3.0f;
            hit.fontSize = 11.0f;
            hit.bg = selected ? RhythmColors::cautionBg() : RhythmColors::bg0();
            hit.border = selected ? RhythmColors::cautionBorder()
                                  : RhythmColors::border1();
            hit.text = selected ? RhythmColors::caution()
                                : RhythmColors::caution().withAlpha(0.75f);
            hit.borderWidth = selected ? 1.0f : 0.5f;
        } else if (const auto *sb = item.getIf<TrackItem::SetBpm>()) {
            hit.label = "=" + juce::String((int)sb->bpm);
            hit.corner = 3.0f;
            hit.fontSize = 11.0f;
            hit.bg = RhythmColors::setBpmBg();
            hit.border = selected ? RhythmColors::setBpmText().withAlpha(0.5f)
                                  : RhythmColors::border1();
            hit.text = selected ? RhythmColors::setBpmText()
                                : RhythmColors::setBpmText().withAlpha(0.75f);
            hit.borderWidth = selected ? 1.0f : 0.5f;
        }
    }

    // anchor=true: make left; false: scroll minimally
    void autoScrollTo(int idx, bool anchor) {
        if (idx < 0 || idx >= (int)items_.size())
            return;
        const auto &comp = *items_[(size_t)idx];
        const int viewX = viewport_.getViewPositionX();
        const int viewW = viewport_.getWidth();
        const int pad   = 8;
        if (anchor) {
            const int target = juce::jmax(0, comp.getX() - pad);
            viewport_.setViewPosition(target, 0);
        } else {
            const int left  = comp.getX();
            const int right = comp.getRight();
            if (left >= viewX && right <= viewX + viewW)
                return; // already fully visible
            const int target = left < viewX
                ? juce::jmax(0, left - pad)
                : right - viewW + pad;
            viewport_.setViewPosition(juce::jmax(0, target), 0);
        }
    }

    TrackBuilder &builder_;
    int &trackIndex_;
    juce::Viewport viewport_;
    juce::Component content_;
    std::vector<std::unique_ptr<ItemHit>> items_;
    int lastPlayingIndex_{-1};
    int beatWidth_{52};
    int lastBeatWidth_{52};
    bool stackedFractions_{false};

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR(ItemStrip)
};

TrackRowComponent::~TrackRowComponent() = default;
TrackListComponent::~TrackListComponent() = default;

TrackRowComponent::TrackRowComponent(TrackBuilder &builder, int trackIndex)
    : builder_(builder), trackIndex_(trackIndex),
      deleteButton_(juce::String::fromUTF8(u8"×")) {
    nameLabel_.setJustificationType(juce::Justification::centredLeft);
    nameLabel_.setFont(juce::Font(juce::FontOptions(13.0f)));
    addAndMakeVisible(nameLabel_);
    addAndMakeVisible(muteChip_);
    addAndMakeVisible(soloChip_);

    deleteButton_.setStateColor(ChipButton::StateColor::Custom);
    deleteButton_.setColours(RhythmColors::bg1(), RhythmColors::border0(),
                             RhythmColors::deleteText());
    deleteButton_.setFontSize(22.0f);
    deleteButton_.setOnClick([this] {
        if (!builder_.state().isPlaying() && onDeleteTrack)
            onDeleteTrack(trackIndex_);
    });
    addAndMakeVisible(deleteButton_);

    muteChip_.setOnClick([this] {
        if (builder_.state().isPlaying()) return;
        if (auto *d = draft())
            builder_.setTrackMuted(trackIndex_, !d->muted);
    });
    soloChip_.setOnClick([this] {
        if (builder_.state().isPlaying()) return;
        if (auto *d = draft())
            builder_.setTrackSoloed(trackIndex_, !d->soloed);
    });
    soundChip_.setOnClick([this] {
        if (!builder_.state().isPlaying() && onTrackSound)
            onTrackSound(trackIndex_);
    });
    addAndMakeVisible(soundChip_);

    strip_ = std::make_unique<ItemStrip>(builder_, trackIndex_);
    addAndMakeVisible(strip_.get());
}

const TrackDraft *TrackRowComponent::draft() const {
    const auto &s = builder_.state();
    if (trackIndex_ < 0 || trackIndex_ >= (int)s.tracks.size())
        return nullptr;
    return &s.tracks[(size_t)trackIndex_];
}

void TrackRowComponent::syncToState() {
    if (auto *d = draft()) {
        nameLabel_.setText(d->label, juce::dontSendNotification);
        const bool isActive = builder_.state().activeTrackIndex == trackIndex_;
        nameLabel_.setColour(juce::Label::textColourId,
                             isActive ? RhythmColors::accent()
                                      : RhythmColors::textSecondary());
        muteChip_.setActive(d->muted);
        soloChip_.setActive(d->soloed);
        soundChip_.setActive(d->defaultSoundId.has_value());
    }
    strip_->syncToState();
    repaint();
}

void TrackRowComponent::scrollIntoView(int /*itemIndex*/) {
    strip_->syncToState();
}

void TrackRowComponent::paint(juce::Graphics &g) {
    const bool isActive = builder_.state().activeTrackIndex == trackIndex_;
    g.fillAll(isActive ? RhythmColors::trackActiveBg() : RhythmColors::bg1());
    g.setColour(RhythmColors::border0());
    g.drawLine(0.0f, (float)getHeight() - 0.5f, (float)getWidth(),
               (float)getHeight() - 0.5f, 0.5f);
}

void TrackRowComponent::resized() {
    auto bounds = getLocalBounds();
    auto left = bounds.removeFromLeft(96).reduced(8, 6);
    nameLabel_.setBounds(left.removeFromTop(18));
    left.removeFromTop(4);
    auto chipRow = left.removeFromTop(22);
    muteChip_.setBounds(chipRow.removeFromLeft(22));
    chipRow.removeFromLeft(3);
    soloChip_.setBounds(chipRow.removeFromLeft(22));
    chipRow.removeFromLeft(3);
    soundChip_.setBounds(chipRow.removeFromLeft(22));

    deleteButton_.setBounds(bounds.removeFromRight(30).reduced(2, 8));
    strip_->setBounds(bounds);
}

void TrackRowComponent::mouseDown(const juce::MouseEvent &) {
    if (!builder_.state().isPlaying())
        builder_.setActiveTrack(trackIndex_);
}

void TrackRowComponent::setBeatWidth(int w) { strip_->setBeatWidthPublic(w); }
void TrackRowComponent::setStackedFractions(bool s) { strip_->setStackedFractionsPublic(s); }
void TrackRowComponent::setPlayingIndex(int idx) { strip_->setPlayingItemIndex(idx); }

TrackListComponent::TrackListComponent(TrackBuilder &builder)
    : builder_(builder) {
    addAndMakeVisible(viewport_);
    viewport_.setViewedComponent(&content_, false);
    viewport_.setScrollBarsShown(true, false);

    addRowButton_.setStateColor(ChipButton::StateColor::Custom);
    addRowButton_.setColours(RhythmColors::bg1(), RhythmColors::border0(),
                             RhythmColors::textSecondary());
    addRowButton_.setCornerRadius(0.0f);
    addRowButton_.setOnClick([this] {
        if (!builder_.state().isPlaying()) {
            builder_.addTrack();
            syncToState();
        }
    });
    content_.addAndMakeVisible(addRowButton_);

    copyRowButton_.setStateColor(ChipButton::StateColor::Custom);
    copyRowButton_.setColours(RhythmColors::bg1(), RhythmColors::border0(),
                              RhythmColors::textSecondary());
    copyRowButton_.setCornerRadius(0.0f);
    copyRowButton_.setOnClick([this] {
        if (!builder_.state().isPlaying()) {
            builder_.copyTrack();
            syncToState();
        }
    });
    content_.addAndMakeVisible(copyRowButton_);

    rebuildRows();
}

void TrackListComponent::syncToState() {
    const int needed = (int)builder_.state().tracks.size();
    if ((int)rows_.size() != needed)
        rebuildRows();
    for (int i = 0; i < (int)rows_.size(); ++i) {
        rows_[(size_t)i]->setTrackIndex(i);
        rows_[(size_t)i]->onTrackSound = onTrackSound;
        rows_[(size_t)i]->syncToState();
    }
    resized();
}

void TrackListComponent::rebuildRows() {
    for (auto &r : rows_)
        content_.removeChildComponent(r.get());
    rows_.clear();
    const int n = (int)builder_.state().tracks.size();
    rows_.reserve((size_t)n);
    for (int i = 0; i < n; ++i) {
        auto row = std::make_unique<TrackRowComponent>(builder_, i);
        row->onDeleteTrack = [this](int idx) {
            builder_.deleteTrack(idx);
            syncToState();
        };
        row->onTrackSound = onTrackSound;
        row->setBeatWidth(beatWidth_);
        row->setStackedFractions(stackedFractions_);
        row->syncToState();
        content_.addAndMakeVisible(row.get());
        rows_.push_back(std::move(row));
    }
}

void TrackListComponent::setTrackPlayingIndex(const std::string &trackId, int itemIdx) {
    const auto &tracks = builder_.state().tracks;
    for (int i = 0; i < (int)rows_.size() && i < (int)tracks.size(); ++i) {
        if (tracks[(size_t)i].id == trackId) {
            rows_[(size_t)i]->setPlayingIndex(itemIdx);
            return;
        }
    }
}

void TrackListComponent::setBeatWidth(int w) {
    beatWidth_ = w;
    for (auto &r : rows_) r->setBeatWidth(w);
}

void TrackListComponent::setStackedFractions(bool s) {
    stackedFractions_ = s;
    for (auto &r : rows_) r->setStackedFractions(s);
}

void TrackListComponent::paint(juce::Graphics &g) {
    g.fillAll(RhythmColors::bg1());
}

void TrackListComponent::resized() {
    viewport_.setBounds(getLocalBounds());
    const int rowHeight = 70;
    const int addRowHeight = 36;
    int y = 0;
    for (auto &r : rows_) {
        r->setBounds(0, y, viewport_.getWidth(), rowHeight);
        y += rowHeight;
    }
    const int w = viewport_.getWidth();
    copyRowButton_.setBounds(0, y, w / 2, addRowHeight);
    addRowButton_.setBounds(w / 2, y, w - w / 2, addRowHeight);
    y += addRowHeight;
    content_.setSize(w, y);
}

} // namespace rhythm
