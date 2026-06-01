#include "MainComponent.h"
#include "dialogs/RhythmDialogs.h"
#include "../persistence/ProjectSerializer.h"
#include "../PluginProcessor.h"

namespace rhythm
{

MainComponent::MainComponent (RhythmEngineProcessor& processor)
    : processor_ (processor),
      builder_   (processor.builder()),
      availableSounds_ {
          { "default", "default", false },
          { "hi",      "hi click", false },
          { "lo",      "lo click", false },
          { "accent",  "accent",   false },
          { "hat",     "hat",      false },
      },
      topBar_      (builder_),
      trackList_   (builder_),
      bottomPanel_ (builder_)
{
    setLookAndFeel (&lookAndFeel_);

    addAndMakeVisible (topBar_);
    addAndMakeVisible (trackList_);
    addAndMakeVisible (bottomPanel_);

    builder_.setOnStateChanged ([this] (const TrackBuilderState&)
    {
        processor_.markDirty();
        triggerAsyncUpdate();
    });

    // Settings button → popup menu: file ops + theme
    topBar_.onSettingsClicked = [this]
    {
        juce::PopupMenu themeMenu;
        const auto& themes = BuiltInThemes::all();
        for (int i = 0; i < (int) themes.size(); ++i)
        {
            themeMenu.addItem (200 + i, juce::String (themes[(size_t) i].name), true,
                               juce::String (themes[(size_t) i].name) == juce::String (RhythmColors::active().name));
        }

        juce::PopupMenu menu;
        menu.addItem (1, "New Project");
        menu.addItem (2, "Open Project...");
        menu.addSeparator();
        menu.addItem (3, "Save",      true);
        menu.addItem (4, "Save As...", true);
        menu.addSeparator();
        menu.addSubMenu ("Theme", themeMenu);

        menu.showMenuAsync (juce::PopupMenu::Options().withTargetComponent (&topBar_),
            [this] (int result)
            {
                if      (result == 1) newProject();
                else if (result == 2) openProject();
                else if (result == 3) saveProject();
                else if (result == 4) saveProjectAs();
                else if (result >= 200)
                {
                    const int idx = result - 200;
                    const auto& themes = BuiltInThemes::all();
                    if (idx >= 0 && idx < (int) themes.size())
                    {
                        RhythmColors::setActive (themes[(size_t) idx]);
                        rebuildFromState();
                        repaint();
                    }
                }
            });
    };

    topBar_.onBpmClicked = [this]
    {
        auto cb = [this] (double v) { builder_.setBpm (v); };
        showRhythmDialog (this, std::make_unique<BpmInputDialog> (builder_.state().bpm, cb));
    };

    topBar_.onProjectNameClicked = [this] { renameProject(); };

    bottomPanel_.onMmRequested = [this]
    {
        const auto* item = builder_.state().cursorItem();
        std::optional<int> p, q;
        if (item != nullptr)
            if (const auto* m = item->getIf<TrackItem::Modulation>()) { p = m->p; q = m->q; }
        showRhythmDialog (this,
            std::make_unique<MmDialog> (p, q, [this, idx = builder_.state().cursorIndex,
                                                editing = item != nullptr && item->isModulation()]
                                              (int pp, int qq)
            {
                if (editing && idx.has_value()) builder_.replaceModulation (*idx, pp, qq);
                else                            builder_.commitModulation (pp, qq);
            }));
    };

    bottomPanel_.onSetBpmRequested = [this]
    {
        const auto* item = builder_.state().cursorItem();
        std::optional<double> initial;
        bool editing = false;
        if (item != nullptr)
            if (const auto* sb = item->getIf<TrackItem::SetBpm>()) { initial = sb->bpm; editing = true; }
        showRhythmDialog (this,
            std::make_unique<SetBpmDialog> (builder_.state().bpm, initial,
                [this, idx = builder_.state().cursorIndex, editing] (double v)
                {
                    if (editing && idx.has_value()) builder_.replaceSetBpm (*idx, v);
                    else                            builder_.commitSetBpm (v);
                }));
    };

    bottomPanel_.onCustomBeatRequested = [this]
    {
        const bool editingBeat = builder_.state().isEditMode()
                              && builder_.state().cursorItem() != nullptr
                              && builder_.state().cursorItem()->isBeat();
        showRhythmDialog (this,
            std::make_unique<CustomNumberDialog> ("Beat value",
                                                  "numerator - must be greater than 0",
                [this, editingBeat] (int n)
                {
                    if (editingBeat) builder_.replaceBeat (n);
                    else             builder_.enterBeat (n);
                }));
    };

    bottomPanel_.onCustomDenomRequested = [this]
    {
        showRhythmDialog (this,
            std::make_unique<CustomNumberDialog> ("Subdivision",
                                                  "denominator",
                [this] (int n) { builder_.setDenom (n); }));
    };

    bottomPanel_.onRepeatCustomRequested = [this]
    {
        showRhythmDialog (this,
            std::make_unique<RepeatDialog> (
                [this, editing = builder_.state().isEditMode()
                                && builder_.state().cursorItem() != nullptr
                                && builder_.state().cursorItem()->isRepeat()] (int count)
                {
                    if (count == TrackItem::Repeat::INFINITE_COUNT) builder_.setRepeatInfinite();
                    else if (editing)                              builder_.replaceRepeat (count);
                    else                                           builder_.setRepeatCustom (count);
                }));
    };

    bottomPanel_.onChangeBeatSound = [this] { openBeatSoundPicker(); };

    updateProjectNameDisplay();
    rebuildFromState();

    startTimer (30000); // autosave every 30s
}

void MainComponent::openBeatSoundPicker()
{
    const auto& s = builder_.state();
    if (! s.cursorIndex.has_value()) return;
    const auto* t = s.activeTrack();
    if (t == nullptr) return;
    const int idx = *s.cursorIndex;
    if (idx < 0 || idx >= (int) t->items.size()) return;
    const auto* beat = t->items[(size_t) idx].getIf<TrackItem::Beat>();
    if (beat == nullptr) return;

    showRhythmDialog (this,
        std::make_unique<SoundPickerDialog> (
            availableSounds_,
            beat->soundId,
            [this, idx] (const std::string& soundId)
            {
                builder_.setBeatSound (idx, soundId);
            }));
}

void MainComponent::newProject()
{
    confirmIfDirty ([this]
    {
        TrackBuilderState fresh;
        fresh.tracks.push_back (TrackBuilder::newTrackDraft (0));
        fresh.bpm = 120.0;
        builder_.restoreState (fresh);
        processor_.setProjectName ("Untitled");
        processor_.setProjectFile ({});
        processor_.clearDirty();
        updateProjectNameDisplay();
    });
}

void MainComponent::openProject()
{
    confirmIfDirty ([this]
    {
        fileChooser_ = std::make_unique<juce::FileChooser> (
            "Open Project",
            juce::File::getSpecialLocation (juce::File::userDocumentsDirectory),
            "*.rhy");

        fileChooser_->launchAsync (
            juce::FileBrowserComponent::openMode | juce::FileBrowserComponent::canSelectFiles,
            [this] (const juce::FileChooser& fc)
            {
                const auto f = fc.getResult();
                if (f == juce::File{}) return;

                if (! processor_.loadFromFile (f))
                {
                    juce::NativeMessageBox::showAsync (
                        juce::MessageBoxOptions()
                            .withTitle ("Could not open file")
                            .withMessage ("The file could not be read or is invalid.")
                            .withButton ("OK"),
                        nullptr);
                    return;
                }
                updateProjectNameDisplay();
                rebuildFromState();
            });
    });
}

void MainComponent::saveProject()
{
    if (processor_.projectFile().existsAsFile())
    {
        if (! processor_.saveToFile (processor_.projectFile()))
        {
            juce::NativeMessageBox::showAsync (
                juce::MessageBoxOptions()
                    .withTitle ("Save failed")
                    .withMessage ("Could not write to file.")
                    .withButton ("OK"),
                nullptr);
            return;
        }
        updateProjectNameDisplay();
    }
    else
    {
        saveProjectAs();
    }
}

void MainComponent::saveProjectAs()
{
    fileChooser_ = std::make_unique<juce::FileChooser> (
        "Save Project As",
        juce::File::getSpecialLocation (juce::File::userDocumentsDirectory)
            .getChildFile (juce::String (processor_.projectName()) + ".rhy"),
        "*.rhy");

    fileChooser_->launchAsync (
        juce::FileBrowserComponent::saveMode
            | juce::FileBrowserComponent::canSelectFiles
            | juce::FileBrowserComponent::warnAboutOverwriting,
        [this] (const juce::FileChooser& fc)
        {
            auto f = fc.getResult();
            if (f == juce::File{}) return;
            if (! f.hasFileExtension ("rhy"))
                f = f.withFileExtension ("rhy");

            // Use the filename (without extension) as the project name
            processor_.setProjectName (f.getFileNameWithoutExtension().toStdString());

            if (! processor_.saveToFile (f))
            {
                juce::NativeMessageBox::showAsync (
                    juce::MessageBoxOptions()
                        .withTitle ("Save failed")
                        .withMessage ("Could not write to file.")
                        .withButton ("OK"),
                    nullptr);
                return;
            }
            updateProjectNameDisplay();
        });
}

void MainComponent::renameProject()
{
    auto* alert = new juce::AlertWindow ("Rename Project", "", juce::MessageBoxIconType::NoIcon);
    alert->addTextEditor ("name", juce::String (processor_.projectName()), "Project name:");
    alert->addButton ("OK",     1, juce::KeyPress (juce::KeyPress::returnKey));
    alert->addButton ("Cancel", 0, juce::KeyPress (juce::KeyPress::escapeKey));
    alert->enterModalState (true,
        juce::ModalCallbackFunction::create ([this, alert] (int result)
        {
            if (result == 1)
            {
                auto name = alert->getTextEditorContents ("name").trim();
                if (name.isNotEmpty())
                {
                    processor_.setProjectName (name.toStdString());
                    processor_.markDirty();
                    updateProjectNameDisplay();
                }
            }
        }),
        true);
}

void MainComponent::confirmIfDirty (std::function<void()> onProceed)
{
    if (! processor_.isDirty())
    {
        onProceed();
        return;
    }
    juce::NativeMessageBox::showAsync (
        juce::MessageBoxOptions()
            .withTitle ("Unsaved Changes")
            .withMessage ("You have unsaved changes. Discard?")
            .withButton ("Discard")
            .withButton ("Cancel"),
        [proceed = std::move (onProceed)] (int result)
        {
            if (result == 0) proceed(); // first button is Discard
        });
}

void MainComponent::updateProjectNameDisplay()
{
    auto name = juce::String (processor_.projectName());
    if (processor_.isDirty()) name += " *";
    topBar_.setProjectName (name);
}

juce::File MainComponent::autosavePath()
{
    return juce::File::getSpecialLocation (juce::File::userApplicationDataDirectory)
               .getChildFile ("RhythmEngine")
               .getChildFile ("autosave.rhy");
}

MainComponent::~MainComponent()
{
    stopTimer();
    builder_.setOnStateChanged (nullptr);
    setLookAndFeel (nullptr);
}

void MainComponent::timerCallback()
{
    if (! processor_.isDirty()) return;
    const auto path = autosavePath();
    path.getParentDirectory().createDirectory();
    const std::string json = ProjectSerializer::serialize (
        builder_.state(), processor_.projectName());
    path.replaceWithText (juce::String (json));
}

void MainComponent::handleAsyncUpdate()
{
    rebuildFromState();
    updateProjectNameDisplay(); // keep dirty indicator in sync
}

void MainComponent::rebuildFromState()
{
    topBar_.syncToState();
    trackList_.syncToState();
    bottomPanel_.syncToState();
}

void MainComponent::paint (juce::Graphics& g)
{
    g.fillAll (RhythmColors::bg0());
}

void MainComponent::resized()
{
    auto bounds = getLocalBounds();
    topBar_.setBounds (bounds.removeFromTop (52));

    if (bounds.getWidth() >= 600)
    {
        // Landscape: tracks left, bottom panel right.
        const int rightW = juce::jmin (bounds.getWidth() * 5 / 12, 420);
        bottomPanel_.setBounds (bounds.removeFromRight (rightW));
        trackList_.setBounds (bounds);
    }
    else
    {
        bottomPanel_.setBounds (bounds.removeFromBottom (340));
        trackList_.setBounds (bounds);
    }
}

} // namespace rhythm
