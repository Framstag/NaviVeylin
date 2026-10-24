# Review round 4 — the corrections to `2026-10-22-fix-gate-suite-map-covers-the-pipeline` (issue #242)

Reviewer: read-only pass. No file in the worktree was written, no gate or suite was run. Worktree
`.pi/worktrees/242`, branch `fix/issue-242-the-loop-s-gate-is-the-gradle-test-task`, range
`a06ef5ca..84ba96ad` (the corrections: `146d0cdf`, `4050b7a1`, `eea24047`, `ec01f57e`, `84ba96ad`).

## Finding 1 — the blocking finding (path-side roster) is resolved — PASS

The round-3 refusal (`evidence/review-3-refused.md`) demanded: narrow the claim to the command side, state the
path side as a named residual, file the gap. Done, and the claim is gone from the places it was made:

- `guidelines/Build.md:392–403` — "The pin closes the command side only, and two edges stay residual. Which
  *paths* a step reads is named by no workflow — `tools/check-portability.sh` reads `tools/loop/loop.conf`,
  `tools/tooling.json` and `tools/sensors/sensors.json`, none of which is mapped to a command of
  `Check portability` — so a delta touching one of them is not gated by that step."
- `openspec/specs/agent-issue-loop/spec.md:1931–1933` and `…/1949–1950`, and the archived delta
  (`specs/agent-issue-loop/spec.md:9–10`, `:27–28`) — now **verbatim identical** (commit `84ba96ad`), including
  the three named paths.
- `proposal.md:46–47`, `:76–78`; `design.md:80–85` ("stated as a residual rather than claimed … **#289**");
  `tasks.md:109–114`; `tools/loop/loop.conf:130–133`.
- The three paths are real: `tools/check-portability.sh:81,82,188` reads `tools/tooling.json`,
  `tools/sensors/sensors.json` and `LOOP_GATE_CMD` out of `tools/loop/loop.conf`.
- The residual is filed: `gh issue view 289` → OPEN, "The loop's gate map closes the command side only, so a path
  a check step reads but names is gated by no command of that step", naming the same three paths and citing the
  same seams. PASS.
- No sentence of the old "every pipeline-gated path maps to every step the pipeline runs for it" form survives;
  the string now appears only as the quoted refusal in `tasks.md:104`.

## Finding 2 — the command-side claim is true — PASS

Reproduced the pin's parse independently (same awk + `comm -13` as `tools/loop-cases.sh:2706–2724`):

- `Check …` steps of `.github/workflows/build.yml` run **31** distinct `bash`/`sh` commands.
- `LOOP_GATE_SUITES` at HEAD holds **58 pairs / 32 distinct commands**; `comm -13 map pipeline` is **empty**.
  The one extra map command is `tools/loop-mutations.sh --check`, which is not a pipeline command and is
  disclosed as such (`design.md:42–44`, "plus the `--check` record pass the map already carried").
