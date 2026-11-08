# Design — one derivation of "which tree was measured", used at both ends of the gate

## Root cause

`tools/loop/gate.sh:290` opens the per-run record and `:304` writes one line per full run,
`$session_key\t$gate_argv`. The record is keyed to the child that paid for the run and says nothing about the tree
the run measured, and the one case that asserts the record's content asserts its line count
(`tools/loop-cases.sh:12615`). A gate over a tree and a gate over that tree corrected afterwards therefore leave the
same record, and the reviewer's third item — "is this a real execution *for this change*?" — has only timestamps to
read. On 2026-10-22 that cost a claim: `openspec/changes/archive/2026-10-22-fix-staging-leftover-discovered-as-map/tasks.md`
§5.6 records a round-2 gate re-run, and every XML under that archive's `evidence/gate/` is stamped
`12:23:41.923Z`–`12:26:30.823Z` against the round-2 runs at `12:42:56.420Z` and `12:43:06.048Z`.

## The single fix

One derivation, `loop_change_identity <tree> <base>` in `tools/loop/lib.sh`, called by both processes that have to
agree about a tree: the gate writes it into its own record and prints it, and the run writes it beside the diff a round
is handed. The reviewer compares the two recorded values; nothing infers a tree from a timestamp. The helper is a
one-line wrapper around `loop_tree_identity <tree> <base> [<pathspec>]` that reads the pathspec from the profile, so
the path set is a property of the derivation and not of a caller: neither end can hold one of its own.

**Why the digest is over the change and not over `HEAD`.** The change child measures an *uncommitted* tree — that is
the tree the gate child and the worker both run in — and the run commits it before the diff a reviewer reads is
written (`tools/loop/run.sh:2212`, `worktree.sh end-item`). A digest of `HEAD`, or of `git status --porcelain` plus
`git diff` (the shape `run.sh` already uses for its outside-write guard, at `tools/loop/run.sh:778`), changes when the
same content moves from the working tree into a commit, so the ordinary flow would read as a corrected one and every
round would be refused for a difference that is an artefact of the commit. The digest is therefore taken over the
*paths* the change touches — `git diff --name-only <base>` plus `git ls-files --others --exclude-standard` — each with
`git hash-object` of its working-tree content, and a path the change deletes is recorded as `deleted`. Committed,
staged and unstaged content of the same change give the same listing and the same digest.

**Why that listing leaves the generated trees out.** The gate names its tree *before its first step*, and from then on
it writes its own tallies and XML into the change directory; the run derives the identity beside the diff long after.
With a listing that took every path, those two moments name two digests on a tree nobody corrected — and the new item 3
is told to grade exactly that as blocking, so the fix would have refused every change the loop gates. The listing
therefore takes the same pathspec the round's diff is built with (`loop.conf`'s `LOOP_REVIEW_EXCLUDE_PATHSPEC`,
`:662`), which is the one exclusion both ends can read from one place. Found in this change's round-1 review; the case
that keeps it true is `gate#theGateAndTheRoundNameOneTree`, which derives both moments and compares them, and mutation
`262.4` is the one that removes the pathspec and turns it red.

**Why the commit is recorded beside it.** The digest says *what* was measured; the commit says *when* — it is the
attribution a reader needs when two runs agree about content but not about history, and it is what the item asked
for (`git rev-parse HEAD` plus a digest of the artifact). It is not part of the comparison: it is the digest that
answers item 3.

**Why the run writes it beside the diff instead of into the prompt.** The diff is written by one function
(`write_review_diff`) that already knows the run's base and the worktree, and the identity belongs to the artifact it
describes: a `review-<round>.diff` and its `review-<round>.diff.identity` cannot be separated by a copy, and the
correction round — whose diff is `git diff <refused head>..HEAD` — still carries the identity of the *change*, because
the function derives it from the run's own base and not from the range it wrote. A placeholder in the reviewer
template would have to be threaded through three render sites and would be a second record of the same value.
**Why the refusal names the tree too.** A refusal already names each earlier run's command; naming the tree beside it
costs one field and is what lets a reader see, in the refusal itself, that the runs it counted were over a tree that
has since moved — the state in which a third run is not a repeat of the same measurement.

