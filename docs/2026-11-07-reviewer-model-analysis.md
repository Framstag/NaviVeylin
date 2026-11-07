# Reviewer model analysis — can a cheaper model do the review?

**Goal.** Decide whether the loop's reviewer can move off `ollama-cloud/deepseek-v4.1-flash` ($0.30 in /
$1.20 out per Mtok) to a model at most half that price with a ≥ 256 K window, without losing the review's one
product: a defect found, pointed at, and quoted from the evidence.

**Date.** 2026-11-07. Re-take with the skill `reviewer-model-benchmark`; the recipe, the rubric and the probe
generator are that skill's.

## Why the reviewer and not the worker

The reviewer is the cheaper half of the loop to move. Over 227 archived items (`tools/loop-spend.py --json`,
2026-11-03) the reviewer's 7 522 turns carry 53.6 k tokens each and the worker's 25 654 carry 84.5 k. The
reviewer runs ~33 turns per item, so ~1.8 Mtok of **input** per review against a few thousand output tokens —
input price is the bill, and 53.6 k of context per turn is the window the reviewer must hold.

## The candidate set

Qualifying gates: at most half the baseline on input and output, ≥ 256 K context, and tool calling (a reviewer
that cannot call tools cannot read a diff). Prices from `https://ollama.com/pricing`, 2026-11-07.

| model | in / out $/M | ctx | verdict |
|---|---|---|---|
| deepseek-v4.1-flash (baseline) | 0.30 / 1.20 | 1 M | reference |
| minimax-m3.1-flash | 0.15 / 0.60 | 976 K | **selected** |
| glm-5.3-flash | 0.15 / 0.50 | 1 M | passed |
| mimo-v2.6-flash | 0.14 / 0.28 | 1 M | passed |
| beam | 0.125 / 0.40 | 1 M | failed |
| nemotron-3-super | 0.015 / 0.60 | 256 K | failed |
| nemotron-3-nano:30b | 0.06 / 0.24 | 256 K | failed |

Excluded before spending anything: `gpt-oss:120b` and `:20b` (128 K window), `gemma4` (no `tools` tag),
`nemotron-3-ultra` (out $3.00, above half), `mimo-v2.6-pro` and `mistral-large-3` (above half).

## The probe

`.pi/issue-loop/items/116` — the round that found a real blocking defect (`MapTreeDiscovery.installedOnly`
drops a leftover staging directory with no `DiagnosticsLog` line, contradicting the requirement the change
ships), over a 441 334 B / 6 639-line diff, which is the archive's p99 shape (p50 36.9 kB, p90 143.6 kB).

Two parts in one session, same prompt for every model:

- **Part A** — replay the round from `reviewer-prompt-1.md`, the prompt the loop really handed it, and produce
  the eight-item report. Graded against `reviewer-report-1.md`, the round's own recorded report, which no child
  was allowed to open.
- **Part B** — read `review-2.diff` whole in the same session (891 687 B of diff ≈ 223 k tokens) and answer six
  byte-exact anchors spread over both files. The read tool caps one result at ~2 000 lines / 50 kB, so the file
  must be paged — the condition a reviewer meets on any large diff.

## Results

| model | report | key finding | verdict | anchors C1/C3/C6 | fabricated | note |
|---|---|---|---|---|---|---|
| deepseek-v4.1-flash | 20.3 kB, 8 items | ✓ | FAIL / 1 | ✓ / ✗ / ✓ | none found | baseline; also caught the diff carries nine other items |
| **minimax-m3.1-flash** | 25.2 kB, 8 items, 6 UNPROVEN | ✓ with line refs | FAIL / 1 | ✓ / **✓** / ✓ | none | **only model byte-exact on the trailing line**, and found what the recorded round missed (below) |
| glm-5.3-flash | 14.6 kB, 8 items, 4 NOTEs | ✓ | FAIL / 1 | ✓ / ✗ / ✓ | none | its `totals.txt`, 733/5789 and `check-doc-routes 120/5` quotes were re-verified against the tree |
| mimo-v2.6-flash | 14.7 kB, 8 items, NOTEs + UNPROVEN | ✓ | FAIL / 1 | ✓ / ✗ / ✗ (92) | none | one counting slip; caught round-2 artifacts post-dating the round |
| beam | 3.3 kB, no table | ✗ | **PASS ✗** | ✓ / ✓ / ✓ | — | never reached the defect |
| nemotron-3-super | trailer only | ✗ | FAIL, wrong reason | not answered | — | raised an arguable unrelated-hunk complaint instead |
| nemotron-3-nano:30b | no table | ✗ | **PASS ✗** | ✗ / ✗ / ✓ | **yes** | invented falsification tallies and a "verified" `Design.md` §5 reference |

Anchors: `C1` = `@@ -341,6 +341,10 @@ jobs:` (line 5 of the round-1 diff), `C3` = the last line of the round-2 diff
(`     if [ "$review_verdict" = "FAIL" ]; then`, five leading spaces), `C6` = 91 `diff --git` lines in each.

Two facts the study produced that were not asked for:

- **`C3` is off by one or two spaces in every model, the baseline included** — a markdown-fence artefact of the
  output, not a capability difference. Not used to separate candidates.
- **minimax-m3.1-flash found a defect the recorded round missed.** The archive's round-2 `tasks.md` §5.6 claims
  "the run's gate re-run once over the corrected tree … `evidence/gate/` (replaced with the round-2 run's
  tallies and XMLs)", but every XML under `evidence/gate/` is stamped 12:23–12:26 and `totals.txt` still says
  the gate was "taken once", while round-2's own green and revert-check runs are stamped 12:42–12:43. The
  corrected `installedOnly` therefore never went through the gate the change quotes. Filed as an issue.

## Decision

`tools/loop/loop.conf`:

```sh
LOOP_AGENT_CMD_REVIEWER="bash tools/pi-child-events.sh pi --model ollama-cloud/minimax-m3.1-flash"
```

minimax-m3.1-flash: half the input price, a quarter of the output price, 976 K window, and the strongest record
in the set on the two things that matter — recovering the finding with pointers, and quoting what it read.
`mimo-v2.6-flash` is the cheaper alternate if the first live rounds show it holds up ($0.28 output); `glm-5.3-
flash` the second.

## What this does not settle

- **One item, one sample.** A reviewer model is not proven by one round. The first three live rounds are the
  second measurement: `python3 tools/loop-spend.py --json` per role against the recorded 53.6 k/turn, and a
  `revert-check` on the first finding the new reviewer raises.
- **No gate round was run** by any child. This measures reading, format and quoting — not how a model behaves
  across a budgeted item with a gate in it.
- **Wall clocks are not comparable.** The plan queues past three concurrent requests and every candidate ran in
  the same batch.
- **The price model assumes thinking tokens are output tokens**, which they are: at a 110-turn reviewer bound a
  thinking-heavy model's report is a small fraction of its output.

## Re-taking this

`.pi/skills/reviewer-model-benchmark` (with `tools/bench/probe-spec.sh`) regenerates the probe and its answer key
for any archived item, runs one child per candidate, and carries the grading rubric. Re-take when a new
ollama-cloud model qualifies, when the plan's prices move, or when live rounds disagree with this record.
