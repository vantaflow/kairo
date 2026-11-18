package com.vantaflow.kairo.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import com.vantaflow.kairo.ui.theme.Themes
import com.vantaflow.kairo.ui.theme.displayName
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R

/** Choix du thème visuel. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ThemeSettings(vm: SettingsViewModel) {
    val current by vm.themeId.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Thème personnalisé : activé en modifiant une option plus bas (pas de 7e choix, la rangée tient sur une ligne).
        ChoiceRow {
            Themes.forEach { theme ->
                ChoiceChip(selected = theme.id == current, onClick = { vm.setThemeId(theme.id) }) { Text(theme.displayName) }
            }
        }
        Text(stringResource(R.string.settings_theme_hint), color = OnBackgroundMuted)
        CustomThemeEditor(vm)
    }
}

/** Météo : activation, ville (vide = automatique), unité. */
@Composable
fun WeatherSettings(vm: SettingsViewModel) {
    val enabled by vm.weatherEnabled.collectAsStateWithLifecycle()
    val city by vm.weatherCity.collectAsStateWithLifecycle()
    val fahrenheit by vm.useFahrenheit.collectAsStateWithLifecycle()
    var cityText by remember(city) { mutableStateOf(city) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SwitchRow(stringResource(R.string.settings_weather_show), enabled, vm::setWeatherEnabled, stringResource(R.string.settings_weather_show_desc))
        SwitchRow(stringResource(R.string.settings_weather_fahrenheit), fahrenheit, vm::setUseFahrenheit, stringResource(R.string.settings_weather_fahrenheit_desc))
        FieldWithButton(stringResource(R.string.settings_weather_city), cityText, { cityText = it }, stringResource(R.string.settings_save), { vm.setWeatherCity(cityText) })
        Text(stringResource(R.string.settings_weather_credits), color = OnBackgroundMuted)
    }
}
