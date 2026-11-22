# Proposal — the spend split is taken at the gate, and the suite's own tail is stated beside it

surface: 3 cases added (their rows, their dispatch arms and their bodies), 2 mutation records, 4 guideline paragraphs,
2 `tools/loop/loop.conf` comments, 1 `tools/loop/run.sh` comment, 1 comment in `tools/gate-attribute.sh`, 1 skill
bullet — 8 files in the tree, `git diff --stat 1a5dede2`

## Why

`GATE_RE` was `re.compile(r'gate\.sh|gradlew ')`: one pattern, two kinds of call. Every figure `tools/loop-spend.py`
printed as "after the item's first gate" was therefore the tail after the **first verification-ish call** — whichever
of a suite run, a `--plan`, or a `sed` over `gate.sh` the session happened to make first (`tools/loop-spend.py:55`,
quoted by `guidelines/Backlog.recipes.md:83`, `tools/loop/loop.conf:36`, `tools/loop/run.sh:1423` and
`tools/gate-attribute.sh:6`).

Measured over the archive on this tree (`.pi/issue-loop/items`, 2026-11-22, 59 items, 254 child sessions, 1.1 B tokens):
**15.6 Mtok** falls after the first **gate** run in the 37 sessions that made one, and **75.8 Mtok** after the first
**focused suite** run in the 13 that made one. The two positions differ by a factor of five, and the sentence the
loop's own argument for pre-gate review rests on named the smaller one — the suite's — as the gate's. A reading that
cannot tell them apart cannot say which tail a cut has to land in, which is the question every row of
`tools/loop/loop.conf` that quotes it is asking.

## What Changes

- **`tools/loop-spend.py` reads the program a call runs**, not a word in its text: `gate.sh` behind the wrappers a
  child writes (`cd …`, `timeout <n>`, `bash`, `env`, `nohup`, leading `VAR=value`) and without `--plan`/`--list`/
  `--help` is the gate; `gradlew` is a focused suite run. A command whose *argument* is the gate's path runs nothing.
- **The split is taken twice** — at the first gate and at the first suite — and both are printed, both are in
  `--json` (`afterFirstGate`, `afterFirstSuite`), and `LOOP-SPEND-GATE` carries both (`after=`, `suite=`). The suite
  line is not a second feature: it is the number the old line was printing under the gate's name.
- **The sites that quote the old figure are corrected to say which call it was taken at** — the earlier of the two —
  without rewriting their digits: those readings were taken over a 227-item window this tree no longer holds, so
  re-deriving them here would replace a measurement with a different one over a thinner archive
  (`design.md`, "The numbers the sites quote").
- **Scope**: `tools/loop-spend.py`, its selftest, the six prose sites and the skill bullet. No app code, no build, no
  gate configuration key.
- **Not this change**: the *other* reader of the same rule (`tools/loop/lib.sh`'s `gate_command_jq`, used by
  `state.sh` and `run.sh`) is untouched; whether the two readings should be one implementation is `design.md`'s
  alternative A, eliminated on cost, not on principle.
