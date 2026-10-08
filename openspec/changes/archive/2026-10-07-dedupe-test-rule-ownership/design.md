# Design

See `proposal.md` — **Why** for the measured drift (four quoted suite sizes, two forcing rules, four
copies of the classloader rule) and `specs/build-test-gate/spec.md` for the requirements this design
implements.

## Context

The repository's knowledge is organized on two axes, and the drift lives on the second one:

```
by concern      Design.md  UI.md  MapRendering.md  Build.md  Regulatory.md
by durability   repo-tracked:  guidelines/*, AGENTS.md, openspec/config.yaml
                gitignored   :  .pi/skills/*   (AGENTS.md:291-294)
```

`Build.md` already owns the gate: its header declares "Build, Test & Release Skills" and a maintenance
rule ("when a change supersedes a build convention here, update this document in the same change",
`guidelines/Build.md:7-8`), and it holds §2 (baseline + timing record), §3 (commands), §4 (result
evaluation, evidence rules), §6 (test constraints), §7 (coverage), §10-11 (on-device evidence, phone
measurement). Nothing about the gate is homeless; three specific rules are *copied*.

Constraints that shape the approach:

- `AGENTS.md:291` states the durability rule itself: a rule that must survive a fresh clone belongs in
  `AGENTS.md`, `openspec/config.yaml` or `guidelines/*.md` — never only in a skill. The `run-tests` skill
  currently violates that as the byte-identical holder of `Build.md` §6's classloader bullet.
- `openspec/config.yaml` is YAML whose guidance lists must stay quoted (`AGENTS.md` "OpenSpec Workflow";
  CI step "Check OpenSpec artifact hygiene", `.github/workflows/build.yml:100-122`).
- The spec contract is already written: `build-test-gate` requires "Local and CI gate recipes agree" and
  treats a divergence as "a defect in the recipe". `openspec/config.yaml`'s tasks guidance diverges today,
  so this change closes a violation rather than inventing a rule.
- A measurement is only evidence with its run attached (`Build.md` §2/§4): hence "the number lives where
  the run is dated", never in an entry document.

## Goals / Non-Goals

**Goals**

- One normative statement and one measurement table per gate rule, reachable from every document that
  needs it.
- The change-artifact guidance prescribes the same forcing rule as the documented gate.
- Every edit is checkable by inspection; no build, no test run, no device.

**Non-Goals**

- No new guideline file. The audit found no homeless *topic* — only duplicated *copies* — and a sixth
  required read would add a copy site rather than remove one.
- No movement of numbers between `Build.md` sections, no re-measurement, no `--rerun` of any gate.
- No mechanical drift guard in this change (see Open Questions).
- No `buildSrc` scanner, no CI workflow change, no Gradle configuration change.
- No spec change to `unit-test-suite-runtime`, `test-coverage` or the other build capabilities: their
  requirements are untouched by deleting duplicates.

## Decisions

### D1 — `guidelines/Build.md` is the single owner; no `guidelines/Test.md`

**Chosen.** Test rules and gate measurements keep their home in `Build.md` §2/§4/§6/§7. Every other site
(`AGENTS.md`, `openspec/config.yaml`, the skills) carries a section reference.

Rationale: `Build.md` is the file the gate's *other* rules already live in (native ABI scope, build cache,
SBOM, license gate, release versioning) and it is covered by one maintenance rule. A comparison of the
alternatives on the criterion that matters — number of places a rule must be edited:

```
rule: "a cached run is not evidence"
  today (Build.md §4 owner)             1 canonical + run-tests mirror          = 2 sites
  with guidelines/Testing.md as owner   1 canonical + Build.md §4 pointer
                                        + run-tests pointer                    = 3 sites
```

**Alternatives considered.**

1. *Promote test rules into `guidelines/Testing.md`.* Rejected: raises the site count (above) and splits
   §6 from the §4/§7 sections it cross-references (the fork budget is quoted by §2's baseline and by §7's
   coverage notes). It would also need a fifth entry in the `openspec/config.yaml` context line and in the
   `AGENTS.md` Documentation Map — new surfaces to keep in sync.
