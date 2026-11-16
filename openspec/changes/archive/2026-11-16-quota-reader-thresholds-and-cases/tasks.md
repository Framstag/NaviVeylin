# Tasks

## 1. The reader's cases, written before the fix (they are red on HEAD)

- [x] 1.1 Create `tools/loop/tests/loop-quota-selftest.sh` in the shape of `tools/loop/tests/loop-spend-selftest.sh`:
      `quota#<id>` case ids, `--case <id>` with an unknown id refused (exit 2, never a quiet zero), fixtures written
      under `mktemp -d` and fed to the reader through `LOOP_QUOTA_JSON`, no network, no token; the summary line in
      the shape the gate derives `tests=`/`failures=` from. Verify: `bash tools/loop/tests/loop-quota-selftest.sh
      --case quota#noSuchCase` exits 2, and `--case` for a case that exists runs exactly one.
      Spec: `agent-issue-loop` — the requirement's own clause that each comparison is exercised offline.
- [x] 1.2 Add `quota#aWindowUnderItsFloorHoldsUntilItResets`: two fixtures, one with the session window under its
      floor and one with the weekly window under its. Verify: each exits 3, the reason names the window and its
      percentage, and `resume-at` is the window's own `resets_at` plus the margin.
      Spec: `agent-issue-loop` — "A window under its floor holds the run".
- [x] 1.3 Add `quota#thePacingLineIsTheElapsedShareLessTheReserve`: fixtures on both sides of the line, with the
      reserve and the week's start pinned so the expectation is arithmetic (`elapsed × (100 − reserve) / 100`) and
      not a recording of today's answer. Verify: the one below the line exits 0 in silence, the one above exits 3
      naming burn, line, elapsed share and reserve.
      Spec: `agent-issue-loop` — "The pacing line is the week's elapsed share less the reserve".
- [x] 1.4 Add `quota#aHoldOnTheLineNamesWhenTheLineArrives`: a burn past the line but under the reserve's cap.
      Verify: `resume-at` is the instant the line reaches that burn, which is strictly earlier than the window's
      `resets_at`, and the two differ by the arithmetic the case spells out.
      Spec: `agent-issue-loop` — "A hold on the line names when the line arrives".
- [x] 1.5 Add `quota#anInstantItCannotParseIsNamedNotDropped`: fixtures spelling `resets_at` with a `+00:00` offset,
      with fractional seconds, and absent. Verify against HEAD's reader first — the case must be **red** there,
      because today's reader skips the pacing comparison and prints nothing. Record the red run (command, exit,
      output) in the task's evidence before touching `tools/loop-quota.sh`.
      Spec: `agent-issue-loop` — "An instant the reader cannot parse is named, not dropped".
- [x] 1.6 Add `quota#aBalanceItCannotReadIsAGoWithANote`: an absent fixture, an unreadable one, and JSON of another
      shape. Verify: each exits 0 and prints one line naming what it could not read, and none holds.
      Spec: `agent-issue-loop` — "A balance it cannot read is a go with a note".
- [x] 1.7 Add `quota#theRecordedValuesAreTheOnesTheseCasesAssume`, reading the three `LOOP_QUOTA_*` values out of
      `tools/loop/loop.conf`. Verify: it is red until task 3.1, and names which value differs.

## 2. The reader names what it could not parse

- [x] 2.1 In `tools/loop-quota.sh`, print one line naming the instant it could not read when the session or weekly
      `resets_at` is absent or not in the one shape `fromdateiso8601` accepts, keeping exit `0` and the `go` decision
      — the run relays a zero-exit reader's stdout (`state.sh cmd_next`) — and print it on that exit-0 path only, so
      a hold's stdout stays its reason and `resume-at` (the pair `tools/loop/run.sh:2284` reads). Verify: 1.5 turns
      green and 1.3's below-the-line case still exits 0 in silence, so a clean balance says nothing.
      Spec: `agent-issue-loop` — "An instant the reader cannot parse is named, not dropped".
