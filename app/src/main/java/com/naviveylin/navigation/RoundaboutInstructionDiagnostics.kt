package com.naviveylin.navigation

import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType

/**
 * Coordinate-free diagnostics for the roundabout fields of a route instruction (spec: `osmscout-jni`
 * — A route instruction carries the roundabout exit ordinal and its circulation direction; spec:
 * `auto-diagnostics` — no coordinates in diagnostics).
 *
 * The line exists because the fields are filled in native code and no JVM test executes the bridge:
 * this is what a device run reads to see that the route really produced an ordinal and a direction,
 * instead of inferring them from a screenshot. Identity only — turn type, ordinal, direction — never a
 * coordinate (enforced by the `checkNoCoordinatesInLogs` build gate).
 *
 * The maneuver constant chosen from these fields is deliberately not part of the line: it is an
 * `androidx.car.app` value that `:app` must not depend on, and mirroring its integers here would
 * create a second source of truth for a decision the car module already proves by unit test
 * (`NavigationTemplateMapperTest`).
 */
internal fun roundaboutInstructionLine(instruction: RouteInstruction): String =
    "rbt: type=${instruction.turnType.name}" +
        " exit=${instruction.roundaboutExitNumber}" +
        " cw=${instruction.roundaboutClockwise}" +
        " hintExit=${instruction.nextNextRoundaboutExitNumber}"

/**
 * Whether [instruction] carries a roundabout manoeuvre worth logging: its own exit, or a "next next"
 * hint that is one. A non-roundabout instruction is not logged, so the line is emitted once per
 * roundabout manoeuvre rather than on every position update.
 */
internal fun logsRoundabout(instruction: RouteInstruction): Boolean =
    (instruction.turnType == TurnType.ROUNDABOUT_LEAVE && instruction.roundaboutExitNumber > 0) ||
        instruction.nextNextRoundaboutExitNumber > 0
