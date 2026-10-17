# Proposal

## Why

`TODO.md` is the project's only backlog and it has outgrown the context it is read in. Measured
2026-10-17: **3 622 lines / ~396 KB / ~100 k tokens**, 137 numbered sections. ~100 k tokens is where
model performance falls off (the "dumb zone" the agentic-coding concept doc names in §1), so every
skill that reads it whole — `triage-todo`, `cleanup-todo`, `process-failure-log`, `fix-loop` — works
below its own competence. The file is also machine-readable only by regex: 137 hand-maintained
metadata lines, a hand-maintained cluster index that has already drifted, 16 raw category values of which
2 (`rendering`, `build`) are outside the file's own vocabulary (14 canonical values), and no dedup at all
— a finding is
duplicated or lost by whoever last edited the prose.

The backlog moving to GitHub Issues is the prerequisite for everything the concept doc schedules
after it: an item-per-issue backlog gives an agent **titles and labels to rank, bodies to load only
for candidates**, the cluster index becomes a saved search, and the item loop becomes a scripted
queue over attributable, machine-checkable state instead of a prose file.

Why now, and why in this shape: the repository is `Framstag/NaviVeylin`, **public**, issues enabled,
currently **0 issues**, and only `Framstag` has write access. A public tracker means *any* GitHub
user can open an issue, so the tooling that a future loop trusts must be built with its security gate
first — it is much cheaper to define the gate before 137 issues exist than to retrofit it afterwards.

Measured state the import must survive:

```
TODO.md  137 sections, every one carrying its metadata line
  status   open 97 | fixed-by 17 | on-hold 14 | in-flight 8 | retired 1
           (open folds 21 `open (device)` and one `open (unproven on a device)` → `unverified`;
            `retired — <date>: <reason>` is an in-place retirement, §149)
  class    improvement 77 | bug 51 | feature 9
  category 16 raw values; off-vocabulary: rendering (§190), build (§202)
  §N is identity and is quoted in 324 places outside the file;
  292 archived-change artifacts also name TODO.md
harness  12 of 21 tracked skills reference TODO.md
```

## What Changes

- **A label taxonomy is defined and created idempotently** (the tagging system). Namespaced, so a
  namespace always means one thing and GitHub's ambiguous defaults are never used for logic:

  | namespace | values | role |
  |---|---|---|
  | `class/` | `bug` · `improvement` · `feature` | mirrors the entry's `class` — the triage order |
  | `category/` | the file's known vocabulary, kebab-case | the old cluster key, now searchable |
  | `status/` | `open` · `in-flight` · `fixed-by` · `on-hold` · `open-device` · `unverified` | the entry's `status`, one value |
  | `source/` | `todo-import` · `agent` · `human` · `ci` | provenance, so a reader can tell an imported item from a machine-filed one |
  | `sensor/` | one value per declared check, named by the registry | ownership: which check may close or reopen the item. The namespace is reserved here; its values come from the check registry (`sensor-registry-and-metric-issues`), so the vocabulary stays one file with one owner |
  | `agent/` | `eligible` · `in-progress` · `needs-decision` · `needs-device` | the loop's own state; `eligible` is the opt-in gate |
  | `prio/` | `p0` · `p1` · `p2` · `p3` | the persisted priority band, in a **fixed range**. Reserved and created here; **computed** by `issue-metadata-loop`, which also owns the body marker the band derives from and the check that the two agree. `prio/p0` is not authority — `agent/eligible` stays the only opt-in |

  `status/open (device)` maps to `status/open-device`; the label carries the change name for
  `in-flight`/`fixed-by` in the body, because a label cannot.

- **A deterministic, idempotent import script with `--dry-run` as its default.** It parses the
  `## N.` sections and their metadata lines mechanically (no LLM), and prints the plan it *would*
  apply: one issue per section, `§N` in the title, the section body verbatim, labels from the
  taxonomy, and a machine marker `<!-- nvid: N -->` in the body. Re-running is a no-op: an existing
  `nvid` is skipped, not recreated.

