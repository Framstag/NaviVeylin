# Design — a relocation record names the owners it cites

## The root cause

A document change proves that a removed block's statements live elsewhere by citing the documents that own them.
The record this rule comes from cited the *directory* the owners live in — `.pi/skills/*/SKILL.md`, a glob beside
all 35 contracts it expands to — while the block it justified named eight skills
(`evidence/relocation-entry-document.txt:19`, `:21`-`:55`; `git show 4330ce3e^:AGENTS.md` table rows = 8). The claim
is uncheckable from the record: its reader is given a set of 35 where the claim is about eight, and nothing reports
the difference. That is the mechanism, and it is one mechanism.

## The fix

One clause in `documentation-ownership`, and the class that enforces it in the checker that already owns the
silent-failure classes of the OpenSpec artifacts:

- **The rule.** A relocation record names each owner as a path of its own, the way the removed block named them, and
  never as a glob — the form a directory's complete listing takes. An archived record is not inspected: it is
  history.
- **The class.** `tools/check-openspec-hygiene.sh` reads the `owner:` lines of the open changes' artifacts and
  reports one that carries a glob, with the file and the line. It skips `openspec/changes/archive/`, so the record
  the rule came from keeps exactly what it recorded.
- **The case.** `artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported` builds the record above into an open
  change and the same lines into an archived one, and requires the first to be reported and the second not to be.

The class is lexical — a citation carries a glob or it does not — and the requirement says no more than that: the
record must *let* a reader check the claim for the set it is about, and the check refuses the form that stops them.
Whether the named set is the block's set stays where it already was: the change's own review, which is what graded
G2 a NOTE on evidence quality in the first place.

## Why this home, and not the two that were eliminated

- **`tools/loop/evidence-check.sh`.** Its subject is a pointer that does not resolve: a `§N` with no row, a
  `tests=` pair no XML carries, a `path:NN` past the end, a `/tmp` path. Every citation in the record that produced
  this item resolves — the 35 skill paths all exist — so nothing it already checks would have fired, and widening
  its subject from "resolves" to "resolves to the set the claim is about" is a second concern in a tool whose
  header states the first one. Measured, not assumed: run over that change it prints `ok C5 file:line: 1042 resolved
  and in range, 0 unresolved or ambiguous` and `evidence-check: PASS`
  (`evidence/why-not-evidence-check.txt`), and the record's 35 owner paths are not `path:NN` citations at all — the
  same command counts 0 of them — so the pointer pass never reads them. It is also the heavier gate — a path under
  `tools/loop/**` owes 288 of the loop's 305 declared cases (`grep -c 'files: .*tools/loop/' tools/loop-cases.sh`),
  where this change owes two shell suites.
- **`tools/check-doc-routes.sh`.** It reads routes, not records, and its header states that a change artifact's text
  is neither checked nor rewritten. The rule is about how a change proves a relocation, which is an artifact's
  business.
- **Repairing `relocation-entry-document.txt`.** The item forbids it and it is right: the archived record is what
  was written at the time, and rewriting it would destroy the evidence that the gap existed. It is also the one
  thing the new class must not do, which is why it skips `archive/`.

## Blast radius

One checker, one selftest, one added requirement. No compiled file and no path under `tools/loop/**`, so the gate is
the shell checks an `openspec/*` and a `tools/check-openspec-hygiene*` path owes. Measured over the tree: 10 `owner:`
lines in one record, 1 of them carrying a glob, and that record is archived — so the class reports nothing today and
its first finding is the next document change's.

The one behaviour change to an existing case: `clean_tree` gains a well-formed `owner:` line, so
`artifact-hygiene#aWrittenPurposePasses` keeps proving that a clean tree passes a checker that now reads one more
class.

## Rollback

Delete class 4 from `tools/check-openspec-hygiene.sh`, the case and the fixture line from
`tools/check-openspec-hygiene-selftest.sh`, and the requirement from the delta before archive. Nothing else in the
tree consumes the class, so the rollback is subtractive and leaves no route dangling: the case id is cited only by
the requirement it lands with.

## Open decisions

none
