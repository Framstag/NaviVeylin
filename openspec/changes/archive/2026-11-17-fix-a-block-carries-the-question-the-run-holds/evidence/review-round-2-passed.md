## Review — issue #960, review round 2 (read-only), tip `26655a8a`, base `6b0e01a7`

Read: `git diff 6b0e01a7..26655a8a` (5 files, +113/−12), `git show`/`git grep` at the tip, `tools/loop/run.sh`
(4290–4340), `tools/loop/tests/loop-cases.sh` (registry `:163-167`, dispatch `:935-939`, `STUB_WORKER_REPORT`
`:1598-1602`, `:5276-5312`, `:11678-11721`), `tools/loop/tests/loop-mutations.sh` (`:280-285`, `:1443-1450`,
`:1640-1745`), `tools/mutate.sh`, `tools/loop/lib.sh` (`loop_tree_identity`, `loop_change_identity`),
`tools/loop/prompts/{reviewer,worker}.md`, `guidelines/Backlog.md` §5/§14, `guidelines/Backlog.recipes.md` §7d,
`openspec/specs/agent-issue-loop/spec.md:566-571`, the four change artifacts, `.pi/issue-loop/items/960/*`,
`.pi/issue-loop/items/954/*`, `bash tools/check-rule-routes.sh --diff 6b0e01a7..26655a8a` (exit 0, 27 entries) and
`openspec validate fix-a-block-carries-the-question-the-run-holds --strict` (valid). No suite was run. The tracked
tree is clean at the tip (`git diff 26655a8a` empty; `git status --porcelain` shows only two untracked, non-ignored
build directories, `architecture/bin/` and `osmscout-client-java/bin/`, created 19:25).

---

## Step 1 — round 1's findings, checked against the tree

**Round-1 BLOCKING 1 (the precedence scenario was UNPROVEN) — CLOSED.** The delta's third scenario is now exercised
by its own case, and both reports carry a decision at once:

- `specs/agent-issue-loop/spec.md`, "A review round's question is preferred to the worker's", `**Case**:
  run#aReviewersDecisionOutranksTheWorkers`.
- `loop-cases.sh:11699` writes both: the worker report carries `LOOP-DECISION: the workers fork: ship it as it stands,
  or rework the surface | recommended: rework the surface` (`:11709`) and the reviewer report carries
  `LOOP-DECISION: the reviewers fork: apply the corrections, or abandon the item | recommended: apply the
  corrections` (`:11712`), with `--review on` (`:11713`).
