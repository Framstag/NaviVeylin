# Forced both-flavor gate (task 5.2) — split evidence

> **Provenance caveat (read `README-provenance.md`): the *second* run below is void.** It ran while another
> session's `test -PforceTests --no-build-cache` (`pid 1036964`) wrote the same `app/build/test-results/*`;
> two Gradle builds in one project dir void both tallies (`ki_processing_failures.log` 2026-10-08). The first
> run's exclusivity is unproven. Neither verdict is evidence until a gate runs in a quiet tree.

Run: `./gradlew test -PforceTests --rerun-tasks` on 2026-10-15, after `./gradlew --stop` (the earlier
`:auto` run had died with `java.io.EOFException` because two stale daemons held ~2 GB; the box had 1.0 GB
free before this run).

**Result: red, but none of the reds is this change's.** The worktree carries a second, active builder
(`distinguish-routing-failures`, archived `fix-roundabout-exit-number`), and the failing classes are its
areas.

## This change's suites — all green in the same forced run

| suite | module | tally |
|---|---|---|
| `com.naviveylin.core.search.SearchGateTest` | `:core` | `tests="8" failures="0" errors="0"` |
| `com.naviveylin.auto.SearchScreenTest` | `:auto` | `tests="15" failures="0" errors="0"` |
| `MapCanvasViewModelSearchCancellationTest` | `:app` | `tests="6" failures="0" errors="0"` |
| `MapCanvasViewModelPoiSearchCancellationTest` | `:app` | `tests="3" failures="0" errors="0"` |
| `RoutePanelViewModelSearchCancellationTest` | `:app` | `tests="3" failures="0" errors="0"` |
| `FakeOSMScoutClientSearchRequestTest` | `:app` | `tests="4" failures="0" errors="0"` |
| `SearchBreakerTest` (host, not part of the Gradle gate) | submodule | 29 assertions / 8 cases, 0 failed |

## The failing classes, and whose work they are

```
BasemapSectionComposeTest            MapUpdatesSectionTest
MapManagerSourceSelectorComposeTest  NavigationStateOverlayComposeTest
NextTurnOverlayTest                  RouteSummaryStepSelectionTest
ShippedSpecClassificationTest
```

- Six of the seven cover files the other builder has dirty in this tree
  (`NextTurnOverlay.kt`, `NavigationStateOverlay.kt`, `RouteSummary.kt`, `MapManagerSourceSelector*`,
  `BasemapSection*`, …) — their in-flight work, red before this change touched anything.
- `ShippedSpecClassificationTest` fails with
  `every shipped spec id must be named by tools/feature-list/specs.json expected:<> but was:<[routing-failure-reporting]>`
  — their new capability, created in `openspec/specs/` as part of their change. This change's new
  capability (`search-request-lifecycle`) is still a change delta and only becomes a shipped spec at
  archive, which is when its classification is owed (the change's Workflow follow-up records that).

## Second run, after this change's submodule commit and pin bump (2026-10-15, later)

`./gradlew test -PforceTests --rerun-tasks` → red, on the same seven classes, none of them this change's:

```
15  MapManagerSourceSelectorComposeTest      6  NextTurnOverlayTest
 8  RouteSummaryStepSelectionTest            2  BasemapSectionComposeTest
 7  MapUpdatesSectionTest                    1  NavigationStateOverlayComposeTest
 1  ShippedSpecClassificationTest
```

Every suite this change owns is green in the same run:

| suite | tally |
|---|---|
| `:core` `SearchGateTest` | `tests="8" failures="0" errors="0"` |
| `:auto` `SearchScreenTest` | `tests="15" failures="0" errors="0"` |
| `:auto` `PoiResultsScreenSearchLifecycleTest` (added during apply) | `tests="3" failures="0" errors="0"` |
| `:app` `MapCanvasViewModelSearchCancellationTest` | `tests="6" failures="0" errors="0"` |
| `:app` `RoutePanelViewModelSearchCancellationTest` | `tests="3" failures="0" errors="0"` |

`NextTurnOverlayTest` was green in the first run and red again here, which is the shape of a worktree whose
other builder is editing those files while the gate runs: their reds move; this change's suites do not.

## Third run (2026-10-15, alone in an idle tree) — and the verdict of §195

The full forced gate was run once more in an idle tree: `./gradlew test -PforceTests --rerun-tasks` (single
build, only idle daemons present). Its reds are the family recorded as **`TODO.md` §195** — "Every Compose
suite times out in the full forced gate, while each passes in isolation" (`verification`, `bug`, `open`,
found 2026-10-13 while implementing `distinguish-routing-failures`) — which names the classes and the
exception itself, `AppNotIdleException: Compose did not get idle after ~2.9 million attempts in 60 SECONDS`,
and states: "Not one is an assertion failure, and not one is in a class this change touched."

