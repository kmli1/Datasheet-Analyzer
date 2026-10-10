package com.example.datasheetanalyzer.ocr

/** One in-flight recognition; throttles start times using a monotonic clock. */
class AnalysisGate(private val intervalMs: Long = 400L) {
    private var busy = false
    private var stopped = false
    private var lastStart: Long? = null

    init { require(intervalMs >= 0) }

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        val last = lastStart
        if (stopped || busy || (last != null && nowMs - last < intervalMs)) return false
        busy = true
        lastStart = nowMs
        return true
    }

    /** Returns true when the recognizer can now be closed. */
    @Synchronized
    fun complete(): Boolean {
        busy = false
        return stopped
    }

    /** Stops new work; returns true if no task is currently using the recognizer. */
    @Synchronized
    fun stop(): Boolean {
        stopped = true
        return !busy
    }

    @Synchronized
    fun isStopped(): Boolean = stopped
}
