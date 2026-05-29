package com.jayc180.rhythmengine.platform

actual fun nanoNow(): Long = System.nanoTime()

actual fun nanosleep(nanos: Long) =
    java.util.concurrent.locks.LockSupport.parkNanos(nanos)

actual fun startHighPriorityThread(name: String, body: () -> Unit) {
    Thread(body, name).also {
        it.priority = Thread.MAX_PRIORITY
        it.isDaemon = true
        it.start()
    }
}
