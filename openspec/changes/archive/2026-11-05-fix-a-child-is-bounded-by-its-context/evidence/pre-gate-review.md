## Review — item #760, change `fix-a-child-is-bounded-by-its-context` (pre-gate round)

Read: `review-pre-gate.diff` (5 files, 284+/7−), the change root `.pi/worktrees/loop` (`openspec/changes/fix-a-child-is-bounded-by-its-context/{proposal,design,tasks}.md`, `specs/agent-issue-loop/spec.md`, `evidence/`), the item package (`change: none`, `recorded: none`), and the reading set derived by the one allowed command:

```
$ bash tools/check-rule-routes.sh --diff 122cc2f9ee652e42f58c002100e175e2404deb29     # in .pi/worktrees/loop
  Backlog.md §1 · §3 (+2 rules) · §4 (+1 rule) · §5 (+5 rules) · §7 · §14 (+1 rule)
  BacklogTooling.md §9 (+5 rules) · §10 (+1 rule)
  BuildGate.md §2a
```

`guidelines/Backlog.recipes.md` §7b and `guidelines/BacklogTooling.md` §11 — the two sections the change's artifacts name as owning — are **additions** to that set (recorded as additions below, never as substitutes). Nothing in the ITEM_CHANGE block: the item names no change and records no decision, so the decision-fork check has no input to read.

---

### 1. Scenario coverage — per delta scenario

**Scenario `A child whose context crosses the recorded bound is ended and named` → PASS.**
Case `run#childPastItsContextBoundIsBlocked` (`tools/loop-cases.sh:11505`), green in `evidence/green-cases.log` and `/items/760/green-bound.log`. Assertions, quoted: `assert_contains "$out" "item-context-max=200000"`; `assert_contains "$out" "LOOP-CONTEXT:"`; `assert_contains "$out" "LOOP_ITEM_CONTEXT_WARN records 143124"`; `assert_contains "$out" "exit 124"`; `assert_contains report "LOOP-TIMEOUT: the child reached the recorded context bound of 200000 tokens"`; `assert_contains report "at 210000 tokens"`; `assert_not_contains report "the child exceeded"`; `assert_eq "blocked" … .items[0].result`; `assert_eq "0" "$(grep -c 'issue close' …)"`. Timestamp: red run `2026-11-05T11:53:03Z` (worker report; corroborated by `loop-20261105-115253` in `red-zero-on-head.log`); the green logs carry a date, `# the applied tree, 2026-11-05`, and no clock time.

**Scenario `A recorded zero keeps the context bound off` → PASS.**
Case `run#theRecordedZeroKeepsTheContextBoundOff` (`tools/loop-cases.sh:11608`), green in the same two logs. Assertions: `"item-context-max=0"`, `assert_not_contains "$out" "LOOP-CONTEXT:"`, `"LOOP-TIMEOUT: the child exceeded 2 s"`, `assert_not_contains report "context bound"`, `assert_eq "blocked" …`. Red timestamp `2026-11-05T11:52:51Z` (`evidence/red-on-head.log`).

### 2. Cause or gap scope — the named root cause and nothing else

The diff is the bound and its consumers, in five files. The hunk that is the root cause:

```
+child_context() { # child_context <events file> <lines already read> → "<largest context in the new lines> <lines read>"
…
+      (([.[] | select(.type == "message_update" or .type == "turn_end") | .usage? | …
+          | ((.input // 0) + (.cacheRead // 0))] | max) // 0) as $turn
+      | (([.[] | select(.type == "compaction_end") | (.result?.tokensBefore? // empty) | numbers] | max) // 0) as $compacted
```

and the two decisions in `run_child`'s whole-second wait (`run.sh:1361-1391`): `LOOP-CONTEXT:` at `item_context_warn`, `ended_by=context` + TERM/grace/KILL at `item_context_max`, plus the `LOOP-TIMEOUT` arm. The field paths match the reading the report already takes — `tools/loop/state.sh:674` uses the same `.result?.tokensBefore?` and `.input + .cacheRead` — and the stream is the item's shared one, exactly as `guidelines/Backlog.recipes.md:110` describes the turn bound. Remaining hunks: the two header fields, the two `loop.conf` keys, two cases + registration, two guideline paragraphs, and two refusal strings rewrapped to say "its turns or its context". **PASS** — nothing outside the root cause; `measure.txt` confirms `paths=5`.

### 3. Gate reality

`PASS` — the gate has not run — this round precedes it. (`evidence/measure.txt`: `LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run`. No suite was run by this round.)

