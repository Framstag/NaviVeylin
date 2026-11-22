# Gate result and its attribution (tail, phase F) — item #1018

Gate run once by the gate child; not re-run. Line as handed to the tail:

```
tests=1254 failures=4 errors=0 (tools/loop gate plan, 22 steps, shell flavour: selftest driver#everyCaseDeclaresTheFilesItCanReach + --from-git be4f330e, loop-mutations --check, doc-values/routes, openspec hygiene, portability, issue-tooling, 7 mutation lists; base-red matched 2 of the suite's 6, hygiene red on other runs' open changes)
```

Full output the line was taken from: `evidence/gate.log` (same file at
`.pi/issue-loop/items/1018/gate.log`). The gate's own tally carries this change's red test as green:
`gate.log:374: PASS worktree#publishesTheBaseItLandedOn`.

## The 4 failures, and why they are not this change's

The failing step is `tools/loop/tests/loop-selftest.sh --from-git be4f330e`. Its 6 suite failures minus the
2 base-red matched (`run#aQuotaHoldPausesTheRunAndResumesIt`, `run#theQuotaSwitchIsExplicit`,
`gate.log:144-145`) leave `failures=4`:

- `trim#aResultOlderThanTheAgeIsStubbedAndNamed` (`gate.log:960`)
- `repeat#aRepeatReadIsNamedNotHandedOverAgain` (`gate.log:1166`)
- `repeat#aVerificationReadIsBoundedNotHandedOverWhole` (`gate.log:1167`)
- `repeat#aRepeatReadIsCountedAndTheRefusalArmWithholdsIt`

All four assert that the `LOOP-AGED-RESULT` / `LOOP-ALREADY-READ` notice names the absolute path the case
typed (`tools/loop/tests/loop-cases.sh:14051`, and the like in the three `repeat#` bodies) and got a path
spelled relative to a root instead — `.pi/tmp/loop-selftest.…/section.md` for the absolute
`<root>/.pi/tmp/…/section.md`. That spelling comes from `readKey`/`readRoots`
(`tools/pi-trim-result.mjs:263-287`, roots taken from `LOOP_TOOL_RESULT_LEDGER` and `LOOP_WORKTREE`,
issue #1009; the notice builders are `tools/pi-trim-result.mjs:176` and `:355`), work the open change
`fix-a-tool-result-ages-out-of-the-request` (#1002 — its proposal names `trim#aResultOlderThanTheAgeIsStubbedAndNamed`
as its own red test) and its neighbours #685/#886/#925 answer for, not this change.

What proves this change did not make them red and cannot turn them:

- Each case declares its whole reach: `# files: tools/loop/tests/loop-cases.sh tools/pi-trim-result.mjs`
  (`loop-cases.sh:14000`, `:21346`, `:21602`, `:21684`) — and `tools/pi-trim-result.mjs` is byte-identical
  to the project tree (`diff` exit 0).
- The whole `tools/` tree differs from the project tree by exactly this change's three files
  (`diff -rq …/tools`): `tools/loop/run.sh`, `tools/loop/leftovers.sh`, `tools/loop/tests/loop-cases.sh`.
- Those diffs are `run.sh:2796-2803` (the base push beside the item push), `leftovers.sh:411-413` (its
  paragraph) and `loop-cases.sh` hunks `109a110`, `936a938`, `4459c4461`, `4492a4496-4531` — the `worktree#`
  registrations, `worktree#noPushWithoutTheFlag`'s call-site count and the new `worktree#publishesTheBaseItLandedOn`
  body. No hunk touches the trim/repeat case bodies (`loop-cases.sh:13999`, `:21345`, `:21601`, `:21683`) and
  nothing in the diff reaches `readKey`, `readRoots` or either notice builder.
- The suite step is the one failing step in the tally; the change's own paths are green in the gate's own
  log (`gate.log:374`, plus `PASS worktree#noPushWithoutTheFlag`, `PASS worktree#pushSkipsABranchItAlreadyMerged`).

`tools/check-openspec-hygiene.sh` is red over 7 `.openspec.yaml` files missing `x-issue:` — all other runs'
open changes; this change's `.openspec.yaml` is absent from the seven, and the base-red step carried the same
hygiene red (`LOOP-BASERED: tests=0 failures=0 errors=1 (tools/check-openspec-hygiene.sh)`,
`worker-tail-report-gate-output.txt:181`).

Waived with `LOOP-GATE-WAIVED` in the tail report; the suite that names the four cases is
`tools/loop/tests/loop-selftest.sh` (`LOOP-GATE-WAIVED-SUITE`).
