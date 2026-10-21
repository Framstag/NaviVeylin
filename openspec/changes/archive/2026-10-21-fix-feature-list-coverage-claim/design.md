# Design — fix-feature-list-coverage-claim

## D1 — The tree decides the direction: restate, do not restore

The item offers two sides — restore a spec-level coverage check, or restate the row over highlights — and the
tree has already decided which one is the product: `openspec/specs/feature-list-generation/spec.md:115-121`
("The catalogue is a selection, not an inventory") requires that a user-visible spec no highlight names is
**deliberately absent**, and `guidelines/FeatureList.md` §1 says the same ("The catalogue is a pitch, not an
inventory"). The spec-level check the row still describes was the pre-shortlist behaviour, when the catalogue
was one bullet per user-visible capability; the archived `2026-10-09-generate-feature-list` records it firing
(`user-visible specs no bullet reaches: immediate-turn-instruction`). The shortlist narrowed the published set
and the check moved with it; only the prose stayed behind. Restating is hence not a preference between two
sound options — restoring would make the run fail on the 39 shipped user-visible specs no highlight names today
and would contradict a shipped requirement.

## D2 — The other case names that no suite carries

`guidelines/FeatureList.md`'s own maintenance rule (`:12`) is "a rule in this document names the case that
enforces it; a rule with no such case does not belong here". The red probe (evidence, `red-probe.sh`) joins every
`Cases:` paragraph in `guidelines/*.md`, extracts the backticked names and requires each to appear in a script
under `tools/` (or a `buildSrc` Kotlin source). On the run's base commit it reports 7 of 39 names missing — all
seven in this one file, in four groups:

| group | names | what the tree has instead |
|---|---|---|
| coverage | `gates: a user-visible spec no bullet reaches fails the run` | `gates: an unreached selling point fails the run` (`tools/gen-feature-list-selftest.sh:409`) |
| density | `density: one entry per capability fails the run`, `density: a merged area passes`, `catalogue: the report states the density it achieved` | nothing — no density check exists in `tools/gen-feature-list.sh` and no requirement asks for one; the shortlist itself is what keeps the catalogue from becoming an inventory |
| car placement | `catalogue: the car-only capability of a feature area stays under that area`, `catalogue: it is not moved into the car-only area` | `catalogue: a dual-surface capability tags both surfaces`, `catalogue: the car-only area is rendered under its own heading`; one capability in one area is a property of `areas.json`, not a check that can fail |
| terminology | `gates: the keyless bullet is named` | `gates: the keyless entry is named` (`tools/gen-feature-list-selftest.sh:378`) |

The fix keeps each rule and replaces the name with the check that exists; where no check exists (density) the
claim is deleted rather than a check invented for it.

## D3 — No enforcement check is added

The drift the item found has one mechanism behind it: nothing cross-checks a guideline's stated case names
against the suites. Making that a gate would be a new check with its own owner document, its own self-test and a
CI step (`.github/workflows/build.yml` runs the tool gates), and the choice of *whether* the documentation owes
that invariant is an owner decision, not a consequence of this defect — the item asks for the two sides to
agree, not for a new gate. The probe that proved the defect is therefore recorded as this change's structural
check (evidence + tasks) and not installed as a tool. The guideline's own maintenance rule is left standing as
the rule an author is held to.

## D4 — Blast radius and rollback

Two prose files, no code path:

- `guidelines/FeatureList.md` §1 (delete a `Cases:` block, three names), §4 (the `userVisible` bullet and its
  case list), §5, §6 (one table row, one case name).
- `.pi/skills/feature-list/SKILL.md` §3 (what a catalogue section covers) and §4 (the failure list).

Nothing reads these files at run time: the generator reads `tools/feature-list/*.json`, the specs and the
sections, never the guideline. The one reader that changes behaviour is a person or an agent following the
Documentation Map, which is the point of the change.

Rollback: `git checkout -- guidelines/FeatureList.md .pi/skills/feature-list/SKILL.md`. No state, no data, no
schema.

## Verification

- Red: the probe at the base commit exits 1 with 7 names missing (`evidence/probe-red-on-base.txt`).
- Green: the same probe on the changed tree exits 0, 0 names missing (`evidence/probe-after-fix.txt`).
- The suites that own the surviving names stay green and untouched:
  `bash tools/gen-feature-list-selftest.sh` → 105 passed, 0 failed
  (`evidence/gen-feature-list-selftest-after-fix.txt`),
  `bash tools/check-doc-routes.sh` → all 89 section references resolve
  (`evidence/doc-routes-after-fix.txt`).
- The classification gate still reports the same picture, so the measurement in the proposal is reproducible:
  `bash tools/gen-feature-list.sh --check-classification` → 176/176 classified, 19 highlights
  (`evidence/classification-after-fix.txt`).
- No device, no build: no compiled file is touched (`guidelines/Build.md` §2, scope block), so the run's Gradle
  gate is not owed by this delta and is not claimed here.
