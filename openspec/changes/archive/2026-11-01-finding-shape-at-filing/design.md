# Design — the finding's shape at filing time

## Context

The filing path is one Java core behind one shell entry: `tools/issues/create.sh` reads a finding as JSON, asks
`tools/issues/core/*.java` for the fingerprint, the label gate and the body record, and issues exactly one
`gh issue create`. Three refusals already exist there and are the model this change follows:

| existing refusal | the reason it exists |
|---|---|
| `duplicate` (exit 4) | the item is not filed if the fingerprint already exists |
| `label-reserved` / `label-opaque` (exit 3) | no script authorises work, and no typo creates a label |
| a body that cites a path nothing resolves (exit 3) | "a body the tooling accepts must cite something that resolves" (`create.sh:84`) |

What no refusal covers is the shape of the finding — and the tracker measures the consequence: 39 of the 44 items
on `status/on-hold` carry a bare hold, a verdict word with no question (`guidelines/Backlog.md` §5, "A hold is not a
decision"). Each of those was a complete finding as far as the writer was concerned.

Two facts from the tree bound the design:

- `create.sh` is called by more than a person. `tools/sensors/` files findings from the check registry, and those
  findings carry neither a cause nor a change — their cause is the failing case the registry already names, and
  their identity is the condition, not the fix (spec `backlog-sensors`).
- The readiness checker C1 ships has no caller; the profile is the place a project records a path it lets another
  tool run (`tools/tooling.json`, "recorded configuration, never a path in an engine").

## Goals / Non-Goals

**Goals:**

- An item filed after this change is one a worker can act on without asking: either its cause is proven, or its
  change exists and declares nothing open.
- Refusals that name the missing half, in the vocabulary a reader can quote into the next session.
- One record for the answered forks, in the form the worker's package already reads.

**Non-Goals:**

- Deciding the shape for the filer. The writer refuses an unclear shape; it never picks one.
- Re-shaping the items already filed.
- Changing what the tracker's labels mean. No namespace value is added.

## Decisions

### D1 — Shape is a precondition of the write, not a warning after it

A warning is a thing a session learns to ignore. The tree's own doctrine is the refusal: *"a scenario with no case
is a planning defect: fix the plan rather than promising a test later"* (`tools/loop/prompts/worker.md`, phase C).
So `create.sh` exits 3 with the shape it wanted and creates nothing. The consequence is accepted and stated in the
proposal: an existing caller that supplies no shape is broken, which is the point of a boundary.

### D2 — The tier lives in the body record, not in a label

**Chosen:** `<!-- tier: a -->` / `<!-- tier: b -->` plus `<!-- change: <name> -->`, written through
`issues_body_set` and read through `issues_body_field` — the pair that exists so "a body record the metadata pass
reads cannot be spelled differently by the pass that writes it" (`tools/issues/lib.sh:582`).

**Eliminated: a `tier/*` namespace or a new `agent/*` value.** A label is a *mode* in this tracker and the mode it
carries is what a selector queries. Both tiers are selected, scored and authorised identically — the tier changes
what a *worker* does, not what the queue holds — so a label would advertise a distinction no query needs. And
`agent/*` is the run's own vocabulary (`eligible`, `in-progress`, `needs-decision`, `needs-device`, the excluded
states), which the run writes; a filing path writing a value into it would be a second writer of the run's mode.

**Eliminated: overloading `class/*`.** `class/feature` already means *what kind of thing is missing*, and a bug can
sit in either tier. Overloading it would break `prescreen.sh`'s ranking, which reads `roles.workedClasses`.

### D3 — The cause is a structure, and its pointer is checked by the gate that already exists

`cause: {file, line, case, tests, failures}` is refused when either half is missing: a `file:line` with no case is a
hypothesis, a case with no pointer is a repro nobody can re-run. The pointer resolves through the citation rule the
body already passes (`create.sh:84`), so this change adds no second definition of "a path that resolves".

### D4 — A declared exemption, and the exemption is written on the item

The check registry's findings carry no cause: the failing case *is* the cause, and the registry names it. So a
finding filed with `--sensor <id>` is exempt from the shape, and the writer says so on the item, in the body record
beside the `occurrence` count it already writes. Without the exemption the sensor runner breaks on its next
scheduled run; with an undeclared exemption the boundary leaks.

