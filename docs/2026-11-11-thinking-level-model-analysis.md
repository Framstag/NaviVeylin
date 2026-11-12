# Thinking-level analysis — can a loop step think less?

**Goal.** Decide whether a loop step's thinking level can be lowered (`off` / `low` / `high` / `max`, the four
levels `ollama-cloud/deepseek-v4.1-flash` offers) without losing the judgement the step exists for, on the thesis
that a lower level costs fewer tokens. The step in question is the loop's Worker pass — the only child
`tools/loop/loop.conf` leaves on the default model (63 % of the archive's spend); the reviewer, the rework round
and the three mechanical children already run on cheaper models.

**Date.** 2026-11-11. Judged on three probes and twelve child processes; every run's own event stream carries its
usage, read by `tools/bench/thinking_probe.py`. Re-take with the recipe at the end; the probe specs, the answer
keys and the event streams are kept under `.pi/tmp/tp2/` (gitignored).

**Short answer: no.** The thesis holds per turn and fails per run. Thinking tokens are output tokens, and output
is 2–5 % of what a child spends; the other 95 % is the cached context, which the level changes *indirectly* — by
changing how the child reads. On the read-heavy probe the two lower-cost levels were the two most expensive runs,
and `off` was the only level that missed the recorded blocking defect. `high` (today's default) was the best or
tied-best quality on all three probes and 1.9–2.7× cheaper than `max`, which bought nothing. No `loop.conf` line
changes for the Worker; the level and its measurement are recorded there instead.

The one place a level was tested below the default and held every quality gate is the reviewer, which runs on
`minimax-m3.1-flash` — but its saving did not reproduce on a third item (phase 2 below): median −15 %, mean +1 %,
worst case +53 %. No level changes anywhere.

## What the lever is, exactly

`pi --thinking <level>` takes `off`, `minimal`, `low`, `medium`, `high`, `xhigh`, `max`, and clamps it to what the
model offers. Measured 2026-11-11 on `ollama-cloud/deepseek-v4.1-flash` by reading `thinkingLevel` off the
child's own `turn_end` records: there are **four** levels, and three of the seven names are aliases.

| asked | applied |
|---|---|
| `off` | `off` |
| `minimal` | `low` |
| `low` | `low` |
| `medium` | `high` |
| `high` (default) | `high` |
| `xhigh` | `max` |
| `max` | `max` |

A loop role takes it through the command it already carries — the engine's per-role indirection, no engine change:

```sh
LOOP_AGENT_CMD_WORKER="bash tools/pi-child-events.sh pi --thinking low"
```

The provider reports `reasoning: 0` and folds the thinking into `output` (`totalTokens = input + output +
cacheRead`), so a level's thinking cost is visible as the per-turn `output` figure in the tables below.

## The three probes

| probe | the step it stands for | answer key | graded |
|---|---|---|---|
| **R** | the reading/judging half: `.pi/issue-loop/items/116` reviewer round replayed from its own rendered prompt (`tools/bench/probe-spec.sh`), 891 687 B of diff in two parts | the round's own `reviewer-report-1.md` | `reviewer-model-benchmark`'s five gates |
| **W743** | the planning half: Worker phase A on a small, test-only item (`#743`), in a git worktree at that item's base commit `741603aa` | the archived change's design decisions D1–D3, the file it touched, the case names its delta spec carries | 7 mechanical checks (below) |
| **W87** | the planning half on a second item (`#87`), worktree at base `2e732f0e` | the archived change's "single fix" (attribution by stack), the capability, the case names | 7 mechanical checks (below) |

One session per level per probe, four levels in parallel, all four started from the project root through
`tools/pi-child-events.sh` so each writes its own event stream. Wall clocks are comparable **within** a probe only.

### W743's first set is discarded, and why it matters

The first W743 run let a child read anything under the item directory. `low` opened `worker-report.md` and two
`evidence/*-diagnostic.txt` files — the archived round's own products — and produced the best plan of the four.
That is a leak, not a capability, and it is the trap this kind of probe has: a child that reads the answer looks
brilliant at every level. The spec now forbids every file under the item directory except `work-package.md`, and
the table reports the four clean runs (`W743b`). The discarded set is kept for the record; its numbers are not
used. Check after any re-take: no run may read a path under `.pi/issue-loop/items/<n>/` other than the work
package, nor open an archived `openspec/changes/archive/…` directory of the change it is planning.

## Results — what each level spent

Rates: $0.30 per Mtok input, $1.20 per Mtok output, cache read billed as input (`https://ollama.com/pricing`,
2026-11-07; the provider's own `cost` field reports 0 on this plan and cannot be quoted).

### Probe R — reviewer replay, 891 687 B of diff

| level | turns | tool calls | input | cacheRead | output | total | max ctx | wall | cost | vs high |
|---|---|---|---|---|---|---|---|---|---|---|
| off | 44 | 49 | 50 797 | 1 807 488 | 11 208 | **1 869 493** | 68 302 | 161 s | $0.57 | −7.3 % |
| low | 39 | 141 | 287 507 | 5 050 112 | 35 599 | **5 373 218** | 329 712 | 260 s | $1.64 | **+166.4 %** |
| high | 48 | 47 | 50 394 | 1 940 096 | 26 351 | **2 016 841** | 83 219 | 289 s | $0.63 | — |
| max | 48 | 172 | 326 882 | 7 290 752 | 53 069 | **7 670 703** | 386 296 | 365 s | $2.35 | **+280.3 %** |

The mechanism is in the tool mix: `high` and `off` spent 20 and 34 `ctx_shell` calls (grep/sed over the diff) and
read 26 and 15 files; `low` and `max` paged the whole 891 kB into context instead — 129 and 155 `ctx_read` calls,
a 330 k / 386 k largest context. The level did not make the same reading plan cheaper; it produced a different,
far more expensive one. Output per turn does fall with the level (255 / 913 / 549 / 1 106 tokens), and output is
0.6–1.6 % of the bill here.

### Probe W743b — Worker phase A, test-only item

| level | turns | tool calls | total | cost | wall | vs high |
|---|---|---|---|---|---|---|
| off | 21 | 27 | 527 093 | $0.16 | 57 s | −44.5 % |
| low | 16 | 27 | **398 615** | $0.13 | 51 s | **−58.0 %** |
| high | 27 | 28 | 949 890 | $0.30 | 103 s | — |
| max | 50 | 58 | 1 959 522 | $0.61 | 173 s | +106.3 % |

### Probe W87 — Worker phase A, second item

| level | turns | tool calls | total | cost | wall | vs high |
|---|---|---|---|---|---|---|
| off | 20 | 22 | **465 757** | $0.14 | 47 s | **−61.5 %** |
| low | 32 | 32 | 902 159 | $0.28 | 108 s | −25.5 % |
| high | 31 | 46 | 1 210 366 | $0.38 | 122 s | — |
| max | 38 | 54 | 2 287 535 | $0.73 | 214 s | +89.0 % |

On the two small planning probes the level *is* the lever the thesis claims: `off` and `low` cut spend by 45–62 %
and 26–58 %. On the read-heavy probe the same levels cost 1.7× and 3.8× more. A Worker pass does both.

## Results — what each level got right

### Probe R (gates from `reviewer-model-benchmark/references/rubric.md`)

The recorded round found one blocking defect: `MapTreeDiscovery.installedOnly` drops a leftover staging directory
with no `DiagnosticsLog` line, contradicting the requirement the change ships, and the change's own doc claim that
the class is "not a filter". `VERDICT: FAIL`, `LOOP-BLOCKING: 1`, `LOOP-CORRECTIONS-ONLY: no`.

| level | 8 items + table + trailer | the recorded finding | verdict / count | anchors exact | corrections-only |
|---|---|---|---|---|---|
| off | ✓ | **✗** — two *other* findings raised as blocking (a `Design.md` §9 "four readers" universal, a `tasks.md` §4.2 phantom merge); the silent drop appears only as an UNPROVEN note | ✗ (FAIL / 2) | **7/7** | yes (key: no) |
| low | ✓ | ✓ with the sink and the assertion | ✓ (FAIL / 2 — its second blocking is the §9 finding) | 5/7 — `C3` quoted with four leading spaces, `C4` lost its `+` marker | no ✓ |
| high | ✓ | **✓** — sole blocking, exactly the recorded one | ✓ (FAIL / 1) | 6/7 — `C3` four spaces | no ✓ |
| max | ✓ | ✓ — sole blocking | ✓ (FAIL / 1) | **7/7** | no ✓ |

`off` is the level that fails the one gate this probe exists for: a reviewer that returns FAIL for two real but
different reasons is not a substitute for one that finds the defect the change has. It is honest, not idle — its
§9 finding is arguably one the recorded round missed — which is exactly why "found something" does not grade.

`low` keeps the decisive gate and loses care at the edges: two of seven byte-exact anchors, one of them by
dropping a diff marker, and a second blocking invented next to the real one.

### Probe W743b — plan against the archived change's three decisions

The archived change's design carries D1 "the case owns its clock read; the production check is left alone",
D2 "disarm the surface-created wait before lowering the bound", D3 "assert the retry's own observables, not a
snapshot taken after the request"; its delta spec adds the five case lines.

| level | test file | capability | ≥3 real case names | D1 | D2 | D3 | spec delta |
|---|---|---|---|---|---|---|---|
| off | ✓ | ✓ | ✗ (1) | ✗ — edits `armFrameWaitWatchdog`/`frameWaitIteration` (production) | ✗ | ✗ | ✗ — invents a requirement clause |
| low | ✓ | ✓ | ✗ (1) | ✓ — test-only, one call-counting clock installed before `requestRender()` | ✗ | ✗ | ✗ ("none") |
| high | ✓ | ✓ | ✓ | ✓ — test-only, clock queue installed before `renderRequest()` | ✗ | **✓** — `frameWaitRetries == 1` and `reports.single()` carries `retry 1/2` | ✓ — case line into the requirement's scenario, `MODIFIED` block noted |
| max | ✓ | ✓ | ✓ | ✓ — arms the wait already expired, nothing written after `requestRender()` | ✗ | ✗ — keeps both old assertions | ✓ (also edits `BuildTests.md` §6e) |

Every level missed D2, the decision the change recorded as the second race. `high` is the only one that reached
the change's own shape on the fix, its assertion and its delta.

### Probe W87 — plan against the archived change's "single fix"

The archived fix keeps counting main-dispatcher traffic and **attributes** each counted dispatch to a stack that
names `MapCanvasViewModel`, widens the sample to three six-tick windows, and adds a provoked foreign dispatch per
window as the control; the production code is untouched.

| level | test file | `gps-fix-quality` named | attribution by stack | provoked/foreign control | production untouched | N = 3 windows | spec delta |
|---|---|---|---|---|---|---|---|
| off | ✓ | ✓ | ✗ (quotes a first-dispatch stack only) | ✗ | ✓ | ✓ | ✓ (scenario tightened) |
| low | ✓ | ✓ | ✗ | ✓ (provokes the subject) | ✗ — extracts `fixQualityTickOnce` | ✓ | ✗ — invents a `unit-test-suite-runtime` requirement |
| high | ✓ | ✓ | **✓** | ✓ | ✗ — extracts `evaluateFixQualityTick` | ✓ | ✓ |
| max | ✓ | ✗ | ✓ | ✓ | ✗ — extracts `runFixQualityTicker` | ✓ | ✗ (only `unit-test-suite-runtime`) |

`high` is again the closest to the change that shipped, one check down (it extracts a production function the
archived change did not need). `max` over-engineers: a production seam plus a virtual-time rewrite. `low` reaches
the right instinct (stop sampling, provoke) without the attribution that made the case stable. `off` reaches
neither central check.

## Phase 2 — the reviewer on `minimax-m3.1-flash`'s four levels

The reviewer (19 % of the archive's spend, 646.3 Mtok over 298 sessions) is the other phase whose body is reading
and judging, and it already runs on the cheaper model. Its level inventory is not deepseek's: asking `minimax-m3.1-
flash` for `off` applies **`low`** — there is no off — and the four levels are `low`, `medium`, `high` (default),
`max`. Measured 2026-11-11 on the same probe R, on three items rather than one.

### Spend

| item | level | turns | total | cost | vs high | max ctx | wall |
|---|---|---|---|---|---|---|---|
| #116 (891 687 B) | low | 50 | 7 798 497 | $1.19 | +22.9 % | 370 368 | 279 s |
| #116 | **medium** | 61 | **5 414 425** | **$0.83** | **−14.7 %** | 243 201 | 243 s |
| #116 | high | 53 | 6 346 677 | $0.98 | — | 389 530 | 347 s |
| #116 | max | 190 | 38 735 007 | $5.85 | +510.3 % | 411 576 | 731 s |
| #108 (774 385 B) | low | 34 | 5 359 769 | $0.82 | −26.4 % | 305 674 | 173 s |
| #108 | **medium** | 42 | **4 828 493** | **$0.74** | **−33.7 %** | 245 305 | 219 s |
| #108 | high | 51 | 7 286 611 | $1.12 | — | 330 909 | 300 s |

| #42 (785 764 B) | low | 55 | 14 250 976 | $2.19 | +137.8 % | 785 565 | 753 s |
| #42 | **medium** | 61 | 9 151 911 | $1.39 | +52.7 % | 326 885 | 305 s |
| #42 | high | 41 | 5 992 301 | $0.92 | — | 350 316 | 363 s |

`max` is refused on the same grounds as on deepseek: six times the tokens of `high` for nothing.

Across the three items, against `high`: `medium` −14.7 %, −33.7 %, **+52.7 %** (median −14.7 %, mean +1.4 %); `low`
+22.9 %, −26.4 %, **+137.8 %** (median +22.9 %, mean +44.8 %); `max` +510 % on its one item. The sign is not stable:
the level that saved a third of one review cost half again on another, and the item with the most findings — three
blockings — is where it inverted. Turn counts say why: 41 turns at `high`, 61 at `medium`, 55 at `low` for the same
product, and a turn is a read.

### Quality

Keys: #116 → `FAIL` / 1 blocking / corrections-only `no`, the silent-drop finding; #108 → `FAIL` / 1 / `yes`, the
delta scenario "The build refuses an unrecorded deviation" named a case that asserts only the scan.

| item | level | 8 items + table + trailer | the keyed finding | verdict / count | anchors exact | corrections-only |
|---|---|---|---|---|---|---|
| #116 | low | **✗ — Part B absent** (no anchors, no read accounting) | ✓ | ✓ FAIL/1 | 0/7 | ✓ |
| #116 | medium | ✓ | ✓ | ✓ FAIL/1 | 6/7 (`C3` four spaces) | ✓ |
| #116 | high | ✓ | ✓ | FAIL/**2** (its second is the `Design.md` §9 rule) | 6/7 (`C3` six spaces) | ✓ |
| #116 | max | ✓ | ✓ | ✓ FAIL/1 | 6/7 (`C3` four spaces) | ✓ |
| #108 | low | ✓ | ✓ | ✓ FAIL/1 | 6/7 (`C3` answered with the wrong line) | ✓ |
| #108 | medium | ✓ (items as `**Item N**`, not `### N.`) | ✓ | ✓ FAIL/1 | 6/7 (`C3` four spaces) | ✓ |
| #108 | high | ✓ | ✓ | ✓ FAIL/1 | 6/7 (`C3` four spaces) | ✓ |
| #42 | low | ✓ | ✓ | ✓ FAIL/3 | 6/7 (`C3` six spaces) | ✓ |
| #42 | medium | ✓ | ✓ | ✓ FAIL/3 | 6/7 (`C3` six spaces) | ✓ |
| #42 | high | ✓ | ✓ | ✓ FAIL/3 | **4/7** — `C2` and `C4` lost their leading `+`, `C3` eight spaces | ✓ |

`C3` is the known markdown-fence artefact of the 2026-11-07 study — every level of both models mis-indents that one
line, and it does not separate them. **On quality nothing separates the levels**: every run recovered the keyed
finding(s) with their files; the verdict and the blocking count matched the recorded round on all three items at
`medium` and `low`, and on two of three at `high`, which added a second blocking on #116 and dropped two diff markers
on #42. `low` lost the shape gate once (#116 delivered Part A only) and mis-answered one anchor (#108).

### The reviewer's lever: no change, on the third item

The two-item result (15–34 % cheaper at `medium`, no gate lost) did **not** survive a third item: #42 cost 53 % more
at `medium` and 138 % more at `low`, which also spent 753 s against `high`'s 363 s. Over three items the level's
effect on spend is a wash with a long tail upward, and its effect on quality is nil. Under this study's own rule —
a change must buy something measured — `LOOP_AGENT_CMD_REVIEWER` keeps its default `high`. `max` is refused for six
times the tokens and no gate; `low` for the 138 % tail, the doubled wall clock and the dropped half-probe.

What phase 2 does establish is a fact worth having: the spending difference between levels is **the reading plan
the level induces**, not the thinking tokens it buys. The same reviewer, on the same spec, reads 1.3 M or 3.2 M
tokens of diff depending on how many windows it chooses — which is why the sign flips between items, and why no
per-role level is safe to adopt without the live rounds.

## Decision

1. **No `loop.conf` line changes the thinking level.** `high` is the measured default for the Worker, and it is
   the level that recovered the recorded blocking defect and the archived fix's mechanism on both plan probes.
2. **`off` is refused everywhere.** Cheapest on two probes, −7 % on the read-heavy third, and the only level that
   missed the one finding the reviewer probe is keyed on, and the only one that got both plans' mechanism wrong.
   A step that saves a third of its tokens by not seeing the defect costs a whole item.
3. **`max` is refused everywhere.** −0 % quality for +89 % to +280 % spend on every probe.
4. **`low` is not adopted, and is the only candidate worth a live experiment** — and only on a step that plans
   rather than reads. It was 58 % and 25 % cheaper on the two planning probes with one check lost on each, and
   166 % *dearer* on the reading probe. If it is ever tried, it goes on one class-keyed role
   (`LOOP_AGENT_CMD_WORKER_<CLASS>`), and the live rounds are the second measurement: `tools/loop-spend.py
   --json` per role against the recorded 84.5 k tokens per worker turn, and a `revert-check` on the first finding
   that round produces.
5. **The lever that is actually cheap is reading discipline, not thinking.** The read-heavy probe's spread
   (1.87 M to 7.67 M tokens) came from *how* the child read 891 kB — 155 paged reads against 34 greps — and not
   from its per-turn output. A child that thinks less reads worse, and the loop pays for it in cache re-reads.
6. **The reviewer keeps `high` too, on the third item.** Phase 2's two-item saving (−15 %, −34 %) inverted to +53 %
   on the third, with no quality difference either way: the level does not buy tokens, it perturbs the reading
   plan. The reviewer's own numbers therefore change nothing, and the phase's finding is item 5 again, measured on
   a second model.

## What this does not settle

* **One sample per cell, three probes.** Each cell is one child process; a level that loses one check here could
  win the next item. The direction of the *cost* result is not close (1.7×, 3.8×, 0.42×), the direction of the
  quality result is (two probes agree on `high` ≥ `max` > `low` > `off` for the mechanism checks).
* **No gate, no edits, no live rounds.** W743 and W87 measure the plan, not the run: no child wrote a change, ran
  a suite or answered a reviewer. The published evidence for a *worker* level remains the live rounds.
* **Probe R's second part loads the repaired diff** (`review-2.diff`), so a level that read it late can see the
  fix and describe it — the same contamination the 2026-11-07 study carried. It cuts against the keyed finding,
  not for it, and `off` still missed it.
* **The plan probes' keys are the archived change, not ground truth.** A plan that differs from the change that
  shipped may be better. D2 and D3 are the change's own record of two races it found in review; missing them is
  a defect the reviewer would have to buy back, which is the cost the grade stands for.
* **Level changes the tool mix, so the tool policy is in the measurement.** Every child ran with this tree's
  result-trimming extension and shell-refusal policy; `off` and `low` reached for `ctx_shell` more, and the
  refusal it returns is a turn spent. Another tree's policy could move these numbers.
* **One model, one provider, one day.** `deepseek-v4.1-flash` on `ollama-cloud`, its four clamped levels; a
  model that maps the levels differently may behave differently. The level inventory is per model, and not
  monotonically named: deepseek has `off/low/high/max`, minimax has `low/medium/high/max` (no off),
  `mimo-v2.6-flash` collapses six of the seven names onto `high` — it has `off` and `high` only.
* **The reviewer's three items are three samples, not a trend.** `medium` held every gate on all three and `low`
  failed one; none of the three is a live round, and the live rounds remain the second measurement the 2026-11-07
  study also required.

## Re-taking this

```sh
# the reviewer probe and its key (the skill's own generator)
tools/bench/probe-spec.sh .pi/issue-loop/items/116 .pi/tmp/tp2/R-spec.md .pi/tmp/tp2/R-key.md

# the two plan probes run in a worktree at the item's base commit, so the defect is still present
# (`<base>` is the `- the run's base commit:` line of that item's `worker-prompt.md`)
git worktree add --detach .pi/tmp/wt-743 741603aaab8efc1382ea5859cfec38a548459272
git worktree add --detach .pi/tmp/wt-87  2e732f0e00fa771d04569483ffe80bc2ca3fa974
```

The plan-probe spec is one page: it names `TREE = <worktree>`, `work-package.md` as the **only** file the child may
open under the item directory (naming the rest — `worker-report.md`, `evidence/`, `gate.log`, `review-*.diff`,
`*.xml` — as another round's product and off limits), asks for the worker's phase A and nothing else, and fixes the
output shape (`## PLAN` with `### Files`, `### Test`, `### Spec delta`, `### Risks`). The ban is the part a re-taker
must not drop: without it `low` opened the archived round's own report and won the probe (2026-11-11).

```sh
# four children per probe, one level each, from the project root
for lvl in off low high max; do
  LOOP_CHILD_EVENTS=.pi/tmp/tp2/R-$lvl.events.jsonl \
    bash tools/pi-child-events.sh pi --model ollama-cloud/deepseek-v4.1-flash --thinking $lvl \
    .pi/tmp/tp2/R-spec.md > .pi/tmp/tp2/R-$lvl.out 2>&1 &
done

# what each run spent and did: usage, the level the provider applied, tool mix, and the leak check
python3 tools/bench/thinking_probe.py runs  .pi/tmp/tp2/R-*.events.jsonl
python3 tools/bench/thinking_probe.py table \
  "R off  .pi/tmp/tp2/R-off.events.jsonl"  "R low  .pi/tmp/tp2/R-low.events.jsonl" \
  "R high .pi/tmp/tp2/R-high.events.jsonl" "R max  .pi/tmp/tp2/R-max.events.jsonl"
```

Grade R with the skill's rubric; grade a plan probe against the archived change directory's `design.md`,
`specs/**/spec.md` and the file list of `review-1.diff`, and read `runs`'s verdict line before believing any plan
result. Re-take when the loop's Worker moves model, when a level mapping changes, or when a live round's
`tools/loop-spend.py` disagrees with the 84.5 k tokens per worker turn.
