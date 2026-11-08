package com.example.toolbox.location

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceNameCancellationTest {
    private fun executor() = ThreadPoolExecutor(
        1, 1, 1L, TimeUnit.SECONDS, SynchronousQueue(),
        { task -> Thread(task, "place-name-test").apply { isDaemon = true } },
    )

    private fun awaitIgnoringInterrupts(latch: CountDownLatch) {
        while (true) {
            try {
                latch.await()
                return
            } catch (_: InterruptedException) {
                // Simulate a vendor backend that cannot be interrupted.
            }
        }
    }

    @Test(timeout = 10_000)
    fun timeoutReturnsBeforeAnUninterruptibleBackendAndDoesNotQueueMoreRequests() = runBlocking {
        val executor = executor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val startedAt = System.nanoTime()
            val request = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(150L) {
                    cancellablePlaceLookup(executor) {
                        entered.countDown()
                        awaitIgnoringInterrupts(release)
                        "late result"
                    }
                }
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertNull(request.await())
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) < 2_000L)
            val nextStarted = AtomicBoolean(false)
            assertNull(cancellablePlaceLookup(executor) { nextStarted.set(true); "next" })
            assertFalse(nextStarted.get())
        } finally {
            release.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 10_000)
    fun cancelledOldResultCannotOverwriteTheNewResult() = runBlocking {
        val executor = executor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var displayed: String? = null
        try {
            val old = launch(start = CoroutineStart.UNDISPATCHED) {
                displayed = cancellablePlaceLookup(executor) {
                    entered.countDown()
                    awaitIgnoringInterrupts(release)
                    "Place A"
                }
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            old.cancelAndJoin()
            displayed = cancellablePlaceLookup(Executor { it.run() }) { "Place B" }
            release.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
            assertEquals("Place B", displayed)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun cancellingBeforeWorkerStartsPreventsTheLookup() = runBlocking {
        val tasks = mutableListOf<Runnable>()
        val called = AtomicBoolean(false)
        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            cancellablePlaceLookup(Executor { tasks += it }) { called.set(true) }
        }
        request.cancelAndJoin()
        tasks.single().run()
        assertFalse(called.get())
    }

    @Test
    fun backendFailureReturnsUnavailable() = runBlocking {
        assertNull(cancellablePlaceLookup(Executor { it.run() }) { error("backend unavailable") })
    }
}
