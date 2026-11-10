# Tasks

Reference: `proposal.md` for the root cause and the red run, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The record line the rule owes, red first

Requirement: "A run counts the reads its sessions repeated", its two scenarios.

- [x] 1.1 `repeat#aRepeatIsRecordedAsWhatItWithheld` in `tools/loop/tests/loop-cases.sh`: one path read twice through
      the CLI with `--record`, so the second read is the rule's repeat — the record holds exactly one
      `LOOP-TOOL-RESULT-REPEAT: ctx_read chars=<n> kept=<k>` line, `<n>` the file's size and `<k>` the size of the
      notice the child was handed (`0 < kept < chars`), and no path in the line. Its second half: a first read and a
      read after the session edited the path record no repeat line at all, so the record counts replacements and not
      reads.
- [x] 1.2 `report#anItemRecordsItsRepeatReads` rewritten: its three items' streams move into per-item directories,
      because the record is read from beside the stream; each item's `tool-result-trims.log` holds the repeat lines its
      stream's repeats describe (20+30 characters withheld for #21, 40 for #22, none for #23). It asserts
      `metrics.withheld.reads`/`.chars` beside `dupReads`/`dupChars`, the trend's `reads:` line's new text, and — with
      the field deleted — the dash that says the state predates it.
- [x] 1.3 `repeat#aRepeatAfterAnEditIsHandedOverWhole` is unchanged in what it asserts; the "a verification records no
      repeat line" half is 1.1's second part, where the record is the subject.
- [x] 1.4 `# files:` declarations: `tools/loop/tests/loop-cases.sh tools/pi-trim-result.mjs` for the new case, the
      rewritten one keeps its own; `driver#everyCaseDeclaresTheFilesItCanReach` passes (it is also the gate plan's first
      line, run before anything else).
- [x] 1.5 The red run on the base `d5d2e668`, the cases and nothing else:
      `repeat#aRepeatIsRecordedAsWhatItWithheld` FAILs — *the repeat rule records nothing of the repeat it replaced: no
      record at …/trims.log* — and `report#anItemRecordsItsRepeatReads` FAILs — *the record beside the stream is read,
      and its two repeat lines are counted: expected '2', got 'null'*. Both XMLs are in
      `.pi/issue-loop/items/831/` (`red-on-HEAD-*.xml`, 2026-11-10T16:25:31Z).

## 2. The fix

Requirement: the same.

- [x] 2.1 `tools/pi-trim-result.mjs`: `repeatRecordLine` beside `trimRecordLine`, and `repeatResult(text, read, bound,
      tool, record)` appending it where it replaces the text — best effort, never throwing. `trimContent` and the CLI
      pass the tool and record they already hold; the header states the second line shape.
- [x] 2.2 `tools/loop/state.sh`: `repeat_withheld` sums `<dirname events>/tool-result-trims.log`'s
      `LOOP-TOOL-RESULT-REPEAT` lines (`chars - kept`, floored at zero) and `child_usage_json` records
      `metrics.withheld`; `null` in the empty-usage fallback, and for a file that does not exist.
- [x] 2.3 `tools/loop/report.sh`: the trend's `reads:` line prints `<n> chars in the repeat results · <w> chars of them
      withheld by the repeat rule` beside the worst item, and the block comment says why the raw volume is not the
      re-injected one. `re-injected` is gone from the report's output.
- [x] 2.4 The green run: `--group repeat` 5/5, `--group report` 30/30, `--group state` 38/38 — logs in
      `.pi/issue-loop/items/831/green-*.log`.

## 3. The falsification and the structural checks

- [x] 3.1 `96.3` — the record append removed → `RED repeat#aRepeatIsRecordedAsWhatItWithheld` (*the repeat rule records
      nothing …*), restore green (`mutation-96.3.log`).
- [x] 3.2 `96.2` retargeted at the withheld clause — ` withheld by the repeat rule` → ` re-injected` →
      `RED report#anItemRecordsItsRepeatReads`, restore green (`mutation-96.2.log`).
- [x] 3.3 `bash tools/loop/tests/loop-mutations.sh --check` over the whole set: 318 records, every one applies to the
      file it names.
- [x] 3.4 `./tools/check-openspec-hygiene.sh` — 17 open changes consistent, every capability spec states its Purpose;
      `./tools/check-portability.sh` — 56 engines name no project value (a local called `held` collided with one and was
      renamed `withheld_json`); `./tools/check-doc-values.sh` and `./tools/check-doc-routes.sh` unchanged and green.

## 4. The prose the change makes false

- [x] 4.1 The delta in `specs/agent-issue-loop/spec.md` (the shipped requirement takes it at the archive): the
      requirement and its first scenario carry the withheld figure and the clause that the raw volume is not called
      re-injected; the second scenario carries the record line.
- [x] 4.2 `guidelines/Backlog.recipes.md` section 7a (the bound and the repeat rule) and section 7e (the report's trend line): the two
      halves, the record line, `metrics.withheld` beside `dupChars`, and the new case and mutation records.

## 5. The gate (not run by this change's own pass)

- [ ] 5.1 The gate child runs `tools/loop/gate.sh --base d5d2e668e5acb4cffb47a832b83843f0d7e91be1` on this diff and
      records the tally — the loop suite is owed whole, because the delta touches `tools/loop/tests/loop-cases.sh`,
      which the suite's own case file declares as core.
