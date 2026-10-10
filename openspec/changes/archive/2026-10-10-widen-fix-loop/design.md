# Design — widen-fix-loop

See `proposal.md` — Why for the motivation. This document records how the gate, the name and the document
change, and what each decision cost.

## Context

Current state, only what the approach needs:

- The loop's gate is six conditions (`SKILL.md` "Phase B"); three of them (3, 5, 6) discriminate, and none
  reads the entry's class. The class filter is applied earlier, in the pre-screen contract, and in two prose
  rules ("When NOT to use", pitfall 12).
- The open run's state (`.pi/bugfix-loop/run.json` in the loop worktree): `bugLimit 20`, `0 closed`,
  `32 skipped`, `current: null`, deadline `2026-10-10T05:02Z`. Verdict histogram: `needs-device 8`,
  `needs-decision 7`, `in-flight 7`, `refuted 4`, `too-wide 2`, `stale 2`, `unspecifiable 1`,
  `needs-diagnosis 1`. **No skip carries a class verdict** — the queue was built from bugs.
- Backlog: `bug` 23 (10 open, 7 open-device), `improvement` 62 (46 open, 13 open-device), `feature` 9.
- Tree topology: the skill is tracked only on branch `bugfix-loop-2026-10-09` (worktree
  `.pi/worktrees/loop`, 22+ commits ahead of `main`, `TODO.md` there reaching §171). On `main` the skill
  directory is untracked (`?? .pi/skills/bugfix-loop/`), and `AGENTS.md` plus `openspec/config.yaml`
  reference it by path.
- `loop-state.sh` reads and writes a `bugLimit` field; the live run's JSON carries that field today.

## Goals / Non-Goals

**Goals**

1. The loop closes defect-shaped improvements under an unchanged bar — no new machinery, no new phase.
2. `SKILL.md` states each rule once, in execution order, so a child with fresh context reads the rules and
   not their repetition.
3. One name for the thing across skill, flags, state fields and the two documents that reference it.

**Non-Goals**

- `class: feature` — a feature has no wrong behaviour to falsify, and scope growth (not the gate) is its
  failure mode. Condition 5 alone would carry that risk; not this change.
- The `needs-device` block (8 of 32 skips). Device verification cannot be closed by a loop that proves
  correctness on the host — a separate problem with a separate change.
- Re-ranking `TODO.md`. The queue order is `triage-todo`'s, unchanged.
- A `documentation-ownership` requirement for skill documents. The principle applies, but that capability
  governs `AGENTS.md` and `guidelines/`, and extending it is a change of its own (see Open Questions).
- `evidence-check.sh` behaviour and the phase E review contract — both are class-agnostic already.

## Decisions

### D1 — The class leaves the reject set and stays in the rank set

| alternative | why not |
|---|---|
| Keep `class: improvement` as a reject reason | The measured skip histogram shows it rejected nothing (0 of 32 skips); it only excludes §164 and §120, which pass every condition |
| Merge both classes into one queue with no order | Loses the measured bug-first priority. The improvement sample (3 entered, 0 closed: §73 `too-wide`, §9 `unspecifiable`) is a weak prior, and the risk of an improvement-heavy run crowding out bugs is real |
| **Chosen: bugs ranked first, improvements taken when the bug queue drains** | `triage-todo` already orders bugs → improvements → features, so no ranking rule is added; the loop's own drain point (third pass over all bug entries → `eligible: none`) is exactly when improvements become the only candidates |

### D2 — Condition 4 is parameterised and one improvement-only condition is added

