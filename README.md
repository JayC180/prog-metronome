# juce_metronome

A C++/JUCE desktop port of `prog-metronome` — same metronome logic, but the output is a **VST3 / AU plugin** and a **cross-platform standalone app** (Windows / macOS / Linux).

This README **does not repeat** the metronome concepts themselves (rhythm builder, tracks, modulation, etc. — the original project already documents those). It only explains the **project layout** and **how the multi-platform build works**, since you haven't worked with JUCE before.

---

## 1. What JUCE is, and how it maps to your Kotlin/Compose project

JUCE is a C++ audio framework. Mental model:

| Kotlin / Compose Multiplatform | JUCE equivalent |
|---|---|
| Compose Multiplatform UI | `juce_gui_basics` — hand-painted widgets, each one is a `juce::Component` that implements `paint(Graphics&)` + `resized()` |
| `Modifier` / `Theme` | `LookAndFeel` — a global style sheet (colors, fonts, how controls are drawn) |
| Gradle `commonMain` + per-platform source sets | **No such concept.** One C++ source tree compiles for every platform; platform differences are gated with `#if JUCE_MAC / JUCE_WINDOWS / JUCE_LINUX` |
| Audio / MIDI abstractions | `juce_audio_processors` + `juce_audio_devices` (host ↔ plugin, driver abstraction) |
| Project config (`build.gradle.kts`) | `CMakeLists.txt` |
| Gradle wrapper | CMake + a per-platform IDE generator (Xcode / Visual Studio / Ninja) |

A JUCE project is essentially a **plugin**, and JUCE auto-generates several "wrappers" (formats) for it:
- **Standalone** — a self-contained app (`.exe` / `.app` / Linux binary), with JUCE's built-in audio device picker UI
- **VST3** — a `.vst3` bundle that loads into any DAW (Cubase / Reaper / Ableton…)
- **AU** (macOS only) — a `.component`, used by Logic / GarageBand

Same source, three artifacts; only the entry point differs (CMake generates the wrapper for each).

---

## 2. File layout — what each thing corresponds to on the Kotlin side

```
Source/
├── PluginProcessor.{h,cpp}     ← Plugin entry point. Roughly: top-level ViewModel + the audio engine class
├── PluginEditor.{h,cpp}        ← UI entry point. JUCE instantiates this when a plugin window opens
│
├── core/                       ← Maps to the `core` part of the original project's commonMain
│   ├── BeatNode.{h,cpp}        Beat-tree node
│   ├── Rational.h              Rationals (same as in your Kotlin version)
│   └── TempoContext.h
│
├── builder/                    ← Rhythm-builder state machine (direct translation of the original `builder` package)
│   ├── TrackDraft.{h,cpp}      Editable state of a single track
│   ├── TrackItem.{h,cpp}       beats / brackets / repeats / tempo nodes
│   ├── TrackBuilderState.*     Global state (all tracks + bpm + cursor + …)
│   ├── TrackBuilder.{h,cpp}    Controller — every state mutation lives here (addTrack / setBpm / enterBeat…)
│   ├── Interpreter.{h,cpp}     Compiles a TrackDraft into a timeline (a sequence of PrecomputedEvent)
│   └── TapTempoCalculator.{h,cpp}
│
├── scheduler/                  ← Turns a timeline into "what to play, when"
│   ├── PrecomputedEvent.h      Interpreter output. One InterpretResult per track
│   ├── ScheduledEvent.h        An event queued and waiting to be dispatched
│   ├── StreamClock.{h,cpp}     Per-track clock (handles loop, infinite repeat)
│   └── Transport.{h,cpp}       Two-thread engine: the scheduler thread queues events ~150 ms ahead,
│                               the dispatcher thread fires them at the right moment
│
├── audio/                      ← Audio backend
│   ├── AudioEngine.h           Interface: trigger(soundId, volume, atNanos)
│   ├── JuceAudioEngine.*       Concrete impl: JUCE Mixer + sample playback
│   ├── SoundConfig.h / SoundInfo.h
│   └── SoundMap.*              soundId → AudioBuffer lookup table
│
├── persistence/                ← JSON save / load
│   └── ProjectSerializer.{h,cpp}   Built on a juce::var tree (not reflection-based like kotlinx.serialization)
│
└── ui/                         ← All UI
    ├── MainComponent.*         Root component (≈ the original project's MainScreen composable)
    ├── TopBarComponent.*       Top bar: BPM, play, tap-tempo
    ├── BottomPanelComponent.*  Bottom panel: beat input, sound picker
    ├── TrackRowComponent.*     One track's row (label + M/S chip + content strip)
    ├── dialogs/RhythmDialogs.* Dialogs (sound picker, etc.)
    ├── RhythmLookAndFeel.*     Global style: colors, type sizes, button shapes (≈ Compose's Theme)
    ├── RhythmColors.h / ThemeConfig.h
    └── UiHelpers.{h,cpp}       Small reusable bits (chips, buttons, …)
```

### Audio-thread constraints (important)

JUCE's audio callback runs on a **real-time thread** — inside it you **must not**:
- Allocate memory (`new` / `std::make_shared` / pushing into a `vector` that might grow)
- Wait on locks (avoid `std::mutex::lock` if it could ever block)
- Make any syscall (file I/O, logging, networking, …)

