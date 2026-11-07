# Design — the inbox's consumer

## The state this answers

`shaping-inbox` owns a state, a label, a comment shape and a queue verb. A queue with no reader is a listing, and
`guidelines/Backlog.md` §5 already says the queue "is a listing, not a ranking" — the ranking belongs elsewhere.
This change adds the reader and nothing else.

## Decisions

### D1 — A skill of its own, not a mode of `triage-todo`

**Chosen.** `.pi/skills/reshape-issue/SKILL.md`, one skill, one act: read the inbox, propose, stop, write on approval.

**Alternatives eliminated.**

- *Extend `triage-todo`.* It ranks the backlog by `prio/*` and its output is "the next change to start". The
  shaping queue is explicitly not a ranking (`Backlog.md` §5, §185), and its items are not candidates for starting
  — they are items no run may start. Folding two opposite outputs into one skill makes both unreadable, and
  `triage-todo`'s "When NOT to use" list already has to say so.
- *A `--propose` mode on `tools/issues/shaping.sh`.* A shell verb can print the queue and the item's clause; it
  cannot read a tree and name the mechanism the item lacks, which is the whole act. Adding an LLM-shaped flag to a
  read-only query verb would also put a proposal path into a script that must stay a query.
- *Let the loop propose.* `run.sh` is refused a guess by design (`prompts/worker.md` Phase B: "on a rejection do
  not ask"), and its skip is what created the state. A proposing loop would be the decision path this state exists
  to stay out of.

### D2 — The skill stops at the owner, and writes only through `create.sh` / `update.sh`

**Chosen.** The proposal ends the session's authority. On approval the skill calls `tools/issues/create.sh` per
child and `tools/issues/update.sh` once for the item, which is the write `shaping-inbox` already designates as the
way out: it removes `agent/needs-shaping` in the same call that records the new shape.

**Alternatives eliminated.**

- *Write the reshape directly.* §5's table gives "decide a design question" to a person and only a person, and
  which mechanism is the defect is exactly that question. A skill that decided it would be the loop it replaced,
  with the loop's refusal removed.
- *A new writer for the inbox.* `shaping-inbox` §"Leaving the inbox is an existing writer's act" refuses a fourth
  vocabulary of transitions for the same three exits; `update.sh` and `close.sh` already carry the label removal.

### D3 — A child is proposed with its filing shape named, not filed shape-less

**Chosen.** `create.sh` refuses a finding carrying neither `cause` nor `change` (`shape-missing`), and `file-finding`
forbids re-shaping a refusal. The skill therefore states, per child, whether it is a tier-A finding (a `file:line`
plus the case that exercises it) or needs a change first, and files only the former.

**Consequence.** Splitting an epic whose children have no proven cause yet produces proposals, not items. That is
the honest outcome: each child then enters the backlog through the same gate every other finding does.

### D4 — Verification is three cases in the issue tooling's own suite

**Chosen.** Three `shaping#` cases in `tools/issue-tooling-cases.sh`, the file that already carries the nine
`shaping#` cases and is sourced by `tools/issue-tooling-selftest.sh`. They assert the skill's contract mechanically:
it names the queue verb, it names the two writers and no other write path, and it carries the stop sentence. Cheap,
red on HEAD (the skill does not exist), and no new suite is registered in `tools/loop/loop.conf`.

**Alternatives eliminated.**

- *A dedicated `tools/check-shaping-consumer.sh` plus a selftest plus `loop.conf` rows.* Three `grep` assertions
  would cost a permanent gate entry on every `tools/check-*` edit — the trade the portability and doc-route checks
  already declined for smaller rules.
- *`**Case**: none — a skill has no runtime`.* Permitted by the readiness checker with a reason, but it would file
  the change as unverified while a mechanism to verify it exists one file away.

## Verification

- `bash tools/check-change-readiness.sh openspec/changes/shaping-inbox-consumer` prints `LOOP-CHANGE-READY: ok`.
- `openspec validate shaping-inbox-consumer --strict` passes.
- `bash tools/issue-tooling-selftest.sh` gains the three cases; each is red before `SKILL.md` exists and green after.
- `bash tools/check-doc-routes.sh` passes: the new skill is a directory carrying a `SKILL.md`, and `Backlog.md`'s
  pointer to it resolves (a route to a missing skill is reported, `routes#aSkillPathThatNoLongerResolvesIsReported`).

**No runtime component.** The skill runs in a session: it reads the tracker through `shaping.sh` (read-only), reads
the tree, and writes only through two CLI verbs after the owner answers. No dispatcher, no lifecycle, no state.

## Risks / Trade-offs

- **A skill is prose, and prose drifts.** The three cases pin its contract, not its judgement: a consumer can name
  the queue verb and still propose badly. Accepted — the owner reads every proposal before anything is written,
  which is the same guard the decision queue relies on.
- **The inbox can still grow.** This change makes items reachable, not cheaper to resolve; a person still owes the
  reshaping. `shaping-inbox` declined a cap on the inbox deliberately and that reasoning stands.
- **A second consumer could appear.** Nothing forbids another skill reading `shaping.sh`; the requirement names one
  consumer so the pointer in `Backlog.md` resolves, not to make a second one wrong.

## Open decisions

none
