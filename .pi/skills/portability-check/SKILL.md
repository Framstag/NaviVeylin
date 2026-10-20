---
name: portability-check
description: Proves the tooling under tools/ still names no project value — runs the leak check, its 27-case self-test and the synthetic foreign project end to end. Use when a change touches tools/issues, tools/sensors, tools/loop or tools/check-portability.sh, when a task says "portability", "no project value in an engine", "the foreign project" or "the porting recipe", when CI's Check portability step fails, or before copying this tooling into another project.
---

# Portability check

The tooling is meant to be **copied into another project**: the gate, the taxonomy reader, the verbs, the
loop's phases, the metadata pass and the sensors' runner are *engines*, and everything that is one
particular project's lives in a committed config (`tools/tooling.json`,
`tools/sensors/sensors.json`, `tools/loop/loop.conf`). This skill runs the proof of that boundary, and is
the recipe for keeping it true when an engine or a config changes.

`guidelines/Backlog.md` §10 owns the concept; `PORTING.md` is the adopter's recipe; the spec is
`tooling-portability`.

## The four commands

| command | what it answers |
|---|---|
| `bash tools/check-portability.sh` | does any engine name a project value? Prints the engine set, the excluded set and the value set it read |
| `bash tools/check-portability.sh --values` | …and prints every forbidden value with the file it was read from (for debugging a verdict) |
| `bash tools/check-portability-selftest.sh` | 27 cases: the suite's two preconditions, the value-set cases, the four discovery cases, the foreign project end to end, and the recipe's commands |
| `bash tools/mutate.sh --mutations tools/portability-mutations.json` | the portability revert-checks as a versioned set (13 records: 5.1–5.7, 5.3 naming two cases, and 6.1–6.5 for the value set, the exception and the suite's preconditions); the recipe is `.pi/skills/mutation-runner` |

`--quiet` gives the verdict without the sets; `--root <dir>` checks another tree (the self-test's
mechanism); `--case <group#name>` runs one case; `--list` lists the cases (the mutation runner has a `--list`
of its own, so keep the tool name in front of the flag).

## The rule, in two modes

An engine may not name a project value. "Name" is deliberately not "contain the same letters":

| mode | match | used for |
|---|---|---|
| `token` | the whole word, comments included | the repository, its name, the allowlisted authors — `acme` in `metadata` is not a name |
| `literal` | a fixed substring, comments skipped | a label spelling, a declared command or path, an escalation label, the gate command |
| `word` | the value is **named**: inside a quote, as `namespace/value`, or as a flag's argument (a token of its own — not a substring of a flag's name, not the left half of `name=value`) — comments skipped | every namespace's values, the profile's `roles` values, the worker skills |

The third mode is load-bearing: a project's category can be an ordinary word (`core`, `docs`, `infra` — the
fixture's), and the engine legitimately uses `core` inside `ISSUES_CORE_DIR`, `$LIB_DIR/core` and the
sentence "the Java core does not compile". A plain word-boundary scan reported 3 false positives on the
fixture and 14 over this tree (measured 2026-10-18, `tooling-portability` D1); a rule like that gets switched
off, which is the failure it was written to prevent. For the same reason a bare word before another tool's
own option is not a name: `gh --state open` is GitHub's word, and the wide rule that fired on it also fired
on `list.sh --eligible-only` and `awk -v open=…`.

The one exception the rule has is asserted, never tolerated: the label that authorises work
(`reservedLabels`) may appear exactly once, as a read-only assignment (`LOOP_AUTHORISATION_LABEL="…"`).
The check prints that line as the documented exception and fails on a second declaration or on any other
occurrence — which is what a write path spelling the label would look like.

## Two preconditions