- [x] 2.2 Keep the note off the `--json` path so the self-test's structured reading is unchanged. Verify: `read_quota
      "$offset" --json` and `read_quota "$floors_blind" --json` inside case 1.5 assert `has("notes")=false` and the
      `go`/`window` the decision implies — one document per balance, no note key.

## 3. The two thresholds

- [x] 3.1 `tools/loop/loop.conf`: `LOOP_QUOTA_PACING_RESERVE_PERCENT` 10 → 5; `LOOP_QUOTA_SESSION_MIN_PERCENT` is
      already 5 at this base (commit `07c3612c`), so it stays and the comment that called it the larger floor is
      rewritten to say the two floors are one number; `LOOP_QUOTA_WEEKLY_MIN_PERCENT` unchanged at 5. Verify: 1.7
      green, and `bash tools/check-doc-values.sh` green (the gate maps `tools/loop/loop.conf` to it).
      Spec: `agent-issue-loop` — the requirement's clause that the values are the project's configuration.
- [x] 3.2 Add one `LOOP_GATE_SUITES` row mapping `tools/loop-quota*` to
      `bash tools/loop/tests/loop-quota-selftest.sh`, in the shape of the existing `tools/loop-spend*` row. Verify:
      `tools/loop/gate.sh` selects that step for a delta touching `tools/loop-quota.sh` and selects nothing new for a
      delta touching `tools/loop/tests/loop-cases.sh`.
- [x] 3.3 Re-read the change against the reading it was written from: with reserve 5 and weekly floor 5, the line's
      end point is 95 % burned and the floor is 5 % remaining, so no weekly point is unreachable. Verify: 1.3's
      `line-cap` and `line-cap-over` fixtures assert that pair — 95 % burned is a hold at the window's reset (the
      line cannot arrive later), 4.9 % remaining is the floor's hold, and 5 % remaining as a *burn* is a go.

## 4. Revert-checks — one mutation per invariant the change introduces

- [x] 4.1 The pacing line. Mutate `(100 - $reserve)` in the jq program to `100`, so the line is the elapsed share
      alone. Verify: `quota#thePacingLineIsTheElapsedShareLessTheReserve` fails; restore; re-run the case green.
- [x] 4.2 The arrival instant. Mutate the pacing branch's solved `resume` to `$wr`. Verify:
      `quota#aHoldOnTheLineNamesWhenTheLineArrives` fails; restore; re-run the case green.
- [x] 4.3 The floors. Mutate the weekly floor comparison so the floor is 0. Verify:
      `quota#aWindowUnderItsFloorHoldsUntilItResets` fails; restore; re-run the case green.
- [x] 4.4 The note. Replace the exit-0 note block with a no-op that still parses — `printf '%s\n' "$decision" |
      jq -r '.notes[]? | "quota: " + .' >/dev/null 2>&1 || true` — so the reader stays a working script and the only
      thing missing is the line. Verify: `quota#anInstantItCannotParseIsNamedNotDropped` fails on the missing note
      (not on a syntax error); restore; re-run the case green.

## 5. The gate

- [ ] 5.1 The delta touches no compiled file (`tools/**`, `openspec/**`), so the gate is the structural set:
      `bash tools/loop/tests/loop-quota-selftest.sh`, `bash tools/loop/tests/loop-selftest.sh`,
      `bash tools/check-doc-values.sh`, `bash tools/check-openspec-hygiene.sh`,
      `bash tools/gen-feature-list.sh --check-classification`, `bash tools/check-doc-routes.sh`. Verify: every one
      exits 0 and the selftest's summary reports the expected `tests=` count with `failures=0`. Left open on purpose:
      this item's gate is the run's, and `./tools/loop/gate.sh` is not this phase's step (`LOOP-DIFF-READY`).
- [x] 5.2 `openspec validate quota-reader-thresholds-and-cases --strict`. Verify: "is valid", and every scenario in
      the delta names a `quota#` case that task 1 or 3 added.

## Workflow follow-up

- Archive the change with `openspec archive`, which lands the added requirement in
  `openspec/specs/agent-issue-loop/spec.md`.
- `tools/loop/loop.conf` is read by a run that may be live. The run's own budget (`--items`, `--minutes`) was fixed
  when its `run.sh` ran `loop_resolve` once, but the quota thresholds are read by **each** `state.sh` invocation —
  `state.sh` runs `loop_resolve` per process and `cmd_next` takes the three numbers from `loop_conf_value` — so the
  edit lands on the live run's **next item boundary**, not at the next run. State that when the change lands: a live
  run's pacing line moves the moment the file does, and a landing placed between a gate and its merge is exactly the
  window in which nothing should be changing what the run is allowed to spend.
