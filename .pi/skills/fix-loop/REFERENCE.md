# fix-loop — reference

Reference material for the `fix-loop` skill. **The rules live in `SKILL.md`**; nothing here is a rule, and a
child running one iteration never needs this file. `SKILL.md` names the section below where a run needs it.

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

The worktree is **kept** at the end of a run — it is the next run's warm build — and removed only by
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
- The worktree stays after the run **by design** (it is the next run's warm build), and the branches stay
  until the run's commits are merged. Delete a branch then, not before: the run's report names the branches
  it created.

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
