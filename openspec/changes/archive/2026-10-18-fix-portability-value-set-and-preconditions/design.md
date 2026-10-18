# Design

## Context

See `proposal.md` — Why. What shapes the approach:

- `tools/check-portability.sh` builds one `value<TAB>source<TAB>mode` list (`add`, the value-set section) and
  scans the engines and the loop's prompt skeletons with it. Three modes exist and are unchanged in meaning:
  `token` (a fixed string, comments included — the repository and its authors), `literal` (a fixed substring,
  comments skipped — a declared path, a command, an escalation label), `word` (the value must be *named* —
  inside a quote, as a label, or as a flag's argument). The namespace loop uses `word` for the bare values and
  `literal` for the label spellings, which is what the category-only code already did; widening it to every
  namespace is the same two calls in the same loop.
- The profile's `namespaces` object is the vocabulary's home (`Profile.namespaces()`, `Profile.values()`), and
  its namespace *names* are the contract (`PORTING.md`, "Assumptions this recipe does not remove"). One value
  of the `source` namespace is also a namespace name (`agent`), and `PORTING.md` says the namespace names are
  what the engine reads — so the bare value `agent` cannot be forbidden without forbidding the contract's own
  word. The label form `source/agent` can.
- `tools/loop/lib.sh` is "what every loop script shares" and reads `loop.conf` through `loop_conf_value`; the
  profile through `loop_profile()`/`loop_compat_profile_value`. A role read belongs there, once (D16: a rule
  re-implemented per script is a rule that drifts).
- The profile already carries an engine's own semantics as a top-level section: `bands` is
  `issue-metadata-loop`'s mapping, read by `Bands.java`, validated in `Profile.problems()`. `roles` is the same
  shape for the loop's and the resolver's decisions — a section, not a new file, so a second project still
  edits **three** files.
- `tools/loop/loop.conf` calls itself the home of "the project-specific keys (D16)" and already lists the gate
  command and the worker skills; a project path a worktree has to check out belongs in exactly that block.
- `tools/issues/resolve-stale.sh` is issue tooling, not loop tooling, and it parks an item under the same
  status the loop's hold verdict uses. It reads the profile through the Java core, and its parking status is a
  value of the vocabulary like any other.
- This is shell tooling: one process per call, no dispatcher, no lifecycle, no native boundary, nothing that
  can run on an Android thread.

## Goals / Non-Goals

**Goals**

- The value set the check forbids is the whole of the project's vocabulary, read from the profile and the
  per-tool configs at run time, plus the values the profile's `roles` section declares.
- The one exception — the read-only label that authorises work — is asserted (one declaration, its file and
  line printed), not merely tolerated.
- No engine names a project value: the loop's decisions and the stale-citation resolver read their vocabulary
  values, and the loop's worktree reads the submodule path.
- The suite refuses its two root preconditions with a named reason and `exit 2`, and states them in its header.

**Non-Goals**

- Not renaming a namespace or reshaping the taxonomy: the namespace *names* stay the contract.
- Not validating the `roles` values against the namespaces inside `Profile.problems()`: the section is the
  loop's and the resolver's, and `tools/issues/**` must keep running for a project that dropped `tools/loop/`.
  The loop's own loader validates the *shape* (every role declared).
- Not touching the destination of a `gh` state filter: the two `--state open` removals keep the filter, which
  is `gh`'s own default.
- Not adding a fourth file to the copy: `roles` goes into the profile, beside `bands`.

## Decisions

### D1 — The widened rule's boundary: what counts as *naming* a value

**Alternatives the root-cause evidence eliminated:**

- *Keep the flag-argument rule as any flag's argument.* Impossible: with every namespace's values forbidden,
  it fires on `list.sh`'s own option `--eligible-only`, on `awk -v open=…` (the marker variable's name) and on
  `gh … --state open` — three places where no project vocabulary is spoken. Fixing them in the engines would
  mean renaming a public flag, an awk variable and GitHub's state word. Measured: the wide rule reported 78
  lines over a tree whose real leaks are 40.
- *Forbid the bare values only inside quotes, dropping the flag rule.* Impossible: it would stop catching
  `grep -x <value>`, which is how `prescreen.sh` named the class and status values — the leak the issue
  measured. The rule must keep a flag's argument.
- *Rename the loop's verdict words so they stop colliding with the `agent` values.* Impossible: the verdict
  words *are* the vocabulary values by construction (`verdict="needs-device"` is the label
  `agent/needs-device` the transition writes) and are documented in the loop's own vocabulary; renaming them
  would change the run state's contract to satisfy a scan.

**Chosen**: the value must be a token of its own (`l` whitespace/`=` and not followed by `=`) and the flag
before it must exist. So `--status on-hold` and `grep -x bug` are names, `--eligible-only`, `-v open=` and
`gh --state open` are not. Documented in the check's header, in `guidelines/Backlog.md` §10 and in the
`portability-check` skill; falsified by 5.1 and 6.1.

**Risk**: a bare value as another tool's own argument is not caught. Accepted and named: no scan can tell
GitHub's `open` from this project's `status` value, and a rule that fires on both is switched off.

### D2 — Where the loop's decisions get their vocabulary values

