package com.drift.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.drift.tv.analytics.DriftAnalytics
import com.drift.tv.data.Sound
import com.drift.tv.playback.LayerState
import com.drift.tv.playback.TimerState
import com.drift.tv.ui.theme.AccentMagenta
import com.drift.tv.ui.theme.AccentViolet
import com.drift.tv.ui.theme.MoonWhite
import kotlinx.coroutines.delay

private val TIMER_CHOICES = listOf(15, 30, 45, 60, 90)
private const val PEEK_MS = 4_000L
// 6s was too twitchy in practice — you lose the controls while still reaching
// for the remote in the dark.
private const val CONTROLS_AUTOHIDE_MS = 12_000L
/** Idle time after the controls fade before the screen blacks itself out. */
private const val AUTO_LIGHTS_OUT_MS = 60_000L

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
    val controlsFocus = remember { FocusRequester() }

    LaunchedEffect(dimmed) { onDimChanged(dimmed) }

    LaunchedEffect(lastInteraction, dimmed, showMixPicker) {
        if (dimmed) return@LaunchedEffect
        controlsVisible = true
        // While the picker owns the screen, don't run the idle cycle at all:
        // hiding the bar would start swallowing the picker's own key presses,
        // and blacking out mid-choice is just rude.
        if (showMixPicker) return@LaunchedEffect
        delay(CONTROLS_AUTOHIDE_MS)
        controlsVisible = false
        // The point of the app is to fall asleep, so don't make the user walk
        // to the moon button — put the remote down and the room goes dark on
        // its own. Any key press restarts this from the top.
        delay(AUTO_LIGHTS_OUT_MS)
        dimmed = true
        DriftAnalytics.event("lights_out_triggered", mapOf("trigger" to "auto_idle"))
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(dimmed) {
        while (dimmed) { now = System.currentTimeMillis(); delay(250) }
    }
    val peeking = dimmed && now < peekUntil

    // Something must always hold focus, or the remote goes dead: with nothing
    // focused Compose never sees the key events at all, so even BACK stops
    // working. The control bar holds it normally (it stays composed even while
    // faded out); the root box only takes over for lights-out, where it swallows
    // every key to keep a stray press from firing a control in the dark.
    LaunchedEffect(dimmed, showMixPicker) {
        when {
            dimmed -> runCatching { rootFocus.requestFocus() }
            showMixPicker -> Unit  // the picker focuses itself
            else -> runCatching { controlsFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            // This full-screen box is an invisible focus target, so arrowing off
            // the edge of the control bar can land here and strand the user. If
            // that happens while the controls are up, bounce focus back to them.
            .onFocusChanged { state ->
                if (state.isFocused && !dimmed && !showMixPicker) {
                    runCatching { controlsFocus.requestFocus() }
                }
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInteraction = System.currentTimeMillis()
                when {
                    dimmed && event.key == Key.Back -> { dimmed = false; true }
                    dimmed -> { peekUntil = System.currentTimeMillis() + PEEK_MS; true }
                    // Controls faded out: swallow the key so it only brings them
                    // back rather than blind-firing whatever still holds focus.
                    !controlsVisible && event.key != Key.Back -> true
                    event.key == Key.Back && showMixPicker -> { showMixPicker = false; true }
                    event.key == Key.Back && showTimerPicker -> { showTimerPicker = false; true }
                    event.key == Key.Back -> { onBack(); true }
                    else -> false
                }
            }
    ) {
        AssetImage(primary.sound.image, primary.sound.title, Modifier.fillMaxSize())

        // Faded rather than removed from composition: AnimatedVisibility would
        // destroy whichever control holds focus, and with nothing focused
        // Compose stops receiving key events entirely — the remote goes dead
        // and even BACK stops working.
        val controlsAlpha by animateFloatAsState(
            targetValue = if (controlsVisible && !dimmed) 1f else 0f,
            animationSpec = tween(if (controlsVisible && !dimmed) 300 else 600),
            label = "controlsAlpha",
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .alpha(controlsAlpha)
        ) {
            ControlBar(
                layers = layers,
                timer = timer,
                isPlaying = isPlaying,
                defaultFocus = controlsFocus,
                showTimerPicker = showTimerPicker,
                onToggleTimerPicker = { showTimerPicker = !showTimerPicker },
                onTogglePlayPause = onTogglePlayPause,
                onSetTimer = { onSetTimer(it); showTimerPicker = false },
                onMix = { showMixPicker = true },
                onLightsOut = {
                    dimmed = true
                    DriftAnalytics.event("lights_out_triggered", mapOf("trigger" to "manual"))
                },
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
    defaultFocus: FocusRequester,
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
            // Keeps D-pad search inside the bar (same idiom HomeScreen uses for
            // its rows) so arrowing past an edge doesn't drop focus altogether.
            .focusGroup()
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
            Button(
                onClick = onTogglePlayPause,
                modifier = Modifier.focusRequester(defaultFocus)
            ) { Text(if (isPlaying) "Pause" else "Play") }
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // The handler has to sit on the focusable node itself. On the Row it is
        // an ancestor of the focus target, and D-pad focus search claims the
        // arrow keys first — so "◀ ▶ to adjust" just moved focus off the row.
        Button(
            // Removal is long-press only: this button is also the volume focus
            // target, and a stray OK used to drop the layer with no undo.
            onClick = {},
            onLongClick = { if (removable) onRemove() },
            modifier = Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onVolume((layer.volume - 0.05f).coerceAtLeast(0f)); true }
                    Key.DirectionRight -> { onVolume((layer.volume + 0.05f).coerceAtMost(1f)); true }
                    else -> false
                }
            }
        ) {
            Text(layer.sound.title)
        }
        VolumeBar(layer.volume)
        Text(
            if (removable) "◀ ▶ volume   ·   hold OK to remove" else "◀ ▶ volume",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A level bar reads at 10 feet; a percentage doesn't. */
@Composable
private fun VolumeBar(volume: Float) {
    Canvas(Modifier.size(width = 132.dp, height = 6.dp)) {
        val r = size.height / 2
        drawRoundRect(
            color = Color(0xFF3A3260),
            cornerRadius = CornerRadius(r, r),
        )
        if (volume > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(AccentViolet, AccentMagenta)),
                size = Size(size.width * volume, size.height),
                cornerRadius = CornerRadius(r, r),
            )
        }
    }
}

@Composable
private fun MixPicker(catalog: List<Sound>, onPick: (Sound) -> Unit) {
    val firstChoice = remember { FocusRequester() }
    // Without this the picker opens with nothing focused and the D-pad is dead.
    LaunchedEffect(catalog.isEmpty()) {
        if (catalog.isNotEmpty()) {
            repeat(5) {
                if (runCatching { firstChoice.requestFocus() }.isSuccess) return@LaunchedEffect
                withFrameNanos {}
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE0150F24))
            .padding(horizontal = 64.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Add a layer",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (catalog.isEmpty()) {
            Text("All sounds are already in the mix.")
        } else {
            // Has to scroll: the catalog outgrew a single screen of buttons, and
            // a plain Column silently clipped the last few sounds off-screen.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(catalog, key = { _, s -> s.id }) { i, s ->
                    Button(
                        onClick = { onPick(s) },
                        modifier = if (i == 0) Modifier.focusRequester(firstChoice) else Modifier
                    ) { Text(s.title) }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
