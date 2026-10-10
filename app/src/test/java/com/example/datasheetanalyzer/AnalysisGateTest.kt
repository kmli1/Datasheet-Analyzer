package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.ocr.AnalysisGate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisGateTest {
    @Test fun respectsFourHundredMillisecondBoundary() {
        val gate = AnalysisGate(400)
        assertTrue(gate.tryAcquire(0))
        assertFalse(gate.complete())
        assertFalse(gate.tryAcquire(399))
        assertTrue(gate.tryAcquire(400))
    }

    @Test fun slowRecognitionNeverOverlaps() {
        val gate = AnalysisGate(400)
        assertTrue(gate.tryAcquire(0))
        assertFalse(gate.tryAcquire(2000))
        gate.complete()
        assertTrue(gate.tryAcquire(2000))
    }

    @Test fun stoppingAnInFlightTaskDefersClose() {
        val gate = AnalysisGate()
        assertTrue(gate.tryAcquire(0))
        assertFalse(gate.stop())
        assertTrue(gate.complete())
        assertFalse(gate.tryAcquire(5000))
    }

    @Test fun stoppingIdleTaskAllowsImmediateClose() {
        val gate = AnalysisGate()
        assertTrue(gate.stop())
        assertTrue(gate.isStopped())
        assertFalse(gate.tryAcquire(0))
    }

    @Test fun concurrentFramesOnlyAcquireOnce() {
        val gate = AnalysisGate(0)
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val finished = CountDownLatch(12)
        val count = AtomicInteger()
        try {
            repeat(12) {
                pool.execute {
                    start.await()
                    if (gate.tryAcquire(0)) count.incrementAndGet()
                    finished.countDown()
                }
            }
            start.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(1, count.get())
        } finally { pool.shutdownNow() }
    }
}
