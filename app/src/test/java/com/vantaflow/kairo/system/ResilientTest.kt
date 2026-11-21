package com.vantaflow.kairo.system

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResilientTest {
    private val failures = mutableListOf<Throwable>()
    private val log: (String, Throwable) -> Unit = { _, e -> failures += e }

    @Test
    fun valueArrivesAfterTwoFailures() = runTest {
        var calls = 0
        val source = flow {
            calls++
            if (calls <= 2) throw IllegalStateException("échec $calls")
            emit(42)
        }
        assertEquals(42, source.resilient("test", log).first())
        assertEquals(3, calls)
        assertEquals(2, failures.size)
        // 1 s puis 2 s d'attente, en temps virtuel.
        assertEquals(3_000L, currentTime)
    }

    @Test
    fun cancellationIsNotRetried() = runTest {
        var calls = 0
        val source = flow<Int> {
            calls++
            throw CancellationException("arrêt")
        }
        val error = runCatching { source.resilient("test", log).first() }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(1, calls)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun delayStartsOverAfterASuccessfulEmission() = runTest {
        var calls = 0
        val failedAt = mutableListOf<Long>()
        val source = flow {
            calls++
            when (calls) {
                1 -> throw IllegalStateException("échec 1")
                2 -> {
                    emit(1)
                    throw IllegalStateException("échec 2")
                }
                3 -> throw IllegalStateException("échec 3")
                else -> emit(2)
            }
        }
        val values = mutableListOf<Int>()
        source.resilient("test") { _, _ -> failedAt += currentTime }.collect { values += it; if (it == 2) return@collect }
        assertEquals(listOf(1, 2), values)
        // Échec à 0, 1 s ; succès puis échec à 1 s : de nouveau 1 s (et non 2 s) ; puis 2 s.
        assertEquals(listOf(0L, 1_000L, 2_000L), failedAt)
        assertEquals(4_000L, currentTime)
    }

    @Test
    fun delayDoublesUpToThirtySeconds() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L), (0L..7L).map(::retryDelayMs))
        assertEquals(30_000L, retryDelayMs(1_000L))
    }
}
