# Design

## Context

See `proposal.md` — Why. This document records only what shapes the approach.

Constraints measured on this machine, 2026-10-16:

| fact | value | consequence |
|---|---|---|
| `gh` | 2.102.0, authenticated as `Framstag`, scopes `gist, read:org, repo, workflow` | the actor exists; no new credential is needed |
| tracker | `Framstag/NaviVeylin`, **public**, issues enabled, **0 issues**, 9 default labels | any GitHub user can open an issue; deletion needs admin |
| allowlist candidate | `viewerPermission: ADMIN` for the authenticated account | the tooling's own author must be the allowlist, not a role check |
| `kotlinc` / `kotlin` | **absent** | the Kotlin half of the mixture is unavailable |
| `java` / `javac` | 27 (OpenJDK); `java Foo.java` single-file launcher verified working | a policy core is runnable with zero build system |
| `jq`, `openspec` | present; `openspec` 1.14.2 via mise | the shell wrapper layer needs no new tool |
| `gh` output language | this machine emits German for `gh`/`git` text | never parse human-readable `gh` output |
| repo house style | `tools/*.sh` + `tools/*-selftest.sh`, `tools/feature-list/` as the one subdir precedent | where the new files go and how they are tested |

`TODO.md` inventory that the import must be faithful to, re-measured 2026-10-17: 137 sections, all
carrying a metadata line; `status` open 97 / fixed-by 17 / on-hold 14 / in-flight 8 / retired 1, where
`open` folds 21 `open (device)` and one `open (unproven on a device)`; `class` improvement 77 / bug 51 /
feature 9; 16 raw `category` values, of which `§190 rendering` and `§202 build` are outside the file's own
vocabulary (14 canonical values). `§149` is the one `retired — <date>: <reason>` line: an in-place
retirement, so its id, heading and body stay where they are.

## Goals / Non-Goals

**Goals**

- A backlog surface whose *selection* is cheap: titles + labels fetched in one paged call, bodies
  fetched only for candidates.
- A gate that is decidable offline from committed data, so it is testable without a token.
- No operation in the tooling that can lose the only record of a finding.
- The whole tooling runnable and self-tested on a machine with no network and no token.

**Non-Goals**

- Not the migration itself, and not a change to any existing skill, guideline or `AGENTS.md`
  (proposal, "Explicitly not in this change").
- Not the loop: no queue, no budget, no branch, no worktree, no `pi` invocation. Change 2 owns those,
  and this design only fixes the *shape* the loop will consume: a labelled, attributable,
  fingerprint-deduplicated issue.
- Not a GitHub App, not a bot account, not a webhook. Everything runs as the authenticated human.
- Not a mirror of `TODO.md`'s prose style. The issue body is the section verbatim; the structured
  facts move to labels.
- Not sandbox-aware, and never a sandbox dependency. Every script here runs on the **host**, as the
  authenticated human, and is called by whoever needs it — a session, CI, or a future loop wrapper. It
  must not require an agent sandbox, and it must not assume the agent that filed a finding ran in the
  same process or the same machine. That is what keeps it usable as a plain command by a sandboxed
  child's wrapper later (`agent-issue-loop` D15).

## Decisions

### D1 — Substrate: thin shell over a Java single-file policy core

`tools/issues/*.sh` own the `gh` interaction, argument parsing, exit codes and the human/`--json`
rendering. `tools/issues/core/*.java` own the pure decisions: taxonomy mapping, policy evaluation,
fingerprint, `TODO.md` section parsing. The core is invoked as `java tools/issues/core/X.java …`
and is **not** a Gradle source set.

| alternative | why rejected |
|---|---|
| Kotlin, mise-installed, `.kts` scripts | `kotlinc` is absent; adding it makes the tooling depend on a machine-local `mise.local.toml` entry that CI would have to reproduce. The owner's decision excludes `./gradlew` as the entry point, leaving no Kotlin runtime. |
| Kotlin as a Gradle module | excluded by the owner's decision; and a JVM + Gradle start per gate evaluation is the wrong cost for a per-issue decision in a loop. |
| Pure shell + `jq` | the state machine (taxonomy mapping, fingerprint, policy) is exactly the part that shell tests worst; the repo's `*-selftest.sh` pattern would have to re-implement string normalisation in `awk`. |

Language level is pinned to **`--release 17`**, not 27, because CI is `ubuntu-latest` whose default
JDK is below the local one; the self-test asserts the required feature version and refuses with a
clear message rather than failing obscurely.