- **A deduplication rule with a fingerprint**, so an item cannot be created twice — by the import or
  by an agent filing a new finding. The fingerprint is computed from the structured fields (class,
  category, the normalised first claim, the primary `file:line`), and `create` **refuses** when a
  match exists, naming the existing issue number instead of opening a second one.

- **An issue tooling surface**, split the way the project already splits tools: thin shell wrappers
  that speak `gh`/`jq`, over a small policy core in Java run as **single-file source**
  (`java tools/issues/core/Policy.java …`). The Kotlin half of the mixture is not available on this
  machine (`kotlinc` absent) and the core must not be started through `./gradlew` (see `design.md`).

  ```
  tools/issues/labels.sh    create/refresh the taxonomy (idempotent)
  tools/issues/create.sh    one issue from a structured finding; fingerprint dedup
  tools/issues/update.sh    labels / status / body of an existing issue
  tools/issues/close.sh     close with evidence + policy pass; never deletes
  tools/issues/list.sh      filter by criteria (label, class, category, status, author)
  tools/issues/import.sh        findings JSON -> issues, idempotent on the source id
  tools/issues/import/todo-md.sh  the SOURCE ADAPTER: parses TODO.md's grammar and its renames
  tools/issues/policy.sh    the gate as a command (preflight + decision for one issue)
  tools/issues/lib.sh       shared shell helpers; core/Policy.java, core/Fingerprint.java,
                            core/TodoSections.java
  tools/tooling.json        THE PROJECT PROFILE — the one file a new project adapts:
                            repo, allowedAuthors, reservedLabels, the namespace value sets,
                            the source renames, bounds, band edges. Read by this change's tools,
                            by the sensors and by the loop; no engine names a project value
  ```

- **The mechanism is portable to another project by construction.** One profile holds everything
  project-specific, every script discovers "the current project" instead of being told, and the one
  piece of this repository's own backlog format — `TODO.md`'s `## N.` grammar and the two category
  renames — sits behind a **source adapter** that emits a neutral findings stream. A generic reconciler
  turns findings into issues; the adapter is the only project-specific half of the import. The
  namespace *names* are the contract, their *values* come from the profile. What proves it rather than
  asserts it is `tooling-portability`: a synthetic foreign project with a different repo, allowlist and
  vocabulary running the whole toolchain.

- **The security gate, defined before anything can use it.** Two independent conditions must hold
  for the tooling — and later the loop — to act on an issue:

  1. **the issue's author is on a committed allowlist.** `author.login` is the only *attributable*
     actor GitHub exposes, it is reviewable in the repository, and it is testable offline;
  2. **the issue carries `agent/eligible`**, applied deliberately as the opt-in, so that filing an
     issue is never the same act as authorising an agent to work on it — and `agent/eligible` is a
     **reserved label**: `create.sh`, `update.sh` and `import.sh` refuse it in their input
     (`label-reserved`, exit 3), so the tooling cannot authorise itself. The only way to set it is a
     human in the GitHub UI; this change ships no authorise command.

  Recorded honestly: **GitHub does not record which actor applied a label.** On a public repository
  only a collaborator with triage/write can add a label at all, so the label's presence is real
  evidence — but not attributable evidence. That is exactly why condition 1 exists and is the
  deciding one. The tooling is **fail-closed**: a preflight refuses to run at all when the
  authenticated `gh` login is not on the allowlist, so a borrowed credential cannot widen the set.

  Why the reservation is load-bearing rather than belt-and-braces: `gh` is authenticated as
  `Framstag` and `Framstag` is the only allowlisted author, so **the author condition is satisfied by
  any issue this tooling creates**. Without the reservation the label would be the gate's only real
  discriminator and the tooling could satisfy it unaided — the gate would then verify nothing. See
  `design.md` D2, "The reserved label".

  **Issue text is untrusted data, never instructions.** Title, body and comments are attacker-
  controlled on a public tracker. Two named classes are bounded: *prompt injection* — issue text is
  passed to any agent as a delimited, labelled data field inside a prepared work package, never as a
  directive, and the receiver must re-verify every claim against the tree; and *shell injection* —
  the wrappers never `eval` issue content and never interpolate it into a command unquoted.

