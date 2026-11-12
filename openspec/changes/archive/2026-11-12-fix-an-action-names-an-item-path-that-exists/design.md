# Design — the item root is the loop root's, not the state file's directory

## Root cause

`report.sh:631` names the directory the state file sits in, and the two arms that print an item path append `items/` to
it:

```
# report.sh:631 (HEAD ff3a402a)
state_dir=$(dirname "$state_file")

# report.sh:687 — an item whose gate never completed
… "re-run the gate for #$issue … transcript $state_dir/items/$issue/worker-report.md"

# report.sh:701 — an item the run's bound cut
… "resume #$issue … transcript $state_dir/items/$issue"
```

`state_dir` is the *loop* directory only while the state is the one in place (`<project>/.pi/issue-loop/run.json`,
`lib.sh:154`). The archive puts the same state one directory lower — `state.sh:183-199` copies it to
`"$RUNS_DIR/$started.json"`, and `RUNS_DIR` defaults to `<state dir>/runs` (`state.sh:109`) — so for the only state a
*f*inished run can be reported on, the same expression reads `<project>/.pi/issue-loop/runs/items/<n>`. The item
directories are written beside the state in place (`loop_leftover_patch_of`, `lib.sh:593`,
`$(dirname "$LOOP_STATE_FILE")/items/$1`), never under `runs/`, so the path the archived report prints is one the tree
does not hold.

The second defect is on the resume line: `report.sh:701` appends `${patch:+ · its work is kept at $patch}` to the item's
note, and `run.sh:3433` (and `:3499`) already appended that sentence to the same note when the state recorded the
patch, so a cut item's line prints it twice.

## The fix

Two expressions, and the existing arms keep their text:

| what | HEAD | now |
|---|---|---|
| the item root | `state_dir=$(dirname "$state_file")`, used as `$state_dir/items` | `$state_dir/items` when that directory exists, else `$(dirname "$state_dir")/items` (`report.sh:638-639`, named `item_root` in both arms, `:695` and `:717`) |
| the resume line's patch phrase | `${patch:+ · its work is kept at $patch}` | appended only when the note does not already name the path |

The candidate test is the tree's own answer and not a guess about which shape the caller used: `--state` takes an
absolute path, a path relative to the project (`report.sh:74`) or the state in place, and a derivation that asked *is
this the archive?* would have to know `LOOP_RUNS_DIR`, which is a value the profile may move (`state.sh:107-111`) —
whereas an `items` directory that exists beside the state, or one level above it, is the directory the run itself
wrote. A tree that holds neither (a state copied into an empty fixture) keeps HEAD's answer, `$state_dir/items`, so
nothing about a shape nobody has measured changes.

The phrase is appended only where the note lacks it, and not dropped outright: a state written by hand, or by a tree
older than #674, can carry `--leftover` with a note that never named the patch, and the resume line is the only place
that caller learns where the kept work is.

## The item's open question

The item leaves open whether the arms should name the item *directory* or the file inside it. The red case answers it:
each arm names what its reader opens — the gate arm `worker-report.md`, because the reader reads a transcript, and the
resume arm the item's directory, because the next run is handed the item and reads several files of it — and both are
paths the tree holds once the root is derived correctly. That is the shape `report.sh` already has, so this change
alters no line's wording beyond the derivation and the phrase.

## Blast radius

`tools/loop/report.sh` (the derivation, two arms, one phrase, the comments above them); `tools/loop/tests/loop-cases.sh`
(one case, added to the case list and the dispatcher); `tools/loop/tests/loop-mutations.sh` (two records);
`guidelines/Backlog.recipes.md:1410-1421` "a named action list"; the change's spec delta (one requirement, one
scenario). No compiled file, no Gradle suite, no build — the gate for this delta is the loop's own suite over the paths
it changes.

## Rollback

Restore `state_dir/items` in the two arms and the unconditional `${patch:+ …}` suffix, and the new case returns to red
on its archived report (`…/runs/items/21` does not exist). Nothing else in the tree depends on the derivation, so the
revert leaves no residue.

## Alternatives the evidence eliminated

- **Read `LOOP_RUNS_DIR` and subtract it from `$state_file`** — eliminated: it is a profile value a project may move
  (`state.sh:107-111`), so the report would have to re-derive `state.sh`'s three-case resolution to learn a fact the
  tree states directly — whether an `items` directory is where the state sits or one level above it.
- **Name the item from git** (`git -C "$LOOP_ROOT" …`) — eliminated: the item directories are not tracked, so git
  cannot answer where they are; the report's own comment (`report.sh:619-620`) states that every action line comes from
  the state or from git, and this path is the state's.
- **Drop the resume line's patch phrase entirely** — eliminated: a record written before #674, or by hand, carries no
  sentence in its note, and the phrase is where such a caller learns the patch's path.

## Open decisions

none