| alternative | why not |
|---|---|
| Keep six conditions; trust 3, 5 and 6 | Conditions 5 and 6 do not catch a "make it faster" entry that is one file and host-testable — nothing there requires a measurable target |
| Add three conditions (`spec-shaped`, `countable`, `non-refactor`) | Inflates the gate's single table for one clause's worth of information |
| Fold the improvement clause into condition 3 | 3 is about *uniqueness of the fix*; the clause is about *the delta's owner and the red case's shape*. Two ideas in one cell is what made the gate hard to read |
| **Chosen: reframe 4, add 7** | One cell rewritten, one row added. Condition 7's reject verdicts (`not-spec-shaped`, `needs-target`) name the actual gap an improvement has and a bug does not |

Condition 4, rewritten: *a falsifiable WHEN/THEN that is false on HEAD — a bug asserts what the code does
wrong; an improvement asserts the bound, count or absence its delta requires and the code does not yet
meet.*

Condition 7, improvement-only: *the delta is owned by an existing capability (or adds one small
requirement), and the red case asserts a number, a count or an absence — not "better".*

Phase A gains no new machinery: the improvement red case is the **contract/bound case**, which is the
doc-vs-code divergence shape the phase already defines (`FavAutoZoomClampRangeTest`,
`MapMenuBackOrderComposeTest` precedent), with the same revert-check mutation (mutate the code constant).
The run-1 property "each archived change touched one production file" stays the width bound for
improvements too — no new clause, because condition 5 already measures it.

### D3 — Rename to `fix-loop`

| alternative | why not |
|---|---|
| Keep `bugfix-loop`, document both classes | Zero churn, but every artifact the loop emits says *fix* (`fix-<slug>`, `fix:`, `fixed-by`) — the name would be the only thing claiming otherwise, and a reader scanning skill names would never find improvements there |
| `defect-loop` | An improvement is not a defect; the name would inherit the same problem |
| **Chosen: `fix-loop`** | Matches the artifact vocabulary the loop already has. Cost: one directory, two flags, four versioned references (`AGENTS.md` ×2, `openspec/config.yaml` ×2) |

**Sub-decision — the state directory keeps its name.** `.pi/bugfix-loop/run.json` stays where it is.
Renaming it would orphan the live run (20 items, 32 skip verdicts, an open deadline) for no benefit; a later
rename can move it once no run is open (Open Questions). A consequence to state in the tasks: the *skill*
is `fix-loop` while its *state* lives at `.pi/bugfix-loop/` — the skill's own "State:" line must say so,
because a reader will otherwise look for `.pi/fix-loop/`.

### D4 — One rules file, execution order, one normative home for each rule

| alternative | why not |
|---|---|
| Minimal dedupe, keep the section order | Leaves the phases behind four sections the reader does not execute, and the gate conditions duplicated |
| Split into `SKILL.md` + a sibling reference file | For **rules**, the harness loads `SKILL.md` as the skill body, so a child may never read the sibling, and two rule files drift. This reason does not reach reference material, and the split below is scoped to exactly that |
| Rewrite from scratch | Discards the measured provenance, which is the document's real value |
| **Chosen: reorder + dedupe within `SKILL.md`, appendices in `REFERENCE.md`** | Phases follow the pre-screen; cross-cutting invariants (evidence pointers, one writer per tree, budget authority) are stated once and referenced by name; the gate conditions exist only in Phase B; the report template loses its `##` heading; the pitfalls deduplicate to the ones that add information. The three appendices — the worktree recipe, the measured costs and the field notes — carry no rule, are needed only while setting up or reading a run, and move to `REFERENCE.md`, which `SKILL.md` names. The worktree recipe copies the whole skill directory, so the second file adds no step |

**Measured, and a corrected target.** The first pass left `SKILL.md` at **444 lines** (from 479): the dedupe
collapsed ~75 lines of duplicated statements (the copy-out rule 5 → 1, the gate conditions 2 → 1, the
`§id`-max rule 3 → 1, the pitfalls 15 → 10), while the widened gate added ~40 lines of new rule text
(condition 7, two verdicts, the three red shapes, the `class` column, the ranking wording). The proposal's
“~320 lines” was written before that addition and is not reachable without cutting a rule, which the change
forbids; the split lands the rules file at the **measured 377 lines** and `REFERENCE.md` at **86**. The rule
inventory (task 4.6) is what proves no rule was lost in either file — the line count is the wrong instrument
for that, which is why it is recorded here and not asserted.

