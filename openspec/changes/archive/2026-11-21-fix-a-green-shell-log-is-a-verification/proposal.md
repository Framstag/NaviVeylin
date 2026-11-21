# Proposal — a shell suite's green log is a verification record

## Why

**Root cause.** `carried_verification` (`tools/loop/worktree.sh:492`) reads a finished attempt's verification record
in one shape only: a `*.xml` under the archived change's `evidence/` whose testsuite ran at least one test
(`tests="[1-9]"`), failed none (`failures="0"`) and raised no error. Its second line returned before any other
record was read — `printf '%s\n' "$files" | grep -q '\.xml$' || return 0`, the base's
`tools/loop/worktree.sh:486`, which this change deletes — so a change whose evidence holds only a console log is not
read at all, and `branch_state_of` (`tools/loop/worktree.sh:569`) answers `archived-not-green` for it (`:579`).
`adopt-item` then refuses the branch with "the change … carries no green verification record"
(`tools/loop/worktree.sh:711`), and the finished work stays on `fix/issue-N-…` for a person.

**The class this closes.** A `tools/**` item's gate is the shell suites, and its green proof is the suite's console
log: `PASS <group>#<case>`, the exit status, no XML. `tools/loop/gate.sh` derives each step's JUnit tally *from that
summary and the failing cases the suite named* (`tools/loop/gate.sh:24-26`), so the `PASS` line is the same
measurement in the shape a shell-only gate leaves it.

**Evidence, verified against the tree (issue #997).** Measured 2026-11-21 while landing the previous run's branches
at base `66d7618f`: three branches carry one archived change of their own, a green shell run and no XML, and
`bash tools/loop/worktree.sh branch-state <issue> <branch>` answered `archived-not-green` for each — `#948`
(`evidence/green-case.log`: `PASS gate#aStoppedStepIsNamedBeforeItsBound`, 2026-11-21T02:11:08Z, base `8e3861c4`),
`#959` (`evidence/green-on-change.log`: `PASS prompt#theTooWideRowCountsTheSurfaceBeforeTheVerdict`) and `#965`
(`evidence/green-on-change.log`: `PASS prompt#reviewerCarriesTheEightItems`, `exit=0`). The two branches of the same
run that carry XML (`#956`, `#979`) read `adoptable` — the item's measurement of that run, which this change does
not re-take — so the reading, not the branch, is what separates them. On this base the same logs are still read the
same way: `git show
origin/fix/issue-965-the-clause-that-exempts-a-refusal-s-loop:…/evidence/green-on-change.log` ends in
`PASS prompt#reviewerCarriesTheEightItems` and `exit=0`, and the function that reads it still requires an `.xml`.

**The red test.** `worktree#aShellSuiteLogIsAVerificationRecord` — a fixture branch carrying one archived change of
its own whose `evidence/green-case.log` holds one `PASS gate#…` line — asserts the branch reads `adoptable` and is
adopted. On the base commit `be56d860` it fails, reporting what the item reports:
`a branch whose green proof is the suite's own PASS log is adoptable: archived-not-green … expected 'adoptable', got
'archived-not-green'` (`evidence/red-on-base.log`, `tools/loop/tests/loop-selftest.sh --case
worktree#aShellSuiteLogIsAVerificationRecord`, `loop-selftest: 1 of 1 cases failed`, exit 1,
2026-11-21T18:54:44Z). The same case pins the other side: a log that names a failing case (`FAIL gate#…`) is the red
proof and stays `archived-not-green`.

**The requirement the item extends.** `openspec/specs/agent-issue-loop/spec.md:306`, "Version control belongs to the
run's script, not to the agent" — its scenarios at `:407-435` state that a branch is merged when the archived change
it adds "whose verification record is green", and that one "without a green verification" is not merged. Neither
states the record's *shape*, so this change adds the requirement the reading was missing a clause of.

surface: 1 case added, 0 rewritten, 18 fixtures left as they stand, 1 engine function changed — `tools/look.sh
'carried_verification|archived-not-green|green verification record' --path tools` (13 matches in 3
files: `tools/loop/worktree.sh` 8, `tools/loop/tests/loop-cases.sh` 4, `tools/loop/layout.tsv` 1) and
`tools/look.sh 'printf .<testsuite tests="2"' --path tools` (18 matches, one file). The 18 fixtures write the XML
stand-in and stay valid: the delta widens a reading, it retires no shape.

**The guideline.** `guidelines/Backlog.recipes.md`, section 7d "Refusals, records and the landing" (heading at
`:1225`), `:1488-1490`: "A branch
whose own evidence does not admit it — no archived change of its own, no green verification record, a reviewer's
refusal, a round announced and never answered — is the owner's". It names the condition and not the record's shape;
`tools/look.sh 'JUnit|testsuite|green verification' --path guidelines` (9 matches in 4 files) finds no section that
states the shape either, so the shape stays where it is implemented and commented, `carried_verification`'s own
comment, which this change rewrites. No guideline edit is owed.

## What changes

1. `carried_verification` reads two shapes: the JUnit testsuite as today, and the shell suite's console log — a file
   whose lines carry a `PASS <group>#<case>` verdict and no `FAIL` line. The early "no XML, then nothing" return goes.
2. One new case, `worktree#aShellSuiteLogIsAVerificationRecord`, in `tools/loop/tests/loop-cases.sh`.
3. One ADDED requirement in `openspec/specs/agent-issue-loop/spec.md` via this change's delta, naming the two shapes
   and the log's refusal.

Nothing else reads the record, so the branch-state vocabulary, the pre-screen's exclusions, the review-round reading
and `merge-item` are untouched.
