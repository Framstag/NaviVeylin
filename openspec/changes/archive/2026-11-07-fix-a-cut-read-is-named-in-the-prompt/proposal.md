# A child that reads a diff is told its read can arrive cut

## Why

`tools/loop/prompts/reviewer.md:24` hands the reviewer its diff as one path — "the diff under review: `{{DIFF_PATH}}`"
— and says nothing about a result that arrives cut. The worker's prompt tells its child to cite a file it has already
read rather than read it again (`tools/loop/prompts/worker.md:106`) and likewise says nothing about a result that is
not the whole file. Every child reads through tools that do bound the result, twice over:

- `tools/pi-trim-result.mjs:55` cuts every tool result above `LOOP_TOOL_RESULT_MAX_CHARS` (`tools/loop/loop.conf:372`,
  `8000`) and appends `[LOOP-TRIMMED: … read the rest in ranges (ctx_read mode="lines:N-M", or sed -n 'N,Mp')]`, and the
  trim is recorded beside the child's event stream;
- the read tool stops at its own cap, ~2 000 lines or 50 kB per result
  (`docs/2026-11-07-reviewer-model-analysis.md:47`, measured by the probe that paged an 891 687 B diff), and marks the
  cut the same way.

No prompt named either: on the base, `./tools/look.sh 'LOOP-TRIMMED' --files` read 6 matches in 4 files — the
trimmer, the cases that drive it, and an archived design with its hook probe — and no file under
`tools/loop/prompts/`.

**Root cause, in one sentence:** the result bound is applied and asserted where a result passes
(`tools/pi-trim-result.mjs`, `trim#aResultAboveTheBoundIsTrimmedAndNamed`) and never named where a child is told what
it is given, so a review round can grade a diff it holds the first 8 000 characters of and report `PASS` with a
timestamp the gate cannot tell from one written over the whole file.

**The red case, on the base `a3cefa93`** — `tools/loop-cases.sh`, one
`bash tools/loop-selftest.sh --case 'prompt#thePhasePromptsCarryTheirOwnRoute'` run, 2026-11-07T12:32:32Z–12:32:34Z:

```
prompt#thePhasePromptsCarryTheirOwnRoute|   worker.md names the marker a cut result carries (issue #818): 'LOOP-TRIMMED' not found in: # Worker — one item, phases A–D
FAIL prompt#thePhasePromptsCarryTheirOwnRoute
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.xml` is the tally of that run (`tests=1 failures=1 errors=0 time=2`), transcribed into a
`testsuite` from the runner's own summary — the loop's shell runner writes no JUnit XML — and kept beside
`evidence/red-on-HEAD.log`, whose own lines are the failure and the `1 of 1 cases failed` summary.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md` — added, "A prompt whose child reads a long text
names the cap that cuts it". Its shape follows the neighbouring requirement "The phase prompt carries the route the
child needs" (`:2719`), whose scenario is the one this case already answers.

**Owning guideline section:** `guidelines/Backlog.recipes.md` section 7a "Running one item: the script, the child and the
package" — the paragraph "What it therefore re-reads of its own work, and the one bound on it" (`:154`) owns the
result bound; it states that the rule is applied where a result passes and that "the prompt has asked for that since
#246", and owes the sentence that a prompt now names the cut as well.

surface: 4 phase prompts carry the paragraph (`tools/loop/prompts/reviewer.md:24`, `worker.md:106`,
`worker-gate.md`, `worker-tail.md`), 1 existing case extended (`prompt#thePhasePromptsCarryTheirOwnRoute`, whose
`# files:` declaration gains `tools/pi-trim-result.mjs`), 1 requirement added with 1 scenario, 1 guideline section
extended (section 7a), 1 mutation record added — counted with `bash tools/loop-selftest.sh --list | wc -l` (353 cases,
declared), `./tools/look.sh 'LOOP-TRIMMED' --files` (6 matches in 4 files, before the change) and
`./tools/look.sh 'ctx_read mode=' --files` (4 matches in 3 files: the cases, the refusal policy and the trimmer).

## What Changes

1. **`reviewer.md` states the rule where it hands the child its diff, and what its report owes.** The bound, the
   marker, the paging call and arguments, and "a window that arrived whole" as the only place an anchor may come
   from; a scenario the round never saw is `UNPROVEN`, never `PASS`.
2. **`worker.md`, `worker-gate.md` and `worker-tail.md` state the same rule at the place each is told what it is
   given**, so no phase child holds a window without a fact in its session saying so.
3. **`tools/loop-cases.sh` asserts it.** `prompt#thePhasePromptsCarryTheirOwnRoute` already renders the four
   skeletons; it gains four assertions per prompt inside the loop it runs.
4. **`guidelines/Backlog.recipes.md` section 7a names the rule and its case**, beside the bound it is about.

## What this does not do

- It does not move the cap, the bound key or the trimmer: `LOOP_TOOL_RESULT_MAX_CHARS` keeps its value and
  `tools/pi-trim-result.mjs` is unchanged. Whether the cap itself should move is #815's question.
- It does not make a review round page by itself: a round that ignores the sentence is a round that failed its own
  contract, and nothing here turns it into a refusal.
- It does not touch the run's budget, its bounds or the report.
