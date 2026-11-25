# Proposal — a rejected publish is recorded on the item

## Why

**Root cause.** `push_branch` (`tools/loop/leftovers.sh:414-421`) is the run's only push. When the
remote refuses it, the branch's stderr line and the `publish-failed` event are emitted and the
function's `||` arm ends on `loop_emit`'s status, so the caller reads a success. Its caller
(`tools/loop/run.sh:2840-2844`) reads nothing back and cannot tell a refusal from a publish. The
item's record is written by `state.sh done` before the push runs — the branch is merged and
released first — so `merged.sha` is written with no sentence beside it about the refusal. The
report (`tools/loop/report.sh`) reads the state file and git, so its branches block names the
branch the run left behind and calls the landed one removable, and never says the remote lacks it.

**The failure this allows.** The project's own run event stream carries three refused pushes and no
reader: `.pi/issue-loop/run-events.jsonl` line 909
(`fix/issue-1010-a-child-s-context-bound-is-decided-by-a`), line 996
(`fix/issue-1004-the-result-bound-cuts-…`) and line 2769
(`fix/issue-1006-the-refusal-leaves-sed-out-and-sed-is-no`), each a
`{"kind":"publish-failed",…}` event. A run whose publish was refused ends as "landed" and its
report names the branch as removable, while the remote never carried the commits — so a reader
comparing the closure's sha with the remote has no sentence that explains the difference. The
previous change's own evidence
(`openspec/changes/archive/2026-11-22-fix-the-run-publishes-the-base-it-landed-on/proposal.md`)
shows the same shape for the base: `e4132813..3a39d92a main -> main` refused, six hours and 29
commits CI never measured.

**What already stands.** The push is the loop's only push and its one stderr line names the branch
and where its commits stayed; the `publish-failed` event type exists (`tools/loop/events.tsv:104`);
the item's record carries `merged` beside the landing.

**The gap.** No caller reads the push's status, so nothing records the refusal on the item and
nothing prints it as a leftover. A refused publish is a leftover of the same kind as a branch the
run kept: its commits are in the run tree and not on the remote.

**The red test.** `worktree#aRejectedPushIsRecordedOnTheItem` drives one item against a bare remote
whose own hook refuses every push, and on the base it fails on the report and on the state, not on
the push: `loop-selftest: 1 of 1 cases failed`, `tests="1" failures="1"`
(`evidence/red-on-HEAD.xml` and `evidence/red-on-HEAD.log`, 2026-11-25T01:48:10Z).

**The owning requirement and the owning section.** `agent-issue-loop`, "The publish publishes what
still exists" (`openspec/specs/agent-issue-loop/spec.md`), gains the scenario "A refused publish is
recorded on the item"; the owning guideline section is `guidelines/Backlog.recipes.md`, section 7e,
"Leftovers, the report and the queue".

surface: 1 case added, 1 rewritten (`worktree#noPushWithoutTheFlag`), 0 fixtures realigned, 1 mutation record (`1027.1`) — counted with `tools/look.sh 'push_branch' --files`, which names the 16 files that hold the rule's text, its symbols and its cases.

## What changes

- `push_branch` returns 1 when the remote refused the push, keeping its one stderr line and its
  event, so the caller can read the refusal.
- `run.sh` reads that status and records the refused branch on the item's record beside the
  landing's sha, through a new `state.sh publish-refused` verb.
- `state.sh` gains `<issue> --branch <name> [--sha <commit>] [--remote <commit>]`, appending to the
  item's `publishRefused` list, unique by branch.
- `report.sh` prints one line per refused branch in its branches block: the branch, its local head,
  and what the remote's tracking ref names, or that it names nothing.
- The spec's "The publish publishes what still exists" requirement gains the case its failing
  scenario was missing and a scenario for the record.
- `guidelines/Backlog.recipes.md` section 7e gains the sentence that a refused publish is a leftover the
  report names.

## Impact

- `tools/loop/tests/loop-cases.sh`: `worktree#aRejectedPushIsRecordedOnTheItem`; the suite's
  declared count is 553 cases, this one included (`LOOP-MEASURE-SUITE cases=553`).
- `tools/loop/tests/loop-mutations.sh`: `1027.1` drops the count and reddens the case.
- Read paths: the state file gains a field the report reads. No other reader of the state file
  changes, and the push's own text and event are untouched.
