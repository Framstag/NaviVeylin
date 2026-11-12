# Proposal — the repeat rule's ledger is the item's, not the child's

## Why

**Root cause.** The repeat rule keeps its ledger in a `Map` that lives as long as one child process —
`tools/pi-trim-result.mjs:200-202` as the base has it: "The ledger is the session's: one child process is one session,
so a Map that lives as long as the hook is per-session" — and `repeatResult`, in the same file, replaces a read only when
its content sha matches an entry that ledger holds. An item split at its gate runs three or four
children in sequence over one tree (worker, the round before the gate, the gate child, the tail), so every path the
worker read is a **first** read for the gate child and enters its context whole. The item's record counts the repeat
anyway (`metrics.dupReads`, measured over the item's whole stream), which is why the report and the rule disagree.

**Evidence, verified against the tree (2026-11-12, issue #858).** `tools/look.sh 'ledger' --path tools` names four
files and no ledger of a fifth: the rule's own `Map` (`tools/pi-trim-result.mjs`), its CLI form (`readLedger`/
`writeLedger`, the one path a ledger has ever had), the cases that drive that CLI, and two hits that are not this
ledger at all (`tools/issues/core/Fingerprint.java` and `tools/loop-spend.py` use the word for other things). So the
change is which path the run hands each child, not a second rule. The run's own numbers, from
`.pi/issue-loop/runs/2026-11-11T11:37:08Z.json`:
90 of 251 file reads were a second read of a path the session already had (36 %), 486 700 characters in the repeat
results, **1 370 of them withheld by the repeat rule — 0.3 %** — and the two items that read most are #848 (31 of 83
reads) and #849 (27 of 77). Over the six items that reached no bound the repeat reads carry 389 156 characters
(`metrics.dupChars`).

**The red test.** `repeat#anItemsLedgerSpansItsChildren` drives the rule's own CLI twice with one ledger named by the
environment, the first read as the worker and the second as the gate child, then once more after an edit of the path.
On the base it FAILs — *the item's second child is handed the line, not text its worker already paid for:
'FIRST-LINE' should not appear in: FIRST-LINE …* — because a ledger the environment names is not one the CLI reads; the
adapter has named no such file either, which `adapter#theChildIsStartedWithTheItemsLedger` fails on its own
(*expected '…/items/858/tool-result-ledger.tsv', got ''*). Both XMLs and logs are in `.pi/issue-loop/items/858/`
(`red-on-head.log`, `red-on-head.xml`, 2026-11-12T21:20:56Z).

**The fix.** The ledger is the item's: `tools/pi-child-events.sh` names one file beside the item's event stream
(`LOOP_TOOL_RESULT_LEDGER`), `tools/pi-trim-result.mjs` opens it at the first read of every child and writes back what
it read, and the line a repeat is replaced by names the session that read the path (`LOOP_CHILD_SESSION`, the name of
the prompt the run handed that child). No new `loop.conf` key and no new state field: `metrics.dupReads` is unchanged
and the report already prints the withheld volume.

**One consequence the report owns.** `metrics.withheld` is read from the item's record, so once the ledger spans the
children it counts a repeat across two children that `metrics.dupChars` (per session, by the requirement "A run counts
the reads its sessions repeated") does not. The trend's `reads:` line said "N chars **of them** withheld", which would be
false: it now prints "N chars withheld by the repeat rule", and the requirement says why the two figures are no longer
one number's halves.

**Owning capability and guideline.** Capability `agent-issue-loop` (`openspec/specs/agent-issue-loop/spec.md`) owns the
rule's count and its record; the guideline section that owns the rule and the bound is `Backlog.recipes.md` §7a "Running one
item: the script, the child and the package" (`guidelines/Backlog.recipes.md:174-205`), which stated the bound and the
record and said nothing of the ledger's scope. Both are edited.

**Surface** (`tools/look.sh 'LOOP-ALREADY-READ|alreadyReadNotice|readLedger|writeLedger'`, and
`tools/look.sh 'ledger' --path tools`): 1 rule file (`tools/pi-trim-result.mjs`), 1 adapter (`tools/pi-child-events.sh`),
1 report clause (`tools/loop/report.sh`), 3 cases added and 1 rewritten in `tools/loop/tests/loop-cases.sh`, 6 mutation
records added in `tools/loop/tests/loop-mutations.sh`, 1 guideline section, and the spec delta.

surface: 3 cases added, 1 rewritten, 6 mutation records added, 1 report clause and 1 guideline section realigned —
`tools/look.sh 'ledger' --path tools` names the two files the rule lives in, `tools/look.sh 'LOOP-ALREADY-READ'` the two
that assert it, and the suite's own `--list` counts the three cases.
