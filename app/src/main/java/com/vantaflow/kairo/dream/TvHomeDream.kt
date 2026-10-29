package com.vantaflow.kairo.dream

import android.service.dreams.DreamService
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.ui.DesignScale
import com.vantaflow.kairo.home.formatDate
import com.vantaflow.kairo.home.formatTime
import com.vantaflow.kairo.settings.WallpaperConfig
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import com.vantaflow.kairo.wallpaper.WallpaperLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** Une image toutes les 45 s pendant l'écran de veille. */
const val DREAM_SLIDE_MS = 45_000L

/** Écran de veille Kairo : images Pixabay (ou fond choisi), heure, date et météo. */
class TvHomeDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TvHomeDream)
            setViewTreeSavedStateRegistryOwner(this@TvHomeDream)
            setContent { DesignScale { DreamContent() } }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun DreamContent() {
    val context = LocalContext.current
    val container = (context.applicationContext as TvHomeApp).container
    var config by remember { mutableStateOf<WallpaperConfig>(WallpaperConfig.Default) }
    var image by remember { mutableStateOf<String?>(null) }
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var weather by remember { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()

    LaunchedEffect(Unit) {
        config = container.prefs.wallpaper.first()
        val city = container.prefs.weatherCity.first()
        val fahrenheit = container.prefs.useFahrenheit.first()
        weather = if (container.prefs.weatherEnabled.first()) {
            withContext(Dispatchers.IO) { runCatching { container.weather(city, fahrenheit) }.getOrNull() }?.headerText()
        } else null
    }
    LaunchedEffect(Unit) {
        while (true) {
            val now = container.clock.now()
            time = formatTime(now, zone, container.prefs.use24h.first())
            date = formatDate(now, zone)
            delay(10_000)
        }
    }
    LaunchedEffect(config) {
        // Diaporama Pixabay, quel que soit le fond de l'accueil (thème Nature par défaut).
        val query = (config as? WallpaperConfig.Pixabay)?.query ?: "nature landscape"
        while (true) {
            withContext(Dispatchers.IO) { container.nextWallpaper(query) }?.let { image = it }
            delay(DREAM_SLIDE_MS)
        }
    }

    Box(Modifier.fillMaxSize()) {
        WallpaperLayer(config)
        Crossfade(targetState = image, animationSpec = tween(2_000), label = "dream") { url ->
            if (url != null) {
                AsyncImage(
                    model = remember(url) { ImageRequest.Builder(context).data(url).size(1920, 1080).allowRgb565(true).build() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(64.dp)) {
            Text(time, fontSize = 96.sp, fontWeight = FontWeight.Light, color = OnBackground)
            Text(listOfNotNull(date, weather).joinToString(" · "), fontSize = 26.sp, color = OnBackgroundMuted)
        }
    }
}
