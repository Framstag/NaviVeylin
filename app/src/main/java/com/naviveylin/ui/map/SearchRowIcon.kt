package com.naviveylin.ui.map

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.SEARCH_ROW_ICON_DP
import com.naviveylin.core.SearchRowIcons

/** Edge length of a result row's type icon, in dp. */
internal val SearchRowIconSize = SEARCH_ROW_ICON_DP.dp

/**
 * The type artwork a result row leads with, or `null` while it is unresolved and for a type
 * the stylesheet gives none (spec: `search-result-row-icon`).
 *
 * This is the *only* place a row may reach native artwork, and it does it from a
 * `LaunchedEffect` on the default dispatcher inside `SearchRowIcons.resolve`, never from
 * composition: the artwork is a native call on the database thread (`guidelines/Design.md`
 * §4). The first frame of a row therefore shows its text and marking without the icon, and
 * the icon appears when the shared cache answers.
 *
 * `peek` is what keeps that cheap: a type that was already resolved (the usual case — the
 * list shows several entries of a kind, and the cache survives a style until the next style
 * load) never starts a coroutine at all.
 */
@Composable
internal fun rememberRowTypeIcon(client: OSMScoutClient?, objectType: String?): Bitmap? {
    if (client == null || objectType.isNullOrEmpty()) return null

    val sizePx = with(LocalDensity.current) { SearchRowIconSize.roundToPx() }

    var icon by remember(objectType, sizePx) { mutableStateOf(SearchRowIcons.peek(objectType, sizePx)) }
    LaunchedEffect(client, objectType, sizePx) {
        if (icon == null) {
            icon = SearchRowIcons.resolve(typeIconSource(client), objectType, sizePx)
        }
    }
    return icon
}