- `evidence/round4-map-vs-pipeline.txt` reproduces byte-for-byte the same two lists and the same empty
  `comm -13`; the numbers in the change ("58 pairs", "naming all 31", "24 → 58 pairs",
  "10 → 21 uncovered", `proposal.md:39,49,65`, `design.md:14,42`, `evidence.md`'s 8/23 and 10/21) all check out
  against the tree (24 pairs / 10 commands at `a06ef5ca`, 58 / 32 at HEAD).

## Finding 3 — BLOCKING (P1): one surviving universal of the same family is still false

`guidelines/Build.md:394–395`, `tools/loop/loop.conf:161–163` and `design.md:42–43` still say, unqualified:

> "The map's unit is a pipeline *step*: a path maps to every `bash`/`sh` command the step whose tools read it
> runs — the check, its self-test and its mutation list …"

That is the round-3 claim in mirror (per path, full step coverage) and it is falsified by the map itself:

| step | commands the step runs | commands the step's own path carries |
|---|---|---|
| `Check issue tooling` (build.yml:182–207) | 6 — `tools/issue-tooling-selftest.sh` + five `tools/mutate.sh --mutations … --list` | `tools/issues/*` carries **1** (the self-test) |
| `Check agent issue loop` (build.yml:226–231) | 2 — `tools/loop-selftest.sh`, `tools/loop-mutations-selftest.sh` | `tools/loop/*` carries **1** |
| `Check portability` (build.yml:275–283) | 4 — `check-portability.sh`, `-selftest.sh`, `tools/mutate-selftest.sh`, portability `--list` | `tools/check-portability*` carries **3** (no `tools/mutate-selftest.sh`) |

It holds only for steps whose commands share one glob (`Check the build inputs`, `Check documentation routes`,
`Check Compose sandbox rule`, …), which is exactly the one step `gate#aPipelineCheckIsOwedByItsPaths` asserts
(`tools/loop-cases.sh:2730–2751`) — so the case cannot falsify the sentence, and the pin compares commands, not
paths. The appositive gloss ("the check, its self-test and its mutation list") is what the map actually does per
tool; the leading clause is not.

Smallest fix (wording only, three sites): replace "a path maps to every `bash`/`sh` command the step whose tools
read it runs" with the per-tool form, e.g. *"a path maps to that tool's triple inside the step — the check, its
self-test and its mutation list — and each other command a step runs is carried by the path of the tool it
belongs to (`tools/issues/*` owes the issue tooling's self-test; the five tracker sets owe their own
`tools/mutate.sh … --list`)"*. The map, the case and the spec's SHALL ("`LOOP_GATE_SUITES` SHALL carry every
`bash`/`sh` command that pipeline's `Check …` steps run") are all correct and need no change.

## Finding 4 — BLOCKING (P1): the round-3 "three free corrections" are recorded as applied and are not in the tree

`tasks.md:119–121`:

> "The three free corrections of round 3's report: `evidence.md`'s surface grep count, task 5's "the seven
> beside it" (six), and `gate-round3.log`'s missing exit marker, each replaced with what the artifact records"

None of the three is visible at HEAD:

- `evidence.md` carries no surface grep count at all (`grep -n surface` over every `*.md` in the change dir
  matches only `proposal.md:49` and `tasks.md:119`). The pointer names a number that is not in the file it
  names.
- `tasks.md:52` still reads "`evidence/tools_loop-selftest.sh.xml` and the **seven** beside it in that
  directory": `evidence/` holds **7** `*.xml` tallies in total, so the "six" the bullet records is the correct
  figure and the body was never changed.
- `evidence/gate-round3.log` carries no `exit=` marker (`grep -c 'exit=' → 0`; its last line is the
  `LOOP-GATE: tests=157 failures=0` summary). `gate-round4.log` has none either.
- The one surface count the change does carry is `proposal.md:49–51`, "→ 11 files", and its own quoted command
  (`grep -rl 'LOOP_GATE' … | grep -v archive | grep -v .pi/`) returns **12** at HEAD (and at `0c09b152`, i.e.
  before this round): `guidelines/Backlog.md`, `guidelines/Build.md`,
  `openspec/specs/agent-issue-loop/spec.md`, `PORTING.md`, `tools/check-portability.sh`,
  `tools/fixtures/foreign-project/tools/loop/loop.conf`, `tools/loop-cases.sh`, `tools/loop/gate.sh`,
  `tools/loop/loop.conf`, `tools/loop/measure.sh`, `tools/loop-mutations.sh`, `tools/loop/run.sh`. The prose
  beside it accounts for 9 of them.

Smallest fix: make the bullet say what is there — correct `tasks.md:52` to "the six beside it", drop or reword
the `evidence.md` pointer (the count lives in `proposal.md`, 11 → 12), and state that `gate-round3.log` carries
the summary line but no exit marker instead of claiming it was added. Adding an `exit=` line to a captured log by
hand would falsify evidence; if the marker is wanted, the gate has to be re-run (that is a supervisor decision,
not a reviewer edit).

## Finding 5 — P2: the round-4 baseline artifact does not identify the tree it was taken on

`evidence/round4-baseline-failure.log` is three lines with no header: no base sha, no command, no date, so
"fails identically on the base (`a06ef5ca` clean)" (`tasks.md:123–125`, `evidence.md` round-4 section) rests on
an unlabelled capture. The rest of the attribution is sound: `#287`/`#288` exist and name
`run#aWriterTheRunCanPlaceBlocksTheItem`; `gate-round4.log:386,762` carries that one failure out of 375 tests with
`exit 1` over eight commands, matching `tasks.md:122–126`. Fix: prepend the header line the other artifacts carry
(`== round 4 baseline, <time>, base a06ef5ca, git status clean`) on the next run.

## Finding 6 — record ids, pair counts and command counts introduced this round — PASS

- Mutation records `54.1–54.4` exist in `tools/loop-mutations.sh` with those labels, and
  `evidence/round4-mutations.log` shows `selected 4 of 132 records (labels: 54.1 54.2 54.3 54.4)`, four RED, four
  restored, `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0`, `exit=0`. `40.x` is indeed
  `run#aCorrectiveRoundIsHandedTheCorrections`, so the renumbering reason stated at `tasks.md:127–129` is true.
- `evidence/round4-green-cases.log` carries both cases `PASS` with `exit=0`; `tasks.md:130–131` cites it
  accurately.
- The 58 pairs / 31 commands / 24 → 58 / 10 → 21 numbers are true (Finding 2).

## Note (not graded)

`146d0cdf` is a merge commit whose message says "No content edits in this commit beyond the conflict
resolution", while the same commit adds `tasks.md` section 8, `design.md`'s residual paragraph and `loop.conf`'s
narrowing comment. The content is what the refusal asked for; only the commit message understates it.

## Verdict

The blocking finding of round 3 is resolved: the claim is narrowed to the command side, the three
`Check portability` paths are named as a residual in all six places the review named, the delta and the shipped
spec now agree, and the gap is filed as #289. The corrections did, however, keep one universal of the same
family ("a path maps to every command that step runs" — false for `Check issue tooling` 1/6,
`Check agent issue loop` 1/2, `Check portability` 3/4) and record three round-3 corrections that are not in the
tree. Both are one-line text fixes; no map, case, mutation or spec change is owed.

VERDICT: FAIL