The suite reads the real tree's check and the fixture's versioned path, so it needs a **checkout**: run it
from a copy outside a git working tree, or from a path under `.pi/` (machine-local state), and it refuses
with a named reason and exit 2 instead of reporting a case red. That red was read as a regression of the
code under test twice (issue #172); the check itself (`tools/check-portability.sh --root <dir>`) runs
anywhere. The header of `tools/check-portability-selftest.sh` states both, and
`preconditions#refusesOutsideAGitTree` / `preconditions#refusesUnderMachineLocalState` hold it to them.

## Adding a value to the check

One `add` call in the value-set section of `tools/check-portability.sh`:

```bash
add "$value" "<the file and section it was read from>" token|literal|word
```

Rules that keep the check honest:

1. **Read it, don't hard-code it.** A value the check carries literally is a value that drifts out of the
   rule the moment the profile changes — and a stale ban list fails open, which is why
   `portability#valueSetIsReadNotHardCoded` exists.
2. **Print the exclusion.** Anything the check does *not* scan gets a line in `list_excluded()` with its
   reason. An invisible exclusion is a proof that has quietly shrunk; the adapter (`todo-md.sh`) is excluded
   because reading this project's record format is its whole job.
3. **Pick the mode by the kind of value**, not by convenience. A short word that the engine can legitimately
   contain must be `word`; a repository name must be `token`; a path or a command must be `literal`.
4. **Add the case, not just the value.** Either extend an existing case or add one to
   `tools/portability-cases.sh` (see below) — a value with no case is a value nobody has seen fail.

## Adding a case

Three edits, all in the shell half (`tools/portability-cases.sh`):

1. the case id in `PORTABILITY_CASES` (`group#name`, e.g. `discovery#fromAWorktree`);
2. the dispatch line in `portability_run_case` → `case_<group>_<name>`;
3. the function. It runs in a subshell and exits non-zero with a reason on failure; `fail`, `assert_eq`,
   `assert_contains`, `assert_not_contains`, `assert_empty` are already there, and a throwaway tree comes
   from `local_tree` (this project's configs) or `foreign_tree` (the fixture overlaid).

Then prove it can fail: extend `tools/portability-mutations.json` with a record (`label`, `case`, `file`,
`search`, `replace`, `why`) and run `bash tools/mutate.sh --mutations tools/portability-mutations.json
--only <label>`. A case with no mutation is a case nobody has seen red.
## Adding a fixture file to the foreign project

`tools/fixtures/foreign-project/` is overlaid onto a throwaway tree whose engines are copied from this
repository at test time — **the fixture never carries an engine**, so it cannot drift. Add config, findings
and `gh` answers (`gh/repo.txt`, `gh/login.txt`, `gh/labels.json`, `gh/issues.json`, `gh/prs.json`).

Two rules for the fixture:

- **it must differ from this project** (repository, allowlist, vocabulary, bands) — a fixture that is
  accidentally similar passes for the wrong reason, and `foreign#profileDiffersFromOurs` asserts the
  difference;
- **it must not name this project** — `foreign#noProjectNameInTheStub` greps the whole fixture directory for
  `Framstag` and `NaviVeylin`, so a comment mentioning the upstream project fails the check.

## When the check fails

```
check-portability: FAIL tools/issues/create.sh:44: names the project value 'Framstag' (tools/tooling.json allowedAuthors)
```

Read the source in the parentheses: it says *where the value was read from*, which is where the fix belongs
in reverse.

| verdict | the fix |
|---|---|
| the repository, an author, a vocabulary value | it belongs in the profile, or the engine should read it from there (`issues_profile_value`, `tools/sensors/lib.sh`, `loop_conf_value` — all three exist) |
| a declared path or command | move it into `tools/sensors/sensors.json` (a row's `cmd`/`input`) or `tools/loop/loop.conf` (the project keys) |
| the gate command | `LOOP_GATE_CMD`; an engine never runs a build, and a project with none leaves it empty |
| the adapter or a fixture was reported | the exclusion list is wrong, or you edited the wrong file — the adapter is excluded on purpose |
| the check fails on an unchanged tree | the value set moved (a profile edit) — read `--values` before touching the engine |

Do **not** add an escape hatch (`# portability-ok` and friends) and do not widen the exclusion list to make a
red run green: the one legitimate exception already has a file of its own.

## Known boundary (issue #162)

The value set covers the **category** vocabulary plus the values the per-tool configs declare — not every
namespace. Three facts in the loop engine sit outside it: `tools/loop/worktree.sh:71` names the submodule
path `app/src/main/cpp/libosmscout`, and `tools/loop/prescreen.sh` / `tools/loop/labels.sh` name `class`,
`status` and `agent` values literally. Widening the check to every namespace reports them; the fix belongs in
a change that owns those engines (issue #162, `openspec/changes/archive/2026-10-18-tooling-portability/report.md`). Until then,
"the check is green" means "no *declared* value is named", and this paragraph is the honest scope.

## Pitfalls

1. **A hand-built scratch tree is a stale cache.** Probing `tools/check-portability.sh` inside a tree you
   copied ten minutes ago answers for the *old* rule (measured 2026-10-18: three false positives chased in
   code that no longer had them). Let the self-test build the tree, or re-copy after every edit.
2. **A green check is not a green rule.** The check is one of two things a change to `tools/**` owes; the
   self-test's foreign-project run is the other, because it proves the tooling *works elsewhere* rather than
   that it names nothing.
3. **The CI step runs both**, plus `--list` of the mutation set, so a set that no longer parses fails the
   pipeline instead of rotting.
4. **The fixture is test data, not a project.** It lives under `tools/`, so it is versioned; putting it under
   `.pi/` would make a fresh clone unable to run the proof (`foreign#fixtureIsVersioned`).

## References

- `PORTING.md` — what to copy, the three files to edit, the adapter to delete, what an update costs
- `guidelines/Backlog.md` §10 "Portability" — the concept and the boundary
- `.pi/skills/mutation-runner/SKILL.md` — the runner behind the revert-checks
- `.pi/skills/revert-check/SKILL.md` — the rule the mutations serve (one mutation per invariant)
- `openspec/changes/archive/2026-10-18-tooling-portability/{report.md,evidence/}` — the change that landed it, with the gate
  transcript and the traceability table
