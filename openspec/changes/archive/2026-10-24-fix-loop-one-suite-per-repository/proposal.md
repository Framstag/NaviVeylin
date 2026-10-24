# One suite at a time runs in a repository

## Why

**Verified in the tree today.** The driver takes no lease on the repository it runs in. It resolves the scratch root
the repository records (`tools/loop/loop.conf:86` sets `LOOP_TMP_DIR=""`, so `tools/loop-selftest.sh:82` resolves
`.pi/tmp` under the tree it was started in), it creates this suite's own scratch tree under it
(`tools/loop-selftest.sh:84`), and nothing in the driver can tell that a second suite stands beside it in the same
repository. The per-invocation scratch root and the per-case throwaway repository isolate the files each suite
writes; they do not isolate the host both suites are then running on.

**Root cause, one sentence.** Two suites in one repository are allowed to run beside each other — nothing takes the
repository before a suite starts and nothing refuses the second — so the failing set of one suite depends on the
other, and a red gate is attributed to the change under test when the neighbour caused it.

**Measured, 2026-10-24 (the item's own measurement).** A run of 210 cases (`--from-git 49dfd7db`) failed
`prompt#workerNamesEveryPhaseAndSkill`, `prompt#workerCarriesTheFeaturePath` and
`run#aWriterTheRunCanPlaceBlocksTheItem`; a control run of the same suite from the pristine base (`49dfd7db`, 209
cases, same host, same day, a detached worktree) failed `run#prescreenAnnouncesItselfBeforeItReturns`,
`run#childOutlastingTheWallClockIsBlocked` and the same writer case. The intersection is one case, and that one is
another item's stable red (#288). The two `prompt#` cases pass 11 of 11 alone on the same tree, so what moved
between the two runs was not the change under test. **The lease this delta adds is per scratch root, so it does not
refuse that pair**: those two suites stood in two trees of one repository — a change worktree and a detached control
worktree — and each resolves its own root. What remains between them is the host's load, which is #259/#260's ground;
what this delta changes is that a second suite *in one tree* is refused and named instead of silently halving the
host between two suites of that tree.

**Re-measured in this change's own run, 2026-10-24T09:28:36Z.** Three suites were live at once: this change's full
suite (`.pi/worktrees/i295`), `tools/loop/gate.sh` running `tools/loop-selftest.sh --from-git a0ca0f35…` in the
project tree, and `tools/loop/gate.sh --base c4c727fb` running `tools/loop-selftest.sh --from-git c4c727fb` in
`.pi/worktrees/i287`; two loop runs stood beside them (`…/issue-227/tools/loop/run.sh --items 1 --minutes 90
--review off`). `evidence/concurrent-harnesses.log` is the `ps` snapshot and the `git worktree list` of that moment.

**The whole set, run three times from this change's own tree (the same base, the same tree), 2026-10-24.** Run 1
(`bash tools/loop-selftest.sh`, 09:27:55Z → 09:50Z) failed 2 of 211 cases; run 2 (the gate's own suite, 09:51Z →
10:14Z, with three suites and two loop runs live beside it, `evidence/concurrent-harnesses.log`) failed 5 of 211;
run 3 (the gate re-run, 10:20Z → 10:38Z, `evidence/gate.log`) failed 1 of 211. Every red is attributed in
`evidence/whole-set-runs.log`: `run#aWriterTheRunCanPlaceBlocksTheItem` fails isolated here *and* on the pristine base
(`evidence/isolated-reds.log`, `evidence/baseline-reds.log` — issue #288, whose fix is not in this base),
`run#prescreenAnnouncesItselfBeforeItReturns` likewise (issue #287), and the three reds that run 2 alone carried
(`run#childOutlastingTheWallClockIsBlocked`, `run#theBaseAdvancesWithEveryMerge`,
`run#staleTestResultsAreNotThisChildsGate`, `run#doesNotCloseOnAMergeTheBaseCannotTake`) pass isolated on this tree —
the load-dependent red the item describes. The failing set of one suite, one tree and one base moved 2 → 5 → 1, and
the intersection is another item's stable red: that is the item's measurement, reproduced here.

**The red test.** `driver#aSecondSuiteInOneRepositoryIsRefused` (`tools/loop-cases.sh:6775`, added by this change)
starts a whole suite of this repository — a nested driver over stub cases, run with the repository's own scratch
root — and then asks a second one to run there. On HEAD no driver takes a lock, so the case's first half never sees
one:

```
  the suite this case started never took the lock at …/.pi/tmp/.loop-selftest.lock
FAIL driver#aSecondSuiteInOneRepositoryIsRefused
loop-selftest: 1 of 1 cases failed
```

`evidence/red-HEAD.xml` carries the tally (`tests="1" failures="1"`), `evidence/red-HEAD.log` the transcript.

**Owning requirement.** `agent-issue-loop`, "One suite at a time runs in a repository" (ADDED by this delta). Its
neighbours are the two requirements that already own the self-test's own machinery — "A suite ended by a signal
removes its scratch tree" (`openspec/specs/agent-issue-loop/spec.md:909`) and "A suite ended by a signal ends the
case it was in, and its processes" (`openspec/specs/agent-issue-loop/spec.md:979`) — and the lease is the shape the
run's own lock already has, "One run at a time holds the repository"
(`openspec/specs/agent-issue-loop/spec.md:1520`), extended from a run to a suite.

**Owning guideline section.** `guidelines/Backlog.md`, section "9. Checks" — the paragraph that states what the
self-test selects and what a delta owes (`guidelines/Backlog.md:335`), whose new neighbour is the paragraph this
change adds.

## What Changes

- **`tools/loop-selftest.sh`** — a lease in the scratch root the tree resolved: `selftest_lock_take`
  (`tools/loop-selftest.sh:172`) creates `<scratch root>/.loop-selftest.lock` holding the pid that took it and the
  moment it did, refuses a live owner with exit 2 — the rule and the owner's own record named — and takes a lock
  over when its owner is not running any more; an unreadable owner is read again before it is read as "no owner".
  The lease is taken after the traps are installed and before the first case runs, and released last in `on_exit`
  (`tools/loop-selftest.sh:122`), which is also what removes the scratch tree the suite made at
  `tools/loop-selftest.sh:84` — a refused suite's tree goes with it. `selftest_runs_one_named_case`
  (`tools/loop-selftest.sh:166`) is the exemption: a listing (`--list`, `--case-files`) or exactly one named
  `--case` takes no lease, and `is_glob` moves up beside it (one definition, two readers — the driver already tells
  a single-case run from a selection with that test at `tools/loop-selftest.sh:449`).
- **`tools/loop-cases.sh`** — one case, `driver#aSecondSuiteInOneRepositoryIsRefused`, registered in
  `LOOP_SHELL_CASES` (`tools/loop-cases.sh:242`) and in the dispatch (`tools/loop-cases.sh:500`). It reads the
  repository's lock, starts the suite that holds it when this invocation is one named case and holds nothing,
  refuses the second suite, and covers the exemption and the lease beside it.
- **`tools/loop-mutations.sh`** — four records, one per invariant, in a new `55.x` block: the lock is taken at all
  (`55.1`), a pattern is a suite while one named case is not (`55.2`), the release leaves the repository free for
  the suite after it (`55.3`), and the lease takes a lock over when its owner is gone (`55.4`).
- **`guidelines/Backlog.md`**, section "9. Checks" — one paragraph: what the lease is, what it refuses, what it
  exempts, and the measurement behind it (#295).
- **the spec `agent-issue-loop`** — one ADDED requirement, "One suite at a time runs in a repository", with its
  three scenarios, each naming the one case above. "Repository" is the tree the driver was started in: the
  requirement's second sentence names `--list`, `--case-files` and one named `--case`, and the worktree pair above
  is deliberately outside it.

surface: 1 case added, 0 rewritten, 0 fixtures realigned (`bash tools/loop-selftest.sh --list | grep -c .` → **211**,
`git show HEAD:tools/loop-cases.sh | grep -cE "^  '[a-z-]+#[A-Za-z0-9_]+'$"` → **210**); 4 mutation records added, 0
checks rewritten (`bash tools/loop-mutations.sh --list | grep -c .` → **137**, `git show
HEAD:tools/loop-mutations.sh | grep -cE "^  '[0-9]"` → **133**). The new block is `55.x` because `54.x` is taken by
the gate's own records and a repeated label makes `--only` select two records at once. No recorded count derives
from either set — the driver counts the cases it declares and the harness counts the records it reads — so this
delta realigns no number.
