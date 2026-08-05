package com.drift.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "drift_prefs")

/** Remembers per-sound volume so quiet sounds don't need re-adjusting each night. */
object Prefs {
    private fun volumeKey(soundId: String) = floatPreferencesKey("vol_$soundId")

    suspend fun savedVolume(context: Context, sound: Sound): Float =
        context.dataStore.data.first()[volumeKey(sound.id)] ?: sound.defaultVolume

    suspend fun saveVolume(context: Context, soundId: String, volume: Float) {
        context.dataStore.edit { it[volumeKey(soundId)] = volume }
    }
}