### 4. Tasks claiming done

- 3.2 (`[x]`, "the red cases pass on the applied tree, and the two turn-bound cases still pass unchanged") — satisfied for the four cases it names (`tasks-verification.md` 3.2).
- 4.2 (`[x]`, `git status --porcelain` names only the listed files) — satisfied: five `M` lines, no archive.
- 4.1 (`[x]`) — its `Verify:` line is *"each named check exit 0 for the files this change touches"*, while `measure.txt` names `tools/loop-selftest.sh --from-git 122cc2f9`, `tools/check-doc-routes-selftest.sh`, `tools/mutate-selftest.sh`, `tools/issue-tooling-selftest.sh` and four `tools/mutate.sh --list` calls, none of which the session ran. The task body says why; the marker does not.

NOTE: `tasks.md` 4.1 is `[x]` under `Verify: each named check exit 0 for the files this change touches`, and the named checks did not run — the task body defers them to the gate, so the correction is the marker's wording, not a new claim.

### 5. Revert-check

The mutation that proves the invariant is the base-commit run of the two cases (`evidence/red-on-head.log`, `--from-git 122cc2f9`), and it fails for the right reason, in the two shapes the root cause has:

```
run#theRecordedZeroKeepsTheContextBoundOff|   the run's header says the context bound is off: 'item-context-max=0' not found in: agent-issue-loop: … item-turns-max=250 min-free-mib=1024 …        (2026-11-05T11:52:51Z)
run#childPastItsContextBoundIsBlocked|   the run was still waiting after 60 s on a child whose context had grown to 210 000 tokens past a recorded bound of 200 000                                               (2026-11-05T11:53:03Z)
```

Neither is a missing line dressed as a defect: the second is the run waiting past the reading, which is the invariant. Restore is clean — the run was taken against the base commit, so no file was mutated; `tasks-verification.md` 4.2 shows `git status --porcelain -uall` naming only the five changed files, and `grep -n "if false\|# the mutation" review-pre-gate.diff` prints nothing. `loop-mutations: all 235 cases passed` is `--check` (no mutation applied, no case run), so it is not the revert evidence; the red run is.

### 6. Evidence pointers

Numbers with their command in the record: `n=300 min=22710 p50=107669 p75=143124 p90=174984 p95=191815 max=244764` — the proposal carries the full `jq | sort | awk` and `evidence/context-distribution.txt` carries its output; `1.86×` is `200 000 / 107 669`; `paths=5 insertions=284 deletions=7`, `cases=316`, `scenarios=2 cases=2 unnamed=0` are `measure.txt`; `134:/135:` are the `grep -n '^LOOP_ITEM_CONTEXT' tools/loop/loop.conf` output.

NOTE: `10 of the 300 recorded items (3.3 %)` (loop.conf:127, run.sh comment, `Backlog.recipes.md` §7b, design.md) rests on a second command whose line is not quoted — `evidence/context-distribution.txt` prints the ten values and `# how many / 10`, but the file's only `$` line is the percentile command (with `awk '...'` elided), and the proposal's block prints percentiles only. No case or assertion depends on it; 10/300 is consistent with the ten values listed.
NOTE: `tests=2 failures=2` (proposal, "`tests=2 failures=2` over the two-case selection, no errors") was printed by no command in the record — the two runs each printed `loop-selftest: 1 of 1 cases failed`.
NOTE: `40 k` ("the same 250 turns cost 40 k or 240 k") has no command in the record: `240 k` matches `max=244764`, `40 k` is below the distribution's own `min=22710`→ no, above it, but the recorded distribution carries no 40 k reading; drop it or cite the row it came from.
NOTE: `227 items, 2026-11-03, tokens per item p50 10.4 M and p90 23.6 M … p50 140 and p90 273` names `tools/loop-spend.py --json` but the record carries no output of it; the numbers are the item body's, quoted as measurement.
NOTE: the proposal cites `evidence/red-bound-on-head.log` and `evidence/red-zero-on-head.log`; the change carries **one** file, `evidence/red-on-head.log`, holding both runs with both timestamps. The two names it points at exist only under `.pi/issue-loop/items/760/`, so the archive would carry a pointer to nothing. Correct the two pointers.
NOTE: `evidence/context-distribution.txt` and the two runs' logs are the only copies; nothing here is build output, so nothing is fragile.
NOTE: neither green log carries a clock time — `# the applied tree, 2026-11-05` only — so the green side of item 1 is dated, not timestamped.

### 7. Spec and guideline honesty — graded against the derived set

