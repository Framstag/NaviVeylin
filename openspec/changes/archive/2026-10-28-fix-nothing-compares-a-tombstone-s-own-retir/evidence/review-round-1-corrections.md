# Round 1's corrections — what was changed and what proves it

Round 1 (`evidence/review-round-1-requested.md`, `reviewer-report-1.md`) returned `VERDICT: FAIL` with two
blocking findings and `LOOP-CORRECTIONS-ONLY: yes`. The owner's answer was **Option 2**: apply the two
corrections, re-run the gate on the final tree and copy its line and tallies. Neither correction adds a claim, a
case or an assertion.

## Blocking 1 — a `backlog-sensors` scenario named an input no case supplies

The delta widened `sensors#metadataDriftRow`'s WHEN to include "an item whose settled record and state label
disagree", while the case builds only band-pair shapes (items 278/279/280 in `tools/sensor-cases.sh`). Taken the
narrow way the owner's option names: the scenario's WHEN/THEN go back to the pair the case supplies, in the shipped
requirement (`openspec/specs/backlog-sensors/spec.md`) and in this archive's delta that produced it. The
requirement's *body* keeps the widening — the row reads `check-metadata.sh --live` whole, so it does report the
settled-record condition, and the check's own case is where that condition's behaviour is asserted
(`check#aDisplacedSettledStateIsReported`, `tools/issues/check-metadata-selftest.sh`). No claim, case or assertion
was added: `LOOP-MEASURE-CHANGE … scenarios=5 cases=5 unnamed=0`.

## Blocking 2 — the gate covered 9 of the delta's paths, not the delta

The round-1 gate ran before the archive existed, so no recorded run covered the change directory, the two deltas
or the two shipped specs. The branch was also behind `main`: merged (`main`'s settled-state guard from #681/#684
and this change's settled-record check both kept in `tools/issues/lib.sh` and `openspec/specs/issue-backlog/spec.md`),
and the gate re-run from `main`'s tip, which is the base the landing will fast-forward from:

- `LOOP-GATE: tests=231 failures=0 errors=0` over 21 suites — `evidence/gate.md`, tallies under
  `evidence/gate-tallies/` (21 files), summary in `evidence/gate-summary.xml`;
- `LOOP-MEASURE base=ccbc309994e4ccd9585974ce7b67d3642ce09eb6 paths=41` at the gate run (48 on the tree that carries this
  record's own evidence, which no mapped check reads) and `LOOP-MEASURE-GATE seconds=294 suites=21` —
  `evidence/measure.txt`, regenerated on the final tree;
- the pathspec now pulls the `openspec/**` and `*.md` checks in (`gen-feature-list --check-classification` 9s,
  `check-openspec-hygiene` + selftest, `check-doc-routes` + selftest, `check-todo-references` 2s), which round 1
  found missing;
- `evidence/evidence-check-archived.log`: `evidence-check: PASS` on the archived change.

## The five NOTEs, and the residuals round 2 found

- **The suite that carries cases**: `gate.md` said two, the run has three and now four — corrected from the
  tallies (`129 + 67 + 29 + 6 = 231`).
- **The unrecorded delegation**: `tools/issues/resolve-stale.sh` is owed by no mapped suite (the map's
  `tools/resolve-stale*` glob is the top-level tool), so it was run by hand on this tree and recorded whole in
  `evidence/resolve-stale-selftest.log` — `all 36 cases passed`.
- **Red by status only**: `evidence/revert-checks.md` now quotes the harness's own 8-mutation output, the command's
  timestamp and its exit status, and the restore checked with `git status`/`git diff` after the run — the mutation's
  message is still the red XML's (`evidence/settled-record-red.xml`, `expected [1] got [0]`), because the harness
  decides red by exit status.
- **Two UNPROVEN claims**: the numbers of the settled tombstones are now read and recorded —
  `tombstones=21 settled_numbers=138,…,158` in `evidence/tracker-read.txt`, which puts #140 and #143 among the 20 —
  and `design.md`'s rejection of a line-anchored reading says what the tracker shows (the marker occurs in 21
  bodies, no others) instead of claiming what the migration created.
- **The surface line**: it was a hand-written count beside a command that printed something else, and round 2 is
  right that `LOOP-MEASURE-SURFACE` only re-prints the proposal's own line (`tools/loop/measure.sh:207`) — it is no
  measurement. The count and the nine names are gone from `proposal.md`; it now says what the command prints (17
  files on 2026-10-29, the check and its selftest among them).

## Two residuals round 2 measured, closed after it

- **Both requirement bodies crossed the documented 500-character bound** — the `issue-backlog` ADDED at 513 and
  the `backlog-sensors` MODIFIED at 705 — so both were rewritten to 491 and 476 without losing a clause of what
  they bind. The `backlog-sensors` heading still reads "The priority pair is re-checked on a schedule"; its body
  now names the priority invariant first and the tracker's other per-item invariants beside it, so the heading
  states its share of the subject rather than all of it.
- **The marker's reader in the check's own case** (`tools/issues/check-metadata-selftest.sh:196`) reads
  `.roles.settledRecordMarker` with `jq` directly, as `tools/issues/check-metadata.sh:141` does. Left as it is: the
  single definition is the profile file both read, and routing the case's fixture guard through
  `issues_profile_value` would compile and run the Java core for a precondition, in a case that must stay green
  for a reason that is not the guard's.
