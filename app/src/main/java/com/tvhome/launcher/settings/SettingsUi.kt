package com.tvhome.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.tv.material3.OutlinedButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.ui.theme.OnBackgroundMuted

private val OnLight = Color(0xFF101216)

/**
 * Choix d'une liste (thème, taille, intervalle…) : sélectionné = fond de la couleur d'accent et ✓,
 * focus = fond clair. Lisible à 3 mètres, contrairement aux puces tv-material par défaut.
 */
@Composable
fun ChoiceChip(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) accent else Color.White.copy(alpha = 0.08f),
            contentColor = if (selected) OnLight else OnBackground,
            focusedContainerColor = Color.White,
            focusedContentColor = OnLight,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) Text("✓", fontSize = 16.sp)
            content()
        }
    }
}

/** Rangée de choix qui passe à la ligne au lieu d'être coupée au bord de l'écran. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        content()
    }
}

/** Libellé d'un groupe de réglages dans une section. */
@Composable
fun SettingLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = OnBackground, fontSize = 18.sp, modifier = modifier.padding(top = 8.dp))
}

/** Explication en petit sous un réglage. */
@Composable
fun SettingHint(text: String) {
    Text(text, color = OnBackgroundMuted, fontSize = 14.sp)
}

/**
 * Champ texte + bouton : le libellé au-dessus, puis le champ (qui prend la place disponible)
 * et le bouton côte à côte. Le bouton n'est jamais écrasé, quelle que soit la largeur.
 */
@Composable
fun FieldWithButton(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    buttonText: String,
    onButton: () -> Unit,
    secret: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = OnBackgroundMuted, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = TextStyle(color = OnBackground, fontSize = 18.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(min = 240.dp, max = 560.dp)
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .padding(14.dp),
            )
            OutlinedButton(onClick = onButton) { Text(buttonText) }
        }
    }
}

/**
 * Ligne de réglage « titre + description + interrupteur » (maquette Claude Design) :
 * toute la ligne prend le focus et OK bascule l'interrupteur.
 */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = { onChange(!checked) },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = OnBackground,
            focusedContainerColor = Color.White.copy(alpha = 0.10f),
            focusedContentColor = OnBackground,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 17.sp, color = OnBackground)
                description?.let { Text(it, fontSize = 13.sp, color = OnBackgroundMuted) }
            }
            // Interrupteur dessiné (pas un second élément focusable dans la ligne).
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(width = 46.dp, height = 26.dp)
                    .background(if (checked) accent else Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                    .padding(3.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(20.dp)
                        .background(if (checked) OnLight else OnBackgroundMuted, RoundedCornerShape(50)),
                )
            }
        }
    }
}

