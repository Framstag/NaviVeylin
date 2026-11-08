<!-- The round-1 review of this change, as it was written (2026-11-07T13:24Z). It is quoted here because this
     change answers its two blocking findings; the log and diff names it cites are that round's own and are no
     longer under this evidence directory. Its replacement in the record is `red-on-HEAD-*.log`, `green-*.log`,
     `group-*.log` and `revert-check-262.*.log`, taken on the base this change was worked on. -->

## Review — #819, pre-gate round

**Change under review:** `fix-a-gate-record-names-the-tree-it-measured`, 7 files (`guidelines/Backlog.recipes.md`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`, `tools/loop/gate.sh`, `tools/loop/lib.sh`, `tools/loop/prompts/reviewer.md`, `tools/loop/run.sh`). Item body: `change: none`, `ITEM_DECISION recorded: none`.

### Reading set (item 7's derivation)
`bash tools/check-rule-routes.sh --diff a3cefa934793b341e0d9290034a39b482e4e93dc` in the change root derives: `Backlog.md` §1, §3 (+2 rules), §4 (+1 rule), §5 (+6 rules), `Backlog.md`:282, §14 (+1 rule); `BacklogTooling.md` §9; `BuildGate.md` §2a. All read. `Backlog.recipes.md`:567 — the section the paragraph was written into — is **an addition to that set**, not a substitution: `Backlog.md`:282 routes the loop's procedure there, so I read it as the owning section.

### 1. Scenario coverage — 6 delta scenarios (`LOOP-MEASURE-CHANGE … scenarios=6 cases=6 unnamed=0`)

| scenario | asserting case | evidence | grade |
|---|---|---|---|
| third full run refused with the scoped command | `gate#aThirdFullRunInOneSessionIsRefused` | `gate-group.log` `PASS gate#aThirdFullRunInOneSessionIsRefused`; `green-gate-case.log` `PASS`; red `12:15:05Z` | **PASS** |
| a record names the tree it measured; a corrected tree differs | same case | assertions `LOOP-GATE-TREE: head=`, `$3` field = `git -C "$p" rev-parse HEAD`, `tree: head=`, `[ "$last_tree" != "$first_tree" ]`, `assert_eq "$first_tree" …`; exercised at `2026-11-07T12:20:27Z` (`revert-check-94.3.log`: `a tree corrected after the gate is a tree the gate never saw: head=5c52f1… diff=…`) | **PASS** |
| the round's task names the gate and the suite it may not run | `prompt#reviewerIsBoundedToReading` | none — `grep -l 'prompt#' evidence/*.log` matches nothing | **FAIL** |
| a session's own reviewer is handed the same text | `prompt#iterationProtocolHandsTheReviewerTheSameSkeleton` | none — case not run, no log | **FAIL** |
| the round's task states the rule a quoted number follows | `prompt#reviewerIsBoundedToReading` | none | **FAIL** |
| the round compares the gate's tree with the tree it reads | `run#aReviewDiffNamesTheTreeItWasWrittenFrom` | `green-run-case.log` `PASS`; red `12:15:17Z` `the diff under review was handed no tree identity` | **PASS** (the *equality* it exists for is item 8's finding) |

BLOCKING: three of the six scenarios have no execution in the record, and one of their cases declares the very file the diff edits — `prompt#iterationProtocolHandsTheReviewerTheSameSkeleton` carries `# files: guidelines/BuildGate.md tools/loop/prompts/reviewer.md`, and the diff rewrites `tools/loop/prompts/reviewer.md:56-62`. `measure.txt` itself lists the selection that owes it (`LOOP-MEASURE-CHECK tools/loop-selftest.sh --from-git a3cefa…`), yet the run's evidence holds only the `gate`, `driver` and `mutation` groups plus the two individual cases — the prompt group was never run, so the delta's change to item 3 is asserted by no case at all. `prompt#reviewerIsBoundedToReading` is the case both remaining scenarios name and it is likewise absent. The support the change does carry (`assert_contains "$(cat "$prompt")" "LOOP-GATE-TREE:"`) is a *different* claim.

Correction, one pass: run `bash tools/loop-selftest.sh --case prompt#reviewerIsBoundedToReading` and `--case prompt#iterationProtocolHandsTheReviewerTheSameSkeleton`, write both logs into `openspec/changes/fix-a-gate-record-names-the-tree-it-measured/evidence/`, and name them in task 7 beside the three groups (or add a task of their own).

