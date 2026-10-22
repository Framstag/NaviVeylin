# Review round 1 → the round-2 corrections, and what each one rests on

The reviewer refused the change on three blocking findings and seven notes. Nothing shipped was reverted: the
`AGENTS.md` block, the two requirements, the seven-case probe and the falsification all stand. What changed is
three statements that were wrong or broader than their own cases tested, plus the probe's ability to run after
the archive. The delta is edited in both places it lives — the archived change and the merged
`openspec/specs/documentation-ownership/spec.md` — so no sentence is re-archived.

All numbers below are quoted from `evidence/round2-checks.log`, one run at **2026-10-22T02:56:09Z** on the
corrected tree, and from `evidence/green-round2.txt` (**02:57:12Z**, `probe: 7 passed, 0 failed`).

## B1 — `--strict` "reports" the two rules

The shipped sentence was false: `--strict` does not own the report. Measured on the merged capability spec, on
the tree under review:

```
$ openspec validate documentation-ownership --type spec
Specification 'documentation-ownership' is valid
⚠ [WARNING] requirements[11]: Requirement text is very long (>500 characters). …
⚠ [WARNING] requirements[13]: Requirement text is very long (>500 characters). …
exit=0
```

and on a change delta with a lengthened requirement body:

```
$ openspec validate probe-…            # no --strict
Change 'probe-…' is valid
⚠ [WARNING] documentation-ownership/spec.md: ADDED "…": Requirement text is very long (>500 characters). …
exit=0
$ openspec validate probe-… --strict
… exit=1
```

What `--strict` changes is fatal-versus-ignorable, not reported-versus-unreported. Corrected in all four places
the claim stood:

- `AGENTS.md`: "a plain `openspec validate` reports both as warnings and exits 0, and `openspec validate
  <change> --strict` fails on them, so that command is run before the artifact is finished…";
- the ADDED requirement: "A plain `openspec validate` SHALL report both as warnings and exit 0, where
  `openspec validate <change> --strict` fails on them";
- the scenario, retitled *An over-long requirement is a warning until the strict command*;
- the paragraph the probe injects, which now says the same thing.

The case asserts both halves rather than the title's claim: `--strict` exits 1 **and** a plain run exits 0 with
the `[WARNING]` line (`anOverLongRequirementIsReported` PASS, `green-round2.txt`). Restoring the round-1 wording
in `AGENTS.md` is mutation `M2` and turns exactly one case red (`6 passed, 1 failed`).

## B2 — "each artifact rule that `openspec validate --strict` enforces"

A universal the entry document does not deliver: it states two rules, and the validator enforces more than two
(`Requirement must contain SHALL or MUST`, `Requirement must have at least one scenario`, `Why section must be
at least 50 characters`, the `## Purpose` placeholder rule, …). The requirement now says **"the two artifact
rules below"**, which is what it means and what its four scenarios test.

The corrected body is **483 characters**, so the change does not trip the rule it documents:

```
$ awk '/^### Requirement: The validator.s own rules/{f=1;next} f && /^#### /{exit} f && NF{c+=length($0)+1} END{print c}' \
    openspec/specs/documentation-ownership/spec.md
483
```

The two warnings the spec does report are pre-existing debt this change does not touch: `requirements[11]`
(724 characters) and `[13]` (529). They are named here rather than fixed — a 724-character requirement is a
different change, and editing it would put this change's name on text it was not asked to change.

The narrowing also settles the fourth rule round 1's report named — `## Why` over 1000 characters, which this
change's own `## Why` trips — as a **follow-up rather than a gap**: with the universal gone, the requirement no
longer claims to cover every rule the validator enforces, and the entry document states the two this change is
about. The `Why` bound is the same class of finding and belongs in its own item, not in a statement this change
would then have to widen again.

## B3 — three read cases asserted a bare substring

Each read case now asserts every clause of its THEN, with one pattern per clause and every pattern required:

| case | clauses asserted |
|---|---|
| `theWorkflowSectionStatesTheScenarioRule` | `MODIFIED … repeats … verbatim` **and** `verbatim … under their own titles` |
| `theWorkflowSectionStatesTheTextBound` | `500 characters` **and** `requirement body, not its scenarios` **and** `example belongs in a scenario` |
| `theStrictCommandIsNamedBesideTheRules` | `openspec validate … --strict` **and** `plain \`openspec validate\` reports both as warnings` |
| `theChangeSetIsValidatedByACommand` | the change's name **and** the requirement's name **and** the omitted scenario's title (the requirement name was printed but never grepped, which is the overreach the reviewer named) |

