cmake_minimum_required(VERSION 3.22.1)
project(rhythmengine)

set(CMAKE_CXX_STANDARD 17)

find_package (oboe REQUIRED CONFIG)

add_library(
        rhythmengine
        SHARED
        src/main/cpp/audio_engine.cpp
        src/main/cpp/sample_store.cpp
        src/main/cpp/voice_mixer.cpp
)

target_link_libraries(
        rhythmengine
        oboe::oboe
        android       # for AAssetManager
        log           # for __android_log_print
)

target_link_options(rhythmengine PRIVATE "-Wl,--build-id=none")

target_include_directories(rhythmengine PRIVATE src/main/cpp)

add_custom_command(TARGET rhythmengine POST_BUILD
        COMMAND ${ANDROID_TOOLCHAIN_ROOT}/bin/llvm-objcopy
        --remove-section .comment
        $<TARGET_FILE:rhythmengine>
)