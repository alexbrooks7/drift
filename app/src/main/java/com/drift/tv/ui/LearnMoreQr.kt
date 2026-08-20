package com.drift.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.drift.tv.R
import com.drift.tv.ui.theme.MoonDim

/**
 * The "learn more" QR code Bright SDK requires wherever Web Indexing is
 * offered or controlled.
 *
 * A QR rather than a tappable link because this is a TV app: Bright's rules
 * spell out separate requirements for mobile ("include a Learn more link")
 * and TV ("include a QR code"), and the instruction text below it is
 * prescribed wording, not ours to paraphrase.
 *
 * This replaced a plain `Text` reading "Learn more: bright-sdk.com/users",
 * which on a D-pad was unreachable — not focusable, not clickable, so the
 * required disclosure was effectively absent for anyone holding a remote.
 *
 * The code is a checked-in PNG (drawable-nodpi, so it's never density-scaled
 * and its module edges stay pixel-crisp) rather than a vector or a
 * runtime-generated bitmap: the URL is fixed, and antialiased or resampled
 * module edges are the usual reason a camera fails to read a code off a TV
 * across a room. Verified to decode to Bright's required URL exactly.
 */
@Composable
fun LearnMoreQr(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.bright_learn_more_qr),
            // Decorative here: the adjacent text carries the meaning, and a
            // screen reader announcing a QR image adds nothing actionable.
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                // White quiet zone around the code has to survive the dark
                // theme -- the PNG carries its own, and this keeps the
                // rounded corners from eating into it.
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .padding(4.dp),
        )
        Text(
            "Scan the QR Code to learn more about web indexing by Bright Data.",
            style = MaterialTheme.typography.bodySmall,
            color = MoonDim,
        )
    }
}
