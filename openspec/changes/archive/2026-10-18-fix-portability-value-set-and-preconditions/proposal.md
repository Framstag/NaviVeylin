# Proposal

## Why

Two defects of the same rule, filed as #162 and #172 and measured on 2026-10-18. Both are about the
portability proof: what it forbids, and where it may be run.

### #162 — the value set stops short of the rule, so three engine facts name project values

Root cause and evidence:

- **Root cause**: `tools/check-portability.sh` builds its value set from the profile and the per-tool
  configs, but of the profile's `namespaces` section it reads **the `category` vocabulary only**
  (`jq -r '.namespaces.category[]?'`, the value-set section). The rule it implements — "an engine names no
  project value" (`guidelines/Backlog.md` §10, spec `tooling-portability` → "An engine names no project
  value") — is stated over the vocabulary, not over one namespace, so every other namespace's values are
  forbidden by nothing at all.
- **Reproduction** (recorded in `evidence/red-on-head.log`): planting one value per namespace in an engine
  and running the real check reports **only the category value** — `check-portability: 1 leak(s)` for a tree
  that names six namespaces' values; the seven-versions-later measurement of the *widened* check reports
  **78 leak lines in 9 engine files** over this tree (the size of the gap).
- **The consequence, in code** (the three facts the issue names, plus the ones the widened check found):
  `tools/loop/worktree.sh` names the submodule path `app/src/main/cpp/libosmscout`; `tools/loop/prescreen.sh`
  names `feature`, `bug`, `improvement`, `in-flight`, `open-device`, `fixed-by`, `retired` to *select* them
  from the profile; `tools/loop/labels.sh` names `in-progress`, `needs-decision`, `needs-device`, `on-hold`,
  `fixed-by` in its verdict→label mapping. A widened check additionally names `run.sh`, `report.sh`,
  `state.sh` (the loop's verdict words, which *are* the vocabulary values of the `agent` namespace it
  writes), `tools/issues/resolve-stale.sh` (the parking status, twice) and the loop's `--state open`
  argument to `gh`.

The single fix follows from the root cause: the check reads **every** namespace's values, its label forms
and the values the profile's `roles` section declares, and the engines stop naming them — the loop's
decisions read their vocabulary values from the profile, and the submodule path moves into `loop.conf`,
where the project's other keys already live. There is one defensible fix: any narrower reading of the rule
is the defect itself.

The requirement the defect contradicts is `tooling-portability` → **"The check's value set is the project's
own"** ("The check SHALL read the values it forbids from the project's profile rather than carrying a list of
its own") — a check that reads one namespace of it is exactly the stale-list failure that requirement
forbids, one namespace at a time. Its sibling "An engine names no project value" is modified with it, because
the exception the widened rule needs (the read-only authorisation label) is that requirement's business.

### #172 — the suite reports a red case, not a refusal, when it is run from the wrong root

Root cause and evidence:

- **Root cause**: `tools/check-portability-selftest.sh` computes its root from its own path and runs its 26
  cases from there, with no precondition on the root. Two of its cases need what a copy cannot provide:
  `portability#passesOnTheRealTree` drives the real tree's check (which refuses to resolve a root outside a
  git working tree, `exit 2`), and `foreign#fixtureIsVersioned` asserts `PORTABILITY_ROOT` is not
  machine-local state. Run the suite from an export, and the first reports `FAIL`; run it from a copy under
  `.pi/`, and the second does.
- **Reproduction** (both readings verbatim in `evidence/red-on-head.log`): from
  `git archive HEAD | tar -x -C /tmp/ci-verify`, `check-portability: not inside a git working tree — pass
  --root <dir>` (exit 2) and then `FAIL portability#passesOnTheRealTree` → **1 of 21 cases failed**; from a
  copy under `<repo>/.pi/`, `FAIL foreign#fixtureIsVersioned` → **1 of 21 cases failed**, while the same
  commands in the checkout are green. Two of the last three sessions read that red as a regression of their
  own diff.

