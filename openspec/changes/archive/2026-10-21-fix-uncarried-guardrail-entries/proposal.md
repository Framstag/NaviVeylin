# fix-uncarried-guardrail-entries

## Why

Issue #69 (`<!-- nvid: 134 -->`) is the record of the guardrails extracted from `ki_processing_failures.log`:
37 entries numbered 62–98. Its own convention is stated in the first paragraph — `**[open]**` marks an entry
whose named artifact does not carry the rule yet, and *an entry without the marker is only a cross-reference to
a rule that already exists* (§40, a `guidelines/*` section, a skill).

Audited entry by entry against the tree at base `44b673f424a75dbfae97e704f7e8ece03f3670b2`
(`evidence/audit.md`, one row per entry with the carrier's `file:line`): 33 entries are carried as claimed
(e.g. §134.62 → `guidelines/Build.md:141` "Liveness guard and shell recipes"; §134.77 →
`guidelines/UI.md:156` "Drag surface checklist"; §134.98 → `guidelines/Regulatory.md:277`). **Four entries
state a rule that no guideline section, skill or CI step states**, and three of them cross-reference a
*neighbouring* §40 trap instead of the rule — for §134.65 the absent thing is the *rule* (a whole-suite run
adds `--continue`), not the flag: the skill the entry names uses `--continue` incidentally and states neither
the condition nor the executed-task reading:

| entry | rule the entry states | what the tree has today |
|---|---|---|
| §134.64 | recovery order after a killed run: `--status` → `--stop` → per module/variant → `free -m` → kill orphan test workers by PID (confirm `-Djava.library.path` points at this repo) → retry with `--max-workers=1` | `Build.md:172-177` carries "split per module", the `rm -rf` of the dead results and `--status`/`--stop`; `grep -n 'max-workers' guidelines/*.md` and `grep -n 'free -m' guidelines/*.md` return nothing, so the memory check, the orphan-worker kill and the single-worker retry are nowhere |
| §134.65 | add `--continue` to any run meant to cover the whole suite, and read the executed-task list rather than the flavors you expect | the task names are carried (`Build.md:311`, `:210-221`) and the executed-task rule is carried (`openspec/specs/build-test-gate/spec.md:50`), but the rule itself is stated nowhere. The flag occurs only incidentally — `Build.md:211` inside a recorded baseline invocation, `.pi/skills/run-tests/SKILL.md:50` (re-run one failing module) and `:133` (a measured coverage invocation), `guidelines/Backlog.md:34` (a different flag, `tools/issues/answer.sh --continue`) — and no artifact says a whole-suite run adds it |
| §134.73 | never mock a class that bears `native` methods or returns Kotlin flows — `native` cannot be relaxed, a relaxed `StateFlow`-returning member throws `KotlinNothingValueException` on `collect`, a relaxed `Boolean`/object answer is `false`/`null`; use the module's fakes and real `MutableStateFlow`s | `grep -rn 'KotlinNothingValueException' guidelines/` → 0 hits; the entry's only named artifact is "(Cross-ref §40.24)", and §40.24 (`guidelines/Design.md:338`) is the `@JvmStatic`-object rule — a different trap |
| §134.76 | car-app / Compose / lifecycle test traps: drive `LifecycleRegistry` through `INITIALIZED` → `CREATED` → `DESTROYED` (never straight to `DESTROYED`), `androidx.car.app.Screen`'s constructor is protected (subclass it), `ShadowDialog.getLatestDialog()` is null for a `Box` overlay, Material3 `PartiallyExpanded` is a fraction of *content*, `assertIsDisplayed` fails for content inside a `verticalScroll`, Compose test symbols are rule members or extensions | §40.20/§40.21 (the 320×470 dp clamp and the shadow canvas) are carried at `Design.md:676-680` and `Build.md:669-681`; `grep -rn 'PartiallyExpanded\|getLatestDialog\|LifecycleRegistry' guidelines/` → 0 hits for every one of the remaining five clauses |

The knowledge is not lost — it is readable in the tracker — but it is unreadable from the tree, and the
project's own rule is that a rule lives next to the section that owns it (`AGENTS.md` "Documentation Map",
`openspec/specs/documentation-ownership/spec.md` "A concern has one owning document"). An entry marked carried
while its rule is stated only in the record is exactly the "second backlog" the TODO migration's design
rejected (`openspec/changes/archive/2026-10-17-migrate-todo-to-github-issues/design.md`, the alternatives
table).

