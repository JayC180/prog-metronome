package com.jayc180.rhythmengine.util

import java.util.concurrent.locks.ReentrantLock

internal actual class SpinLock actual constructor() {
    private val lock = ReentrantLock()
    actual fun lock()   = lock.lock()
    actual fun unlock() = lock.unlock()
}
