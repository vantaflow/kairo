package com.vantaflow.kairo.adblock

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.settings.ChoiceChip
import com.vantaflow.kairo.settings.ChoiceRow
import com.vantaflow.kairo.settings.SettingLabel
import com.vantaflow.kairo.settings.SwitchRow
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Réglages du bloqueur de pubs : marche / arrêt, filtre, compteur. */
@Composable
fun AdBlockSettings() {
    val context = LocalContext.current
    val prefs = (context.applicationContext as TvHomeApp).container.prefs
    val scope = rememberCoroutineScope()
    val enabled by prefs.adBlockEnabled.collectAsStateWithLifecycle(false)
    val filter by prefs.adBlockFilter.collectAsStateWithLifecycle(AdFilter.Ads.name)
    val running by AdBlock.running.collectAsStateWithLifecycle()
    // Accord d'Android pour créer le VPN local (une seule fois).
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) scope.launch {
            prefs.setAdBlockEnabled(true)
            AdBlock.start(context)
        }
    }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            tick++
        }
    }
    val fmt = remember { NumberFormat.getIntegerInstance(Locale.FRENCH) }

    val strictDns = remember { privateDnsHost(context) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // DNS privé strict : il remplace tout autre DNS, le bloqueur de Kairo ne peut pas tourner.
        if (strictDns != null) item {
            Text(
                if (isAdBlockingDns(strictDns)) "✓ Les pubs sont déjà bloquées par le DNS privé de la box ($strictDns), sans rien qui tourne en arrière-plan. Le bloqueur de Kairo n'est pas nécessaire."
                else "Le DNS privé de la box ($strictDns) remplace tout autre DNS : le bloqueur de Kairo ne peut pas fonctionner. Pour bloquer les pubs, mettez dns.adguard-dns.com dans Paramètres Android › Réseau › DNS privé.",
                color = OnBackground, fontSize = 18.sp,
            )
        }
        // Toujours un élément sélectionnable : sinon le focus retombe sur l'accueil derrière.
        if (strictDns != null) item {
            com.vantaflow.kairo.settings.SmallButton("Paramètres réseau d'Android", {
                com.vantaflow.kairo.settings.openSafely(context, android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
            })
        }
        if (strictDns == null) item {
            SwitchRow(
                "Bloquer les pubs", enabled,
                { on ->
                    if (!on) scope.launch {
                        prefs.setAdBlockEnabled(false)
                        AdBlock.stop(context)
                    } else {
                        val ask = VpnService.prepare(context)
                        if (ask != null) consent.launch(ask) else scope.launch {
                            prefs.setAdBlockEnabled(true)
                            AdBlock.start(context)
                        }
                    }
                },
                "Pubs et pistage bloqués dans toutes les applis et sites, par un filtre DNS (AdGuard DNS).",
            )
        }
        if (enabled && strictDns == null) {
            item {
                tick.let { }
                Text(
                    if (running) "${fmt.format(AdBlock.blocked.get())} pubs et traqueurs bloqués depuis l'allumage (sur ${fmt.format(AdBlock.queries.get())} requêtes)."
                    else "Démarrage…",
                    color = OnBackground, fontSize = 18.sp,
                )
            }
            item {
                SettingLabel("Filtre")
                ChoiceRow {
                    AdFilter.entries.forEach { f ->
                        ChoiceChip(filter == f.name, { scope.launch { prefs.setAdBlockFilter(f.name) } }) { Text(f.label) }
                    }
                }
            }
        }
        item {
            Text(
                "Profil enfant : le filtre famille (contenus adultes bloqués aussi) s'applique automatiquement.\n" +
                    "Les pubs dans les vidéos YouTube viennent des mêmes serveurs que les vidéos : aucun filtre DNS ne peut les retirer. " +
                    "Utilisez SmartTube pour YouTube sans pub.\n" +
                    "Android n'accepte qu'un VPN à la fois. Si une appli ne fonctionne plus, désactivez le bloqueur.",
                color = OnBackgroundMuted, fontSize = 15.sp,
            )
        }
    }
}
