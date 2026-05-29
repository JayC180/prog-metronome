#include "sample_store.h"
#include "voice_mixer.h"
#include <oboe/Oboe.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>
#include <string>
#include <memory>
#include <atomic>
#include <time.h>

#define LOG_TAG "AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class AudioEngine : public oboe::AudioStreamDataCallback {
public:
    bool open() {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setFormat(oboe::AudioFormat::I16)
                ->setChannelCount(oboe::ChannelCount::Stereo)
                ->setSampleRate(48000)
                ->setDataCallback(this);

        auto result = builder.openStream(stream_);
        if (result != oboe::Result::OK) {
            LOGE("openStream failed: %s", oboe::convertToText(result));
            return false;
        }
        stream_->setBufferSizeInFrames(stream_->getFramesPerBurst() * 2);
        result = stream_->start();
        if (result != oboe::Result::OK) { LOGE("start failed: %s", oboe::convertToText(result)); return false; }
        isOpen_.store(true);
        LOGI("Stream started: rate=%d burst=%d", stream_->getSampleRate(), stream_->getFramesPerBurst());
        return true;
    }

    void close() {
        if (stream_) { stream_->stop(); stream_->close(); stream_.reset(); }
        isOpen_.store(false);
    }

    bool loadSampleAsset(AAssetManager* mgr, const std::string& id, const std::string& path) {
        return store_.loadFromAssets(mgr, id, path);
    }

    bool loadSampleFd(const std::string& id, int fd, long offset, long length) {
        return store_.loadFromFd(id, fd, offset, length);
    }

    bool loadSampleFromBuffer(const std::string& id, const uint8_t* data, size_t len) {
        return store_.loadFromBuffer(id, data, len);
    }

    void triggerSound(const std::string& id, float vol, int64_t expectedNanos) {
        const Sample* s = store_.get(id);
        if (!s) { LOGE("Unknown sound: %s", id.c_str()); return; }
        int64_t drift = (nanoNow() - expectedNanos) / 1000;
        if (drift > 5000) LOGI("Drift %lldµs for %s", (long long)drift, id.c_str());
        mixer_.trigger(s, vol);
    }

    void stopAll()            { mixer_.stopAll(); }
    int  activeVoiceCount()   { return mixer_.activeVoiceCount(); }
    bool isOpen() const       { return isOpen_.load(); }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* data, int32_t frames) override {
        mixer_.mix(static_cast<int16_t*>(data), frames);
        return oboe::DataCallbackResult::Continue;
    }

private:
    std::shared_ptr<oboe::AudioStream> stream_;
    SampleStore store_;
    VoiceMixer  mixer_;
    std::atomic<bool> isOpen_{false};

    static int64_t nanoNow() {
        struct timespec ts{}; clock_gettime(CLOCK_MONOTONIC, &ts);
        return (int64_t)ts.tv_sec * 1'000'000'000LL + ts.tv_nsec;
    }
};

static AudioEngine gEngine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_jayc180_rhythmengine_audio_AudioEngineAndroid_nativeOpen(JNIEnv*, jobject) {
    return gEngine.open() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_jayc180_rhythmengine_audio_AudioEngineAndroid_nativeClose(JNIEnv*, jobject) {
    gEngine.close();
}

JNIEXPORT jboolean JNICALL
Java_com_jayc180_rhythmengine_audio_AudioEngineAndroid_nativeLoadSample(
        JNIEnv* env, jobject, jobject assetMgr, jstring jId, jstring jPath) {
    AAssetManager* mgr  = AAssetManager_fromJava(env, assetMgr);
    const char* id   = env->GetStringUTFChars(jId,   nullptr);
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    bool ok = gEngine.loadSampleAsset(mgr, id, path);
    env->ReleaseStringUTFChars(jId, id);
    env->ReleaseStringUTFChars(jPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_jayc180_rhythmengine_audio_AudioEngineAndroid_nativeLoadSampleFd(
        JNIEnv* env, jobject, jstring jId, jint fd, jlong offset, jlong length) {
    const char* id = env->GetStringUTFChars(jId, nullptr);
    bool ok = gEngine.loadSampleFd(id, fd, (long)offset, (long)length);
    env->ReleaseStringUTFChars(jId, id);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_jayc180_rhythmengine_audio_AudioEngineAndroid_nativeTriggerSound(
        JNIEnv* env, jobject, jstring jId, jfloat vol, jlong expectedNanos) {
    const char* id = env->GetStringUTFChars(jId, nullptr);
    gEngine.triggerSound(id, vol, expectedNanos);
    env->ReleaseStringUTFChars(jId, id);
}

JNIEXPORT jboolean JNICALL
Java_com_jayc180_rhythmengine_audio_AudioEngineAndroid_nativeLoadSamplePath(
        JNIEnv* env, jobject,
        jstring jSoundId,
        jstring jFilePath) {

    const char* soundId  = env->GetStringUTFChars(jSoundId,  nullptr);
    const char* filePath = env->GetStringUTFChars(jFilePath, nullptr);

    // Open file from filesystem (user-imported sounds live in app's files dir)
    FILE* f = fopen(filePath, "rb");
    bool ok = false;

    if (f) {
        fseek(f, 0, SEEK_END);
        long len = ftell(f);
        fseek(f, 0, SEEK_SET);

        std::vector<uint8_t> buf(static_cast<size_t>(len));
        fread(buf.data(), 1, len, f);
        fclose(f);

        ok = gEngine.loadSampleFromBuffer(soundId, buf.data(), buf.size());
    } else {
        LOGE("nativeLoadSamplePath: cannot open file: %s", filePath);
    }

    env->ReleaseStringUTFChars(jSoundId,  soundId);
    env->ReleaseStringUTFChars(jFilePath, filePath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_jayc180_rhythmengine_audio_AudioEngineAndroid_nativeStopAll(JNIEnv*, jobject) {
    gEngine.stopAll();
}

JNIEXPORT jint JNICALL
Java_com_jayc180_rhythmengine_audio_AudioEngineAndroid_nativeActiveVoiceCount(JNIEnv*, jobject) {
    return (jint)gEngine.activeVoiceCount();
}

} // extern "C"