- **Deletion is not an operation this tooling has.** `close` is the only terminal step and it is
  reversible; no script exposes `gh issue delete`, so an accidental or malicious call cannot destroy
  the only record of a finding. Both halves of the loss risk are covered: *duplicate* by the
  fingerprint refusal, *unjustified deletion* by there being no deletion path plus a close that
  requires evidence.

- **Four new skills document the operations** for agents — `create-issue`, `update-issue`,
  `close-issue`, `list-issues` — wrapping the scripts so the LLM is used for judgement and the
  scripts do the work. They are **new files only**: no existing skill is edited here.

- **The tooling is testable offline.** A self-test runs the Java core's cases and drives the shell
  wrappers against a **stub `gh` on `PATH`** (the same technique as the existing test-only JNI stubs),
  so it needs no token and no network, and one CI step in `.github/workflows/build.yml` runs it.

- **Explicitly not in this change** — the split is deliberate and named, so the boundary is visible
  and nothing is half-migrated:

  | deferred to change 2 (`migrate-todo-to-github-issues`) | why deferred |
  |---|---|
  | running the live import (137 issues on the public tracker) | irreversible public state; needs its own authorisation |
  | deleting `TODO.md` and carrying its `§N` identity | dangling references if done before the import is verified |
  | rewiring the 12 existing skills, `guidelines/*`, `AGENTS.md`, `openspec/config.yaml` | the harness rule may only flip once the issues exist |
  | porting `fix-loop`, and the outer loop wrapper that owns branching | both consume the tracker, not the tooling |

  Consequence accepted: after this change the four new skills exist but are **not yet listed** in
  `AGENTS.md`, and the 12 existing skills still read `TODO.md`. That is the interim the split buys —
  no session is ever pointed at an empty tracker. Listing them is part of change 2.

## Capabilities

### New Capabilities
- `issue-backlog`: the tracker as the project's backlog — one issue per distinct finding, with a
  fingerprinted refusal against a duplicate; the label taxonomy that carries an item's kind, area,
  state and provenance; the authorisation gate that keeps an automated actor off an item its owner has
  not authorised; identity that survives its source record; closure-with-evidence as the only terminal
  state; and discovery of the current project from its own working tree and profile.

`.openspec.yaml` no longer sets `skip_specs`. An earlier draft argued that harness behaviour is owned by
`guidelines/*`, skills and CI rather than by a spec, on the strength of two archived harness precedents;
that was wrong — this repository spec-owns ten process and harness capabilities, including
`project-metrics-report` and `spec-feature-index`, which are this shape of tooling. The mechanism is
therefore spec-owned, and `design.md` D10 records the correction.

### Modified Capabilities

None. No existing capability's requirements change.

## Impact

All new:
- `tools/issues/lib.sh`, `labels.sh`, `create.sh`, `update.sh`, `close.sh`, `list.sh`,
  `import.sh`, `import/todo-md.sh`, `policy.sh`
- `tools/issues/core/Json.java`, `Profile.java`, `Taxonomy.java`, `WorkPackage.java`, `Fingerprint.java`,
  `TodoSections.java`, `TodoAdapter.java`, `Policy.java`, `Cases.java` — Java single-file source, run as
  `java <file>.java`, **not** a Gradle source set. `Json.java` is the small reader that lets the core carry no
  dependency at all; `Cases.java` is the case runner the self-test drives
