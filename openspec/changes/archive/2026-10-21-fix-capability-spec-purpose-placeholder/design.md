## Context

The defect is one sentence `openspec archive` writes into a capability it creates, and the absence of
anything that reads it. `openspec/specs/navigation-ongoing-notification/spec.md:4` and
`openspec/specs/render-mode-switch/spec.md:4` still carry it; `highlight-measurement` carried it until the
`fix-highlight-longest-run` archive repaired that one by hand.

Measured on base `44b673f`, 2026-10-21T11:21:39Z:

- `openspec validate navigation-ongoing-notification --type spec` → `Specification 'navigation-ongoing-notification' is valid`, exit **0**, plus one `⚠ [WARNING] overview: Purpose section is still a placeholder…`.
- the same for `render-mode-switch`, exit **0**.
- the probe `grep -rln 'TBD - created by archiving' openspec/specs/*/spec.md` prints two paths, exit 1.

## Root cause

The placeholder is written by the archive and read by nobody: no check over `openspec/specs/` looks at a
`## Purpose`, and the validator that does notice it exits 0, so a capability spec can ship without saying
what it is for, and only a hand edit can repair it because the CLI reads a delta's `## Purpose` only when
the capability is created.

## The single fix

Four edits, no decision between options:

1. Write one sentence into each of the two `## Purpose` sections, in the main specs (a delta cannot).
2. Add the check to the CI step `Check OpenSpec artifact hygiene` — the same step, the same inline style,
   the same `fail=1` accumulation as its two existing checks — so the placeholder is reported instead of
   waiting for a reader. It reads only the lines between a `## Purpose` heading and the next `## ` heading.
3. Add the requirement this check enforces to `documentation-ownership`, which owns the project's documents
   and the properties of them a command can check.
4. Correct `AGENTS.md` "OpenSpec Workflow", which says **two** artifact rules are CI-gated.

## Alternatives the evidence eliminated

- **Repair through a change delta.** Impossible: the CLI reads a delta's `## Purpose` only when the
  capability is created, and both capabilities exist. The CLI's own warning says so, and the two specs are
  unchanged by every change archived since.
- **Write the two sentences and stop there.** Eliminated by occurrence: this is the second and third
  capability to carry the placeholder, and `highlight-measurement` was repaired by hand inside the archive
  that created it. A hand repair with nothing reading the section reproduces the defect at the next archive
  that creates a capability.
- **A standalone `tools/check-spec-purpose.sh` with its own `tools/*-selftest.sh`.** Eliminated as wider for
  the same coverage: the hygiene step already owns this class — a structural invariant over `openspec/` that
  the CLI does not enforce — and runs in the same job. A new script adds a second invocation path, a second
  self-test and a second glob to keep right, and the step's two existing checks are inline for exactly that
  reason.
- **Rely on `openspec validate`.** Eliminated by measurement: exit 0 with the warning present (above).
- **Report it upstream to the OpenSpec CLI.** Not a fix for this tree; the placeholder text is deliberate
  (it is the archive's note to a human), and this change does not depend on the CLI changing.

## Blast radius

| file | edit |
|---|---|
| `openspec/specs/navigation-ongoing-notification/spec.md` | the `## Purpose` sentence |
| `openspec/specs/render-mode-switch/spec.md` | the `## Purpose` sentence |
| `.github/workflows/build.yml` | ~8 lines inside one existing step |
| `AGENTS.md` | "two" → "three", plus one bullet naming the rule |
| `openspec/specs/documentation-ownership/spec.md` | one ADDED requirement, two scenarios (via this change) |

No compiled file, no Gradle module, no native code, no app behaviour. No new component, so no threading or
lifecycle question: the check runs in CI's shell and in an agent's shell, touching no state.

## Risk

The check is an exact string match on the archive's own sentence. A whole-file `grep` was the first cut and
it flagged `documentation-ownership` — the spec that states this rule quotes the sentence in its requirement
prose — so the check reads only a `## Purpose` section, and the string may appear freely elsewhere: in
`guidelines/`, in changes, in requirement prose and in archived evidence, including this change's own.
What would remain a false positive is a spec that quoted the sentence inside its Purpose prose; the check's
message says what to write there instead.

## Rollback

Revert the four file edits. The two specs return to the placeholder, the check disappears with the step, and
`AGENTS.md` says two again. Nothing else reads any of them.

## Verification

- `evidence/red-probe.txt`: on base `44b673f`, 2026-10-21T11:22:13Z, the placeholder is carried by
  `openspec/specs/navigation-ongoing-notification/spec.md` and `openspec/specs/render-mode-switch/spec.md`
  (the diagnosis probe, a whole-file grep — the red half before the check was narrowed).
- `evidence/red-probe-green.txt`: the shipped check over the repaired tree — `Every capability spec states
  its Purpose.`, exit 0 (2026-10-21T11:24:10Z).
- The CI step's body run locally over the tree (`evidence/hygiene-step.txt`): `fail=0`, "OpenSpec artifacts
  are consistent."
- `openspec validate --all` and `openspec doctor` (`evidence/openspec-validate-green.txt`,
  `evidence/openspec-doctor.txt`).
- Revert-check: put the placeholder back into one spec, the probe exits 1 naming it, restore, probe exits 0.
  Evidence: `evidence/revert-check.txt`.
- No Gradle gate is owed: the delta touches no compiled file (`guidelines/Build.md` §2, its scope block).
