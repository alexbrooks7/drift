package com.drift.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.drift.tv.sharing.PawnsManager
import com.drift.tv.ui.theme.AccentMagenta
import com.drift.tv.ui.theme.AccentViolet
import com.drift.tv.ui.theme.MoonDim
import com.drift.tv.ui.theme.MoonWhite
import com.drift.tv.ui.theme.Panel
import com.pawns.sdk.common.dto.ServiceState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-app settings, reached from Home's gear. Currently holds only the
 * internet-sharing control, so it's shown only when [PawnsManager.available].
 *
 * Status is read from the SDK's own service-state flow rather than from the
 * consent flag — consent being granted doesn't prove the service is actually
 * running, and showing "Active" while it's erroring would be a lie the user
 * can't see through.
 */
@Composable
fun SettingsScreen(
    onOpenConsent: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val stateFlow = remember { PawnsManager.serviceState() ?: MutableStateFlow(ServiceState.Off) }
    val serviceState by stateFlow.collectAsState(ServiceState.Off)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val active = serviceState is ServiceState.On || serviceState is ServiceState.Launched.Running
    val status = when (val s = serviceState) {
        is ServiceState.Off -> "Off — nothing is being shared"
        is ServiceState.On -> "Active — this device is sharing its connection"
        is ServiceState.Launched.Running -> "Active — this device is sharing its connection"
        is ServiceState.Launched.LowBattery -> "Paused — battery is low"
        is ServiceState.Launched.Error -> "Not sharing — ${s.error}"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 28.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onBack(); true
                } else false
            }
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
        )

        Surface(
            modifier = Modifier
                .padding(top = 24.dp)
                .widthIn(max = 760.dp),
            shape = RoundedCornerShape(18.dp),
            colors = SurfaceDefaults.colors(containerColor = Panel),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Internet sharing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MoonDim,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                SettingsButton(
                    label = if (active) "Turn off" else "Turn on",
                    onClick = {
                        when {
                            active -> PawnsManager.stopSharing(context)
                            // Already consented before — no need to re-ask.
                            PawnsManager.hasConsent() -> PawnsManager.startSharing(context)
                            // Never consented (or previously declined): the
                            // disclosure has to come before sharing starts.
                            else -> onOpenConsent()
                        }
                    },
                    modifier = Modifier.focusRequester(focus),
                )
            }
        }

        SettingsButton(
            label = "Review what this shares",
            onClick = onOpenConsent,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * Border-based focus indicator, matching the rest of Drift. tv-material3's
 * `Button` signals focus with a fill-color change only, which against this
 * dark theme is nearly invisible from across a room.
 */
@Composable
private fun SettingsButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            focusedContainerColor = MaterialTheme.colorScheme.background,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(2.dp, MoonWhite.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(50)
            ),
            focusedBorder = Border(
                BorderStroke(3.dp, Brush.linearGradient(listOf(AccentViolet, AccentMagenta))),
                shape = RoundedCornerShape(50)
            ),
        ),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    }
}
