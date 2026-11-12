# Fix — the run's gate column is the tally the item's own gate wrote

## Why

At this change's base (`4a14574f`) `tools/loop/run.sh:2842` read the gate's evidence as every XML under any path holding
`test-results`, anywhere in the worktree, and `head -20` capped it:

```sh
gate_xmls=$(find "$worktree" -path '*test-results*' -name '*.xml' 2>/dev/null | head -20)
gate_found=$(printf '%s' "$gate_xmls" | grep -c . || true)
```

Nothing in that selection belongs to the item. In this tree, before any item runs, `find . -path '*test-results*'
-name '*.xml' | wc -l` prints **24**, and `git ls-files` counts **24** of them — 21 in
`openspec/changes/archive/2026-10-27-fix-client-java-coverage-floor/evidence/module-test-results/` and 3 in
`tools/tests/fixtures/gate-timings/root/app/build/test-results/` (measured 2026-11-11 in this change's own worktree).
`head -20` makes `gate_found` the constant **20**, so the progress line every item prints is the same number, the
`gate_field` of a child that made no claim is `<20> xml`, and the arm `[ -n "$LOOP_GATE_CMD" ] && [ "$gate_found"
-eq 0 ]` (`run.sh:2925`) — "the report claims a gate result but no test-results XML exists in the tree" — cannot fire
in this repository whatever a child wrote. An item that ran no gate passes the reading,
and the `closed` arm's `evidence_paths` (`run.sh:3282`) names a file that stood before the item as its gate evidence.

The two predicates are also two rules over one tree: `clear_stale_test_results` (`run.sh:1435`) removes a directory
named `test-results` whose parent is a `build` directory before every worker, and the gate writes its tallies into
exactly one such path (`LOOP_GATE_TALLY_DIR`, `tools/loop/loop.conf:721`: `app/build/test-results/loop-gate`). The
reading matches `*test-results*` — a different, wider predicate — and the tracked files it catches there are cleared
by neither.

**Root cause, in one sentence:** the run reads the gate's evidence with a `find` over every `test-results` path in the
tree instead of the one directory the profile names for the tallies a gate writes, so the column counts files that
stood before the item and the no-evidence arm can never see an empty set.

**The field's two halves, from the owner's decision (2026-11-11T19:18:12Z).** The reading above is one half. The
other is what the field is set to when the reading finds nothing: `gate_field="${gate_claim:-$gate_found xml}"`
wrote `0 xml` — a count of files that stood before the item — for an item that claimed no gate result, and
`tools/loop/report.sh:688` reads an **empty** `metrics.gate` as the gate a caller is owed and lists the item on its
`re-run the gate for #N` line (`report.sh:687`). Measured over the run of 2026-11-11T11:37:08Z (`.pi/issue-loop/run.json`):
four skipped items carried `gate: "0 xml"` and none of them appeared in that list. The refusal arm is the same
field's other side: it fires only on a report that **claims** a gate result, so a claim over an empty directory blocks
the item and names the directory, while an item that claimed none records the absence as absent.

**Red test:** `run#theGateColumnNamesThisItemsGateResult` (`tools/loop/tests/loop-cases.sh`). Its fixture is the tree
this repository is: a tracked archived change whose evidence directory is named `module-test-results`, an item whose
children claim no gate and leave no tally, and a second item whose own gate leaves one. On HEAD the run prints
`gate · read — 1 test-results XML in the tree` for the first item and `read — 2` for the second (its own plus the
tracked one), records `1 xml` for an item that ran no gate, and the report never names it — `evidence/red-on-head.xml`,
`tests=1 failures=1` (2026-11-12T11:56:24Z), transcript `evidence/red-on-head.log`. The case asserts both halves: the
ungated item records an empty field and appears on the report's `re-run the gate for #21` line, and the gated item
records 1 tally, closes on it and names that tally as its evidence. The arm that blocks a claim over an empty
directory keeps its own case, `run#claimedGateWithoutEvidenceIsBlocked`.

**Owning capability:** `agent-issue-loop`, "The run verifies rather than trusts" — its scenario "The gate's evidence
is read by the run" (`openspec/specs/agent-issue-loop/spec.md:617`), whose case is
`run#claimedGateWithoutEvidenceIsBlocked`: the run quotes the evidence it found. This delta adds "The gate's evidence
is the tally this item's gate wrote".

**Owning guideline section:** `guidelines/Backlog.recipes.md:755` "The gate and its suite", the paragraph "The
directory a run writes its tallies into is that run's" — the section that owns the tally directory and the one run
that may write it. The rule lands beside it: the same directory is the one reading of the gate's evidence, and the
run is one of its readers.

surface: 1 case added, 2 rewritten, 8 fixture sites realigned, 2 mutation records — `tools/look.sh
'app/build/test-results' --path tools/loop/tests`: 29 matches in `tools/loop/tests/loop-cases.sh`, 25 of them in
`app/build/test-results/loop-gate` (the tally directory); the selection's call sites are `stub_write`'s shared action
(the fixture 40-odd closure cases use), seven hand-written actions of cases that close on a gate result, and two
assertion sites that name the refusal's text; the rest are the `worktree` case's build output, the evidence-check
fixtures and the gate-timings fixtures, which no reading of the column touches.

## What changes

- `tools/loop/run.sh`: the gate's evidence is the `*.xml` files in `<worktree>/<LOOP_GATE_TALLY_DIR>` — the directory
  the profile names, cleared by the worker's start (`run.sh:1435`) and by the gate's own sweep before it writes
  (`gate.sh`, issue #848). The progress line names that directory, and the no-evidence arm's reason names it too — and
  fires only on a report that claims a gate result. The item's field is the claim when there is one, the count when the
  directory holds tallies, and **empty** when there is neither.
- `tools/loop/report.sh`: unchanged, and that is the point — its selector (`report.sh:688`) already reads an empty
  `metrics.gate` as the missing result; the change is what lets a record carry one.
- `tools/loop/lib.sh`: the comment above `gate_tally_state` lists the shapes the run's tally takes; the count it names
  is the tallies the run found, not the test-results XMLs of the tree.
- `tools/loop/tests/loop-cases.sh`: the red case above; the fixture's gate XML moves to where the gate writes it, in
  `stub_write`'s shared action and in seven hand-written actions; and the two cases that quote the arm's reason quote
  the new one.
- `tools/loop/tests/loop-mutations.sh`: two records (`849.1` the reading, `849.2` the empty field).
- `guidelines/Backlog.recipes.md:755` "The gate and its suite": the rule beside its measurement.
- `openspec/specs/agent-issue-loop/spec.md` (delta): the new requirement and its one scenario.

## Non-goals

- **Which XMLs appeared after the item started.** In a fresh worktree every tracked file carries the checkout's own
  mtime, so the 24 tracked matches arrive at the item's start and this reading answers exactly what the broad `find`
  answers. The measurement is in `design.md`.
- **`measure.sh`'s own reading** of the tally directory (`tools/loop/measure.sh:155`): it already reads that one
  directory, which is why the change is the run's half of a rule the block already keeps.
- Making the arm's reading *narrower* than the gate's writing rule — a second predicate is the defect, not the fix.