That's why `Transport` deliberately does the "prepare events" work on a regular scheduler thread, and only the fastest operations (e.g. reading from a ring buffer) happen on the audio thread. Keep this in mind whenever you touch the audio path.

---

## 3. CMakeLists.txt at a glance

The equivalent of `build.gradle.kts`, only ~100 lines. Three sections is all you need to understand:

```cmake
# Section 1: pull in JUCE.
# Prefer a local checkout at ~/JUCE_local (fast on a dev machine);
# on CI, where that directory doesn't exist, FetchContent pulls JUCE 8.0.13.
add_subdirectory(...)

# Section 2: declare the plugin target. The FORMATS line decides which artifacts get built.
juce_add_plugin(RhythmEngine
    FORMATS VST3 AU Standalone        # ← key line. Add LV2/AAX here if you ever want them
    BUNDLE_ID com.elsasystems.rhythmengine
    PLUGIN_MANUFACTURER_CODE Elsa
    PLUGIN_CODE Rhy1
    IS_SYNTH TRUE
    COPY_PLUGIN_AFTER_BUILD TRUE      # ← auto-installs the plugin to the system plugin dir so any DAW sees it
    ...)

# Section 3: list source files + link JUCE modules
target_sources(RhythmEngine PRIVATE Source/...)
target_link_libraries(RhythmEngine PRIVATE
    juce::juce_audio_utils            ← these are JUCE's "packages"
    juce::juce_gui_basics
    ...)
```

A JUCE **module** is its package system — one folder per module, link only what you need. `juce_dsp` is filters/FFT; `juce_gui_extra` has things like webview; we only link the ones we actually use.

---

## 4. How to build

### Locally (recommended)

You need **CMake ≥ 3.22** and a platform compiler:

```bash
# The same command works on all three platforms:
cmake -B build -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build --target RhythmEngine_Standalone
# For VST3:           --target RhythmEngine_VST3
# For AU on macOS:    --target RhythmEngine_AU
```

Generator alternatives:
- macOS, if you want to debug in Xcode → `-G Xcode`, then `open build/RhythmEngine.xcodeproj`
- Windows → `-G "Visual Studio 17 2022" -A x64` (or use Ninja)
- Linux → Ninja or the default Makefile generator both work

Artifact paths: `build/RhythmEngine_artefacts/Release/{Standalone,VST3,AU}/`

With `COPY_PLUGIN_AFTER_BUILD TRUE`, VST3/AU artifacts are also copied into the system plugin directories (`~/Library/Audio/Plug-Ins/...` / `C:\Program Files\Common Files\VST3\` / `~/.vst3/`), so any DAW will pick them up after a local build.

### CI (GitHub Actions)

`.github/workflows/build.yml` at the repo root — three jobs running in parallel:

| Job | Runner | Dependencies | Artifacts |
|---|---|---|---|
| `windows` | `windows-latest` | Comes with VS 2022 + CMake | `RhythmEngine.exe` + `RhythmEngine.vst3/` |
| `macos`   | `macos-latest`   | Comes with Xcode Command Line Tools | `RhythmEngine.app` + `.vst3` + `.component` |
| `linux`   | `ubuntu-latest`  | `apt-get install` for ALSA / JACK / X11 headers | `RhythmEngine` (binary) + `RhythmEngine.vst3/` |

Each job follows the same three steps:

```
checkout → cmake -B build → cmake --build → upload artifact
```

Only the dependency-install step varies between platforms. This is exactly why CMake is the de facto standard in cross-platform C++ — **one build script handles every platform**, and the CI config has almost no platform-specific logic in it.

Triggers:
- push to `main` (every commit triggers a build)
- push of a `v*` tag (used for releases)
- any pull request
- manual `workflow_dispatch` (button on the Actions page)

JUCE sources are not vendored into the repo — on a fresh CI run, CMake's `FetchContent` clones JUCE 8.0.13 from GitHub. The first run of each job spends ~3 minutes on this; adding a cache step later would save that time.

### Signing

**All artifacts are currently unsigned:**
- macOS: a downloaded build is blocked by Gatekeeper on first launch — users have to **right-click → Open** once. Fully clean signing requires an Apple Developer ID ($99/year) plus signing secrets in CI. Deferred.
- Windows: an unsigned .exe gets a SmartScreen "unknown publisher" warning. Same idea — ignore for now, or buy a certificate later.

---

## 5. How this port differs from the Kotlin original

The big one: **there is no commonMain / iosMain / androidMain split** — a single C++ tree handles all desktop platforms, and mobile isn't covered here (JUCE can target iOS/Android, but it's a lot of extra work; deferred).

The business logic layer is mostly a 1:1 translation:
- `builder/` mirrors the original project's `builder` package
- `core/BeatNode` matches the same-named class in the original
- `Interpreter` emits a `PrecomputedEvent` list — this is a JUCE-port-specific optimization (timeline is compiled ahead of time, so the audio thread does zero allocation)
- The UI is **rewritten** (Compose doesn't cross to C++/JUCE), but layout and interactions follow the original
