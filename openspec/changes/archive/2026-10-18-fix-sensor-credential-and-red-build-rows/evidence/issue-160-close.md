# Issue #160 — closure reason and evidence

**Archived change:** `openspec/changes/archive/2026-10-18-fix-sensor-credential-and-red-build-rows/`

## Reason

The credential's permissions have one live home, and the two live claims that contradicted them are
corrected. The registry is unchanged, so every declared row can run with the secret the owner decided on
(2026-10-17): **Issues: Read and write** plus **Pull requests: Read**, repository access limited to this
repository, owner an allowlisted person.

**Root cause (one).** The scope was documented in prose rather than derived from what the rows do. The
`pr-conflicts` row calls `gh pr list` (`tools/sensors/conditions/pr-conflicts.sh`), whose endpoint sits
behind the fine-grained permission **Pull requests: Read**, while `.github/CODEOWNERS` and
`guidelines/Backlog.md` §9 both said the token could do "`Issues: write` only". Nothing connected the
documented scope to the verbs the declared rows actually call, so the claim could not fail until a secret
built from it was used.

**The single resulting fix.** `guidelines/Backlog.md` §9 — the section that owns the checks' doctrine and
already states the identity rule — now names the credential's permissions (`**Issues: Read and write** plus
**Pull requests: Read**`, this repository only, an allowlisted owner) and why the second one is there.
`.github/CODEOWNERS` line 3, the other live claim, was corrected in the same breath and points at §9. The
archived `wire-sensor-inputs` `report.md` and `design.md` D2 are history and were left untouched, as the
issue states.

**The case, so the claim cannot drift again.** `sensors#theDocumentedCredentialCoversEveryDeclaredRow`
(`tools/sensor-cases.sh`) reads the scope out of §9 and derives the required permissions from the rows' own
commands: it asserts both permission names, then walks `jq -r '.rows[].cmd'` and fails when a command calls a
`gh` verb whose permission the documented scope does not name (`gh pr …` → Pull requests, `gh issue …` /
`gh label …` → Issues). It was red on HEAD (`the doctrine does not name the tracker's write scope:
'Issues: Read and write' not found`, `evidence/red-on-head.log` §2) and is green now
(`evidence/green.log` §1, 40 of 40 cases).

## Evidence

Red on HEAD (`../../evidence/red-on-head.log` §2, HEAD `bf6f5d7`):

```
  the doctrine does not name the tracker's write scope: 'Issues: Read and write' not found in: ## 9. Checks
FAIL sensors#theDocumentedCredentialCoversEveryDeclaredRow
sensor-selftest: 4 of 40 cases failed
```

Green (`../../evidence/green.log`):

```
$ bash tools/sensor-selftest.sh
sensor-selftest: all 40 cases passed (JDK 27, offline, no token)
$ bash tools/sensor-selftest.sh --case sensors#theDocumentedCredentialCoversEveryDeclaredRow
PASS sensors#theDocumentedCredentialCoversEveryDeclaredRow
```

The shipped requirement now states the scope too, so a secret created as the documentation says can run
every declared row: `openspec/specs/backlog-sensors/spec.md`, "The checks run in a pipeline of their own,
pinned and least-privileged" — "given the credential its declared rows need and no more — write access to
issues, and read access to pull requests for the row that lists them", with the scenario "The credential's
scope covers every declared row" naming the case above.

## What this closure does not cover

The secret itself is a repository setting: recreating `SENSORS_TOKEN` with the scope §9 now states is a
person's step in the GitHub UI, and nothing in this tooling can read or change it. Nothing else is owed.

## The closure run

```
$ tools/issues/update.sh 160 --status fixed-by
https://github.com/Framstag/NaviVeylin/issues/160
exit=0

$ tools/issues/close.sh 160 --reason ... --evidence ...
✓ Closed issue Framstag/NaviVeylin#160 (The sensor pipeline's documented token scope omits Pull requests: Read, which the pr-conflicts row needs)
closed #160 (completed)
exit=0
```