The single fix: the suite refuses **before it reads anything**, with a named reason and `exit 2` (its other
environment refusals' shape), when its root is not inside a git working tree or when its root path contains
`/.pi/`; and its header states both preconditions. The requirement it contradicts is `tooling-portability`
→ **"Discovery is exercised where it breaks"**, whose scenario "Invoked outside a project" says a tool
"reports that and stops, and no default project is invented" — the suite reported a *failed case* instead.

## What Changes

- **`tools/check-portability.sh`** — the value set becomes the project's own:
  - every namespace's values are forbidden in word mode, and every namespace's label form
    (`<namespace><separator><value>`) as a fixed string, comment-filtered like the other label spellings;
  - the values of the profile's new `roles` section (which value of a namespace each engine decision means)
    are forbidden exactly as the namespaces' are;
  - `loop.conf`'s `LOOP_SUBMODULE_PATH` is forbidden, so the submodule path has exactly one home;
  - a value that is also a namespace *name* (`agent` is a `source` value) stays the contract and is not
    forbidden on its own — the label form is;
  - the reserved labels are the **one documented exception**, asserted rather than tolerated: exactly one
    read-only declaration (`LOOP_AUTHORISATION_LABEL="agent/eligible"`), printed with its file and line in
    the run header; a second declaration, or any other occurrence, is a leak;
  - the word mode's flag-argument rule is made precise: the value must be a token of its own, so a substring
    of a flag's *name* (`--eligible-only`) or the left half of a `name=value` pair (`awk -v open=…`) is not
    an argument. A bare word standing before another tool's own option (`gh --state open` — GitHub's word,
    not this project's) is not a name either: no scan can tell the two apart, and the rule that fired on
    both also fired on the two coincidences above (measured).
  - the engine set's exclusion for the source adapter is completed: `TodoAdapter.java` and
    `TodoSections.java` are the adapter `PORTING.md` already promises is excluded for reading this
    project's own record format; the printed exclusion names all three files.
- **`tools/loop/loop.conf`** — the project-specific key block gains `LOOP_SUBMODULE_PATH="app/src/main/cpp/libosmscout"`
  (empty for a project with none).
- **`tools/tooling.json`** — a `roles` section: `queuedVerdict`, `workedClasses`, `refusedClasses`,
  `inFlightStatus`, `deviceStatus`, `holdStatus`, `closeStatus`, `settledStatuses`, `inProgressAgent`,
  `deviceAgent`, `decisionAgent`. Every consumer reads the value for its role; a key a project does not need
  is declared empty, never omitted.
- **`tools/loop/lib.sh`** — `loop_roles_load` (the section must exist and declare every role) and
  `loop_role` (one value, or one per line for a list), read next to `loop_resolve`.
- **`tools/loop/prescreen.sh`** — the class/status roles come from the profile; the verdict it writes for a
  queued item, an in-flight item and a device-gated item is the role's value; the ranking is the profile's
  `workedClasses` order.
- **`tools/loop/labels.sh`** — the verdict→label mapping is data: `in-progress`, `needs-decision`,
  `needs-device`, `on-hold`, `fixed-by` come from the roles.
- **`tools/loop/run.sh`, `report.sh`, `state.sh`** — the same for the decision label they look for and the
  queued verdict they filter on.
- **`tools/loop/worktree.sh`** — reads `LOOP_SUBMODULE_PATH` from `loop.conf` instead of naming it.
- **`tools/issues/resolve-stale.sh`** — the parking status comes from the roles; `gh issue list --state open`
  loses its redundant state flag (the default is the same filter, and the word `open` is GitHub's).
- **`tools/sensors/conditions/pr-conflicts.sh`** — the same `--state open` removal, same reason.
- **`tools/fixtures/foreign-project/tools/tooling.json`** — the foreign project's own `roles` section, with
  `deviceStatus`/`deviceAgent` declared empty, so the fixture proves a profile with no device state runs.
- **Tests**: `tools/portability-cases.sh` gains five cases (one planted value per namespace; the submodule
  path; the asserted exception; the two preconditions) and `tools/loop-cases.sh` two
  (`prescreen#readsTheVerdictsFromTheProfile`, `worktree#checksOutTheSubmoduleTheLoopConfigNames`);
  `tools/portability-mutations.json` gains the five revert-checks (6.1–6.5) and its 5.1 record is re-pointed
  at the widened value set.
- **Documents**: `PORTING.md` (the profile's `roles` section and `loop.conf`'s new key), `guidelines/Backlog.md`
  §10 (the adaptation table and what "named" means), `AGENTS.md` and `.pi/skills/portability-check/SKILL.md`
  (the case count, the exception, the two preconditions).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `tooling-portability` → **"The check's value set is the project's own"**: MODIFIED. The delta adds the
  scenarios that name the cases (`portability#plantedValueInEveryNamespaceFails`,
  `portability#plantedSubmodulePathFails`, `portability#authorisationLabelIsTheOneException`) and states that
  the value set is every namespace and the roles section, and that the reserved label is asserted as the one
  exception. No unrelated text is rewritten and no existing scenario is dropped.
- `tooling-portability` → **"Discovery is exercised where it breaks"**: MODIFIED. The delta adds the two
  scenarios for the suite's own preconditions (`preconditions#refusesOutsideAGitTree`,
  `preconditions#refusesUnderMachineLocalState`). Nothing else in the requirement changes.

## Impact

- Tooling only: `tools/check-portability.sh`, `tools/check-portability-selftest.sh`, `tools/portability-cases.sh`,
  `tools/portability-mutations.json`, `tools/tooling.json`, `tools/fixtures/foreign-project/tools/tooling.json`,
  `tools/loop/{lib,prescreen,labels,run,report,state,worktree}.sh`, `tools/loop/loop.conf`,
  `tools/loop-cases.sh`, `tools/issues/resolve-stale.sh`, `tools/sensors/conditions/pr-conflicts.sh`.
- Documents: `PORTING.md`, `guidelines/Backlog.md` §10, `AGENTS.md`, `.pi/skills/portability-check/SKILL.md`.
- No Android surface, no Gradle, no device: no compiled file is touched, so no build runs (`guidelines/Build.md`
  §2, the scope block).
- Additive in intent: a profile that declares the `roles` section runs exactly as before — the loop's
  decisions read the same values the literals named. Breaking only for a profile that declares none: the loop
  refuses with a named reason instead of silently deciding nothing (the profile's own "fail loudly" rule).
- Rollback: revert the one commit; nothing reads the tooling's internals and no persisted format changes
  (`loop.conf` and the profile are configuration).
- The two `gh --state open` removals are behaviour-preserving: `gh issue list` and `gh pr list` default to the
  open state.
