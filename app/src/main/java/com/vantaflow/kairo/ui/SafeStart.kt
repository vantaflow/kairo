package com.vantaflow.kairo.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** Lance une activité sans jamais faire planter le launcher ; renvoie false en cas d'échec. */
fun Context.startSafely(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