- `Backlog.md` §4 "an invariant is stated with the observation that would falsify it": the two new cases are that observation, and the §4 mechanism (I12 one writer, I13 project facts in the profile) is untouched — `run.sh` adds no project value (both keys default `0`), the numbers stay in `tools/loop/loop.conf`; `check-portability.sh exit 0 (56 engines, 7 templates)`.
- `BacklogTooling.md` §10 "an engine names no project value": satisfied as above; `LOOP_ITEM_CONTEXT_WARN=143124` is recorded in the project's profile, not defaulted in the engine.
- `BacklogTooling.md` §9 rules (declared rows, identity, hysteresis, bounded filings, first exercise): no check or row is added, so none is implicated.
- `BuildGate.md` §2a "a delta owes the verification its file roster names, and no more" + "name every exclusion": roster is `tools/**`, `guidelines/**`, `openspec/**` → no build, no suite owed; tasks 4.1/4.2 name the deferral and the four structural checks ran (`checks.log`). **Satisfied — and it is the reason the finding below was not found by the change.**
- Additions: `Backlog.recipes.md` §7b got a paragraph stating the unit, the measurement, both values, the checkpoint and the stop, the `0` switch and what is not replaced (a procedure, not a restatement); `BacklogTooling.md` §11 got the two table rows. Both recorded as additions to the reading set.
- Kind: one requirement ADDED, with a SHALL contract and two scenarios each naming a case — but the delta is ADDED-only where a MODIFIED block was owed.

BLOCKING: `tools/loop/run.sh:2270` narrows the reviewer's cut reason to `"the run ended the reviewer of round $round at one of the item's spend bounds (its turns or its context) — …"`, and `tools/loop/run.sh:2598` writes exactly that into the item's note (`blocked_note="${blocked_reason:-the item was blocked}"`), while `tools/loop-cases.sh:5131` asserts `assert_contains "$(jq -r '.items[0].note' "$state")" "turn bound" "the reason on the item names the bound"` — the substring `turn bound` is absent from the new text, so `run#aReviewerEndedAtItsTurnBoundIsNotARefusal` (registered at `loop-cases.sh:192`, a shipped `[x]`-grade case) goes red on the applied tree. The four cases the session ran do not include it, and `loop-mutations --check` cannot see it. Correction: keep the words in the string (`at one of the item's spend bounds (its turn bound or its context bound)`) or widen the assertion to `its turns or its context`; either is one line and needs no new claim, case or assertion.

NOTE: the delta is ADDED-only, and two shipped scenarios enumerate the turn bound as the only spend bound: `openspec/specs/agent-issue-loop/spec.md:97` (`run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault`, WHEN "was not ended by the run's own wall-clock or its turn bound") and `:3049` (`run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun`, WHEN "cut by the deadline, ended at the item's turn bound, or cut twice") — the second describes precisely the code path the change edits (`pre_gate_refusal`). A MODIFIED block over those two WHEN clauses is the honest kind, not a substitution for the ADDED requirement.
NOTE: `guidelines/Backlog.md:273` (§7b, a derived section) still reads "The item's cost, the clock, the memory floor, the turn bound and the gate-step bound are `Backlog.recipes.md` §7b" — the index names two bounds where the recipe now records three. Same shape at `Backlog.recipes.md:367`, `:677` and `:110` (not in the derived set; recorded as additions), all of which name `LOOP_ITEM_TURNS_MAX` as "the **stop**".
NOTE: the ADDED requirement opens with two sentences of history ("The item's only bound that ends a running child was a count of turns … so a session whose context has grown pays …"), which is design prose in a contract document; the SHALL clauses that follow are the contract and are stated as one.

### 8. New overreach — claims no evidence supports

UNPROVEN: "at the window every provider this project runs sits at" (loop.conf:126-127, the `run.sh` comment, `Backlog.recipes.md` §7b, `design.md` "Why the numbers are what they are") — no command, file or measurement in the change or the tree records a provider's window; `tools/tooling.json` and `tools/loop/loop.conf` carry no such fact.
UNPROVEN: "the archive's event streams run to megabytes per item" (`design.md`, "Alternatives the evidence eliminated", rejecting the whole-stream re-read) — no size is measured in the record.
UNPROVEN: "97 % of it cache read" / "227 items at 78 k tokens each" (recipes §7b addition, running the item body's numbers as measurement) — the item body is untrusted tracker text and no command in the record produced them.

---

LOOP-BLOCKING: 1
VERDICT: FAIL
