# Review — #780, pre-gate round

Reading set (from `bash tools/check-rule-routes.sh --diff ec58cb91177f2d1c4621b87cdd26a9c7fced5117`, run in the worktree): `BacklogTooling.md` §9 + §10 and the named rules §9 "a check is a declared row, not a script.", §9 "identity is finer than the check.", §9 "hysteresis in both directions.", §9 "the issue is the measurement history.", §9 "one run of one check files a bounded number of condition items", §9 "a row's first exercise is reported, not spent.", §10 "an engine names no project value". Read all. No addition to the reading set was needed; `guidelines/Backlog.md` §5 (named by the proposal) is an addition and is not graded.

## 1. Scenario coverage

| Delta scenario | verdict | asserting case, assertion, timestamp |
|---|---|---|
| The recurrence bound is reached | PASS | `run#escalatesPastKRecurrences` — `assert_eq "0" "$(recorded "$log" 'issue reopen')" "past the recurrence bound nothing is reopened"` + `assert_contains … "--add-label agent/needs-decision" "the escalation marks the issue as awaiting a decision"`; `sensor-selftest.log:46` `PASS`, suite at `2026-11-05T07:56:11Z` (the log itself carries no time — see item 6) |
| Escalating does not authorise work | PASS | `run#escalationNeverSetsEligibility` — `assert_eq "0" "$(recorded "$log" 'agent/eligible')" "an escalation authorises no work"` plus the label-pair equality; `sensor-selftest.log:50` `PASS`, same suite run |
| A flapping condition does not churn | PASS | `run#hysteresisBothDirections` — `assert_eq "0" "$(recorded "$log" 'issue close')" "one clear run does not close"` … `assert_eq "1" … "the second consecutive clear closes"`; `sensor-selftest.log:44` `PASS`, same suite run |
| A settled issue's escalation files the returned condition | **FAIL** | `run#anEscalationOnASettledIssueFilesANewItem`, green `sensor-selftest.log:47`, red `FAIL` at `2026-11-05T07:52:12Z` (`red-case.log`). Two of its three THEN clauses are asserted (`condition_creates`=1, `recorded … "$fp"`=1, no `issue edit`); the third — "reports the item it filed **rather than an escalation**" — is asserted only by presence (`tools/sensor-cases.sh:1680`), never by absence |

BLOCKING: `tools/sensor-cases.sh:1680-1681` asserts only `assert_contains "$out" "action=filed"`. A fix that filed the item *and* left the `action=escalated (past 3 recurrences)` line in `$out` passes all four assertions, which is exactly the credit the item names as the defect ("`sensors_write` is never credited with a write the tooling refused") and what task 1.1 states ("must not report `escalated`"). Add one assertion after line 1681, e.g. `assert_eq "0" "$(grep -c 'action=escalated' <<< "$out")" "the refused escalation is not reported as written: $out"`.

## 2. Cause or gap scope

Root cause named by the item: the escalation is credited with a write `update.sh` refused. Implemented, and nothing unrelated:

```
-sensors_escalate() { # <row> <number> <finding> <body> <occurrence>
+sensors_escalate() { # … → the writer's status
   sensors_write "escalate #$number (${labels[*]})" \
     bash "$SENSORS_ISSUES_DIR/update.sh" "${args[@]}"
+  code=$?
   rm -f "$bodyfile" "$commentfile"
+  return "$code"
```
```
+    if [ "$status" -eq 0 ]; then
+      sensors_report … "escalated (past $k recurrences)"
+      return 0
+    fi
…
+    if ! sensors_file "$row" "$id" "$finding" "$baseline" "" --allow-duplicate; then
```

The `sensors_file` signature change (writer flags after the body) is the consequence the design names (`create.sh` exit 4 without `--allow-duplicate`), not extra scope. One branch of the fix is beyond the named cause and unsupported by the record — item 8 below.

## 3. Gate reality

PASS — the gate has not run — this round precedes it.

## 4. Tasks claiming done

All seven `[x]` carry a verification. Six hold against the tree: `grep -c 'escalated (past' tools/sensors/run.sh` = 1, `grep -c 'refus' guidelines/BacklogTooling.md` = 1, `bash -n` claims, the suite's `all 82 cases passed`, the red case. Two do not hold as written:

BLOCKING: task **1.1** (`tasks.md:9-14`) is `[x]` on the verification "must file a new item with the same fingerprint **and must not report `escalated`**", but the case asserts no such absence — same finding as item 1, same one-line correction.

NOTE: task **4.2** (`tasks.md:37-39`) and `design.md:76-77` record the mutation as "make `sensors_escalate`'s last command `rm -f …` again", while `evidence.md` records what was actually run: "`sensors_escalate`'s `return "$code"` mutated to `return 0  # REVERT-CHECK MUTATION`". The two mutations share the invariant but are not the same edit; make the two artifacts name the edit the record carries.

## 5. Revert-check

Mutation targets the production invariant: yes — `return 0` in place of the writer's status is precisely "credit a write that did not land". Fails for the right reason: the record quotes the filing assertion, `expected '1', got '0'` under `the returned condition is filed as a new item` (the same message as the red run). Restore clean: no `REVERT-CHECK MUTATION` under `tools/`, `git status --porcelain` empty on the reviewed tree, case `PASS` at `2026-11-05T07:54:26Z`. Graded PASS.

