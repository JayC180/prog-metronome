#include "sample_store.h"
#define MA_NO_DEVICE_IO
#define MA_NO_RESOURCE_MANAGER
#define MA_NO_NODE_GRAPH
#define MA_NO_ENGINE
#define MA_NO_ENCODING
#define MA_NO_GENERATION
#define MA_NO_THREADING
#define MINIAUDIO_IMPLEMENTATION
#include "third_party/miniaudio/miniaudio.h"
#include <array>
#include <cstdio>
#include <cstring>
#include <unistd.h>
#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "SampleStore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...) ((void)0)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif

namespace {
constexpr ma_uint32 OUTPUT_CHANNELS = 2;
constexpr ma_uint32 OUTPUT_SAMPLE_RATE = 48000;
constexpr ma_uint64 DECODE_CHUNK_FRAMES = 4096;
constexpr ma_uint64 MAX_DECODED_FRAMES = OUTPUT_SAMPLE_RATE * 30ULL;

uint32_t readLe32(const uint8_t* bytes) {
  return static_cast<uint32_t>(bytes[0]) |
      (static_cast<uint32_t>(bytes[1]) << 8) |
      (static_cast<uint32_t>(bytes[2]) << 16) |
      (static_cast<uint32_t>(bytes[3]) << 24);
}

bool isFourCc(const uint8_t* bytes, const char* expected) {
  return std::memcmp(bytes, expected, 4) == 0;
}

bool validateRiffWave(const uint8_t* data, size_t len) {
  if (len < 12 || !isFourCc(data, "RIFF") || !isFourCc(data + 8, "WAVE")) {
    return false;
  }

  const uint64_t riffEnd = static_cast<uint64_t>(readLe32(data + 4)) + 8ULL;
  if (riffEnd < 12 || riffEnd > len) return false;

  bool foundFmt = false;
  bool foundData = false;
  uint64_t pos = 12;
  while (pos + 8 <= riffEnd) {
    const uint8_t* header = data + static_cast<size_t>(pos);
    const uint64_t chunkSize = readLe32(header + 4);
    const uint64_t payloadStart = pos + 8;
    const uint64_t paddedSize = chunkSize + (chunkSize & 1ULL);
    if (payloadStart > riffEnd || paddedSize > riffEnd - payloadStart) return false;

    if (isFourCc(header, "fmt ")) {
      if (chunkSize < 16) return false;
      foundFmt = true;
    } else if (isFourCc(header, "fact")) {
      // guards miniaudio 0.11.25 unsigned underflow path
      if (chunkSize < 4) return false;
    } else if (isFourCc(header, "data")) {
      foundData = true;
    }

    pos = payloadStart + paddedSize;
  }
  return foundFmt && foundData;
}

ma_encoding_format detectEncoding(const uint8_t* data, size_t len) {
  if (validateRiffWave(data, len)) return ma_encoding_format_wav;
  if (len >= 4 && isFourCc(data, "fLaC")) return ma_encoding_format_flac;
  if (len >= 3 && std::memcmp(data, "ID3", 3) == 0) return ma_encoding_format_mp3;
  if (len >= 2 && data[0] == 0xFF && (data[1] & 0xE0) == 0xE0) {
    return ma_encoding_format_mp3;
  }
  return ma_encoding_format_unknown;
}
}

// load from Android assets
#ifdef __ANDROID__
bool SampleStore::loadFromAssets(AAssetManager* mgr, const std::string& soundId,
                                 const std::string& assetPath) {
  AAsset* asset = AAssetManager_open(mgr, assetPath.c_str(), AASSET_MODE_BUFFER);
  if (!asset) { LOGE("Asset not found: %s", assetPath.c_str()); return false; }

  size_t len       = AAsset_getLength(asset);
  const uint8_t* b = static_cast<const uint8_t*>(AAsset_getBuffer(asset));

  Sample sample; sample.soundId = soundId;
  bool ok = decodeAudio(b, len, sample);
  AAsset_close(asset);

  if (ok) { samples_[soundId] = std::move(sample); LOGI("Loaded '%s'", soundId.c_str()); }
  return ok;
}
#endif

// load from fd (res/raw)
bool SampleStore::loadFromFd(const std::string& soundId, int fd, long offset, long length) {
  if (length <= 0 || static_cast<unsigned long>(length) > MAX_ENCODED_BYTES) {
    LOGE("Invalid or oversized resource for soundId=%s", soundId.c_str());
    return false;
  }

  // read the bytes at [offset, offset+length) from the fd -> memory
  std::vector<uint8_t> buf(static_cast<size_t>(length));
  if (lseek(fd, offset, SEEK_SET) < 0) {
    LOGE("lseek failed for soundId=%s", soundId.c_str());
    return false;
  }
  size_t totalRead = 0;
  while (totalRead < buf.size()) {
    ssize_t nread = read(fd, buf.data() + totalRead, buf.size() - totalRead);
    if (nread <= 0) {
      LOGE("read failed for soundId=%s after %zu of %ld bytes",
           soundId.c_str(), totalRead, length);
      return false;
    }
    totalRead += static_cast<size_t>(nread);
  }

  Sample sample; sample.soundId = soundId;
  bool ok = decodeAudio(buf.data(), buf.size(), sample);
  if (ok) { samples_[soundId] = std::move(sample); LOGI("Loaded '%s' from fd", soundId.c_str()); }
  return ok;
}