Patterns use `[[:space:]]+` for word gaps because the section is flattened from wrapped lines, so a rule that
wraps carries two spaces where a reader sees one — the reason the first corrected run failed
`example belongs in a scenario` while the sentence was present.

Falsification per clause, `M3` and `M4` in `round2-checks.log`:

- `M3` drops `under their own titles` → `theWorkflowSectionStatesTheScenarioRule` alone red, `6 passed, 1 failed`;
- `M4` drops `An example belongs in a scenario` → `theWorkflowSectionStatesTheTextBound` alone red, `6/1`;
- `M1` removes the whole added block → the three read cases red, `4 passed, 3 failed` (round 1's revert-check),
  and the restored tree `7 passed, 0 failed`.

## The probe could not run after the archive

The reviewer's own reading stopped at the point this would have bitten: `probe.sh` addressed
`openspec/changes/<name>/specs/...`, which after `openspec archive` no longer exists, so the four validator
cases would fail with `no delta spec` on the shipped tree. The probe now:

- resolves the change directory open **or** archived (`openspec/changes/archive/*-<name>`);
- when archived, copies it to `openspec/changes/probe-<name>` — an archived change is not in the open change
  set, which is exactly what `openspec validate --changes` reads — and its exit trap removes the copy;
- finds the project root by walking up to `AGENTS.md`, because an archived probe sits one level deeper.

Shown on the corrected tree: `probe: 7 passed, 0 failed` run from
`openspec/changes/archive/2026-10-22-…/evidence/`, and `openspec/changes/` holds no `probe-` directory
afterwards. The archive itself still validates in strict mode, driven from a temporary copy:
`Change 'probe-round2-validate' is valid`, exit 0.

## Notes the reviewer raised, answered or recorded

- **`tasks.md` 4.4's "exit 0" was unproven** — resolved without re-running the gate: `tools/loop/gate.sh` prints
  the `LOOP-GATE: tests=… failures=… (…)` line only in its success path (`gate.sh:142`), after the failing
  suites are named and the script exits 1 (`gate.sh:139-140`). A tally with no `— failing:` clause is a gate
  that reached the end; the line is quoted in `evidence/gate.log:8`.
- **`probe.sh`'s header said "the five cases" and "two drive the validator"** — the header now states seven
  cases, three reading `AGENTS.md` and four driving the validator, and the archive-or-open resolution.
- **The worker report claimed "two of the cases mutate this change's own delta"** — four do. `probe.sh`'s header
  is corrected in this round and this file records the corrected count; the round-1 report is left as it was
  written rather than rewritten.
- **`gate.log` and `gradle-suite-tally.xml` carry no timestamp** — the gate is not re-run to date them (a re-run
  destroys the evidence); the ordering argument is the one the reviewer already accepted (mtimes 04:48–04:49
  local, after the 02:34 evidence), and round 2 adds no gate run because no compiled file changed.
- **`evidence/surface.txt`'s exclusion lives on the command line, not in the claim** — the claim in
  `proposal.md` now carries the command verbatim.
- **The owning spec violates the bound it states** — recorded above (B2): `requirements[11]`/`[13]`, out of
  scope, named rather than silently inherited.

## The gate

Not re-run, and none owed: the correction touches `AGENTS.md`, two `spec.md` files, the change's own documents
and a shell probe — no compiled file, so `guidelines/Build.md` §2's scope rule applies and the structural checks
stand in. Round 1's gate stands as quoted:

```
LOOP-GATE: tests=325 failures=0 (./gradlew test -PforceTests --no-build-cache, tools/loop-selftest.sh, tools/sensor-selftest.sh, tools/check-action-runtimes-selftest.sh, tools/gen-feature-list-selftest.sh, bash tools/gen-feature-list.sh --check-classification, tools/check-doc-routes.sh)
```

(one line in `evidence/gate.log:8`; wrapped here only by the width of this file)

The checks this correction does owe were run on the corrected tree at 2026-10-22T02:56:09Z:
`check-doc-routes: all 120 section references resolve, all 5 skill references resolve` exit 0,
`Specification 'documentation-ownership' is valid` exit 0, the archived delta valid in strict mode from a copy,
and the probe green.
