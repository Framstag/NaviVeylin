# Design

## Context

See `proposal.md` — Why. What shapes the approach:

- `tools/issues/update.sh` is the single write path of four callers: `tools/loop/labels.sh:108` (every loop
  verdict), `tools/issues/resolve-stale.sh:230` (parking), `tools/issues/prioritise.sh:343` and
  `tools/issues/classify.sh:191` (band/label+body repairs), and the `update-issue` / `issue-metadata` skills'
  documented repair call. None of them reads the issue first; none of them can know which label to strip.
- `tools/issues/reopen.sh:99-117` already implements replacement: it reads
  `gh issue view <n> --json labels`, filters labels by the `status` prefix, and passes the survivors as
  `--remove-label` in the same `gh issue edit` as `--add-label`. Same profile, same separator, same offline
  `gh` stub.
- The offline harness already answers everything the fix needs: `tools/issues/fixtures/gh` serves
  `gh issue view <n> --json …` from `issues.json` and records writes to `GH_STUB_RECORD`.
- `tools/check-portability.sh` forbids project *values* in the engines but reads namespace *names* as the
  contract (line 137-138), so the literal `status` prefix stays legal in `lib.sh`.
- This is shell tooling: no dispatcher, no lifecycle, no native boundary. The "threading model" is one process
  per call, and nothing here may block an Android thread because nothing here runs on one.

## Goals / Non-Goals

**Goals:**

- One `--status` call is one state transition: read, then exactly one `gh issue edit` carrying the add and every
  displaced removal.
- The scan that decides which labels are displaced exists once and is shared by the two writers that replace a
  state.
- A request naming two states is refused before the write, not silently resolved.
- The behaviour is proven offline: a named case that is red on HEAD, and a mutation of the fix that makes it red
  again.

**Non-Goals:**

- No periodic dual-state check, no `gh` label-deletion, no new CLI verb. (A check would report damage after the
  write path produced it; see proposal, "Not in scope".)
- No change to `tools/loop/labels.sh`, `resolve-stale.sh`, `prioritise.sh`, `classify.sh` — they are fixed by
  the write path beneath them, which is the point of fixing it there.
- No change to label vocabularies, the reserved-label gate, or `close.sh`.

## Decisions

