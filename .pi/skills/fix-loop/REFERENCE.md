# fix-loop — reference

Reference material for the `fix-loop` skill. **The rules live in `SKILL.md`**; nothing here is a rule, and a
child running one iteration never needs this file. `SKILL.md` names the section below where a run needs it.

## The run's state, and the state it replaces

`.pi/issue-loop/run.json` is the run's only record: its budget, its queue, the items it finished with their
verdicts and metrics, and the stop reason `next` recorded. `state.sh init` writes over it, so it keeps the
file it is about to replace first — copied to `runs/<run.startedAt>.json` beside the state file (`LOOP_RUNS_DIR`
in `tools/loop/loop.conf` moves that directory) — and announces on stderr, without refusing, when the state it
replaces never recorded a stop reason (a crashed or killed run). Measured 2026-10-20: a later 30-minute run had
re-initialised the file, and the 100-item / 480-minute run under review had to be reconstructed from the
console transcript and `.pi/issue-loop/items/*` instead of from its own state.

Reporting on a kept run: `tools/loop/report.sh --state .pi/issue-loop/runs/<startedAt>.json` (the default is
still the run in place). The budget line subtracts the spend of the run whose state it reads — to the stop that
run recorded once it ended, and to the clock only while its window is open — so a state whose window closed
without a stop reads `budget left unknown: these numbers belong to another run — its window closed at <time>
and it recorded no stop`, never a negative budget left (issue #207).

## Running the loop in a worktree (the wrapper owns it)

The loop runs in its own worktree, so the run's commits and the OpenSpec archive never mix with another
change's work in progress. **`tools/loop/worktree.sh create` does all five steps below** — they are recorded
here because the facts they produce shape verification, not because anyone runs them by hand:

```bash
# the wrapper's own recipe, one command from the repository root — it creates and seeds the worktree itself
tools/loop/run.sh --items N --minutes M
#
# what it does, in order (worktree.sh, `cmd_create`):
# 1. git worktree add .pi/worktrees/loop -b loop-<date> <base>   inside the gitignored .pi/
# 2. git submodule update --init app/src/main/cpp/libosmscout    offline; objects already in .git/modules
# 3. cp local.prop* (SDK/NDK/Java paths) and write .pi/loop-env.sh exporting VCPKG_ROOT
#    (never a symlink: a symlink named `vcpkg` is untracked and pollutes `git status`)
# 4. cp the gitignored skills (openspec-* and this one) into $W/.pi/skills/
# 5. the children are started with cwd=<the project> and told $W as their change root, and their state is
#    the project's .pi/issue-loop/run.json
```

The worktree is **kept** at the end of a run — it is the next run's warm checkout — and removed only by
`--clean-worktree`. One branch per item is created from the run's recorded base
(`fix/issue-<N>-<slug>`), and the wrapper refuses the next item when `main` has advanced past that base.

Five facts shape verification in a worktree:

- `openspec/changes/*/` is **gitignored** (only `archive/` is versioned), so the worktree sees **no** in-flight
  changes. Verify an `in-flight` claim against the **main tree's** `openspec list --json`. Archiving is what
  makes a change's artifacts versioned on the loop branch.
- The tracker is not in the worktree: issues live in the repository, and every write goes through
  `tools/issues/*.sh` against the **one** repository, so two trees cannot allocate the same identity — the
  §id collision that `cleanup-todo` had to renumber five entries for (measured: §154–§158 against
  §155–§164) is gone, because a filed issue's number is the tracker's.
- The loop owns the branch; the wrapper owns the worktree, and the seed step copies the skill in at the start
  of every run — so an edit to the skill takes effect on the next run, never mid-run (`SKILL.md`, invariant 2).
- A child process is started **in the project, not in the worktree** (issue #169, measured 2026-10-18): a
  fresh process resolves the project's own tool policy at the directory it starts in, and the worktree,
  the work packages and the run's staging directory all lie inside that directory — a child started in the
  worktree could reach none of them. The tree it changes is the worktree, and its prompt names it. The run
  reads the project's own tracked tree around every child, so a write left outside the worktree blocks the
  item instead of being committed.
- The worktree stays after the run **by design** (it is the next run's warm checkout), and the branches stay
  until the run's commits are merged. Delete a branch then, not before: the run's report names the branches
  it created.

## What a kept worktree keeps, and what the loop leaves on disk

Keeping the tree is a decision about the *checkout*: the next run inherits the repository, the submodule and
the git state, and skips the measured 11-minute cold build (`Measured costs`). Carrying the *build output* of
the previous run's gate is not part of that bargain — no item reads it, every build regenerates it, and it is
the largest thing on the developer host's disk.

Measured 2026-10-20 (issue #210), on a root filesystem at **88 %** (391 GB of 471 GB):

| what | size | where |
|---|---|---|
| the kept loop worktree | **3.2 GB** | `.pi/worktrees/loop` |
| of which one module's build output | **1.9 GB** | `.pi/worktrees/loop/app/build` |
| `loop-selftest.*` scratch trees of killed suites | **90 MB** across four trees | `.pi/tmp` |

Three facts follow, and each has one owner:

- `tools/loop/run.sh`'s end step (`worktree.sh strip-scratch`) removes the module build directories the
  project records in `LOOP_WORKTREE_SCRATCH_PATHS` from a tree it keeps. The list is configuration because the
  directories are the project's (`tools/check-portability.sh` reads each as a value no engine may name). The
  removal stays inside `$LOOP_WORKTREE`, skips a recorded path that is a symlink, skips one whose directory
  resolves outside the tree, and skips one git tracks. It does not run under `--clean-worktree` (the tree is
  removed whole) or under `--reset-worktree` (the caller named the tree's contents as the thing to repair).
- `tools/loop-selftest.sh` traps `HUP`, `INT` and `TERM` beside `EXIT`, so an interrupted suite removes its
  scratch tree like a failing one. `SIGKILL` cannot be trapped; the tree it leaves is one of the leftovers
  below.
- `tools/loop/loop-clean.sh` is the one report of both. Without `--apply` it prints each path with its bytes
  and removes nothing; with `--apply` it removes exactly those. Its scratch-tree half is age-gated by
  `LOOP_TMP_STALE_MINUTES` (default 120) because only the age tells a killed suite's tree from one a live
  suite is writing into, and it is idempotent — a second run prints `nothing to remove` and exits 0.

```bash
tools/loop/loop-clean.sh             # report only
tools/loop/loop-clean.sh --apply     # remove; safe to repeat
```

## Measured costs (plan with these numbers)

| outcome | cost | notes |
|---|---|---|
| archived change | 40–65 min | gate 5–7 min, review 5–10 min, Phase F 5 min. §138 took 65 min because it needed review round 2 |
| `gated-out` skip | 10–15 min **with** the cheap-first rule, 20–25 min without | the gate is a few greps and one file read. Never analyse before it passes |
| cold loop worktree, first build | 11 min | one-time; after that a single test class is 1–3 min |
| forced both-flavor gate | 4m42s–6m41s | **it overwrites the previous run's XML evidence** — `SKILL.md`, invariant 1 |
| one item, measured 2026-10-19 (#126) | 100 min | worker 83 min plus three review rounds — the item the constant floor could not pay for |

The floor `state.sh next` refuses by is these numbers, not a constant (issue #206): the median of the run's
own finished wall times × **1.5**, rounded up, with `LOOP_ITEM_FLOOR_MINUTES` as its lower bound. The 1.5 is
the spread the table above already shows — 65/45 inside the change range, 100/65 for the #126 item — so a run
whose items are typical gets a floor just above them, a run of skips keeps the configured minimum, and one
expensive item among cheap ones does not move the median at all.

The clock is not the only budget an item spends. Measured 2026-10-19 (issue #209) on one host: item #126's gate
found **194 MB free RAM and 18 GB of swap** and spent **30 min** inside `:app:kspMobileDebugUnitTestKotlin`, the
same 5 680 tests took **3m47s** on the same tree when the machine was quiet, and a `free` taken during another
run read **467 MB free / 2 499 MB available** with 13 915 MB of 37 887 MB swap used — Firefox 7 600 MB, three
VS Code language-server JVMs 2 282 MB, Gradle/Kotlin daemons 1 625 MB, `cpptools` 407 MB, the loop's own
children 649 MB. The loop was the smallest consumer and the one that failed, and its `wallSeconds` measured the
desktop session. The wrapper therefore reads `MemAvailable` (the file `LOOP_MEMINFO_PATH` names, `/proc/meminfo`
here) before an item's first child and compares it with `LOOP_MIN_FREE_MIB` (1024 MiB): below the floor the item
is recorded `blocked` with the measurement and the run stops — a shorter `wallSeconds` on a starved host is not
a cheaper item. `MemAvailable`, not `MemFree`, is the number: on the host this was measured, `free` read
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
  lead the queue and the class order decides inside a group.

## Field notes

Each line names the section of `SKILL.md` that owns the rule; none of them is a new rule.

1. **Fixing before reproducing** — the red-on-HEAD case is the whole correctness argument (Phase A).
2. **"The fix is obvious" is not uniqueness** — two mechanisms producing the symptom means `needs-diagnosis`
   (Phase B, condition 2).
3. **Asking the user inside the loop** — only the run-start budget is a prompt (Start, Phase B).
4. **The orchestrator reading artifacts** — context exhaustion is the failure mode this design removes
   (Roles).
5. **Two writers in one tree**, including the orchestrator's own issue bookkeeping during a child's run
   (invariant 2).
6. **Archiving on the writer's word** — the review child is read-only and independent on purpose (Phase E).
7. **Exceeding the budget because "one more"** — `loop-state.sh next` is the authority (invariant 3).
8. **A tall, convincing artifact with one false pointer** — a change's own review failed twice on this alone
   (invariant 1).
9. **Reading a round-2 `FAIL` as "the fix is wrong"** — separate the classes (Phase E).
10. **Starting an iteration the clock cannot finish** — the pre-screen and the measured costs above tell a
    15-minute skip from a 65-minute change (Stop conditions).
11. **Starting an iteration the machine cannot finish** — a starved host turns a 3m47s gate into 30 minutes in
    one Gradle task, and the run's own numbers then measure the desktop session (Stop conditions, Budget
    authority).
