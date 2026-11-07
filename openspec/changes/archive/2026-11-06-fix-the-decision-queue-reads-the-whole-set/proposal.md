# The decision queue reads the items that carry the label, not the tracker's newest 400

## Why

`tools/issues/decisions.sh:26` set `limit=400`, and `:48` handed it to `gh issue list --state all --limit "$limit"`,
which answers **newest-created first**. So the queue read a fixed window over the tracker rather than the set it is a
query over, and every item older than the 400th newest was invisible to it — while the count it printed said how many
it had seen, which is the number a person reads as the whole queue. On 2026-11-06 the tracker held 453 items
(`gh issue list --state all --limit 2000 --json number | length` → 453), so 53 were never read, three of them
carrying `agent/needs-decision`: `decisions.sh` printed `1 item(s) await a decision` where `--limit 500` printed five.

**Root cause, in one sentence:** the queue's read was a fixed number of the tracker's newest items instead of the items
carrying the decision label, so the read was a window the tracker outgrows rather than the set the label names.

**The red cases, on the base `2e59e48a`** (the engine change the only thing reverted; `evidence/red-on-HEAD.xml`
carries `tests=2 failures=2`, because the shell runner writes no JUnit XML and the log is kept beside its tally):

- `decisions#theOldestQueuedItemIsListed`, 2026-11-06T19:42:44Z–19:42:46Z (`evidence/red-on-HEAD.log`): a tracker of
  405 items whose oldest (#1) and newest (#405) carry the label — the base lists `#405` alone:
  `'#1  **The decision:** merge the two rows into one' not found in: #405 … 1 item(s) await a decision`.
- `decisions#aReadThatCannotCoverTheSetIsRefused`, the same run: a tracker whose labelled set outruns the count the
  tracker reported — the base prints `2 item(s) await a decision` and exits **0**, where the queue owes exit 3 and no
  count at all.

**Owning requirement:** `openspec/specs/decision-queue/spec.md`, "The queue names what awaits a decision and what each
item asks" (`:14`, what the queue lists — the requirement this delta leaves alone) and the requirement this delta
**adds**, "The queue reads the whole labelled set, and refuses a set it could not cover", whose two scenarios name the
two cases above.

**Owning guideline section:** `guidelines/Backlog.md` §5 "Who may write what", the block "Six rules keep the decision
queue answerable" (`:144`) — the fifth rule becomes the sixth, because a queue whose read can miss items is not
answerable.

surface: 2 cases added, 0 rewritten, 3 tool files changed (`tools/issues/decisions.sh`, `tools/issues/fixtures/gh`,
`tools/issue-tooling-cases.sh`), 1 guideline rule added, 1 requirement added, 0 compiled files — counted with
`bash tools/issue-tooling-selftest.sh --list | wc -l` (160 cases, 158 before this change),
`tools/look.sh 'limit 400' --path tools` (8 matches in 6 files: the fixed windows this change does **not** touch, named
in `design.md`), `tools/look.sh '^#### Scenario' --path openspec/specs/decision-queue/spec.md` (15 scenarios in the
shipped capability, 2 added here) and `git diff --numstat tools/issues/decisions.sh tools/issues/fixtures/gh
tools/issue-tooling-cases.sh` (`41/6`, `82/4`, `69/0`).

The change's own measurement block (`./tools/loop/measure.sh --change fix-the-decision-queue-reads-the-whole-set`,
2026-11-06T19:46:03Z, `evidence/measure.txt`) — its pathspec is the worktree's diff against the base and therefore carries
the paths of the changes beside this one, so it is quoted as it printed rather than picked over:

```
LOOP-MEASURE-PROFILE /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf
LOOP-MEASURE base=6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db paths=117 insertions=8171 deletions=62
LOOP-MEASURE-CHECK tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach
LOOP-MEASURE-CHECK tools/loop-selftest.sh --from-git 6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db
LOOP-MEASURE-CHECK bash tools/loop-mutations.sh --check
LOOP-MEASURE-CHECK tools/loop-mutations-selftest.sh
LOOP-MEASURE-CHECK tools/issue-tooling-selftest.sh
LOOP-MEASURE-CHECK tools/issues/check-metadata.sh
LOOP-MEASURE-CHECK tools/issues/metadata-mutations.sh --list
LOOP-MEASURE-CHECK bash tools/gen-feature-list.sh --check-classification
LOOP-MEASURE-CHECK tools/check-doc-routes.sh
LOOP-MEASURE-CHECK tools/check-doc-routes-selftest.sh
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
LOOP-MEASURE-CHECK tools/check-todo-references.sh
LOOP-MEASURE-CHECK tools/check-openspec-hygiene.sh
LOOP-MEASURE-CHECK tools/check-openspec-hygiene-selftest.sh
LOOP-MEASURE-CHECK tools/check-portability.sh
LOOP-MEASURE-CHECK tools/check-portability-selftest.sh
LOOP-MEASURE-CHECK tools/mutate-selftest.sh
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/portability-mutations.json --list
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/status-mutations.json --list
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/create-mutations.json --list
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/close-mutations.json --list
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/duplicates-mutations.json --list
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/decision-question-mutations.json --list
LOOP-MEASURE-SUITE cases=339 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
LOOP-MEASURE-CHANGE fix-the-decision-queue-reads-the-whole-set scenarios=2 cases=2 unnamed=0
LOOP-MEASURE-SURFACE surface: 2 cases added, 0 rewritten, 3 tool files changed (`tools/issues/decisions.sh`, `tools/issues/fixtures/gh`,
LOOP-MEASURE-END
```

## What Changes

1. **`tools/issues/decisions.sh`** — the read becomes two passes and a self-check: the labelled population is counted
   (`gh api graphql` `totalCount`), the list is scoped by that same label and asked for **one item more** than the
   count, and a read that comes back full is refused with exit 3 (`decision-set-uncovered`) naming the count and the
   read — because a count over a set it could not cover is the defect in its silent form. The human form prints the
   set its count was taken over beside the queue's own count, and `--limit` leaves the usage: it was the guess.
2. **`tools/issues/fixtures/gh`** — the stub models what the defect needs from `gh` and nothing else: `gh issue list`
   honours `--state` and `--label` and slices newest-created first when the fixture holds more items than `--limit`;
   `gh api graphql` answers `totalCount` from the fixture's own items, refusing a query it does not describe;
   `GH_STUB_COUNT_LAG` makes the count lag the list, the race the refusal exists for.
3. **`tools/issue-tooling-cases.sh`** — `decision_tracker` generates the tracker a case needs (405 items, or three),
   and the two cases above assert the oldest queued item is listed and that a read which cannot cover the set is
   refused.
4. **`guidelines/Backlog.md`** §5 — a sixth rule: the queue reads the whole labelled set and refuses one it could not
   cover.
