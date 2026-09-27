package com.jayc180.rhythmengine.audio

val SUPPORTED_AUDIO_EXTENSIONS: Set<String> = setOf("wav", "mp3", "flac")

const val MAX_IMPORTED_AUDIO_BYTES: Long = 32L * 1024L * 1024L

fun supportedAudioExtension(fileName: String): String? =
    fileName.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .takeIf { it in SUPPORTED_AUDIO_EXTENSIONS }

fun isSupportedAudioFile(fileName: String): Boolean =
    supportedAudioExtension(fileName) != null

fun audioFileStem(fileName: String): String =
    if (supportedAudioExtension(fileName) != null) fileName.substringBeforeLast('.') else fileName
