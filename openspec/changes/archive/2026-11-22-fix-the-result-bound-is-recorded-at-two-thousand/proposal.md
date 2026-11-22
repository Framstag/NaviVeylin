# Proposal — the result bound is the number the tree records, and the number is 2 000

## Why

**The condition, verified on the base.** `tools/loop/loop.conf:493` records
`LOOP_TOOL_RESULT_MAX_CHARS=4000` on base `3a39d92a77eb23b4ab58f75246136c4db3d7d4f7`, and that one line is the whole
of the project's decision: every reader in the
tree takes the number from there rather than holding a copy — the adapter exports it into every child
(`tools/pi-child-events.sh:88-90`), the trimmer applies it (`tools/pi-trim-result.mjs:415`, `:441`), and
`tools/look.sh:94-96`, `tools/loop/phase.sh:81`, `tools/gate-attribute.sh:66` and `tools/loop/state.sh:282` read
the same key. Nothing is broken: the bound is applied where a result passes and it is the number the file records.
What is wrong is the number, and it is wrong in the direction the mechanism cannot correct for.

**Root cause, one sentence.** The bound is a rule about the tail, and the tail is the volume — the median result in
the archive this key is measured on is under a tenth of the bound, so the number only ever decides *how much of the
long tail* a child is handed, and 4 000 leaves more of it than a diagnosis needs.

**Evidence.** The measurement is the item's own model over `.pi/issue-loop/items`, taken with the reader
`tools/loop-spend.py` uses; it is quoted verbatim at `evidence/issue-1004-model.md` and **not re-taken here** — this
change claims the file's number, not a fresh model, so no figure of that model is restated as a measurement of this
change's. What the tree states for itself is the key, its readers and the class that does **not** move with it:
`tools/pi-trim-result.mjs:130` holds gate/evidence output at `GATE_EVIDENCE_BOUND` (8 000) whatever this key
records, so the log a red run is attributed from keeps its 8 000 and the ordinary `ctx_shell`/`ctx_read` result is
what this line moves.

**The red test.** `trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut` (new,
`tools/loop/tests/loop-cases.sh`): a 3 003-character result is handed to the trimmer against the bound the file
records, and must arrive cut and named. On this run's base `3a39d92a77eb23b4ab58f75246136c4db3d7d4f7` the recorded bound is
4 000, so the result arrives whole: `FAIL trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut` — "nothing past the
recorded bound reaches the child's context: 'CUT-AWAY' should not appear in …" — `tests=4 failures=1 errors=0`,
2026-11-22T07:19:29Z (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`). The run set the recorded value back to
4 000 for its own length and restored 2 000 after it, so what it measures is the new case and the rewritten arm —
the delta — and not a re-typed base tree. The three shipped `trim#*` cases pass on the same run, so the red is the new arm and
not a case the fixture broke.

**The owning requirement.** `openspec/specs/agent-issue-loop/spec.md:3020`, "A child's tool results are bounded and
a trim is named". The requirement names the key and never a value, so nothing in it forces this number; the delta
adds the one scenario the new case names — a result between the recorded bound and the one it replaced arrives cut —
and drops the "nine results in ten" clause of of the at-or-under scenario, which was measured against the 8 000 the
bound shipped with and is false at any tighter number.

**The owning guideline section.** `guidelines/Backlog.recipes.md` section 7a "Running one item: the script, the child and
the package": `:165-181` is the paragraph that states the rule for a reader of the loop, and it stated the bound as
8 000 twice — a number the tree stopped recording at 4 000 (#815) and stops recording at 2 000 here. That paragraph
is where the edit is owed. Section 7b "What an item costs, and the bounds on it" owns the *other* bounds (the item's
context and turns, the gate's pool, the reviewer's input) and states no result bound, so it is untouched.

## What Changes

- `tools/loop/loop.conf:493` — `4000` → `2000` (the line stands at `:505` after this change's own comment
  paragraph), and the key's comment gains the paragraph #1004 owes it: why the
  value is 2 000 rather than 1 000 (the JUnit line and the stack frame `guidelines/BuildEvidence.md` §4d attributes
  a red run with), and that the gate/evidence class keeps its own 8 000, so a red log is not what this line moves.
- `tools/loop/tests/loop-cases.sh` — one case added (`trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut`, in the
  case list and in the dispatch), and `trim#aResultUnderTheBoundIsHandedOverWhole` rewritten to read the recorded
  bound from `tools/loop/loop.conf` instead of passing a literal 8 000, so the arm that must stay whole is
  measured against the number the tree stands on. Its `# files:` declaration gains `tools/loop/loop.conf`, which is
  what makes the gate's `--from-git` selection owe it for this delta.
- `tools/pi-trim-result.mjs:134-139` — the `VERIFICATION_READ_BOUND` doc comment calls 2 000 "a quarter of the
  recorded result bound"; at a recorded bound of 2 000 it is the same number, so the comment says that instead of a
  ratio that is no longer true.
- `guidelines/Backlog.recipes.md:165-181` — the two 8 000s become the recorded number, and the `9.0 %` / `14 %`
  figures are stated as the measurement of the 8 000 the bound shipped with rather than as a claim about today's.
- `openspec/changes/fix-the-result-bound-is-recorded-at-two-thousand/specs/agent-issue-loop/spec.md` — one `MODIFIED`
  requirement: the five scenarios of "A child's tool results are bounded and a trim is named", one of them new.

Surface, with the command that counted it: `./tools/look.sh 'LOOP_TOOL_RESULT_MAX_CHARS' --path tools` → `66 matches
in 14 files`; of those, the arms that read the *recorded* value rather than naming their own are two in the trim group
(one added, one rewritten here) and six elsewhere that already read it —
`state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst` (`tools/loop/tests/loop-cases.sh:2299`),
`adapter#theChildIsStartedWithTheResultBound` (`:13950`), `look#aResultOverTheBoundIsCutAndNamed` (`:20410`),
`gate#theBlockStaysInsideTheRecordedBound` (`:20731`), `phase#aResultOverTheBoundIsCutAndNamed` (`:20951`) and
`phase#theIdentitySurvivesTheBoundOnAnyRoot` (`:21016`) — which is why the number can move in one line and why
these are the cases this delta's focused runs are owed for.

## Out of scope

- Re-taking the item's model. The 2 000 is the item's recorded measurement; a second model would be a second number
  for one decision, and `tools/loop-spend.py:293` still sweeps the caps it swept before (`4000`, `8000`, `16000`) —
  adding a 2 000 row to that sweep is a change to the reader, not to the bound, and no case here needs it.
- The gate/evidence class. `GATE_EVIDENCE_BOUND` stays 8 000: `guidelines/BuildEvidence.md` §4d reads a gate log
  and a suite XML whole, and a bound about the ordinary result must not tighten it.
