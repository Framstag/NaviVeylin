# Proposal — fix-navigation-session-file-size

## Why

`auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` is **1 589 lines**, past the `source-size` row's
bound of 1 500 — item #835, class `improvement`, and the improvement is a non-defect: the row counts lines per
tracked file, files one item per path, and the item closes when that path drops under the bound
(`tools/sensors/sensors.json`, the `source-size` row: `"threshold": 1500`, `"comparison": "greater-than"`, the
fingerprint taken over the field `path`).

Item #835 recorded **1 546** lines when it was filed; on base `c0ddb97c` the same measurement reports **1 589**
(`evidence/red-probe-head.log`, taken 2026-11-10T03:17:10Z with `git status --porcelain` empty), so the claim
still holds and the file grew by 43 lines since it was filed. The premise is the one the row measures — a
tracked `.kt` file over 1 500 lines — and no part of it moved.

**Root cause (one).** One file carries two things: the session class and the twenty-two pure decision seams
its cases reach. A seam was always typed in where the rule was read, so the file's length is the sum of every
rule the session applies *plus* the session itself, and each rule that lands there grows that sum. On HEAD the
class closes at line 1327 of `NavigationSession.kt` (the file since shortened, so the line is HEAD's — the
moved block is kept as `evidence/seams-head.txt`), and every declaration from `:1329` to `:1589` is top-level: no
member, no field, no session state — which is why a class-level cut and a file-level cut are the same number
here.

**Red case — the number the delta must move, taken with the row's own measurement.** A non-defect has no
failing JUnit case to offer unless the build grows a second copy of the row, so the red case is the row's probe
run with the item's own open condition (`evidence/red-probe-head.log`):

```
$ SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[{"state":"OPEN","condition":{"path":"auto/src/main/java/com/naviveylin/auto/NavigationSession.kt"}}]' bash tools/sensors/metrics/source-size.sh
{"path":"auto/src/main/java/com/naviveylin/auto/NavigationSession.kt","fingerprint":"f0611efc58bd51efa80f8d38c7d98075378a4ca45792b996344e8f3257fa92c2","measurement":"1589","verdict":"violation","baseline":"","title":"the source file is past the size bound","claim":"auto/src/main/java/com/naviveylin/auto/NavigationSession.kt is 1589 lines, past the row's bound of 1500"}
```

After the split the same probe prints `"measurement":"1324","verdict":"clear"` under the same fingerprint
(`evidence/green-probe.log`). The row's clearing pass is what closes the item; the number is the row's and not
a second copy of it.

**The invariant the split keeps, and how it is checked.** `evidence/seams-head.txt` is HEAD's `:1329`–`:1589`
(261 lines, the twenty-two top-level declarations). `evidence/line-accounting.log` is the `diff` between that
file and the new file's body — **empty**: the text moved, nothing was retyped, reordered or re-wrapped. The
class keeps its name, its constructor, its members, its own field visibility and every signature; no behaviour
changes, so no case's assertion changes.

**The owning requirement.** `backlog-sensors` — "A source file's size is a declared check"
(`openspec/specs/backlog-sensors/spec.md:471`): the bound, the unit and the per-path identity whose clear is
what closes this item. The guideline sections the delta answers to are `guidelines/BacklogTooling.md` §9
"Checks" (the row set this row belongs to, `:81`) and `guidelines/BacklogTooling.recipes.md` §9 (`source-size`
measures lines per tracked file; the number, the unit and the scope are the owner's recorded decision of
2026-10-21, `:90`-`:98`). No guideline states a rule about **how** an over-long file is split, and this change
does not add one: it makes an existing rule hold, which is why the diff carries no guideline edit (`design.md`,
"The guideline this change owes, and why it is none").

**Surface before the plan was fixed** (`evidence/surface.log`). `tools/look.sh 'NavigationSession' --path
auto/src` → 20 files (8 production, 12 test); `tools/look.sh 'NavigationSessionKt'` → 1 file,
`CalculationNoticeDwellTest.kt`, which names the file's JVM facade class to reach one seam by reflection;
`tools/look.sh 'NavigationSession\.kt' --path openspec/specs` → 0, `--path guidelines` → 0, `--path tools` → 0;
two test comments cite a line of the file — `NavigationSessionTripSendTest.kt:29` names `:732` and
`MapScreenTest.kt:280` names `:373-379` — both below the cut, so both stay true as they stand.

surface: 0 cases added, 1 case realigned (its facade-class name), 2 line citations that stay true, 0 spec
texts, 0 guidelines — `tools/look.sh 'NavigationSession' --path auto/src` (20 files),
`tools/look.sh 'NavigationSessionKt'` (1), `tools/look.sh 'NavigationSession\.kt' --path
openspec/specs|guidelines|tools` (0/0/0), `tools/look.sh 'NavigationSession\.kt:[0-9]' --path auto/src` (2).

## What changes

The file becomes two, the second under the bound, and the class file 265 lines shorter:

| file | what it holds | lines |
|---|---|---|
| `NavigationSession.kt` | the class alone: collaborators, state, the lifecycle observer, warmup, deep-link entry, the observation start/stop, the screen-stack presentation, the notice lifecycle, the error notice, the companion | 1 589 → **1 324** |
| `NavigationSessionSeams.kt` (new) | the twenty-two top-level declarations that were the file's foot — twenty `internal fun`, one `private fun`, one `internal const val` — with a header stating why they are top-level and where they came from | **284** |

**What stays in the class, and why.** Every member: the seams are pure by construction, and a member that
reads session state (the screen-stack mutations, the notice jobs, the observers) cannot leave without changing
its visibility and its dispatch, which is a behaviour change this item does not ask for. `SessionPresencePublishTest`
reads the `onStart`/`onDestroy` callback bodies out of `NavigationSession.kt` as text, so those callbacks stay
where they are; they do.

**The one realignment the move forces.** `CalculationNoticeDwellTest` reaches one seam by reflection and names
the JVM facade class of the file the seam lived in (`Class.forName("com.naviveylin.auto.NavigationSessionKt")`,
`:49` and `:51`). The facade class of a Kotlin file is named after the file, so the move renames it to
`com.naviveylin.auto.NavigationSessionSeamsKt`; the case's two strings and its `AssertionError` wording follow,
and every assertion it makes is unchanged. The seams that the same test calls directly
(`surfaceFailureReportElapsed`) need no edit: they are top-level `internal` declarations of the same package.

**Additive, no breaking change.** No behaviour changes, no new case, no spec text, no guideline, no build
logic, no manifest, no resource, no native source. Rollback: revert this change's commit — the new file
disappears, `NavigationSession.kt` returns to 1 589 lines, the test's two strings return to
`NavigationSessionKt`, and the row's probe prints `measurement=1589 verdict=violation` again.
