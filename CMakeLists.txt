cmake_minimum_required(VERSION 3.22)

project(RhythmEngine VERSION 0.1.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_OSX_DEPLOYMENT_TARGET "10.13" CACHE STRING "")

# Statically link the MSVC runtime so juceaide.exe (and our plugin) don't depend
# on VCRUNTIME DLLs being on PATH — fixes "Testing juceaide failed" on CI runners
# that have VS Build Tools installed but no Dev Cmd environment active.
if(MSVC)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

# JUCE 8.0.13.
# Prefer a local checkout (set JUCE_LOCAL_PATH or fall back to ~/JUCE_local
# for local dev); otherwise FetchContent pulls JUCE from upstream — used by CI
# runners that don't have a local copy.
if(DEFINED ENV{JUCE_LOCAL_PATH} AND EXISTS "$ENV{JUCE_LOCAL_PATH}/CMakeLists.txt")
    message(STATUS "JUCE: using local path $ENV{JUCE_LOCAL_PATH}")
    add_subdirectory("$ENV{JUCE_LOCAL_PATH}" JUCE EXCLUDE_FROM_ALL)
elseif(EXISTS "$ENV{HOME}/JUCE_local/CMakeLists.txt")
    message(STATUS "JUCE: using ~/JUCE_local")
    add_subdirectory("$ENV{HOME}/JUCE_local" JUCE EXCLUDE_FROM_ALL)
else()
    message(STATUS "JUCE: fetching 8.0.13 from upstream")
    include(FetchContent)
    FetchContent_Declare(JUCE
        GIT_REPOSITORY https://github.com/juce-framework/JUCE.git
        GIT_TAG        8.0.13
        GIT_SHALLOW    TRUE)
    FetchContent_MakeAvailable(JUCE)
endif()

juce_add_plugin(RhythmEngine
    COMPANY_NAME "ELSA Systems"
    BUNDLE_ID com.elsasystems.rhythmengine
    IS_SYNTH TRUE
    NEEDS_MIDI_INPUT FALSE
    NEEDS_MIDI_OUTPUT FALSE
    IS_MIDI_EFFECT FALSE
    EDITOR_WANTS_KEYBOARD_FOCUS FALSE
    COPY_PLUGIN_AFTER_BUILD TRUE
    PLUGIN_MANUFACTURER_CODE Elsa
    PLUGIN_CODE Rhy1
    FORMATS VST3 AU Standalone
    PRODUCT_NAME "RhythmEngine")

target_sources(RhythmEngine PRIVATE
    Source/PluginProcessor.cpp
    Source/PluginEditor.cpp

    Source/core/BeatNode.cpp

    Source/builder/TrackItem.cpp
    Source/builder/TrackDraft.cpp
    Source/builder/TrackBuilderState.cpp
    Source/builder/Interpreter.cpp
    Source/builder/TrackBuilder.cpp
    Source/builder/TapTempoCalculator.cpp

    Source/scheduler/StreamClock.cpp
    Source/scheduler/Transport.cpp

    Source/audio/SoundMap.cpp
    Source/audio/JuceAudioEngine.cpp

    Source/persistence/ProjectSerializer.cpp

    Source/ui/RhythmLookAndFeel.cpp
    Source/ui/UiHelpers.cpp
    Source/ui/TopBarComponent.cpp
    Source/ui/TrackRowComponent.cpp
    Source/ui/BottomPanelComponent.cpp
    Source/ui/MainComponent.cpp
    Source/ui/dialogs/RhythmDialogs.cpp)

target_include_directories(RhythmEngine PRIVATE Source)

target_compile_definitions(RhythmEngine PUBLIC
    JUCE_WEB_BROWSER=0
    JUCE_USE_CURL=0
    JUCE_VST3_CAN_REPLACE_VST2=0
    JUCE_DISPLAY_SPLASH_SCREEN=0)

target_link_libraries(RhythmEngine PRIVATE
    juce::juce_audio_utils
    juce::juce_audio_processors
    juce::juce_audio_formats
    juce::juce_audio_devices
    juce::juce_audio_basics
    juce::juce_audio_plugin_client
    juce::juce_dsp
    juce::juce_gui_basics
    juce::juce_gui_extra
    juce::juce_core
    juce::juce_data_structures
    juce::juce_events
    PUBLIC
    juce::juce_recommended_config_flags
    juce::juce_recommended_lto_flags
    juce::juce_recommended_warning_flags)
