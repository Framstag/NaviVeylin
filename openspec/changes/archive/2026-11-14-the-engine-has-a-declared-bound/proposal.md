# Proposal — every engine script enters one table, and the table holds its bound

## Why

`tools/loop/run.sh` is 3793 lines and nothing refuses a file past a size: the engine grew 410 → 3793 lines in the
four weeks to 2026-11-14 (+120/day), and the suite cannot report it because no file carries a number it could
compare against. `tools/loop/*.sh` is 10790 lines over 12 files, and which names two of them share is knowable only
by reading all 12.

This change declares what exists. **Nothing is moved and no behaviour changes** — the table describes the engine as
it stands today, the bound is today's size plus a headroom, and the suite becomes able to say "this file grew past
the number someone argued for". The move itself is the next child
(`run-sh-is-split-along-the-stream`), which needs this table to exist first and narrows it afterwards.

## What Changes

- **`tools/loop/layout.tsv`** — one row per engine script: the file, its line bound, and its source order. Twelve
  rows today; a script with no row fails the suite, and a row with no usable bound fails it too.
- **`tools/loop/shared.tsv`** — every name more than one engine file reads, with the file that assigns it and the
  files that read it. Harvested from the function bodies, one row per name; `local` needs no row.
- **Eight cases**, one per scenario of the delta, each a static read: every engine script is in the table, no file
  exceeds its bound, a row without a usable bound is refused, the shared names the table declares equal the names the
  files actually share, an undeclared read and a name assigned twice are refused, the entry sources the declared order,
  and a call to a later file's function fails.
- **Scope**: `tools/loop/**` only. No app code, no dependency, no build change, no run behaviour, no case body
  changed except its `# files:` declaration where it gains the two tables.
- **Not this change**: the move of `run.sh` into five owner files, and the ownership column the table gains once
  those files exist. Both are `run-sh-is-split-along-the-stream`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. `agent-issue-loop` gains three requirements, all new: the table and its completeness, the per-file bound, and
the shared-name declaration.

## Impact

Code:

- `tools/loop/layout.tsv` (new) — file, bound, source order.
- `tools/loop/shared.tsv` (new) — name, assigned by, read by.
- `tools/loop/tests/loop-cases.sh` — the `driver#` cases below, and the `# files:` declarations that gain the tables.
- `tools/loop/tests/loop-selftest.sh` — nothing: the cases select by declaration as they always did.

Guidelines and documents:

- `guidelines/Backlog.md` section 7 names the table, so a person fixing the engine finds a file's bound before editing it.
- `guidelines/BuildGate.md` §2a states that a `tools/loop/**` delta's cost is visible in the table.

Rollback: revert the commit. Two data files and eight static cases; nothing at run time reads them.

Costs to prove by measurement rather than assume:

- **the baseline table**, from the tool itself: `wc -l` per engine script, recorded in the change's evidence, with
  the bound each row now carries (today + 25 %, rounded up);
- **the shared-name count**, from the harvest: the number of names more than one file reads, recorded the same way.

surface: 3 requirements and 8 scenarios added to 1 delta file, 0 requirement bodies rewritten, 2 new data tables,
8 static cases, 0 files moved — counted with
`grep -hc '^#### Scenario:' openspec/changes/the-engine-has-a-declared-bound/specs/agent-issue-loop/spec.md` and
`grep -h '^### Requirement:' openspec/changes/the-engine-has-a-declared-bound/specs/agent-issue-loop/spec.md`
