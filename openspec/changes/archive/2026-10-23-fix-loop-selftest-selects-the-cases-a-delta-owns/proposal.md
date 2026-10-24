# The self-test selects the cases a delta's files own

## Why

**Observed ✗, verified in the tree today.** The declared case set is a flat list of names —
`LOOP_SHELL_CASES`, `tools/loop-cases.sh:32` — and the driver's only filters are the name and the group:
`--case` and `--group` at `tools/loop-selftest.sh:39`–`:40`, and the selection they decide is the loop at
`:309`. Nothing in the file
records which file a case exercises, so a delta to `tools/loop/worktree.sh` and nothing else owes the whole
set. `tools/loop/gate.sh` already computes the delta's paths for its suite map (`tools/loop/gate.sh:88`) and
had nowhere to hand them to a case selection.

**Root cause, one sentence.** A case carries no record of the files it can be affected by, so the only
selectable dimensions are its name and its group and the gate must ask for the suite whole.

**The bound.** 190 cases and 723 s of self-test per `tools/loop/**` gate run (issue #261 measured 163 cases
and 723 s on 2026-10-22). A delta such as #261's touches two engine files.

**The asymmetry the item names.** `tools/loop-mutations.sh` stores the file each record mutates
(`label@fifo@file@case@desc@exprs`, `tools/loop-mutations.sh:17`) and has carried `--files` / `--from-git`
since #235 — the same shape, in the same repository, one harness over.

**The red test.** Five cases added by this change; against the base commit's driver and gate
(`git show 6683376e:tools/loop-selftest.sh`, `…:tools/loop/gate.sh`, restored afterwards), 2026-10-23T12:05Z:

- `tools/loop-selftest.sh --group driver` → `FAIL` for `driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns`,
  `driver#aFileSetThatNamesNoCaseIsRefused`, `driver#aCoreFileOwesEveryCase` and
  `driver#everyCaseDeclaresTheFilesItCanReach`, each with `loop-selftest: unknown argument --files` or
  `--case-files`; `evidence/red-driver-group.xml` (`tests="10" failures="4"`), transcript
  `evidence/red-driver.log`.
- `tools/loop-selftest.sh --group gate` → `FAIL gate#theDeltaHandsASuiteTheBaseItMeasured`, because the suite
  the profile named kept the literal `{base}` instead of the base the gate resolved;
  `evidence/red-gate-group.xml` (`tests="12" failures="1"`), transcript `evidence/red-gate.log`.

**Owning capability and guideline.** The capability is `agent-issue-loop`, requirement "A suite run selects
what it was asked for and the gate records what it cost" (`openspec/specs/agent-issue-loop/spec.md:1659`) —
the requirement this change extends with a third dimension. The guideline is `guidelines/Backlog.md`, section "7. The loop"
("The loop"), the paragraph beside the streamed gate; `AGENTS.md` routes the loop there.

## What Changes

- Every case declares the files it can be affected by in the first line of its body — `# files: <token> …` —
  190 declarations, a token ending in `/` for a directory and `*` for "any delta at all".
- `tools/loop-selftest.sh` gains `--files <path> …`, `--from-git <base>` and `--case-files`: the selection is
  the cases the changed paths select, a file set that names no case is refused with exit 2, and the selection
  is stated against the declared set before a case runs.
- A core file (`tools/loop/lib.sh`, the profile, the case file, the driver) owes the whole set whatever the
  cases declare.
- `tools/loop/gate.sh` substitutes `{base}` in a profile's suite command with the base it resolved, and
  `tools/loop/loop.conf` runs the self-test as `tools/loop-selftest.sh --from-git {base}`.

## Impact

`tools/loop-cases.sh`, `tools/loop-selftest.sh`, `tools/loop/gate.sh`, `tools/loop/loop.conf`,
`tools/loop-mutations.sh` (five new records), `guidelines/Backlog.md`, section "7. The loop", and the delta in
`openspec/specs/agent-issue-loop/spec.md`. No compiled file: the gate owes the two shell suites and the record
check, and the `*.md`/`openspec/*` paths owe the doc-route and classification checks.
