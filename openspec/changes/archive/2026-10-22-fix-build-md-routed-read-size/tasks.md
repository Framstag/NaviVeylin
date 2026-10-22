# Tasks — fix-build-md-routed-read-size

The change touches no compiled file, so no Gradle task exists here: the structural check that stands in is
`bash tools/check-doc-routes.sh` on the repository plus the change's own probe (`guidelines/Build.md` §2a, the
scope block; `tools/loop/loop.conf`'s `LOOP_GATE_SUITES` maps `*.md` and `AGENTS.md` to that check).

## 1. Red case

- [x] 1.1 Write `evidence/red-probe.sh` with the four cases — `routed-read#bound` (no section of
      `guidelines/Build.md` above 12 000 bytes), `routed-read#numbers` (sections 1–12 present and the route check green,
      the counter-case), `routed-read#route` (the map names a lettered sub-section of each offender),
      `routed-read#verbatim` (each offender carries ≥ 4 lettered sub-sections and no line is rewritten). Run it on
      the base tree. Verify: `tests=4 failures=3`, three cases named with their messages, sections 10 (31 568),
      4 (23 380), 2 (23 235) and 6 (22 053) above the bound (`evidence/red-on-head.xml`,
      `evidence/red-probe.sh`, 2026-10-22T16:55:15Z).
      *(spec: all four scenarios of *The routing cost is measured*)*
- [x] 1.2 Record the condition the item claims beside it: `wc -c guidelines/Build.md` → `137276`, and the
      per-section table in `proposal.md`'s `## Why`. Verify: the four offenders' byte counts are the ones the
      red case prints.
      *(spec: A section carrying more than one question is split)*

## 2. Split the four sections

- [x] 2.1 `guidelines/Build.md`: section 2 → `§2a–§2e`, section 4 → `§4a–§4f`, section 6 → `§6a–§6h`, section 10 → `§10a–§10j`, each parent
      keeping its heading and body plus one index paragraph that names its sub-sections and their measured cost.
      Verify: `grep -n '^## ' guidelines/Build.md` lists 41 sections with sections 1–12 intact, and
      `routed-read#bound` passes with the worst section at 6 839 B (`evidence/green-after-fix.log`).
      *(spec: A section carrying more than one question is split)*
- [x] 2.2 Retarget the 18 intra-document references inside `Build.md` that pointed at text the split moved
      (section 4 → `§4b` where the forced-rerun bullet now lives, section 2 → `§2c` for the gate-timing record, section 6 →
      `§6d` for the concurrency table, and so on), and update the two heading-text references in `AGENTS.md`
      and one in `.pi/skills/run-tests/SKILL.md`. Verify: `bash tools/check-doc-routes.sh` exits 0 with
      `all 144 section references resolve`, and `routed-read#verbatim` passes.
      *(spec: A split section keeps its number and its citations; The split moves text instead of rewriting it)*
- [x] 2.3 Re-point the three `AGENTS.md` rows that routed sections 2/4, 6 and 10 at the lettered sub-sections, stating
      the measured per-section cost. Verify: `routed-read#route` passes naming 24 lettered Build.md sub-sections,
      and the row text still resolves (`bash tools/check-doc-routes.sh`).
      *(spec: The route names the sub-section)*

## 3. The invariant, falsified and restored

- [x] 3.1 Falsify `routed-read#verbatim`: reword one sentence of §6f in place, run the probe, restore. Verify: the
      case fails naming that line, then `tests=4 failures=0` again (`evidence/revert-check-verbatim.log`).
      *(spec: The split moves text instead of rewriting it)*
- [x] 3.2 Falsify `routed-read#route`: revert the `AGENTS.md` build row to the bare parents (sections 2, 3 and 4), run the probe,
      restore. Verify: the case fails with `the routing table names no sub-section of: §2a §4a`, then green
      (`evidence/revert-check-route.log`).
      *(spec: The route names the sub-section)*
- [x] 3.3 Falsify `routed-read#bound`: run the probe with `--bound 5000`, restore by omitting the flag. Verify:
      the case fails naming sections 7 and 9 as well, then green (`evidence/revert-check-bound.log`).
      *(spec: A section carrying more than one question is split)*

## 4. Verify and archive

- [x] 4.1 Run the run's gate once (`./tools/loop/gate.sh`), which for these paths is
      `tools/check-doc-routes.sh`. Verify: its summary line and the tally under
      `app/build/test-results/loop-gate/` are copied into `evidence/gate.log` and `evidence/gate-tallies/`
      (`LOOP-GATE`).
- [x] 4.2 Validate the change: `openspec validate fix-build-md-routed-read-size --strict`. Verify: zero failures,
      and the requirement body is under the validator's 500-character limit.
- [x] 4.3 Run `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-build-md-routed-read-size
      --root .` from the change root. Verify: no dangling pointer, then archive the change and confirm the delta
      merged into `openspec/specs/documentation-ownership/spec.md`'s *The routing cost is measured*.
