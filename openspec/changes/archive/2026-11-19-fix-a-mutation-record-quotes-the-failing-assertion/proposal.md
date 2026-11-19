# A mutation record quotes the assertion the case failed on

`case_reason` (`tools/loop/tests/loop-mutations.sh:1691`) is the one reading behind every `RED` line the mutation
harness prints: the reason a record carries beside its timestamp. It takes `head -1` of the `<case>| ` lines the
suite streams, and the suite prefixes **every** line a case prints (`tools/loop/tests/loop-selftest.sh:723`,
`printf '%s| %s\n' "$name" "$line"`), not just the assertion — so the first match is whatever the case's own fixture
printed first.

For record `29.8`, whose case is `worktree#anIdeBuildDirectoryDoesNotBlockTheMerge`, that is a `git init` progress
line: the record quoted `Bereite Arbeitsverzeichnis vor (neuer Branch 'loop-test')`
(`evidence/red-on-base-29.8.log`, 2026-11-19T02:40:17Z) while the case had failed on
`the merge was refused on a build directory: loop: the project tree is not clean — refusing to merge onto the base:`
(`tools/loop/tests/loop-cases.sh:10443`). The same record quoted the same fixture line in the archive of item #962
(`openspec/changes/archive/2026-11-18-a-path-no-child-wrote-is-not-a-write/evidence/mutation-29.8-red.txt`).

**The consequence:** the mutation record is the falsification a change archives, and a reason that names a fixture
line proves the case went red, not that it reddened where the mutation was aimed — the half of `revert-check`'s
contract that "the expected assertion, quoted" (`.pi/skills/revert-check/SKILL.md:39`) exists for.

**Root cause, in one sentence:** `case_reason` picked the *first* line carrying the suite's `<case>| ` prefix, which
is the case's first output line and not the message `fail` printed last, so the record's reason was a fixture line
wherever a case printed anything before it failed.

**The red test:** `tools/loop/tests/loop-mutations.sh --only 29.8` on the base prints
`RED    29.8  worktree#anIdeBuildDirectoryDoesNotBlockTheMerge` followed by
`2026-11-19T02:40:17Z — Bereite Arbeitsverzeichnis vor (neuer Branch 'loop-test')`
(`evidence/red-on-base-29.8.log`), and the self-test leg that owns the record's reason
(`tools/loop/tests/loop-mutations-selftest.sh`, the `--only 29.8` leg this change adds) is red on that output because
it names neither the assertion nor the log line it was read from. The case's own log is
`evidence/red-on-base-29.8-case.log`, and the transcription of the run's own tally is
`evidence/red-on-base-29.8.xml` (`tests=1 failures=1 errors=0`, `2026-11-19T02:40:17Z`) — a shell gate has no JUnit
XML of its own (`.pi/skills/revert-check/SKILL.md` §"When the gate is a shell self-test, not Gradle").

**The item's own fix line cannot be the assertion.** It asks for "the line that follows the last `FAIL <name>`",
and the suite writes that line *after* the case's text, not before it: `finish_case` prints
`--- <case>[<slot>] (exit <status>)` and then repeats everything the case printed
(`tools/loop/tests/loop-selftest.sh:803`), and the pool prints `FAIL <case>` after the dump returns
(`:1005`). The line following the last `FAIL` is the suite's own summary (`loop-selftest: 1 of 1 cases failed`). What
the item is after — the assertion the case failed on — is the **last** thing the case printed, and a case signals
failure by calling `fail` (`tools/loop/tests/loop-cases.sh:1310`), which prints its message indented by two spaces
and exits at once. The change reads that line, and records its index in the case's log beside the timestamp, which
is what the item asks for as "that line's index … so a reader can see the choice was made and not guessed".

**The owning requirement:** `agent-issue-loop`, and none of its 168 requirements owns this rule yet: the two that
name the harness decide what it selects and what it checks, not what it records
(`openspec/specs/agent-issue-loop/spec.md:1769`, `:1929`). The delta ADDs one requirement, "A mutation record quotes
the assertion the case failed on", with one scenario.

**The owning guideline section:** `.pi/skills/revert-check/SKILL.md`, the Contract row "Failure reason" (`:39`) and
§"When the gate is a shell self-test, not Gradle" (`:44`-`60`), which is where the shell form of the record is
stated (`--list` prints the set, `--only` runs one, the transcript is quoted). No document under `guidelines/` states
the rule: `tools/look.sh 'assertion' --path guidelines` → `24 matches in 10 files`, none of them about a mutation
record's reason, and the three sections the touched paths derive (`guidelines/Backlog.md` §1 and §4,
`guidelines/BuildGate.md` §2a) own the tracker's semantics, its invariants and the iteration protocol, which routes a
shell gate to this skill and quotes no measurement.

surface: 8 matches in 5 files for `case_reason` before this change wrote its own three documents
(`tools/look.sh 'case_reason'` → 2 in the live `tools/loop/tests/loop-mutations.sh`, 6 in four archived reports and
tasks that quote the defect), and 17 in 8 with them in the tree; 2 matches in 2 files for the `RED` line's printer
and its one reader (`tools/look.sh 'RED    ' --path tools/loop/tests` →
`loop-mutations.sh:1759`, `loop-mutations-selftest.sh:88`); **1 function rewritten, 1 self-test leg added, 0 cases
added** (`LOOP-MEASURE-SUITE cases=491`, unchanged — the added leg is a check of
`tools/loop/tests/loop-mutations-selftest.sh`, which is not a case of `loop-selftest.sh --list`), and **0 mutation
records added** (426 declared, unchanged) because a record of the set can only name a `loop-selftest.sh` case, and no
such case reads the harness's own `RED` line.

## What Changes

1. **`case_reason` reads the case's last assertion.** The reason is the first line of the last run of two-space
   indented lines in the case's own text — the message `fail` printed last, stripped of the suite's prefix — and the
   line it was taken from is printed with it (`case log line 41: …`). A case that printed no assertion is answered
   with its last line and said to be that, not with the fixture line the `head -1` reading produced.
2. **The self-test leg holds it** (`tools/loop/tests/loop-mutations-selftest.sh`): the existing `--only 9.8` leg runs
   one record on a copy; the new leg runs `--only 29.8`, whose case prints three fixture lines before it fails, and
   asserts that the `RED` line's reason quotes that case's assertion and names a log line after the first.
3. **The skill states the record's shape** (`.pi/skills/revert-check/SKILL.md` §"When the gate is a shell self-test"):
   the `RED` line quotes the assertion and the line of the case's log it was read from, because the suite repeats the
   case's text *before* its `FAIL` line — the reading that made `head -1` look right.
