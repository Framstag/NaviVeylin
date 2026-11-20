# Tasks — issue type census

## 1. The census in the check

- [x] 1.1 `check-metadata.sh` names every open item that carries no issue type, in human mode one line per item and
      under one `--json` field, without touching the exit status (D1). Verify: `check-metadata.sh --issues <fixture>`
      prints the number of the untyped item and exits as it did before. Case: `check#untypedItemIsReported`.
- [x] 1.2 The self-check (no argument) asserts the census is wired, so a report that stops firing is caught inside
      the token-free mode CI runs. Verify: mutating the report out of the check leaves the no-argument run green and
      the case red. Case: `check#untypedItemIsReported`.
- [x] 1.3 Two fixtures under `tools/issues/tests/fixtures/metadata/` — one item with an issue type, one without —
      and the case drives both directions (the typed item is not named, the defect removed the case goes green).
      Verify: `bash tools/issues/tests/check-metadata-selftest.sh --case check#untypedItemIsReported`.
      Case: `check#untypedItemIsReported`.

## 2. The same set as one query

- [x] 2.1 `list.sh --untyped` prints the open items with no issue type, beside the existing filters. Verify:
      `tools/issues/list.sh --untyped` against the live tracker names items and `--json` carries the numbers.
      Case: `list#untypedIsQueried`.
- [x] 2.2 `.pi/skills/list-issues/SKILL.md` documents the flag beside the queries it already lists. Verify: every
      command in the document runs as written. Case: `list#untypedIsQueried`.

## 3. The route, as a case

- [x] 3.1 `docs#theFilingSkillsNameTheTypeAxis` in `tools/issues/tests/issue-tooling-cases.sh`: both skills name
      `--type` and `--parent` (D3). Verify: deleting either flag from either file fails the case and nothing else.
      Case: `docs#theFilingSkillsNameTheTypeAxis`.
- [x] 3.2 `update-issue/SKILL.md`'s "at least one of …" sentence lists the two fields, so the documented minimum
      matches what `update.sh` accepts (`:162-163`). Verify: the sentence names `--type` and `--parent`.
      Case: `docs#theFilingSkillsNameTheTypeAxis`.

## 4. The backfill

- [x] 4.1 Type the 60 untyped open items from their labels by D2's table, one `update.sh --type` per item, and
      record the before/after census in `evidence/census.md`. Verify: the proposal's `surface:` command prints the
      residue, and every remaining number is one the table cannot type. Case: `check#untypedItemIsReported`.
- [x] 4.2 Where the residue holds a container, file its children with `--parent` first and type the container
      `Epic` after (D2's last row), so no item is a container by prose alone. Verify: `gh issue list --json
      issueType,parent` shows the link both ways. Case: `prescreen#aContainerTypeIsNotWork`.
- [x] 4.3 `guidelines/Backlog.md` §3 carries the census sentence and the command that re-takes it, so the number is
      re-readable without this change. Verify: the command in the guideline runs and prints a number.
      Case: `check#untypedItemIsReported`.

## 5. Integration

- [x] 5.1 The full issue-tooling suite is green offline and the census command in the proposal prints the residue
      after 4.1. Verify: `bash tools/issues/tests/issue-tooling-selftest.sh` and the `surface:` command.
      Case: `check#untypedItemIsReported`.

## Outcome (applied 2026-11-20)

- **Suite**: `bash tools/issues/tests/issue-tooling-selftest.sh` — 188 cases green, offline, no token, no device
  (`evidence/green-on-change.log`).
- **Red on the base**: none of the three case names exists at `4c7202eb`, and the behaviour each asserts is absent
  from that base's engines (`evidence/red-on-HEAD-4c7202eb.log`).
- **Mutations**: three, each red then restored — the census block disabled, the census line removed from the
  report (which also turns the no-argument mode red), and `--parent` taken out of `create-issue`
  (`evidence/mutations.log`).
- **Census**: 4 untyped open items at the base, 0 after the backfill, read by `list.sh --untyped` and by
  `check-metadata.sh --live` (`evidence/census.md`, `evidence/live-census.log`). Task 4.2 is a no-op: no item in
  the residue carried a `parent`, so the table's container row typed nothing.
- **Structural checks of the two documents this delta edits**: `tools/check-doc-values.sh` (exit 0) and
  `tools/check-doc-routes.sh` (356 section references and 8 skill references resolve, exit 0).
