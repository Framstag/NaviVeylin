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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naviveylin.R
import com.naviveylin.data.AppSettings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Test handle on the surface's root: the band a host composes it into. */
internal const val SETTINGS_SURFACE_TAG = "settings-surface"

/** Test handle on the root page: the six topics, and nothing below them. */
internal const val SETTINGS_ROOT_TAG = "settings-root"

/** Test handle on a topic page: the one column of sections and rows. */
internal const val SETTINGS_TOPIC_TAG = "settings-topic"

/** Test handle prefix on a setting row, so a case points at one row rather than at its label. */
internal const val SETTINGS_ROW_TAG_PREFIX = "settings-row-"

/** Test handle prefix on the control a numeric row renders, so a case can drag its track. */
internal const val SETTINGS_SLIDER_TAG_PREFIX = "settings-slider-"

/** Test handle prefix on the button that opens a row's long help text. */
internal const val SETTINGS_HELP_TAG_PREFIX = "settings-help-"

/** Test handle on the sheet that carries a row's long help text. */
internal const val SETTINGS_LONG_HELP_TAG = "settings-long-help"

/** Test handle on the surface that carries a `SELECT` row's options. */
internal const val SETTINGS_SELECT_TAG = "settings-select"

/** Test handle prefix on one option of a `SELECT` row, so a case points at an option, not its text. */
internal const val SETTINGS_SELECT_OPTION_TAG_PREFIX = "settings-select-option-"

