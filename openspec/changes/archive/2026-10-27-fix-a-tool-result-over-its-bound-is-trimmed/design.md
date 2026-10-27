# Design — fix-a-tool-result-over-its-bound-is-trimmed

## Root cause

One mechanism: a tool result enters the child's context at its own size and is re-read by every later turn of that
session, and nothing between the tool and the context bounds it. The adapter appends each event line as it
arrives (`tools/pi-child-events.sh:75`) and prints the answer whole (`:96`); the run starts that command with no
word about size (`tools/loop/run.sh:1025`). Measured over the archive this run reads
(`python3 tools/loop-spend.py`, `evidence/loop-spend.log`): 59.2 M characters injected once, 876.4 M tokens of
re-read tool output — 53 % of the 1.66 B tokens the archive spent — with a 116.6 k `ctx_read` and 51.3 k
`ctx_shell` results at the top. The distribution (`evidence/result-sizes.py`, `evidence/result-sizes.log`):
`p50=825`, `p75=2917`, `p90=7194`, `p95=13684`, `p99=16149`, `max=116628` over 22 901 results.

## The fix

One rule, in one file, applied where a result passes from the tool to the child's context:

1. `tools/pi-trim-result.mjs` — the bound. As a pi extension it registers the `tool_result` hook: a text block
   longer than the bound is replaced by its first `<bound>` characters, one character-safe cut, then one line
   `[LOOP-TRIMMED: <tool> returned <n> characters; the first <bound> are shown — read the rest in ranges (…)]`.
   A result at or under the bound is returned untouched, and each trim is appended to the record file as
   `LOOP-TOOL-RESULT-TRIM: <tool> chars=<n> kept=<bound>`. The same file is a CLI (`--bound`, `--tool`, `--record`)
   over stdin, so the rule the cases drive is the rule the hook applies, and no second implementation of it exists.
   The handler never throws: an error leaves the result as the tool returned it.
2. `tools/pi-child-events.sh` — `extension=(-e <the file beside this adapter>)`, appended before the mode words and
   the context switch, so every child goes through it; and `LOOP_TOOL_RESULT_TRIM_RECORD`, named beside the child's
   own event stream (`<item dir>/tool-result-trims.log`) when the run gave one, so the trim is a fact of the item.
   The bound is **read** from `tools/loop/loop.conf` — the same "read, never source" shape `loop_tmp_dir_for`
   already uses for another tree's configuration — and exported to the child; a project that records no key gets
   `0`, which the extension reads as no bound.
3. `tools/loop/loop.conf` — `LOOP_TOOL_RESULT_MAX_CHARS=8000`, recorded with its derivation (p90 of the 22 901
   results, the 9.0 % of results above it and the 14 % of the archive `LOOP-SPEND-CAP8K` names).

## Where the bound sits, and why 8 000

The archive's per-result distribution decides both. `p90` is 7 194 characters, so 8 000 is the smallest round
bound above nine results in ten: an ordinary result — the median is 825 — is handed over byte for byte, and the
spend that goes is the tail. The other two numbers the measurement offers cost more than they buy: 16 000 is past
`p99` and saves 2 %, and 4 000 cuts 18.9 % of all results and saves 27 %, which is one result in five of the
material an item is worked from. `LOOP_TOOL_RESULT_MAX_CHARS=0` turns the bound off, like the other recorded
bounds' `0`.

## The risk, and what proves it did not happen

- **A handler that throws would fail a tool mid-item.** The hook body is wrapped: on any error it returns nothing,
  which leaves the result exactly as the tool produced it. The rule itself is exercised through the CLI the same
  file exposes (`trim#aResultAboveTheBoundIsTrimmedAndNamed`, `trim#aResultUnderTheBoundIsHandedOverWhole`,
  `trim#theTrimIsRecorded`), so a mistake in the cut or in the notice is a red case, not a silent trim.
- **A child started without the bound.** `adapter#theChildIsStartedWithTheResultBound` asserts the `-e` word and
  the file it names, and that the file is a hook on `tool_result` rather than a flag the agent happens to accept;
  `adapter#modeFlagIsItsOwnArgument` asserts the child's whole argv, so a word that moves in front of the prompt is
  a red case. What an offline case cannot do is run the agent: the hook's *loading* is pinned, its *behaviour* is
  the CLI's behaviour.
- **A claim judged on a cut line.** The trim names the tool, the size and the bound, and records the same three
  facts beside the child's event stream, so a reader of the item sees which result was cut and can re-read the
  range.

## Blast radius

`tools/pi-trim-result.mjs` (new), `tools/pi-child-events.sh`, one key in `tools/loop/loop.conf`, 4 cases added and
1 rewritten in `tools/loop-cases.sh`, 4 records in `tools/loop-mutations.sh`, one paragraph in
`guidelines/Backlog.md` section 7 "The loop", and this change. No engine script (`tools/loop/*.sh`) changes, so
`run#engineNamesNoProjectFact` and `tools/check-portability.sh` are owed but not moved by it; a ported project
deletes the adapter and keeps whatever its own configured command does, exactly as with #661.

## Rollback

Remove the `extension=(-e …)` words from the adapter: every child is started exactly as before, the bound key and
the cases stay and the extension file is loaded by nothing. `LOOP_TOOL_RESULT_MAX_CHARS=0` is the narrower switch:
the extension is still loaded and hands every result over unchanged. Nothing else reads either.

## Alternatives the evidence eliminated

- **A bound at each source** — the gate log summarised by `tools/loop/gate.sh`, `ctx_read` told in the prompt to
  use its map/signature modes above a size, a diff read in hunks. Eliminated as *the* repair, not as a habit: it
  bounds only the sources somebody named today, and the archive's tail is spread over the tools that exist
  (`ctx_shell` 25.0 M characters over 13 545 calls, `ctx_read` 32.2 M over 4 796, `web_search` 33.0 k over 4), so
  the next tool re-opens it; and two thirds of it is prose, which the next alternative removes. It stays available
  as a complement and is not part of this change.
- **Prose in the phase prompts** — "prefer `mode=map` above 8 000 characters", "`tail -n` a gate log". Eliminated by
  measurement: the worker prompt has carried size and re-read guidance since #246 (`worker-prompt.md`
  "read the section that owns the rule and never the whole document", "a second read of the same path is a second
  model turn and buys nothing"), and the archive's repeated-read share is 45 % before and after that change, with
  a 116.6 k single result standing beside it.
- **Bounding the reader itself** (`ctx_read`, `ctx_shell`). Not this tree's to change — those are a separate tool's
  modes, configured outside this repository — and the bound has to hold for every tool a child calls, including
  one this project has never named.
- **Cutting turns instead**, which the spend report prices higher (`15% fewer turns - 378.4M`). A different lever on
  a different mechanism: the number of turns is what an item's work costs, not what one result is handed over at.
  It is not a bound on a result and this item is not that change.
