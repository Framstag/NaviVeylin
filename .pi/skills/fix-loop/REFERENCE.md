# fix-loop — reference

Measured facts about the budgeted fix loop (`tools/loop/run.sh`). **The rules live in the spec
`agent-issue-loop` and in the prompt skeletons** (`tools/loop/prompts/worker.md`, `reviewer.md`); nothing here
is a rule, and a child running one iteration never needs this file. `SKILL.md` says where everything else is.

## Measured costs (plan with these numbers)

| outcome | cost | notes |
|---|---|---|
| archived change | 40–65 min | gate 5–7 min, review 5–10 min, archive 5 min. §138 took 65 min because it needed review round 2 |
| `gated-out` skip | 10–15 min **with** the cheap-first rule, 20–25 min without | the gate is a few greps and one file read. Never analyse before it passes |
| cold loop worktree, first build | 11 min | one-time; after that a single test class is 1–3 min |
| forced both-flavor gate | 4m42s–6m41s | **it overwrites the previous run's XML evidence** — copy the XMLs you cite into the change's `evidence/` |
| one item, measured 2026-10-19 (#126) | 100 min | worker 83 min plus three review rounds — the item the constant floor could not pay for |

The floor `state.sh next` refuses by is these numbers, not a constant (issue #206): the median of the run's own
finished wall times × **1.5**, rounded up, with `LOOP_ITEM_FLOOR_MINUTES` as its lower bound. The 1.5 is the
spread the table above already shows — 65/45 inside the change range, 100/65 for the #126 item — so a run whose
items are typical gets a floor just above them, a run of skips keeps the configured minimum, and one expensive
item among cheap ones does not move the median at all.

The clock is not the only budget an item spends. Measured 2026-10-19 (issue #209) on one host: item #126's gate
found **194 MB free RAM and 18 GB of swap** and spent **30 min** inside `:app:kspMobileDebugUnitTestKotlin`, the
same 5 680 tests took **3m47s** on the same tree when the machine was quiet, and a `free` taken during another
run read **467 MB free / 2 499 MB available** with 13 915 MB of 37 887 MB swap used — Firefox 7 600 MB, three
VS Code language-server JVMs 2 282 MB, Gradle/Kotlin daemons 1 625 MB, `cpptools` 407 MB, the loop's own
children 649 MB. The loop was the smallest consumer and the one that failed, and its `wallSeconds` measured the
desktop session. The run therefore reads `MemAvailable` (the file `LOOP_MEMINFO_PATH` names, `/proc/meminfo`
here) before an item's first child and compares it with `LOOP_MIN_FREE_MIB` (1024 MiB): below the floor the item
is recorded `blocked` with the measurement and the run stops — a shorter `wallSeconds` on a starved host is not a
cheaper item. `MemAvailable`, not `MemFree`, is the number: on the host this was measured, `free` read
8 383 MiB free against 11 505 MiB available on the same moment, so `MemFree` would refuse a host that can pay.

The measurements behind the rules (first run, 2026-10-09: 12 candidates, 3 archived, 9 gated out; a second pass
over all 31 `class: bug` entries → `eligible: none`; a later run: 0 closed, 32 skipped):

- the red-on-HEAD rule: all 3 archived changes had one, e.g. `42.67 dp < 48 dp`;
- review: 4 rounds, both FAILs about claims and pointers, never about the fix;
- condition 3 rejected 5 of the 9 gated candidates (an owner decision, unspecified semantics, competing fixes);
- condition 5: each of the 3 archived changes touched **one** production file;
- the gate: `mobile 1752/0 · automotive 1752/0 · 20 executed · FROM-CACHE 0`;
- without the pre-screen, 5 of 8 candidates were ineligible; each rejection took ~10 min with a citation,
  against a ~40 min analysis;
- skip verdicts of that second pass: `needs-device 8 · needs-decision 7 · in-flight 7 · refuted 4 · too-wide 2
  · stale 2 · unspecifiable 1 · needs-diagnosis 1` — **not one** died on the item's class, which is why the
  class is a ranking input and not a gate — and so is the category beside it: the profile's `firstCategories`
  lead the queue in the classes `firstCategoryClasses` names (harness bugs), while their other classes are
  mixed in one after every `firstCategoryEvery` item and the class order decides the rest.

## The run's state, and the state it replaces

`.pi/issue-loop/run.json` is the run's only record: its budget, its queue, the items it finished with their
verdicts and metrics, and the stop reason `next` recorded. `state.sh init` writes over it, so it keeps the file
it is about to replace first — copied to `runs/<run.startedAt>.json` beside the state file (`LOOP_RUNS_DIR` in
`tools/loop/loop.conf` moves that directory) — and announces on stderr, without refusing, when the state it
replaces never recorded a stop reason (a crashed or killed run). Measured 2026-10-20: a later 30-minute run had
re-initialised the file, and the 100-item / 480-minute run under review had to be reconstructed from the console
transcript and `.pi/issue-loop/items/*` instead of from its own state.

Reporting on a kept run: `tools/loop/report.sh --state .pi/issue-loop/runs/<startedAt>.json` (the default is
still the run in place). A state whose window closed without a stop reads `budget left unknown: these numbers
belong to another run …`, never a negative budget left (issue #207).

## What a kept worktree keeps, and what the loop leaves on disk

Keeping the tree is a decision about the *checkout*: the next run inherits the repository, the submodule and the
git state, and skips the measured 11-minute cold build. Carrying the *build output* of the previous run's gate
is not part of that bargain — no item reads it, every build regenerates it, and it is the largest thing on the
developer host's disk. Measured 2026-10-20 (issue #210), on a root filesystem at **88 %** (391 GB of 471 GB):
the kept loop worktree **3.2 GB**, of which one module's build output **1.9 GB**; `loop-selftest.*` scratch trees
of killed suites **90 MB** across four trees.

```bash
tools/loop/loop-clean.sh             # report only
tools/loop/loop-clean.sh --apply     # remove; safe to repeat
```

The rules the run follows here — the kept tree carries sources and not build output, a suite ended by a signal
cleans up after itself and ends what it started, nothing outlives the layer that started it, and the leftover
report names the processes and not only the trees — are the spec `agent-issue-loop`'s, under *A kept worktree
carries the sources, not the build output*, *A suite ended by a signal removes its scratch tree*, *A suite ended
by a signal ends the case it was in, and its processes*, *Nothing outlives the layer that started it* and *One
command reports and removes what the loop left*.

## Field notes

Eleven measured failure modes, recorded as observations; the rule each one belongs to is the spec's or the
prompt's, not this file's.

1. **Fixing before reproducing** — the red-on-HEAD case is the whole correctness argument.
2. **"The fix is obvious" is not uniqueness** — two mechanisms producing the symptom means `needs-diagnosis`.
3. **Asking the user inside the loop** — the budget is arguments, not a prompt.
4. **The orchestrator reading artifacts** — context exhaustion is the failure mode the fresh-process design removes.
5. **Two writers in one tree**, including the orchestrator's own issue bookkeeping during a child's run.
6. **Archiving on the writer's word** — the review child is read-only and independent on purpose.
7. **Exceeding the budget because "one more"** — `tools/loop/state.sh next` is the authority.
8. **A tall, convincing artifact with one false pointer** — a change's own review failed twice on this alone.
9. **Reading a round-2 `FAIL` as "the fix is wrong"** — separate the classes.
10. **Starting an iteration the clock cannot finish** — the pre-screen and the measured costs above tell a
    15-minute skip from a 65-minute change.
11. **Starting an iteration the machine cannot finish** — a starved host turns a 3m47s gate into 30 minutes in one
    Gradle task, and the run's own numbers then measure the desktop session.

## What changed when the loop became a script

Recorded so a reader of the old name is not surprised. The skill kept its name; the mechanics it points at moved,
and the rules moved into the spec `agent-issue-loop` and the prompt skeletons.

| was | is |
|---|---|
| the session was the entry, and the budget came from an `ask_user` | `tools/loop/run.sh` with flags, whose defaults are recorded in `tools/loop/loop.conf` |
| the orchestrator held the state in `.pi/bugfix-loop/run.json` and drove `scripts/loop-state.sh` | `tools/loop/state.sh` owns `.pi/issue-loop/run.json` (the old file is left alone so an open run is not orphaned) |
| the orchestrator wrote labels and the item's bookkeeping itself | `tools/loop/labels.sh` and `tools/issues/update.sh` / `close.sh` do it, and the run never writes the authorisation label |
| the phase contract lived in the skill | the phase contract is `tools/loop/prompts/worker.md` / `reviewer.md`; the rules are the spec's |
| branch, worktree and commit were steps a person or a session ran | `tools/loop/worktree.sh` creates the worktree, the branch and the two commits; nothing pushes unless `--push yes` |

**Retired 2026-10-22.** `scripts/loop-state.sh` — a forwarder that translated the pre-`agent-issue-loop` command
names, the old `LOOP_STATE` override and a `§N` id into `tools/loop/state.sh` — and the self-test that covered
it (`scripts/selftest.sh`) are removed. Nothing in the repository called them; they existed for prose written
before 2026-10-17. The old state file `.pi/bugfix-loop/run.json` is still left alone by `state.sh`, so an open
run from the prose era is not orphaned.

**Reduced 2026-10-22.** This skill was a manual (428 lines) that restated the spec and the prompt skeletons. It
is now a pointer table plus the one script it owns (`scripts/evidence-check.sh`); the rules it restated live in
the spec `agent-issue-loop` and in `tools/loop/prompts/worker.md`.
