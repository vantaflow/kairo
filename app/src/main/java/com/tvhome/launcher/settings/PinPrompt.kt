package com.tvhome.launcher.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.tvhome.launcher.ui.theme.Background
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.ui.theme.OnBackgroundMuted

/** Demande le PIN du profil enfant avant d'ouvrir les réglages. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PinPrompt(profile: Profile, guard: PinGuard, onUnlocked: () -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        repeat(10) { androidx.compose.runtime.withFrameNanos { }; runCatching { first.requestFocus() } }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            // Le focus reste dans l'écran du PIN : rien n'est atteignable sur l'accueil derrière.
            .focusGroup()
            .focusProperties { exit = { androidx.compose.ui.focus.FocusRequester.Cancel } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Profil « ${profile.name} » : code PIN", color = OnBackground, fontSize = 26.sp)
            val valider = remember { androidx.compose.ui.focus.FocusRequester() }
            fun submit() {
                when {
                    guard.attempt(pin, profile.pinHash) -> onUnlocked()
                    guard.lockedForMs() > 0 -> error = "Trop d'essais : réessayez dans ${guard.lockedForMs() / 1000 + 1} s."
                    else -> error = "Code incorrect."
                }
                pin = ""
            }
            TvInput(
                pin,
                { pin = it.filter(Char::isDigit).take(6); error = null },
                Modifier
                    .width(220.dp)
                    .focusRequester(first)
                    // Bas : vers « Valider » (et non « Annuler », plus proche à l'écran).
                    .focusProperties { down = valider },
                secret = true,
                placeholder = "OK pour saisir",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                // « Valider » sur le clavier vérifie directement le code.
                onDone = { if (pin.isNotEmpty()) submit() },
            )
            error?.let { Text(it, color = OnBackgroundMuted) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = ::submit, modifier = Modifier.focusRequester(valider)) { Text("Valider") }
                OutlinedButton(onClick = onCancel) { Text("Annuler") }
            }
        }
    }
}
