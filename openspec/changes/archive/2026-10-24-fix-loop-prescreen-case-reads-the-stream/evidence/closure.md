# The closure of #287

`tools/issues/close.sh 287 --reason … --evidence …` — exit 0, commented then closed, `state_reason` `completed`.

```
https://github.com/Framstag/NaviVeylin/issues/287
closed #287 (completed)
```

Verified after the call:

```
state=CLOSED reason=COMPLETED closedAt=2026-10-24T12:15:52Z
labels=agent/eligible,category/build-and-harness,class/bug,prio/p3,source/agent,status/fixed-by
```

The landed commit is `cb791e83` on `main` (`merge: fix/issue-287-the-prescreen-case-reads-the-stream (#287)`),
carrying `f32c90bf` (the fix) and `09c13c3e` (the verification), and the archived change is
`2026-10-24-fix-loop-prescreen-case-reads-the-stream`.

## What the closure names

- **This change's half of the item** — `run#prescreenAnnouncesItselfBeforeItReturns`, red on the base with the
  item's own message ("`'loop: pre-screen' not found in:`") while the engine printed the line. The case now waits,
  within 30 s, for the line the stand-in pre-screen itself prints; `evidence/red-before.log`,
  `evidence/green-after.log`, `evidence/green-repeat.log`.
- **The item's other red is another change's** — `run#aWriterTheRunCanPlaceBlocksTheItem` is the defect #288
  fixed, and #288 is on `main` (`810be73d`) beneath this landing. On the base this change was developed on,
  that case is green (`PASS` in `evidence/gate.log`).
- **The gate** — `evidence/gate.log` and `evidence/gate-archived-2.log` over the change's own delta, and
  `evidence/gate-on-main.log` on the base it lands on: `LOOP-GATE: tests=213 failures=0 errors=0`. The one red
  between the two green runs is attributed in `evidence/evidence.md` ("The gate, twice, and the one flake between
  them") to the host's own load and a case this delta does not touch.
- **The guard still holds** — `tools/loop-mutations.sh` record `34.1` and a hand capture-and-replay probe both
  redden the case (`evidence/mutation-34.1.log`, `evidence/replay-probe.log`).
- **The review** — an independent read-only process: `LOOP-BLOCKING: 0`, `VERDICT: PASS`
  (`evidence/review.md`); its findings are NOTES, answered or shipped as artefacts.
- **The residual** — the identical fixed-instant read of `gate#theSuitesOutputReachesTheConsoleWhileItRuns` is
  filed as #296 (`evidence/evidence.md`, "What is not asserted").
