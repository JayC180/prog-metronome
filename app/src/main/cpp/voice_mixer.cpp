#include "voice_mixer.h"
#include <algorithm>
#include <climits>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "VoiceMixer"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGW(...) fprintf(stderr, __VA_ARGS__)
#endif

// trigger called from kotlin dispatcher thread
void VoiceMixer::trigger(const Sample *sample, float volume) {
  if (!sample || sample->frameCount == 0)
    return;

  int slot = findFreeVoice();

  // write all fields before setting active = true
  // audio thread checks active first so this order ensures full written voice is seen
  voices_[slot].sample = sample;
  voices_[slot].position = 0;
  voices_[slot].volume = std::clamp(volume, 0.0f, 1.0f);
  voices_[slot].triggeredAt =
      triggerCounter_.fetch_add(1, std::memory_order_relaxed);
  voices_[slot].active.store(true, std::memory_order_release);
}

// mix called from oboe
void VoiceMixer::mix(int16_t *outputBuffer, int32_t numFrames) {
  // zero output buffer first
  std::fill(outputBuffer, outputBuffer + numFrames * 2, int16_t(0));

  for (auto &voice : voices_) {
    // load-acquire pairs with the store-release in trigger()
    if (!voice.active.load(std::memory_order_acquire))
      continue;

    const Sample *s = voice.sample;
    float vol = voice.volume;

    for (int32_t frame = 0; frame < numFrames; ++frame) {
      if (voice.position >= s->frameCount) {
        // sample finished
        voice.active.store(false, std::memory_order_release);
        break;
      }

      // mix interleaved stereo
      size_t idx = voice.position * 2;
      int32_t L =
          outputBuffer[frame * 2] + static_cast<int32_t>(s->pcm[idx] * vol);
      int32_t R = outputBuffer[frame * 2 + 1] +
                  static_cast<int32_t>(s->pcm[idx + 1] * vol);

      // clamp to int16 range; maybe limiter later?
      outputBuffer[frame * 2] =
          static_cast<int16_t>(std::clamp(L, -32768, 32767));
      outputBuffer[frame * 2 + 1] =
          static_cast<int16_t>(std::clamp(R, -32768, 32767));

      voice.position++;
    }
  }
}

// utils

void VoiceMixer::stopAll() {
  for (auto &v : voices_) {
    v.active.store(false, std::memory_order_release);
  }
}

int VoiceMixer::activeVoiceCount() const {
  int count = 0;
  for (const auto &v : voices_) {
    if (v.active.load(std::memory_order_relaxed))
      count++;
  }
  return count;
}

int VoiceMixer::findFreeVoice() {
  // first pass, find free slot
  for (int i = 0; i < MAX_VOICES; i++) {
    bool expected = false;
    // atomically claim the slot if it's inactive
    if (voices_[i].active.compare_exchange_strong(expected, false,
                                                  std::memory_order_relaxed)) {
      return i;
    }
  }
  // all slots taken, take oldest
  LOGW("Voice pool full (%d voices) — stealing oldest", MAX_VOICES);
  return findOldestVoice();
}

int VoiceMixer::findOldestVoice() {
  int oldest = 0;
  int64_t oldestAt = LLONG_MAX;
  for (int i = 0; i < MAX_VOICES; i++) {
    int64_t t = voices_[i].triggeredAt;
    if (t < oldestAt) {
      oldestAt = t;
      oldest = i;
    }
  }
  return oldest;
}