### D2 — The gate: an allowlisted author ∧ a label our own surface cannot write, fail-closed

Both conditions must hold:

1. `issue.author.login` is listed in `tools/tooling.json` → `allowedAuthors`;
2. the issue carries `agent/eligible`, which is a **reserved label**: none of the write paths this
   tooling exposes can set it (below).

The honest limitation is part of the design, not a footnote: **GitHub records no labeler.** The
label's presence therefore proves only that *someone with triage/write on a public repo* applied it
— real evidence, but not attributable. Condition 1 is the only **attributable** term, and it is
decided from `author.login`, which is committed and offline-testable; condition 2 is only a control
because it is unwritable by the surface under test.

Before doing anything, `policy.sh preflight` compares `gh api user --jq .login` against the
allowlist and **refuses** when it is absent. Consequently a borrowed or narrower credential cannot
widen the set, and the tooling says so by refusing to run rather than by degrading to "no
candidates" — a silent empty result would read as "backlog is clean".

#### The reserved label — why this is an invariant, not a conjunction

A conjunction of two conditions is only as strong as its weaker term, and here the two terms share
an actor. Measured on this machine: `gh` is authenticated as `Framstag`, and `Framstag` is also the
only allowlisted author. So:

```
allowlisted author  = Framstag    (the human)
gh identity used by agents = Framstag    (same account — measured, gh auth status)

=> the author condition is satisfied by ANY issue this tooling creates
=> the label is the only real discriminator
=> if create.sh / update.sh / import.sh could set agent/eligible,
   an agent would authorise its own finding in the same act,
   and the gate would verify nothing at all
```

The design therefore does not rely on the label being *unattributable* — it relies on the label
being **unwritable by the surface under test**. `tooling.json` carries `reservedLabels` (currently
`agent/eligible`); every write path — `create.sh`, `update.sh`, `import.sh` — **refuses** a
reserved label in its input with reason code `label-reserved` and exit 3. The only way to set
`agent/eligible` is a human, in the GitHub UI: this change ships **no authorise command**.

That gives three independent statements instead of one: the author is attributable (condition 1),
the opt-in cannot be self-served (reservation), and the import starts with **0** eligible issues
(asserted in the recorded histogram, not assumed). The residual — a compromised collaborator
applying the label by hand — is the one no label scheme here can address.

| alternative | why rejected |
|---|---|
| rely on the label's unattributability alone | it is not a control. Any tool, script or agent that can write labels can satisfy the gate; the design must not depend on a caller's restraint. |
| let `create.sh` set `agent/eligible` when the finding is `source/agent` | that is the escalation, spelled out: it makes filing and authorising one act. |
| detect "am I an agent?" and gate on it | the process cannot reliably know what invoked it, and the check fails open exactly when it matters. |
| a separate `authorize.sh` with an interactive confirmation | a confirmation flag is just another write path an automated caller can pass; and it adds a command change 2 would have to keep secure. A human in the GitHub UI is the smaller surface. |

| alternative | why rejected |
|---|---|
| label only | unattributable; also makes filing an issue and authorising an agent the same act. |
| author only | an outsider's issue fails, but every issue the owner ever files becomes loop-eligible by default; no deliberate opt-in. |
| "created by the import script" as proof of trust | a body marker proves *provenance*, not *authority* — anyone can write the same marker into their own issue. Provenance is kept, as `source/*`, for a different question. |
| a collaborator-role check instead of an allowlist | GitHub's role is not the same as "the human who owns this backlog"; the allowlist is reviewable in a diff and testable offline. |

### D3 — Identity: `§N` in the title, `nvid` marker in the body, issue number as transport

An imported issue keeps `§N` in its title (`§129 The router's overall route distance …`) and carries
`<!-- nvid: 129 -->` in its body. The issue *number* is transport only. This keeps the ~324 `§N`
references outside `TODO.md` and the 292 archived-change artifacts resolvable after `TODO.md` is
deleted in change 2: `§N` is looked up by marker, not by file.

For a finding that has no `§N` — an agent filing something new — identity is the issue number plus
its fingerprint; the title carries no `§` prefix, and `source/agent` says where it came from.

### D4 — Deduplication: a fingerprint, checked live, never a committed ledger

`create.sh` computes `sha256` over a canonical string and refuses when an existing issue matches:

