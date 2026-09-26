package com.naviveylin.ui.map

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naviveylin.R

/**
 * Advisory indication that a car session is live and navigation is presented on
 * the car screen (spec: `car-session-presence` — The phone is informed, not
 * disabled).
 *
 * Informational only: it is not a control, it disables nothing, and no map or
 * navigation action is gated by it. Rendered over the map in both browse and
 * driving modes; the label is a translatable resource (no hardcoded text).
 */
@Composable
fun CarSessionIndicator(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.car_session_active_indicator)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