**Root cause, one sentence.** The record's carriage marker was cleared on the strength of an adjacent
cross-reference rather than of the rule being present in the named artifact — three entries name a
neighbouring §40 trap and one names only two of its six clauses — so four guardrails are recorded as carried
while nothing in the tree states them.

**Red test on HEAD** — `evidence/red-probe.sh` (text-only: no build, no device, no network), which asks, per
entry, whether the *section that owns the concern* carries one distinguishing token of the entry's rule (one
token per entry — a multi-clause bullet can lose a clause and still pass, which is why the bullets state every
clause while the guard checks one):

```
$ bash openspec/changes/fix-uncarried-guardrail-entries/evidence/red-probe.sh
MISSING §134.64  guidelines/Build.md:172-190 ("A suite run that died mid-flight") does not state "--max-workers=1"
MISSING §134.65  guidelines/Build.md:301-318 ("## 3. Commands reference") does not state "--continue"
MISSING §134.73  guidelines/Design.md:660-689 ("### Kotlin / Compose / Robolectric traps") does not state "KotlinNothingValueException"
MISSING §134.76  guidelines/Design.md:660-689 ("### Kotlin / Compose / Robolectric traps") does not state "PartiallyExpanded"

entries=4 missing=4
FAIL record-carriage#eachRecordedRuleIsStatedInItsSection — 4 of 4 recorded rules are stated only in the tracker (exit 1)
```

Recorded `evidence/red-HEAD.txt` (captured 2026-10-21T11:41:24Z; the file carries that time in its own
header): **entries=4 missing=4**, the probe's `FAIL … (exit 1)` line. The exit code is the branch that line
announces rather than a separately observed code; `evidence/revert-mutation.txt` records an observed `EXIT=`
line for the same branch. No JUnit XML exists for this probe: the delta touches no compiled file, so no Gradle
gate is owed (`guidelines/Build.md` §2, the scope block). After the four rules are carried the same probe
prints `entries=4 missing=0`, `PASS record-carriage#eachRecordedRuleIsStatedInItsSection (exit 0)`
(`evidence/green-after.txt`, captured 2026-10-21T11:42:28Z by the same header convention).

Owning requirement: `openspec/specs/documentation-ownership/spec.md` — this change ADDS "A recorded guardrail
is stated in the section that owns it", next to the shipped "A concern has one owning document" and "A
relocated rule keeps its statement" (whose homeless-rule scenario has no record-side counterpart today).
Owning guideline sections: `guidelines/Build.md` §2 "Common behavior" (the killed-run bullet) and §3 "Commands
reference", `guidelines/Design.md` §11 "Testing", its "Kotlin / Compose / Robolectric traps" list — the list
the record's own group C names.

## What changes

- `guidelines/Build.md` §2, the killed-run bullet: the recovery order gains its missing half — `free -m`, the
  orphan test worker killed by PID after confirming its `java.library.path`, and the single-worker retry.
- `guidelines/Build.md` §3: one line under the command table — a run meant to cover the whole suite adds
  `--continue`, and the executed-task list is read rather than the flavors one expects.
- `guidelines/Design.md` §11, the trap list: two bullets (§134.73, §134.76) carrying the mocking rule and the
  car-app / Compose / lifecycle test traps, each tagged with its record entry so the mapping stays auditable.
- `openspec/specs/documentation-ownership/spec.md` via this change's delta: the new requirement and its one
  scenario, traced to the probe.

No source, resource or build file changes; the delta is additive documentation plus one requirement.

## Impact

- Files: `guidelines/Build.md`, `guidelines/Design.md`,
  `openspec/changes/fix-uncarried-guardrail-entries/**`, `openspec/specs/documentation-ownership/spec.md`
  (merged at archive).
- Modules: none (`:app`, `:auto`, `:core`, `:osmscout-client-java` untouched).
- Guidelines affected: `Build.md` §2/§3, `Design.md` §11; `AGENTS.md` unchanged (its routes already name those
  sections).
- Specs touched: `documentation-ownership` (ADDED requirement only).
- Previous specifications: none superseded.
- Additive, not breaking. Rollback: revert the change and its archive commit — no runtime behaviour, no
  build input and no test is involved, so a revert cannot leave a stale artifact behind.
- Scope: project documentation only; neither phone, Android Auto/AAOS nor the native bridge is affected.
