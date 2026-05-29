package com.jayc180.rhythmengine.platform

/** Monotonic nanosecond clock — equivalent of System.nanoTime(). */
expect fun nanoNow(): Long

/** Park the calling thread for approximately [nanos] nanoseconds. */
expect fun nanosleep(nanos: Long)

/**
 * Spawn a high-priority daemon thread.
 * [body] contains a blocking loop; runs until it returns.
 */
expect fun startHighPriorityThread(name: String, body: () -> Unit)
