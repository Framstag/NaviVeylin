# Reason for the closure of #172

**Archived change:** `openspec/changes/archive/2026-10-18-fix-portability-value-set-and-preconditions/`
(the reference is available once the change is archived; before that,
`openspec/changes/fix-portability-value-set-and-preconditions/`).

## What was wrong

`tools/check-portability-selftest.sh` ran its cases from wherever it was copied to, with no precondition on
its root. Two of its cases need a checkout: `portability#passesOnTheRealTree` drives the real tree's check
(which refuses to resolve a root outside a git working tree, `exit 2`) and `foreign#fixtureIsVersioned`
asserts the root is not machine-local state. Run from a `git archive` export, the suite reported
`FAIL portability#passesOnTheRealTree` (1 of 21 cases failed); run from a copy under `.pi/`, it reported
`FAIL foreign#fixtureIsVersioned` (1 of 21). Both readings were verbatim in `evidence/red-on-head.log`; the
same commands are green in the checkout.

## What now makes it impossible

- `tools/check-portability-selftest.sh:33-44` refuses **before anything is read or created**: outside a git
  working tree, and from a root path containing `/.pi/`, it names the reason and exits 2 — the shape of its
  other environment refusals. Its header states both preconditions, and `-h` prints them.
- The two readings are held by
  `tools/portability-cases.sh`'s `preconditions#refusesOutsideAGitTree` and
  `preconditions#refusesUnderMachineLocalState`, on a copy of the suite taken the way a verifier takes it.

## Evidence

- Red on HEAD: `evidence/red-on-head.log` — both readings verbatim (1 of 21 cases failed each), and the two
  new cases red (`5 of 26 cases failed`).
- Green: `evidence/verify.md` — `check-portability-selftest: all 26 cases passed`, including both
  `preconditions#…` cases.
- Falsification: `evidence/revert-checks.md` — mutations 6.4 (the git-tree precondition) and 6.5 (the
  `/.pi/` precondition) each make their case red, restored green.