### D5 — `selftest.sh` guards the restructure mechanically

| alternative | why not |
|---|---|
| No check | The dedupe is exactly the kind of change that silently regresses on the next edit, and this document's own history is the evidence (the gate conditions already drifted into two places) |
| A separate check script | A second entry point for five assertions, and the loop's rule is that the skill's own selftest proves each check can go red |
| **Chosen: five assertions in `selftest.sh`** | (a) `--items` is accepted, `itemLimit` written and the old `--bugs` flag refused; (b) a state file carrying only `bugLimit` still resolves its remaining budget, asserting the exit code *and* the line (the live-run tolerance of D6); (c) no report-template heading is a `## ` section, while the template is still present; (d) `SKILL.md` has exactly one occurrence of the cited-XML copy-out instruction and one seven-row gate table; (e) the pre-screen's scope sentence still admits `improvement`. Every assertion must be **red on the un-restructured document** or falsifiable by a named mutation — the first draft of (c) checked `^## fix-loop` while the defect was `^## Bug-fix loop`, so it passed with the defect present; it was rewritten and is red on HEAD (1) against 0 after. Task 5.1 added (e), and the 5.2 round hardened (b) after the first mutation proved a bare `$( )` under `set -e` aborts the run before the case name is printed |

### D6 — `--items` / `itemLimit`, with the old field tolerated on read

| alternative | why not |
|---|---|
| Keep `--bugs` / `bugLimit` | The flag is the surface a user types; leaving it says "bugs" in the one place the change is about |
| Rename and migrate the open run's JSON in place | Hand-editing the state file is forbidden by the skill's own rule, and an in-place migration cannot be tested by `selftest.sh` without a fixture |
| Finish the open run, then rename | The run has 20 items of budget left and will not be finished for a rename's convenience; blocking on it is worse than the tolerance |
| **Chosen: write `itemLimit`, read `(.itemLimit // .bugLimit)`** | Every reader (`show`, `remaining`, `next`, `done`, `report`) keeps the live run's arithmetic correct while `init` writes the new field. Removed when no state file carries `bugLimit` |

### D7 — The change lands on `main`

| alternative | why not |
|---|---|
| Apply in the loop worktree (`.pi/worktrees/loop`) | A rename is meaningless against an untracked path on `main`; it would also edit the skill mid-run on a branch holding a live run and 22+ commits, risking the merge |
| Apply in both trees | Two divergent copies of the same document — the failure the skill's own worktree note was written to prevent |
| **Chosen: on `main`, adopting the untracked copy under the new name** | The skill's own rule already says the repo copy is authoritative and the worktree copy is refreshed from it (worktree recipe step 4). This also repairs the fact that a documented, referenced skill is currently untracked on the default branch |

## Risks / Trade-offs

- **The loop branch keeps the old skill until merge** (`bugfix-loop/SKILL.md` there, `fix-loop/SKILL.md` on
  `main`). A loop run started in `.pi/worktrees/loop` before the merge would read stale rules →
  mitigate by stating it in the change notes and in the branch's next commit: the worktree's copy is
  refreshed by worktree-recipe step 4, which now copies `fix-loop`.
- **Improvement yield is unmeasured** (0 of 3 improvements closed in the only sample) → mitigate: keep the
  bug-first rank (D1), and require the first post-change run to record a full pre-screen pass over both
  classes with its verdict histogram, so the next decision has data rather than a prediction.
- **A widened gate admits a "clever" improvement with no spec owner** → mitigate: condition 7 rejects on
  `not-spec-shaped` before any analysis, in the same cheap-first position as the rest of the gate.
