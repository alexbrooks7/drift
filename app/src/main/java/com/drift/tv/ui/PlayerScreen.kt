package com.drift.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.drift.tv.data.Sound
import com.drift.tv.playback.LayerState
import com.drift.tv.playback.TimerState
import com.drift.tv.ui.theme.AccentMagenta
import com.drift.tv.ui.theme.AccentViolet
import com.drift.tv.ui.theme.MoonWhite
import kotlinx.coroutines.delay

private val TIMER_CHOICES = listOf(15, 30, 45, 60, 90)
private const val PEEK_MS = 4_000L
private const val CONTROLS_AUTOHIDE_MS = 6_000L

@Composable
fun PlayerScreen(
    layers: List<LayerState>,
    catalog: List<Sound>,
    timer: TimerState?,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSetTimer: (Int?) -> Unit,
    onAddLayer: (Sound) -> Unit,
    onRemoveLayer: (String) -> Unit,
    onLayerVolume: (String, Float) -> Unit,
    onDimChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val primary = layers.firstOrNull() ?: return
    var dimmed by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var peekUntil by remember { mutableLongStateOf(0L) }
    var showMixPicker by remember { mutableStateOf(false) }
    var showTimerPicker by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(dimmed) { onDimChanged(dimmed) }

    LaunchedEffect(lastInteraction, dimmed) {
        if (!dimmed) {
            controlsVisible = true
            delay(CONTROLS_AUTOHIDE_MS)
            controlsVisible = false
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(dimmed) {
        while (dimmed) { now = System.currentTimeMillis(); delay(250) }
    }
    val peeking = dimmed && now < peekUntil

    LaunchedEffect(dimmed) { if (dimmed) rootFocus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInteraction = System.currentTimeMillis()
                when {
                    dimmed && event.key == Key.Back -> { dimmed = false; true }
                    dimmed -> { peekUntil = System.currentTimeMillis() + PEEK_MS; true }
                    event.key == Key.Back && showMixPicker -> { showMixPicker = false; true }
                    event.key == Key.Back && showTimerPicker -> { showTimerPicker = false; true }
                    event.key == Key.Back -> { onBack(); true }
                    else -> false
                }
            }
    ) {
        AssetImage(primary.sound.image, primary.sound.title, Modifier.fillMaxSize())

        AnimatedVisibility(
            visible = controlsVisible && !dimmed,
            enter = fadeIn(tween(300)), exit = fadeOut(tween(600)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ControlBar(
                layers = layers,
                timer = timer,
                isPlaying = isPlaying,
                showTimerPicker = showTimerPicker,
                onToggleTimerPicker = { showTimerPicker = !showTimerPicker },
                onTogglePlayPause = onTogglePlayPause,
                onSetTimer = { onSetTimer(it); showTimerPicker = false },
                onMix = { showMixPicker = true },
                onLightsOut = { dimmed = true },
                onRemoveLayer = onRemoveLayer,
                onLayerVolume = onLayerVolume,
            )
        }

        if (showMixPicker && !dimmed) {
            MixPicker(
                catalog = catalog.filter { c -> layers.none { it.sound.id == c.id } },
                onPick = { showMixPicker = false; onAddLayer(it) },
            )
        }

        // "Lights out": a true #000000 layer. On OLED these pixels are off.
        AnimatedVisibility(visible = dimmed, enter = fadeIn(tween(1500)), exit = fadeOut(tween(400))) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }

        AnimatedVisibility(
            visible = peeking,
            enter = fadeIn(tween(250)), exit = fadeOut(tween(800)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    layers.joinToString("  +  ") { it.sound.title },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0x99EDEBF7)
                )
                Text(
                    timer?.let { "Sleep timer  ${formatMs(it.remainingMs)}" } ?: "No sleep timer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0x669B93B8),
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "BACK to wake",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0x449B93B8),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun ControlBar(
    layers: List<LayerState>,
    timer: TimerState?,
    isPlaying: Boolean,
    showTimerPicker: Boolean,
    onToggleTimerPicker: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSetTimer: (Int?) -> Unit,
    onMix: () -> Unit,
    onLightsOut: () -> Unit,
    onRemoveLayer: (String) -> Unit,
    onLayerVolume: (String, Float) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0150F24))))
            .padding(horizontal = 48.dp, vertical = 20.dp)
    ) {
        layers.forEach { layer ->
            LayerRow(
                layer = layer,
                removable = layers.size > 1,
                onVolume = { onLayerVolume(layer.sound.id, it) },
                onRemove = { onRemoveLayer(layer.sound.id) },
            )
        }

        if (showTimerPicker) {
            Row(
                Modifier.padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TIMER_CHOICES.forEach { m -> Button(onClick = { onSetTimer(m) }) { Text("${m}m") } }
                Button(onClick = { onSetTimer(null) }) { Text("Off") }
            }
        }

        Row(
            Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onTogglePlayPause) { Text(if (isPlaying) "Pause" else "Play") }
            Button(onClick = onMix) { Text("Mix another sound") }
            TimerRing(timer = timer, onClick = onToggleTimerPicker)
            MoonButton(onClick = onLightsOut)
        }
    }
}

/**
 * The app's signature control, adapted from the reference's glowing mood-circle:
 * a soft radial halo behind a crescent moon, with a label underneath — same
 * "glowing circle + caption" shape as the reference's mood row, repurposed here
 * as the primary "lights out" action.
 */
@Composable
private fun MoonButton(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            modifier = Modifier
                .size(64.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(MoonWhite.copy(alpha = 0.35f), Color.Transparent),
                            radius = size.minDimension * 1.1f,
                        ),
                        radius = size.minDimension * 1.1f,
                        center = center,
                    )
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("\uD83C\uDF19", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Text(
            "Lights out",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Circular sleep-timer countdown, borrowed from the reference's "70% today"
 *  progress ring — here the sweep genuinely means something: time remaining. */
@Composable
private fun TimerRing(timer: TimerState?, onClick: () -> Unit) {
    val progress = if (timer != null && timer.totalMs > 0) {
        timer.remainingMs.toFloat() / timer.totalMs.toFloat()
    } else 0f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            modifier = Modifier.size(64.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    val stroke = Stroke(width = 5.dp.toPx())
                    drawCircle(color = Color(0xFF3A3260), style = stroke)
                    if (timer != null) {
                        drawArc(
                            brush = Brush.linearGradient(listOf(AccentViolet, AccentMagenta)),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = stroke,
                        )
                    }
                }
                Text(
                    timer?.let { formatMs(it.remainingMs) } ?: "\u23F1",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            "Sleep timer",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun LayerRow(
    layer: LayerState,
    removable: Boolean,
    onVolume: (Float) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onVolume((layer.volume - 0.05f).coerceAtLeast(0f)); true }
                    Key.DirectionRight -> { onVolume((layer.volume + 0.05f).coerceAtMost(1f)); true }
                    else -> false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(onClick = { if (removable) onRemove() }) {
            Text(if (removable) "✕ ${layer.sound.title}" else layer.sound.title)
        }
        Text("Volume ${(layer.volume * 100).toInt()}%   (◀ ▶ to adjust)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MixPicker(catalog: List<Sound>, onPick: (Sound) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE0150F24))
            .padding(64.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Add a layer",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        catalog.forEach { s -> Button(onClick = { onPick(s) }) { Text(s.title) } }
        if (catalog.isEmpty()) Text("All sounds are already in the mix.")
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