```
fingerprint = sha256( "class="   + class
                    + "|category=" + category
                    + "|claim="    + normalize(first sentence of the claim)
                    + "|anchor="   + primary path + ":" + primary symbol )
normalize(s) = lowercase → whitespace collapsed → non-alphanumerics → single space → trim
```

The anchor never contains a **line number** — line numbers drift, so they would defeat the dedup
exactly when the entry is re-read. Where the claim names a symbol, the symbol is the anchor; where
it does not, the anchor is the path alone.

The check reads `gh issue list --state all --json number,title,labels,body` (paged, 2 calls at 137
issues) and matches in the Java core. A short-lived cache under `.pi/` avoids repeated calls inside
one run, and is advisory only: authentication and refusal always re-query.

On a match, `create.sh` **refuses and reports the existing number**; it never silently skips (the
caller must be able to distinguish "already filed" from "filed nothing"). `--allow-duplicate` exists
and is recorded in the plan output, so a genuine collision is a deliberate act with a trace.

| alternative | why rejected |
|---|---|
| a committed `tools/issues/fingerprints.tsv` | a second source of truth that must be appended under a lock, produces merge conflicts, and can disagree with the tracker — exactly the failure the check exists to prevent. |
| a `id/N` label per issue (137 labels) | pollutes the label namespace, hits label-count friction, and encodes an identity a body marker already carries. |
| GitHub search (`gh search issues`) as the dedup query | text search is fuzzy and its matching rules are not ours; the exact rule must be in the tested core, not in a server-side ranking. |
| the issue number as identity from the start | forces a rewrite of every `§N` reference in change 2, and loses the imported entry's provenance. |

### D5 — Taxonomy: five closed namespaces, and GitHub's defaults are never logic

`class/` · `category/` · `status/` · `source/` · `agent/` · `prio/` · `sensor/`. Every namespace's value set lives in
`tools/tooling.json`, so an unknown value is a **refusal**, not a new label — a label created by
a typo would otherwise silently hide an issue from every selector. `labels.sh` is idempotent
(`gh label create --force`-style refresh) and creates the taxonomy before anything can use it.

The mapping from `TODO.md` is faithful and lossless:

| `TODO.md` | label | note |
|---|---|---|
| `class: bug\|improvement\|feature` | `class/<same>` | 1:1 |
| `category: <v>` | `category/<v>` | vocabulary extended by two explicit renames (below) |
| `status: open` | `status/open` | |
| `status: in-flight <change>` | `status/in-flight` | the change name goes in the body; a label cannot carry it |
| `status: fixed-by <change>` | `status/fixed-by` | stays **open** — the legend defines the residue as the remaining work |
| `status: on-hold <decision>` | `status/on-hold` | stays **open** — the decision is still owed |
| `status: open (device)` | `status/open-device` | |
| `status: open (unproven on a device)` | `status/unverified` | reported once, no device run |
| `status: retired — <date>: <reason>` | `status/retired` | the subject stopped existing; id, heading and body stay in place (§149) |
| *(imported)* | `source/todo-import` | provenance |

Off-vocabulary categories are renamed, not extended: `§190 rendering` → `category/map-rendering`
(the entry is a follow-render latency measurement) and `§202 build` → `category/build-and-harness`
(a host-build compile failure). The `category` vocabulary stays closed at the file's 14 canonical
values plus nothing.

**No entry is auto-closed at import.** All 137 arrive open, each carrying its own `status/*`; §149's
`status/retired` is a label, not a closure — its subject stopped existing and the record stays in place.
This is not a postponed decision — it follows from the legend: `fixed-by` and `on-hold` both mean work
remains (a verification, a measurement, a decision). Closing them at import would assert a judgement
the import cannot make, and closure is the one operation this tooling deliberately keeps manual.

| alternative | why rejected |
|---|---|
| GitHub's default labels (`bug`, `enhancement`) as the class axis | `bug` would then mean either a GitHub default or the project's class, and `enhancement` does not distinguish `improvement` from `feature`. |
| flat, unnamespaced labels (`bug`, `car`, `open`) | a reader cannot tell a class from a category from a status, and one typo collides two axes. |
| milestones for status | status is orthogonal to a release milestone; a milestone is a delivery target. |
| GitHub Projects for status | a second API surface with its own auth and its own drift, for a field that fits a label. |

### D6 — Closure only; deletion is not implemented

