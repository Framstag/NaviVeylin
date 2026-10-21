# fix-ambiguous-section-reference

## Why

Measured on 2026-10-21 at base `44b673f` (issue #98, `§154`, found 2026-10-07 while applying
`scope-guideline-reads`): `guidelines/MapRendering.md` numbers two sections `## 14.` — `:612`
`## 14. Rotation Gesture Display-Layer Handoff` and `:689` `## 14. Android Auto renderer — smooth follow
(overrun + blit + extrapolation)` — so a bare `§14` of that document names two sections, and the two live
readers of it already disagree: `AGENTS.md:28` writes the heading text (`§14 "Android Auto renderer"`), while
`guidelines/Design.md:503` writes the bare number for the AA renderer and is the one reference in the tree
that cannot be resolved.

**Root cause, one sentence.** The refusal that makes a bare duplicated number a defect is implemented in
`tools/check-doc-routes.sh` over **one** place — the `## Documentation Map` region of `AGENTS.md` — while the
convention it enforces is written in exactly the place a citation is also written, a guideline body, so a
bare `§14` in `guidelines/Design.md` (and the next one anybody writes) escapes the check that exists to stop
it.

The evidence, in the tree at `44b673f`:

- **Before this change**, `tools/check-doc-routes.sh` extracted references from `"$tmp/region"` alone (the
  map region, read in its step 2), and its three resolve-branch `printf`s reported every error with
  `file=AGENTS.md` hard-coded. The check already *opened* every `guidelines/*.md` to collect its `## `
  headings, but it had no path that extracted **references** from a body.
- `grep -rhoE 'MapRendering\.md[` ]*§14' --include=*.md .` returns **22** references: 20 in
  `openspec/changes/archive/` (17 files), 1 in `AGENTS.md:28` (with its heading text), 1 in
  `guidelines/Design.md:503` (**bare**). The archive is split both ways over the duplicated number —
  `archive/2026-10-09-fix-follow-rotation-crash/design.md:13` cites `§14` for the rotation handoff, while
  `archive/2026-10-05-fix-car-render-loop-restart/proposal.md:38` cites `§14` for the AA renderer — so the
  ambiguity is not theoretical: it is already read both ways in the recorded history, which cannot be
  rewritten.
- Measured over every explicit `<File>.md` §N reference in `guidelines/*.md` (30 references), **one** is
  ambiguous (`guidelines/Design.md:503`) and none is dangling; over `guidelines/*.md` +
  `openspec/specs/*/spec.md` + `.pi/skills/*/SKILL.md` + `README.md` (65 references) the same one. So the
  guard costs nothing today beyond fixing that one line.
- `guidelines/MapRendering.md` itself writes **no** `§14` self-reference (0 of its 18 bare `§N`
  self-references), so the duplication does not bite inside the document that carries it.

