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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.vantaflow.kairo.R
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
            Text(stringResource(R.string.sleep_title), fontSize = 26.sp, color = Color.White)
            Text(
                if (remaining != null) stringResource(R.string.sleep_remaining, formatMinutes(remaining)) else stringResource(R.string.sleep_pick),
                fontSize = 18.sp, color = OnBackgroundMuted,
            )
            ChoiceRow {
                SleepDurations.forEachIndexed { i, min ->
                    val chip = @Composable { ChoiceChip(false, { onPick(min) }) { Text(formatMinutes(min)) } }
                    if (i == 0 && remaining == null) androidx.compose.foundation.layout.Box(Modifier.focusRequester(first)) { chip() } else chip()
                }
            }
            if (remaining != null) {
                Button(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth().focusRequester(first)) { Text(stringResource(R.string.sleep_stop)) }
            }
            Text(stringResource(R.string.sleep_warning_hint, SLEEP_EXTEND_MIN), fontSize = 15.sp, color = OnBackgroundMuted)
            if (!serviceOn) {
                Text(
                    stringResource(R.string.sleep_service_off),
                    fontSize = 15.sp, color = Color(0xFFFFB74D),
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