Observed in this run: `BasemapSectionComposeTest` (2) and `MapManagerSourceSelectorComposeTest` (1), with six
`AppNotIdleException` occurrences at the point of reading; and, from the same run, this change's own suites
already green — `:core` `SearchGateTest` 8/0, `:auto` `SearchScreenTest` 15/0, `:auto`
`PoiResultsScreenSearchLifecycleTest` 3/0.

**So the verdict on 5.2 is structural, not circumstantial:** a *full forced gate* in this tree cannot be
"all modules green" while §195 is open, whatever any one change does. The admissible evidence that this
change is green is therefore the **focused** suite runs (see `standalone-forms-decision.md` and the device
files), not the aggregate tally — which is also what §195 itself implies. 5.2 stays open, attributed to
`TODO.md` §195 rather than to any session's in-flight work.

## Verdict

Task 5.2 cannot be completed while the tree has two owners: "all modules green" is not reachable, and the
reds are not attributable to this change. It stays unchecked, together with 2.10 (submodule commit +
gitlink bump) and 5.1 (which is paired with that commit). Re-run it once the tree has one owner; the
suites above are the ones this change must keep green. **Update (later the same day): 2.10 and 5.1 are done**
(`submodule-commit-and-pin.md`, `standalone-forms-decision.md`), so 5.2 is the only task left, and it is
red only on the other builder's classes.

## Who owns the reds — asked and answered (2026-10-15, peer network)

I asked the two sessions reachable in this project which of them held the uncommitted routing work. Neither
does:

- the `label-word-wrapping` session (`NaviVeylin#4`) had already committed and pushed its own work
  (`292fce729`, `e303556e2`) and bumped the parent gitlink (`7b8c97c`); its own hunk in the shared file was
  the single `@@ -1544,6 +1592,19 @@` block, staged the same way this change staged the search hunks;
- the design-only session (`NaviVeylin#2`) has written nothing into this tree and has escalated the routing
  work to the human architect, who has not ruled; it asked not to be waited for, and warned (correctly) not
  to stash, clean or commit-all in that directory.

So the six red test classes belong to an **orphaned** change: the native half of the archived
`distinguish-routing-failures` (plus `nav-status-progress-rings`) sits uncommitted in the submodule with no
committed baseline, and its Kotlin half is among the ~103 dirty files in the parent — which is why those
classes are red for everyone. `ShippedSpecClassificationTest` is the same situation through the spec path:
`openspec/specs/routing-failure-reporting` exists (their archive created it) but nothing names it in
`tools/feature-list/specs.json`.

What that means for a future session: 5.2 is not waiting on this change or on an active writer — it is
waiting on a ruling about that orphaned work. Nothing in the search change can or should make those seven
classes pass.

## Push state at the end of this change (nothing pushed by it)

```
submodule  c8e340a87  ahead=1 behind=0 vs origin/naviveylin-local   (this change's commit, local)
parent     04dce74    on top of 7b8c97c                           (this change's gitlink bump, local)
main       9 commits ahead of upstream/main                      (unpushed, as this repository keeps it)
```

The submodule push discipline (`guidelines/Design.md` §5) is "one session owns the submodule and its pushes,
run `git ls-remote origin <branch>` immediately before the push" — and that command must run **inside** the
submodule, because the superproject has `upstream`, not `origin`. This change left the push to the operator.
