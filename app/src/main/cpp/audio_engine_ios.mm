// iOS audio engine — AudioUnit RemoteIO replacing Oboe.
// voice_mixer.cpp and sample_store.cpp are reused unchanged.

#import <AudioUnit/AudioUnit.h>
#import <AVFoundation/AVFoundation.h>
#include "audio_engine_c.h"
#include "sample_store.h"
#include "voice_mixer.h"
#include <time.h>
#include <stdio.h>
#include <vector>

static SampleStore gStore;
static VoiceMixer  gMixer;
static AudioUnit   gAudioUnit = nullptr;
static bool        gIsOpen    = false;

static int64_t nanoNow() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

// Render callback — mirrors Oboe's onAudioReady.
// Stereo interleaved int16 matches VoiceMixer::mix() output format exactly.
static OSStatus renderCallback(
    void*                       inRefCon,
    AudioUnitRenderActionFlags* ioActionFlags,
    const AudioTimeStamp*       inTimeStamp,
    UInt32                      inBusNumber,
    UInt32                      inNumberFrames,
    AudioBufferList*            ioData
) {
    gMixer.mix(static_cast<int16_t*>(ioData->mBuffers[0].mData), (int32_t)inNumberFrames);
    return noErr;
}

bool re_audio_open(void) {
    if (gIsOpen) return true;

    // Low-latency playback session
    AVAudioSession* session = [AVAudioSession sharedInstance];
    NSError* error = nil;
    [session setCategory:AVAudioSessionCategoryPlayback error:&error];
    [session setPreferredIOBufferDuration:0.005 error:&error];  // 5 ms
    [session setActive:YES error:&error];

    // Locate RemoteIO audio unit
    AudioComponentDescription desc = {};
    desc.componentType         = kAudioUnitType_Output;
    desc.componentSubType      = kAudioUnitSubType_RemoteIO;
    desc.componentManufacturer = kAudioUnitManufacturer_Apple;

    AudioComponent comp = AudioComponentFindNext(NULL, &desc);
    if (!comp) return false;

    OSStatus status = AudioComponentInstanceNew(comp, &gAudioUnit);
    if (status != noErr) return false;

    UInt32 one = 1;
    AudioUnitSetProperty(gAudioUnit,
                         kAudioOutputUnitProperty_EnableIO,
                         kAudioUnitScope_Output, 0,
                         &one, sizeof(one));

    // 48 kHz stereo interleaved int16 — identical to the Android Oboe stream
    AudioStreamBasicDescription asbd = {};
    asbd.mSampleRate       = 48000.0;
    asbd.mFormatID         = kAudioFormatLinearPCM;
    asbd.mFormatFlags      = kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked;
    asbd.mBytesPerPacket   = 4;   // 2 ch × 2 bytes
    asbd.mFramesPerPacket  = 1;
    asbd.mBytesPerFrame    = 4;
    asbd.mChannelsPerFrame = 2;
    asbd.mBitsPerChannel   = 16;

    AudioUnitSetProperty(gAudioUnit,
                         kAudioUnitProperty_StreamFormat,
                         kAudioUnitScope_Input, 0,
                         &asbd, sizeof(asbd));

    AURenderCallbackStruct cb = {};
    cb.inputProc       = renderCallback;
    cb.inputProcRefCon = NULL;
    AudioUnitSetProperty(gAudioUnit,
                         kAudioUnitProperty_SetRenderCallback,
                         kAudioUnitScope_Input, 0,
                         &cb, sizeof(cb));

    status = AudioUnitInitialize(gAudioUnit);
    if (status != noErr) { AudioComponentInstanceDispose(gAudioUnit); return false; }

    status = AudioOutputUnitStart(gAudioUnit);
    gIsOpen = (status == noErr);
    return gIsOpen;
}

void re_audio_close(void) {
    if (!gIsOpen) return;
    AudioOutputUnitStop(gAudioUnit);
    AudioUnitUninitialize(gAudioUnit);
    AudioComponentInstanceDispose(gAudioUnit);
    gAudioUnit = nullptr;
    gIsOpen    = false;
    NSError* error = nil;
    [[AVAudioSession sharedInstance] setActive:NO error:&error];
}

bool re_audio_load_sample_buffer(const char* soundId, const unsigned char* data, size_t len) {
    return gStore.loadFromBuffer(soundId, data, len);
}

bool re_audio_load_sample_path(const char* soundId, const char* filePath) {
    FILE* f = fopen(filePath, "rb");
    if (!f) return false;
    fseek(f, 0, SEEK_END);
    long len = ftell(f);
    rewind(f);
    std::vector<uint8_t> buf(static_cast<size_t>(len));
    fread(buf.data(), 1, static_cast<size_t>(len), f);
    fclose(f);
    return gStore.loadFromBuffer(soundId, buf.data(), buf.size());
}

void re_audio_trigger(const char* soundId, float volume, int64_t expectedNanos) {
    const Sample* s = gStore.get(soundId);
    if (!s) return;
    int64_t drift = (nanoNow() - expectedNanos) / 1000;
    if (drift > 5000) NSLog(@"AudioEngine drift %lldµs for %s", (long long)drift, soundId);
    gMixer.trigger(s, volume);
}

void re_audio_stop_all(void)    { gMixer.stopAll(); }
int  re_audio_voice_count(void) { return gMixer.activeVoiceCount(); }