`close.sh` requires `--reason` and `--evidence`, runs the same policy pass as every other write, sets
GitHub's **`state_reason`** — `completed` for
every closure this tooling makes, `not_planned` for a closure a human makes as a decision — and writes
the reason as a comment before closing, so a closure is auditable and reversible. `state_reason` is not
decoration: the sensor lifecycle (`sensor-registry-and-metric-issues`) reopens only a `completed`
closure, because reopening a human's `not_planned` decision would be the tooling overruling the owner.
No script calls
`gh issue delete`; the string does not appear in the tools.

| alternative | why rejected |
|---|---|
| delete behind `--yes-i-mean-it` | the flag becomes the attack surface and the accident surface; a mistyped destructive call is unrecoverable, while a mistaken close is one click back. |
| delete only for `source/todo-import` issues, for rollback | the rollback this change needs (proposal, "Rollback path") is "revert the tooling", and it leaves the tracker untouched precisely because the import is deferred. A rollback path for change 2 can be a compensating close, not a delete. |
| rely on GitHub's "admin only can delete" | it stops an outsider and not a mistake, which is the case that actually destroys a record. |

### D7 — Issue text is untrusted data: wrapped, escaped, and re-verified

Title, body and comments on a public tracker are attacker-controlled. The design fixes the boundary
now so change 2 cannot get it wrong:

- **prompt injection** — the core's work-package renderer emits issue text inside a delimited,
  labelled data field (`<<<ISSUE_TEXT … ISSUE_TEXT`, provenance and fingerprint alongside it), never
  in an instruction position. The receiver's contract is the `fix-loop` rule that already exists: the
  entry is a **memory, not a fact** — the claim is re-verified against the tree before anything is
  fixed.
- **shell injection** — issue content reaches commands only as `--json`-parsed values, never through
  `eval`, never interpolated into a shell string. Bounds in `tooling.json` (max body bytes, max lines,
  no NUL) make an oversized or malformed issue a **refusal** with a reason, not a partial parse.

| alternative | why rejected |
|---|---|
| strip instruction-like text from bodies | an unbounded filter that must anticipate every phrasing, and it fails open on new ones; it also destroys the finding it is meant to carry. |
| require a signed marker to trust body text | the marker is written by the same untrusted author, so it proves nothing; and signing an issue body is not something GitHub supports. |
| trust issue text because the gate passed | the gate authorises the *actor*, not the *content*: an allowlisted author's issue can still quote an attacker's prose, or their account can be compromised. Authorisation and content trust are separate. |

### D8 — Interface: human text by default, `--json` for callers, distinct exit codes

Every script prints a human-readable line set by default and a single JSON document under `--json`,
because the same script is read by a person and consumed by an agent. Exit codes are the contract:

```
0  acted, or was a deliberate no-op (idempotent re-run)
2  usage / malformed input
3  policy refusal (author not allowlisted, issue not eligible, label outside the closed vocabulary)
4  a duplicate exists (create) or the marker is already present (import) — reported, not an error
```

`3` distinct from `1` matters: a scripted caller branches on "refused" instead of treating it as a
crash, and the loop's later "nothing eligible" state stays distinguishable from "the tooling broke".

| alternative | why rejected |
|---|---|
| JSON always | unusable by hand, and the tooling is documented for humans as well as agents. |
| one generic failure code | a caller cannot tell a refusal from a defect; refusal is expected and must not page anyone. |
| parse `gh`'s human output | it is localised on this machine (German) and changes between releases. |

### D9 — Testability: a stub `gh` on `PATH` plus pure core functions

The self-test needs no token and no network. `gh` is shadowed by `tools/issues/fixtures/gh`, a
script that answers the three `gh` invocations the tools use from fixture JSON and fails on anything
else, so a call the fixtures do not describe is visible rather than silently passing. The Java core
is tested as pure functions: `TODO.md` section parsing (against a committed fixture excerpt with
every `status` shape), taxonomy mapping (including the two renames and a refusal case), fingerprint
stability (same entry, whitespace-varied, must match) and fingerprint separation (two entries that
must not collide). `tools/issue-tooling-selftest.sh` runs both halves and one CI step runs it.

| alternative | why rejected |
|---|---|
| test against the live API | needs a token in CI, mutates a public tracker, and is flaky; it would also make the test's failure mode indistinguishable from GitHub's. |
| record/replay HTTP fixtures via a mock server | a heavier harness for three calls, and it couples the tests to `gh`'s internal URL shapes rather than to our interface. |
| test only the shell layer | leaves the policy and fingerprint — the security-relevant part — untested. |

