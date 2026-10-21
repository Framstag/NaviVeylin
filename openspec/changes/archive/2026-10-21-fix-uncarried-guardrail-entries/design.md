# Design — fix-uncarried-guardrail-entries

## Root cause

The record (issue #69, `nvid` §134) clears an entry's `[open]` marker when the entry names an artifact, not
when that artifact states the rule. Three of the four affected entries therefore point at a neighbouring §40
trap (§40.24 is the `@JvmStatic`-object rule, not the native/flow mock rule; §40.20/§40.21 are the 320×470 dp
clamp and the shadow canvas, not the five other car-app test traps), and one entry (§134.64) was carried by a
rule that states only its first half. Evidence: `evidence/audit.md` (37 rows, one per entry) and
`evidence/red-HEAD.txt` (4 of 4 rules absent from their owning section, `entries=4 missing=4`, captured
2026-10-21T11:41:24Z).

## The single fix

State the four rules in the sections that own them, tagged with their record entry, and leave the record
untouched:

| entry | owning section | what is added |
|---|---|---|
| §134.64 | `guidelines/Build.md` §2, the killed-run bullet (`:172-177`) | the memory check, the orphan-worker kill by PID with its `java.library.path` confirmation, and the `--max-workers=1` retry |
| §134.65 | `guidelines/Build.md` §3, below the command table | `--continue` for a whole-suite run; read the executed-task list, not the flavors one expects |
| §134.73 | `guidelines/Design.md` §11, "Kotlin / Compose / Robolectric traps" | one bullet: never mock a class that bears `native` methods or returns flows — the fakes, the real `MutableStateFlow`s, `isValid` |
| §134.76 | same list | one bullet: the car-app / Compose / lifecycle test traps (`LifecycleRegistry` order, protected `Screen` constructor, `ShadowDialog` over a `Box`, `PartiallyExpanded` as a fraction of content, `assertIsDisplayed` inside a `verticalScroll`, rule-member vs extension symbols) |

Each bullet keeps the record's own wording (the fakes' names, the `KotlinNothingValueException`, the state
order) so nothing is paraphrased out of existence, and each carries `(§134.NN)` the way `Design.md`'s trap list
already carries `(§40.NN)` — the mapping stays auditable through `tools/check-todo-references.sh`, which
resolves `§134.NN` through the migration table row `| 134 | #69 |`.

## Alternatives the evidence eliminated

1. **Mark the four entries `[open]` in the tracker and wait.** Eliminated by the project's recorded decision:
   `openspec/changes/archive/2026-10-17-migrate-todo-to-github-issues/design.md` rejects a record that grows
   as a backlog ("that is a second backlog, which is the thing this migration removes"), and a marker asserts
   the opposite direction of travel — the rule would stay unreadable from the tree. It is also not a tree
   edit: the record is the run's script's to write.
2. **Add a section for guardrails, or a new guideline document.** Eliminated by
   `documentation-ownership` ("A concern has one owning document", "A rule has one normative home"): test-side
   traps are owned by `Design.md` §11 and build/run traps by `Build.md` §2–§3, and the record's own group
   headings (A "Harness / shell / Gradle", C "Kotlin / Compose / car-app") name exactly those concerns.
3. **Ship a general gate that re-derives the record's coverage.** Impossible without the record: it lives in
   the tracker, and a CI check must need no token and no network (`documentation-ownership`, "The references
   are checked without a build or a device"). A hand-maintained table inside a check would be the second list
   the routing rule forbids. What remains decidable offline is the probe over the four rules' section text,
   which is what the change ships as evidence.
4. **Change the requirement that already covers homeless rules** (`documentation-ownership`, "A relocated rule
   keeps its statement"). Eliminated because that requirement's subject is a block removed from the
   always-read entry document and its scenarios count removed blocks against statements found; a MODIFIED
   block would then have to carry that count for a case it does not measure. The change ADDs a requirement
   instead and leaves the shipped one intact.

No option survives that would split this into two fixes: the mechanism is one (a marker cleared against a
cross-reference), and its instances are answered by one kind of edit in the sections that already own the
concern.

## Blast radius, threading, lifecycle

Documentation only: three insertion points in two guideline files, plus the change's own artifacts and the
requirement merged into `openspec/specs/documentation-ownership/spec.md` at archive. No Kotlin, Java, C++,
Gradle, manifest, resource or test file changes, so no dispatcher, lifecycle or native-boundary concern
applies; nothing in the app's behaviour, its build inputs or its test suite is affected.

## Verification

- Red on HEAD: `evidence/red-probe.sh` → `entries=4 missing=4`, the probe's `FAIL … (exit 1)` line
  (`evidence/red-HEAD.txt`, captured 2026-10-21T11:41:24Z), no JUnit XML — the delta touches no compiled file,
  so no Gradle gate is owed (`guidelines/Build.md` §2, scope block). The probe's guard is **one distinguishing
  token per entry**, not every clause of a multi-clause bullet: it shows the rule reached the owning section,
  and a partial deletion of a bullet can still pass it.
- Green after the edits: the same probe → `entries=4 missing=0`, `PASS
  record-carriage#eachRecordedRuleIsStatedInItsSection`, the `(exit 0)` branch (`evidence/green-after.txt`,
  captured 2026-10-21T11:42:28Z, before the archive commit; the copies in both evidence directories carry the
  capture time in their header).
- Falsification (`revert-check` discipline, one mutation): delete the §134.76 bullet → the probe must fail
  naming §134.76 → restore → green again (`evidence/revert-mutation.txt`). The mutation is a text deletion,
  so it is reachable at run time and the probe is the case it protects.
- Structural checks standing in for the build: `tools/check-todo-references.sh` (the new `§134.NN` citations
  resolve), `tools/check-doc-routes.sh` (the sections' references still resolve), `openspec validate --all`
  (`evidence/checks.txt`). No device measurement is claimed: no on-screen behaviour changes.
