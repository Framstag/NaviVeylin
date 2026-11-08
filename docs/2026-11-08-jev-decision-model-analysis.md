# Jev decision-model analysis — can a System One model take a decision step of the loop?

**Goal.** Decide whether a *System One* decision model (TypeSafe's Jev: typed questions in, calibrated
probabilities out, no generated text) can take one of the loop's judgement steps — in particular the cheap
pre-filter in front of the expensive reader — and whether it is cheaper than the loop's current per-role models
(`tools/loop/loop.conf`, `LOOP_AGENT_CMD_*`) at the same or better quality.

**Date.** 2026-11-08. Judged on two probes and one control, 3 620 graded cases. Re-take with the recipe below
and `tools/bench/jev_probe.py`.

**Short answer: no.** Jev's cost and latency claims are true and its two decision shapes both carry no signal on
this loop's judgement calls. No `loop.conf` line changes. The one property worth keeping is that its `score`
primitive discriminates where its `noul` primitive does not.

## The candidate

| | |
|---|---|
| interface | `POST https://openrouter.ai/api/alpha/decisions` (or `POST https://api.typesafe.ai/v1/systemone`) |
| primitives | `noul` (P(yes)), `choice` (one of ≤255 options), `score` (2–10 ordered levels) — answers in parallel, mutually blind, and **no text output at all** |
| price | **$0.042 / M input tokens, output free** (against mimo-v2.6-flash's $0.14/$0.28 and minimax-m3.1-flash's $0.15/$0.60) |
| window | `jev-1.13` 32 k state + longest question; `jev-1.14` 262 k. Pinned here: `typesafe/jev-1.14-20261014` |
| not callable from `pi` | there is no `/v1/chat/completions` for it, so the loop's `LOOP_AGENT_CMD` indirection is the only way in and a Jev child is not a session — it has no transcript, so it cannot honour the `tools/pi-child-events.sh` `turn_end` contract |
| access | the account's guardrail is a **14-model allowlist** that excluded the whole `decisions` modality until `typesafe/jev-*` was added to it (`404 … "reason":"model-ignored-by-guardrail"`, identical for the free `parallel/decisions-fast:free`). An inference key cannot read or change it (`401 Invalid management key` on `/v1/guardrails`) |

