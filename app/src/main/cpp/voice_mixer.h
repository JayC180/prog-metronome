#pragma once
#include "sample_store.h"
#include <array>
#include <atomic>
#include <cstdint>

/**
 * VoiceMixer — mixes up to MAX_VOICES simultaneous sample playbacks
 *
 * two threads calls this
 *   TRIGGER THREAD (Kotlin dispatcher thru JNI):
 *      calls trigger() for new voice
 *
 *   AUDIO THREAD (Oboe callback):
 *      calls mix() to render active voices -> out buf
 *
 * lock free design:
 *   - voice slots are claimed atomically (compare-exchange on 'active' flag)
 *   - mix() only reads voices whose 'active' flag is set
 *   - trigger() only writes to voices whose 'active' flag is clear
 *   - No mutex on the hot path — the audio thread must never block
 *
 * if all MAX_VOICES slots taken, oldest voice gets stolen
 */

static constexpr int MAX_VOICES = 32; // prob don't need more?

struct Voice {
  std::atomic<bool> active{false};
  const Sample *sample = nullptr;
  size_t position = 0; // current frame position within sample
  float volume = 1.0f;
  int64_t triggeredAt = 0; // for voice stealing: evict oldest
};

class VoiceMixer {
public:
  VoiceMixer() = default;

  /**
   * Trigger a sample. Claims a free voice slot and starts playback from frame
   * 0. Safe to call from any thread. Lock-free.
   *
   * @param sample   Pointer to pre-loaded sample (must outlive the voice)
   * @param volume   0.0 – 1.0
   */
  void trigger(const Sample *sample, float volume = 1.0f);

  /**
   * Mix all active voices into [outputBuffer].
   * Called exclusively from the Oboe audio callback.
   *
   * @param outputBuffer  Interleaved stereo int16 output
   * @param numFrames     Number of stereo frames to render
   */
  void mix(int16_t *outputBuffer, int32_t numFrames);

  /** Stop all active voices immediately. Safe to call from any thread. */
  void stopAll();

  /** Number of currently active voices (approximate — for debug/monitoring). */
  int activeVoiceCount() const;

private:
  std::array<Voice, MAX_VOICES> voices_;
  std::atomic<int64_t> triggerCounter_{0}; // monotonic, for voice stealing

  int findFreeVoice();   // returns index of free slot, or steals oldest
  int findOldestVoice(); // for voice stealing
};
