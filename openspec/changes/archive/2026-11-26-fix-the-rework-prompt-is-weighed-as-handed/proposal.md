# The rework prompt is weighed as the file the child is handed

LOOP-ROUTE: guidelines/BuildGate.md §2a

## Why

The rework prompt is the largest prompt the run composes, and it was the one prompt the bound could never fire on.

- `worker_prompt_bound_check` ran at `tools/loop/run.sh:2515` on `worker-prompt-round2.md` as it stood **after the
  render and before the refusal was appended**, and the reviewer's refusal went onto that file two lines later
  (`printf … "$findings" >>`, `:2516`), where `findings=$(cat "$reviewer_report")` (`:2505`) was the **whole** report.
- On item #1080 (2026-11-25) the handed file was `worker-prompt-round2.md` at 42 409 B: 27 120 B of rendered
  `worker.md` and 15 289 B of `reviewer-report-1-restart.md` (15 222 B) plus its header. The check read the 27 120 B
  figure against `LOOP_WORKER_PROMPT_MAX_BYTES` (29 000, `tools/loop/loop.conf:393`), so the 56 % the append added was
  never weighed and no line ever named it.
- The append also carried the report's prose into a file re-sent with every one of the rework session's turns: on
  #1080's own numbers that is a 3.8 k-token slice of each turn, about 0.55 Mtok of 32.8 Mtok over the item.

Root cause: the bound is measured on the file the run has just rendered, while the prompt the child is handed is
that file plus an unbounded append — so the check and the bytes it exists to weigh are two different files.

**Red test**: `run#theReworkPromptIsTheFileTheChildIsHanded` fails on HEAD.
`bash tools/loop/tests/loop-selftest.sh --case 'run#theReworkPromptIsTheFileTheChildIsHanded' --jobs 1` exits 1 with
`'the worker-round2 prompt is 34269 B' not found in: …` — the run's own line named the pre-append size, and the
handed file was 34 269 B (`items/1088/red-on-HEAD-theReworkPromptIsTheFileTheChildIsHanded.log`).

## What changes

1. `tools/loop/evidence.sh` — `blocking_finding_lines <report>` prints the report's `BLOCKING:` lines, and
   `blocking_findings` counts them through it, so the vocabulary #1085 established has one reading and not two.
2. `tools/loop/run.sh:2510` — the append's text is `blocking_finding_lines "$reviewer_report"`, and the appended
   section names the report's own path, so a finding the summary leaves out is one `ctx_read` away.
3. `tools/loop/run.sh:2523-2525` — the append happens first and `worker_prompt_bound_check` runs on the file the child
   is handed.
4. `tools/loop/layout.tsv`, `tools/loop/shared.tsv` — the new name is declared where the engine's table and its
   shared names are read.
5. `guidelines/BuildGate.md` §2a — the owning rule states that the weighed file is the handed file, and that the
   rework append carries the findings and the report's path.

surface: 1 case added, 0 rewritten, 0 fixtures realigned — `bash tools/loop/tests/loop-selftest.sh --list --files
tools/loop/run.sh` (185 `run#` cases in the delta's declared surface).

## What this does not claim

It does not make a rework session cheap: it bounds an append that no bound covered and stops re-sending a report's
prose at every turn. It says nothing about the prompt's 27 120 B baseline, the item's context depth or its duplicate
reads.
