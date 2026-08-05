package com.drift.tv.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a bundled asset image off the main thread. Swap for Coil if art moves to a CDN. */
@Composable
fun AssetImage(path: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it).asImageBitmap() }
            }.getOrNull()
        }
    }.value
    if (bitmap != null) {
        Image(bitmap, contentDescription, modifier, contentScale = ContentScale.Crop)
    }
}
