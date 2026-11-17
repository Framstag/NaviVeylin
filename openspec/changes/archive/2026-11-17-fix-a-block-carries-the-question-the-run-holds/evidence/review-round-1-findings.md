## Review — issue #960, commit `b08c26b7` on base `6b0e01a7` (read-only round 1)

Read: `git diff 6b0e01a7..b08c26b7`, `git show`/`git grep` at both commits, `tools/loop/run.sh` (blocked arm),
`tools/loop/tests/loop-cases.sh`, `tools/loop/tests/loop-mutations.sh`, `tools/mutate.sh`,
`tools/loop/gate.sh` + `tools/loop/lib.sh` (`loop_tree_identity`), `tools/loop/prompts/worker.md`,
`guidelines/Backlog.md`, the four change artifacts, `.pi/issue-loop/items/960/{full-suite.txt,gate.txt}`, and
`bash tools/check-rule-routes.sh --diff 6b0e01a7..b08c26b7` (exit 0). No suite was run and the tree was not touched.

Correct (what is good):
- The production hunk is the named cause and nothing else: `tools/loop/run.sh:4310`
  `[ -n "$decision_field" ] || decision_field="$worker_decision"`, added directly under the reviewer's read at
  `run.sh:4304`, with the fallback sentence kept at `run.sh:4323`. `worker_decision` is assigned unconditionally in
  the same scope (`run.sh:3371`, re-read after the `answer-empty` retry at `run.sh:3427`), so the fallback is a read
  of the value the skip arm already trusts, not a second report read.
- The cited contract is real: `tools/loop/prompts/worker.md:328-331` requires `LOOP-DECISION` for "a skip on
  `needs-decision`, `needs-diagnosis` or `stale`, or a blocked item", which is what `run.sh:4304-4309` cites.
- The rule landed in `guidelines/Backlog.md:205-212` is a contract (who answers, and what the last resort is), not a
  restatement of the code, and it carries both case names.

### 1. Scenario coverage

| delta scenario | grade | asserting case, assertion, timestamp |
|---|---|---|
| `A block before any review round carries the worker's question` | **PASS** | `run#aBlockedItemCarriesTheWorkersDecision` (`loop-cases.sh:5294-5310`, registered `:936`): `assert_contains "$body" 'land the writer alone and file the conversion'` (`:5306`) and `assert_not_contains "$body" 'retry the item, or abandon it'` (`:5308`), on a `--review off` run. `PASS run#aBlockedItemCarriesTheWorkersDecision` at `full-suite.txt:473` (file mtime 2026-11-17 18:49) and `gate.txt:481` (mtime 19:13) — see item 3 for the tree those two runs measured. |
| `A block that holds no question states its own` | **PASS** | `run#blockedItemAsksItsOwnQuestion` (`loop-cases.sh:5274-5287`): `assert_contains "$body" 'retry'` / `'abandon'` on `STUB_WORKER_REPORT` (`:1596-1600`), which carries no `LOOP-DECISION`, with the reviewer report `'VERDICT: PASS'` (no decision either). `PASS run#blockedItemAsksItsOwnQuestion` at `full-suite.txt:472`, `gate.txt:480`. |
| `A review round's question is preferred to the worker's` | **UNPROVEN** | The delta names `run#escalationCarriesTheDecision`, and that case cannot see the claim: `loop-cases.sh:11682-11686` writes the reviewer report with `LOOP-DECISION: merge as it stands, apply the corrections, or abandon it …` over `STUB_WORKER_REPORT`, which carries **no** `LOOP-DECISION`. With the worker's side empty the case passes under either precedence. The change's own design admits it: `design.md` D3 — "the other two escalation cases pass with the line deleted (`run#escalationCarriesTheDecision` quotes a reviewer, and `run#blockedItemAsksItsOwnQuestion`'s report holds no decision)". No case sets both reports and asserts the reviewer's wins. |

No artifact in this item's directory carries a per-case timestamp; only the two files' mtimes do (18:49, 19:13),
which is what the table quotes.

### 2. Cause or gap scope

The named cause is implemented, and two of the four touched files carry work that is not this cause.

