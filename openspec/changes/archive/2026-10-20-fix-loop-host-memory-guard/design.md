# Design — fix-loop-host-memory-guard

## The root cause, measured

`tools/loop/run.sh` read no host value at all. Its header listed the budget and the child's wall-clock
(lines 92-98 before this change); `state.sh next` decides from counts, a deadline, the queue and a floor
derived from the run's own finished items; and the item loop took that verdict and started the item's child at
once (`issue="$next_out"` → `worktree.sh start-item` → `run_child worker-prompt.md`). The one bound on a
gate-bearing phase was `LOOP_CHILD_MAX_SECONDS`, and a bound on the damage is not a reading of the host.

The two measurements of 2026-10-19 (issue #209) bound the error:

| where | the host's number | what the run recorded |
|---|---|---|
| #126's gate, on the same tree | 194 MB free, 18 GB swap | 30 min inside `:app:kspMobileDebugUnitTestKotlin`, then a retry that died |
| the same gate on a quiet host | — | 3m47s for the same 5 680 tests |
| a `free` during another run of the tree | 467 MB free / 2 499 MB available / 13 915 MB of 37 887 MB swap | a `wallSeconds` that measures the desktop session |

Attribution from the same `free` and `ps`: Firefox 7 600 MB, VS Code's Java language server 2 282 MB across
three JVMs, Gradle/Kotlin daemons 1 625 MB, VS Code UI 885 MB, `cpptools` 407 MB, the loop's own children
649 MB. The loop was the smallest consumer and the one whose work failed.

## The fix

```
free = MemAvailable(kB) / 1024          # from the file LOOP_MEMINFO_PATH names; nothing when it is absent
floor = LOOP_MIN_FREE_MIB               # recorded, default 1024 MiB, 0 disables the check
```

- **`MemAvailable`, not `MemFree`.** The issue's own line reads `467 MB free, 2 499 MB available`: `MemFree`
  counts only unallocated pages and ignores what the page cache will give back, so it would refuse a host that
  can pay for the item. On the host this change was written on, `free` reads 8 383 MiB free against
  11 505 MiB available — a factor of 1.4 on the *same* moment, which is the difference between "refuse" and
  "run" at the floor.
- **The read is per item, before `start-item`.** Memory is what the previous item's own gate may have spent,
  so the reading is taken again for every item; and it is taken before the branch, the in-progress label and
  the work package exist, so a starved item leaves no half-created state behind.
- **Below the floor: record and stop.** The item is written `blocked` with `the host has <n> MiB available,
  below the recorded floor of <floor> MiB (LOOP_MIN_FREE_MIB) — the item was not started`, printed, and the run
  records that shortfall as its stop reason (`state.sh stop`, printed as `stop: …`). The run does not take the
  next candidate: every item pays the same gate, so the next one cannot be productive either, and a run that
  kept trying would spend its clock proving the same fact once per item.
- **Not escalated.** The ordinary blocked path writes `agent/needs-decision` and `status/on-hold` and spends
  the decision cap. This block is not a decision: nobody has to judge the item, they have to free memory. It is
  recorded in the state and named in the report's `stop:` line, and the next run takes the item again.
- **`free-mib=unknown` when the reading cannot be taken.** The header always carries the field, so a reader can
  tell "no reading on this host" from "the run did not look".
- **The reading is injectable through the recorded keys.** The floor is `LOOP_MIN_FREE_MIB` and the path is
  `LOOP_MEMINFO_PATH` (default `/proc/meminfo`, a host fact and not a project value). The cases append the two
  keys to a copy of `loop.conf` and pass it as `LOOP_CONF` — the same seam every budget case already uses — so
  no case depends on how much memory the machine running the suite happens to have, and the absent-file case is
  a path that simply does not exist.

`run_child` is untouched: its start line, its 30-second progress lines and its `LOOP_CHILD_MAX_SECONDS` cap run
exactly as they did. Nothing about the run's threading changes — every line above runs in the one `run.sh`
process, between items, and there is no JVM, dispatcher or lifecycle involved.

## Alternatives the root-cause evidence eliminated

**A. Let the gate fail, and read the failure.** Eliminated by the measurement: it already did. The gate spent
30 minutes inside one Gradle task at 194 MB free, its retry died in another module, and the run learned what
kind of machine it was on only from the transcript afterwards. Worse, a slow or failed gate is not a signal the
run can act on: the failure of `:app:kspMobileDebugUnitTestKotlin` under memory pressure is indistinguishable
from a genuine compile error, so trusting it risks recording a false negative against a change that is correct.

**B. Warn only — print the free memory and start the item anyway.** Eliminated because the run is unattended:
nothing reads a warning before the damage. The issue's own expectation allows "a run that starts anyway is not
wrong *if it says so*", but saying so costs the item the same 30 minutes and still leaves the run's
`wallSeconds` measuring the desktop session — which is the half of the defect that makes two runs
incomparable. The header line is kept (it is what makes a run's numbers readable); the refusal is what keeps
them honest.

**C. Let the child check for itself.** Eliminated by the loop's own trust rule (`agent-issue-loop`, "The run
verifies rather than trusts"): the child is the thing whose report of its own budget cannot be trusted, and a
worker that has to be started in order to discover the host has already paid the item's first minutes. It also
moves a host fact into a prompt template, which the portability rule forbids.

**D. Defer the item instead of blocking it (`skipped` with a new verdict).** Eliminated by the vocabulary: the
profile's verdict set has no "host" value, so deferring needs a new project value in `tooling.json` and a new
branch in every reader of the verdict, and the queue would re-admit the same item on the same starved host —
the run would still have to stop. A recorded `blocked` plus a stop reason states the same fact with the fields
the state already has.

**E. Read again before the gate's results are trusted, or before every child.** Eliminated as a second reading
that changes no decision: the floor is compared once per item, and a re-read before the gate would only
re-price a decision already taken (and, taken after the worker's analysis, it would block an item whose work is
half done — the half-done branch the loop exists to avoid). Reading before *every* child is the same objection
one step later: the item has no result for "started but starved halfway", so the run could neither close nor
block it cleanly.

**F. Escalate the blocked item to the tracker like every other block.** Eliminated by the cost it imposes: the
escalation writes the decision label and the decision cap (`--max-decisions`) counts those labels, so a
starved night would stop the next run with "3 items already await a decision" — a condition no decision
answers, on items nobody has to judge. The owner's action is `free` and re-run, which the printed stop line
says.

**G. A flag on `run.sh` instead of a recorded key.** Eliminated by the rule the other budgets already follow
(`agent-issue-loop`, "The budget is arguments, not a prompt", and the portability rule): a value a run's
behaviour depends on is recorded in `loop.conf` and overridable by argument, and the two new values are facts
about the host, which is exactly the class `LOOP_CHILD_MAX_SECONDS` already belongs to. A flag would also have
to be repeated on every invocation of an unattended loop, where an omitted flag is the normal case.

## Risk assessment

- **The floor is a judgement (1024 MiB).** A host with 900 MiB free might still finish a small item, and one
  with 2 GB free might not finish a both-flavor gate. The number is recorded, not compiled in, so a project
  lowers or raises it — and `0` disables the check — and the refusal names both numbers it compared, so a
  wrong floor is visible in the first line it produces.
- **A point-in-time sample.** The reading bounds the start of an item, not its whole life: memory can fall
  after it. That is the pre-existing risk and the reason `LOOP_CHILD_MAX_SECONDS` still exists — the two bounds
  answer different questions.
- **A host with no procfs gets no guard.** A Mac or a bare container passes the check by design. Named, because
  it is the price of "a check that cannot be made is not a refusal".
- **The existing `run#` cases use the real floor of 1024 MiB.** They are unchanged, so a genuinely starved
  machine running the suite would now see them block — which is the guard doing its job, and the reason the
  three new cases drive their floor and their path through `LOOP_CONF` instead.
- **Portability.** Two new keys in `loop.conf`, no project value in an engine: `/proc/meminfo` is a host path
  and `MemAvailable` a kernel field, neither read from the profile.

## Verification

- Red on HEAD `fb713c1`, the three cases: `evidence/red/` — the first fails on behaviour (with an unreachable
  floor in the configuration, the child is started and the item closes), the other two on the header line the
  reading is named in.
- Green on the fix: `evidence/green/`, plus the two cases that already own this surface
  (`run#childOutlastingTheWallClockIsBlocked`, `run#stopReasonIsPrintedAndReported`) in
  `evidence/green/existing-surface-cases.txt`. The whole offline suite is the orchestrator's, run once on the
  merged tree.
- Falsification in the harness's own form: mutation `24.1` in `tools/loop-mutations.sh` replaces the guard's
  `if` with `if false`, and `bash tools/loop-mutations.sh --only 24.1` reports `RED 24.1` and
  `restore 24.1 … green again` — `evidence/green/mutation-24.1.txt`.
- Structural check instead of a build: the delta touches no compiled file (no Kotlin, Java, C++, Gradle,
  manifest or resource), so `guidelines/Build.md` §2's scope block applies and the check is `bash -n` on the
  two edited shell files plus the self-test gate for `tools/loop/**`.
- `bash tools/check-portability.sh --quiet`, `openspec validate fix-loop-host-memory-guard --strict` and
  `openspec doctor` — `evidence/green/validation.txt`.
- On-device: not applicable. No phone or car surface, no rendering, no log line and no coordinate.