**D1 — The transition is completed inside `update.sh`'s flag translation, not in a caller.**
`--status <v>` becomes "target label + every other label of that namespace on this issue". The alternatives the
root-cause evidence eliminates: a per-verdict fix in `tools/loop/labels.sh` (impossible: it serves one of four
callers, and the loop does not know the issue's current labels either — it would need the same read, in five
places); a fix in `close.sh` (impossible: the displaced label is written *before* close — the loop's `close`
verdict calls `update.sh --status fixed-by` and then `close.sh`, and the `--comment-only` path never reaches
`close.sh` at all, which is exactly how #114/#115 kept `status/open`); a checker plus hand repair (impossible as
a fix: the write path keeps producing the state, and #109/#110 are the damage it already produced).

**D2 — The current labels are read with one `gh issue view <n> --json labels`, only when `--status` is present.**
Alternatives: read on every `update.sh` call (adds a network round trip to the loop's `unpick` and to
label/comment-only repairs, which change no state); read the whole tracker with `gh issue list` (stale by
construction, and it pays for labels nobody asked about). The single view is the pattern `close.sh:82` and
`reopen.sh` already use, and the offline stub answers it.

**D3 — The displacement filter is a pure function in `tools/issues/lib.sh`, shared with `reopen.sh`.**
It takes the issue JSON, the target label and the status prefix and prints the labels to remove — no `gh` call
and no profile read of its own, so both callers keep their own read and their own single edit. The prefix is a
parameter because reading the profile starts a JVM: each writer resolves it once and passes it on, and
`issues_status_prefix` refuses to answer before `issues_resolve` rather than returning a separator-less
namespace that would make the scan match nothing (the review of 2026-10-18 measured both, `evidence/review.md`
P2-2). Alternatives: duplicate the loop in `update.sh`
(impossible to keep correct: the two copies would drift, and `reopen.sh`'s copy is the one that was right);
have `update.sh` shell out to `reopen.sh` (it is a writer with its own argument parsing and its own reopen
semantics — calling it to filter would perform a second write). `lib.sh` is already where the namespace and
separator live (`issues_profile_value namespaceSeparator`), and both writers source it.

**D4 — Only the difference is written: no `--add-label` for the label the item already carries as its state,
and never an `--add-label` and a `--remove-label` for the same label in the edit this call produces.**
The scope is the state write, which is what this change owns: `--label <l>` beside `--remove-label <l>` for a
*non*-status label is still passed through as the caller wrote it, and a displaced label the caller also names
in `--remove-label` is removed twice in the recorded write (harmless to `gh`, visible in the log). Both are
pre-existing and out of this change's way — the review of 2026-10-18 recorded them (`evidence/review.md`, P2-4)
and neither can make an item carry two states.
Alternatives: always add the target and remove the others (harmless-looking, but it makes an idempotent call
emit add+remove of one label, whose resolution is `gh`'s behaviour rather than our contract — the offline stub
cannot answer it, so it would be unprovable); rely on `gh` to de-duplicate (same problem: an invariant of the
tool replaced by an invariant of the client).

**D5 — A request that names two states is refused with exit 2 (usage) and a reason naming both values, before
any read or write.**
Alternatives: let `--label status/other` win and drop the `--status` value (a silent decision on which state an
item has); refuse with exit 3 and a reason code (reserved: exit 3 is the policy/vocabulary refusal — `label-opaque`,
`label-reserved` — and a contradiction between two valid values is not a vocabulary problem, so reusing it would
blur what the skills retry on). `issues_die` already exits 2, and `update.sh` already exits 2 for "nothing to
change", so the caller's contract does not change shape.

**D6 — `--json` reports the displaced labels inside the existing `removedLabels` array.**
Alternatives: a second field (`replacedStatus`) describing the same act — two names for one fact, and every
reader would have to learn both; omit them (the loop's own log line would then misdescribe the transition, and a
skill could not prove what it replaced). The existing field is what a caller already reads, and "removed" is
literally what happened.

**D7 — Verification is the offline case, the mutation, and the three suites that drive `update.sh`.**
The new cases live in `tools/issue-tooling-cases.sh` under the names the spec scenarios carry
(`update#statusReplacesTheOldStatus`, `update#statusReplacementIsIdempotent`,
`update#statusRejectsTwoStatesInOneCall`, `update#statusReportNamesImpliedRemoval`) and run through
`tools/issue-tooling-selftest.sh --case <name>`. Falsification: delete the removal loop in `update.sh`, the
replacement case must go red, restore, green forced. Regression: `tools/issue-tooling-selftest.sh`,
`tools/resolve-stale-selftest.sh` and `tools/sensor-selftest.sh` (both drive `update.sh --status`), plus
`tools/check-portability-selftest.sh` because `lib.sh` is an engine. No on-device measurement: nothing here
renders, logs a coordinate or runs on Android.

**D8 — The state may be named as the namespaced label, and that spelling is folded into the value spelling.**
A state written as `--label status/on-hold` is the same act as one written as `--status on-hold`, so the
replacement and the one-state rule apply to it: the label is pulled out of the plain labels and treated as the
target, before the contradiction checks, the vocabulary gate and the read, so the rest of the script has one
shape to handle. This is what the review of 2026-10-18 found (P1, `evidence/review.md`): a declared check's
escalation writes the state as a label — `tools/sensors/run.sh` passes the registry's `escalation.labels`, one of
which is `status/on-hold` — so a fix gated on `--status` alone would have left that writer producing exactly the
two-state item this change exists to remove, and `tools/sensor-cases.sh` pinned that add-only write. The delta
requirement was always phrased over "a call that sets an item's lifecycle state", so the spec's requirement text
needed no change; the implementation and the sensor case did (a fifth scenario names the case).
Alternatives: teach `sensors.json` to pass `--status` from a new `escalation.status` key (the registry would own
a second way to say the same thing, and the next writer of a state label would still be unguarded); leave it to a
separate change and narrow the invariant's wording (impossible to state honestly: I16 would be false of the code,
and the falsifying observation — an item with two states — reachable by one escalation run).

## Risks / Trade-offs

- **A `gh` read before every state write** → one extra API call per transition; the alternative (a caller that
  remembers the old state) was already the bug. Bounded by being status-only.
- **Existing cases assert recorded writes** → adding `--remove-label` to a recorded `gh issue edit` could trip a
  case that greps the log; the three suites above are run, and a fixture issue with no status label yields no
  removal at all.
- **A concurrent writer between the read and the edit** → the edit could remove a state that arrived after the
  read; the tooling's single-writer discipline (`guidelines/Backlog.md` §5) is what makes this acceptable, and
  the same window already exists in `reopen.sh`.
- **The helper lands in `lib.sh`, which portability scans** → the namespace *name* is allowed (the check forbids
  values), and `tools/check-portability-selftest.sh` is run as the evidence.
- **Repairing #109/#110 needs a decision about which state survives** → the rule is the one already recorded on
  the items: a closed item keeps `status/fixed-by`, an open parked item keeps `status/on-hold`.

## Migration Plan

1. Land the write-path fix and its cases.
2. Sweep the items that already carry two states — `tools/issues/list.sh --json` over the tracker, repair each
   with one fixed `update.sh --remove-label status/<stale>`; nothing is hand-edited, no new tool.
3. `guidelines/Backlog.md` §4 gains the invariant row with its measurement (the replacement case), so the next
   reader sees the rule where the other tracker invariants are.

Rollback: revert the fix commit. The labels already repaired stay repaired (the sweep only removed duplicates);
no persisted format, no schema, no dependency, no caller contract changes shape.

## Open Questions

- Whether a dual-state check should exist at all — deferred to its own item (#178 stays a write-path bug; a
  check would be a report, not a fix).
