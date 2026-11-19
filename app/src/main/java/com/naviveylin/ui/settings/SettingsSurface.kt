package com.naviveylin.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naviveylin.R
import com.naviveylin.data.AppSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Test handle on the surface's root: the band a host composes it into. */
internal const val SETTINGS_SURFACE_TAG = "settings-surface"

/** Test handle on the root page: the six topics, and nothing below them. */
internal const val SETTINGS_ROOT_TAG = "settings-root"

/** Test handle on a topic page: the one column of sections and rows. */
internal const val SETTINGS_TOPIC_TAG = "settings-topic"

/** Test handle prefix on a setting row, so a case points at one row rather than at its label. */
internal const val SETTINGS_ROW_TAG_PREFIX = "settings-row-"

/** Test handle prefix on a row's slider, so a case reads the range its row offers. */
internal const val SETTINGS_SLIDER_TAG_PREFIX = "settings-slider-"

/** Test handle on one row's option page: the values a `SELECT` row offers. */
internal const val SETTINGS_OPTIONS_TAG = "settings-options"

/** Test handle prefix on one option, so a case points at the value it marks. */
internal const val SETTINGS_CHOICE_TAG_PREFIX = "settings-choice-"

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
        onOpenOptions = { topic, rowKey -> controller.openOptions(topic, rowKey) },
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
    onOpenOptions: (SettingsTopic, String) -> Unit,
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
            SettingsHeader(title = headerTitle(state, root), onBack = onBack)
            HorizontalDivider()
            when (state) {
                SettingsSurfaceState.Closed -> Unit
                SettingsSurfaceState.Root -> SettingsRootPage(root = root, onOpenTopic = onOpenTopic)
                is SettingsSurfaceState.Topic -> SettingsTopicPage(
                    rows = root.firstOrNull { it.topic == state.topic }?.rows.orEmpty(),
                    settings = settings,
                    onWrite = onWrite,
                    onOpenOptions = { row -> onOpenOptions(state.topic, row.key) },
                    onOpenMapManager = onOpenMapManager,
                    onOpenAbout = onOpenAbout
                )

                is SettingsSurfaceState.Options -> SettingsOptionsPage(
                    row = root.flatMap { it.rows }.firstOrNull { it.key == state.rowKey },
                    settings = settings,
                    onWrite = onWrite,
                    onReturn = onBack
                )
            }
        }
    }
}

/**
 * The header's title: the topic's own, a row's own on its option page, and the surface's name on the
 * root (spec: `settings-surface` — depth stops where the user can still see where they are).
 */