### D10 — The mechanism is spec-owned: `issue-backlog`

This change creates the capability `issue-backlog` and writes its requirements. An earlier draft set
`skip_specs: true` and argued that harness behaviour is owned by `guidelines/*` + skills + CI rather than
by a spec, citing the archived `2026-10-06-screenshot-evidence-via-view-image` and the open
`fix-screenshot-evidence-transcript-safety`. **That argument was wrong and is corrected here:** this
repository spec-owns ten process and harness capabilities — `build-jvm-toolchain`, `build-sbom`,
`build-test-gate`, `ci-unit-test-jni`, `documentation-ownership`, `kover-aggregate-report`,
`project-metrics-report`, `spec-feature-index`, `test-coverage`, `unit-test-suite-runtime` — and two of
them are this shape of tooling (a report, an index). Two harness precedents do not outweigh ten
counter-examples, and a mechanism that other projects are meant to adopt is exactly the kind of durable
contract a spec is for.

The requirements are written as observable behaviour, not as an implementation: *one issue per distinct
finding*, *a value outside the vocabulary is refused*, *the tooling cannot grant the opt-in to itself*,
*no operation deletes an issue*, *the current project is discovered from its tree*. Where a reason code
or an outcome name appears, it appears because a caller depends on it — that is contract, not
implementation.

| alternative | why rejected |
|---|---|
| keep `skip_specs: true` and leave the contract to the self-test and CI | the ten existing harness capabilities say otherwise, and a copied tooling whose contract exists only in another repository's test suite cannot be reviewed by the project adopting it. |
| write the spec as the scripts' interface (exit codes, flags, file names) | the schema's own test applies: if the implementation can change without changing observable behaviour, it does not belong. The spec states what a caller and an owner can rely on. |
| one broad `agent-backlog` capability for the whole series | the schema warns against catch-all names; one cohesive behaviour family per change also keeps every delta an ADDED one, so no change has to quote a baseline another change has not archived yet. |

### D11 — Portable by construction: one profile, discovery instead of arguments, and the backlog format behind an adapter

The mechanism is meant to be used in other projects, so the split between *the general engine* and
*this project's facts* is a design decision here, not a later refactor. Three parts:

**One profile.** `tools/tooling.json` holds everything project-specific about the tracker: the repo,
`allowedAuthors`, `reservedLabels`, the namespace value sets (including the four bands), the source
renames, the input bounds and the band edges. It merges what an earlier draft split across `core.json`
and `policy.json`, because "adapt one file" is the requirement and two files that must agree is the
drift shape this series rejects everywhere else. The sensors keep `sensors.json` (commands, cadence and
thresholds are inherently per project) and the loop keeps `loop.conf` (its own budget and agent
command); each is read by exactly one tool.

**Discovery, not arguments.** Every script answers "which project am I in?" itself:

```
root     git rev-parse --show-toplevel            never a hard-coded path
repo     gh repo view --json nameWithOwner --jq .nameWithOwner
         (fallback: git remote get-url origin)   never a required --repo argument
profile  <root>/tools/tooling.json               --profile <path> is the only escape hatch
```

No script takes a project name, a repository or a path as a required argument, and `--repo` stays an
override rather than a prerequisite.

**The engine names no project value.** `tools/issues/*.sh` and `tools/issues/core/*.java` must not
contain the repository name, an allowlist entry, a category value, a project path or this project's
name; those live in the profile and reach the engine as data. The class is checkable, and
`tooling-portability` owns the check and a synthetic foreign project that runs the toolchain with a
different repo, allowlist and vocabulary.

**The one exception is made explicit.** The import has a genuinely project-specific half — `TODO.md`'s
`## N.` grammar and the two category renames — so it splits: `tools/issues/import.sh` is the generic
reconciler (*findings JSON → issues*, idempotent on the source id), and
`tools/issues/import/todo-md.sh` is the **source adapter** that parses this project's backlog format and
emits the neutral findings stream. A second project writes one adapter and reuses the reconciler.

