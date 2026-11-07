---
name: reviewer-model-benchmark
description: Measure whether a cheaper cloud model can do the loop's REVIEWER job — replay one archived review round against its own recorded report, load a p99-size two-diff context, and grade every candidate on findings, format and quoted anchors. Use when the reviewer might move to a cheaper model, when a new ollama-cloud model appears and someone asks whether it is good enough, when a review round looked weak and the model is the suspect, or when re-taking a model decision recorded in `tools/loop/loop.conf`.
---

# Benchmark a reviewer model

One measurement, re-runnable: replay a **finished** review round on a model, grade it against the report that
round actually produced, and load the window the archive's p99 diff demands. A model that clears it is a
candidate for `LOOP_AGENT_CMD_REVIEWER`; nothing here edits the loop, and the switch is its own commit.

The recorded decisions are `docs/2026-11-07-reviewer-model-analysis.md` (why `minimax-m3.1-flash` and not
`deepseek-v4.1-flash`) and the `LOOP_AGENT_CMD_REVIEWER` block in `tools/loop/loop.conf`. Read the report's
"Re-taking this" section before starting — it names what the first run could not settle.

## 0. Know which price you are moving from

The reviewer is **input-bound**: ~33 turns per review at ~53.6 k context (`tools/loop-spend.py --json`), so
1.8 Mtok of input per review and a few thousand output tokens — plus thinking tokens, which are output too.
A candidate is worth testing when, at `https://ollama.com/pricing`, it is **at most half** the current model on
input and output, has **≥ 256 K** context, and carries the **`tools`** tag (a model without tool calling cannot
be a reviewer: it has to read files). Write the qualifying set down before spending anything.

## 1. Pick the item — one with a hard, recorded round

Use a finished item from `.pi/issue-loop/items/` that has `review-1.diff`, `reviewer-prompt-1.md`,
`work-package.md` and **`reviewer-report-1.md`** (the answer key: the round the old model really produced). The
diff should be at or above the archive's p90 (`ls -l .pi/issue-loop/items/*/review-1.diff | awk '{print $5}' |
sort -n`). A round that found a real blocking defect is worth more than a clean one — it is what separates a
model that reads from a model that passes everything.

## 2. Write the probe and its key

```bash
tools/bench/probe-spec.sh .pi/issue-loop/items/<n> .pi/tmp/probe-spec.md .pi/tmp/probe-key.md
```

The spec asks for two things in one session: **Part A**, the item's own reviewer round replayed from the rendered
prompt, and **Part B**, a second diff read whole with six anchors spread over both files. The key carries those
anchors' exact text and the `diff --git` counts — never hand the key to a child, and keep it out of the spec.

## 3. Run one child per candidate

Same agent, same spec, one model each — one `subagent` call per candidate, all launched in one message so they
run in parallel (the Pro plan queues past 3 concurrent requests, so wall clock is not comparable between them):

```text
subagent({ agent: "reviewer", model: "ollama-cloud/<id>", cwd: "<project>",
           task: "Read <project>/.pi/tmp/probe-spec.md and carry out both parts exactly as written. Print
                  nothing outside the two required sections." })
```

An `Unknown subagent model` answer lists the registry's real ids (`nemotron-3-nano` is `…-nano:30b`), so one
failed launch costs one call, not the run. Run the model the project uses today as a **baseline** through the
same spec: it calibrates the grading, and it catches a prompt that grades everything red.

## 4. Grade against the key, not against the impression

`references/rubric.md` holds the five gates and the pass rule. In short: the eight-item report must exist, the
recorded round's blocking finding must be recovered *with its file and line*, the verdict must match, the
anchors must be byte-exact, and **fabricated evidence is disqualifying** — one invented test tally outranks a
hundred correct sentences. Read `reviewer-report-1.md` only after every child has finished.

## 5. Record it

- **Keep the outputs** under `.pi/tmp/model-tests/` (gitignored) and put the numbers in a dated report under
  `docs/`, one file per study, goal and date in the name — `docs/<YYYY-MM-DD>-reviewer-model-analysis.md`.
- **A model that wins** changes one line, `LOOP_AGENT_CMD_REVIEWER` in `tools/loop/loop.conf`, with the
  measurement beside it in the comment: which item, which date, what it recovered, what it costs, what it failed.
- **A model that loses** is named in the same comment only when the failure was interesting (a PASS over a real
  defect is), so a later study does not retest it blind.
- **A defect the probe uncovers** in this tree (a claim the evidence contradicts, a prompt that leaves a bound
  unstated) becomes an issue through `create-issue` — never an inline fix, because the study and the change must
  not be the same write.

## Two limits to state in every report

Single item, single sample: a reviewer model is not proven by one round, and the report says so. And no
candidate runs a gate round here — this measures reading, format and quoting, not how the model behaves across
a budgeted item. The first live run is the second measurement: `python3 tools/loop-spend.py --json` per role,
plus a `revert-check` on the first finding the new reviewer raises.
