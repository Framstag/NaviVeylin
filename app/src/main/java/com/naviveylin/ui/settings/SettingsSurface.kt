package com.naviveylin.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naviveylin.R
import com.naviveylin.data.AppSettings
import kotlinx.coroutines.launch

/** Test handle on the surface's root: the band a host composes it into. */
internal const val SETTINGS_SURFACE_TAG = "settings-surface"

/** Test handle on the root page: the six topics, and nothing below them. */
internal const val SETTINGS_ROOT_TAG = "settings-root"

/** Test handle on a topic page: the one column of sections and rows. */
internal const val SETTINGS_TOPIC_TAG = "settings-topic"

/** Test handle prefix on a setting row, so a case points at one row rather than at its label. */
internal const val SETTINGS_ROW_TAG_PREFIX = "settings-row-"

/**
 * The settings surface, hosted on whatever page is showing (design D2, D3; spec: `settings-surface`).
 *
 * The host is deliberately thin: the surface's position lives in [SettingsController] and its rows in
 * [SettingsRegistry], so this composable only reads the two flows a host already has and hands a write
 * back to the one writer. Both hosts of the surface — the map screen's modal band and the start screen
 * — compose this rather than a copy of it.
 *
 * @param root the tables the surface renders; [SettingsRegistry.root] in the app, a fixture table in a
 *   case that asserts what a page shows before the rows that carry the shapes land.
 */
@Composable
fun SettingsSurfaceHost(
    controller: SettingsController,
    onOpenMapManager: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    root: List<SettingsTopicTable> = SettingsRegistry.root
) {
    val state by controller.surfaceState.collectAsState()
    val settings by controller.settings.collectAsState()
    val scope = rememberCoroutineScope()
    SettingsSurface(
        state = state,
        settings = settings,
        onOpenTopic = { controller.openTopic(it) },
        onBack = { controller.back() },
        onWrite = { row, value ->
            val write = row.write
            if (write != null) {
                scope.launch { controller.update { current -> write(current, value) } }
            }
        },
        onOpenMapManager = onOpenMapManager,
        onOpenAbout = onOpenAbout,
        modifier = modifier,
        root = root
    )
}

/**
 * The surface itself: a full-height [Surface] over whatever the host composed beneath it, a header
 * that goes back one level, and one page — the root or a topic page.
 *
 * It is not a navigation destination (design D3): a host composes it inside its own overlay band, so
 * the map's renderer, follow loop and route session are never re-initialised by opening it.
 */
@Composable
fun SettingsSurface(
    state: SettingsSurfaceState,
    settings: AppSettings,
    onOpenTopic: (SettingsTopic) -> Unit,
    onBack: () -> Unit,
    onWrite: (SettingSpec, String) -> Unit,
    onOpenMapManager: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    root: List<SettingsTopicTable> = SettingsRegistry.root
) {
    if (state == SettingsSurfaceState.Closed) return
    // Back leaves one level and never the app: a topic page returns to the root, the root closes the
    // surface, and the map underneath keeps its own back behaviour
    // (spec: `settings-surface`; spec: `back-gesture`).
    BackHandler(enabled = true) { onBack() }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(SETTINGS_SURFACE_TAG),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SettingsHeader(state = state, onBack = onBack)
            HorizontalDivider()
            when (state) {
                SettingsSurfaceState.Closed -> Unit
                SettingsSurfaceState.Root -> SettingsRootPage(root = root, onOpenTopic = onOpenTopic)
                is SettingsSurfaceState.Topic -> SettingsTopicPage(
                    rows = root.firstOrNull { it.topic == state.topic }?.rows.orEmpty(),
                    settings = settings,
                    onWrite = onWrite,
                    onOpenMapManager = onOpenMapManager,
                    onOpenAbout = onOpenAbout
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(state: SettingsSurfaceState, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back)
            )
        }
        Text(
            text = when (state) {
                is SettingsSurfaceState.Topic -> stringResource(state.topic.titleRes)
                else -> stringResource(R.string.settings_title)
            },
            style = MaterialTheme.typography.titleLarge
        )
    }
}