### 2. Cause or gap scope — PASS
The diff implements the named root cause — the record's third field and a tree identity both moments derive — and nothing else. Quoted hunk, `tools/loop/gate.sh`:
```
-    printf '%s\t%s\n' "$session_key" "$gate_argv" >> "$runs_record"
+    printf '%s\t%s\t%s\n' "$session_key" "$gate_argv" "${tree_identity:-head=unknown diff=unknown}" >> "$runs_record"
```
with `tree_identity=$(loop_tree_identity "$LOOP_ROOT" "$base")` printed as `LOOP-GATE-TREE:` before the first step and skipped under `--plan`; `run.sh:1230` `loop_tree_identity "$worktree" "${base:-$1}" > "$2.identity"` (`base` is the run's global from `run.base` at `run.sh:617`, so a correction round's narrower `$1` cannot shift it); the refusal now prints each earlier run's tree. No unrelated hunk.

### 3. Gate reality — PASS, `the gate has not run — this round precedes it`
Nothing else graded about it. (Note the change agrees: `LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run`.)

### 4. Tasks claiming done
All eight `[x]` map to a command and an output; tasks 1–6 and 8 hold. `BLOCKING:` task 7 (`[x] 7. The suites the delta owes, scoped by what it touched`) claims a scope the record does not carry — the delta touches `tools/loop/prompts/reviewer.md`, a case declares that file, and that case was not run. Same finding as item 1's; fix it once, in one pass.

`NOTE:` tasks 2, 5 and 8 quote a verification with no captured log (`bash -n …` → no output, `./tools/look.sh 'LOOP-GATE-TREE'` → `13 matches in 7 files`, `openspec validate … --strict` → valid). Nothing in the change depends on them.

### 5. Revert-check — PASS
Three mutations, each on the production invariant it names, each failing for the right reason with a timestamp, each restored:
- `94.1` `tools/loop/gate.sh` — `2026-11-07T12:20:23Z — each record names the commit the run measured: : expected 'daa2900…', got ''`
- `94.2` `tools/loop/run.sh` — `run#aReviewDiffNamesTheTreeItWasWrittenFrom`, the identity write dropped
- `94.3` `tools/loop/lib.sh` — `2026-11-07T12:20:27Z — a tree corrected after the gate is a tree the gate never saw: head=5c52f1… diff=…`

each log ending `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` and `restore … green again`. This round runs no version control, so "`git diff` of the production file empty" is read from that restore line and not re-measured.

