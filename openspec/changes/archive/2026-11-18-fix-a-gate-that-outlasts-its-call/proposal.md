# Proposal — a gate that outlasts the call that started it

## Why

The gate phase prompt hands the command in one line and names no wall it must survive: `tools/loop/prompts/worker-gate.md:103`
carried `{{GATE_CMD}}` alone, and `tools/loop/prompts/gate-own.md` — the section `worker.md` substitutes for the
one-child shape — carried it the same way. A gate whose suites take longer than one **tool call** therefore ends with no
`LOOP-GATE:` line, and the run's own evidence rules read that as "no gate execution", which the round after the gate
refuses (issue #957).

Measured, run of 2026-11-17T10:58Z → 16:25Z:

- #911's second gate attempt ended at the child's own 1 500 s tool ceiling, `wrote no LOOP-GATE: line (nothing
  measured)`, and the round-2 review refused the diff for exactly that (`the tree this round reads has no gate
  execution of its own`); the item blocked with 7 commits unlanded and 29.4 Mtok spent.
- Gate clock per item in that run: #950 1 802 s, #894 2 694 s, #911 3 025 s — 49.4 / 76.8 / 58.6 % of those items'
  wall. Three of the run's fourteen items recorded no gate result at all.

The two bounds the profile records are both **above** the ceiling that ended the call: `LOOP_GATE_STEP_MAX_SECONDS`
(1 800 s, `tools/loop/loop.conf:399`) and `LOOP_ITEM_GATE_SECONDS` (3 900 s, `:284`). So the gate is not misconfigured —
the prompt asks for a shape the harness cannot deliver, and the child has no instruction that would let it deliver the
tally anyway. That gap is the defect the requirement "A gate step is bounded, and the step that overran is named"
(`openspec/specs/agent-issue-loop/spec.md`) already assumes is closed from the other side: a step is bounded by the
gate, and the gate itself was bounded by nothing the child could see.

A second defect sits under the first, and writing the fix found it: the renderer cannot carry an ampersand. `loop_render`
(`tools/loop/lib.sh:967`) substitutes one pair at a time with `${tpl//\{\{$key\}\}/$value}`, and bash 5.2 gave the
**replacement** a meaning of its own — a `&` stands for the text the pattern matched, `patsub_replacement`, on by default
in this host's bash 5.3.20. So the very command the fix has to hand a child — `cd x && nohup … 2>&1 &` — renders as
two copies of the placeholder and a redirection into one (`evidence/red-on-HEAD-render.log`). No prompt could give a
child a background start until that is fixed, and the same break reaches every value a run substitutes: the gate
command, a work package path, a finding a reviewer wrote.

## What Changes

- **`tools/loop/prompts/worker-gate.md`** — step 3 hands the gate as a **detached** start
  (`cd {{CHANGE_ROOT}} && nohup {{GATE_CMD}} > {{EVIDENCE_DIR}}/gate.log 2>&1 &`) and adds the wall paragraph: the
  tool-call ceiling and the profile's two longer clocks, why an inline run ends as a cut call with no tally, the poll
  (`sleep 600 && tail -n 40 {{EVIDENCE_DIR}}/gate.log`, a wait and a range read the child's shell allows), the two
  lines that end the gate (`LOOP-GATE:` / `LOOP-GATE-REFUSED:`), and the resume: poll again rather than start a second
  gate, whose refusal is `LOOP-GATE-REFUSED: another gate of this tree holds …` with exit `4`. The section's two
  numbered steps that both said `3.` are renumbered `4.` and `5.`, and `5.` becomes `6.`.
- **`tools/loop/prompts/gate-own.md`** — the same wall and the same detached start for the shape where the change child
  runs the gate itself (`worker.md`'s `{{GATE_STEP}}` section), so both skeletons a child can be handed its gate by
  state one rule.
- **One case** — `prompt#theGatePromptNamesTheWallAndTheWayThrough`, in `tools/loop/tests/loop-cases.sh`: both
  skeletons state the tool call and its ceiling, the detached start, the evidence-directory log, the poll, the allowed
  range read and the refusal a restart meets. Red on the base (`evidence/red-on-HEAD.log`), green with the prompts.
- **`tools/loop/lib.sh`** — `loop_render` turns bash's `patsub_replacement` off around its own loop and restores it, so a
  value arrives as the text it was handed and a section can carry a shell command.
- **A second case** — `prompt#aValueArrivesAsTheTextItWasHanded`: a value carrying `&&`, `2>&1` and a trailing `&`
  reaches the rendered text byte for byte. Red on the base (`evidence/red-on-HEAD-render.log`).
- **Scope**: `tools/loop/prompts/**`, `tools/loop/lib.sh` and one test file. No app code, no dependency, no build
  change, no run behaviour.
- **Not this change**: moving the gate into the run's own process, and raising or naming the harness's tool-call
  ceiling — both named in `design.md` under the alternatives this evidence eliminated.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop` — one requirement added: "The prompt that hands a child its gate names the wall and the way
  through it", with two scenarios. It is the prompt-side half of "A gate step is bounded, and the step that overran is
  named", which owns the gate's own bound.

## Impact

Code:

- `tools/loop/prompts/worker-gate.md` — step 3's command block and the wall paragraph; steps renumbered.
- `tools/loop/prompts/gate-own.md` — the command block and the wall paragraph.
- `tools/loop/lib.sh` — `loop_render`'s substitution made literal for one loop and the shell option restored after it.
- `tools/loop/tests/loop-cases.sh` — two case bodies, their entries in `LOOP_SHELL_CASES` and their lines in the
  dispatch table.

Guidelines and documents: none. `guidelines/Backlog.recipes.md:66`-`:68` and `:89` describe phase E's order (findings,
completion, archive, then the gate), which this change does not move; the wall is a fact of the prompt, and the
guideline names the prompt rather than restating its text.

Rollback: revert the commit. Two prose files, one four-line guard in the renderer and two cases; nothing at run time
reads the new sentences, and the guard restores the shell option it found.

surface: 2 prompt files edited (1 command block and 1 paragraph each, plus 3 renumbered step markers), `loop_render`
edited (5 lines), 2 cases added (0 rewritten), 0 other engine scripts — counted on the base `8b59ce84`, before this
change's files existed, with `tools/look.sh 'nohup'` (2 matches in 2 files), `tools/look.sh 'patsub_replacement'`
(2 matches: this change's guard and its case) and `tools/look.sh 'loop_render'` for the renderer's call sites.

## Evidence on the base

- Red on the base's prompts, the change's case: `evidence/red-on-HEAD.log` — `'tool call' not found in: # Gate child —
  phase E, the item's one gate` (2026-11-18T20:08:30Z), and `evidence/red-on-HEAD.xml` carries it as a JUnit tally.
- Green with the prompts: `evidence/focused-case.log`, `evidence/prompt-group.log` (the `prompt` group, 21 cases).
- The portability of the two skeletons, which the gate checks because a template is scanned for a project value:
  `evidence/structural-checks.log` (`tools/check-portability.sh` — "61 engines and 8 templates name no project value").
- Red on the base's renderer, the second case: `evidence/red-on-HEAD-render.log` — `expected 'start cd x && nohup
  ./gate.sh > gate.log 2>&1 & end /e/d', got 'start cd x {{CMD}}{{CMD}} nohup ./gate.sh > gate.log 2>{{CMD}}1
  {{CMD}} end /e/d'`.
- The first full gate over this change, which is what found the renderer defect:
  `evidence/gate-round-1.log` — `LOOP-GATE: tests=1157 failures=4 errors=0 … — failing: tools/loop/tests/loop-selftest.sh
  --from-git 8b59ce84`, with `LOOP-GATE-BASERED-UNMEASURED:` saying the base's red could not be measured for that step, so
  the red stayed with the item rather than being written off.
