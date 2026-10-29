package com.tvhome.launcher.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tvhome.launcher.TvHomeApp
import com.tvhome.launcher.remote.KeyRemapService
import com.tvhome.launcher.remote.PHONE_REMOTE_PORT
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.ui.theme.OnBackgroundMuted
import kotlinx.coroutines.launch
import java.net.Inet4Address

/** Adresse IPv4 de la box sur le réseau local (Wi-Fi ou Ethernet) ; null hors réseau. */
fun localIpv4(context: Context): String? {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    return cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
        ?.map { it.address }
        ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        ?.hostAddress
}

/** QR code noir sur blanc, un pixel par module (agrandi sans lissage à l'affichage). */
fun qrCode(text: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix[i % matrix.width, i / matrix.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Télécommande sur téléphone : activation, QR code à scanner et lien à usage personnel. */
@Composable
fun PhoneRemoteSettings() {
    val context = LocalContext.current
    val container = (context.applicationContext as TvHomeApp).container
    val scope = rememberCoroutineScope()
    val enabled by container.prefs.phoneRemoteEnabled.collectAsStateWithLifecycle(false)
    var token by remember { mutableStateOf("") }
    var renewed by remember { mutableStateOf(0) }
    LaunchedEffect(enabled, renewed) { if (enabled) token = container.prefs.phoneRemoteToken() }
    val ip = remember(enabled) { localIpv4(context) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SwitchRow(
                "Activer la télécommande sur téléphone",
                enabled,
                { on -> scope.launch { container.prefs.setPhoneRemoteEnabled(on) } },
                "Flèches, OK, volume, texte, lancement d'applis et envoi de fichiers ou d'APK, depuis le navigateur du téléphone.",
            )
        }
        if (enabled && token.isNotEmpty()) {
            if (ip == null) {
                item { Text("La box n'est connectée à aucun réseau.", color = OnBackground) }
            } else {
                val url = "http://$ip:$PHONE_REMOTE_PORT/?t=$token"
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        val qr = remember(url) { qrCode(url) }
                        Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(10.dp)) {
                            Image(qr, contentDescription = "QR code", filterQuality = FilterQuality.None, modifier = Modifier.size(190.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Scannez avec l'appareil photo du téléphone", color = OnBackground, fontSize = 17.sp)
                            Text("Le téléphone doit être sur le même réseau que la box. Ajoutez la page à l'écran d'accueil du téléphone pour la retrouver.", color = OnBackgroundMuted, fontSize = 14.sp)
                        }
                    }
                }
                item { Text(url, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = OnBackground) }
                item {
                    SmallButton("Nouveau lien (déconnecte les téléphones)", {
                        scope.launch {
                            container.prefs.renewPhoneRemoteToken()
                            renewed++
                        }
                    })
                }
            }
            if (KeyRemapService.instance == null) {
                item {
                    SettingHint("Pour les flèches, OK et Retour dans toutes les applis, activez aussi « Kairo » dans Accessibilité (Réglages › Assistant de configuration).")
                }
            }
        }
    }
}
