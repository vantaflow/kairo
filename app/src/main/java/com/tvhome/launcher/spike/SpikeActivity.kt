package com.tvhome.launcher.spike

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.tvhome.launcher.ui.theme.TvHomeTheme

/** Écran de diagnostic : `adb shell am start -n com.tvhome.launcher/.spike.SpikeActivity`. */
class SpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = EpgProbe.run(this)
        val lines = buildList {
            add("canReadChannels=${report.canReadChannels} channels=${report.channels.size} " +
                "watchNext=${report.watchNextCount} preview=${report.previewProgramCount}")
            report.error?.let { add("error=$it") }
            report.channels.forEach {
                add("channel id=${it.id} pkg=${it.packageName} name=${it.displayName} browsable=${it.browsable}")
            }
        }
        lines.forEach { Log.i(TAG, it) }
        setContent {
            TvHomeTheme {
                Column(Modifier.padding(32.dp).verticalScroll(rememberScrollState())) {
                    lines.forEach { Text(it) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "EpgProbe"
    }
}
