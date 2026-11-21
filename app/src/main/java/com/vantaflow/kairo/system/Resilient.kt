package com.vantaflow.kairo.system

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Attente avant le nouvel essai n° [attempt] (0 = premier) : 1 s, doublée à chaque fois, 30 s au plus. */
fun retryDelayMs(attempt: Long): Long = minOf(30_000L, 1_000L shl attempt.coerceIn(0L, 5L).toInt())

/**
 * Flux d'état qui reprend après une exception au lieu de mourir : sans cela, l'état de l'écran
 * resterait figé (heure arrêtée, favoris jamais mis à jour). L'annulation n'est jamais réessayée.
 * À placer en amont du `stateIn` ; en aval, la dernière valeur reste affichée pendant les essais.
 * Le délai repart de 1 s après chaque valeur reçue : seuls les échecs d'affilée l'allongent.
 */
fun <T> Flow<T>.resilient(tag: String, log: (String, Throwable) -> Unit = ::logRetry): Flow<T> = flow {
    var attempt = 0L
    onEach { attempt = 0L }
        .retryWhen { e, _ ->
            if (e is CancellationException) false
            else {
                log(tag, e)
                delay(retryDelayMs(attempt++))
                true
            }
        }
        .collect { emit(it) }
}

/**
 * Comme [resilient], mais si la source échoue avant sa première valeur, [default] est émis tout de
 * suite : l'écran qui en dépend s'affiche toujours, puis les vraies valeurs arrivent dès qu'un essai
 * réussit. Après une première valeur, un échec ne fait que relancer la lecture.
 */
fun <T> Flow<T>.orDefaultOnError(default: T, tag: String, log: (String, Throwable) -> Unit = ::logRetry): Flow<T> = flow {
    var emitted = false
    onEach { emitted = true }
        .catch { e ->
            if (!emitted && e !is CancellationException) {
                emitted = true
                emit(default)
            }
            throw e
        }
        .resilient(tag, log)
        .collect { emit(it) }
}

private fun logRetry(tag: String, e: Throwable) {
    // Tests JVM sans Android : le journal est facultatif.
    runCatching { Log.w("Kairo", "$tag : nouvel essai", e) }
}

/**
 * Valeur [default] si le flux n'a rien émis après [timeoutMs], puis ses vraies valeurs dès qu'elles
 * arrivent. Valeur reçue à temps : [default] n'est jamais émis (pas d'image intermédiaire).
 */
fun <T> Flow<T>.orDefaultAfter(timeoutMs: Long, default: T): Flow<T> = channelFlow {
    val lock = Mutex()
    var received = false
    val timer = launch {
        delay(timeoutMs)
        lock.withLock { if (!received) send(default) }
    }
    collect { value ->
        lock.withLock {
            received = true
            timer.cancel()
        }
        send(value)
    }
}
