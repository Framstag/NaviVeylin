# fix-loop — reference

Reference material for the `fix-loop` skill. **The rules live in `SKILL.md`**; nothing here is a rule, and a
child running one iteration never needs this file. `SKILL.md` names the section below where a run needs it.

## Running the loop in a worktree (recipe)

Run the loop in its own worktree whenever the main tree carries another change's WIP; otherwise the loop's
`git add -A` and the OpenSpec archive mix with it.

```bash
# 1. policy: a sibling path outside the repo is refused, so put the worktree inside the gitignored .pi/
cd <repo> && mkdir -p .pi/worktrees
git worktree add .pi/worktrees/loop -b fix-loop-<date>
W=.pi/worktrees/loop

# 2. the submodule must be checked out (its objects are already in .git/modules, so this is offline)
git -C $W submodule update --init app/src/main/cpp/libosmscout

# 3. machine-local inputs the worktree does not inherit
cp local.prop* $W/                      # SDK/NDK/Java paths; `local.prop*` also dodges the path-read policy
export VCPKG_ROOT=<repo>/vcpkg          # do NOT symlink it: a symlink named vcpkg is untracked and pollutes status

# 4. the gitignored skills the worktree does not have (open changes and the openspec-* skills are local WIP)
cp -a .pi/skills/openspec-* $W/.pi/skills/
mkdir -p $W/.pi/skills && cp -a .pi/skills/fix-loop $W/.pi/skills/

# 5. give the children cwd=$W; the state file then resolves to $W/.pi/bugfix-loop/run.json automatically
```

Four facts shape verification in a worktree:

- `openspec/changes/*/` is **gitignored** (only `archive/` is versioned), so the worktree sees **no** in-flight
  changes. Verify an `in-flight` claim against the **main tree's** `openspec list --json`. Archiving is what
  makes a change's artifacts versioned on the loop branch.
- The worktree's `TODO.md` is the branch's copy. When the main tree has another change's WIP in the same file,
  **allocate new §ids from the max over both trees** (measured: main's WIP owned §155–§164 while the loop filed
  §154–§158, so `cleanup-todo` had to renumber the loop's five to §165–§169).
- The loop owns the branch; the orchestrator owns the skill in the **repo** copy. Edit the repo copy, copy it
  into the worktree (step 4), and commit it with the next iteration commit — otherwise the branch's children
  read stale rules.
- Keep the worktree after the run for the next loop. Remove it with `git worktree remove`, and delete the
  branch only when the run's commits are merged.

## Measured costs (plan with these numbers)

| outcome | cost | notes |
|---|---|---|
| archived change | 40–65 min | gate 5–7 min, review 5–10 min, Phase F 5 min. §138 took 65 min because it needed review round 2 |
| `gated-out` skip | 10–15 min **with** the cheap-first rule, 20–25 min without | the gate is a few greps and one file read. Never analyse before it passes |
| cold loop worktree, first build | 11 min | one-time; after that a single test class is 1–3 min |
| forced both-flavor gate | 4m42s–6m41s | **it overwrites the previous run's XML evidence** — `SKILL.md`, invariant 1 |

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
  class is a ranking input and not a gate.

## Field notes

Each line names the section of `SKILL.md` that owns the rule; none of them is a new rule.

1. **Fixing before reproducing** — the red-on-HEAD case is the whole correctness argument (Phase A).
2. **"The fix is obvious" is not uniqueness** — two mechanisms producing the symptom means `needs-diagnosis`
   (Phase B, condition 2).
3. **Asking the user inside the loop** — only the run-start budget is a prompt (Start, Phase B).
4. **The orchestrator reading artifacts** — context exhaustion is the failure mode this design removes
   (Roles).
5. **Two writers in one tree**, including the orchestrator's own `TODO.md` bookkeeping during a child's run
   (invariant 2).
6. **Archiving on the writer's word** — the review child is read-only and independent on purpose (Phase E).
7. **Exceeding the budget because "one more"** — `loop-state.sh next` is the authority (invariant 3).
8. **A tall, convincing artifact with one false pointer** — a change's own review failed twice on this alone
   (invariant 1).
9. **Reading a round-2 `FAIL` as "the fix is wrong"** — separate the classes (Phase E).
10. **Starting an iteration the clock cannot finish** — the pre-screen and the measured costs above tell a
    15-minute skip from a 65-minute change (Stop conditions).
