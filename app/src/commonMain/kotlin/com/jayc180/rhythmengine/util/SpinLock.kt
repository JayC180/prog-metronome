package com.jayc180.rhythmengine.util

/**
 * Platform lock — implemented as ReentrantLock on JVM, NSLock on iOS.
 * Named SpinLock to match existing callers; semantics are a proper mutex.
 */
internal expect class SpinLock() {
    fun lock()
    fun unlock()
}

internal inline fun <T> SpinLock.withLock(block: () -> T): T {
    lock()
    return try { block() } finally { unlock() }
}