@Composable
private fun headerTitle(state: SettingsSurfaceState, root: List<SettingsTopicTable>): String =
    when (state) {
        is SettingsSurfaceState.Topic -> stringResource(state.topic.titleRes)
        is SettingsSurfaceState.Options -> root.flatMap { it.rows }
            .firstOrNull { it.key == state.rowKey }
            ?.let { stringResource(it.labelRes) }
            ?: stringResource(R.string.settings_title)

        else -> stringResource(R.string.settings_title)
    }

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
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
            text = title,
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
    onOpenOptions: (SettingSpec) -> Unit,
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
                    onOpenOptions = onOpenOptions,
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
    onOpenOptions: (SettingSpec) -> Unit,
    onOpenMapManager: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val value = row.read(settings)
    val linkTarget = (row.payload as? SettingControlPayload.Open)?.target
    val slider = row.payload as? SettingControlPayload.Slider
    val choices = when (val payload = row.payload) {
        is SettingControlPayload.Choice -> payload.choices
        is SettingControlPayload.Select -> payload.choices
        else -> emptyList()
    }
    // A slider's read is the bare number, so the value and both endpoint labels below come from the
    // one resource the row's payload names. A choice-bearing row's read is the persisted key, so the
    // row shows the key's own text rather than a key a user would never read.
    val shownValue = slider?.let { payload -> formatValue(payload, value) }
        ?: choices.firstOrNull { it.key == value }?.let { choice -> choiceText(choice) }
        ?: value
    val rowModifier = Modifier
        .fillMaxWidth()
        .testTag(SETTINGS_ROW_TAG_PREFIX + row.key)
        .let { modifier ->
            when {
                linkTarget != null ->
                    modifier.clickable { open(linkTarget, onOpenMapManager, onOpenAbout) }

                // A SELECT row has more values than fit inline, so the row is the way onto the page
                // that lists them (design D5; spec: `settings-surface` — "A finite value set is
                // presented as options, never only by cycling").
                row.control == SettingControl.SELECT -> modifier.clickable { onOpenOptions(row) }
                else -> modifier
            }
        }
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

                SettingControl.LINK -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )

                SettingControl.SELECT -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = shownValue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                }

                SettingControl.CHOICE, SettingControl.SLIDER -> Text(
                    text = shownValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // The editors a value with more than two options needs (design D5): the inline selection list
        // for a CHOICE row, and the bounded slider for a SLIDER row — a slider that only showed its
        // value would be a range no user can reach.
        if (row.control == SettingControl.CHOICE) {
            Column(Modifier.selectableGroup()) {
                (row.payload as? SettingControlPayload.Choice)?.choices.orEmpty().forEach { choice ->
                    ChoiceRow(
                        row = row,
                        choice = choice,
                        selected = value == choice.key,
                        onWrite = onWrite,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (slider != null) {
            Slider(
                value = value.toFloatOrNull() ?: slider.min.toFloat(),
                onValueChange = { current -> onWrite(row, current.roundToInt().toString()) },
                valueRange = slider.min.toFloat()..slider.max.toFloat(),
                steps = (slider.max - slider.min) / slider.step - 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SETTINGS_SLIDER_TAG_PREFIX + row.key)
            )
            // Both endpoints, in the row's own unit: the range a user is choosing inside.
            Row(Modifier.fillMaxWidth()) {
                EndpointLabel(payload = slider, value = slider.min)
                Spacer(Modifier.weight(1f))
                EndpointLabel(payload = slider, value = slider.max)
            }
        }
    }
}

/** One endpoint of a slider's range, rendered by the row's own value resource. */
@Composable
private fun EndpointLabel(payload: SettingControlPayload.Slider, value: Int) {
    Text(
        text = stringResource(payload.valueFormatRes, value),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** A slider row's value, formatted with the resource its payload names. */
@Composable
private fun formatValue(payload: SettingControlPayload.Slider, value: String): String =
    stringResource(payload.valueFormatRes, value.toIntOrNull() ?: payload.min)

/**
 * One option of a `CHOICE` or `SELECT` row: the value's text, the current one marked and selected
 * through the accessibility tree, so "the current value is marked" is a property a case reads
 * (spec: `settings-surface` — "A finite value set is presented as options, never only by cycling").
 */
@Composable
private fun ChoiceRow(
    row: SettingSpec,
    choice: SettingChoice,
    selected: Boolean,
    onWrite: (SettingSpec, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .testTag(SETTINGS_CHOICE_TAG_PREFIX + row.key + "-" + choice.key)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onWrite(row, choice.key) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = { onWrite(row, choice.key) })
        Text(choiceText(choice), style = MaterialTheme.typography.bodyMedium)
    }
}

/** One option's text: its own resource, or the literal a values-own-label row carries. */
@Composable
private fun choiceText(choice: SettingChoice): String =
    choice.labelRes?.let { stringResource(it) } ?: choice.label.orEmpty()

/**
 * One row's option page (design D5; spec: `settings-surface` — "A finite value set is presented as
 * options, never only by cycling"): a `SELECT` row's values, the current one marked, laid out in the
 * columns its payload names — a list for one, the vehicle anchors' 5×3 grid for five.
 *
 * Choosing a value writes it and returns to the topic the row lives in, so the page never becomes a
 * place the surface can be stranded in; a row the registry no longer declares renders nothing rather
 * than a page of stale values.
 */
@Composable
private fun SettingsOptionsPage(
    row: SettingSpec?,
    settings: AppSettings,
    onWrite: (SettingSpec, String) -> Unit,
    onReturn: () -> Unit
) {
    if (row == null) return
    val payload = row.payload as? SettingControlPayload.Select ?: return
    val columns = payload.columns.coerceAtLeast(1)
    val current = row.read(settings)
    val choose: (SettingChoice) -> Unit = { choice ->
        onWrite(row, choice.key)
        onReturn()
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(SETTINGS_OPTIONS_TAG)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        payload.choices.chunked(columns).forEach { cells ->
            Row(Modifier.fillMaxWidth()) {
                cells.forEach { choice ->
                    val marked = current == choice.key
                    if (columns == 1) {
                        ChoiceRow(
                            row = row,
                            choice = choice,
                            selected = marked,
                            onWrite = { spec, key -> onWrite(spec, key); onReturn() },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        OptionCell(
                            choice = choice,
                            selected = marked,
                            onClick = { choose(choice) },
                            modifier = Modifier
                                .weight(1f)
                                // The same handle a list option carries, so a case points at the cell
                                // that is marked rather than at the grid's position in it.
                                .testTag(SETTINGS_CHOICE_TAG_PREFIX + row.key + "-" + choice.key)
                        )
                    }
                }
                // A short last row keeps its cells the width of a full row's.
                repeat(columns - cells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One cell of a grid option page: the value's text, bordered when it is the current one. */
@Composable
private fun OptionCell(
    choice: SettingChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(2.dp)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(2.dp)
    ) {
        Text(
            text = choiceText(choice),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