**Alternatives eliminated:**

- *A separate config file for the roles.* Impossible: the adopter's cost is "the three files to edit"
  (`PORTING.md`, `AGENTS.md`); a fourth file would be a fourth.
- *Read the values in the shell from `.namespaces` and guess the role* (e.g. "the value called `in-flight`").
  Impossible: it is the same literal, one level of indirection later — the defect.
- *Validate the roles in `Profile.problems()`.* Impossible as the single home: the issue tooling must keep
  running for a project that copied only `tools/issues`. The loop refuses a half-adapted profile itself
  (`loop_roles_load`), which is where the loop's decisions are made.

**Chosen**: a `roles` section in the profile (beside `bands`), read by `loop_role` in `lib.sh` for the loop
and by `jq` in `tools/issues/resolve-stale.sh` for the parking status; `loop_roles_load` refuses a missing
section or a missing key with the profile's own "fail loudly" reasoning (an omitted key is a half-adapted
profile; an empty value is a project without that state).

**Risk**: two homes for vocabulary data (the namespaces and the roles) can drift. Mitigated where it matters:
`tools/check-portability.sh` forbids every role value in an engine exactly as it forbids the namespaces'
values, and the foreign fixture declares its own roles with a device state deliberately empty, so a drift
between the two sections shows up as a fixture behaviour, not as silence.

### D3 — The submodule path's home

**Alternatives eliminated:**

- *Keep the path in `worktree.sh` and teach the check to ignore it.* Impossible: that is the carve-out the
  issue is about; the rule is stated over paths.
- *A `git submodule foreach`/`.gitmodules`-derived path.* Impossible: the worktree must check out *the*
  submodule the run needs, and a project can declare several; the choice is the project's.
- *Keep the path only in the check's value set.* Impossible: a forbidden value needs a source of truth, and a
  value the engines may not name must live where they read it.

**Chosen**: `LOOP_SUBMODULE_PATH` in `loop.conf`'s project-keys block, read by `worktree.sh` when the project
carries a `.gitmodules`, empty for a project without one. Falsified by 6.2 and proven behaviourally by
`worktree#checksOutTheSubmoduleTheLoopConfigNames` (a throwaway project whose `.gitmodules` declares the path
the configuration names).

### D4 — The reserved label: asserted, not tolerated

**Alternatives eliminated:**

- *Exclude `tools/loop/lib.sh` from the scan.* Impossible: the file is the loop's shared engine; excluding it
  hides every leak in it.
- *Allowlist the value (never report it).* Impossible: that is the tolerance the module header calls out as a
  proof that has quietly shrunk, and it is a write path's spelling of the reserved label that the invariant
  must catch.

**Chosen**: the reserved labels (profile `reservedLabels`) are scanned as their own mode. Exactly one
occurrence may be a read-only declaration (`NAME="<label>"`, nothing else on the line); it is printed in the
run header as "the documented exception", and a second declaration — or any other occurrence — is a leak.
Portable: no file path is named by the check, so a foreign project's read-only constant is asserted the same
way. Falsified by 6.3.

### D5 — The two `gh --state open` removals

**Alternatives eliminated:**

- *Teach the check about GitHub's state words.* Impossible: that list would be the check's own — the failure
  mode the value-set requirement is written against.
- *Pass the project's status value to `gh`.* Impossible: the status value *means* open here but need not
  elsewhere (`fresh`), and `gh` needs GitHub's word, so a project with a different vocabulary would break.

**Chosen**: drop the redundant flag. `gh issue list` and `gh pr list` both default to the open state, so the
filter is unchanged; the word `open` was the only bare vocabulary value named as another tool's argument.

## Risks

- **A widened value set is a wider net.** The measured false positives (the three above) are exactly why D1's
  boundary is stated. Every remaining hit on the real tree was a real leak (measured: 78 → 0 after the engine
  fixes, `check-portability: OK — 40 engines and 2 templates name no project value (91 value(s) read from the
  project)`).
- **The source adapter's exclusion is completed, not widened**: `PORTING.md` already promises it is excluded;
  the Java files that *are* the adapter were being scanned while the printed list named only the shell
  wrapper. Printing the three files keeps the proof and the print in step.
- **A concurrent run of two loop-driven suites** (the portability suite runs the loop in the foreign tree) is
  unchanged from before this change; each case works in its own scratch tree.

## Verification

- `bash tools/check-portability.sh` → `check-portability: OK — 40 engines and 2 templates name no project
  value (91 value(s) read from the project)`, with the documented exception printed.
- `bash tools/check-portability-selftest.sh` → all 26 cases pass (5 of them new, red on HEAD:
  `evidence/red-on-head.log`).
- `bash tools/loop-selftest.sh` → all 55 cases pass (2 of them new, red on HEAD).
- `bash tools/mutate.sh --mutations tools/portability-mutations.json` → 13 mutations, each red with the
  mutation in place and green after the restore (`evidence/revert-checks.md`).
- No Gradle, no device, no emulator: this change touches no compiled file, renders nothing and logs no
  coordinate, so no build or Android test runs (`guidelines/Build.md` §2, the scope block). Nothing to measure
  on device; nothing that cannot be produced on a stationary emulator.
