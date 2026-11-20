# A mutation record quotes the assertion the case failed on

`case_reason` (`tools/loop/tests/loop-mutations.sh:1721`; `:1710` on the base, before this change's comment) is the
one reading behind every `RED` line the mutation
harness prints: the reason a record carries beside its timestamp. It took `head -1` of the `<case>| ` lines the suite
streams, and the suite prefixes **every** line a case prints (`tools/loop/tests/loop-selftest.sh:723`,
`printf '%s| %s\n' "$name" "$line"`), not just the assertion — so the first match is whatever the case's own fixture
printed first.

For record `29.8` (`tools/loop/tests/loop-mutations.sh:288`), whose case is
`worktree#anIdeBuildDirectoryDoesNotBlockTheMerge`, that is a `git init` progress line: on the base the record quoted
`Bereite Arbeitsverzeichnis vor (neuer Branch 'loop-test')` (`evidence/red-on-base-29.8.log`, 2026-11-20T13:24:29Z)
while the case had failed on `the merge was refused on a build directory: loop: the project tree is not clean —
refusing to merge onto the base:` (`tools/loop/tests/loop-cases.sh:10554`). The same record quoted the same fixture
line in the archive of item #962
(`openspec/changes/archive/2026-11-18-a-path-no-child-wrote-is-not-a-write/evidence/mutation-29.8-red.txt`).

**The consequence:** the mutation record is the falsification a change archives, and a reason that names a fixture line
proves the case went red, not that it reddened where the mutation was aimed — the half of `revert-check`'s contract
that `.pi/skills/revert-check/SKILL.md:39` states as "the expected assertion, quoted".

**Root cause, in one sentence:** `case_reason` picked the *first* line carrying the suite's `<case>| ` prefix, which is
the case's first output line and not the message `fail` printed last, so a record's reason was a fixture line wherever
the case printed anything before it failed.

**The red test:** the self-test leg this change adds — `tools/loop/tests/loop-mutations-selftest.sh`, `a red record
quotes the assertion the case failed on` — is red on the base: the whole self-test ends
`1 of 39 cases failed` and the leg's own line carries the record's reason as
`2026-11-20T13:25:56Z — Bereite Arbeitsverzeichnis vor (neuer Branch 'loop-test')`
(`evidence/red-on-base-selftest.log`). A shell gate has no JUnit XML of its own (`.pi/skills/revert-check/SKILL.md`
§"When the gate is a shell self-test, not Gradle"), so the tally is that script's, not an XML's; the record's own run
is `evidence/red-on-base-29.8.log` and the case's log it was read from is `evidence/red-on-base-29.8-case.log`.

**The item's own fix line cannot be the assertion.** It asks for "the line that follows the last `FAIL <name>`", and
the suite writes that line *after* the case's text, not before it: `finish_case` prints
`--- <case>[<slot>] (exit <status>)` and then repeats everything the case printed
(`tools/loop/tests/loop-selftest.sh:803`), and the pool prints `FAIL <case>` after that (`:1005`). The line following
the last `FAIL` is the suite's summary (`loop-selftest: 1 of 1 cases failed`). What the item is after — the assertion
the case failed on — is the last thing the case printed, and a case signals failure by calling `fail`
(`tools/loop/tests/loop-cases.sh:1322`), which prints its message indented by two spaces and exits at once. The change
reads that line and records its index in the case's log beside the timestamp, which is what the item asks for as "that
line's index … so a reader can see the choice was made and not guessed".
<!-- the round-1 review of the earlier attempt at this item rejected the item's own wording on this evidence; the
     decision recorded on the item says not to re-open it. -->

**The owning requirement:** `agent-issue-loop`, and none of its requirements owns this rule yet: the ones that name
the harness decide what it selects and what it checks, not what it records. The delta ADDs one requirement, "A
mutation record quotes the assertion the case failed on", with one scenario, and the scenario names the one case.

**The owning guideline section:** `.pi/skills/revert-check/SKILL.md:39` (the contract's `Failure reason` row) and
§"When the gate is a shell self-test, not Gradle", which is where the rule is stated for `tools/loop/**`; the change
carries the paragraph that section owes, and no `guidelines/` document states the rule (that section is the one that
routes `tools/loop/**` mutations).

**Surface** (`tools/look.sh 'case_reason' --path tools`, `tools/look.sh 'RED    ' --path tools/loop/tests`):
2 occurrences in 1 file for the function (its definition and its one call, `loop-mutations.sh:1721` and `:1787`), and
2 sites that read a `RED` line (`loop-mutations.sh:1786` prints it, `loop-mutations-selftest.sh:88` asserts it) —
surface: 1 function rewritten, 2 test files edited, 1 leg added, 1 skill paragraph added.

## Why the fallback is one clause and not a case

A case that prints **no** indented line (killed at its bound, or exiting non-zero without `fail`) has no assertion to
quote; it is answered with the last line it printed, said to be that. The probe
(`evidence/case-reason-probe.log`) runs the harness's own `awk`, extracted from `case_reason`, over such a log and
over record `29.8`'s real log; the same probe over the earlier round's `awk` prints nothing, which is the finding that
round's review raised and this change answers. No case is added for that branch: the cheapest real one holds the
harness's bound arithmetic rather than this rule, and the branch is a fallback a reader can read.