| alternative | why rejected |
|---|---|
| per-tool profiles | the allowlist and the vocabulary would exist in three places and could disagree; the failure mode is a gate that passes in one tool and refuses in another. |
| fully configurable namespace names | every skill sentence, query and check would become project-specific, so nothing would be copyable — the opposite of the requirement. Names are the contract; values are the project (D5). |
| hard-code the values and document them | it is what the earlier draft did, and the first foreign project discovers the leaks itself, one at a time. |
| extract to a shared repository or submodule now | premature with one consumer; the profile seam plus the portability check is what makes that extraction cheap later, and a copy is already this repo's distribution model for `tools/` and `.pi/skills/`. |
| make the importer fully generic by changing `TODO.md`'s format | it would edit the backlog to suit the tool, and the format is quoted by 324 references that a migration is already dealing with. |

## Risks / Trade-offs

- **A label's applier is unattributable** → the author allowlist is the attributable term, and the
  design states the limitation rather than implying the label is proof. Residual risk is a compromised
  collaborator, which no label scheme in this design can address.
- **The gate's two terms share one actor** (`gh` is authenticated as `Framstag`, the only allowlisted
  author), so a self-served `agent/eligible` would make the gate vacuous → `agent/eligible` is
  `reservedLabels` in `tooling.json` and every write path refuses it (`label-reserved`, exit 3), the
  import is asserted to arrive with 0 eligible, and revert-check 7.7 shows the case fails when the
  reservation is removed. This is the highest-severity risk in the change and the one most likely to
  be reintroduced by a later convenience flag, so it is listed first among the controls.
- **`gh` output is localised (German) on this machine** → every read goes through `--json`; the stub
  `gh` fixture enforces that by answering only JSON.
- **API rate limits / a 137-issue burst** → paged reads, the advisory `.pi/` cache, `--dry-run` as
  the default, and change 2 throttles the live run.
- **A fingerprint collision between two genuinely different findings** → `create.sh` refuses and
  names the existing issue instead of silently dropping the new one; `--allow-duplicate` is the
  escape and is recorded in the plan output.
- **A stale `.pi/` cache makes `create` look clean** → the refusal path always re-queries; the cache
  may only ever add a match, never remove one.
- **Java 27 locally vs. an older JDK in CI** → the core is written and compiled to
  `--release 17`, and the self-test asserts the JDK feature version and refuses below it.
- **Interim state: four skills exist that `AGENTS.md` does not list** → deliberate and stated in the
  proposal; the existing 12 skills still read `TODO.md`, so no session is broken, and change 2 lists
  them when the rule flips.
- **The import is only ever dry-run here** → the live run's mistakes are not yet observable. The dry
  run's recorded plan (section count, label histogram, dedup report, preflight refusal case) is the
  evidence that stands in for it; change 2 verifies the live run against that plan.
- **Prompt-injection defence is specified now but exercised in change 2** → the boundary is fixed in
  code (the work-package renderer) and tested with a fixture body containing instruction-like prose,
  so change 2 inherits a tested seam rather than a rule in prose.

- **A project value leaks into an engine** → D11 states the rule, and `tooling-portability` owns the check and the synthetic foreign project that proves it. Until that change lands this is unenforced, and the change report says so rather than implying the property is proven.
- **A second project copies the tooling and finds the leaks itself** → the profile is the single adaptation point by construction (D11), and `tooling-portability`'s foreign-project fixture runs the whole toolchain in a project with a different repo, allowlist and vocabulary before any second project does.

## Migration Plan

This change publishes nothing. Sequence:

1. taxonomy definition + `labels.sh` (dry-run capable; creates labels only when `--apply`);
2. core: `Policy.java`, `Fingerprint.java`, `TodoSections.java` + `tooling.json`;
3. shell layer: `lib.sh`, `policy.sh`, `create.sh`, `update.sh`, `close.sh`, `list.sh`;
4. `import.sh` with `--dry-run` default;
5. self-test + fixture stub `gh`; one CI step;
6. the four skills;
7. one real dry run over `TODO.md`, output recorded.

Rollback: revert the files (proposal, "Rollback path"). No issue, label or comment is created by any
step above — only a dry-run plan and local fixtures exist, so rollback leaves no public state.

## Open Questions

Genuinely deferrable — none changes the approach or the task breakdown:

- Whether change 2 adds a GitHub issue *template* (`.github/ISSUE_TEMPLATE/`) so a human-filed
  finding arrives with the Observed / Why / Impact / Fix-candidate skeleton the import produces.
- Which saved searches replace `TODO.md`'s generated cluster index, and their names.
