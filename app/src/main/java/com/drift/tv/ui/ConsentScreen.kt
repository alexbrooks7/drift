package com.drift.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.drift.tv.ui.theme.AccentViolet
import com.drift.tv.ui.theme.MoonDim
import com.drift.tv.ui.theme.MoonWhite
import com.drift.tv.ui.theme.Panel

/** Same blue as the reference dialog's link text. */
private val LinkBlue = Color(0xFF6EA8FE)

/**
 * Priming screen for Bright SDK's "Web Indexing" bandwidth sharing, shown
 * over Home on first launch. Modeled on the layout in the reference
 * screenshot: centered card over a dimmed scrim, title, short body, policy
 * links, primary action with a secondary decline beneath it.
 *
 * Unlike Pawns, this is *not* where consent is actually captured — Bright's
 * API has no way to grant consent programmatically. "Okay" here dismisses
 * this screen and immediately opens Bright's own consent screen (restyled to
 * match Drift via `CustomConsentSettings`, see BrightManager), which is the
 * only place a "yes" can be recorded. "No thanks" still records a decline
 * directly, since opting out is a plain API call.
 *
 * Testing a bundled consent screen directly on a TV (during the earlier
 * Pawns integration) turned up three blockers a priming screen like this one
 * sidesteps for the common case: it needed ~13 D-pad presses to reach the
 * buttons, its buttons drew no focus indicator at all, and it was a
 * full-white scrolling page. Declining never has to touch Bright's screen at
 * all here; accepting still does, once, since that's unavoidable.
 *
 * On the copy: the body deliberately states what Bright Data actually
 * receives and costs, per their own disclosure requirements. Claiming "no
 * personal data is collected" would contradict it, and Bright puts
 * responsibility for how this feature is presented on the app owner, so an
 * inaccurate disclosure is the app owner's liability.
 */
@Composable
fun ConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    val acceptFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { acceptFocus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            // The dialog must swallow BACK itself. Focus lives inside this
            // subtree, so a handler on whatever screen is underneath is a
            // sibling and never sees the event — it fell through to the
            // Activity's default handler and closed the whole app.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onDismiss(); true
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(containerColor = Panel),
            modifier = Modifier.widthIn(max = 940.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 48.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Help Keep Drift Free",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Drift can use a portion of your device's network bandwidth for Web " +
                        "Indexing on behalf of Bright Data and its clients. This helps fund " +
                        "development and keep the app free. It runs in the background while " +
                        "your sounds play.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoonWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Bright Data receives your IP address and approximate location. This uses " +
                        "data and battery and can affect your internet speed, so skip it on a " +
                        "metered or restricted connection. You can turn it off at any time in " +
                        "Settings. Tapping Okay opens one more short screen from Bright Data to " +
                        "confirm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoonDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    "Learn more: bright-sdk.com/users",
                    style = MaterialTheme.typography.labelMedium,
                    color = LinkBlue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )

                Column(
                    Modifier
                        .padding(top = 28.dp)
                        .fillMaxWidth()
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Primary action: bright, filled, and holding focus on open.
                    Surface(
                        onClick = onAccept,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(acceptFocus),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = AccentViolet,
                            focusedContainerColor = AccentViolet,
                            contentColor = MoonWhite,
                            focusedContentColor = MoonWhite,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                BorderStroke(3.dp, MoonWhite),
                                shape = RoundedCornerShape(12.dp)
                            )
                        ),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), Alignment.Center) {
                            Text("Okay", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    // Secondary action: visually quieter than the primary, but
                    // kept legible and equally reachable — one press away, with
                    // the same unmistakable focus ring.
                    Surface(
                        onClick = onDecline,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            contentColor = MoonDim,
                            focusedContentColor = MoonWhite,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                BorderStroke(1.dp, MoonWhite.copy(alpha = 0.18f)),
                                shape = RoundedCornerShape(12.dp)
                            ),
                            focusedBorder = Border(
                                BorderStroke(3.dp, MoonWhite),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        ),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                            Text("No thanks", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
