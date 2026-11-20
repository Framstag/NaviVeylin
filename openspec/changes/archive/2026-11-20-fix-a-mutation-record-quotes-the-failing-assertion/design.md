# Design — the line a mutation record quotes

## The root cause

`case_reason` (`tools/loop/tests/loop-mutations.sh:1721`; `:1710` on the base) read the case's log with
`sed -n 's/^[^|]*|  *//p' "$case_log" | head -1 | cut -c1-240`. The suite prefixes every line a case prints with
`<case>| ` (`tools/loop/tests/loop-selftest.sh:723`), so that filter matches the case's **first** output line, which
is its fixture's — for record `29.8`, `Bereite Arbeitsverzeichnis vor (neuer Branch 'loop-test')`
(`evidence/red-on-base-29.8.log`). The message the case failed on is the **last** thing it printed: a case signals
failure by calling `fail` (`tools/loop/tests/loop-cases.sh:1322`), which prints its message indented by two spaces and
exits at once.

The log's order is what makes `head -1` look plausible and the item's own fix line wrong. `finish_case` prints
`--- <case>[<slot>] (exit <status>)` and then repeats everything the case printed
(`tools/loop/tests/loop-selftest.sh:803`), and only after that does the pool print `FAIL <case>` (`:1005`); the line
following the last `FAIL` is the suite's summary. So the assertion is neither the first prefixed line nor anything
after `FAIL` — it is inside the text the case printed, on the line `fail` wrote.

Record `29.8`'s log, kept whole as `evidence/red-on-base-29.8-case.log` (13 lines), is the shape the reading has to
survive: three fixture lines, then line 4 `  the merge was refused on a build directory: …`, then line 5
`?? architecture/bin/Thing.class`, then the dump, the `FAIL` line and the tally.

## The single fix

`case_reason` walks the log once and keeps the first line of the last **run** of two-space indented prefixed lines,
strips the suite's prefix and that indent, and prints it with the log line it came from:

```
2026-11-20T13:27:05Z — case log line 4: the merge was refused on a build directory: loop: the project tree is not
clean — refusing to merge onto the base:
```

- a **run** and not the last indented line: `fail` may print a multi-line message (`fail "…: $out"`), whose
  continuation lines are not indented, so the run's first line is the assertion itself;
- the **last** run and not the first: a fixture may print indented output of its own before the case fails;
- a prefixed line with a single space ends a run and is remembered as the case's last printed line, so a case that
  printed no indented line at all still has an answer (`case log line <n>, the last line the case printed: …`);
- the log line beside the timestamp is the item's own ask, and it is what lets a reader find the quoted text in the
  archived log instead of re-running the record.

The cut at 240 characters stays, and so does the caller's line
(`tools/loop/tests/loop-mutations.sh:1787`): one reason, one timestamp, one index.

## Blast radius

Two live test files and one skill. `tools/loop/tests/loop-mutations.sh` — one function and its comment; nothing else
reads `case_reason` (`tools/look.sh 'case_reason' --path tools` → 2 occurrences, the definition and its one call).
The `RED` line's shape is read in two places (`tools/look.sh 'RED    ' --path tools/loop/tests` →
`loop-mutations.sh:1786`, `loop-mutations-selftest.sh:88`), and the harness's summary line
(`red-when-mutated: …`) is unchanged, so the gate's reading of the harness is untouched.
`tools/loop/tests/loop-mutations-selftest.sh` — one leg after the existing `--only 9.8` leg, driving `--only 29.8` on
the same engine copy, plus the file's own header and summary line naming it. `.pi/skills/revert-check/SKILL.md` — one
paragraph in §"When the gate is a shell self-test, not Gradle". Neither test file is an engine script `tools/loop/layout.tsv`
holds a bound for (that table covers the scripts, not their tests) and no shell name is introduced, so
`tools/loop/shared.tsv` is unchanged.

## Rollback

Revert the three files: no state, no fixture and no record of the declared set moves. The declared set stays at the
430 records `--only 29.8` reports (`selected 1 of 430 records`), because the fix adds no mutation record: a record can
only name a `loop-selftest.sh` case, and no such case reads the harness's own `RED` line. The falsification is the
self-test leg, which is the harness's own form for its own guards
(`.pi/skills/revert-check/SKILL.md` §"When the gate is a shell self-test, not Gradle").

## The reading set the change's paths derive

`tools/check-rule-routes.sh --diff 6f32fdb005aef825bd10d821f0e5bd27ec98f7b8 --change
openspec/changes/fix-a-mutation-record-quotes-the-failing-assertion` derives four sections from the three live paths
this change touches (`evidence/rule-routes.log`), and the change names each:

- `Backlog.md` §1 "Status, and what this document owns" — the item is #964 and its record is what the tracker holds;
  no edit owed, the change adds no tracker field and no status rule.
- `Backlog.md` §4 "The invariants" — the record is evidence for an invariant the loop already states; no edit owed,
  because the requirement this delta adds is the record's own text and not a new invariant of the tracker.
- `BacklogTooling.md` §9 "Checks" — the checks `tools/loop/**` owes; the harness's own self-test is the check this
  delta extends, and the section's description of it stays true, so it owes no edit.
- `BuildGate.md` §2a "Agent iteration protocol" — look then measure before changing code, which is what Phase A did:
  record `29.8` was run on the base, and its transcript is `evidence/red-on-base-29.8.log`.

## The alternatives the evidence eliminated

- **"The line that follows the last `FAIL <name>`"** (the item's own wording): eliminated by the log itself — the dump
  precedes `FAIL` (`evidence/red-on-base-29.8-case.log`, lines 6–13: the dump, its repeated text, `FAIL <case>`, then
  `loop-selftest: 1 of 1 cases failed`). Taking that literally records the suite's summary.
- **The last prefixed line**: eliminated by record `29.8` itself — line 5 is `?? architecture/bin/Thing.class`, the
  refusal's payload and not the assertion.
- **Stripping the indent and keeping the first match** (the shape an older hand-run red script used,
  `grep -m1 -F "$c|"`): the same first-line defect under another name, and nothing asserts it now.
- **A `--probe` seam in the harness for the case's log**: eliminated because the self-test can drive the real record
  (`--only 29.8 --engine "$copy_dir"`) for the cost of its two runs, which needs no new seam.

## NOTE — one branch the delta does not assert with a case

A case that prints no indented line (killed at its bound, or exiting non-zero without `fail`) is answered with the
last line it printed, the reason saying so (`case log line <n>, the last line the case printed: …`) rather than the
empty reason the old reading gave for such a log. It is probed, not cased: `evidence/case-reason-probe.log` runs the
harness's own `awk` — extracted from `case_reason`, so the probe runs the file's text — over a log of that shape and
over record `29.8`'s real log, and prints nothing for the same probe over the earlier round's `awk`, which is the
fallback's original defect. A real case for it would hold the harness's bound arithmetic rather than this rule.
