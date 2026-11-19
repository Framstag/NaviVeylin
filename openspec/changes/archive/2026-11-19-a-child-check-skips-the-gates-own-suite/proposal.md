# Proposal: a-child-check-skips-the-gates-own-suite

## Why

A child is stopped by the number of checks it ran (`LOOP_CHILD_CHECKS_MAX=40`, `tools/loop/loop.conf`), counted by
`child_check_calls` in `tools/loop/lib.sh` from the stream's calls that run one of the gate's check programmes. The
count already removes the gate's own programme (`is_gate`, issue #882). It does not remove a call that runs the whole
suite by hand — `tools/loop/tests/loop-selftest.sh --from-git <base>`, `./gradlew test` — which is the gate's own
work under another name, and the one call a child makes that the gate will certainly re-run.

- **Observed** ✗: in the run started 2026-11-18T22:18:25Z, 4 of 11 items (36.4 %) ended at that bound — #964 620
  turns after 64 checks, #902 580 after 53, #900 318 after 40, #894 a deadline after 447 — against **0 of 18 items**
  in the archive a bound ended (`tools/loop/report.sh --state .pi/issue-loop/run.json`, its `bound:` line).
- **Measured** ⊨: those children ran **253 checks themselves = 1.9 h**, 19.4 % of the run's 9.6 h of item wall, and
  the item-level gate that followed every one of the four **passed** on the tree the bound had left
  (`items/{964,900,902,894}/cut-gate.log`) — the bound cut finished work, not failed work.
- **Cost of the cut** ⊕: 4 unlanded branches and 4 of the run's 19 caller actions are resumes of those items.

## What Changes

- **`tools/loop/lib.sh`: a whole-suite run is the gate's.** The classification `is_gate` gains the full-run forms of
  the programmes `gate_check_programs` already lists (a suite invoked without `--case`, a Gradle test task without a
  `--tests` filter), so `child_check_calls` counts the focused cases `guidelines/BuildGate.md` §2a hands a child and
  not the gate's suite run by hand. Nothing else about the bound moves: it still ends a child that spends 40 of them.
- **`tools/loop/tests/loop-cases.sh`: two cases.** One stages a stream whose only check call is the full suite and
  asserts the child's count stays 0; one stages a focused case and asserts it is still counted and still ends a child
  at the bound.
- **`tools/loop/loop.conf`: the measurement beside the key is stated for what it now counts** — the p90 of 38 was
  taken over gate-shaped calls, so the comment names the suite runs it now excludes and the figure that decision
  rests on (253 checks, 1.9 h, four items of one run).

## Evidence

- The four cut items and their `cut-gate.log` verdicts are in `.pi/issue-loop/items/{964,900,902,894}/`; the bound
  line and the `phases` block that prices the child gate time at 1.9 h are `tools/loop/report.sh --state
  .pi/issue-loop/run.json` output for the run started 2026-11-18T22:18:25Z.
- The classification the change moves is one function pair with one definition each: `is_gate`/`is_check` are shared
  by `report.sh`'s `gateRepeat` count and `run.sh`'s check bound, so the two readings cannot drift apart.

surface: 1 helper (`gate_command_jq`'s classification), 2 cases, 2 mutation records, and the prose the rule makes
wrong — the config comment beside `LOOP_CHILD_CHECKS_MAX`, the `gateRepeat` doc in `state.sh` and `report.sh`, the
worker-gate prompt's sentence and `Backlog.recipes.md` §7b's figure —
`tools/look.sh 'is_gate|is_check|is_whole_check|whole' --path tools/loop/lib.sh` and
`tools/look.sh 'LOOP_CHILD_CHECKS_MAX' --path tools/loop/loop.conf`
