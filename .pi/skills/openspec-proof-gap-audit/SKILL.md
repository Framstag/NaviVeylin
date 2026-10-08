---
name: openspec-proof-gap-audit
description: Audits NaviVeylin's OpenSpec bugfix changes for proof gaps — compares what the original change did to prove a requirement (its tasks, tests, verification clauses) with what the later bugfix change had to add to make the bug observable, and turns each gap into a new actionable `TODO.md` task plus process recommendations. Use when asked to "audit proof gaps", "why do our changes keep regressing", "which specs are unproven", "compare bugfixes against the original changes", or as a periodic verification-hygiene pass after several `fix-*` changes landed.
---

# Audit proof gaps in OpenSpec bugfix changes

A bugfix change is a retrospective experiment. Everything it had to add to *demonstrate* the defect —
a revert-check, a concurrency case, a new test seam, an injected failure, an invalid input — is exactly
the proof the original change should have shipped and did not. This skill diffs those two task sets,
classifies the gap, and writes the missing proof obligations back into `TODO.md` as new tasks.

`openspec-verify-change` asks *"is this change done?"*. This skill asks *"was this change ever provable
as specified, and what would have proved it?"*

## When to use

- The user asks to audit / mine the openspec changes for proof gaps, or why a fixed-again area keeps breaking
- Several `fix-*` changes landed and the same capability was patched twice
- Before a release or before proposing a change in a capability with a regression history
- Periodic hygiene pass next to `process-failure-log` (that skill harvests failed *approaches*; this one
  harvests failed *proofs*)

## When NOT to use

- One active change, want "is it complete/coherent" → `openspec-verify-change`
- Rank the whole backlog → `triage-todo`
- Turn `ki_processing_failures.log` into guardrails → `process-failure-log`
- Implement or archive anything → `openspec-apply-change` / `openspec-archive-change`
- The bug is a data/import defect, a device-only symptom with no spec claim, or a harness gap → say so and
  route it (report it as `not a proof gap` with the reason); do not manufacture a test task

## The model

For a bugfix change `F` and a requirement `R` that already existed before `F`:

| Symbol | Meaning | Where it comes from |
|---|---|---|
| `O` (origin proof) | what the change that introduced or last modified `R` shipped to show `R` holds | that change's `tasks.md` verification clauses, cited tests, spec→test mapping, device tasks |
| `N` (fix proof) | what `F` had to add or change to show `R` was violated | `F`'s `tasks.md` (revert-checks, new cases, new seams, injected failures, device recipes), its `design.md` root-cause section, its task notes |
| **gap** | `N \ O` | each element is a proof obligation that was missing at origin time |

