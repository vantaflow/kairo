package com.vantaflow.kairo.system

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Filet des travaux de fond : une exception non attrapée dans une coroutine est journalisée au lieu
 * de faire tomber le process (et l'accueil avec lui). À ajouter à chaque portée créée par l'appli.
 */
val SafeHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, e ->
    Log.e("Kairo", "travail de fond en échec", e)
}
