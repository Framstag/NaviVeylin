# One unanswered item read is not the end of a run

## Why

On the base `fc9e0eb0`, `tools/loop/run.sh:1878` is the run's per-item read — `loop_issue_json "$issue" --comments` —
and its failure arm (`:1878–1882`) recorded `stop_run "gh could not read #$issue — the tracker did not answer"` and
`break`. That condition was real on the base: one read the tracker does not answer for ends the run, and everything
the run had left is unspent. Measured on the run of 2026-11-06
(`.pi/issue-loop/runs/2026-11-06T21:19:06Z.json`): it stopped at `2026-11-07T03:31:27Z` with `reason: gh could not
read #754 — the tracker did not answer`, 108 of its 480 minutes and 69 of its 80 item slots unspent (480 min budget −
372 min elapsed, 21:19:06Z → 03:31:27Z, = 108 min; 80 item slots − 11 recorded = 69), 7 items closed and no other
stop condition holding.

**Root cause, in one sentence:** the arm that reads the item a run is about to work treats the read's failure as a
verdict about the whole run — a `stop_run` — where the pre-screen, reading the same seam's same exit status for the
same candidates, records the item as *unknown* and carries on.

The item's own cause does not hold on this base, and the probe is what says so. It reports that "the loop does not
source that file" and that `loop_issue_json` (`tools/loop/lib.sh:491–505`) calls the CLI directly, so only the paths
under `tools/issues/*.sh` reach the seam. The seam's `gh` function (`tools/issues/lib.sh:94–125`) is a shell function
defined by `tools/issues/lib.sh`, and `tools/loop/lib.sh:35–36` sources that file at the top of every loop script, so
the loop's own call reaches it: `loop_issue_json` runs `gh issue view "$1" --json "$fields"` (`tools/loop/lib.sh:500`)
against the function, which retries a transport failure three times at 5/20/60 s and then reports
`tracker-unreachable` with exit 75, and hands a refusal the tracker produced itself back at once. Probed on this base with the stub CLI failing every `issue view` transiently
(`evidence/probe-seam.sh`'s copy runs `loop_issue_json 21 --comments`):

```
cli=gh
status=75
attempts=4
stderr-last=tracker-unreachable: gh issue did not answer after 4 attempt(s) — Post "https://api.github.com/graphql": dial tcp 20.250.119.72:443: i/o timeout
stdout-bytes=0
```

Four attempts and exit 75 are the seam's own numbers, so the failure the probe produced had already been retried and
reported: what reaches the run's read (`tools/loop/run.sh:1891` in this tree) is the failure the seam handed back — a
refusal it passes through without retrying, or a transport failure it retried and reported as `tracker-unreachable`.
What the run lacks is the step after the seam: the pre-screen's rule
for the same unknown (`tools/loop/prescreen.sh:211–221`, issue #231) applied to the read that happens once per item.

**The red case, on the base `fc9e0eb0`** — one `bash tools/loop-selftest.sh --case
'run#anUnreadableItemIsNotTheEndOfTheRun'` run, 2026-11-07T22:07:39Z–22:07:43Z, which drives a two-item scenario
through `run.sh` with the stub `gh` refusing the run's own read (the one that asks for `comments`) and asserts what the
run recorded:

```
FAIL run#anUnreadableItemIsNotTheEndOfTheRun
  the item the tracker did not answer for is recorded rather than left unrecorded: expected '21', got 'none'
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.log` is that run and `evidence/red-on-HEAD.xml` its tally (`tests=1 failures=1 errors=0`),
transcribed into a `testsuite` from the runner's own summary — the loop's shell runner writes no JUnit XML.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md:1313`, "A tracker failure the transport caused is
not an answer" — it states that the seam reports an outage as *unknown* "so a caller can tell an outage from an answer
instead of reading one as the other", and the run's per-item read was the caller that did not act on the distinction.
This change adds one requirement beside it.

**Owning guideline section:** `guidelines/Backlog.recipes.md` section 7d "Refusals, records and the landing" (`:719`) — its
paragraph on records derived from one list (`:785–790`) states that the run's refusal-streak stop condition and the
report's tallies are all read from the same item records, which is the rule an unreadable item's record now joins;
the index entry is `guidelines/Backlog.md` section 7d (`:302`).

surface: 1 arm rewritten (`tools/loop/run.sh:1890–1899`, the read and its record), 1 case added
(`run#anUnreadableItemIsNotTheEndOfTheRun`), 1 requirement added to `openspec/specs/agent-issue-loop/spec.md`, 1
paragraph added to `guidelines/Backlog.recipes.md` section 7d, 0 cases rewritten, 0 fixtures realigned — counted with
`./tools/look.sh 'gh could not read'` (11 matches in 10 files, the 2 live ones in `tools/loop/run.sh` being the pair
this change replaces), `./tools/look.sh 'loop_issue_json'` (15 matches in 11 files: the function at
`tools/loop/lib.sh:491`, the pre-screen's two calls and this one in `tools/loop/run.sh`), and
`bash tools/loop-selftest.sh --list | wc -l` (`369` before the change, `370` after).

## What Changes

1. **The per-item read's failure records the item and continues.** `tools/loop/run.sh:1890–1899` keeps the read and replaces
   its `stop_run`/`break` with a record — `loop_state done "$issue" --result blocked --verdict unreadable`, carrying the
   CLI's own words in the note — and a `continue`, so the queue moves to the next candidate. The console line names the
   item and what the CLI said, as the pre-screen's does.
2. **A run whose tracker answers nothing still ends**, through the machinery that already exists: the refusal streak
   `state.sh next` reads is a run of that many blocked records in a row — the count the engine is given
   (`LOOP_REFUSAL_STREAK_STOP`, `tools/loop/loop.conf:83`; 4 in this tree), not a pair (issue #804) — so an outage
   costs that many item records and stops the run with the CLI's words on all of them, instead of costing one socket
   the rest of the budget.
3. **One case asserts both halves** — that the unreadable item is recorded under the word the pre-screen writes for the
   same unknown, and that the run ends on its own item cap rather than on that read.
4. **`guidelines/Backlog.recipes.md` section 7d gains the paragraph** that says what a run does with an item the tracker does
   not answer for, and that the refusal streak is what ends the run when every read fails.
5. **The shipped capability gains one requirement and one scenario**, whose case is 3.

## What this does not do

- It does not touch the seam: the retries, the backoff and exit 75 stay as they are, and `lib#onlyTheSeamCallsTheRealCli`
  is unchanged, because no call site begins or stops reaching the seam here (the probe above shows the read already did).
- It does not change what a stop records: the stop a dead tracker produces is the refusal streak's, whose reason is
  `state.sh next`'s own text. The item's record and the console line name the outage; a fourth stop mechanism is not
  invented for it.
- It does not give the unreadable item an escalation: nothing is written on the tracker for it, because nothing was
  decided about it — the record is the run's own and a later run's pre-screen re-reads the item from scratch.
- It does not make the read a third time before giving up: 4 attempts are the seam's contract, and a second retry loop
  beside it would be the second seam the rule forbids.