**The three red cases this change adds fail on HEAD `44b673f`** (a shell gate has no JUnit XML; the case's
own message and exit status are the record — `evidence/red/selftest.txt`, produced by running
`bash tools/check-doc-routes-selftest.sh` against `git show 44b673f:tools/check-doc-routes.sh` with this
change's case file):

| case | red on HEAD | timestamp |
|---|---|---|
| `a bare duplicated number in a guideline body is reported as ambiguous` | `expected exit 1 containing 'ambiguous', got exit 0: check-doc-routes: all 5 section references resolve` | 2026-10-21T11:53:49Z |
| `a heading text in a guideline body resolves its duplicated number` | `expected exit 0 containing 'all 6 section references resolve', got exit 0: … all 5 …` — the body reference was not seen at all | 2026-10-21T11:53:49Z |
| `a guideline body naming a section its document lacks is reported` | `expected exit 1 containing 'UI.md §42', got exit 0: check-doc-routes: all 5 section references resolve` | 2026-10-21T11:53:49Z |

Tally on HEAD `44b673f`: `check-doc-routes-selftest: 12 passed, 3 failed` (exit 1). Green after the fix
(2026-10-21T12:09:48Z, after the round-2 corrections): `15 passed, 0 failed` (exit 0), and on the repository
itself
`check-doc-routes: all 120 section references resolve` (exit 0).

Two further cases are **counter-cases, not red cases**: `a reference inside a fenced block is not a
reference` and `a reference in a change artifact is not checked` each pass on HEAD for the wrong reason (the
body is never read, so neither the block's reference nor the artifact's is counted) and pass after the fix
for the right one. Their job is to keep the new scan from over-reaching — without them the fix would be free
to read a quoted block and the history it must leave alone.

**The owning requirement and the owning rule.** Capability `documentation-ownership`. This delta ADDS the
requirement "A section reference a guideline body writes is resolved", which is the missing half of the
shipped requirement "A route resolves to a section that exists" — that one is scoped to a *route*, and the
defect lives in a reference that is not a route. The requirement is scoped to a reference a body makes to
another **guideline**, because that is what the check implements: its document alternation is built from
`guidelines/*.md`, so a reference is resolved only against a guideline document named before it on the same
line — measured today, 30 of the 66 `<File>.md §N` references in `guidelines/*.md` bodies name a guideline
(all 30 resolve) and the other 36 name `TODO.md`, on 36 lines that name no guideline, so none is counted.
The guideline section that owns the check's
own description is `guidelines/Backlog.md` §14 "Keeping this document honest", whose bullet "The routing
table must name this document" named only the unrouted rule; a sibling bullet now states the body-reference
rule.

**Why it matters.** A reader following `guidelines/Design.md:503` today lands on the rotation-gesture section
and reads the wrong rule about the car renderer's fault budget; the routing table's own row for that
capability already had to spell the heading text out to stay readable, and nothing stopped the next bare
`§14` from being written. The check that exists for exactly this refusal is the thing that did not see it.

## What Changes

- `tools/check-doc-routes.sh` — the reference extraction is factored into `extract_refs` and run over the
  map region **and** every `guidelines/*.md` body; a body is read with fenced code blocks skipped (a quoted
  reference is an example, not a reference), and every error now names the file the reference was written in
  instead of naming `AGENTS.md` unconditionally. The header comment (printed by `-h`) states the widened
  scope and that an archived change or an open change artifact is history, neither checked nor rewritten.
- `tools/check-doc-routes-selftest.sh` — five fixture cases added (`body-ambiguous`, `body-text`,
  `body-fenced`, `body-dangling`, `artifacts`), each against a throwaway tree, plus the header's case list;
  the `expect` helper now takes every substring a case requires, so the two error cases assert the reported
  `file=` as well as the message.
- `guidelines/Design.md:503` — the bare `§14` becomes `§14 "Android Auto renderer"`, the form `AGENTS.md:28`
  already uses; the section it means is unchanged.
- `guidelines/Backlog.md` §14 — the bullet on `tools/check-doc-routes.sh` now says the same check resolves a
  body reference and refuses a bare duplicated number there.
- `openspec/specs/documentation-ownership/spec.md` — one requirement added, restated below.

## Cases and evidence

- `a bare duplicated number in a guideline body is reported as ambiguous` — `guidelines/Build.md` carries
  ``The renderer rules live in `UI.md` §14.`` in a fixture whose `UI.md` numbers two sections 14; the check
  exits 1, reports the reference ambiguous, and reports it against `file=guidelines/Build.md`.
- `a heading text in a guideline body resolves its duplicated number` — the same sentence with
  `§14 "Android Auto renderer"` exits 0 and counts the body reference among the 6 it resolved.
- `a reference inside a fenced block is not a reference` — the bare ambiguous reference inside a fenced block
  exits 0 with 5 references resolved, so the block was skipped rather than resolved (counter-case: it is green
  on HEAD too, for the opposite reason).
- `a guideline body naming a section its document lacks is reported` — `` `UI.md` §42 `` in a body exits 1,
  names `UI.md §42`, and reports it against `file=guidelines/Build.md`.
- `a reference in a change artifact is not checked` — an archived change and an open change artifact each
  carrying `` `UI.md` §14 `` exit 0 with 5 references resolved, so neither was read (counter-case, green on
  HEAD for the same opposite reason).
- Green: `evidence/green/selftest.txt` (`15 passed, 0 failed`, `all 120 section references resolve`), plus three
  revert-checks — `evidence/green/mutation-ambiguity.txt` (`count[key] > 1` → `count[key] > 99`: 13 passed, 2
  failed, both ambiguity cases), `evidence/green/mutation-dangling.txt` (`if (!(key in headings))` → `if (0)`:
  14 passed, 1 failed, the new body-dangling case), and `evidence/green/mutation-report-file.txt` (a body's
  reported file forced back to `AGENTS.md`: 13 passed, 2 failed, the two body error cases — the assertion the
  round-1 review found missing).

## Impact

| file | module / area |
|---|---|
| `tools/check-doc-routes.sh` | the routing check: what it reads, and what it reports |
| `tools/check-doc-routes-selftest.sh` | the check's cases |
| `guidelines/Design.md` | the AA renderer citation in §8, one line |
| `guidelines/Backlog.md` | §14, the bullet that documents the check |
| `openspec/specs/documentation-ownership/spec.md` | one requirement added |

No Kotlin, Java, C++, JNI, manifest, resource, Gradle or CMake file is touched, so the gate this change's
evidence rests on is the check's own self-test, not a Gradle suite: `bash tools/check-doc-routes-selftest.sh`
and `bash tools/check-doc-routes.sh` (both exit 0), which is the structural check the change's build task is
replaced with (`guidelines/Build.md` §2, the scope block). `tools/check-portability.sh` is unaffected: the
check still derives its documents from `guidelines/*.md` and still reads no project value.

## Previous specifications changed

- `openspec/specs/documentation-ownership/spec.md` — **ADDED** the requirement "A section reference a guideline
  body writes is resolved" with its five scenarios. The shipped requirement "A route resolves to a
  section that exists" and its four scenarios keep their wording: a route is still resolved by the check, and
  this change widens what the check reads, not what a route must say.

## Additive or breaking, and rollback

**Additive for the check, breaking for one line of prose.** A guideline body that writes a bare number its
document uses twice now fails the check instead of passing; no reference that resolves today stops resolving,
and the one reference that did not resolve is repaired here.

Rollback: revert this commit. The check reads the map region alone again, the three red body cases fail with
the messages above (the fenced counter-case passes either way), and `guidelines/Design.md:503` returns to the
ambiguous bare number. Nothing else reads the extraction function.

## OpenSpec

- Change: `fix-ambiguous-section-reference`
- Issue: #98 (`§154`)
