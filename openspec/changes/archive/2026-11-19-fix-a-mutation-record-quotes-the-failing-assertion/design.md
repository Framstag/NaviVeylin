# Design — the line a mutation record quotes

## The root cause

`case_reason` (`tools/loop/tests/loop-mutations.sh:1691`) read the case's log with
`sed -n 's/^[^|]*|  *//p' | head -1 | cut -c1-240`. The suite prefixes every line a case prints with `<case>| `
(`tools/loop/tests/loop-selftest.sh:723`), so that filter matches the case's **first** output line, which is its
fixture's — for record `29.8`, `Bereite Arbeitsverzeichnis vor (neuer Branch 'loop-test')`. The message the case
failed on is the **last** thing it printed: a case signals failure by calling `fail`
(`tools/loop/tests/loop-cases.sh:1310`), which prints its message indented by two spaces and exits at once.

The log's order is what makes `head -1` look plausible and the item's own fix line wrong. `finish_case` prints
`--- <case>[<slot>] (exit <status>)` and then repeats everything the case printed
(`tools/loop/tests/loop-selftest.sh:803`), and only after that does the pool print `FAIL <case>` (`:1005`); the line
following the last `FAIL` is the suite's summary. So the assertion is neither the first prefixed line nor anything
after `FAIL` — it is the tail of the text the dump repeats.

## The single fix

`case_reason` walks the log once and keeps the first line of the last **run** of two-space indented lines, strips the
suite's prefix and that indent, and prints it with the log line it came from:

```
2026-11-19T02:40:17Z — case log line 4: the merge was refused on a build directory: loop: the project tree is not
clean — refusing to merge onto the base:
```

- a **run** and not the last indented line: `fail` may print a multi-line message (`fail "…: $out"`), whose
  continuation lines are not indented, so the run's first line is the assertion itself;
- the **last** run and not the first: a fixture may print indented output of its own before the case fails;
- the log line beside the timestamp is the item's own ask, and it is what lets a reader find the quoted text in the
  archived log (`sed -n '4p'`) instead of re-running the record.

The cut at 240 characters stays, and so does the caller's line: one reason, one timestamp, one index.

## Blast radius

Two live files. `tools/loop/tests/loop-mutations.sh` — one function, its comment, and nothing else reads
`case_reason` (`tools/look.sh 'case_reason'` → 2 occurrences in that file, the definition and its one call). The
`RED` line's shape is parsed nowhere but the harness's own self-test
(`tools/look.sh 'RED    ' --path tools/loop/tests` → `loop-mutations.sh:1759`, `loop-mutations-selftest.sh:88`), and
the summary line (`red-when-mutated: …`) is unchanged, so the gate's reading of the harness is untouched.
`tools/loop/tests/loop-mutations-selftest.sh` — one leg added, after the existing `--only 9.8` leg, driving
`--only 29.8` on the same engine copy. Neither file is an engine script `tools/loop/layout.tsv` holds a bound for
(the table covers the scripts, not their tests), and no shell name is introduced, so `tools/loop/shared.tsv` is
unchanged. Then `.pi/skills/revert-check/SKILL.md`, one paragraph, and the spec delta.

## Rollback

`git checkout` of the three files: no state, no fixture and no record of the declared set moves, and the record set
(426) is unchanged — the fix adds no mutation record, because a record can only name a `loop-selftest.sh` case and no
such case reads the harness's own `RED` line. The falsification is the self-test leg, which is the harness's own
form for its own guards (`tools/loop/tests/loop-mutations-selftest.sh`'s probes, `.pi/skills/revert-check/SKILL.md`
§"When the gate is a shell self-test, not Gradle").

## The alternatives the evidence eliminated

- **"The line that follows the last `FAIL <name>`"** (the item's own wording): eliminated by the log itself — the
  dump precedes `FAIL` (`evidence/red-on-base-29.8-case.log`, last five lines: the assertion, its continuation, then
  `FAIL <case>`, then `loop-selftest: 1 of 1 cases failed`). Taking that literally records the suite's summary.
- **The last prefixed line**: eliminated by record `29.8` itself — the assertion's continuation line
  (`?? architecture/bin/Thing.class`) is the last one, and it is the refusal's payload, not the assertion.
- **Stripping the indent and keeping the first match** (the shape `openspec/changes/archive/2026-10-30-fix-worker-own-shell-write-is-staged-and-guarded/evidence/red-on-head.sh:56`
  used — `grep -m1 -F "$c|"`): the same first-line defect under another name; it was a hand-run red script, not the
  harness, and nothing asserts it now.
- **A probe of `case_reason` with a fabricated log** (`--probe …`): eliminated because a probe of the harness is what
  `tools/loop/tests/loop-mutations-selftest.sh` can drive directly — the `--only 29.8` leg reads a real log, of the
  real case, at the cost of one record's two runs, and needs no new seam in the harness.

## NOTE — one branch the delta does not assert

A case that prints **no** indented line (killed at its bound, or exiting non-zero without `fail`) is answered with
the last line it printed, the reason saying so (`case log line <n>, the last line the case printed: …`) rather than
the empty reason or the fixture line the old reading gave. No case is added for it: the cheapest such case is a
`--timeout 1` run of a real record, and it would hold the harness's own bound arithmetic rather than this rule. The
gap is stated here rather than left for a reader to find.
