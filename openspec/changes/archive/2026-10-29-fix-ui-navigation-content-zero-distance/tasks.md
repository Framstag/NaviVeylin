# Tasks — fix-ui-navigation-content-zero-distance

The change touches no compiled file, so no Gradle task exists here: the structural check that stands in is
`bash tools/check-doc-routes.sh` plus the documentation suites `tools/loop/gate.sh` selects for `*.md` and
`openspec/**` (`tools/loop/loop.conf`, `LOOP_GATE_SUITES`).

## 1. Red case

- [x] 1.1 Write `evidence/red-probe.sh`, which extracts §1's NAVIGATION content bullet from
      `guidelines/UI.md` and requires the zero-versus-unknown wording (`known zero`, `0 m`, `absent or NaN`,
      `--`, `stats line`, `hint's secondary text`) and the owning spec name. Run it on HEAD. Verify:
      `tests=1 failures=1`, the case `ui-navigation-content#zero-versus-unknown` named with each missing token
      (`evidence/red-on-head.xml`, `evidence/red-on-head.log`, 2026-10-29T07:30:05Z).
      *(spec: `navigation-ongoing-notification` — A known zero distance is not an unknown distance; docs-only
      change declares `skip_specs: true`)*

## 2. The guideline clause

- [x] 2.1 Append the clause to the NAVIGATION content bullet in `guidelines/UI.md` §1: a known zero renders
      `0 m`, an absent or `NaN` distance renders `--`, and the phone stats line and the car hint's secondary
      text follow it, citing `navigation-ongoing-notification` — *A known zero distance is not an unknown
      distance*. Verify: `sed -n '231,245p' guidelines/UI.md` shows the clause inside the same bullet, and the
      probe reports `tests=1 failures=0` (`evidence/green-after-fix.xml`, `evidence/green-after-fix.log`).
      *(spec: `navigation-ongoing-notification` — A known zero distance is not an unknown distance)*
- [x] 2.2 Check the restatement against `guidelines/UI.md` "Keeping this document honest": it states the
      rendered values and the two surfaces rather than copying the requirement's `remainingDistance` field name,
      rationale or formatter mechanism, and it cites the owning requirement. Verify: the clause names no
      `remainingDistance` field and no code symbol, and `bash tools/check-doc-routes.sh` exits 0 with every
      section reference resolving.
      *(spec: `documentation-ownership` — A rule has one normative home)*

## 3. Structural verification

- [x] 3.1 Run every suite the reviewed pathspec owes once through the project's gate, `./tools/loop/gate.sh`,
      from the change root. Verify: its summary line is quoted in `evidence/gate.log` and names all seven suites
      — `bash tools/gen-feature-list.sh --check-classification`, `tools/check-doc-routes.sh`,
      `tools/check-doc-routes-selftest.sh`, `tools/mutate.sh --mutations tools/check-doc-routes-mutations.json
      --list`, `tools/check-todo-references.sh`, `tools/check-openspec-hygiene.sh`,
      `tools/check-openspec-hygiene-selftest.sh` — the JUnit tallies are copied into `evidence/gate-tallies/`, and
      `LOOP-GATE: tests=6 failures=0 errors=0` reports no failure.
      *(round 2: round 1 gated only the `*.md` pairs because the archive move was still uncommitted and
      `openspec/changes/*/` is gitignored; the gate of 2026-10-29T07:36Z reads `paths=20` and runs all seven)*
- [x] 3.2 Validate the change: `openspec validate fix-ui-navigation-content-zero-distance --strict`. Verify:
      the command exits 0 and reports the change valid; record its output in `evidence/openspec-validate.log`.
- [x] 3.3 Re-run the red probe after the edit and copy its XML into `evidence/green-after-fix.xml`; confirm
      `tests=1 failures=0` and a timestamp after the edit. Verify: the probe's `PASS` line is in
      `evidence/green-after-fix.log`.

## 4. Evidence and archive

- [x] 4.1 Copy every cited XML into the change's `evidence/` directory and the item's evidence directory
      `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/702`, then run
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-29-fix-ui-navigation-content-zero-distance --root .`
      after the archive (`.log` = pre-archive run, `-archived.log` = post-archive and post-correction run).
      Verify: every `file:line` and every quoted `tests=`/`failures=` resolves; record the PASS line in
      `evidence/evidence-check-archived.log`.
- [x] 4.2 Archive the change, confirm the directory exists under `openspec/changes/archive/`, and confirm no
      capability spec changed (`.openspec.yaml` declares `skip_specs: true`). Verify: the archive directory
      carries `proposal.md`, `design.md`, `tasks.md`, `.openspec.yaml` and `evidence/`, and
      `openspec/changes/fix-ui-navigation-content-zero-distance` no longer exists.
- [x] 4.3 Leave the tree as the change leaves it: no commit, no push, no version control, no tracker write —
      the run's script owns those.
