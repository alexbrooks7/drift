package com.drift.tv.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drift.tv.data.Sound
import com.drift.tv.data.SoundCatalog
import com.drift.tv.playback.LayerState
import com.drift.tv.playback.PlaybackService
import com.drift.tv.playback.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DriftViewModel(app: Application) : AndroidViewModel(app) {

    val catalog: List<Sound> = SoundCatalog.load(app)

    private var service: PlaybackService? = null

    private val _layers = MutableStateFlow<List<LayerState>>(emptyList())
    val layers: StateFlow<List<LayerState>> = _layers.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _timer = MutableStateFlow<TimerState?>(null)
    val timer: StateFlow<TimerState?> = _timer.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val s = (binder as PlaybackService.LocalBinder).service
            service = s
            viewModelScope.launch { s.layers.collect { _layers.value = it } }
            viewModelScope.launch { s.isPlaying.collect { _isPlaying.value = it } }
            viewModelScope.launch { s.timer.collect { _timer.value = it } }
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
    }

    init {
        val ctx = app.applicationContext
        // startService + bindService: keeps the service alive independently of the UI
        // (foreground promotion happens via the MediaSession notification on play).
        ctx.startService(Intent(ctx, PlaybackService::class.java))
        ctx.bindService(Intent(ctx, PlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    fun play(sound: Sound) = service?.play(sound)
    fun addLayer(sound: Sound) = service?.addLayer(sound)
    fun removeLayer(id: String) = service?.removeLayer(id)
    fun setLayerVolume(id: String, v: Float) = service?.setLayerVolume(id, v)
    fun togglePlayPause() = service?.togglePlayPause()
    fun stop() = service?.stopAll()
    fun setSleepTimer(minutes: Int?) = service?.setSleepTimer(minutes)

    override fun onCleared() {
        runCatching { getApplication<Application>().unbindService(connection) }
    }
}