- `tools/tooling.json` — **the project profile**: repo, `allowedAuthors`, `reservedLabels`, the
  namespace value sets (including the four bands), the source renames, the input bounds and the band
  edges. It merges what an earlier draft split across `core.json` and `policy.json`, because "adapt one
  file" is the requirement and two files that must agree is the drift shape this series rejects
  everywhere else
- `tools/issues/fixtures/gh` — the stub `gh` used by the self-test, with its scenario fixtures beside it
- `tools/issue-tooling-selftest.sh` — top-level, matching the existing `tools/*-selftest.sh`
  convention (`gen-feature-list-selftest.sh`, `declared-cases-selftest.sh`, …), plus
  `tools/issue-tooling-cases.sh` for its shell cases
- `.pi/skills/create-issue/SKILL.md`, `update-issue/SKILL.md`, `close-issue/SKILL.md`,
  `list-issues/SKILL.md`

Modified:
- `guidelines/Backlog.md` — the sections this change makes stale: its own row in §1, and the §12 rows for the decisions it lands
- `.github/workflows/build.yml` — one step running the self-test (offline, no token, no Gradle)
- `tools/feature-list/specs.json` — the classification line for `issue-backlog`, so archiving the spec does
  not leave a shipped capability unclassified
- `openspec/changes/github-issue-tooling/specs/issue-backlog/spec.md` — the capability this change creates, with its requirements and scenarios
- `openspec/changes/github-issue-tooling/evidence/**` — the run's own evidence: the self-test output, the
  dry run over `TODO.md`, the tracker state, the scenario→case traceability table and the revert-check log

Not modified — deliberately: `TODO.md`, `AGENTS.md`, `guidelines/*`, `openspec/config.yaml`, all 12
existing skills, and every `openspec/specs/**` file.

Not affected: no Kotlin/Java under `app` / `auto` / `core` / `osmscout-client-java` / `buildSrc`; no
manifest; no Gradle configuration; no native or JNI boundary — so **no submodule patch and no
bridge-module override**, and the `.java` under `tools/` is outside every Gradle source set. Neither
distribution flavour changes. `jq` and `java` are already present on this machine; `gh` 2.102.0 is
already authenticated as `Framstag`, and the allowlist records that fact rather than assuming it.

Scope: agent-harness tooling only — neither phone-only nor car-only work; the two variants are
affected identically and the tooling is variant-independent. Every script runs on the host as the
authenticated human and stays usable as a plain command by a future loop wrapper; nothing here depends
on, or assumes, an agent sandbox.

Guidelines referenced: `guidelines/Build.md` §1 (skills and the agent harness, where a new skill's
prerequisites must be stated), §2 (the iteration protocol and its scope block — this delta runs no
build and no Gradle suite; its gate is the self-test) and §3 (the command table). No `UI.md`,
`MapRendering.md`, `Design.md`, `Regulatory.md` or `Logging.md` section is affected: no UI,
rendering, logging or disclosure behaviour changes, and no log line, coordinate or diagnostics rule
is touched.

**Additive.** Nothing existing is removed or redefined; the only new runtime prerequisites are the
`gh` CLI (already authenticated, machine fact), `jq` and `java`, all already present, and the CI
step needs none of them beyond `java`.

**Rollback path.** Delete `tools/issues/**` and `tools/issue-tooling-selftest.sh`, remove the four
`.pi/skills/*-issue/` directories, remove the one CI step, and remove the spec delta. The live
tracker is untouched by this change: the import runs `--dry-run` only, so a rollback leaves no issue,
no label and no public state behind. Nothing under `.pi/` outside `.pi/skills/` is created or
depended on.

**Verification.** The offline self-test (Java core cases + stub-`gh` wrapper cases) and the CI step
are the change's own gate. The import's dry run over the real `TODO.md` is executed once and its
output recorded as evidence: sections parsed, issue previews, the resulting label histogram, the
dedup report and the fail-closed preflight's refusal when the login is not allowlisted. No device
measurement applies — the change produces no UI and nothing is read on a screen.
