# Proposal — the print-only sites become emit calls, and the report cross-checks the record

## Why

`tools/loop/run.sh` holds 230 `printf` calls, of which **131 are lines that only print** — no state write, no branch,
nothing a caller can read back. The same facts are then re-derived by `report.sh` from the state file (47 `jq` call
sites), so one fact has two homes and nothing checks they agree.

The record itself lands in `run-event-stream-is-typed` — one `emit`, one declared vocabulary, the bound and the
rotation. That change emits four kinds and stops. This change makes the record *complete*: every print-only site
becomes an emit whose line is rendered from the record, and the report gains a second reading of its own counts so a
difference is named instead of hidden.

## What Changes

- **The 131 print-only sites become `emit` calls**, in phase order (header, queue, stop arms, item, child wait, gate,
  review, tail). Each site's printed text is preserved verbatim; only its composition moves. A site that prints a run
  fact without recording it stops existing, and a case says so.
- **`report.sh --from-events`** recomputes the run-level counts from the record and prints them beside the state's
  figures, naming a difference on its own line and saying "the record is absent" when it is. The report's shipped
  source stays the state file — no shipped requirement is replaced.
- **Two facts that are printed and re-derived today** (the item's spend and the run's remaining budget) are emitted
  where they are known, so the cross-check has something to agree with.
- **Scope**: `tools/loop/**` only. No app code, no dependency, no build change. Additive: every existing human line
  keeps its text.
- **Not this change**: the emit path, the vocabulary and the bound are `run-event-stream-is-typed`; the report reading
  the record *instead of* the state is a third change, and it needs this cross-check first.
- **Sequencing**: this lands after `run-sh-is-split-along-the-stream`, so its gates are narrow — every site it
  converts lives in one owner's file, and the case declaration selects that owner's cases instead of the 309 that
  `run.sh` alone selects today.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. `agent-issue-loop` gains two requirements, both new: no print-only site survives, and the record and the
report's figures agree or the difference is named. The report's shipped requirements are untouched — the
cross-check is an additional reading beside them.

## Impact

Code:

- `tools/loop/run.sh` and the four owner files — the conversion, phase by phase.
- `tools/loop/report.sh` — `--from-events`, the cross-check lines, the "absent" line.
- `tools/loop/tests/loop-cases.sh` — the conversion case, the four `report#` cases, and the `# files:` declarations
  the conversion touches.

Guidelines and documents:

- `guidelines/Logging.md` §2 — the rule that an engine's own record is read back rather than grepped.
- `guidelines/BuildEvidence.md` §4f — the record joins the artifact list a run leaves.
- `guidelines/Build.md` §10k — the recipe that reads a run's numbers reads the record.

Rollback: revert the commit. Every converted site reverts with it; nothing persists a record that a reverted engine
cannot produce, and the report's shipped source is unchanged.

Costs to prove by measurement rather than assume:

- **no printed fact was lost**, from the suite: the conversion case compares the kinds a run emits against the lines
  it prints, and a run of `--items 0 --minutes 0` proves the counts equal;
- **the cross-check's noise**, from two real runs after this lands: how many difference lines appear when the record
  is complete, which the claim is should be zero.

surface: 2 requirements and 6 scenarios added to 1 delta file, 0 requirement bodies rewritten, 131 print-only sites
converted, 2 cross-check lines added — counted with
`grep -hc '^#### Scenario:' openspec/changes/the-print-sites-are-converted/specs/agent-issue-loop/spec.md` and
`grep -h '^### Requirement:' openspec/changes/the-print-sites-are-converted/specs/agent-issue-loop/spec.md`
