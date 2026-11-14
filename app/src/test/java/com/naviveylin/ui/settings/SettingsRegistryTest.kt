package com.naviveylin.ui.settings

import com.naviveylin.R
import com.naviveylin.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The registry's own invariants (design D1; spec: `settings-taxonomy`).
 *
 * The tables are empty in the foundations group, so these cases assert two things at once: the rule
 * the rows will have to keep as they land (groups 3-6 add them one at a time), and — through the
 * fixture rows below — that the rule's check is not vacuous over a registry that has no rows yet.
 * A key that appears twice, a car row offered to the phone or a surface-specific row with no surface
 * description has to be a case failure rather than a divergence no page shows.
 */
class SettingsRegistryTest {

    /**
     * Every declared row is identified by one key, and that key is in one topic only (spec:
     * `settings-taxonomy` — "A setting appears once").
     */
    @Test
    fun everyKeyAppearsExactlyOnce() {
        assertEquals(
            "no key is declared twice in the registry",
            emptyList<String>(),
            duplicateKeys(SettingsRegistry.all)
        )
        assertEquals(
            "the registry holds every row of every topic table, and no other",
            SettingsRegistry.root.sumOf { it.rows.size },
            SettingsRegistry.all.size
        )
        assertEquals(
            "a topic's table is the list rowsOf returns for it",
            SettingsRegistry.root.flatMap { it.rows },
            SettingsRegistry.root.flatMap { SettingsRegistry.rowsOf(it.topic) }
        )
        // The check catches a duplicate rather than passing over it: an empty registry would
        // satisfy every assertion above without reading a single key.
        assertEquals(
            "two rows that share a key are reported",
            listOf("duplicate"),
            duplicateKeys(listOf(row("duplicate", SettingScope.SHARED), row("duplicate", SettingScope.CAR)))
        )
    }

    /**
     * A surface-specific row names its surface; a shared row names none (spec:
     * `settings-taxonomy` — "A surface-specific row is marked, a shared row is not").
     */
    @Test
    fun everySurfaceSpecificRowIsMarked() {
        assertEquals(
            "every surface-specific row carries a description and no shared row does",
            emptyList<String>(),
            scopeViolations(SettingsRegistry.all)
        )
        assertEquals(
            "a shared row names no surface",
            null,
            row("shared", SettingScope.SHARED).surfaceContentDescriptionRes
        )
        assertEquals(
            "a phone-only row names the phone",
            R.string.settings_scope_phone,
            row("phone", SettingScope.PHONE).surfaceContentDescriptionRes
        )
        assertEquals(
            "a car-only row names the car",
            R.string.settings_scope_car,
            row("car", SettingScope.CAR).surfaceContentDescriptionRes
        )
        assertEquals(
            "a car row without a surface description is reported",
            listOf("unmarked-car"),
            scopeViolations(listOf(row("unmarked-car", SettingScope.CAR, surfaceDescription = null)))
        )
        assertEquals(
            "a shared row marked with a surface is reported",
            listOf("marked-shared"),
            scopeViolations(
                listOf(row("marked-shared", SettingScope.SHARED, surfaceDescription = R.string.settings_scope_phone))
            )
        )
    }

    /**
     * One surface's projection holds its own rows and the shared ones, never the other surface's
     * (spec: `settings-taxonomy` — "A phone-only row is absent from the car").
     */
    @Test
    fun theCarOffersOnlySharedAndCarRows() {
        val car = SettingsRegistry.rowsFor(SettingScope.CAR)
        assertEquals(
            "the car is offered no phone-only row",
            emptyList<SettingSpec>(),
            car.filter { it.scope == SettingScope.PHONE }
        )
        assertEquals(
            "every row the car is offered is shared or car-only",
            true,
            car.all { it.scope == SettingScope.SHARED || it.scope == SettingScope.CAR }
        )
        assertEquals(
            "the phone is offered no car-only row",
            emptyList<SettingSpec>(),
            SettingsRegistry.rowsFor(SettingScope.PHONE).filter { it.scope == SettingScope.CAR }
        )
        // The projection filters, rather than returning every row or none of them.
        val fixture = listOf(
            row("shared", SettingScope.SHARED),
            row("phone", SettingScope.PHONE),
            row("car", SettingScope.CAR)
        )
        assertEquals(
            "the car's projection is the shared rows plus its own, in declaration order",
            listOf("shared", "car"),
            SettingsRegistry.rowsFor(SettingScope.CAR, fixture).map { it.key }
        )
        assertEquals(
            "the phone's projection is the shared rows plus its own, in declaration order",
            listOf("shared", "phone"),
            SettingsRegistry.rowsFor(SettingScope.PHONE, fixture).map { it.key }
        )
    }

    /**
     * The root is the six topics, in the order the spec names them, with no seventh level (spec:
     * `settings-taxonomy` — "The settings root holds six topics and no deeper level").
     */
    @Test
    fun theRootHoldsSixTopicsInOrder() {
        assertEquals(
            "the root is Karte, Navigation & Route, Hinweise & Sprache, Karten & Daten, Erweitert, Über NaviVeylin",
            listOf(
                SettingsTopic.MAP,
                SettingsTopic.NAVIGATION_AND_ROUTE,
                SettingsTopic.HINTS_AND_LANGUAGE,
                SettingsTopic.MAPS_AND_DATA,
                SettingsTopic.ADVANCED,
                SettingsTopic.ABOUT
            ),
            SettingsRegistry.root.map { it.topic }
        )
        assertEquals(
            "the root shows every declared topic exactly once",
            SettingsTopic.values().toList(),
            SettingsRegistry.root.map { it.topic }
        )
    }

    // ---- the table harness: the rule, and the fixtures that show it is not vacuous ----

    /**
     * One row, with the surface description its scope owes unless a case overrides it. The label and
     * section resources are the platform's own: nothing renders a fixture row, and the app's real
     * labels land with the rows (tasks.md groups 3-6).
     */
    private fun row(
        key: String,
        scope: SettingScope,
        topic: SettingsTopic = SettingsTopic.MAP,
        surfaceDescription: Int? = when (scope) {
            SettingScope.SHARED -> null
            SettingScope.PHONE -> R.string.settings_scope_phone
            SettingScope.CAR -> R.string.settings_scope_car
        }
    ): SettingSpec = SettingSpec(
        key = key,
        topic = topic,
        section = SettingsSection(topic, titleRes = android.R.string.ok),
        scope = scope,
        labelRes = android.R.string.ok,
        control = SettingControl.SWITCH,
        surfaceContentDescriptionRes = surfaceDescription,
        read = { settings: AppSettings -> if (settings.keepScreenOn) "on" else "off" },
        write = { settings, value -> settings.copy(keepScreenOn = value == "on") }
    )

    /** The keys that appear in more than one row, in one order so a failure names them. */
    private fun duplicateKeys(rows: List<SettingSpec>): List<String> =
        rows.groupBy { it.key }
            .filterValues { it.size > 1 }
            .keys
            .sorted()

    /** The keys whose surface field disagrees with their scope, in declaration order. */
    private fun scopeViolations(rows: List<SettingSpec>): List<String> =
        rows.filter { row ->
            when (row.scope) {
                SettingScope.SHARED -> row.surfaceContentDescriptionRes != null
                SettingScope.PHONE -> row.surfaceContentDescriptionRes == null
                SettingScope.CAR -> row.surfaceContentDescriptionRes != R.string.settings_scope_car
            }
        }.map { it.key }
}