/** Test handle prefix on one option of a `CHOICE` row, so a case points at an option, not its text. */
internal const val SETTINGS_CHOICE_OPTION_TAG_PREFIX = "settings-choice-option-"

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
@OptIn(ExperimentalMaterial3Api::class)
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
    // A row's long help is text on a surface of its own (design D7): it shows what the short line
    // cannot and writes nothing, so dismissing it leaves the row's value as it was.
    var openHelp by remember { mutableStateOf<Int?>(null) }
    // A `SELECT` row's options are a surface of their own too, because a set of more than four values
    // is not an inline list (design D5).
    var openSelect by remember { mutableStateOf<SettingSpec?>(null) }
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
                    onOpenHelp = { openHelp = it },
                    onOpenSelect = { openSelect = it },
                    onOpenMapManager = onOpenMapManager,
                    onOpenAbout = onOpenAbout
                )
            }
        }
        openHelp?.let { help ->
            ModalBottomSheet(onDismissRequest = { openHelp = null }) {
                Text(
                    text = stringResource(help),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag(SETTINGS_LONG_HELP_TAG)
                )
                TextButton(
                    onClick = { openHelp = null },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.settings_help_close))
                }
            }
        }
        openSelect?.let { row ->
            val current = row.read(settings)
            val select = row.payload as? SettingControlPayload.Select
            ModalBottomSheet(onDismissRequest = { openSelect = null }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .testTag(SETTINGS_SELECT_TAG)
                ) {
                    val choices = select?.choices.orEmpty()
                    // An option with no key is a row's "inherits" entry: its text is a sentence rather
                    // than a preset's name, so it takes a full-width row above the grid (spec:
                    // `settings-surface` — "Returning to the phone value").
                    val inheriting = choices.filter { it.key.isEmpty() }
                    val values = choices.filter { it.key.isNotEmpty() }
                    inheriting.forEach { choice ->
                        SelectOptionRow(choice = choice, selected = current.isEmpty()) {
                            openSelect = null
                            onWrite(row, choice.key)
                        }
                    }
                    // The rest are laid out in the payload's columns: five reproduces the vehicle
                    // anchors' 5×3 picker, one is the list every other `SELECT` row shows (design D5).
                    values.chunked((select?.columns ?: 1).coerceAtLeast(1)).forEach { cells ->
                        Row(Modifier.fillMaxWidth()) {
                            cells.forEach { choice ->
                                SelectOptionCell(
                                    choice = choice,
                                    selected = choice.key == current,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    openSelect = null
                                    onWrite(row, choice.key)
                                }
                            }
                        }
                    }
                }
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
    onOpenHelp: (Int) -> Unit,
    onOpenSelect: (SettingSpec) -> Unit,
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
                    onOpenHelp = onOpenHelp,
                    onOpenSelect = onOpenSelect,
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
 *
 * A row that declares a long help text carries the button that opens it, and a slider is drawn below
 * its label row with both endpoints named (design D5, D7).
 *
 * The row itself is the target of its control: a switch toggles from anywhere on the row and a
 * `SELECT` row opens its options from anywhere on it, so neither is a small control the user has to
 * hit (design D5).
 */
@Composable
private fun SettingRow(
    row: SettingSpec,
    settings: AppSettings,
    onWrite: (SettingSpec, String) -> Unit,
    onOpenHelp: (Int) -> Unit,
    onOpenSelect: (SettingSpec) -> Unit,
    onOpenMapManager: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val value = row.read(settings)
    val linkTarget = (row.payload as? SettingControlPayload.Open)?.target
    val rowClick: (() -> Unit)? = when {
        linkTarget != null -> ({ open(linkTarget, onOpenMapManager, onOpenAbout) })
        row.control == SettingControl.SWITCH -> ({ onWrite(row, if (value == "on") "off" else "on") })
        row.control == SettingControl.SELECT -> ({ onOpenSelect(row) })
        else -> null
    }
    val rowModifier = Modifier
        .fillMaxWidth()
        .testTag(SETTINGS_ROW_TAG_PREFIX + row.key)
        .let { if (rowClick != null) it.clickable(onClick = rowClick) else it }
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
            // A row with a long explanation offers it here, before the control: the short line stays
            // under the label and the long text is one tap away (design D7).
            row.helpLongRes?.let { help ->
                IconButton(
                    onClick = { onOpenHelp(help) },
                    modifier = Modifier.testTag(SETTINGS_HELP_TAG_PREFIX + row.key)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.settings_help_open)
                    )
                }
            }
            when (row.control) {
                SettingControl.SWITCH -> Switch(
                    checked = value == "on",
                    onCheckedChange = { onWrite(row, if (it) "on" else "off") }
                )

                SettingControl.SELECT -> {
                    Text(
                        text = valueText(row, value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                }

                SettingControl.LINK -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )

                SettingControl.CHOICE, SettingControl.SLIDER -> Text(
                    text = valueText(row, value),
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
                        .selectable(
                            selected = value == choice.key,
                            role = Role.RadioButton,
                            onClick = { onWrite(row, choice.key) }
                        )
                        .testTag(SETTINGS_CHOICE_OPTION_TAG_PREFIX + choice.key),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = value == choice.key, onClick = null)
                    Text(choiceLabel(choice), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (row.control == SettingControl.SLIDER) {
            (row.payload as? SettingControlPayload.Slider)?.let { slider ->
                SliderControl(row = row, value = value, slider = slider, onWrite = onWrite)
            }
        }
    }
}

/**
 * An option's label: the literal the value carries (a vehicle anchor's own label), else its resource,
 * else its key (a map style's name is its stylesheet's file name — spec: `map-styles`).
 */
@Composable
private fun choiceLabel(choice: SettingChoice): String =
    choice.label ?: choice.labelRes?.let { stringResource(it) } ?: choice.key

/**
 * One `SELECT` option as a full-width row: the shape an option with a sentence for a label needs (a
 * car anchor's "uses the phone value" entry). The tag is the option's key, so a case points at one
 * option rather than at its text.
 */
@Composable
private fun SelectOptionRow(choice: SettingChoice, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + choice.key)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = choiceLabel(choice), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * One `SELECT` option as a cell of its row's grid: a radio mark above the preset's label, so fifteen
 * presets sit in the five columns and three rows the anchors' picker uses (design D5).
 */
@Composable
private fun SelectOptionCell(
    choice: SettingChoice,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + choice.key)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = choiceLabel(choice),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A bounded slider under its label row (design D5; spec: `settings-surface` — "The overspeed delta is
 * a bounded slider"): both endpoints are named, every permitted value is reachable, and a drag applies
 * the value it stopped on rather than a save action.
 */
@Composable
private fun SliderControl(
    row: SettingSpec,
    value: String,
    slider: SettingControlPayload.Slider,
    onWrite: (SettingSpec, String) -> Unit
) {
    Slider(
        value = (value.toIntOrNull() ?: slider.min).toFloat(),
        onValueChange = { dragged -> onWrite(row, dragged.roundToInt().toString()) },
        valueRange = slider.min.toFloat()..slider.max.toFloat(),
        steps = ((slider.max - slider.min) / slider.step - 1).coerceAtLeast(0),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SETTINGS_SLIDER_TAG_PREFIX + row.key)
    )
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_slider_endpoint, slider.min),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.settings_slider_endpoint, slider.max),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A row's value as the surface shows it: a number-shaped row formats its own value with the resource
 * it declares, so a unit stays a translated string rather than a literal of this composable
 * (guidelines/UI.md §10).
 */
@Composable
private fun valueText(row: SettingSpec, value: String): String {
    // A `SELECT` row whose stored value is empty is a car value that still inherits the phone's, and
    // the row says so rather than showing a blank line (spec: `settings-surface` — "An inheriting
    // anchor is shown as inheriting").
    if (row.control == SettingControl.SELECT && value.isEmpty()) {
        return stringResource(R.string.settings_anchor_inherit)
    }
    // A `CHOICE` row's stored value is the option's key, so the line names the option the way the
    // radio list below it does rather than showing the key the settings file holds (spec:
    // `settings-surface` — "A finite value set is presented as options, never only by cycling").
    if (row.control == SettingControl.CHOICE) {
        val chosen = (row.payload as? SettingControlPayload.Choice)
            ?.choices
            ?.firstOrNull { choice -> choice.key == value }
        if (chosen != null) return choiceLabel(chosen)
    }
    val format = row.valueFormatRes ?: return value
    val number = value.toIntOrNull() ?: return value
    return stringResource(format, number)
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
