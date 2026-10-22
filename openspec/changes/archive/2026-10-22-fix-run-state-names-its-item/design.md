# design — the run's state names the item it is working on

## Root cause

`tools/loop/state.sh` grew a `start` command that writes `.current` (item, change, phase) and a `start` step,
and `run.sh` never called it: the field `state.sh show` prints, `cmd_step` derives every step's `issue` from and
`cmd_done` clears had no writer in the run. One missing call site, one mechanism — not two.

## The fix

One call, between the tracker's in-progress label and the item's first child
(`tools/loop/run.sh:834-845`):

```bash
in_progress="$issue"
change_hint="$(if [ "$class" = "$propose_class" ]; then printf add; else printf fix; fi)-$slug"
loop_state start "$issue" --change "$change_hint" >/dev/null || {
  printf 'run: could not record #%s as the item in progress\n' "$issue" >&2
  stop_run "the state could not name #$issue as the item in progress"
  break
}
```

- **Between children, not during one.** The write happens before `run_child`, which is where every other state
  write happens (`labels#writesOnlyBetweenChildren` holds the rule that no state is written while a child
  runs); the state file is under `.pi/`, which `project_state` never sees, so the child's outside-write check is
  unaffected.
- **Guarded like its neighbour.** The label write above it stops the run when it fails; a state that cannot
  name the item does the same, so no item is worked whose mark the state does not carry.
- **`change` is the name the run asks for.** The archived name is unknown at pick time — the worker files it
  and `done` records the change its report claims (`tools/loop/run.sh:881`, `.items[].change`). `.current.change`
  therefore carries `fix-<slug>` / `add-<slug>`: the same slug the branch carries
  (`fix/issue-<issue>-<slug>`, `tools/loop/worktree.sh:149`) and the name the worker prompt's own rule asks for
  (`tools/loop/prompts/worker.md:116-117`), where `<PROPOSE_CLASS>` is the same configuration value this line
  reads. The two names are already two facts the loop keeps apart; the state names the one that exists at pick
  time.

## Blast radius

| what | how |
|---|---|
| `tools/loop/state.sh` | untouched: the writer, the live view, the step's fallback and the clear all keep their behaviour |
| the state file's shape | one more `.steps` entry per item (`name: "start"`, `phase: "A"`), written by the writer that already existed; `.current` is non-null for the length of an item, which is what `cmd_done` already expects |
| every step's `issue` | `0` → the item's number, because the fallback `(.current.issue // 0)` now finds the field |
| the report | unchanged: `report.sh` reads `.items`, which `done` already attributed |
| `run_child`'s outside-write check | unchanged: the write is outside the child window and under a git-ignored path |
| the interruption path | unchanged: `loop_state done` on a signal clears a `.current` that is now set, which is the branch its `if` already had |

## Rollback

Delete the five lines above; nothing else refers to `.current`, and no schema, file or field was added. A state
file written by this change stays readable by the previous engine (an unknown-key-free, same-shaped document).

## Alternatives the evidence eliminated

- **The item's own directory as the place of the mark.** `state.sh show` — the command the run's own header
  names as the live view — prints `.current`, not a file under `.pi/issue-loop/items/<n>/`; a second reader for
  a second location is a second source of truth, and nothing in the item's directory is cleared when the item
  ends, so "in progress" would have no end.
- **Recording the change only once the worker claims it** (calling `start` after `step A`). Then `.current`
  would be absent for the whole worker phase — including the kill the item itself reports, whose victim was a
  review round, and every item whose worker was killed.
- **Making `--change` optional and recording `""`.** It would leave a schema field permanently empty (`.current`
  is cleared by `done`, so no run would ever write it), and the live view would print `#263  · phase A`.
- **A `state.sh change <name>` command to overwrite the mark later.** New API, new scenario, and the mark's
  value would then have two writers for one fact.

## What the change does not do

It does not add a branch field to the state. The item names the worktree's branch marker as unreferenced; the
state's `issue` plus the run's own change name locate the branch (`fix/issue-<issue>-<slug>`) and the marker
stays the branch's own record.
