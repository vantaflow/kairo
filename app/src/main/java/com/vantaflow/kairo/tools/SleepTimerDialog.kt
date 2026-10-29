package com.vantaflow.kairo.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.vantaflow.kairo.settings.ChoiceChip
import com.vantaflow.kairo.settings.ChoiceRow
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

/**
 * Minuteur de mise en veille : durée au choix ; [remaining] = minutes restantes si actif.
 * [serviceOn] : sans le service Kairo (Accessibilité), la box ne peut pas être mise en veille.
 */
@Composable
fun SleepTimerDialog(remaining: Int?, serviceOn: Boolean, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(560.dp).background(Color(0xFF202329), RoundedCornerShape(20.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Minuteur de mise en veille", fontSize = 26.sp, color = Color.White)
            Text(
                if (remaining != null) "La box se met en veille dans ${formatMinutes(remaining)}." else "La box (et la télé, via HDMI-CEC) se met en veille au bout de :",
                fontSize = 18.sp, color = OnBackgroundMuted,
            )
            ChoiceRow {
                SleepDurations.forEachIndexed { i, min ->
                    val chip = @Composable { ChoiceChip(false, { onPick(min) }) { Text(formatMinutes(min)) } }
                    if (i == 0 && remaining == null) androidx.compose.foundation.layout.Box(Modifier.focusRequester(first)) { chip() } else chip()
                }
            }
            if (remaining != null) {
                Button(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth().focusRequester(first)) { Text("Arrêter le minuteur") }
            }
            Text("1 minute avant, un bandeau prévient : une touche de la télécommande ajoute $SLEEP_EXTEND_MIN min.", fontSize = 15.sp, color = OnBackgroundMuted)
            if (!serviceOn) {
                Text(
                    "⚠ Activez le service Kairo (Réglages › Assistant de configuration) : sans lui, la box ne peut pas se mettre en veille.",
                    fontSize = 15.sp, color = Color(0xFFFFB74D),
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
