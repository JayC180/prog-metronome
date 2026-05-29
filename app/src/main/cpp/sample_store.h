#pragma once
#include <string>
#include <unordered_map>
#include <vector>
#include <cstdint>
#ifdef __ANDROID__
#include <android/asset_manager.h>
#endif

struct Sample {
    std::string          soundId;
    std::vector<int16_t> pcm;   // interleaved stereo, 48kHz, int16
    int                  channelCount = 2;
    int                  sampleRate   = 48000;
    size_t               frameCount   = 0;
};

class SampleStore {
public:
#ifdef __ANDROID__
    // android asset dir
    bool loadFromAssets(AAssetManager* mgr, const std::string& soundId,
                        const std::string& assetPath);
#endif

    // from fd (res/raw)
    bool loadFromFd(const std::string& soundId, int fd, long offset, long length);

    bool loadFromBuffer(const std::string& soundId, const uint8_t* data, size_t len);

    const Sample* get(const std::string& soundId) const;
    bool          contains(const std::string& soundId) const;
    void          clear();
    size_t        size() const { return samples_.size(); }

private:
    std::unordered_map<std::string, Sample> samples_;
    bool parseWav(const uint8_t* data, size_t len, Sample& out);
    void monoToStereo(std::vector<int16_t>& pcm);
};