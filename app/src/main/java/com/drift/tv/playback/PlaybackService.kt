package com.drift.tv.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.drift.tv.MainActivity
import com.drift.tv.analytics.DriftAnalytics
import com.drift.tv.data.Prefs
import com.drift.tv.data.Sound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One playing sound layer. Layer 0 is the "primary" sound that owns the MediaSession. */
data class LayerState(val sound: Sound, val volume: Float)

data class TimerState(val totalMs: Long, val remainingMs: Long)

/**
 * Foreground media service so audio keeps playing while the UI is dimmed to black
 * for hours. Runs one ExoPlayer per layer (max 3) — each set to REPEAT_MODE_ONE for
 * seamless looping (Media3 loops gaplessly within a single item).
 */
class PlaybackService : MediaSessionService() {

    companion object {
        const val MAX_LAYERS = 3
        private const val FADE_OUT_MS = 90_000L   // fade over the last 90 seconds
        private const val FADE_STEP_MS = 500L
        private const val FADE_IN_MS = 2_500L     // ease in, don't slam in
        private const val FADE_IN_STEP_MS = 50L
    }

    inner class LocalBinder : Binder() {
        val service: PlaybackService get() = this@PlaybackService
    }

    private val localBinder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaSession: MediaSession? = null
    private val players = mutableListOf<ExoPlayer>()
    private val sounds = mutableListOf<Sound>()

    private val _layers = MutableStateFlow<List<LayerState>>(emptyList())
    val layers: StateFlow<List<LayerState>> = _layers.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _timer = MutableStateFlow<TimerState?>(null)
    val timer: StateFlow<TimerState?> = _timer.asStateFlow()

