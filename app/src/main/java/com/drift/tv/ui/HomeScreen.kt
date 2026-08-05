package com.drift.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.drift.tv.data.Sound
import com.drift.tv.ui.theme.AccentMagenta
import com.drift.tv.ui.theme.AccentViolet
import com.drift.tv.ui.theme.MoonWhite

private const val ALL = "All"
private val TileShape = RoundedCornerShape(22.dp)

@Composable
fun HomeScreen(
    sounds: List<Sound>,
    onSelect: (Sound) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(ALL) }
    val categories = remember(sounds) { listOf(ALL) + sounds.map { it.category }.distinct() }
    val visible = if (selectedCategory == ALL) sounds else sounds.filter { it.category == selectedCategory }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Text(
            "Drift",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            "Pick a sound. Long-press the moon while playing to turn the lights out.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // Capsule filter row — borrowed from the reference's day-picker pills.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            modifier = Modifier.focusGroup()
        ) {
            items(categories) { category ->
                CategoryChip(
                    label = category,
                    selected = category == selectedCategory,
                    onClick = { selectedCategory = category },
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.focusGroup()
        ) {
            items(visible, key = { it.id }) { sound ->
                SoundTile(sound = sound, onClick = { onSelect(sound) })
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AccentViolet else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = AccentViolet,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, MoonWhite), shape = RoundedCornerShape(50))
        ),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun SoundTile(sound: Sound, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tileScale")

    Surface(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(16f / 9f)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = "${sound.title}, ${sound.category} sound" },
        shape = ClickableSurfaceDefaults.shape(TileShape),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                BorderStroke(3.dp, Brush.linearGradient(listOf(AccentViolet, AccentMagenta))),
                shape = TileShape
            )
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            AssetImage(sound.image, null, Modifier.fillMaxSize().clip(TileShape))
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0E0A1C)))
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(sound.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        sound.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
