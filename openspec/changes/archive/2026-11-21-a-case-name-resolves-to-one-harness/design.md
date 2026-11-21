# Design — resolve by full name across a declared registry, and count the rest

## Context

See `proposal.md` — Why. What constrains the design:

- **Four harnesses exist and each keeps its own list**: the loop suite (`LOOP_SHELL_CASES` in
  `tools/loop/tests/loop-cases.sh`, 461 names), the sensors suite (`tools/sensors/tests/sensor-cases.sh`, 93), the
  issue tooling's Java cases (`tools/issues/core/Cases.java`, 18, listed by `Cases --list`), and the Gradle/Kotlin
  suites of `app`, `auto`, `core` and `buildSrc`.
- **Group prefixes are not owned.** `run#` and `report#` are used by the loop suite *and* the sensors suite. That is
  not itself harmful: no shipped spec names both, and only one full name is currently in both. Enforcing prefix
  ownership would mean renaming ~309 loop cases or 93 sensors ones — a rename with no behaviour behind it.
- **A case may legitimately serve no scenario**: `stub#theStubWritesAStatus`, `driver#everyCaseDeclaresTheFilesItCanReach`
  and every `usage#` case prove the harness, not a requirement.
- **The readiness checker's lesson is the design's first rule**: it reads no sentence for meaning, because a
  body-reading rule the project trusted was "wrong about 48 of 49 verdicts" (`guidelines/Backlog.md` §6).
- **The baseline is green**: 0 unresolved names. A check whose baseline is green needs no shrink-only baseline, no
  exclusion list and no baseline file — it can be red-provable in its first commit.

## Goals / Non-Goals

Goals: a scenario's case name is proven real, a duplicate name across harnesses is refused, the unclaimed cases are
a number, and the whole check is text over declared lists.

Non-goals: prefix ownership; renaming a case; any judgement whether a case *actually* exercises the scenario it is
named by (that needs an instrumented run, and it is named in the proposal as a follow-up, not pretended here); any
claim about spec-vs-engine content agreement.

## Decisions

### D1 — Resolve by full name across all harnesses, not by prefix ownership

Alternatives:

- **One group belongs to one harness** (rejected): it forces a rename of ~309 loop cases or 93 sensors cases and
  buys nothing — the reader's question is "does this case exist", not "which suite may own this prefix".
- **Resolve against the loop suite only** (rejected): the first measurement showed it reports 30 false dead
  references, all of which resolve in another harness. A check that cries wolf 30 times is turned off.
- **Prefix-qualified names in the spec** (`loop:run#foo`) (rejected for now): it would be the cleanest forward
  statement, and it means editing 1252 spec lines. Recorded here as the alternative that becomes right if a full-name
  collision ever reaches a spec line.

Chosen: a name resolves if exactly one *declared harness* lists it. A group two harnesses share is declared in the
registry (so the possibility is visible), and only an identical full name in two harnesses is refused.

### D2 — The registry is a tooling config beside the profile, not part of it

Alternatives:

- **Inside `tools/tooling.json`** (rejected): the profile is *the project's* vocabulary — repo, labels, roles, band
  edges — and a copy of the tooling edits it. Which scripts define which case list is a fact about the tooling, and
  travels with it unchanged.
- **Derived, with no registry** (rejected): the loop's list is a bash array, the sensors' is a bash array, the issue
  tooling's comes from `Cases --list` and the Gradle ones from the build's XML. Deriving them means four
  harvesters with four failure modes; the registry names the four and the check reads what it names.
- **One row per case** (rejected): 572 rows maintained by hand, which is a second list to drift.

Chosen: `tools/case-harnesses.json` — per harness: `id`, `list` (the file or command that produces its names), the
`groups` it claims, and `surfaces` (which module's tests it is, for the read-only human report). A harness whose list
file is missing is a refusal, because a registry row pointing at nothing proves nothing.

### D3 — The backward direction is a count, never a refusal

Alternatives:

- **Refuse an unclaimed case** (rejected): 69 loop cases are unclaimed today and most are harness-level; a refusal
  would need 69 exemptions, and the exemption list is exactly the drift this project keeps paying for.
- **Say nothing** (rejected): then a case proving behaviour no requirement knows about is invisible, and the number
  is the only cheap signal that a requirement may be missing.

Chosen: printed as one line, listed under `--json`, part of the verdict line so a reader always sees it. A person
decides whether an unclaimed case means a missing requirement or a harness helper.

### D4 — It runs where the other hygiene checks run, and the loop's gate never consults it

Alternatives:

- **In the loop's gate** (rejected): a spec-wide invariant would then be a reason an *item's* gate goes red, and the
  loop's own selection (`# files:`) names no spec.
- **Only as a self-test** (rejected): a self-test proves the tool, not the tree.

Chosen: the CI artifact-hygiene step beside `openspec doctor` and the `## Purpose` check, and reachable by name
through `tools/tooling.json`'s `checks` map so no tool hard-codes its path (`tools/check-portability.sh`'s rule).

### D5 — One verdict line, in the shape the other checkers already print

Alternatives:

- **Free-form prose** (rejected): a caller cannot quote it into a comment or a work package.

Chosen: `CASE-NAMES: ok unresolved=0 harnesses=4 unclaimed=69` / `CASE-NAMES: refused unresolved=2 …` with each
refusal named by harness and line on the same line, exit 0/1 — the `LOOP-CHANGE-READY:` shape that already exists
in this project, so a reader learns one form.

## Risks / Trade-offs

- **The registry rots** (a harness renames its list file) → a row whose `list` is absent is a refusal, so the rot is
  red on the commit that causes it.
- **The Gradle harnesses are expensive to list** → the registry names their XML/class pattern and the check reads the
  *test source names* rather than a report, so it stays offline and under a second; the cost of a real report is not
  this check's business.
- **A full-name collision reaches a spec line** → the refusal names both harnesses and the spec line, and D1's
  recorded alternative (prefix-qualified names) becomes the fix.
- **`--json` grows into a second API** → the document carries exactly the three counts and the two lists the two
  directions produce; nothing else reads it.

## Migration Plan

1. The registry describing today's four harnesses and their 572 names; the check must print `ok` with
   `unclaimed=69` and the collision count as a *warning* line (the one collision, named) so that step 2 can decide.
2. Resolve the one latent collision (`run#dryRunTouchesNothing` in both suites) by renaming the sensors' case, which
   names no spec and no requirement, and turn the warning into a refusal.
3. Join the CI step and record the baseline numbers.

Rollback: revert the commit. Nothing at run time reads the registry.

## Open decisions

none
