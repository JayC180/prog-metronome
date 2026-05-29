#include "sample_store.h"
#include <cstring>
#include <unistd.h>
#include <sys/stat.h>
#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "SampleStore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...) ((void)0)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif

#pragma pack(push, 1)
struct WavHeader {
    char     riff[4]; uint32_t chunkSize; char wave[4];
    char     fmt[4];  uint32_t fmtSize;
    uint16_t audioFormat, numChannels;
    uint32_t sampleRate, byteRate;
    uint16_t blockAlign, bitsPerSample;
};
#pragma pack(pop)

// load from Android assets
#ifdef __ANDROID__
bool SampleStore::loadFromAssets(AAssetManager* mgr, const std::string& soundId,
                                 const std::string& assetPath) {
  AAsset* asset = AAssetManager_open(mgr, assetPath.c_str(), AASSET_MODE_BUFFER);
  if (!asset) { LOGE("Asset not found: %s", assetPath.c_str()); return false; }

  size_t len       = AAsset_getLength(asset);
  const uint8_t* b = static_cast<const uint8_t*>(AAsset_getBuffer(asset));

  Sample sample; sample.soundId = soundId;
  bool ok = parseWav(b, len, sample);
  AAsset_close(asset);

  if (ok) { samples_[soundId] = std::move(sample); LOGI("Loaded '%s'", soundId.c_str()); }
  return ok;
}
#endif

// load from fd (res/raw)
bool SampleStore::loadFromFd(const std::string& soundId, int fd, long offset, long length) {
  // read the bytes at [offset, offset+length) from the fd -> memory
  std::vector<uint8_t> buf(static_cast<size_t>(length));
  if (lseek(fd, offset, SEEK_SET) < 0) {
    LOGE("lseek failed for soundId=%s", soundId.c_str());
    return false;
  }
  ssize_t nread = read(fd, buf.data(), buf.size());
  if (nread != static_cast<ssize_t>(length)) {
    LOGE("read failed for soundId=%s: got %zd expected %ld", soundId.c_str(), nread, length);
    return false;
  }

  Sample sample; sample.soundId = soundId;
  bool ok = parseWav(buf.data(), buf.size(), sample);
  if (ok) { samples_[soundId] = std::move(sample); LOGI("Loaded '%s' from fd", soundId.c_str()); }
  return ok;
}

bool SampleStore::loadFromBuffer(const std::string& soundId, const uint8_t* data, size_t len) {
  Sample sample;
  sample.soundId = soundId;
  bool ok = parseWav(data, len, sample);
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

// parse wav
// https://en.wikipedia.org/wiki/WAV#WAV_file_header
bool SampleStore::parseWav(const uint8_t* data, size_t len, Sample& out) {
  if (len < 44) { LOGE("File too small"); return false; }

  // scan fmt and data chunks; handles extra metadata chunks
  if (strncmp((char*)data, "RIFF", 4) != 0 || strncmp((char*)data+8, "WAVE", 4) != 0) {
    LOGE("Not RIFF/WAVE"); return false;
  }

  uint16_t audioFormat = 0, numChannels = 0, bitsPerSample = 0;
  uint32_t sampleRate  = 0;
  const uint8_t* pcmData = nullptr;
  uint32_t pcmSize = 0;

  size_t pos = 12;
  while (pos + 8 <= len) {
    const char*  id   = (const char*)(data + pos);
    uint32_t     size = *(uint32_t*)(data + pos + 4);
    pos += 8;

    if (strncmp(id, "fmt ", 4) == 0) {
      audioFormat  = *(uint16_t*)(data + pos);
      numChannels  = *(uint16_t*)(data + pos + 2);
      sampleRate   = *(uint32_t*)(data + pos + 4);
      bitsPerSample= *(uint16_t*)(data + pos + 14);
    } else if (strncmp(id, "data", 4) == 0) {
      pcmData = data + pos;
      pcmSize = size;
      break;
    }
    pos += size;
    if (size % 2) pos++;  // word-align
  }

  if (!pcmData) { LOGE("No data chunk"); return false; }
  if (audioFormat != 1) { LOGE("Not PCM (format=%d)", audioFormat); return false; }
  if (bitsPerSample != 16) { LOGE("Not 16-bit (%d)", bitsPerSample); return false; }
  if (numChannels < 1 || numChannels > 2) { LOGE("Bad channel count %d", numChannels); return false; }

  size_t count = pcmSize / sizeof(int16_t);
  const int16_t* src = (const int16_t*)pcmData;
  out.pcm.assign(src, src + count);
  out.sampleRate   = sampleRate;
  out.channelCount = numChannels;

  if (numChannels == 1) { monoToStereo(out.pcm); out.channelCount = 2; }
  out.frameCount = out.pcm.size() / 2;
  return true;
}

void SampleStore::monoToStereo(std::vector<int16_t>& pcm) {
  std::vector<int16_t> s; s.reserve(pcm.size() * 2);
  for (int16_t v : pcm) { s.push_back(v); s.push_back(v); }
  pcm = std::move(s);
}