/**
 * The root page: the six topics, each a row that opens its page and nothing else
 * (spec: `settings-taxonomy` — "The settings root holds six topics and no deeper level").
 */
@Composable
private fun SettingsRootPage(root: List<SettingsTopicTable>, onOpenTopic: (SettingsTopic) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(SETTINGS_ROOT_TAG)
    ) {
        root.forEach { table ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTopic(table.topic) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(table.topic.titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            HorizontalDivider()
        }
    }
}

/**
 * One topic page: a single scrolling column, its rows grouped under the section titles that own them
 * (spec: `settings-surface` — "A topic page groups its rows under section titles").
 *
 * A row whose `enabled` is false does not apply to the current document, and a section left with no
 * row is omitted with it rather than shown empty or greyed.
 */
@Composable
private fun SettingsTopicPage(
    rows: List<SettingSpec>,
    settings: AppSettings,
    onWrite: (SettingSpec, String) -> Unit,
    onOpenMapManager: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val visible = rows.filter { it.enabled(settings) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(SETTINGS_TOPIC_TAG)
    ) {
        visible.groupBy { it.section }.forEach { (section, sectionRows) ->
            Text(
                text = stringResource(section.titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            )
            sectionRows.forEach { row ->
                SettingRow(
                    row = row,
                    settings = settings,
                    onWrite = onWrite,
                    onOpenMapManager = onOpenMapManager,
                    onOpenAbout = onOpenAbout
                )
            }
        }
    }
}

/**
 * One row of a topic page, drawn from its [SettingSpec] and nothing else (design D1, D5).
 *
 * The scope mark is the row's own: a phone-only or car-only row carries a leading icon whose content
 * description names the surface, a shared row carries none (spec: `settings-taxonomy` — "A
 * surface-specific row is marked, a shared row is not").
 */
@Composable
private fun SettingRow(
    row: SettingSpec,
    settings: AppSettings,
    onWrite: (SettingSpec, String) -> Unit,
    onOpenMapManager: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val value = row.read(settings)
    val linkTarget = (row.payload as? SettingControlPayload.Link)?.target
    val rowModifier = Modifier
        .fillMaxWidth()
        .testTag(SETTINGS_ROW_TAG_PREFIX + row.key)
        .let { if (linkTarget != null) it.clickable { open(linkTarget, onOpenMapManager, onOpenAbout) } else it }
        .padding(horizontal = 16.dp, vertical = 8.dp)

    Column(rowModifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            row.surfaceContentDescriptionRes?.let { description ->
                Icon(
                    imageVector = if (row.scope == SettingScope.CAR) {
                        Icons.Default.DirectionsCar
                    } else {
                        Icons.Default.PhoneAndroid
                    },
                    contentDescription = stringResource(description),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(row.labelRes),
                    style = MaterialTheme.typography.bodyLarge
                )
                row.helpShortRes?.let { help ->
                    Text(
                        text = stringResource(help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when (row.control) {
                SettingControl.SWITCH -> Switch(
                    checked = value == "on",
                    onCheckedChange = { onWrite(row, if (it) "on" else "off") }
                )

                SettingControl.LINK, SettingControl.SELECT -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )

                SettingControl.CHOICE, SettingControl.SLIDER -> Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // The editors a value with more than two options needs: the inline selection list for a
        // CHOICE row and the bounded slider for a SLIDER row. They land with the first rows of those
        // kinds (tasks.md groups 3-5), because a control this group does not render would be a
        // surface no case can reach; until then the row shows its current value.
        if (row.control == SettingControl.CHOICE) {
            (row.payload as? SettingControlPayload.Choice)?.choices.orEmpty().forEach { choice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWrite(row, choice.key) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = value == choice.key, onClick = { onWrite(row, choice.key) })
                    Text(stringResource(choice.labelRes), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** The destination a link row leaves the surface for, resolved from the row's own payload. */
private fun open(
    target: SettingsLinkTarget,
    onOpenMapManager: () -> Unit,
    onOpenAbout: () -> Unit
) {
    when (target) {
        SettingsLinkTarget.MAP_MANAGER -> onOpenMapManager()
        SettingsLinkTarget.ABOUT -> onOpenAbout()
    }
}
