package com.vantaflow.kairo.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R
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

/**
 * DNS privé (DNS sur TLS) proposé dans la liste ; [host] nul = DNS du réseau (box Internet).
 * [brand] : nom affiché tel quel (marque), sinon [labelRes] traduit.
 */
data class DnsChoice(val brand: String?, val host: String?, @StringRes val note: Int, @StringRes val labelRes: Int = 0) {
    fun label(context: Context): String = brand ?: context.getString(labelRes)
}

val DnsChoices = listOf(
    DnsChoice(null, null, R.string.dns_note_auto, R.string.dns_auto),
    DnsChoice("Cloudflare", "one.one.one.one", R.string.dns_note_cloudflare),
    DnsChoice("Google", "dns.google", R.string.dns_note_google),
    DnsChoice("Quad9", "dns.quad9.net", R.string.dns_note_quad9),
    DnsChoice("AdGuard", "dns.adguard-dns.com", R.string.dns_note_adguard),
    DnsChoice(null, "family.adguard-dns.com", R.string.dns_note_adguard_family, R.string.dns_adguard_family),
    DnsChoice(null, "family.cloudflare-dns.com", R.string.dns_note_cloudflare_family, R.string.dns_cloudflare_family),
    DnsChoice("Mullvad", "dns.mullvad.net", R.string.dns_note_mullvad),
    DnsChoice(null, "adblock.dns.mullvad.net", R.string.dns_note_mullvad_adblock, R.string.dns_mullvad_adblock),
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
        SettingLabel(stringResource(R.string.dns_title))
        // Deux lignes : sur une seule, les neuf choix deviendraient illisibles.
        DnsChoices.chunked(5).forEach { line -> ChoiceRow {
            line.forEach { c ->
                val selected = if (c.host == null) current == null else current.equals(c.host, ignoreCase = true)
                ChoiceChip(selected, {
                    if (!allowed) {
                        message = context.getString(R.string.system_permission_adb)
                    } else if (setPrivateDns(context, c.host)) {
                        current = currentPrivateDns(context)
                        // Un DNS privé strict remplace tout autre DNS : le bloqueur de Kairo (VPN) n'a plus d'effet.
                        if (c.host != null && com.vantaflow.kairo.adblock.AdBlock.running.value) {
                            com.vantaflow.kairo.adblock.AdBlock.stop(context)
                        }
                        message = context.getString(R.string.dns_applied, c.label(context), context.getString(c.note))
                    } else {
                        message = context.getString(R.string.system_change_refused)
                    }
                }) { Text(c.label(context)) }
            }
        } }
        val active = DnsChoices.firstOrNull { it.host != null && it.host.equals(current, ignoreCase = true) }
        Text(
            message ?: when {
                current == null -> stringResource(R.string.dns_isp)
                active != null -> stringResource(R.string.dns_active, active.label(context), active.host.orEmpty(), stringResource(active.note))
                else -> stringResource(R.string.dns_current, current.orEmpty())
            },
            color = OnBackgroundMuted, fontSize = 15.sp,
        )
        if (!allowed && message != null) {
            Text(PRIVATE_DNS_ADB, color = OnBackground, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