- The three assertions discriminate all three readings: `assert_contains "$log" 'apply the corrections, or abandon the
  item'` (`:11715`), `assert_not_contains "$log" "the workers fork"` (`:11717`), `assert_not_contains "$log" "retry the
  item, or abandon it"` (`:11719`). "Worker wins" fails the first, "concatenate both" fails the second. Round 1's
  objection (the worker's side was empty, so the case passed with the precedence line absent) is gone.
- Green at the tip: `PASS run#aReviewersDecisionOutranksTheWorkers` (`gate-2.txt:487`, log mtime 19:40) and
  `PASS run#aReviewersDecisionOutranksTheWorkers` in `case-s6.log` (mtime 19:17).
- Red under `29.6` (`loop-mutations.sh:284`), which makes the line `decision_field="$worker_decision"` unconditional:
  `RED    29.6  run#aReviewersDecisionOutranksTheWorkers … 2026-11-17T18:17:55Z — the escalation quotes the decision
  the review round worked out: 'apply the corrections, or abandon the item' not found in: …`, then
  `restore 29.6 … green again` (`mutation-29.6.log`).

**Round-1 BLOCKING 2 (the gate measured a tree that was not the reviewed commit's) — CLOSED.** `gate-2.txt:5`
`LOOP-GATE-TREE: head=26655a8a6bbe1c0e6958078b6affa62ba6ad4e2d diff=3dd7ff006c7a767c`, `:4` `LOOP-GATE-BASE:
6b0e01a7 (from --base)`. The head is the tip under review; the selection is the tip's own file set
(`gate-2.txt:13`: `selected 481 of 481 cases (files: guidelines/Backlog.md tools/loop/prompts/reviewer.md
tools/loop/run.sh tools/loop/tests/loop-cases.sh tools/loop/tests/loop-mutations.sh)`); 481 is 480 (round 1's tree)
plus the one case `26655a8a` adds; `:1262` `all 481 cases passed … 1075.08 s`; `:1266` `loop-mutations: all 416 cases
passed — every selected record applies` (415 + `29.6`); `:1804` `LOOP-GATE: tests=1180 failures=0 errors=0`. The
change-directory mtimes (design 18:14, spec 18:13, proposal/tasks 19:18) all precede the gate run (19:2x–19:40), so
nothing was corrected after the gate. The one thing I cannot do is re-derive `diff=3dd7ff006c7a767c` byte for byte:
`loop_tree_identity` hashes untracked non-ignored paths too (`lib.sh:548-556`), and the two build directories above
appeared at 19:25, i.e. after `gate.sh` writes its identity line and before the gate ended. The recorded digest is
therefore of the committed tree; a re-derivation today would not be.

**Round-1 BLOCKING 3 (#954's test half was red on the commit under review) — CLOSED.** The clause those assertions
read is committed on the branch: `tools/loop/prompts/reviewer.md:60-68` (`**This round is non-interactive: there is
nobody to ask and nothing to confirm.**` … `an answer that is a question is a cut-short answer`, issue #954). The two
cases that assert it pass (`gate-2.txt` carries `PASS prompt#reviewerCarriesTheEightItems` and `PASS
prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle`), and record `954.1` now matches its file — `:1266` reports
no `MUTATION-DID-NOT-APPLY`. #954 carries its own evidence too:
`.pi/issue-loop/items/954/{red-on-HEAD.log, green-on-fix.log, green-on-HEAD-41dc62e3.log, mutation-954.1.log,
evidence-check.txt}`.

**Round-1 NOTEs:**

- `tasks.md` 1.3 (fixture sentence) — **CLOSED**. `tasks.md:16-19` now says the fixtures are unchanged and names the
  verdict correctly; `STUB_WORKER_REPORT` is `LOOP-VERDICT: closed` (`loop-cases.sh:1598`) and that case's reviewer
  report is `'VERDICT: PASS'` (`:5282`), so neither report carries `LOOP-DECISION`.
- `tasks.md` 2.2 (scenario count) — **CLOSED**. `tasks.md:32-33` says "three scenarios"; the delta holds three
  (`grep -c '^#### Scenario:'` = 3).
- `tasks.md` 3.1 / 3.3 (numbers with no log) — **OPEN** (item 4 and the findings below). 3.1 gained
  `case-s6.log`; 3.3 is unchanged.
- The #894 claim with no pointer — **PARTLY CLOSED**. `proposal.md:17` now cites
  `issues/894#issuecomment-6950362496`, but no tree artifact carries that comment's text (finding N7).
- The guideline's "when the block ended before any round" condition — **OPEN** (finding N4).
- The third scenario duplicating the shipped `An escalation names the decision` — **OPEN** (finding N5).

---

## Step 2 — the eight items

### 1. Scenario coverage

| delta scenario | grade | asserting case, assertion, timestamp |
|---|---|---|
| A block before any review round carries the worker's question | **PASS** | `run#aBlockedItemCarriesTheWorkersDecision` (`loop-cases.sh:5296-5312`, registered `:165`, dispatched `:937`): `assert_contains "$body" 'land the writer alone and file the conversion'` (`:5309`) and `assert_not_contains "$body" 'retry the item, or abandon it'` (`:5310`), on `--review off` (`:5306`). `PASS run#aBlockedItemCarriesTheWorkersDecision` at `gate-2.txt:486` (mtime 2026-11-17 19:40) and `full-suite.txt:473` (mtime 18:49). |
| A block that holds no question states its own | **PASS** | `run#blockedItemAsksItsOwnQuestion` (`loop-cases.sh:5276-5286`): the two-way sentence on `STUB_WORKER_REPORT` + reviewer `'VERDICT: PASS'`, neither carrying a decision. `PASS run#blockedItemAsksItsOwnQuestion` at `gate-2.txt:485`, `full-suite.txt:472`. |
| A review round's question is preferred to the worker's | **PASS** | `run#aReviewersDecisionOutranksTheWorkers` (`loop-cases.sh:11699-11721`, registered `:166`, dispatched `:938`): both decisions set, `assert_contains` the reviewer's (`:11715`), `assert_not_contains` the worker's (`:11717`) and the run's own sentence (`:11719`). `PASS` at `gate-2.txt:487` (19:40) and in `case-s6.log` (mtime 19:17); red under `29.6` at `2026-11-17T18:17:55Z`. |

No scenario is supported by a different claim. The one evidence weakness is that no log carries a per-case timestamp
(finding N3); the timestamps above are file mtimes plus `29.6`'s own stamp.

### 2. Cause or gap scope

The named cause is implemented, once, at the seam the proposal names: `run.sh:4304` reads the reviewer's
`LOOP-DECISION`, `run.sh:4310` `[ -n "$decision_field" ] || decision_field="$worker_decision"`, and `run.sh:4323`
keeps the run's own sentence as the last resort. `worker_decision` is assigned in the same scope (`run.sh:3371`,
re-read after the `answer-empty` retry at `:3427`) and already trusted by the skip arm (`:4197`) — a read of a value
in hand, not a second report read. The cited contract is real: `prompts/worker.md:328-331` requires `LOOP-DECISION`
for "a skip on `needs-decision`, `needs-diagnosis` or `stale`, or a blocked item".

In scope: `run.sh` (that hunk), `guidelines/Backlog.md:205-212` (the one rule), `loop-cases.sh:165-166`, `:937-938`,
`:5289-5295` (neighbour comment), `:5296-5312`, `:11693-11721`, and `loop-mutations.sh:283-284`.

Not this item's cause — issue #954's half, riding the same branch: `prompts/reviewer.md:60-68`, `loop-cases.sh:4665`
(`# files:`), `:4688-4696` (three assertions), `:7098` (`# files:`), `:7118-7123` (two assertions), and
`loop-mutations.sh:1447` (record `954.1`). See finding N6 for what that does to the branch's evidence.

### 3. Gate reality

A real execution for this tree, not cached: `LOOP-GATE-TREE: head=26655a8a… diff=3dd7ff006c7a767c`
(`gate-2.txt:5`), `LOOP-GATE-BASE: 6b0e01a7` (`:4`), `loop-selftest: all 481 cases passed (JDK 21, offline, no token,
no sandbox) — 1075.08 s, selection: --from-git 6b0e01a7` (`:1262`), `loop-mutations: all 416 cases passed` (`:1266`),
`LOOP-GATE: tests=1180 failures=0 errors=0 (… loop-selftest.sh --from-git 6b0e01a7 1076s, loop-mutations.sh --check
12s, …)` (`:1804`). No `FAIL` line anywhere in the file (`grep -c '^FAIL' gate-2.txt` = 0). `head` equals the commit
under review and the tracked tree at that commit is what the working tree holds. Limitation of this round, not of the
change: the item's directory carries no `*.identity` artifact for a diff (`find .pi -name '*.identity'` finds one for
items 883/909/911/916/917/950 and none for 960), so the gate-vs-diff comparison rests on the gate line, `head`, the
clean tracked tree and the case-count arithmetic (480 → 481) rather than on a recorded `diff=` pair.

### 4. Tasks claiming done

- 1.1 ✓ (`run.sh:4310`). 1.2 ✓ (`loop-cases.sh:5296-5312`, `# files:` line present). 1.3 ✓ (verified above). 1.4 ✓
  (`mutation-29.3.log`, `2026-11-17T18:17:32Z`, red then restored green). 1.5 ✓ (case + `29.6`, `case-s6.log`,
  `mutation-29.6.log`).
- 2.1 ✓ — the rule names the order and both cases; `tools/check-doc-routes.sh` is green inside the gate (`:1804`, 1s).
  2.2 ✓ — three scenarios, each naming its case.
- 3.1 **holds on a thinner log than it reads**: the third case has `case-s6.log`; the two neighbours have no focused
  log, only their whole-suite `PASS` lines; "all PASS (2026-11-17)" has no timestamp inside any log (N3).
- 3.2 **does not describe this tree.** `tasks.md:40-42` claims "**480 cases passed, 887.22 s** … Re-taken after review
  round 1's corrections, at the branch tip", citing `full-suite.txt`. That file is 480 cases (it holds
  `PASS run#aBlockedItemCarriesTheWorkersDecision` at `:473` and no `run#aReviewersDecisionOutranksTheWorkers`), mtime
  18:49 — before `41dc62e3` (18:55) and before the case reached the tree (19:16). The tip's suite is
  `gate-2.txt:1262`: **481 cases, 1075.08 s**. The `[x]`'s fact is true and its pointer is stale (finding N1).
- 3.3 **not supported by any command in the record**: "all 185 selected records apply" has no log, and the item's only
  `--check` output is the unscoped `all 416 cases passed` (`gate-2.txt:1266`); `--only 29.1` has no log either
  (only `29.3` and `29.6` do) (finding N2).
- 3.4 correctly left `[ ]`.

### 5. Revert-check

- `29.3` (`loop-mutations.sh:283`) targets the production file with an anchored, unique search and turns the read into
  `      :`; red for the right reason, quoting the record's own wrong content: `2026-11-17T18:17:32Z — the escalation
  carries the question the worker wrote: 'land the writer alone and file the conversion' not found in: gh issue
  comment 21 --body $'the conversion is 170 sites and one child cannot finish them — the item\'s verdict is blocked…
  (fallback sentence)`, then `restore 29.3 … green again`.
- `29.6` (`loop-mutations.sh:284`) targets the same line and makes the fallback unconditional; red and restored
  (`2026-11-17T18:17:55Z`). It is recorded against the precedence case — the right case. The log's excerpt cannot show
  the worker's text, because `case_reason` cuts at the first line and 240 characters (`loop-mutations.sh:1653-1661`);
  the proof that the precedence case fails for the right reason is the assertion the message names plus the same
  case's green on the unmutated tree (finding N10).
- Both records land once (anchored, unique patterns) and the restore half is the harness's own `backup`/`cmp`
  (`loop-mutations.sh:1745-1751`); no mutation marker is left in `run.sh` in the reviewed tree (the file's only
  occurrence of `decision_field="$worker_decision"` is at `:4310`).

### 6. Evidence pointers

Produced by a command the record carries: `481 cases passed … 1075.08 s`, `all 416 cases passed`, `tests=1180
failures=0 errors=0` and the per-step seconds, `head=26655a8a… diff=3dd7ff006c7a767c`, `LOOP-GATE-BASE: 6b0e01a7`,
every `PASS <case>` line, and `29.3`/`29.6`'s `RED … / restore … green again` pairs.

Numbers with no producing command in the record: `480 cases passed, 887.22 s` **as a tip run** (N1); `all 185 selected
records apply` and the two `--only` runs (N2); `all three PASS (2026-11-17)` (N3); `13 items on 2026-11-17 … 8 of 18 on
2026-11-05` (N8); the #894 escalation sentence (N7). Nothing in the change's contract, a case or an assertion depends
on any of them. The evidence is in `.pi/issue-loop/items/960/`, not in a build directory, so it is not fragile; the one
pointer that is fragile is the tracker comment id.

### 7. Spec and guideline honesty

Reading set (`bash tools/check-rule-routes.sh --diff 6b0e01a7..26655a8a`, exit 0): `Backlog.md` §1, §3 (+4 rules),
§4 (+1), §5 (+8 rules), §7, §14 (+1), `BacklogTooling.md` §9, `BuildGate.md` §2a. Graded per rule name:

- **`Backlog.md` §5 "a block asks its own question."** — touched, and it is a rule, not a restatement: which source
  answers, what the last resort is, the measurement and both case names (`Backlog.md:205-212`). **PASS**, with N4
  (the round-ran condition) and N11 ("filed as #960").
- **`Backlog.md` §14 "a reversal is recorded, not deleted"** — the old clause is replaced in place, and the rule names
  both issues that own the two halves (#225 for the sentence, #960 for the order), so nothing is deleted without a
  record. **PASS.**
- **§1, §3's four rules, §4's rule, §7, `BacklogTooling.md` §9, `BuildGate.md` §2a** — derived through the touched
  paths (`guidelines/Backlog.md`, `tools/loop/**`); the diff changes no claim any of them states, and the iteration
  protocol §2a is what the round-1 corrections followed (focused run, then one gate). **PASS**, no finding.
- **ADDED vs MODIFIED** — `ADDED` is defensible: the shipped `An escalation names the decision`
  (`openspec/specs/agent-issue-loop/spec.md:566-571`) is scoped to "after a failed review" and still holds word for
  word. But the delta's third scenario restates that scenario's claim from the other side, so one fact now has two
  owners (N5). Requirement body is 274 characters, well inside the 500-character rule; `openspec validate --strict`
  is green.
- **Contract, not implementation** — the requirement states what a block SHALL carry; the `[ -n … ] ||` line appears
  only in `design.md`. **PASS.** The artifacts add `guidelines/Build.md` §2 (the scope block) beyond the derived set,
  which is a legitimate addition, not a substitution.

### 8. New overreach

- UNPROVEN: "`tools/issues/decisions.sh` listed 13 items on 2026-11-17, one of them carrying that sentence; the run's
  own census on 2026-11-05 read 8 of 18" (`proposal.md:20-21`) — no command output in the record, and no run record
  carries the figure (N8).
- UNPROVEN: "all 185 selected records apply" and "`--only 29.3` and `--only 29.1` red for the right reason, restored,
  green again" (`tasks.md:43-45`) — the 185 has no log and `29.1` has none (N2).
- UNPROVEN: "**480 cases passed, 887.22 s** … Re-taken after review round 1's corrections, at the branch tip"
  (`tasks.md:40-42`) — the cited log predates the corrections and lacks the new case (N1).
- UNPROVEN: "all PASS (2026-11-17)" as a dated focused run for all three cases (`tasks.md:37-39`) — one log, no
  timestamp in it, and no focused log for the two neighbours (N3).
- UNPROVEN: "the item was escalated with the run's own retry/abandon sentence" for #894 (`proposal.md:16-17`,
  `guidelines/Backlog.md:208-209`) — the pointer is a tracker comment id that no tree artifact holds (N7).
- UNPROVEN: the scope and surface counts, "`tools/loop/run.sh`, its self-test and its mutation set, and the one rule in
  `guidelines/Backlog.md`" and "1 new case plus 1 rewritten fixture and 1 mutation record" (`proposal.md:37`,
  `proposal.md:59-63`) — the diff carries two new cases, two mutation records, no rewritten fixture, and #954's
  `reviewer.md` (N6).

---

## Findings

NOTE: N1 — `tasks.md:40-42` states "**480 cases passed, 887.22 s** … Re-taken after review round 1's corrections, at the
branch tip" over `.pi/issue-loop/items/960/full-suite.txt`. That file is 480 cases with no
`run#aReviewersDecisionOutranksTheWorkers` and mtime 18:49, i.e. before `41dc62e3` (18:55) and before the case reached
the tree (19:16); the tip's suite is `gate-2.txt:1262` — 481 cases, 1075.08 s. The `[x]`'s fact holds, its pointer and
its number do not. Smallest fix: point 3.2 at `gate-2.txt` and quote 481 / 1075.08 s (no new run needed).
NOTE: N2 — `tasks.md:43-45`'s "all 185 selected records apply" has no producing command in the item's directory; the only
`--check` output is the unscoped `loop-mutations: all 416 cases passed` (`gate-2.txt:1266`), and `--only 29.1` has no
log although 3.3 names it. Smallest fix: quote the run `--check` actually did (all 416) and drop the `29.1` half or
record its log.
NOTE: N3 — No log in `.pi/issue-loop/items/960/` carries a per-case timestamp. `case-s6.log` is one line,
`PASS run#aReviewersDecisionOutranksTheWorkers`, and the two neighbours have only their whole-suite lines
(`gate-2.txt:485-486`, `full-suite.txt:472-473`), so `tasks.md:37-39`'s "all PASS (2026-11-17)" rests on file mtimes.
Item 1's timestamp column rests on the same mtimes. Smallest fix: cite `gate-2.txt:485-487` with its mtime and drop the
dated claim, or keep a `--case` log that prints its stamp.
NOTE: N4 — The requirement's first sentence and `guidelines/Backlog.md:205-206` state the *round-ran* discriminator ("the
reviewer's `LOOP-DECISION` when a review round ran, and the worker's `LOOP-DECISION` when it did not"), while
`run.sh:4304-4310` discriminates on *whether the reviewer's report carries one*, and the delta's own second sentence
("only when no report in its hand carries a question") uses the report condition — so the requirement contradicts
itself in the one reachable state the change does not ship a case for (a round ran, its report carries no decision,
the worker's does; `design.md` D1 chose that fall-through deliberately). No case, assertion or number depends on it.
Smallest fix: one clause in both places — "the reviewer's `LOOP-DECISION` when its report carries one, the worker's
otherwise", with the run's sentence only when neither does.
NOTE: N5 — The delta still ships its precedence claim as a third scenario of an `ADDED` requirement, restating the shipped
`An escalation names the decision` (`openspec/specs/agent-issue-loop/spec.md:566-571`), so one fact has two owners
(carried from round 1, unchanged). Smallest fix: cover it with a `MODIFIED` block on the shipped requirement, or note
in `design.md` why the two scenarios are separate claims.
NOTE: N6 — The branch carries two items and this item's artifacts do not say so. `41dc62e3` (#954) rides
`fix/issue-960-a-block-carries-the-question-the-run-holds`, so the range under review includes #954's
`tools/loop/prompts/reviewer.md:60-68`, its four case-assertion blocks and record `954.1`; `proposal.md:37` ("Scope
`tools/loop/run.sh`, its self-test and its mutation set, and the one rule in `guidelines/Backlog.md`") and
`proposal.md:59-63` ("1 new case plus 1 rewritten fixture and 1 mutation record") are false of that diff (two new
cases, two mutation records, no rewritten fixture, plus #954's file). The disclosure exists only in `41dc62e3`'s
subject and body and in `.pi/issue-loop/items/954/`. Consequence for the evidence: it belongs to **two items** —
`gate-2.txt:13` selects `tools/loop/prompts/reviewer.md` (a file only #954 touches) beside `guidelines/Backlog.md` and
`tools/loop/run.sh`, and `:1262` (481/481), `:1266` (416/416) and `:1804` (tests=1180) are branch-level; #960's own
share is `gate-2.txt:485-487` and records `29.3`/`29.6`. This is the pair round 1's remedy allowed ("if the branch
lands as a pair, review the tip"), and #954 has its own evidence directory, so the tree is coherent — but a
merge/closure reader of this item cannot tell that #960's merge also lands #954's fix. Smallest fix: one sentence in
`proposal.md`'s Scope and one in `tasks.md` naming `41dc62e3`/#954 and saying the gate record covers both.
NOTE: N7 — The #894 measurement now has a pointer but no tree evidence: `proposal.md:16-17` cites
`issues/894#issuecomment-6950362496`, and no artifact under `.pi/` carries that comment (the id appears nowhere in the
tree but `proposal.md`); `.pi/issue-loop/run.json`'s item 894 record shows `result: "blocked"`, `reviewRounds: 0`,
`reviewVerdict: "off"`, `escalatedLabel: "agent/needs-decision"`, which is consistent with the claim and is not the
comment's text. `guidelines/Backlog.md:208` states the same measurement with no pointer at all. Smallest fix: cite
`run.json`'s item record as the pointer, or quote the comment.
NOTE: N8 — `proposal.md:20-21`'s "13 items on 2026-11-17, one of them carrying that sentence; the run's own census on
2026-11-05 read 8 of 18" is carried from round 1 with no command output (carried, unchanged). Smallest fix: cite the
`decisions.sh` output's log, or drop the counts — nothing depends on them.
NOTE: N9 — `design.md` D3 is stale after round 1: it says "The invariant is held by one case and one mutation" and that
`run#aBlockedItemCarriesTheWorkersDecision` "is the only case that can see the difference" (`design.md:36-41`, mtime
18:14), while the tree now holds two cases and two records (`tasks.md:22-26`, `29.6`). Smallest fix: add one line to
D3 naming the precedence case and `29.6` as the other half.
NOTE: N10 — `mutation-29.6.log`'s red message cannot show the wrong content it proves: `case_reason` cuts the case's message
at its first line and 240 characters (`loop-mutations.sh:1653-1661`), so the excerpt ends at the record's first line
(`gh issue edit 21 --add-label agent/in-progress`) and the worker's text never appears in it. The record still applies
to the right file, targets the right assertion and restores green. Smallest fix: none owed; if a fuller red is wanted,
assert the absence first in a separate case.
NOTE: N11 — `guidelines/Backlog.md:210-211`'s "(issue #225, filed as #960)" reads as though #225 and #960 were one item,
where #225 owns the two-way sentence and #960 is the item that re-opened the source order. Smallest fix: "(issues
#225 and #960; cases …)".

BLOCKING: 0
VERDICT: PASS
