# fix-a-tool-result-over-its-bound-is-trimmed

surface: 4 cases added, 1 rewritten, 6 mutation records added, 1 adapter edited, 1 bound recorded, 0 prompt skeletons, 0 fixtures — `grep -c "^case_adapter_" tools/loop-cases.sh` (3 adapter cases: 1 rewritten, 1 added), `grep -c "pi-child-events.sh" tools/loop-cases.sh` (224 declarations the adapter's pathspec owes), `grep -n "pi-child-events.sh@adapter#" tools/loop-mutations.sh` (4 records that mutate the adapter today), `grep -c "'6[67]\.[0-9]@" tools/loop-mutations.sh` (8 records: #661's two and this change's six)

The count is the tail the change pays for, not the first file the plan named: the adapter's argv is asserted by
three cases and the whole suite declares the adapter as a file it can reach (224 declarations), so a word added in
front of the prompt is a red case in more than one place, and the trim rule itself needs a case per invariant —
the cut, the pass-through, the notice and the record. Round 2's corrections move that tail by one assertion and two
records, not by a new file: the adapter case now drives the event stream and asserts where the trim record lands, and
the pass-through case drives a result of exactly the bound.

## Why

A child's tool result is injected whole and stays in its context for the rest of the session, so every later turn
re-reads it. Nothing between the tool and the child's context bounds one: the adapter appends each event line as
it arrives and prints the answer whole, and the run starts that command without a word about size.

- **The condition, verified in the tree today.** `tools/pi-child-events.sh:75` appends the child's event line
  unchanged to the item's stream and `tools/pi-child-events.sh:96` prints the answer whole;
  `tools/loop/run.sh:1025` starts `$LOOP_AGENT_CMD "$1"` with no bound of any kind. There is no trim, no cap and no
  maximum that applies to a tool result: `grep -rn "trim\|truncat\|maxResult\|MAX_RESULT" tools/loop/
  tools/pi-child-events.sh` returns two lines and neither touches one — a note about `head -20` in
  `tools/loop/measure.sh:126` and a `ltrimstr` in `tools/loop/prescreen.sh:112` (`evidence/nothing-trims.log`).
- **Root cause, one mechanism.** A result enters the child's context at its own size, and a token injected once is
  re-read by every later turn of that session, so one oversized result is paid for once per turn that follows it.
  Nothing trims a result, and no prompt can: the worker prompt has asked a child to read the section it needs
  rather than the whole document since before #246, and to cite a file rather than read it again since #246 — and
  the archive still records `LOOP-SPEND-READS calls=4798 duplicate=2174`: 45 % of its reads are a second read of a
  path the item already read (issue #246).
- **Measured, on the archive this run reads.** `python3 tools/loop-spend.py` over
  `.pi/issue-loop/items` (137 items, 365 child sessions, 20 648 turns): `LOOP-SPEND-TOKENS
  input=40298231 cacheRead=1610329592 output=13226175 total=1663853998`; the children injected **59.2 M
  characters** of tool output once, 876.4 M tokens of it re-read (53 % of all tokens); the heaviest single results
  are 116.6 k characters (one `ctx_read` in #78) and 51.3 k (`ctx_shell`); `LOOP-SPEND-CAP8K saved=228988001` (14 %
  of the archive), `LOOP-SPEND-CAP4K saved=453282909` (27 %), `LOOP-SPEND-CAP16K saved=29640189` (2 %).
  The distribution beside it (`evidence/result-sizes.py`, log `evidence/result-sizes.log`):
  `LOOP-RESULT-SIZES at=2026-10-27T00:17:42Z n=22901 chars=59179014`, `p50=825`, `p75=2917`, `p90=7194`,
  `p95=13684`, `p99=16149`, `max=116628`, and `bound=8000 above=2052 share=9.0%`. The spend is the tail, not the
  reading: the median result is 825 characters.
- **Red test.** Four cases, added by this change, failing on the base commit:
  `trim#aResultAboveTheBoundIsTrimmedAndNamed`, `trim#aResultUnderTheBoundIsHandedOverWhole`,
  `trim#theTrimIsRecorded` (`loop-selftest: 3 of 3 cases failed`,
  `evidence/red-on-head-trim.xml` `tests="3" failures="3"`, 2026-10-26T23:58:46Z) and
  `adapter#theChildIsStartedWithTheResultBound`, beside the rewritten `adapter#modeFlagIsItsOwnArgument`
  (`loop-selftest: 2 of 3 cases failed`, `evidence/red-on-head-adapter.xml` `tests="3" failures="2"`, same
  timestamp). Logs beside the tallies.
- **Owning requirement.** `openspec/specs/agent-issue-loop/spec.md`, the requirement this change adds, "A child's
  tool results are bounded and a trim is named" — the sibling of #661's "A child carries its own route", which is
  the same seam (what the adapter hands the child) on the prompt side.
- **Owning guideline section.** `guidelines/Backlog.md`, section 7 "The loop" — the paragraph "What a child is
  started with, and what it therefore re-reads", which owns that seam and already carries the #661 measurement.

## What changes

1. `tools/pi-trim-result.mjs` (new) — one rule with two entry points. As a pi extension it registers the
   `tool_result` hook and replaces a text block above the bound with its first `<bound>` characters and one line
   naming the tool, the size it had, the bound it was cut to and how to read the rest; as a CLI
   (`node tools/pi-trim-result.mjs --bound <n> [--tool <name>] [--record <file>]`) it applies the same rule to
   stdin, which is what the cases drive. A result at or under the bound is handed over byte for byte, and the
   trim is appended to `--record` as `LOOP-TOOL-RESULT-TRIM: <tool> chars=<n> kept=<bound>`.
2. `tools/pi-child-events.sh` — loads that extension with every child (`-e <path>`, before the mode words and the
   context switch) and names the record beside the child's own event stream, so the trim is a fact of the item.
   The bound itself is read from `tools/loop/loop.conf`, not sourced and not duplicated: `0`, or a project that
   records no key, means no bound.
3. `tools/loop/loop.conf` — `LOOP_TOOL_RESULT_MAX_CHARS=8000`, with the derivation the archive measured: p90 of
   the 22 901 results is 7 194 characters, so the smallest round bound above it leaves 9 results in 10 untouched
   and buys the 14 % that `LOOP-SPEND-CAP8K` names; 16 000 sits just under p99 (16 149) and above 1.2 % of the
   results, and buys 2 %; and 4 000 cuts one result in five — the material an item is worked from.
4. `tools/loop-cases.sh` — three cases for the rule (`trim#…`) and one for the adapter's loading of it, and
   `adapter#modeFlagIsItsOwnArgument`, whose assertion is the child's whole argv, rewritten for the extra words.
5. `tools/loop-mutations.sh` — six records, one per invariant: the cut, the notice, the record, the adapter's
   `-e`, the export that names the record file, and the pass-through's `at or under` boundary.
6. `guidelines/Backlog.md`, section 7 "The loop" — the rule, the measurement, the value and what disables it.

## Impact

What a child is handed changes; nothing about what the run decides changes. The engine is untouched: the bound
is this project's recorded value, read by this project's adapter where it is applied, so a ported project keeps
whatever its own configured command does (the porting recipe deletes the adapter). No prompt skeleton changes —
prose is exactly what the archive shows does not bound a result. The pathspec owes the loop's self-test, the
record check, the portability check and the doc-route check; the change runs the project's gate once.
