package com.jayc180.rhythmengine.util

/**
 * Binary min-heap backed by pure Kotlin stdlib — compiles on all KMP targets.
 * Drop-in replacement for java.util.PriorityQueue<T : Comparable<T>>.
 */
class MinHeap<T : Comparable<T>> {
    private val data = ArrayList<T>()

    val size: Int get() = data.size
    fun isEmpty()    = data.isEmpty()
    fun isNotEmpty() = data.isNotEmpty()

    fun offer(item: T) {
        data.add(item)
        siftUp(data.size - 1)
    }

    fun peek(): T? = if (data.isEmpty()) null else data[0]

    fun poll(): T? {
        if (data.isEmpty()) return null
        val top = data[0]
        val n   = data.size
        if (n > 1) { data[0] = data.removeAt(n - 1); siftDown(0) }
        else        data.removeAt(0)
        return top
    }

    fun clear() = data.clear()

    private fun siftUp(i: Int) {
        var idx = i
        while (idx > 0) {
            val p = (idx - 1) ushr 1
            if (data[idx] < data[p]) { swap(idx, p); idx = p } else break
        }
    }

    private fun siftDown(i: Int) {
        val n = data.size; var idx = i
        while (true) {
            val l = (idx shl 1) + 1; val r = l + 1; var min = idx
            if (l < n && data[l] < data[min]) min = l
            if (r < n && data[r] < data[min]) min = r
            if (min == idx) break
            swap(idx, min); idx = min
        }
    }

    private fun swap(i: Int, j: Int) { val t = data[i]; data[i] = data[j]; data[j] = t }
}
