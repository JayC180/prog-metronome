package com.jayc180.rhythmengine.util

import platform.Foundation.NSLock

internal actual class SpinLock actual constructor() {
    private val lock = NSLock()
    actual fun lock()   { lock.lock() }
    actual fun unlock() { lock.unlock() }
}
