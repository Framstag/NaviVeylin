# Design — one more field read, in the check that already walks every issue

## Context

See `proposal.md` — Why. Constraints:

- **`check-metadata.sh` has three modes** and only two of them see individual issues: `--live` (the tracker, needs a
  token) and `--issues <file>` (a JSON array, which the offline cases drive). The third is a token-free self-check of
  the profile. The invariant belongs in the two per-item modes and nowhere else.
- **The profile already declares the container type** (`roles.containerType`), and the pre-screen already refuses a
  container as work — it reads the type it needs without anything proving the type is there.
- **`gh issue list --json` already carries `issueType` and `parent`**: the read names both fields and still answers
  with one document, so the axis costs no additional call — which is what keeps the cost claim checkable.
- **A child's type is the filer's judgement**, not the tooling's: the axis admits any declared type.

## Goals / Non-Goals

Goals: an untyped child is reported on the commit that creates it, and the axis with the most consequence (work or not
work) gets a reader. Non-goals: defaulting a type in `create.sh`, requiring a *particular* type, and checking the
container's own type beyond "it is the profile's container type when it has children".

## Decisions

### D1 — The invariant lives in the metadata pass, not in `create.sh`

Alternatives:

- **Refuse in `create.sh` when `--parent` is given without `--type`** (rejected): the writer would then have to decide
  what a child is, and a legitimate `Bug` child filed from a script that passes only `--parent` would be refused for a
  field it does not need to name. It also misses every child filed before this change and every one written by hand.
- **A check in the pre-screen** (rejected): the pre-screen decides what a run may *work*, and a child of a container is
  work regardless of its type. An invariant about the tracker's shape is not a queue verdict.
- **A sensor row** (considered): the `metadata-invariant` sensor row already runs this script; adding the invariant to
  the script gives the row the new check for free, which is the point of having the row.

Chosen: a sixth invariant in `check-metadata.sh`, so one tool answers "is this tracker's metadata consistent" and the
scheduled row inherits it.

### D2 — The rule is "a child carries a type", not "a child carries a particular type"

Alternatives:

- **Require the child to be a non-container type** (rejected as the rule, kept as a consequence): a child whose type is
  the container type is a nesting the tracker renders as another epic, which is legal in GitHub and not this project's
  business to forbid — the profile decides the *name*, not the shape.
- **Require a type only for children of this project's container type** (chosen): a parent that is an ordinary issue is
  not a container, and a child of one is not the failure being fixed.

Chosen: for an item whose `.parent` is an issue of the profile's `containerType`, the item's type SHALL be non-empty.
Nothing about which type. The one case that matters — nine children invisible under a finished epic — is exactly
this condition; a parent that is an ordinary issue leaves the child out of the rule's scope.

### D3 — A violation is reported, and the pass keeps its exit contract

Alternatives:

- **Fail fast on the first untyped child** (rejected): the pass already reports *every* violation it finds and exits 1
  at the end; a second shape of "stop early" is a second contract for the same tool.
- **Make it a warning with exit 0** (rejected): the sensor row files items on a red run; a warning files nothing, and an
  untyped child is exactly as actionable as a doubled band label.

Chosen: the sixth invariant joins the five — named on stdout, carried in `--json`, exit 1 when any is violated.

## Risks / Trade-offs

- **A tracker with many untyped children turns the sensor red at once** → that is the honest state; the row's own
  `maxFilings` bounds how many items one run files, and the two epics in this tracker are the only known offenders.
- **`issueType` is absent on older `gh` versions** → a document that carries no type field at all is one line saying
  the axis could not be read and exit 1, so a client that cannot answer is named rather than read as a clean tracker.
  A parent outside the read's window is a narrower unknown: its type is not in the document, so the child is not
  checked, and the check says nothing about it rather than guessing.
- **The rule fires on a sub-issue of a non-container** → D2's scope is `.parent` set and the *parent* being the
  container type; a plain parent-child between ordinary issues is out of scope by the same reading.

## Migration Plan

1. The invariant in `--issues` mode with its fixture cases (offline, no token).
2. The same in `--live`, one field read, no new call.
3. Correct `create.sh`'s capability comment, and record the measured number of untyped children before the fix.

Rollback: revert the commit.

## Open decisions

none
