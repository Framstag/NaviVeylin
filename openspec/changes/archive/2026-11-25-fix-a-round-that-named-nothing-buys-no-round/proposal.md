# Proposal — a review round that named nothing buys no round after it

## Why

**Root cause.** The round that follows a refusal was started whatever that refusal wrote. `run.sh` read the report's
`LOOP-BLOCKING:` count to decide whether the round was a pass or a refusal, and then, on a refusal, went straight to
the worker round with the findings — so a report that refused the change and named nothing still bought a second
bound. The reviewer's turn bound, not the item, is what ended the archive's sessions: over the eight items landed
2026-11-24/25 (issue #1085's own measurement) **7 of 28 sessions end at exactly `LOOP_CHILD_TURNS_MAX_REVIEWER` = 110**,
and the reviewer role is **9 sessions / 49.1 Mtok, 26 % of that night's 90.6 Mtok** (5.46 Mtok a reviewer session,
against a worker's 6.63). The recorded note beside the key already said what those sessions were: "ended at
`LOOP_CHILD_TURNS_MAX_REVIEWER=110` turns with no refusal in any of the 110". A second round is started whatever round
1's report said, so the majority of items pay the bound once more than the verdict needs.

**Re-derived, for the archive as it stands** (`evidence/measure.log`): the window from 2026-11-24 is now **12 reviewer
sessions and 63.7 Mtok**, and **8 of those 12 end at exactly 110 turns**; the population the new cap is taken over is
the **65 reviewer sessions of 41 items** (2026-11-17 to 2026-11-25), of which **20 sit at exactly 110**.

**The red, on the base.** `run#aReviewThatNamedNothingTakesNoSecondRound`
(`tools/loop/tests/loop-cases.sh`) on the base `ae2373cb` with only that case file checked out over it:

```
run#aReviewThatNamedNothingTakesNoSecondRound|   a report that counted a finding and named none bought a second review round
--- run#aReviewThatNamedNothingTakesNoSecondRound[0] (exit 1)
  a report that counted a finding and named none bought a second review round
FAIL run#aReviewThatNamedNothingTakesNoSecondRound
loop-selftest: 1 of 1 cases failed
```

(`evidence/red-on-base.log`, its first block.)

## What changes

**(a) The field the round after a refusal is keyed on is the report's `BLOCKING:` lines, not its `LOOP-BLOCKING:`
count.** The count is the reviewer's claim about its own grading; the `BLOCKING:` line *is* the finding, and a finding
naming a path or a line is the one thing a worker pass can act on. `blocking_findings()` (`tools/loop/evidence.sh`) is
the reader, and the check sits **before** the corrective arm, so a corrections-only report that names nothing earns no
extra round either. A refusal that names a finding keeps its round 2 unchanged; a refusal that names none ends the
item as a refusal — `blocked` with `refused` as its mechanism, escalated with the reviewer's `LOOP-DECISION` — and
takes no round after it.

**(b) `LOOP_CHILD_TURNS_MAX_REVIEWER` is 81**, the measured **median** of the 37 of the archive's 65 reviewer sessions
that ended with a `VERDICT:` line (p25 74, median 81, p75 98 on the linear interpolation, max 110; the other 28 ended
without a verdict, and 20 of the 65 sit at exactly the old 110). It carried 110, a p95 of a sample censored at that
ceiling, which can only return the ceiling. The figure, its population and the command that re-derives it are in the
key's own comment; the pre-gate key keeps 110 and says why it does not inherit 81.

**(c) The parse reads the finding line whatever markdown wraps it — and this is the correction this landing
carries.** The reviewed reader was `grep -c '^BLOCKING:'`, and the archive writes the line with emphasis around it:
`1042/reviewer-report-1-restart.md` states `LOOP-BLOCKING: 1`, `LOOP-CORRECTIONS-ONLY: yes`, `VERDICT: FAIL`, and puts
its one finding on lines 19 and 118 as `**BLOCKING: the delta's scenario …**`; the round after it passed
(`1042/reviewer-report-2.md`: `LOOP-BLOCKING: 0` / `VERDICT: PASS`). Read by the reviewed pattern that report names
nothing, the guard breaks before the corrective arm, the item is refused, and the note on it says the round named
nothing although it named its finding twice — a round that in fact passed would never have been taken. That is the
direction this guard must not fail in: a silently skipped round is a lie about the run. `999/reviewer-report-2.md:38`
writes its finding the same way. So the reader is `grep -cE '^[[:space:]>*_]*BLOCKING:'` over `report_lines`, and the
guard, its placement and its note are unchanged.

The archive then says something narrower and true, which replaces the claim the reviewed change made about its own
evidence: **every `VERDICT: FAIL` report on record carries a `BLOCKING:` line** — 18 at line start, 2 wrapped in
emphasis, none with no finding at all (`evidence/measure.log`) — so the count a report states is not where the pointer
is, and the line is read in both shapes. The reviewed change had cited #1042's round 1 as a count written with no
line, and the round after it as one spent on nothing; both halves of that sentence were false.

## The two things that must stay true

- **A refusal that named nothing is still a refusal.** It is `blocked` with `refused` as its mechanism, escalated to a
  person with the reviewer's `LOOP-DECISION`, exactly as a refusal that named a finding is. What it does not get is a
  round after it. The case asserts the result, the mechanism and the escalation label for that shape.
- **The round not taken is written into the item's own text.** `refusal_named_nothing` reaches the item's note —
  "the round *N* refused the change and named no finding … so the run takes no round after it: a person reads the
  report and decides" — because a round skipped in silence is a lie about the run. The case asserts both halves of
  that sentence on the item's own note.

## Spec delta: none, and why

The shipped requirement's text and its new scenario were edited **by the fix commit itself**, inside
`openspec/specs/agent-issue-loop/spec.md`, and not as a change-relative `specs/` delta. Recording the same sentences a
second time as a `MODIFIED`/`ADDED` block would merge nothing new at archive and risk a stale copy of text the spec
already carries, so this change carries no `specs/` directory, declares `skip_specs: true` in its `.openspec.yaml` —
the declaration without which `openspec validate --strict` refuses a change with no delta — and archives with
`--skip-specs`, the shape the two landings before it took
(`2026-11-25-fix-a-tail-range-read-of-the-tree-is-refused`, `2026-11-24-fix-the-reds-the-main-carried`).

## Capabilities

`agent-issue-loop` — the scenario "A blocking finding refuses the change as it always has", which now names the
finding the round is read on, the new scenario "A refusal that named no finding buys no round after it", and "A
report that states no count is read as a refusal", which now points at the new case; the cases
`run#aReviewThatNamedNothingTakesNoSecondRound` and `run#eachChildIsBoundedByItsOwnRolesTurns` exercise them. No other
capability is touched: the bound and the round rule are the loop tooling's own, and no product requirement reads them.

## Impact

Tooling only — no app, auto or core source, no native source, no manifest, no resource, no string, no Gradle input.
Nine files (`git show --stat 4d1dea06`): `tools/loop/evidence.sh` (the new reader), `tools/loop/run.sh` (the guard and
the note), `tools/loop/loop.conf` (81 and its derivation), `tools/loop/layout.tsv` and `tools/loop/shared.tsv` (the new
function declared), both test suites, `guidelines/Backlog.recipes.md`, and the shipped spec. 182 insertions, 35
deletions in the reviewed commit, plus this landing's correction to the parse, one case arm and four comment
sentences.

**What it does not claim.** It is not "review less": a round 1 that names a line keeps its round 2 unchanged. It claims
only that a round 1 which named nothing has no second round to pay for. Lowering the cap does not shorten the rounds
that reach a verdict — 81 is their median, so half of them are ended on it and pay one restart on the input
`LOOP_REVIEW_INPUT_MAX_BYTES` already bounded (`LOOP_REVIEW_RESTART_MAX`), which is cheaper than the whole round again.

**Rollback** is one `git revert`: deleting the guard restores the base's behaviour (a refusal always buys its round),
and `1085.1` — which removes the guard — reddens the new case and restores it green (`evidence/green.log`).

## Verification

The case is red on the base and green after (`evidence/red-on-base.log`, `evidence/green.log`); the bold-form arm of it
is red against the reviewed parse and green against the shipped one (the second block of the first, the first block
of the second). The focused selections are the eight cases this change moves, the mutation harness's own self-test and
record `1085.1` red-then-green, and `bash -n` over the four shell files. `driver#anOwnedFunctionIsInItsOwnersFile` is
red at `ae2373cb` **itself** with the same two rows (`report.sh defines action_add`, `state.sh defines
cmd_publish_refused`); this change adds no row to it. No build and no full suite: no compiled file is touched, so the
structural checks stand in (`guidelines/Build.md` §2, the scope block) and the parent gates the branch once.