The reconciler (`tools/issues/import.sh`) is the second such path, and it is exempt for a different reason: an
adapter's finding is a **historical record** — a reading of a record that predates the shapes — so it carries
neither a proven cause nor a prepared change, and requiring one would make the adapter invent it. It passes
`--import` and the item records `exempt: import <sourceId>`, beside the source identity it already carries.

Two clauses, one mechanism: the marker is one key (`exempt`), written through `issues_body_set`, and it names the
reason rather than asserting that none is needed. The path is kept for a project adopting the tooling; a rewrite
of the reconciler is a later change's, not this one's, because retiring it changes a shipped requirement of
`tooling-portability` ("they are the source adapter that reads this project's own record format…").

### D5 — The answered forks are one comment, written by the path that already owns the format

`tools/issues/answer.sh` writes a comment whose first line is `Decision: <answer>` and whose second is
`<!-- answers: <question> -->`, and `tools/issues/core/WorkPackage.java` renders that comment as the
`<<<ITEM_DECISION` block a worker reads (`DECISION_PREFIX = "Decision:"`; `**The decision:**` is the marker of the
*question*, not of the answer). This change does not write a second format: `create.sh` composes the same comment
for the questions a finding carries, **before** the issue's state label is written — the ordering `decision-queue`'s
"recorded before the state" rule states — and writes no `agent/needs-decision` at all, because an item that arrives
with its question answered is not awaiting anything.

Two records of one decision is the defect #196 records (a renumbered option list making a recorded `2` mean
something else). There is one record here: the comment. `design.md` cites the item.

### D6 — The readiness path is a profile key, and empty refuses

`checks.changeReady` in `tools/tooling.json`. An empty value means *this project declares no readiness check*,
and the tier-B shape is then refused with `gate-not-configured` rather than filed unchecked — the convention
`LOOP_UNTRACKED_RECORD_DIR` states for an empty path ("Empty means no ignored path is copied"), and the reason
`tools/check-portability.sh` forbids a project value inside an engine.

**Eliminated: `create.sh` calling `tools/check-change-readiness.sh` by hard-coded path.** A ported project would
inherit a caller with no callee, and the gate would be one rename away from silently not running. A gate the
writing tool cannot reach is a gate a caller can lie about.

### D7 — The skill decides nothing the writer can decide

`.pi/skills/file-finding/SKILL.md` gathers from the session what the shape needs and calls the one command; on a
refusal it reports the refusal's own sentence and stops. It carries no second copy of the rules — the same
discipline the shipped skills keep (`guidelines/Build.md` §1, "Skills").

## Open decisions

none

## Risks / Trade-offs

- **A session that has not done the work cannot file, and will be tempted to invent a cause.** Accepted: an
  invented `file:line` fails the citation check, and an invented case has no tallies in the body. The refusal is the
  cheap outcome; a wrong item is the expensive one.
- **The sensor exemption is a hole a person could take.** `--sensor <id>` validates the id against the registry, so
  the hole is the width of "a registered check", which is the width intended. It is stated on the item, so a reader
  sees it.
- **Tier B is filed and the worker still plans its own change until C3 lands.** Accepted and stated in the
  proposal: the recorded decision is the half that already works, and the item is not wrong — it is worked as the
  loop works an item today.
- **`create.sh` grows a fourth kind of refusal and a fifth.** Mitigated by cases: one per refusal, in the suite the
  gate's pathspec already runs (`tools/issues/*|tools/issue-tooling-selftest.sh`).

## Verification

- `tools/issue-tooling-cases.sh` gains the cases this delta's scenarios name; `bash tools/issue-tooling-selftest.sh`
  runs them offline.
- `tools/issues/metadata-mutations.sh --list` covers the body-record keys: a key the writer writes and the reader
  cannot read is the defect the pair exists to prevent.
- No compiled file is touched → no Gradle gate; the structural checks are the self-test, the mutation list,
  `tools/check-doc-routes.sh` (the AGENTS.md row) and `openspec validate finding-shape-at-filing --strict`.
