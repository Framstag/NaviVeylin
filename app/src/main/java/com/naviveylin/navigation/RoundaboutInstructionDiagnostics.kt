package com.naviveylin.navigation

import com.framstag.libosmscout.client.RouteStep

/**
 * Coordinate-free diagnostics for the roundabout fields of a route step (spec: `osmscout-jni` — A
 * route instruction carries the roundabout exit ordinal and its circulation direction; spec:
 * `auto-diagnostics` — no coordinates in diagnostics).
 *
 * The line exists because the record's own fields are filled in native code and no JVM test executes the
 * bridge: this is what a device run reads to see that the route really produced an ordinal and a direction,
 * instead of inferring them from a screenshot. Identity only — kind, ordinal, direction, and the hint row's
 * own ordinal — never a coordinate (enforced by the `checkNoCoordinatesInLogs` build gate).
 *
 * The step this line is given is the **announced** one, so `hintExit` is the app-derived following
 * manoeuvre (`core/nextManoeuvreAfter`, change `fix-following-manoeuvre-hint`), while `kind`, `exit` and
 * `cw` stay the native record's own values. Read the two apart: `exit` answers "did the native record
 * carry an ordinal", `hintExit` answers "did the surface find the exit to announce before the ring was
 * entered" — which is the defect that change fixed, and 0 on HEAD is its measurement.
 *
 * The manoeuvre constant chosen from these fields is deliberately not part of the line: it is an
 * `androidx.car.app` value that `:app` must not depend on, and mirroring its integers here would
 * create a second source of truth for a decision the car module already proves by unit test
 * (`NavigationTemplateMapperTest`).
 */
internal fun roundaboutStepLine(step: RouteStep): String =
    "rbt: kind=${step.kind.name}" +
        " exit=${step.roundaboutLeave?.exitCountTaken ?: 0}" +
        " cw=${step.roundaboutLeave?.clockwise ?: step.roundaboutEnter?.clockwise ?: false}" +
        " hintExit=${step.nextNext?.roundaboutLeave?.exitCountTaken ?: 0}"

/**
 * Whether [step] carries a roundabout manoeuvre worth logging: its own exit, or a hint row that is one. A
 * non-roundabout step is not logged, so the line is emitted once per roundabout manoeuvre rather than on
 * every position update.
 */
internal fun logsRoundabout(step: RouteStep): Boolean =
    (step.roundaboutLeave?.exitCountTaken ?: 0L) > 0L ||
        (step.nextNext?.roundaboutLeave?.exitCountTaken ?: 0L) > 0L