- **The restructure drops a rule while deduplicating** → mitigate: D5's assertions plus a task that diffs
  the rule inventory before/after (one line per rule, same count), so a lost rule is visible as a missing
  line rather than an absent paragraph.
- **A reader looks for `.pi/fix-loop/run.json`** → mitigate: the state line in the skill names the path
  explicitly and explains why it did not move.

## Verification

No device, no pixels, no Gradle input: the changed files are `.pi/`, `AGENTS.md`, `openspec/`, `TODO.md`.
Verification is mechanical and recorded:

1. `selftest.sh` green, including the four new assertions of D5 (each also shown red by the revert-check
   task that mutates it).
2. `evidence-check.sh --selftest` green — unchanged, proving the restructure did not disturb it.
3. **The gate change is verified by a pre-screen pass over a fixed sample**, whose verdicts this design
   predicts. A pass that disagrees is the falsification:

   | entry | class | predicted | **measured** | deciding condition |
   |---|---|---|---|---|
   | §164 non-HTTP scheme → transport failure | improvement | **eligible** | **eligible** | 1,2,3,4,5,6,7 all hold; verified at `HttpUrlFetcher.kt:125` (`as HttpURLConnection`) and `:153` |
   | §120 car re-applies `daylight` → second stylesheet load | improvement | **eligible** | **`stale`** | 1 — the cited `MapScreen.kt:468`/`NavigationScreen.kt:477` do not exist, and `MapCanvasViewModel.ensureMapStyle` now dedupes on a recorded `(styleName, daylight)` pair, so a style switch no longer pushes the flag |
   | §163 cleartext repo, size+CRC on the same connection | improvement | `needs-decision` | `needs-decision` | 3 — hash vs signature is a format decision |
   | §77 `Bahnhofstraße` vs `Bahnhof Straße` | improvement | `needs-decision` | `needs-decision` | 3 — the boundary analysis is unspecified |
   | §76 mid-name word unreachable | improvement | `too-wide` | `too-wide` | 5 — import-side, every installed map re-imported |
   | §111 follow anchor proven only as geometry | improvement | `needs-decision` | `needs-decision` | 3 — "drive the block" *or* "extract a seam" |
   | a `class: feature` entry | feature | `not-eligible` | `not-eligible` | class is still out of scope |

   **Outcome: one disagreement, and it falsifies the prediction rather than the gate.** §120's prediction was
   written from the `TODO.md` entry, not from the tree — the error Phase A rule 1 exists to prevent, made here
   in a design document. The gate produced the correct verdict (`stale`, condition 1) and needs no wording
   change; the prediction is corrected in place rather than the gate being bent to match it. §164 remains the
   demonstrated case that the widened gate admits an improvement no previous run could have taken.
4. `openspec validate widen-fix-loop` and `openspec doctor` stay green after `skip_specs: true`.

## Migration Plan

1. Land the renamed skill on `main` (D7), commit the restructure and the gate change together — the two are
   one document, and splitting them would leave the gate change documented in old prose.
2. Update `AGENTS.md` and `openspec/config.yaml` in the same commit; a rename that leaves a dangling path in
   the routing table is a defect the `documentation-ownership` route check would flag.
3. Run the verification in §Verification and record the sample's verdict histogram in `tasks.md`.
4. **Rollback**: restore the directory name and the six-condition gate wording. The two halves are
   independent — the gate change can be reverted alone, and `itemLimit` tolerates `bugLimit` in both
   directions until no state file carries the old field.

## Open Questions

- When no loop run is open, should `.pi/bugfix-loop/` move to `.pi/fix-loop/` so the skill and its state
  share one name? Deferrable: it touches no rule and no task, and the state file is machine-local.
- Should `documentation-ownership` gain a requirement for skill documents (one normative home applied to
  `.pi/skills/`)? Deferrable, and a separate change: that capability's requirements are written about the
  entry document and `guidelines/`, and extending them changes what the route check must resolve.
