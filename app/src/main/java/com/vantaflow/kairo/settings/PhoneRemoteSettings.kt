package com.vantaflow.kairo.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.remote.KeyRemapService
import com.vantaflow.kairo.remote.PHONE_REMOTE_PORT
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import kotlinx.coroutines.launch
import java.net.Inet4Address
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R

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

    // Marge haut et bas : le 1er et le dernier bouton grossissent au focus.
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
        item {
            SwitchRow(
                stringResource(R.string.settings_phone_enable),
                enabled,
                { on -> scope.launch { container.prefs.setPhoneRemoteEnabled(on) } },
                stringResource(R.string.settings_phone_enable_desc),
            )
        }
        if (enabled && token.isNotEmpty()) {
            if (ip == null) {
                item { Text(stringResource(R.string.settings_phone_no_network), color = OnBackground) }
            } else {
                val url = "http://$ip:$PHONE_REMOTE_PORT/?t=$token"
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        val qr = remember(url) { qrCode(url) }
                        Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(10.dp)) {
                            Image(qr, contentDescription = stringResource(R.string.settings_phone_qr), filterQuality = FilterQuality.None, modifier = Modifier.size(190.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.settings_phone_scan), color = OnBackground, fontSize = 17.sp)
                            Text(stringResource(R.string.settings_phone_scan_desc), color = OnBackgroundMuted, fontSize = 14.sp)
                        }
                    }
                }
                item { Text(url, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = OnBackground) }
                item {
                    SmallButton(stringResource(R.string.settings_phone_new_link), {
                        scope.launch {
                            container.prefs.renewPhoneRemoteToken()
                            renewed++
                        }
                    })
                }
            }
            if (KeyRemapService.instance == null) {
                item {
                    SettingHint(stringResource(R.string.settings_phone_accessibility_hint, settingsPath(R.string.section_setup)))
                }
            }
        }
    }
}