2. *`AGENTS.md` owns the agent-facing short form, `Build.md` keeps depth.* Rejected: two normative homes
   for the same rule is the definition of the drift we measured, and `AGENTS.md` is where the stale
   numbers already sit (`AGENTS.md:257,263`).
3. *No change; record the findings in `TODO.md`.* Rejected by the owner: the config guidance would keep
   generating tasks with the heavy forcing flag, and the stale "~3 minutes" figure would keep shaping
   iteration budgets.

### D2 — the invariant goes into `build-test-gate`, not into a new capability and not into a doc-only change

**Chosen.** Two spec-level changes: widen "Local and CI gate recipes agree" from two prescription sites
(two → the documented procedure, CI, and the change-artifact guidance), and add "A gate rule or
measurement has one documented home".

Rationale: the widest requirement already treats a recipe divergence as a defect, and `config.yaml`
diverging from `Build.md` §2/§4 *is* one. Adding the "stated once" requirement now is what lets the
deferred CI guard land as enforcement of an existing contract instead of as a new rule invented at gate
time. Precedent: `speed-up-build-test-gate` did the same — it moved the fork budget, concurrency and
wall-clock rules into `unit-test-suite-runtime` / `build-test-gate` rather than leaving them as prose.

**Alternatives considered.**

1. *`skip_specs: true`, pure documentation change.* Legitimate for docs (the proposal instruction names
   it), but then the config-guidance divergence has no contract to violate — the next session would read
   `--rerun-tasks` in `tasks.md` and have no way to call it a defect.
2. *Put the "stated once" rule in `unit-test-suite-runtime`.* Rejected: that capability is about a suite
   completing in one bounded invocation with attributable results. Its purpose is per-class
   attributability, not document ownership. It already has a sibling concern — "Documented procedure needs
   no batching" — and this change leaves that untouched.

### D3 — what counts as a "restatement", and which copy of the classloader rule survives

**Chosen.** The **measurement** (any number: class count, test count, wall time, gate duration) exists
exactly once, in `Build.md`. The **normative sentence** of a rule exists exactly once, in `Build.md` §6; an
entry document may carry a non-normative pointer plus the fact it owns.

Applied per site:

| Rule | Owner (normative) | Other sites become |
|---|---|---|
| Robolectric classloader rule | `Build.md` §6 | `AGENTS.md` keeps the *stub facts* it owns (committed test-only `.so`, test source set, load path, "the stub binds to the first classloader that loads it") and points at §6 for the rule; `Design.md:533` keeps its one-clause trap reference; the skill drops its byte-identical copy |
| Gate evidence and cache rules | `Build.md` §4 | the `run-tests` skill points at §4 instead of mirroring it |
| Fork heap, `maxParallelForks`, wall-clock scan, meson timeout | `Build.md` §6 | unchanged — single home already |
| `DpRect`/48 dp assertion rule | `Build.md` §6 | unchanged — `compose-geometry` (gitignored) points at it |

Rationale: `AGENTS.md` is the file a fresh clone and every new session reads, so it must stay actionable
without a second read — but what it owns is *project facts* (which artifacts exist, why they are
committed), not the test-authoring contract. That split keeps `AGENTS.md` useful and leaves the rule with
one normative statement.

**Alternative considered.** Move the classloader rule *out* of `AGENTS.md` entirely. Rejected: the section
also documents the two committed host stubs and the debug-suffix fallback (`libosmscout_client_javad`), and
a fresh clone that loses those facts fails a full-suite run for a reason no other document explains.

### D4 — the `config.yaml` edit keeps its entry quoted and is verified by the existing gates

**Chosen.** Edit only the text after `- "Every new invariant …"` in `rules.tasks`, keep the double quotes,
then run `openspec doctor` and
`openspec instructions apply --change dedupe-test-rule-ownership --json | jq .operationGuidance`.