NOTE: the mutated run carries no timestamp of its own — only the bracketing red `07:52:12Z` and green `07:54:26Z`. Item 5 asks for the failure's timestamp; the bracket makes it credible but not recorded.

## 6. Evidence pointers

Every number here is a quoted line, with the command in the record beside it, except the following.

NOTE: `evidence.md` and `proposal.md:57-58` quote `3 files changed, 72 insertions(+), 8 deletions(-)`; the reviewed diff is **74 insertions, 9 deletions** (`grep -c '^+'` = 77 less 3 `+++` headers; `grep -c '^-'` = 12 less 3 `---` headers). Nothing depends on it.

NOTE: `proposal.md:64-65` quotes `grep -c 'escalat' tools/sensors/run.sh tools/sensors/lib.sh tools/sensors/sensors.json` = `6, 3, 3`; on the reviewed tree that command reads **13**, 3, 3 — the change's own added mentions are in the count. The other surface numbers hold exactly (`sensor-cases.sh` 22, 6 fixture registries, `guidelines/Backlog.md` 7).

NOTE: `tests=1 failures=1` (evidence.md, proposal.md, `LOOP-RED-TEST`) is not the output of any command in the record: `red-case.log` carries two `date -u` lines and one `FAIL` line, and the runner prints `sensor-selftest: all <n> cases passed` only on success. The figure is the count of the one case it ran (1 of 1 failed), derivable, not printed.

NOTE: `2026-11-05T07:56:11Z` is the suite run's timestamp but `sensor-selftest.log` carries no time line; only the red run's clock is in the record (`red-case.log`). The archived text would inherit the narrative, not the output.

NOTE: the five side checks (`check-doc-routes` "329 section references … 4 skill references", `check-rule-routes` "578 named rule(s)", `check-todo-references` "66 citations", `check-portability` "56 engines and 7 templates", `check-openspec-hygiene`) are quoted summary lines with no captured log in `evidence/`; they are the change's own quotation of its own runs.

## 7. Spec and guideline honesty

Delta kind: **MODIFIED** — correct. `openspec/specs/backlog-sensors/spec.md:140` already carries "A repeating condition escalates instead of reopening forever", the delta restates the requirement and the three existing scenarios verbatim, and adds one. It states a contract (`SHALL … SHALL NOT …`) and no implementation. The guideline got a rule on the derived §9 hysteresis rule, not a restatement: "An escalation the writer refuses … files the returned condition as a new item under the row's filing bound, and is never reported as an escalation that was not written (#780)."

Per derived rule:
- §9 "a check is a declared row, not a script." — PASS: no row, key or registry value added; the behaviour stays in the engine.
- §9 "identity is finer than the check." — PASS: the filed item carries the condition's own fingerprint, and the design states why the duplicate is the signal ("the settled item records the fix, the new one records that it did not hold").
- §9 "hysteresis in both directions." — PASS: extended by the change, and the extension is what the run needed.
- §9 "the issue is the measurement history." — PASS: unchanged; the new item's comments carry the numbers as any filing's do.
- §9 "one run of one check files a bounded number of condition items" — the filing spends `SENSORS_FILED` correctly, but the branch the change adds when the bound is spent writes nothing and reaches no summary (item 8).
- §9 "a row's first exercise is reported, not spent." — PASS: untouched; the new case runs `--apply` on a throwaway project.
- §10 "an engine names no project value" — PASS by inspection of the hunks: `--allow-duplicate` is another tool's flag, and no repository, author, vocabulary value or profile path is named. Its check's pass is the change's quotation (item 6).

## 8. New overreach

UNPROVEN: "the row's existing `unfiled` bookkeeping already names the condition in that case" (`design.md:23`) — contradicted by the code it describes. `run.sh:541` appends to `SENSORS_UNFILED`, and `run.sh:694` `[ "$SENSORS_FAILED" -eq "$failed_before" ] || return 0` returns before `sensors_summary_outcome` writes anything once the row has failed, so the appended finding reaches no summary and no item.

BLOCKING: `tools/sensors/run.sh:540-545` — the bound-exhausted branch of the escalation path adds a behaviour (fail the row, leave the condition unfiled) that no scenario in the delta states and no case exercises, and the record's only account of it (`design.md:23`) is false as quoted above. Name it in the delta as one scenario with a case (a `fixture registry-row-bound.json`-shaped row whose `maxFilings` is spent before a settled escalation is met), and either write the summary the §9 rule requires for it or drop the inert `SENSORS_UNFILED` append at line 541 and the sentence at `design.md:23`.

UNPROVEN: "A later run finds the new item first (`sensors_issue_for_fingerprint` takes the first issue carrying the marker and `gh issue list` returns newest first), so the returned condition becomes ordinary open work instead of escalating again" (`design.md:64-66`) — no case runs a second tick against the filed item. The claim matches `gh issue list`'s documented newest-first default, but nothing in the record measures it, and if it did not hold every tick would file another duplicate.

---

```
LOOP-BLOCKING: 2
VERDICT: FAIL
```