### 6. Evidence pointers
`NOTE:` `13 matches in 7 files` (task 5) has no command output in the record; no command in the record produced it.
`NOTE:` the archive's stamps `12:23:41.923Z`–`12:26:30.823Z` and `12:42:56.420Z`/`12:43:06.048Z` are read from the tree, not from a command the record carries. I checked them where they stand and they hold: `grep -ho 'timestamp="…"' …/archive/2026-10-22-fix-staging-leftover-discovered-as-map/evidence/gate/*.xml` gives exactly `12:23:41.923Z … 12:26:30.823Z`, and `tasks.md:92` is the `§5.6` sentence quoted. No command in the change's record reproduces them, and no assertion depends on them — so a correction here costs nothing.
`NOTE:` both copies (item `evidence/` and the change's `evidence/`) are byte-identical (`diff -r` clean), so no copy is fragile beyond the change's own directory.
All tallies quoted are in the log that produced them: `all 29 cases passed (…) — 21.31 s`, `all 23 cases passed — 26.75 s`, `all 2 cases passed — 2.79 s`, `all 273 cases passed`, `cases=354`, `all 329 section references resolve`, `56 engines and 7 templates`.

### 7. Spec and guideline honesty — PASS on kind, FAIL on one claim
- Delta kind: `## MODIFIED Requirements` for two requirements that exist on the base — `openspec/specs/agent-issue-loop/spec.md:3669` ("A child pays for the full gate…") and `:1512` ("A review's task states what the round may run…"). Right kind, both restated in full, contract wording (SHALL) rather than implementation.
- Guideline: `Backlog.recipes.md`:567 gets a rule with its measurement in the paragraph that already owns the record; it is not restated elsewhere. `Backlog.md` §4 "an invariant is stated with the observation that would falsify it" — the new paragraph states the rule and no falsifying observation for its own central claim (item 8's finding), and `Backlog.md` §14 "the concept is not the implementation" is satisfied elsewhere.
- `BuildGate.md` §2a "a reviewer is a reader, not a runner" — the edited item 3 keeps the prohibition and adds only what is compared. PASS.
- `BacklogTooling.md` §9 — no check row, no tracker write; not implicated.
- `## Open decisions: None` and the item's `recorded: none` agree. The item's filed fork is answered by the tree: `openspec/specs/documentation-ownership/spec.md:735` "An archived record SHALL NOT be inspected or rewritten, because it is history rather than a defect in the tree." — the citation resolves. PASS.

### 8. New overreach
BLOCKING: the mechanism's central claim is contradicted by its own code, so the ordinary flow reads as a corrected one.

The contract: "a digest of the change itself, **taken over the paths the change touches** with the hash of each path's working-tree content, so the same content reads as the same tree whether the change is committed or not" (`openspec/changes/fix-a-gate-record-names-the-tree-it-measured/specs/agent-issue-loop/spec.md`). The code takes every untracked path in the tree, not the change's paths — `tools/loop/lib.sh:272-273`:
```
  listing=$( { git -C "$tree" diff --name-only "$base" 2>/dev/null
    git -C "$tree" ls-files --others --exclude-standard 2>/dev/null; } | sort -u |
```
and the gate derives it before its first step (`tools/loop/gate.sh:209`), while the round derives it later (`tools/loop/run.sh:1230`). Between those two moments the gate's own evidence is written **inside the change directory**, which no ignore rule covers (`.gitignore` has `.pi/*`, `build/`, and no `evidence` entry) — the archive proves the shape: `openspec/changes/archive/2026-10-22-fix-staging-leftover-discovered-as-map/evidence/gate/` holds `totals.txt` and XMLs stamped `12:23:41.923Z`–`12:26:30.823Z`, i.e. files created *after* the identity line was printed. Those paths are in the round's `diff=` (`git diff --name-only base` once committed, `ls-files --others` if not) and cannot be in the gate's, so `diff=` differs on a tree nobody corrected — and the reviewer's new item 3 is told to grade exactly that mismatch `BLOCKING:` (`tools/loop/prompts/reviewer.md:62`). The guideline repeats the claim it breaks: §7c "the two moments must agree about the tree between them" and "a commit-shaped digest would make the ordinary flow read as a corrected one."

UNPROVEN: no case compares a gate record's `diff=` with a `*.identity`'s `diff=`. `run#aReviewDiffNamesTheTreeItWasWrittenFrom` runs no gate — it asserts the file exists, its `head=` is a commit the tree knows, its `diff=` is 16 hex, and the prompt names both strings; `gate#aThirdFullRunInOneSessionIsRefused` asserts the records and the console agree with each other. The two derivations never meet in the record, so a change that made them disagree (a different base, a wider path set — as above) passes both.

UNPROVEN: "the digest is commit-invariant" — the only correction exercised is an uncommitted edit; the same content committed and uncommitted is compared nowhere.

Correction, one pass, both together: give `loop_tree_identity` the same path set both callers must agree on — the diff's own paths, i.e. apply `LOOP_REVIEW_EXCLUDE_PATHSPEC` (`tools/loop/loop.conf:636`, `:(exclude,glob)**/evidence/**`) to the `ls-files --others` listing in `tools/loop/lib.sh:273`, and pass it from `tools/loop/gate.sh:209` and `tools/loop/run.sh:1230` — then assert in `run#aReviewDiffNamesTheTreeItWasWrittenFrom` that a gate run over the uncorrected stub tree and the `.identity` written after it carry the **same** `diff=` (that case needs one gate invocation added), and correct the sentence at `guidelines/Backlog.recipes.md`:567 to say which paths the digest covers.

```
LOOP-BLOCKING: 2
VERDICT: FAIL
LOOP-CORRECTIONS-ONLY: no
LOOP-DECISION: Whether this item ships the tree identity as it stands, with the two blocking findings corrected first — (1) run the two prompt cases the delta owes and name them in task 7, (2) narrow the digest to the diff's own path set and assert the gate's `diff=` equals the round's on an uncorrected tree — or drops the identity and re-scopes the item; I recommend applying both corrections in one worker pass, because finding (2) makes the new reviewer rule refuse every change the loop gates, which is worse than the defect the item was filed for.
```
