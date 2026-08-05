package com.drift.tv.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Sound(
    val id: String,
    val title: String,
    val category: String,
    /** asset-relative path, e.g. "sounds/ocean_waves.ogg" */
    val audio: String,
    /** asset-relative path, e.g. "images/ocean_waves.jpg" */
    val image: String,
    val defaultVolume: Float = 0.8f,
) {
    val audioUri: String get() = "asset:///$audio"
}

@Serializable
data class SoundManifest(val version: Int, val sounds: List<Sound>)

object SoundCatalog {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cached: List<Sound>? = null

    fun load(context: Context): List<Sound> = cached ?: synchronized(this) {
        cached ?: context.assets.open("manifest.json")
            .bufferedReader()
            .use { json.decodeFromString<SoundManifest>(it.readText()).sounds }
            .also { cached = it }
    }

    fun byId(context: Context, id: String): Sound? = load(context).firstOrNull { it.id == id }
}