bool SampleStore::loadFromFile(const std::string& soundId, const std::string& filePath) {
  FILE* file = fopen(filePath.c_str(), "rb");
  if (!file) {
    LOGE("Cannot open audio file: %s", filePath.c_str());
    return false;
  }

  bool ok = false;
  if (fseek(file, 0, SEEK_END) == 0) {
    const long length = ftell(file);
    if (length > 0 && static_cast<unsigned long>(length) <= MAX_ENCODED_BYTES &&
        fseek(file, 0, SEEK_SET) == 0) {
      std::vector<uint8_t> buffer(static_cast<size_t>(length));
      if (fread(buffer.data(), 1, buffer.size(), file) == buffer.size()) {
        ok = loadFromBuffer(soundId, buffer.data(), buffer.size());
      } else {
        LOGE("Could not read complete audio file: %s", filePath.c_str());
      }
    } else {
      LOGE("Audio file is empty or exceeds the import limit: %s", filePath.c_str());
    }
  }
  fclose(file);
  return ok;
}

bool SampleStore::loadFromBuffer(const std::string& soundId, const uint8_t* data, size_t len) {
  Sample sample;
  sample.soundId = soundId;
  bool ok = decodeAudio(data, len, sample);
  if (ok) {
    samples_[soundId] = std::move(sample);
    LOGI("Loaded '%s' from buffer (%zu bytes)", soundId.c_str(), len);
  }
  return ok;
}

// accessors
const Sample* SampleStore::get(const std::string& soundId) const {
  auto it = samples_.find(soundId);
  return it != samples_.end() ? &it->second : nullptr;
}
bool SampleStore::contains(const std::string& soundId) const { return samples_.count(soundId) > 0; }
void SampleStore::clear() { samples_.clear(); }

// decode WAV / MP3 / FLAC and normalize. All stereo, 48 kHz, signed 16 bit PCM
bool SampleStore::decodeAudio(const uint8_t* data, size_t len, Sample& out) {
  if (!data || len == 0 || len > SampleStore::MAX_ENCODED_BYTES) {
    LOGE("Audio file is empty or exceeds the %zu-byte import limit",
         SampleStore::MAX_ENCODED_BYTES);
    return false;
  }

  const ma_encoding_format encoding = detectEncoding(data, len);
  if (encoding == ma_encoding_format_unknown) {
    LOGE("Audio is not a supported RIFF/WAVE, MP3, or FLAC stream");
    return false;
  }

  ma_decoder_config config = ma_decoder_config_init(
      ma_format_s16, OUTPUT_CHANNELS, OUTPUT_SAMPLE_RATE);
  config.encodingFormat = encoding;
  config.ditherMode = ma_dither_mode_triangle;
  config.resampling.linear.lpfOrder = MA_MAX_FILTER_ORDER;

  ma_decoder decoder;
  ma_result result = ma_decoder_init_memory(data, len, &config, &decoder);
  if (result != MA_SUCCESS) {
    LOGE("Audio decoder initialization failed: %s", ma_result_description(result));
    return false;
  }

  std::vector<int16_t> decoded;
  decoded.reserve(static_cast<size_t>(OUTPUT_SAMPLE_RATE * OUTPUT_CHANNELS));
  std::array<int16_t, DECODE_CHUNK_FRAMES * OUTPUT_CHANNELS> chunk{};
  ma_uint64 totalFrames = 0;
  bool ok = true;

  while (true) {
    ma_uint64 framesRead = 0;
    result = ma_decoder_read_pcm_frames(
        &decoder, chunk.data(), DECODE_CHUNK_FRAMES, &framesRead);

    if (framesRead > MAX_DECODED_FRAMES - totalFrames) {
      LOGE("Decoded audio exceeds the 30-second import limit");
      ok = false;
      break;
    }

    decoded.insert(
        decoded.end(), chunk.begin(),
        chunk.begin() + static_cast<size_t>(framesRead * OUTPUT_CHANNELS));
    totalFrames += framesRead;

    if (result != MA_SUCCESS && result != MA_AT_END) {
      LOGE("Audio decoding failed: %s", ma_result_description(result));
      ok = false;
      break;
    }
    if (result == MA_AT_END || framesRead == 0) break;
  }

  ma_decoder_uninit(&decoder);
  if (!ok || totalFrames == 0) return false;

  out.pcm = std::move(decoded);
  out.sampleRate = OUTPUT_SAMPLE_RATE;
  out.channelCount = OUTPUT_CHANNELS;
  out.frameCount = static_cast<size_t>(totalFrames);
  return true;
}