- In scope: `run.sh:4304-4310` (one line) plus its comment; `guidelines/Backlog.md:205-212` (the one rule);
  `loop-cases.sh:162/:936` (registry + dispatch), `loop-cases.sh:5268-5271` (the neighbour's comment) and
  `loop-cases.sh:5282-5310` (the new case); `loop-mutations.sh:283` (`29.3`).
- Not this cause — issue #954's test half, committed whole with the two shared test files:
  - `loop-cases.sh:4666` and `:7096`: `tools/loop/prompts/reviewer.md` added to the two cases' `# files:` lists.
  - `loop-cases.sh:4686-4695`: the three assertions `non-interactive`, `never ask a question`,
    `an answer that is a question is a cut-short answer`.
  - `loop-cases.sh:7116-7122`: `assert_contains "$text" 'non-interactive'` and `'never ask a question'`.
  - `loop-mutations.sh:1443-1447`: record `954.1` over `tools/loop/prompts/reviewer.md`.

That foreign half is not merely extra: on this commit it is red. `git grep` at `b08c26b7` finds
`never ask a question` only in `loop-cases.sh:4692`, `:7120` and `loop-mutations.sh:1446` — the clause it asserts is
not in `tools/loop/prompts/reviewer.md` at `b08c26b7` (`git show b08c26b7:tools/loop/prompts/reviewer.md` has no
`non-interactive`, no `never ask a question`); it is added by the *child* commit `41dc62e3` ("fix:
the-review-round-is-non-interactive (#954)"), whose message states the debt: "The case assertions … and the 954.1
mutation record landed inside b08c26b7 (#960), which committed the two shared test files whole". On `b08c26b7` alone
`prompt#reviewerCarriesTheEightItems` and `prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle` fail their new
assertions, and `tools/loop/tests/loop-mutations.sh --check` fails because `954.1`'s search matches nothing
(`tools/mutate.sh:195-199` counts that as `MUTATION-DID-NOT-APPLY` and `failed++`; `loop-mutations.sh:34` documents
exit 1 for it).

### 3. Gate reality

The execution is real and not cached — `LOOP-GATE: tests=1178 failures=0 errors=0 (… loop-selftest.sh --from-git
6b0e01a7 1016s, loop-mutations.sh --check 12s, … loop-mutations-selftest.sh 103s …)` (`gate.txt:1803`), and the
manual suite's own tail reads `loop-selftest: all 480 cases passed (JDK 21, offline, no token, no sandbox) — 887.22 s`
(`full-suite.txt`, 480 `PASS` lines, 0 `FAIL`). `LOOP-GATE-TREE: head=b08c26b795cb1549db331cabab3af491da307d11
diff=f8a68fe3b8a30df0`, `LOOP-GATE-BASE: 6b0e01a7 (from --base)`.

But the tree those two runs measured is not this commit's tree, and the logs say so in their own first lines:

- `gate.txt:13`: `loop-selftest: selected 480 of 480 cases (files: guidelines/Backlog.md tools/loop/prompts/reviewer.md tools/loop/run.sh tools/loop/tests/loop-cases.sh tools/loop/tests/loop-mutations.sh)` — five files, including `tools/loop/prompts/reviewer.md`, which this diff does not touch (the diff's four files are listed in §2). The gate derives that list from `git diff --name-only "$base"` (commit → *working tree*), so the uncommitted reviewer.md edit was part of the measured tree.
- `gate.txt:1265`: `loop-mutations: all 415 cases passed — every selected record applies to the file it names` — impossible at `b08c26b7`, where `954.1`'s search string does not exist.
- `full-suite.txt:442` and `:633`: `PASS prompt#reviewerCarriesTheEightItems` and
  `PASS prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle` — the two cases whose new assertions read
  reviewer.md text that only exists from `41dc62e3` on (mtime 18:49, i.e. before that commit was made at 18:55).

So: `head` matches the commit under review (`b08c26b7…`), the tree does **not** — the evidence's green belongs to
`b08c26b7`'s content *plus* #954's uncommitted `reviewer.md` clause, which is what `41dc62e3` later committed. The
branch tip's content is what was measured; the reviewed commit's content was not.

### 4. Tasks claiming done

- 1.1, 1.2, 2.1, 2.2, 3.2 hold: the line is at `run.sh:4310`; the case and its `# files:` declaration match
  `loop-cases.sh:5294-5295`; `tools/check-doc-routes.sh` is a green gate step (`gate.txt:1803`); the delta declares
  the requirement; `480 cases passed … 887.22 s` matches `full-suite.txt` exactly.
- 1.3's sentence does not describe this tree: "`run#blockedItemAsksItsOwnQuestion`'s fixture now states a blocked
  verdict with no `LOOP-DECISION`". That case's fixture is `STUB_WORKER_REPORT` (`loop-cases.sh:1596`),
  `LOOP-VERDICT: closed` — not `blocked` — and the diff does not change it (the hunk at `:5268-5271` touches only
  the comment). The property the task needs (no `LOOP-DECISION` in either report of that case) does hold, and the
  case passes (`full-suite.txt:472`), so the tick stands on a wrong sentence rather than on a wrong result.
- 2.2 counts "its two scenarios"; the delta ships three (`specs/agent-issue-loop/spec.md`, `#### Scenario:` ×3), and
  the third is the UNPROVEN one in item 1.
- 3.1's "all three PASS (2026-11-17)" and 3.3's "all 185 selected records apply" have no artifact in this item's
  directory (see item 6). 3.3's `--files "tools/loop/run.sh tools/loop/state.sh"` scoping is also why the inapplicable
  `954.1` did not surface in the focused run: reviewer.md is outside that filter.
- 3.4 is correctly left `[ ]`.

### 5. Revert-check

`loop-mutations.sh:283`:

```
'29.3@-@tools/loop/run.sh@run#aBlockedItemCarriesTheWorkersDecision@the worker decision read beside the reviewer one (issue #960)@s#^      \[ -n "$decision_field" \] || decision_field="$worker_decision"$#      :#'
```

- Targets the production invariant: the file is `tools/loop/run.sh` (not the test), the search is anchored (`^`)
  and carries the six-space indent of `run.sh:4310`, and the replacement `      :` makes the line a no-op, leaving
  `decision_field` empty → the `run.sh:4323` fallback sentence → `run#aBlockedItemCarriesTheWorkersDecision`'s first
  assertion (`'land the writer alone and file the conversion'`) fails. Red for the right reason, and for the cause the
  record names.
- It lands once: the pattern is anchored and unique in `run.sh`.
- Restore is the harness's own (`tools/mutate.sh` copies `.orig`, `mv`s the mutated file back and `cmp`s it), so the
  "restore" half is mechanical rather than a claim.
- Neighbouring record `29.1` (`loop-mutations.sh:281`) is the other arm of the same case family — it mutates the
  fallback sentence `**The decision:** retry the item, or abandon it …` to `:`, which is what
  `run#blockedItemAsksItsOwnQuestion` asserts — so the pair holds both halves: 29.3 the read, 29.1 the fallback.
- What the record does not carry is its run: `--only 29.3` / `--only 29.1` red-and-restored is prose in `tasks.md`
  3.3 with no log in this item's directory (item 6).

### 6. Evidence pointers

Numbers that are the output of a command in the record: `480 cases passed … 887.22 s` (`full-suite.txt` tail),
`tests=1178 failures=0 errors=0` and the per-step seconds (`gate.txt:1803`), `all 415 cases passed` (`gate.txt:1265`),
`head=b08c26b7… diff=f8a68fe3b8a30df0`, `LOOP-GATE-BASE: 6b0e01a7`, and each `PASS <case>` line.

Numbers with no producing command in the record:

- `tasks.md` 3.3: "all 185 selected records apply" — no log; the only `--check` output present is the unscoped
  `all 415 cases passed`. No case or assertion depends on the figure.
- `tasks.md` 3.1: "all three PASS (2026-11-17)" — the PASS lines exist, the date has no command behind it and no
  per-case timestamp anywhere in the artifact.
- `proposal.md` "Why": "`tools/issues/decisions.sh` listed 13 items on 2026-11-17, one of them carrying that sentence;
  the run's own census on 2026-11-05 read 8 of 18" — no command output in the record.
- `proposal.md`/`guidelines/Backlog.md:208-209` "the defect measured on #894 (2026-11-17): three options and a
  recommendation sat in the child's evidence directory" — the first half is verifiable in the tree
  (`.pi/issue-loop/items/894/worker-continuation-report.md:25-26`: `LOOP-VERDICT: blocked` plus a `LOOP-DECISION`
  with three options and a recommendation); that #894's *escalation* then read "retry the item, or abandon it" is not
  in the tree — the same sentence does appear on item #916's recorded decision (`.pi/issue-loop/items/916/`), so the
  class is real, but the #894 instance is unproven here.
- `proposal.md` "`answer.sh` quotes the last comment's question" — verified: `tools/issues/answer.sh:91-95` reads the
  question as "the last comment's first non-empty line".

### 7. Spec and guideline honesty

`bash tools/check-rule-routes.sh --diff 6b0e01a7..b08c26b7` (exit 0) derives the reading set, headed by
`Backlog.md §5 "a block asks its own question."` — the rule the diff rewrites — beside `Backlog.md §1/§3/§4/§5/§7`,
`BacklogTooling.md §9`, `BuildGate.md §2a`. Graded per rule name: only §5 "a block asks its own question" is
touched, and it is a rule, not a restatement (it says which source answers and what the last resort is, with the
measurement and both cases). The remaining names in the set are the derivation of the path, unchanged by the diff.

- Kind: `ADDED` is defensible — the shipped `An escalation names the decision`
  (`openspec/specs/agent-issue-loop/spec.md:566-571`) is scoped to "after a failed review" and still holds word for
  word — but the delta's third scenario restates that shipped scenario's claim from the other side, so one fact now
  has two owners. NOTE below.
- Contract, not implementation: the requirement's body (≈300 characters) states what a block SHALL carry; the
  `[ -n … ] ||` line stays in the code and only appears in `design.md`.
- One three-way disagreement between the artifacts and the code: the guideline (`Backlog.md:205-206`, "the
  worker's when the block ended before any round") and the delta's first sentence ("the worker's `LOOP-DECISION`
  when it did not") both state a *round-ran* condition. The code tests a *report-carries-it* condition
  (`run.sh:4304-4310`), and the delta's own second sentence says so ("only when no report in its hand carries a
  question"). `design.md` D1 chooses the fall-through deliberately, so a block whose round ran and left no
  `LOOP-DECISION` while the worker's does is escalated with the worker's question — a state neither the rule nor any
  case states. NOTE below.

### 8. New overreach

Claims in the change's artifacts with no evidence in the record:

UNPROVEN: "`tools/issues/decisions.sh` listed 13 items on 2026-11-17, one of them carrying that sentence; the run's
own census on 2026-11-05 read 8 of 18." (`proposal.md`, Why — no command output anywhere in the record.)
UNPROVEN: "all 185 selected records apply" and "`--only 29.3` and `--only 29.1` red for the right reason, restored,
green again" (`tasks.md` 3.3 — no `--only` log; the record's only `--check` output is the unscoped `all 415 cases
passed`, `gate.txt:1265`).
UNPROVEN: "all three PASS (2026-11-17)" as a dated focused run (`tasks.md` 3.1 — the PASS lines exist in the two
whole-suite logs; no focused-run log with a timestamp exists).
UNPROVEN: "the item was escalated with the run's own retry/abandon sentence" for #894 (`proposal.md`, and
`guidelines/Backlog.md:208-209`) — the report's decision is in the tree, the escalation it says #894 received is not.
UNPROVEN: "`run#blockedItemAsksItsOwnQuestion`'s fixture now states a blocked verdict with no `LOOP-DECISION`"
(`tasks.md` 1.3 — the fixture is `LOOP-VERDICT: closed`, and the diff does not touch it).

Findings, in severity order:

BLOCKING: The diff carries issue #954's test-side change, and that half is red on this commit. `loop-cases.sh:4666`,
`:4686-4695`, `:7096`, `:7116-7122` and `loop-mutations.sh:1443-1447` (record `954.1`) belong to #954, whose
production half — the `non-interactive` clause in `tools/loop/prompts/reviewer.md` — is not in `b08c26b7` but in its
child `41dc62e3`. Consequence at `b08c26b7`: `prompt#reviewerCarriesTheEightItems` and
`prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle` fail their new assertions (the strings are absent from
`git show b08c26b7:tools/loop/prompts/reviewer.md`), and `loop-mutations.sh --check` reports
`MUTATION-DID-NOT-APPLY` for `954.1` and exits 1 (`tools/mutate.sh:195-199`, `loop-mutations.sh:34`). Smallest fix:
drop those hunks and `954.1` from this commit (they are already in `41dc62e3`, which carries the clause they assert) —
or, if the branch lands as a pair, review `41dc62e3` as the commit under review instead of `b08c26b7`.
BLOCKING: The gate and full-suite evidence measure a tree that is not this commit's. `gate.txt:13` names
`tools/loop/prompts/reviewer.md` among the changed files although this diff does not touch it; `gate.txt:1265` claims
all 415 mutation records apply although `954.1`'s search string is absent at `b08c26b7`; `full-suite.txt:442/:633` pass
the two reviewer-prompt cases whose asserted clause only exists from `41dc62e3`. `head=b08c26b795cb1549db331cabab3af491da307d11`
matches the commit under review; the measured tree does not (uncommitted #954 `reviewer.md` edit in the same working
tree, later committed as `41dc62e3`). Smallest fix: run the gate on a clean `b08c26b7` checkout and record that
`LOOP-GATE-TREE`, or re-point this review at the branch tip `41dc62e3` whose content is what these logs measured.
BLOCKING: The delta ships a scenario no case can see. `specs/agent-issue-loop/spec.md`, scenario "A review round's
question is preferred to the worker's", names `run#escalationCarriesTheDecision`, whose fixture
(`loop-cases.sh:11682-11686`) leaves the worker's `LOOP-DECISION` empty, so the case passes with the precedence line
deleted — `design.md` D3 says so in its own words. Smallest fix: drop that scenario from the delta (the shipped
`An escalation names the decision`, `spec.md:566-571`, already owns it) or add a case where both reports carry a
different decision and assert the reviewer's is the one posted.

NOTE: The guideline and the delta's first sentence state a narrower condition than the code applies and than the
delta's own second sentence states. `guidelines/Backlog.md:205-206` says the worker's decision is used "when the
block ended before any round"; `run.sh:4304-4310` uses it whenever the reviewer's report carries no `LOOP-DECISION`,
whatever the round did, and `design.md` D1 chose that fall-through deliberately. No case holds the state (a round ran,
left no decision, the worker's does), so nothing catches the divergence. Smallest fix: reword the guideline clause (and
the delta's first sentence) to the report-in-hand condition — "the reviewer's `LOOP-DECISION` when its report carries
one, the worker's otherwise".
NOTE: The delta's third scenario duplicates the shipped `An escalation names the decision`
(`openspec/specs/agent-issue-loop/spec.md:566-571`) under a new requirement, so one fact has two owners and a later
edit to either will drift. Smallest fix: cover it with a `MODIFIED` block on the shipped requirement instead of a third
scenario in the added one.
NOTE: `tasks.md` 1.3's sentence describes a fixture change the diff does not make and misnames the fixture's verdict
(`loop-cases.sh:1596` is `LOOP-VERDICT: closed`); 2.2 counts "two scenarios" where the delta ships three; 3.1 and 3.3
quote results whose commands' outputs are not in the item's evidence directory (item 6).
NOTE: The `# files:` declaration added at `loop-cases.sh:4666` and `:7096` is correct for #954 (the case reads
reviewer.md's text), but that is what obliges the gate to run the prompt cases for this delta — a second reason the
#954 hunks cannot be separated from the measurement that says they pass.

BLOCKING: 3
VERDICT: FAIL
