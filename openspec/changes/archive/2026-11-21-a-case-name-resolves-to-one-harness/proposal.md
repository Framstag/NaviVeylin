# Proposal — a scenario's case name resolves to one harness, and the unclaimed ones are counted

## Why

Every `#### Scenario:` in a shipped spec carries a `**Case**:` line naming the case that exercises it — 1252 such
lines across the project's specs, naming **597 distinct cases**. Nothing checks that a name resolves to a case that
exists, and nothing checks the reverse: whether a case the suites run is claimed by any scenario at all.

Measured 2026-11-14 on the committed tree:

- **1252** `**Case**:` lines, **597** distinct names, over the four harnesses that exist (the loop suite 461 cases,
  the sensors suite 93, the issue tooling's Java cases 18, and the Gradle/Kotlin suites of the app, `:auto`,
  `:core` and `buildSrc`).
- **Zero dead references today** — the 30 names a loop-only comparison could not resolve all resolve elsewhere (28
  in the sensors suite, 1 in the issue tooling's Java cases). That is the good news and it is why this change is
  small: there is no backlog to clean, only a property to pin.
- **Two groups are claimed by two harnesses** — `run#` and `report#` exist in the loop suite *and* in the sensors
  suite, and one full name is already in both: `run#dryRunTouchesNothing`. No scenario names it yet, so the
  collision is latent rather than harmful — which is exactly when a check is cheap.
- **69 loop cases are claimed by no scenario** (461 run, 392 named). Most are harness-level (`driver#`, `stub#`,
  `usage#`), but nothing separates "a case that proves a helper" from "a case that proves behaviour no requirement
  knows about".

The one rule that exists today is the readiness checker's, and it is deliberately narrower: a change's *delta*
must carry a `**Case**:` line, and the checker never asks whether the name it carries is real
(`openspec/specs/change-readiness`, "The checker SHALL require each `#### Scenario:` … to carry a `**Case**:`
line"). So the whole traceability link from requirement to test is prose a reviewer believes.

## What Changes

- **`tools/check-case-names.sh`** — one read-only check, two directions:
  - **forward**: every `**Case**:` name in every shipped spec resolves to a case that exactly one harness declares;
    two harnesses declaring the same full name is a refusal naming both;
  - **backward**: every case a declared harness runs but no scenario names is **printed as a count**, with the
    list under `--json` — a print, never a refusal, because a case may legitimately prove a helper.
- **`tools/case-harnesses.json`** — the registry: each harness, the file that defines its case list, and the
  groups it claims. A group two harnesses claim is legal and *declared*, so a reader learns both are possible from
  the registry instead of from an accident; a full name in two harnesses is not.
- **The check reads names, never meaning.** It compares identifiers against declared lists and reads no sentence
  for what it says — the lesson the readiness checker records: a prose-reading rule was "wrong about 48 of 49
  verdicts" (`guidelines/Backlog.md` §6).
- **Scope**: tooling and the specs' metadata only. No app code, no native code, no dependency, no change to any
  existing check's verdict. Additive: the check can land green on day one, because the baseline is green.
- **Not this change**: any claim that a spec and the engine *agree in content*. That is not mechanisable, and a
  check that pretended to would be the false confidence this project's own readiness rule was written against.
- **Sequencing**: this lands **last** in the epic `#895`. The registry it needs is the final group list, and two
  earlier children change it — the engine split re-points ~200 case declarations, and the protocol core adds the
  `core#` group and a second case source. Landing the registry first means editing it in both.

## Capabilities

### New Capabilities

None. A new capability would also owe a row in `tools/feature-list/specs.json` (a shipped spec id missing there
fails the feature-list run), which buys nothing for a check that extends one contract that already exists.

### Modified Capabilities

None. `change-readiness` gains four requirements, all new. Its shipped requirement about a `**Case**:` line is
**not** modified: it scopes itself to *a change's spec delta* and stays exactly true. The new requirements scope
themselves to the *shipped* set, which is a different subject and needs a different sentence in the same home —
the capability that already owns the `**Case**:` contract.

## Impact

Code:

- `tools/check-case-names.sh` (new) — the two directions, `--json`, one verdict line.
- `tools/case-harnesses.json` (new) — the registry, and its comment saying a group two harnesses claim is declared
  rather than forbidden.
- `tools/tests/check-case-names-selftest.sh` (new) — the `traceability#` cases, offline, with fixture specs and a
  fixture registry.
- `tools/tooling.json` — the `checks` map gains `caseNames`, so a caller reaches the check by name and no path is
  hard-coded into a tool.
- `.github/workflows/build.yml` — the check joins the existing artifact-hygiene step.

Guidelines and documents:

- `guidelines/BuildTests.md` §6 (test constraints) — the traceability rule, and that a case's group is a declared
  fact rather than a naming preference.
- `guidelines/Backlog.md` §6 (how an item becomes work) — the unclaimed-case count beside the readiness verdict a
  tier-B finding needs, so the two numbers a filing path quotes have one home.
- `openspec/specs/documentation-ownership/spec.md` is the neighbour and stays untouched: this change adds no
  document and moves no rule. The section it does touch is the validator's own `**Case**:` rule, which stays where
  it is.

Rollback: revert the commit. The check is a read-only tool plus a registry; nothing consults it at run time, and a
reverted tree simply has no case-name check again.

Costs to prove by measurement rather than assume:

- **the baseline**, from the tool itself on the committed tree: 1252 case lines, 597 names, 4 harnesses, 0
  unresolved, 1 latent collision, 69 unclaimed loop cases — recorded with the date and the command in the change's
  evidence, so a later reader re-takes them rather than trusting this page;
- **the check's own cost**, from one run: the claim is under a second on this tree, because it reads text and calls
  no tracker and no build.

surface: 4 requirements and 10 scenarios added to 1 delta file, 0 requirement bodies rewritten, 1 new check tool,
1 new harness registry, 1 new self-test, 1 CI step joined — counted with
`grep -hc '^#### Scenario:' openspec/changes/a-case-name-resolves-to-one-harness/specs/change-readiness/spec.md` and
`grep -h '^### Requirement:' openspec/changes/a-case-name-resolves-to-one-harness/specs/change-readiness/spec.md`
