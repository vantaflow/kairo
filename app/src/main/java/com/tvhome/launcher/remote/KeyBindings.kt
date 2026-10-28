package com.tvhome.launcher.remote

import android.view.KeyEvent

/** Touches que le launcher ne réaffecte jamais (navigation, volume, marche/arrêt, menu). */
private val ReservedKeys = setOf(
    KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
    KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE, KeyEvent.KEYCODE_POWER,
    KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_TV_POWER,
)

fun isRemappable(keyCode: Int): Boolean = keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode !in ReservedKeys

/** Format stocké : une liaison par ligne, « keyCode=package ». */
fun encodeBindings(bindings: Map<Int, String>): String =
    bindings.entries.joinToString("\n") { "${it.key}=${it.value}" }

fun decodeBindings(raw: String?): Map<Int, String> =
    raw.orEmpty().lineSequence().mapNotNull { line ->
        val key = line.substringBefore('=', "").toIntOrNull() ?: return@mapNotNull null
        val pkg = line.substringAfter('=', "").trim()
        if (pkg.isEmpty()) null else key to pkg
    }.toMap()

/** Liaisons actives : en profil enfant, seulement celles qui ouvrent un de ses favoris. */
fun effectiveBindings(bindings: Map<Int, String>, kids: Boolean, favorites: Set<String>): Map<Int, String> =
    if (kids) bindings.filterValues { it in favorites } else bindings

fun bindingFor(keyCode: Int, bindings: Map<Int, String>): String? =
    if (isRemappable(keyCode)) bindings[keyCode] else null

/** Nom lisible d'une touche (« KEYCODE_PROG_RED » → « PROG_RED »). */
fun keyLabel(keyCode: Int): String = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
