package com.tvhome.launcher.apps

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

/** Menu de l'appui long sur une appli. */
@Composable
fun AppActionsDialog(
    app: AppInfo,
    isFavorite: Boolean,
    isHidden: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
    onPlayStore: () -> Unit = {},
    /** Déplacer le favori directement sur l'accueil (null = action absente). */
    onMove: (() -> Unit)? = null,
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(420.dp)
                .background(Color(0xFF202329), RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(app.label, fontSize = 22.sp, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
            Action(if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris", Modifier.focusRequester(first)) {
                onToggleFavorite(); onDismiss()
            }
            if (isFavorite && onMove != null) Action("Déplacer") { onMove(); onDismiss() }
            Action(if (isHidden) "Afficher" else "Masquer") { onToggleHidden(); onDismiss() }
            Action("Infos de l'appli") { onAppInfo(); onDismiss() }
            Action("Voir dans le Play Store") { onPlayStore(); onDismiss() }
            Action("Désinstaller") { onUninstall(); onDismiss() }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun Action(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth()) { Text(label) }
}
