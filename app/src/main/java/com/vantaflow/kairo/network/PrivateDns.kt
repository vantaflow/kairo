package com.vantaflow.kairo.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.vantaflow.kairo.settings.ChoiceChip
import com.vantaflow.kairo.settings.ChoiceRow
import com.vantaflow.kairo.settings.SettingLabel
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

/** DNS privé (DNS sur TLS) proposé dans la liste ; [host] nul = DNS du réseau (box Internet). */
data class DnsChoice(val label: String, val host: String?, val note: String)

val DnsChoices = listOf(
    DnsChoice("Automatique", null, "DNS de votre box Internet, sans chiffrement imposé."),
    DnsChoice("Cloudflare", "one.one.one.one", "Rapide, sans filtrage."),
    DnsChoice("Google", "dns.google", "Sans filtrage."),
    DnsChoice("Quad9", "dns.quad9.net", "Bloque les sites malveillants."),
    DnsChoice("AdGuard", "dns.adguard-dns.com", "Bloque pubs et pistage."),
    DnsChoice("AdGuard Famille", "family.adguard-dns.com", "Pubs, pistage et contenus adultes bloqués."),
    DnsChoice("Cloudflare Famille", "family.cloudflare-dns.com", "Logiciels malveillants et contenus adultes bloqués."),
    DnsChoice("Mullvad", "dns.mullvad.net", "Sans filtrage, sans journal."),
    DnsChoice("Mullvad anti-pub", "adblock.dns.mullvad.net", "Bloque pubs et pistage, sans journal."),
)

const val PRIVATE_DNS_ADB = "adb shell pm grant com.vantaflow.kairo android.permission.WRITE_SECURE_SETTINGS"

fun canSetPrivateDns(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

/** Hôte DNS privé actif (mode « nom d'hôte »), sinon null. */
fun currentPrivateDns(context: Context): String? = com.vantaflow.kairo.adblock.privateDnsHost(context)

/** Applique le choix à toute la box (comme Paramètres Android › Réseau › DNS privé). */
fun setPrivateDns(context: Context, host: String?): Boolean = runCatching {
    val cr = context.contentResolver
    if (host == null) {
        Settings.Global.putString(cr, "private_dns_mode", "opportunistic")
    } else {
        Settings.Global.putString(cr, "private_dns_specifier", host)
        Settings.Global.putString(cr, "private_dns_mode", "hostname")
    }
}.isSuccess

/** Liste de DNS privés : un appui suffit, l'effet est immédiat pour toutes les applis. */
@Composable
fun PrivateDnsPicker() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(currentPrivateDns(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val allowed = remember { canSetPrivateDns(context) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingLabel("DNS privé (DNS sur TLS)")
        // Deux lignes : sur une seule, les neuf choix deviendraient illisibles.
        DnsChoices.chunked(5).forEach { line -> ChoiceRow {
            line.forEach { c ->
                val selected = if (c.host == null) current == null else current.equals(c.host, ignoreCase = true)
                ChoiceChip(selected, {
                    if (!allowed) {
                        message = "Autorisation à donner une seule fois depuis un ordinateur :"
                    } else if (setPrivateDns(context, c.host)) {
                        current = currentPrivateDns(context)
                        // Un DNS privé strict remplace tout autre DNS : le bloqueur de Kairo (VPN) n'a plus d'effet.
                        if (c.host != null && com.vantaflow.kairo.adblock.AdBlock.running.value) {
                            com.vantaflow.kairo.adblock.AdBlock.stop(context)
                        }
                        message = "${c.label} : ${c.note}"
                    } else {
                        message = "La box a refusé le changement."
                    }
                }) { Text(c.label) }
            }
        } }
        val active = DnsChoices.firstOrNull { it.host != null && it.host.equals(current, ignoreCase = true) }
        Text(
            message ?: when {
                current == null -> "DNS de votre box Internet."
                active != null -> "${active.label} (${active.host}) : ${active.note}"
                else -> "DNS privé actuel : $current"
            },
            color = OnBackgroundMuted, fontSize = 15.sp,
        )
        if (!allowed && message != null) {
            Text(PRIVATE_DNS_ADB, color = OnBackground, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
