# Gate red attribution — item #861 (phase F)

The gate ran once (`./tools/loop/gate.sh`, log: this change's `evidence/gate-861.log`, mirrored at
`items/861/gate-861.log`). It came back `tests=1098 failures=1 errors=1` with two failing steps. Both are recorded
here with what proves each was red before this work and stays red after it; neither is a red this change answers for.

Gate line (verbatim, from `evidence/gate-861.log:1911`):

    LOOP-GATE: tests=1098 failures=1 errors=1 (…) — failing: tools/loop/tests/loop-selftest.sh --from-git 869a92fc6349892dbc9d9de8ef902f61cec1a07e bash tools/gen-feature-list.sh --check-classification

## What this change touches

`git diff 869a92fc6349892dbc9d9de8ef902f61cec1a07e --name-only`, run in the change root:

    guidelines/BuildEvidence.md
    openspec/specs/agent-issue-loop/spec.md
    tools/loop/run.sh
    tools/loop/state.sh
    tools/loop/tests/loop-cases.sh
    tools/loop/tests/loop-mutations.sh

## Red 1 — `failures=1`: `phase#aResultOverTheBoundIsCutAndNamed` (the loop-selftest step)

Failing output read from `evidence/gate-861.log`:

    1266: [PHASE-BOUND: cut at 4000 characters — the phase state is printed first, so a cut takes the work package's tail; that text is in …]: 'PKG-LINE-001' not found in: LOOP-PHASE: A — root cause and the red test
    1306: FAIL phase#aResultOverTheBoundIsCutAndNamed

**Red before this work.** Item #853's gate — run at 2026-11-14 04:32:54, nine hours before this item's change root
existed (change root mtime 13:33:51, red test 14:50:54Z, this gate after the 15:05 archive) — failed the same case on
the same assertion, in a tree at a different HEAD (`33105f4f84db` there vs `4a9ad8d636c0` here, `gate-run-853.log:1457`).
Excerpt copied verbatim into this directory as `phase-red-before-861.log`
(source: `items/853/gate-run-853.log:1426` the failed assertion and `:1466` the `FAIL` line):

    [PHASE-BOUND: cut at 4000 characters — …]: 'PKG-LINE-001' not found in: LOOP-PHASE: A — root cause and the red test
    FAIL phase#aResultOverTheBoundIsCutAndNamed

**Stays red after this work** — this gate itself, run after the archive, is the measurement above.

**Why this change cannot answer for it.** The case declares its files at
`tools/loop/tests/loop-cases.sh:17610`: `tools/loop/phase.sh tools/loop/prompts/worker.md tools/loop/loop.conf
tools/loop/lib.sh tools/loop/state.sh tools/loop/tests/loop-cases.sh tools/loop/tests/loop-selftest.sh
tools/pi-child-events.sh tools/tooling.json`. Intersection with the diff above: `tools/loop/state.sh` and
`tools/loop/tests/loop-cases.sh`.

- `tools/loop/state.sh`: both diff hunks (`@@ -704,6 +704,20 @@ cmd_done() {` and `@@ -719,21 +733,10 @@`) change only
  `cmd_done`'s validation of the `--gate-attribution` / `--gate-waived` flags. The case runs `bash tools/loop/phase.sh A`;
  `phase.sh` sources only `tools/loop/lib.sh` (`phase.sh:41`) and reads the state file with `jq` (`phase.sh:70`) — it
  never reaches `cmd_done`, and `phase.sh` prints no line the diff writes.
- `tools/loop/tests/loop-cases.sh`: the six hunks are the new case's list entry (`:317`), its dispatcher entry
  (`:1048`), the body of `case_state_aCloseOverARedGateWithoutAWaiverIsRefused` (`:2666`), two hunks inside
  `case_run_aRedGateIsAttributedToTheTreeThatFailsIt` (`:10897`, `:10918`) and the new `run#aFlakedCaseIsOffTheBlockedPath`
  (`:10962`). None touches `case_phase_aResultOverTheBoundIsCutAndNamed`, `phase_fixture`, or an assert helper.
- Every other file the case declares is absent from the diff, i.e. byte-identical to the base commit. The verdict is a
  pure function of those bytes (the 4000-character cut of `phase A`'s own output), so the case fails at base `869a92f`
  as well — corroborated by the #853 gate above, whose tree pre-dated this work entirely.

## Red 2 — `errors=1`: `bash tools/gen-feature-list.sh --check-classification`

Failing output read from `evidence/gate-861.log:1393-1410`:

    gen-feature-list: unclassified spec id(s):
      settings-surface (introduced by the open change settings-taxonomy-and-surface — classify it in that change, before it archives)
      settings-taxonomy (introduced by the open change settings-taxonomy-and-surface — classify it in that change, before it archives)
    gen-feature-list: stale classification entry for a spec that no longer exists:
      architecture-test-suite / package-structure / search-name-matching / search-result-row-icon / start-map-selection / store-assets
    gen-feature-list: classification gate failed

Only the unclassified half fails the step (`tools/gen-feature-list.sh:220` sets `gate_errors=1`; the stale half only
prints, `:252-255`).

**Red before this work.** The failing input is another change's open delta. `openspec/changes/settings-taxonomy-and-surface`
was created in the project tree at 09:31-09:37 today (`.openspec.yaml`: `created: 2026-11-14`; `proposal.md` mtime
09:37:26 in the project's own tree) — before this item's change root existed (13:33:51). It is gitignored
(`.gitignore:76: openspec/changes/*/`) and therefore absent from the diff above; this change's own delta names
`agent-issue-loop` only (`specs/agent-issue-loop/spec.md`), a shipped, classified capability. Nothing this change
writes can add, remove or classify `settings-surface` / `settings-taxonomy`.

**Stays red after this work** — this gate ran after the archive and printed the output above.

## The clause this close is waived with

    LOOP-GATE-WAIVED: both red steps predate this work and are off its paths — phase#aResultOverTheBoundIsCutAndNamed failed on the identical assertion in item #853's gate nine hours before this change existed (evidence/phase-red-before-861.log) and every file that decides its verdict is byte-identical to the base commit, the diff's only overlaps (state.sh's cmd_done flag validation, loop-cases.sh's unrelated case hunks) being unreachable from `phase A`; and bash tools/gen-feature-list.sh --check-classification fails on the open change settings-taxonomy-and-surface's two unclassified ids — another change's open delta, created at 09:31 today, named by the check itself and absent from this diff.
    LOOP-GATE-WAIVED-SUITE: tools/loop/tests/loop-selftest.sh --from-git 869a92fc6349892dbc9d9de8ef902f61cec1a07e bash tools/gen-feature-list.sh --check-classification
