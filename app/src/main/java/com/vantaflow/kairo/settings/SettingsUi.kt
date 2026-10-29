package com.vantaflow.kairo.settings

import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
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
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

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
            TvInput(
                value = value,
                onValueChange = onValueChange,
                secret = secret,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(min = 240.dp, max = 560.dp)
                    .fillMaxWidth(),
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


/** Petit bouton des listes de réglages : tient à plusieurs sur une ligne du panneau. */
@Composable
fun SmallButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    androidx.tv.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        // Désactivé : non sélectionnable (sinon le focus s'y pose sans que rien ne se voie).
        modifier = Modifier.focusProperties { canFocus = enabled },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 15.sp, maxLines = 1)
    }
}

/**
 * Champ texte adapté à la télécommande : au repos, une case sélectionnable (sans clavier) ;
 * OK ouvre la saisie et le clavier, « Valider » ou la sortie du champ la referment.
 * Évite que le clavier surgisse dès que le focus arrive sur un réglage.
 */
@Composable
fun TvInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    placeholder: String = "",
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    /** Appelé quand la saisie est validée avec la touche « Valider » du clavier. */
    onDone: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var fieldHadFocus by remember(editing) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val box = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)
    val frame = Modifier
        .background(Color.White.copy(alpha = if (focused || editing) 0.14f else 0.08f), shape)
        .border(2.dp, if (focused || editing) accent else Color.Transparent, shape)
        .padding(14.dp)
    val shown = if (secret) "•".repeat(value.length) else value
    // La case reste en place pendant la saisie (le champ est à l'intérieur) : le focus passe
    // de la case au champ sans jamais retomber sur l'accueil derrière le panneau.
    Box(
        modifier
            .focusRequester(box)
            .onFocusChanged { focused = it.hasFocus }
            .onPreviewKeyEvent { e ->
                if (editing) return@onPreviewKeyEvent false
                val code = e.key.nativeKeyCode
                val enter = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                if (enter && e.type == KeyEventType.KeyUp) editing = true
                enter
            }
            .focusable()
            .then(frame),
    ) {
        if (editing) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = TextStyle(color = OnBackground, fontSize = fontSize),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    runCatching { box.requestFocus() }
                    editing = false
                    onDone()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    // Fin de saisie quand le champ perd le focus (pas au premier appel, avant qu'il l'ait).
                    .onFocusChanged {
                        if (it.isFocused) fieldHadFocus = true
                        else if (fieldHadFocus && editing) editing = false
                    },
            )
            LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
        } else {
            Text(
                shown.ifEmpty { placeholder },
                color = if (shown.isEmpty()) OnBackgroundMuted else OnBackground,
                fontSize = fontSize,
                maxLines = 1,
            )
        }
    }
    // Fin de saisie (Valider) : la case reprend le focus pour continuer à naviguer.
    var wasEditing by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (wasEditing && !editing) runCatching { box.requestFocus() }
        wasEditing = editing
    }
}
