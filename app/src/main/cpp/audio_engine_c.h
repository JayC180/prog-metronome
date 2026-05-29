#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

bool  re_audio_open(void);
void  re_audio_close(void);
bool  re_audio_load_sample_buffer(const char* soundId, const unsigned char* data, size_t len);
bool  re_audio_load_sample_path(const char* soundId, const char* filePath);
void  re_audio_trigger(const char* soundId, float volume, int64_t expectedNanos);
void  re_audio_stop_all(void);
int   re_audio_voice_count(void);

#ifdef __cplusplus
}
#endif