Rationale: an unquoted `KEY: text` entry parses as a mapping, the array fails the array-of-strings check,
and the CLI silently drops that operation's guidance — the failure mode `AGENTS.md` records from
2026-10-04. The CI step "Check OpenSpec artifact hygiene" re-checks it on every push, so the verification
already exists and needs no new tooling.

### D5 — threading, lifecycle, native boundary

Not applicable and deliberately not addressed: no new component, dispatcher, coroutine scope, surface or
lifecycle owner is introduced, and no JNI or native source is touched. `guidelines/Design.md` §4
(threading) and §5 (native boundary) therefore impose nothing on this change; `guidelines/MapRendering.md`
is untouched.

## Risks / Trade-offs

- **The skill edit is machine-local, so it cannot be the durable half of the fix.** `.pi/` is gitignored;
  deduping `run-tests/SKILL.md` helps this workstation only, and the skill is the file a "run the tests"
  request loads — hence the most likely place for a copy to be re-added. → Mitigation: the durable halves
  are the `Build.md` owner statement, its maintenance rule naming the mirror sites, and the spec
  requirement; the skill edit is recorded as housekeeping, not as evidence.
- **Deleting the skill's copied blocks makes it depend on `Build.md`.** A session that loads only the skill
  must follow one reference before it can act. → Mitigation: the pointer names the exact section and the
  consequence ("a copy is what drifted"), so the reader knows what to look for.
- **`AGENTS.md` loses a number a reader may want inline.** "~3 minutes per gate" is what made the entry
  useful. → Mitigation: the rule it supports ("one flavor per iteration, the second owed at completion")
  stays; only the figure goes. The figure is a measurement with a run attached, and the run is dated in
  §2/§6.
- **A doc-only change invites the objection that no test proves it.** → Accepted and stated in the
  proposal: the scenarios are inspection- and `grep`-checkable, no device is involved, and the "measure
  first" rule of `AGENTS.md` applies to pixels, not to document ownership.
- **The drift can return** (no guard in this change). → Mitigation: the requirement exists, so the guard
  is enforcement rather than invention; `Build.md`'s maintenance rule names the mirror sites.
- **Editing `openspec/config.yaml` can silently drop the whole `apply` guidance** if quoting is lost.
  → Mitigation: D4's two commands plus the CI hygiene step.

## Migration Plan

Ordering is forced by dependency: the owner statement must exist before the pointers can reference it, and
the config edit must be verified before it is trusted.

```
1  Build.md   maintenance rule names the mirror sites        (no reference target needed)
2  Build.md   §2/§6 phrasing stays the owner (no text move)  -> verify by grep
3  AGENTS.md  drop the two restated numbers, add the section reference
4  AGENTS.md  classloader section: keep the stub facts, point §6 at the rule
5  run-tests  drop the duplicated blocks, point at §4/§6      (machine-local)
6  config.yaml tasks guidance: forcing rule -> -PforceTests --no-build-cache
7  verify: grep for the four quoted sizes (only Build.md), openspec doctor,
   the guidance jq check, openspec validate
```

**Rollback:** revert steps 1-6 as text. Nothing reads these documents at runtime, no build output, cache
key, APK, SBOM or license asset depends on them, so a revert needs no rebuild and no re-measurement.

## Open Questions

1. **The shape of the deferred guard.** A CI shell step beside "Check OpenSpec artifact hygiene" is the
   cheap shape, but a naive pattern would trip on legitimate literals — `AGENTS.md:49` says "52 tests, run
   as part of every build" about `buildSrc`, which is not a suite measurement. The pattern therefore has to
   require a suite context (`N classes`, a gate duration `XmYs`, "N tests" next to "suite"/"flavor"), and
   the check needs an allow-list with a reason per entry. Deferrable: it does not change the specs, the
   approach or the task breakdown, and the requirement it would enforce is written by this change.
2. **Whether `AGENTS.md` should quote a size at all.** If the guard lands, the simplest steady state is
   that `AGENTS.md` names suites by task path and never by size. Left open; it affects only that file's
   wording.
