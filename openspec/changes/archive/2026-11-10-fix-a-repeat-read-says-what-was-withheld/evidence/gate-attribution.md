# Gate attribution — the one red step, and why this change does not own it

The gate this item ran once — the round-2 gate, `./tools/loop/gate.sh --base d5d2e668e5acb4cffb47a832b83843f0d7e91be1`
(`evidence/gate-result.txt`, `evidence/gate-round-2.log`) — reports one error: its `LOOP-GATE` tally line and failing
step are carried verbatim in `evidence/gate-result.txt` (the `LOOP-GATE` line and the `failing step:` line), the failing
step being `bash tools/gen-feature-list.sh --check-classification` (10s); the step's own tally is in
`evidence/gate-tallies-round-2/bash_tools_gen-feature-list.sh_--check-classification.xml`.

## What the step actually failed on

`--check-classification` gates on unclassified spec ids; `stale: 3` is reported and does not fail
(`tools/gen-feature-list.sh:198`–`:255`). The gate error is four unclassified delta spec ids, none of them this
change's:

```
gen-feature-list: unclassified spec id(s):
  architecture-test-suite (introduced by the open change package-structure-as-five-kinds — classify it in that change, before it archives)
  domain-vocabulary (introduced by the open change domain-vocabulary-is-declared — classify it in that change, before it archives)
  package-structure (introduced by the open change package-structure-as-five-kinds — classify it in that change, before it archives)
  vocabulary-projection (introduced by the open change domain-vocabulary-is-declared — classify it in that change, before it archives)
gen-feature-list: classification gate failed
exit=1
```

Full output: `evidence/classification-check-change-root.log:1`, tallies at
`evidence/classification-check-change-root.log:12` (`unclassified: 4`). The script itself assigns the debt to the
two other changes ("classify it in that change, before it archives"). This change's delta id `agent-issue-loop` is
shipped (`openspec/specs/agent-issue-loop/spec.md`) and classified (`tools/feature-list/specs.json` holds
`agent-issue-loop: true`), so it appears in no unclassified set; the check reads only `openspec/specs/*/spec.md`,
`openspec/changes/*/specs/*/spec.md`, and `tools/feature-list/` — none of them paths this change edits
(`tools/pi-trim-result.mjs`, `tools/loop/state.sh`, `tools/loop/report.sh`, `tools/loop/tests/loop-cases.sh`,
`guidelines/Backlog.recipes.md`, this change's own directory).

## Red before this work, red after it — both measured

- **Without this work**: a hardlink copy of the change root with every file this change edits replaced by
  `git show d5d2e668e5acb4cffb47a832b83843f0d7e91be1:<path>` and this change's directory removed, run through the
  same command: `exit=1`, `unclassified: 4` — `evidence/classification-check-before-work.log:1`,
  `evidence/classification-check-before-work.log:12`, `evidence/classification-check-before-work.log:18`.
- **With this work**: the gate's own result (the XML above) and a run of the same command on the change root:
  `exit=1` — `evidence/classification-check-change-root.log:18`. That run still describes this tree: the command reads
  `openspec/specs/*/spec.md`, `openspec/changes/*/specs/*/spec.md` and `tools/feature-list/`, and this round's
  corrections touched none of them — the files it edited are `tools/loop/tests/loop-cases.sh` and this change's own
  `design.md`, `proposal.md` and `tasks.md`.

The two outputs are byte-identical. This change moves the step by nothing; the two other open changes' delta specs
are the whole of the red.

## The clause this close is waived with

`LOOP-GATE-WAIVED`: the gate's one error is `bash tools/gen-feature-list.sh --check-classification`, red on four
unclassified delta spec ids introduced by two other open changes (`package-structure-as-five-kinds`,
`domain-vocabulary-is-declared`) — the classification line those changes owe before they archive — proven red
without this change's work and byte-identically red with it, while this change's own delta id is shipped and
classified and none of the step's inputs are paths it edits.

`LOOP-GATE-WAIVED-SUITE`: `bash tools/gen-feature-list.sh --check-classification`