    private var timerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val primary = newPlayer(handleAudioFocus = true)
        players += primary
        val sessionIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, primary)
            .setSessionActivity(sessionIntent)
            .build()
    }

    /**
     * Only the primary player manages audio focus. Every player requesting it
     * meant layer 2's request landed as a focus *loss* on layer 1, which then
     * paused itself — the mixer competing with itself for the app's own focus.
     * The app holds focus once, through the player that owns the MediaSession.
     */
    private fun newPlayer(handleAudioFocus: Boolean): ExoPlayer =
        ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                handleAudioFocus
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = players.firstOrNull()?.isPlaying == true
                    }
                })
            }

    // ── Public control surface (called by the UI through LocalBinder) ──────────

    /** Replaces everything with a single sound (the normal "pick a tile" flow). */
    fun play(sound: Sound) {
        clearLayersFrom(0)
        addLayerInternal(sound)
        DriftAnalytics.event("sound_played", mapOf("sound_id" to sound.id, "category" to sound.category))
    }

    /** Adds a mixed-in layer (rain + fireplace, etc.). No-op past MAX_LAYERS. */
    fun addLayer(sound: Sound) {
        if (sounds.size >= MAX_LAYERS || sounds.any { it.id == sound.id }) return
        addLayerInternal(sound)
        DriftAnalytics.event(
            "layer_added",
            mapOf("sound_id" to sound.id, "category" to sound.category, "layer_count" to sounds.size)
        )
    }

    fun removeLayer(soundId: String) {
        val i = sounds.indexOfFirst { it.id == soundId }
        if (i <= 0) { if (i == 0) stopAll(); return }  // removing primary stops everything
        players[i].release()
        players.removeAt(i)
        sounds.removeAt(i)
        publishLayers()
        saveMix()
        DriftAnalytics.event("layer_removed", mapOf("sound_id" to soundId, "layer_count" to sounds.size))
    }

    fun setLayerVolume(soundId: String, volume: Float) {
        val i = sounds.indexOfFirst { it.id == soundId }
        if (i < 0) return
        players[i].volume = volume.coerceIn(0f, 1f)
        publishLayers()
        scope.launch(Dispatchers.IO) { Prefs.saveVolume(this@PlaybackService, soundId, volume) }
    }

    fun togglePlayPause() {
        val playing = players.firstOrNull()?.isPlaying == true
        players.forEach { if (playing) it.pause() else it.play() }
    }

    fun stopAll() {
        cancelTimer()
        clearLayersFrom(0)
        _isPlaying.value = false
    }

    fun setSleepTimer(minutes: Int?) {
        cancelTimer()
        scope.launch(Dispatchers.IO) { Prefs.saveTimerMinutes(this@PlaybackService, minutes) }
        DriftAnalytics.event(
            if (minutes != null) "sleep_timer_set" else "sleep_timer_cancelled",
            mapOf("minutes" to minutes)
        )
        if (minutes == null) return
        val total = minutes * 60_000L
        _timer.value = TimerState(total, total)
        timerJob = scope.launch {
            var remaining = total
            while (remaining > 0) {
                delay(FADE_STEP_MS)
                remaining -= FADE_STEP_MS
                _timer.value = TimerState(total, remaining.coerceAtLeast(0))
                if (remaining in 1 until FADE_OUT_MS) {
                    // Gradual fade instead of an abrupt stop.
                    val scale = remaining / FADE_OUT_MS.toFloat()
                    sounds.forEachIndexed { i, s ->
                        players[i].volume = layerTargetVolume(s) * scale
                    }
                }
            }
            stopAll()
            // HDMI-CEC standby is intentionally NOT triggered here by default:
            // most TVs also cut audio on CEC standby, and system apps hold the
            // relevant privileged permission. See README for the experimental path.
        }
    }

    fun cancelTimer() {
        timerJob?.cancel(); timerJob = null
        _timer.value = null
        // restore volumes in case we cancelled mid-fade
        sounds.forEachIndexed { i, s -> players[i].volume = layerTargetVolume(s) }
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private fun layerTargetVolume(sound: Sound): Float =
        _layers.value.firstOrNull { it.sound.id == sound.id }?.volume ?: sound.defaultVolume

    private fun addLayerInternal(sound: Sound) {
        val player =
            if (sounds.isEmpty()) players[0]
            else newPlayer(handleAudioFocus = false).also { players += it }
        sounds += sound
        scope.launch {
            val vol = Prefs.savedVolume(this@PlaybackService, sound)
            player.volume = 0f
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(sound.audioUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder().setTitle(sound.title).setArtist("Drift").build()
                    )
                    .build()
            )
            player.prepare()
            player.play()
            publishLayers()
            saveMix()
            // Ramp up rather than starting at full tilt — the fade-out already
            // exists at the other end, and a hard start is jarring at bedtime.
            val steps = (FADE_IN_MS / FADE_IN_STEP_MS).toInt()
            for (i in 1..steps) {
                delay(FADE_IN_STEP_MS)
                // Bail out if the layer was removed or re-targeted mid-fade.
                if (sounds.none { it.id == sound.id }) return@launch
                player.volume = vol * (i / steps.toFloat())
            }
            player.volume = vol
            publishLayers()
        }
    }

    private fun clearLayersFrom(index: Int) {
        while (sounds.size > index) {
            val i = sounds.size - 1
            if (i == 0) { players[0].stop(); players[0].clearMediaItems() }
            else { players[i].release(); players.removeAt(i) }
            sounds.removeAt(i)
        }
        publishLayers()
    }

    private fun publishLayers() {
        _layers.value = sounds.mapIndexed { i, s -> LayerState(s, players[i].volume) }
    }

    private fun saveMix() {
        val ids = sounds.map { it.id }
        scope.launch(Dispatchers.IO) { Prefs.saveMix(this@PlaybackService, ids) }
    }

    /** Sound ids of the last mix, for the home screen's "resume" tile. */
    suspend fun lastMixIds(): List<String> = Prefs.savedMix(this)

    /**
     * Restores the previous mix and re-arms the remembered sleep timer. Called
     * when the user picks the resume tile rather than a single sound.
     */
    fun resumeLastMix(catalog: List<Sound>, ids: List<String>) {
        clearLayersFrom(0)
        val restored = ids.mapNotNull { id -> catalog.firstOrNull { it.id == id } }.take(MAX_LAYERS)
        restored.forEach { addLayerInternal(it) }
        DriftAnalytics.event(
            "mix_resumed",
            mapOf("sound_ids" to restored.joinToString(",") { it.id }, "layer_count" to restored.size)
        )
        scope.launch {
            Prefs.savedTimerMinutes(this@PlaybackService)?.let { setSleepTimer(it) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else localBinder

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (players.firstOrNull()?.isPlaying != true) stopSelf()
    }

    override fun onDestroy() {
        cancelTimer()
        scope.cancel()
        mediaSession?.release(); mediaSession = null
        players.forEach { it.release() }; players.clear()
        super.onDestroy()
    }
}
