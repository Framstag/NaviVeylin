package com.naviveylin.ui.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.R
import com.naviveylin.core.distanceUsesKilometers
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.formatDurationText

/**
 * The route-ready affordance of the session's hidden anchor (spec:
 * `route-planning-session` — anchors: with the overlay hidden only this remains, and the
 * whole map stays free for analysis).
 *
 * Tapping it brings the overlay back. When the route is not calculated yet, only the
 * label is shown — the affordance still reports that a session is running.
 */
@Composable
fun RouteReadyPill(
    routeEntry: RouteEntry?,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onExpand)
            .testTag("RouteReadyPill"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.route),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (routeEntry != null) {
                Text(
                    text = stringResource(
                        if (distanceUsesKilometers(routeEntry.distance)) {
                            R.string.distance_unit_km
                        } else {
                            R.string.distance_unit_m
                        },
                        formatDistanceNumber(routeEntry.distance)
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatDurationText(routeEntry.duration),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
