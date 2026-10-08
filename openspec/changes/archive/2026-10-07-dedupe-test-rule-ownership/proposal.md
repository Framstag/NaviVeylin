# Proposal

## Why

The gate's rules and its measurements are stated in more than one place, and the copies have drifted out
of agreement. Measured on 2026-10-07:

- the current `:app` suite size is quoted at **four** values — `215 classes / 1635 tests` (`Build.md`
  §2 baseline, 2026-10-04), `219 / 1666` (§2, 2026-10-05), `218 / 1649` (§6 table), `221 / 1719` (§6 gate,
  2026-10-06) — while `AGENTS.md` carries the **oldest, undated** one ("the same 215 classes (~3 minutes
  per gate)") and the slowest baseline as if current ("measured 8m25s on 2026-10-04" against a
  2026-10-06 gate of 4m54s). An agent budgeting an iteration from `AGENTS.md` plans for ~3 minutes.
- the forcing rule disagrees: `guidelines/Build.md` §2/§4/§6 and the `run-tests` skill prescribe
  `-PforceTests --no-build-cache` (with `--rerun-tasks` only when the change touched more than the
  tests), while `openspec/config.yaml`'s tasks guidance still prescribes `--rerun-tasks` for a
  revert-check's green half — and that file is what a change's `tasks.md` is written from.
- the Robolectric classloader rule is stated **four** times (`AGENTS.md:396`, `Build.md:496`,
  `Design.md:533`, and byte-identical inside the gitignored `run-tests` skill), so the copy that is most
  likely to be edited is the one no repository check can reach.

`build-test-gate` already requires that "the documented full gate and the CI unit-test step SHALL run the
same task set and the same forcing rule" and that "a difference between what the two exercise SHALL be
treated as a defect in the recipe". By the spec's own words one of these copies is therefore already a
defect. The capability's purpose is that "a gate result is comparable between runs and between a
workstation and CI" — four quoted sizes and two forced-run recipes break exactly that comparability.

Why now: this was found by an audit of testing knowledge across the guideline documents, while evaluating
whether it should be extracted into a new `guidelines/Test.md`. The audit's conclusion is that the
document set is already organized by concern and by durability, and that the defect is duplication, not
location — so the cheapest correct fix is ownership, not a new document.

## What Changes

- **`guidelines/Build.md` stays the single owner** of test rules and of every gate measurement. It already
  holds §2 (baseline and timing record), §4 (result evaluation and evidence) and §6 (test constraints); no
  section is moved into a new file.
- **`AGENTS.md` drops the restated numbers** at lines 257 and 263 ("measured 8m25s on 2026-10-04", "the
  same 215 classes (~3 minutes per gate)") and points at `guidelines/Build.md` §2/§6 instead; the rule it
  states (one flavor's suite per iteration, the second owed at completion) stays.
- **The `run-tests` skill drops its duplicated blocks** — the gate-procedure text that mirrors `Build.md`
  §4 and the verbatim classloader bullet — and references the sections instead. This is machine-local
  (`.pi/` is gitignored) and is therefore a housekeeping edit, not the durable half of the fix.
- **`openspec/config.yaml`'s tasks guidance is corrected** to the same forcing rule the documented gate
  uses (`-PforceTests --no-build-cache`; `--rerun-tasks` only when the change touched more than the
  tests). The entry stays quoted, as the CI hygiene check requires.
- **`Build.md`'s maintenance rule names its mirror sites** — `AGENTS.md`, the `config.yaml` tasks/archive
  guidance, and the build/test skills — so a later edit knows which copies to repair. Today it only says
  "update this document in the same change".
- **Not changing:** no test, build script, source, manifest, resource or workflow change; no new
  guideline document; no gate re-run is required to land this change.

**Classification:** additive. **Rollback:** revert the four text edits; nothing at runtime observes them.

## Capabilities

### New Capabilities

None. The repository's document set is deliberately organized by concern
(`Design.md` / `UI.md` / `MapRendering.md` / `Build.md` / `Regulatory.md`) and by durability
(`AGENTS.md` / `config.yaml` / guidelines vs. gitignored skills); a new document would add a copy site
rather than remove one.

### Modified Capabilities

- `build-test-gate`: two spec-level changes.
  1. The requirement "The documented full gate and the CI unit-test step run the same task set and the
     same forcing rule" widens to **every site that prescribes the gate**, including the change-artifact
     guidance in `openspec/config.yaml` that a change's `tasks.md` is generated from. As written it
     covers two sites and misses the third, which is the one that has drifted.
  2. A new requirement: **a gate rule or a gate measurement is stated once, and every other document
     that mentions it references it by section** — so comparability of gate results holds across the
     documents that quote them, not only across runs.

### Capabilities considered and not changed

- `unit-test-suite-runtime` — the fork budget/concurrency facts stay where they are (`Build.md` §6); this
  change moves no number, it deletes copies of numbers that already have a home. The classloader rule has
  no spec of its own and its behavior is unchanged, so none is added for it here.
- `test-coverage`, `kover-aggregate-report`, `build-jvm-toolchain`, `ci-unit-test-jni`, `build-sbom` — no
  requirement touched.

## Impact

**Documents (all four edits are text-only).**

| File | Change |
|---|---|
| `guidelines/Build.md` | maintenance rule (lines 7-8) names the mirror sites; §2/§4/§6 remain the canonical statement |
| `AGENTS.md` | lines 257 and 263: drop the quoted numbers, keep the rule, add the section reference. Line 396 (classloader rule) stays — `AGENTS.md` is the fresh-clone entry document and keeps the short form |
| `openspec/config.yaml` | `rules.tasks` entry "Every new invariant … `--rerun-tasks`" → the current forcing rule; entry stays quoted |
| `.pi/skills/run-tests/SKILL.md` | duplicate gate-procedure text and the copied classloader bullet replaced by section references (gitignored, machine-local) |

**Not affected.** `app/`, `auto/`, `core/`, `osmscout-client-java/`, `buildSrc/`, `tools/`,
`.github/workflows/build.yml`, all test sources, manifests and resources. No Android component, no
Gradle configuration, no native or JNI code — so there is nothing to state about submodule patches or
bridge-module overrides.

**Guidelines referenced.** `guidelines/Build.md` is the document this change edits and the one that owns
the rules it deduplicates. `guidelines/Design.md` §11 is checked and deliberately left alone: it states
authoring traps (coroutines, Robolectric, `DpRect`) for a different audience, not gate rules or
measurements. `guidelines/UI.md` and `guidelines/MapRendering.md` are untouched — the `DpRect`/48 dp rule
has a single durable home in `Build.md` §6 already.

**Scope.** Repository-wide documentation ownership plus one change-artifact guidance file. Nothing is
phone-, tablet- or car-specific; the parity/deviation rules of `guidelines/UI.md` do not apply.

**Previous specifications changed.** `build-test-gate` only.

**Verification.** The two new/strengthened scenarios are checkable by inspection and by `grep`:
the four suite-size readings must appear only in `guidelines/Build.md`, and no other file may prescribe a
forcing rule that differs from §2/§4. The change needs no build and no test run; the CI step
"Check OpenSpec artifact hygiene" plus `openspec validate` and `openspec doctor` cover the config edit.

**Deferred, deliberately not part of this change.** A mechanical guard (a CI shell step next to "Check
OpenSpec artifact hygiene", failing when a suite measurement appears outside `guidelines/Build.md`) was
considered and deferred. It is cheap to add once the invariant exists as a requirement — which is why
change 2 above is a spec delta rather than a doc tweak.