## Blast radius

- `tools/loop/lib.sh` — `loop_tree_identity` and the one-line `loop_change_identity` over it, no project path and no
  project value: the pathspec is a profile key like every other, so the engine names nothing of its own (D16), and
  `tools/check-portability.sh` keeps that true.
- `tools/loop/gate.sh` — one identity computed after the base is resolved, one console line before the first step
  (guarded by `--plan`, which measures nothing), one third field in the record, and the refusal's per-run line.
  The steps, clocks, tallies, waves and exit codes `0`/`1`/`2`/`3` are untouched.
- `tools/loop/run.sh` — one line inside `write_review_diff`, so all three call sites (pre-gate diff, round diff,
  correction-round diff) carry it.
- `tools/loop/prompts/reviewer.md` — item 3 names both values and grades a mismatch `BLOCKING:`. Item 7's reading set
  is unchanged, and the prompt states no second definition of *blocking*.
- `guidelines/Backlog.recipes.md`:567 — the rule, the falsifying observation and the measurement, in the paragraph that
  already owns the record.
- `tools/loop-cases.sh` — one case rewritten (`gate#aThirdFullRunInOneSessionIsRefused`, which owns the record), one
  added for the diff's identity (`run#aReviewDiffNamesTheTreeItWasWrittenFrom`), and one for the two moments agreeing
  (`gate#theGateAndTheRoundNameOneTree`); `tools/loop-mutations.sh` — four records, `262.1`–`262.4`.
- No compiled file: no Gradle task is owed, and the shell suites are the whole gate for this delta.

## Alternatives the evidence eliminated

- **A digest over `HEAD` alone.** Eliminated: a correction that is committed moves `HEAD`, and so does the run's own
  commit of a tree the gate has just measured — the second makes every ordinary round a mismatch.
- **A digest over `git status --porcelain` plus `git diff`** (the shape `run.sh:2461` already uses for the
  reviewer-write guard). Eliminated for the same reason: it is empty for a committed change and non-empty for the same
  change uncommitted, and the gate measures the second while the run writes the diff from the first.
- **A digest of the diff file's own bytes.** Eliminated: the gate does not know which file a round will be handed (it
  names no project path, D16), and in the flow where the worker runs the gate the diff does not exist yet — a gate
  that could only stamp trees measured after the diff was written would refuse the tree it is supposed to attest.
- **Two derivations that agree by convention** (the gate's over every path, the round's over the diff's paths).
  Eliminated by this change's own round-1 review, which measured the consequence: the gate's evidence lands in the
  change directory between the two moments, so the ordinary flow reads as a corrected one and item 3 refuses every
  change the loop gates. One helper, one pathspec, and the case that compares the two moments is what keeps it one.
- **Repairing the 2026-10-22 archive instead of the record.** Not open to this change: an archived record SHALL NOT be
  inspected or rewritten (`openspec/specs/documentation-ownership/spec.md:735`, "An archived record SHALL NOT be
  inspected or rewritten, because it is history rather than a defect in the tree"). The archive stays as the dated
  record of what was claimed, and this change makes the next such claim checkable.

## Rollback

Revert the commit. The identity is a field in a line and a file beside a diff, so nothing reads it that has to be
migrated: a record written before this change has two fields, and the gate's refusal line prints
`tree: unrecorded` for it rather than failing on the missing field.

## Open decisions

None. The item's own first step — whether the archive's claim is repaired by re-running the gate over the corrected
tree or by deleting the sentence — is answered by the rule above rather than left to a reader: neither, because an
archived record is not rewritten.
