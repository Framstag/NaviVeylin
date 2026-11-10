# Design — one derivation, and the change's own files under one name

Reference: `proposal.md` for the measurements, `specs/agent-issue-loop/spec.md` for the clause this change modifies.

## The root cause

`loop_tree_identity` (`tools/loop/lib.sh:313`) takes the digest of the paths the round's diff carries: `git diff
--name-only <base>` plus `git ls-files --others --exclude-standard` (`:333-334`), each hashed by its worktree content.
A change being worked is gitignored (`openspec/changes/*/`, `.gitignore:76`), so it is in neither listing and
contributes nothing; its archived, committed self is named by the base's diff and contributes every non-evidence path
of it. One mechanism explains both readings the item names — the tail's own repair after the gate, and #721's
archived record — because the digest is a function of *tracking and location*, and both moments the reviewer compares
sit on opposite sides of the archive's rename.

## The single fix

`loop_tree_identity` enumerates the change family's own files as the working tree holds them, tracked or not
(`git ls-files --others --ignored --exclude-standard`, `:335`), through the same evidence exclusion the round's diff
carries, and normalises every path under the family's `archive/` back to the name the change had before it was
archived: `loop_change_path` (`:296`) removes the date prefix the profile records as `LOOP_CHANGE_ARCHIVE_PREFIX`
(`tools/loop/loop.conf:486`) from the archived directory's name. The listing is hashed and de-duplicated after that
normalisation, so the same content under either name is one entry with one hash.

`loop_change_identity` (`:356`) is untouched, so the gate (`tools/loop/gate.sh:209`) and the diff a round is handed
(`tools/loop/run.sh:1307`) still take one derivation with no path set of its own — the family and the prefix are read
from the profile inside it, which is where `LOOP_REVIEW_EXCLUDE_PATHSPEC` already came from.

What follows from it, clause by clause:

- the archive's move of an uncorrected change is invisible to the digest, so an item lands without a hand repair;
- a non-evidence file of the change rewritten after the gate still moves it — #721's shape stays visible to the
  reviewer's third item;
- the gate's own `evidence/` stays out of both halves, so a gate that writes its log after naming its tree is not a
  correction of it;
- an archived change's path set and an open change's path set are one path set, so the two moments cannot differ about
  where a change lives.

## Blast radius

- `tools/loop/lib.sh` — `loop_change_path` (11 lines) and the family enumeration inside `loop_tree_identity`; the
  printed line, the `head=` field and the digest's length are unchanged, so every reader of a `head=… diff=…` line is
  untouched.
- `tools/loop/loop.conf` — one key, with its derivation in the block `LOOP_CHANGE_SOURCE_DIR` owns.
- `tools/loop/tests/loop-cases.sh` — one case, two registration sites.
- `tools/loop/tests/loop-mutations.sh` — two records.
- `guidelines/Backlog.recipes.md`:722 — the paragraph that states the derivation.

Cost: the family enumeration is one `git ls-files` and one `git hash-object` per file it adds — 83 hashes and 290 ms
on this working tree with its 18 open changes (`evidence/probe-cost.log`). The digest's own listing is unchanged, and
no other gate step reads anything new.

## Rollback

Revert the one commit: the key, the helper and the enumeration are the whole change, and the digest returns to the
tracking- and location-shaped one. Nothing records a digest as a value to be compared across the revert, so no stored
record becomes wrong; a gate record taken after the revert does not match an identity taken before it, which is the
state the tree was in before this change anyway.

## The alternatives the evidence eliminated

The item named two levers and the recorded decision added two more; all four were weighed on the item, and the
evidence eliminates three of them:

1. **Exclude the whole change family from `LOOP_REVIEW_EXCLUDE_PATHSPEC`** — one profile value, cheapest, but the
   reviewer's item 3 then cannot see a post-gate rewrite of `proposal.md`, `design.md` or `tasks.md` at all: the #721
   shape becomes invisible, which is the signal the requirement states.
2. **The tail re-runs the gate on the corrected tree** — correct by construction, but a full round is 600–1 100 s on
   every item, or a scoped run whose base is the repair alone, which no caller computes today; 78 % of a gated
   item's spend already falls after its first gate.
3. **The tail may not repair** — cheapest to implement, but every repairing item stops instead of landing, which the
   item's Expected rules out.
4. **The relocation-invariant digest** (the recorded decision, and this change) — the only arm that satisfies the
   item's Expected without a second gate, and the only one that leaves a genuine post-gate correction visible.

One path set, one derivation, and no rule restated at a call site: the derivation reads the family, the archive's
prefix and the evidence exclusion from the profile, which is where the project's own paths live
(`tools/check-portability.sh`).
