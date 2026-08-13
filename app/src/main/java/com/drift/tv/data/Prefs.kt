package com.drift.tv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "drift_prefs")

/**
 * Remembers per-sound volume so quiet sounds don't need re-adjusting each night,
 * plus the last mix and sleep timer — this is a every-night app, and rebuilding
 * the same three-layer mix from scratch each time is the main recurring chore.
 */
object Prefs {
    private fun volumeKey(soundId: String) = floatPreferencesKey("vol_$soundId")
    private val MIX = stringPreferencesKey("last_mix")
    private val TIMER = intPreferencesKey("last_timer_minutes")
    private val PAWNS_CONSENT_ASKED = booleanPreferencesKey("pawns_consent_asked")

    suspend fun savedVolume(context: Context, sound: Sound): Float =
        context.dataStore.data.first()[volumeKey(sound.id)] ?: sound.defaultVolume

    suspend fun saveVolume(context: Context, soundId: String, volume: Float) {
        context.dataStore.edit { it[volumeKey(soundId)] = volume }
    }

    /** Sound ids of the last mix that was playing, primary layer first. */
    suspend fun savedMix(context: Context): List<String> =
        context.dataStore.data.first()[MIX]
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    suspend fun saveMix(context: Context, soundIds: List<String>) {
        context.dataStore.edit { it[MIX] = soundIds.joinToString(",") }
    }

    /** Last sleep-timer length in minutes, or null if the user turned it off. */
    suspend fun savedTimerMinutes(context: Context): Int? =
        context.dataStore.data.first()[TIMER]?.takeIf { it > 0 }

    suspend fun saveTimerMinutes(context: Context, minutes: Int?) {
        context.dataStore.edit { it[TIMER] = minutes ?: 0 }
    }

    /**
     * Whether the Pawns consent prompt has been shown at least once.
     *
     * The SDK only records a binary "consent given", which can't distinguish
     * "never asked" from "asked and declined" — so auto-prompting on app open
     * off `isConsentGiven()` alone re-asks on every single launch after a
     * decline. This flag makes the prompt ask-once; opting in later is still
     * available from the sharing screen's START button.
     */
    suspend fun pawnsConsentAsked(context: Context): Boolean =
        context.dataStore.data.first()[PAWNS_CONSENT_ASKED] ?: false

    suspend fun setPawnsConsentAsked(context: Context) {
        context.dataStore.edit { it[PAWNS_CONSENT_ASKED] = true }
    }
}
