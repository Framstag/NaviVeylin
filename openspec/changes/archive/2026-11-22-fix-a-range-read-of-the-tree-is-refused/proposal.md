# Proposal — a range read of the tree is refused, and a log's is not

surface: 1 case (its row, its dispatch arm and its body), 1 export and 2 clauses in `tools/pi-shell-refusal.mjs`,
3 phase-prompt bullets, 1 guideline paragraph, 1 adapter comment, 2 `AGENTS.md` routing rows whose stated figure the
guideline's growth moves — 3 matches in 1 file for the case name
(`tools/look.sh 'aRangeReadOfATrackedFileIsRefusedAndALogIsNot' --path tools`) and 3 more for the bullet's heading
(`tools/look.sh 'A shell search of the tree is refused' --path tools`), in the three skeletons a child can be handed.

## Why

**Root cause.** `sed` is absent from the policy's own verb list (`tools/pi-shell-refusal.mjs:50` at the base commit),
and the exemption that left it out is about **which file** a range read names while the rule that grants it never
looks at the file: the branch of `refusesSegment` that classifies a path (`tools/pi-shell-refusal.mjs:186` at the
base) is reached only for the verbs in the list, so a range read of a tracked 22 000-line suite and a range read of a
build log under `/tmp` were the same command and both stood.

**Evidence (the item's own measurement, issue #1006).** Over `.pi/issue-loop/items` (47 items, 10 506 shell calls):
`sed` 3 545 calls (33.7 %) for 10.78 M characters at 3 041 per call, against `ctx_read`'s 6 284 calls at 1.5 k; the
`sed -> sed` adjacent pair is the top of the archive, in 46 of 47 items at 262 667 characters per item; and of the read
verbs' 9 811 calls, 6 261 (63.8 %) name a tracked tree file while 1 214 name an item log — the arm the exemption was
written for is a fifth of the volume. The item's counter-experiment is the same archive: `grep` fell from 4 997 to
1 718 once #687 refused it, so the mix moves where the policy is and not where a prompt asks.

**Red test on the base commit.** `policy#aRangeReadOfATrackedFileIsRefusedAndALogIsNot`
(`tools/loop/tests/loop-cases.sh:24019`), red on `abe49a0f` — the run's base — exit 1, first failure. The proof is the
case's first assertion read off the base module itself: a process continued onto a prepared tree holds the fix and has
no revert to run the case against, so `git show abe49a0f:tools/pi-shell-refusal.mjs` is copied out and
`node … --command "sed -n '100,130p' guidelines/Build.md"` answers `ALLOWED` with exit 0, which is the `expected '3',
got '0'` the case reports. The suite writes no JUnit XML (`.pi/skills/revert-check`, "When the gate is a shell
self-test, not Gradle"), so the tally below is the runner's own summary, transcribed at 2026-11-22T19:28:21Z:

```
a range read of a tracked file is refused (exit 0): ALLOWED: expected '3', got '0'
loop-selftest: 1 of 1 cases failed
```

recorded as `tests=1 failures=1 errors=0` (`evidence/red-on-HEAD.xml` beside the item's own copy).

## What Changes

- `tools/pi-shell-refusal.mjs` — `sed` joins `REFUSED_VERBS`; `isRangePrint` names its one reading arm (a quiet
  `-n`/`--quiet` whose script is a line range, `100,130p`); `refusesSegment` treats `sed`'s first operand as its
  script and lets a `sed` that names no path stand, the way a pipe tail stands; `refusalReason` names
  `ctx_read path=<path> offset=<N> limit=<M>` and the cap that call stops at, so a range wider than the cap keeps the
  shell.
- `tools/loop/tests/loop-cases.sh` — the case, its `LOOP_SHELL_CASES` row and its dispatch arm.
- the three skeletons a child can be handed for work that writes the tree (`worker.md:76`, `worker-gate.md:66`,
  `worker-tail.md:70`) — the block's verb list gains `sed`, and the sentence that offered a range read as refused
  states instead which arms stand.
- `guidelines/BuildGate.md:119-123` — the paragraph that owns the same rule names `ctx_read` for a range of a file in
  the tree and keeps `tail -n`, a `sed` that substitutes or writes, and a stream filter.
- `tools/pi-child-events.sh:68-70` — the adapter's comment, which said the policy leaves a `sed` range read alone.
- `AGENTS.md:43,44` — the two routing rows that state §2a's cost as a range: the paragraph grew, so the stated figure
  moves from 1.4–7.7 kB to 1.4–7.9 kB (`tools/check-doc-routes.sh` compares a row's figure with the section's bytes).
