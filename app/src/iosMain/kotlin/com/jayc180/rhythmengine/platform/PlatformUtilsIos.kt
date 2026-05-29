@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.jayc180.rhythmengine.platform

import kotlinx.cinterop.*
import platform.Foundation.NSThread
import platform.posix.*

actual fun nanoNow(): Long = memScoped {
    val ts = alloc<timespec>()
    // clock_gettime takes clockid_t which is UInt on Darwin
    clock_gettime(CLOCK_MONOTONIC.toUInt(), ts.ptr)
    ts.tv_sec.toLong() * 1_000_000_000L + ts.tv_nsec.toLong()
}

actual fun nanosleep(nanos: Long) {
    usleep((nanos / 1_000L).toUInt().coerceAtLeast(1u))
}

actual fun startHighPriorityThread(name: String, body: () -> Unit) {
    // NSThread.detachNewThreadWithBlock is available iOS 10+ (our min is 16)
    NSThread.detachNewThreadWithBlock { body() }
}