Special case: when `F` also had to **rewrite the requirement text** to make the behaviour falsifiable
(`F`'s delta carries `## MODIFIED Requirements` for `R`), the origin could not have proved `R` as written.
That is a **spec gap**, not a test gap — recommend a spec-delta task, never a test task alone.

## Inputs / outputs

| Path | Role |
|---|---|
| `openspec/changes/archive/*/` | input — bugfix changes (`fix-*`), their `proposal.md`, `design.md`, `tasks.md`, `specs/<cap>/spec.md` deltas |
| `openspec/changes/*/` | input — in-flight changes: a gap in one becomes a task addition, not a TODO entry |
| `openspec/specs/<cap>/spec.md` | input — the merged baseline: which requirements and scenarios existed before the fix |
| the repo tree (`file:line`) | input — the tests that actually exist now |
| `TODO.md` | output — one numbered section per gap (appended at top, new highest numbers) |
| active change `tasks.md` | output **only on explicit request** — proposed task additions for an in-flight change |
| `guidelines/Build.md`, `.pi/skills/*` | output only when a gap class is systemic (routed like a guardrail) |

This skill writes `TODO.md`. It never edits `openspec/specs/**` or archived change artifacts.

## Procedure

### 1. Fix the scope and enumerate the bugfix changes

```bash
openspec list --json                                   # in-flight changes
ls openspec/changes/archive | grep -E '(^|-)(fix|bugfix)-'   # archived bugfix changes
ls openspec/changes/archive | wc -l
grep -n 'FIXED by\|fixed by' TODO.md                   # fixes recorded in the backlog
```

Default scope: the bugfix changes since the last audit (ask the user which; otherwise take the newest
15 by name date and say so in the report). Announce: "Proof-gap audit scope: N archived changes
(a…z) + M active changes."

For each candidate, record in a table:

| change | date | capabilities touched | requirement pre-existed? | origin candidate |
|---|---|---|---|---|

`requirement pre-existed` = the capability path also appears in a **different, earlier** change's
`specs/` dir, or the requirement name already exists in `openspec/specs/<cap>/spec.md`:

```bash
for d in openspec/changes/archive/*/; do [ -d "$d/specs" ] && echo "$d: $(ls "$d/specs")"; done
grep -n '^### Requirement:' openspec/specs/<cap>/spec.md
```

If the fix created the capability/requirement itself (spec dir appears in no earlier change), it is a
**new-behaviour bug**, not a proof gap — keep it in the table with that verdict and skip step 4.

### 2. Reconstruct `N` — what the fix had to prove

Read the bugfix change completely (raw ranges, not a compressed excerpt): `proposal.md` `## Why`
(root cause), `design.md` (root-cause decisions), `tasks.md` (every task, including `— **done …**`
notes, they carry the evidence).

Extract, with the task number as citation:

- **Falsification steps** — a revert-check ("remove the mutex → same case dies SIGSEGV on 3/3 runs"),
  a counterexample input, a mutation the fix disables.
- **New observability** the fix needed — a seam, a counter, `SetChangeCount()`, a dispatcher recorder,
  a fake override, a `saveFailure` switch.
- **New cases** — concurrency/interleaving, ordering, invalid/missing/empty input, tolerance edges,
  second database, permission failure.
- **Device recipes** the fix introduced, and which of them actually ran.
- **Claimed-but-not-run proof** — `BLOCKED`, "by inspection", "verified by grep", "no CMake host build dir
  exists", "recorded as a finding in TODO.md". These are gaps, not evidence.

Also read the fix's spec delta: `## MODIFIED Requirements` for a pre-existing `R` → possible spec gap (G8).

### 3. Reconstruct `O` — what the origin proved

The origin is the change that introduced or last modified `R` before the fix:

```bash
# earlier changes touching the same capability
for d in openspec/changes/archive/*/; do [ -d "$d/specs/<cap>" ] && echo "$d"; done
git log --oneline -- openspec/specs/<cap>/spec.md     # if the spec was ever tracked
grep -rn 'Found .* during `\?<origin-change>' TODO.md  # the origin's own leftover findings
```

From the origin change collect, with task numbers:

- its `tasks.md` verification clauses (`verify: …`) and its spec→test traceability task, if any
- the tests it cites as the proof of `R` (`file:line`, test name)
- its spec→scenario inventory: which `#### Scenario:` of `R` was mapped to a concrete case
- its on-device / verification tasks and whether they ran
- the TODO `§` entries it created "while implementing `<origin>`" for the same behaviour — a requirement
  whose proof was deferred to a later change is a gap the origin already admitted

Verify today's tree: a test the origin *claims* must be grepped for, and a claim of "no test seam" must be
checked against the current `Tests/`, `src/test*` and the seam the fix added.

### 4. Diff `N \ O` and classify

One class per gap; when two fit, the more structural one wins.

| # | Gap class | Test | Real precedent in this repo |
|---|---|---|---|
| G1 | No falsification / revert-check | origin asserted the fixed behaviour but never showed the assertion can fail | `fix-native-database-open-race` task 1.4: remove the lock → SIGSEGV on 3/3 |
| G2 | Helper proven, wiring unproven | origin tested a pure helper/fake; the defect lived in the untested caller/JNI/integration path | `TODO.md` §109 (scope filter tested as helper, `DoSearchLocations` wiring untested) |
| G3 | Spec scenario never mapped to a case | a scenario existed at origin and no task/test referenced it | `TODO.md` §102 (4 scenarios of `reorder-favorite-groups`) |
| G4 | Concurrency / ordering unproven | sequential cases only, no interleaving or thread-count case | `fix-native-database-open-race` task 1.4, `fix-favorite-store-write-race` |
| G5 | Invalid / edge input unexercised | the failing input class (missing, empty, deleted, non-directory) had no case | `map-render` "Open invalid map database" (2026-07-29 → `fix-open-database-path-validation`) |
| G6 | Failure not injectable | fakes always return success, so the failure branch is dead in tests | `TODO.md` §102 item 4 (`saveFavoriteLocations` always `true`) |
| G7 | Device-only proof never executed | the only proof is an on-device task that is `BLOCKED`/unrun, with no host substitute | `fix-native-database-open-race` tasks 6.1/6.2 |
| G8 | Spec gap: requirement could not fail as written | the fix had to MODIFY the requirement text | `fix-follow-vehicle-jumps` (smooth-follow `## MODIFIED Requirements`) |
| G9 | Proof by inspection / absence | "no bare `knownPaths` reference remains", "verified by grep", "by inspection" | `fix-native-database-open-race` task 1.5 |
| G10 | Tolerance contract unproven | the requirement promises "one bad input never fails the rest" and no case exercises a bad input in the middle | `native-database-open` batch tolerance |

### 5. Aggregate before writing anything

Build two tables:

- **by class**: class → count → changes → capabilities
- **by capability**: capability → gaps → how many times patched by a `fix-*`

Systemic class = ≥3 occurrences, or the same class in ≥2 different capabilities for the same reason. A
systemic class gets a **process recommendation** (a rule in `guidelines/Build.md` §verification, a
checklist line in the propose/tasks schema, a CI gate, a skill note) in addition to the per-gap TODO
entries — same routing `process-failure-log` uses for guardrails. State the class explicitly as
`G<n> ×<count> → <target artifact>`; do not restate it per gap.

### 6. Write the recommendations

**Per gap → one `TODO.md` section** (this is the deliverable). Follow the file's own format exactly:

```markdown
## <new-max>. <one-line claim> — Proof gap of `<origin-change>` found <date> auditing `<fix-change>` (spec `<cap>`)

- **Observed** ℹ: <what the origin shipped as proof of `R`, cited by origin task number / test name /
  `file:line`>, and what `<fix-change>` had to add to make the defect observable (fix task number,
  revert-check sentence, new case).
- **Consequence** ⏳: <what regression stays uncaught today, and which spec scenario is consequently
  unproven>.
- **Fix candidate**: <the concrete test/seam to add: file to extend, case name, the input that must fail
  today, the revert-check that must kill it. Name the seam problem if the code is unreachable from a host
  test (then say what narrower seam or database-backed test closes it).>
```

Rules for writing:

- **Numbering is append-only**: new sections take `max+1, max+2, …` (this file's numbers descend by
  insertion, newest at top, so insert the new block directly under the `---` after the legend) and
  **never renumber** an existing section — change artifacts and sessions reference §numbers.
  ```bash
  grep -o '^## [0-9]*' TODO.md | awk '{print $2}' | sort -n | tail -3   # current max
  grep -o '^## [0-9]*' TODO.md | awk '{print $2}' | sort -n | uniq -d   # collisions already present
  ```
- **Deduplicate**: if the gap is already tracked (§102-style coverage list, a `✗`/`⏳` entry, an entry
  marked "removed when the change is archived"), add a cross-reference inside the new entry or skip it —
  never a second copy.
- **One gap per section**, one concrete action per `Fix candidate`.
- **Gap in an in-flight change** → do not write a TODO entry; propose the `tasks.md` addition instead and
  write it only if the user asks.
- **Spec gap (G8)** → the `Fix candidate` is a spec-delta change for the capability, not a test.
- **G7** → the fix candidate must name a host-side substitute; if none exists, say the gap is closed only
  by a device pass and state the blocker.

### 7. Ask once, then write

Show the finished report (scope table, gap diff, class/capability tables, the exact TODO sections) and
ask one question: write the sections into `TODO.md`, write + also draft the process recommendations, or
report only. `TODO.md` is the only file modified; edits are insert-only.

### 8. Report

```markdown
## Proof-gap audit — <date> (N bugfix changes, M capabilities)

### Scope
<changes scanned, and how they were selected>

### Gaps
| fix change | capability | requirement | origin proof `O` | fix proof `N` | gap class | new § |
|---|---|---|---|---|---|---|

### Systemic classes
| class | count | capabilities | process recommendation | target artifact |

### Not proof gaps
<new-behaviour bugs, data/import defects, harness gaps — with the reason>

### Written to TODO.md
<new section numbers and titles> · <cross-references added instead of new entries>
```

## Evidence rules

- Every gap claim carries the origin task number (or "no task") **and** the fix task number or its
  `— **done …**` note, plus a `file:line` or an archived artifact path.
- The fix change's own task notes are the primary evidence: they say in its own words what was needed.
- Never claim a test is missing without grepping for it (`ctx_grep` on the case name and the assertion).
- Quote the revert-check sentence verbatim where one exists; it is the strongest single piece of evidence.
- If the origin change is not in the archive, say "origin not reconstructible" and mark the gap
  `unproven cause` rather than guessing.

## Pitfalls

1. **A fix is not automatically a proof gap.** New-behaviour bugs, data/import defects (`TODO.md` §91,
   §99 shape), harness/tooling gaps (§66, §79) and pure refactors belong elsewhere — list them under
   "not proof gaps" with the reason.
2. **Spec-created-by-the-fix.** If no earlier change carried the capability, there is no `O` to compare;
   do not invent one.
3. **Old archived changes have no `verify:` clauses.** Their absence is itself evidence (G1/G9), but do not
   read a missing clause as a missing test — check the tree.
4. **`BLOCKED` device tasks are not automatic gaps.** Only if no host substitute exists and the requirement
   is device-only (G7).
5. **Do not double-count one root cause as several gaps.** Rank the cluster by its structural class and note
   the siblings.
6. **The spec delta is evidence too.** A `## MODIFIED Requirements` block for a pre-existing requirement is
   the fix admitting the old text could not fail — G8, and the recommendation is a spec change.
7. **`TODO.md` is a working file.** Duplicate section numbers, `FIXED by …` markers and
   "removed when the change is archived" entries all occur; report anomalies, cross-reference instead of
   duplicating, and never renumber.
8. **Never edit `openspec/specs/**` or archived artifacts.** The audit reads them; only `TODO.md` is written.
9. **Repo shell constraints.** `python3`/`perl` are blocked — use `jq`, `grep`, `sed`, `awk`.
   `ctx_read` compression silently drops lines of long files (`tasks.md` here is 15-20 KB): read raw
   ranges for any artifact whose exact sentence is being cited. Git/gh output may be German; that is normal.

## Notes

- Scope defaults to the newest archived bugfix changes; an audit of all 67 is not a single session — say
  what was covered and what was not.
- The result is a proposal until step 7: the audit's value is the class table (what keeps being unproven),
  not the individual TODO entries.
- Re-running the audit after fixes land should show a class shrinking; if a class recurs, the process
  recommendation (not the entry) is what failed — name that in the report.
