package com.naviveylin.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.naviveylin.core.VehicleAnchorPosition

/**
 * Bottom-center street-name pill for the phone map in free driving (spec:
 * current-road-info — current road shown when no route is active). Dark pill
 * with the road's "ref name" text, styled like the Auto surface's
 * [com.naviveylin.auto.StreetNameLabel]. Blank text draws nothing.
 */
@Composable
fun StreetNamePill(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1
        )
    }
}

/**
 * Free-driving street label with anchor-row placement (spec:
 * current-road-info — "Street label moves to the top for bottom-row
 * anchors"): bottom-center pill by default (top row and middle row);
 * top-center when the active follow anchor preset is in the bottom row, so
 * the pill never covers the vehicle marker nor lies in the travel corridor
 * (parity with the Android Auto street-name label — same rows, same
 * behavior). Blank text draws nothing.
 *
 * [onPillInset] reports the measured pill height (padding included); the
 * caller decides whether a bottom inset applies — the pill only needs to
 * reserve map space below the follow anchor when it sits at the bottom
 * (spec: smooth-follow — the overlay insets keep the anchor inside the
 * visible part of the map).
 */
@Composable
fun FreeDrivingStreetPill(
    roadText: String?,
    anchor: VehicleAnchorPosition,
    onPillInset: (Int) -> Unit = {}
) {
    if (roadText.isNullOrBlank()) return
    // Row rule (design D6, street-name-host-views): the whole bottom row
    // (`fy == 0.9`) parks the label at the top; top row and middle row keep
    // the bottom placement.
    val atTop = anchor.fy == 0.9
    // Top placement must clear the status bar / front camera on edge-to-edge
    // phones (spec: current-road-info — "Street label clears the status
    // bar/camera area"): status-bar insets are the outer modifier, the 16.dp
    // visual margin sits inside them (mirror of the bottom placement's
    // margin). Bottom placement byte-identical.
    val safeTop = if (atTop) Modifier.statusBarsPadding() else Modifier
    Box(Modifier.fillMaxSize()) {
        StreetNamePill(
            text = roadText,
            modifier = safeTop
                .testTag("free-driving-street-pill")
                .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(top = if (atTop) 16.dp else 0.dp, bottom = if (atTop) 0.dp else 16.dp)
                .onSizeChanged { onPillInset(it.height) }
        )
    }
}