Independent evaluation for reference: Deußer et al. (*Evaluating and Benchmarking the System One Model Jev*,
arXiv 2609.37647), jev-1.13 zero-shot on 37 datasets — routing 89.5 % on CLINC150 across 151 options, sentiment
and commonsense high, but a grounding check at 78.6 % mean balanced accuracy, **long-input close reading weak
(32–35 %)**, and sequential decisions its worst regime (ALFWorld 35 %, and it "often repeats actions that had no
effect"). Diff reading is exactly the regime the loop would need it for.

## Probe A — per-hunk: "does this file's diff break the claim?"

`tools/bench/jev_probe.py extract` over the loop's archive (`.pi/issue-loop/items`), answer key = the archived
round-1 reviewer report.

* **Cases: 2 552** — 400 positive, 1 771 hard negative (other hunks of the same diff), 165 cross negative
  (another item's hunk), 215 falsification (the same file from `review-2.diff`, the repaired tree), 1 null state.
* **Answer key.** A positive is a hunk of the file that **declares the symbol the recorded finding names**; a
  finding-named path breaks ties. Three weaker rules were discarded on the way, and each is a way a probe lies:
  matching the finding's backticked terms flags doc hunks that merely *mention* the symbol; matching named paths
  flags files the report only *read*; taking the file with the most symbol mentions flags tests and build
  scripts. On item 116 the key moved from `app/build.gradle.kts` to
  `core/src/main/java/com/naviveylin/core/mapsource/MapTreeDiscovery.kt` under the declaration rule.
* **Questions, one request per case:** a `noul` ("is there a defect in `hunk` that would make the change fail
  `claim`?"), a `score`, and a `choice` of failure class.

**Results (all 2 552 answered, 0 failures).**

| predictor | AUC (positive vs hard+cross negative) |
|---|---|
| `noul` | **0.430** — below chance; p50 P(defect) 0.23 positive vs 0.28 negative |
| `score`, inverted | **0.565** — the best Jev signal, still weak |
| control `openai/gpt-oss-120b` on the same states | 0.576 |

* The threshold sweep is the decisive shape: recall and false-positive rate track each other at every step
  (t=0.10 → 0.97/0.99; t=0.25 → 0.47/0.58; t=0.50 → 0.10/0.11). There is no operating point.
* **Brier 0.180 against 0.132 for a constant guess at the base rate (0.16)** — the model is worse than not
  looking.
* **Falsification fails outright:** at the only threshold with recall 1.0, **215 of 215** hunks of the repaired
  `review-2.diff` are flagged as defects.
* Null state 0.04 — not an always-yes model.
* Latency p50 0.24 s, p95 0.38 s. Cost $0.23 for 2 552 requests (3 questions each). **The price and latency
  claims hold.**

## Probe B — per-change, one question per scenario (the primitive's own shape)

`tools/bench/jev_probe_scenarios.py`. One request per **change** (the whole diff as state), **one `noul` per
scenario the change's own delta spec adds**, all in parallel; predictor `1 - min(P(established))` = "the change
fails at least one of its own scenarios".

| kind | n | p50 predictor |
|---|---|---|
| positive — round-1 report recorded a blocking finding | 100 | **0.18** |
| negative — round-2 report recorded none | 71 | **0.18** |
| ablation — the target file's section deleted | 97 | 0.55 |

`AUC (blocking vs not blocking) = 0.510` on 100/71 cases — a coin flip at identical medians and ranges.
Latency p50 0.4 s, p95 1.2 s; 1 440 questions for $0.29.

The ablation is **not** a falsification, and saying so is part of the record: deleting the file that implements a
scenario makes "the scenario is not established" *true*, so 55 % there is close to correct. What it exposes is
what Jev's answer actually tracks — the structural completeness of the diff. It noticed a missing file; it did
not notice item 116's recorded finding, that a `**Case**:` line exists and covers only half its scenario.

## Decision

1. **No Jev in the loop.** Nothing in `tools/loop/loop.conf` changes; the per-role models stay as recorded.
2. **If Jev is ever used for judgement, use `score`, never `noul`.** The ordinal answer scored 0.565 where the
   binary one was anti-correlated at 0.430 — a property of the primitive, not of this project.
3. **A Jev step must never gate anything.** It cannot emit the report the reviewer's product is, and it has no
   transcript for `evidence-check.sh` to read.

## What this does not settle

* **The positives are reviewer judgements, not ground truth.** A cheap model failing to reproduce a frontier
  reviewer's blocking calls could mean the reviewer saw something subtle. The anti-correlated `noul` and the
  Brier score worse than the base rate are not subtlety — they are a predictor pointing the wrong way.
* **The positive side of probe A is genuinely ambiguous.** A positive hunk comes from the file that *declares*
  the defect's symbol, and the correct implementation lives in the same file, so a hunk can be clean while the
  defect sits two functions down. Probe B removes that ambiguity and still finds nothing.
* **The claim was thin.** Probe A gave the issue text as the claim, probe B the delta spec's scenario text;
  neither carried the evidence a real reader gets (gate tallies, XML timestamps, the work package).
* **One project, one archive, one model version.** `jev-1.14-20261014` is pinned because a threshold tuned
  against one version need not survive the next.

## Re-taking this

```sh
# probe A — cases, run, grade (output lands in .pi/jev-probe/)
python3 tools/bench/jev_probe.py extract && python3 tools/bench/jev_probe.py plan
JEVPAR=12 python3 tools/bench/jev_probe.py run      --model typesafe/jev-1.14-20261014
python3 tools/bench/jev_probe.py grade              --model typesafe/jev-1.14-20261014 \
                                                    --control-model openai/gpt-oss-120b
# the control on the same states (see the note below on the reasoning budget)
JEVPAR=12 python3 tools/bench/jev_probe.py control --control-model openai/gpt-oss-120b --limit 800
# probe B
python3 tools/bench/jev_probe_scenarios.py extract && python3 tools/bench/jev_probe_scenarios.py plan
JEVPAR=6  python3 tools/bench/jev_probe_scenarios.py run --model typesafe/jev-1.14-20261014
```

Every response is cached under `.pi/jev-probe/responses/`, keyed by the exact request body, so a re-grade costs
nothing and an interrupted run resumes. Re-take when a new Jev version lands, when the plan's prices move, when
the account's guardrail changes, or when a *different* step is proposed — one whose answer is a ranking rather
than a defect localisation.

Two traps a re-taker meets, both recorded because they cost a round each:

* **A thinking chat model returns `content: null` when the reasoning budget is not capped** — the whole
  `max_tokens` goes to `reasoning`, `finish_reason` is `length`, and the control silently grades an empty set.
  The control therefore caps it (`reasoning.max_tokens`, `JEJ_REASONING`) and only sends the parameter when
  asked; `deepseek-v4.1-flash` and `minimax-m3.1-flash` both need it, `openai/gpt-oss-120b` does not.
* **Two cases can hash to one cached request.** The cache write is atomic
  (`write temp + os.replace`) for that reason; before it was, a concurrent run wrote interleaved files and the
  grade step failed on `Extra data`.
