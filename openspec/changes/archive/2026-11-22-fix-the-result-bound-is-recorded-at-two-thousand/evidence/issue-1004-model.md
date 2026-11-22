# The item's model, quoted — issue #1004

This is the measurement behind the number this change records (`LOOP_TOOL_RESULT_MAX_CHARS=2000`,
`tools/loop/loop.conf`). It is **the item's own model** over `.pi/issue-loop/items`, taken with the reader
`tools/loop-spend.py` uses; the block below is quoted verbatim from the run's work package
(`.pi/issue-loop/items/1004/work-package.md`, fingerprint `6c184a6cba07eab5`) so the number has an origin a reader
can follow. This change takes no new model of its own and states no figure of it as a measurement of its own.

```
<<<ISSUE_TEXT
- **Observed** ✗: `LOOP_TOOL_RESULT_MAX_CHARS=4000` (`tools/loop/loop.conf:481`) bounds what one tool result may put into a child's context, and #815 set it there from 8 000. The median result in this archive is 825 characters, so the bound only ever cuts the tail — and the tail is where the volume is: `ctx_shell` carried 19.6 M of the 32.0 M characters injected, at 2.0 k per call, against `ctx_read`'s 9.3 M at 1.5 k.
- **Evidence** ⊨: the same filter model over `.pi/issue-loop/items` (the reader `tools/loop-spend.py` uses; as-is row p50 114 k, p90 164 k, 995 Mtok, +13.7 % p50 over the reported window, so read these as comparative):
  - recorded 4 000 (the model's as-is row, uncut): 114 k, 164 k, 995 Mtok
  - **2 000: p50 96 k, p90 134 k, 792 Mtok (-20 %)**
  - 1 000: 79 k, 111 k, 626 Mtok (-37 %)
  - 500: 66 k, 98 k, 526 Mtok (-47 %)
- **Why 2 000 and not 1 000** ⊨: the bound is a rule about the tail, and the tail is what a diagnosis reads. 2 000 keeps a whole failing test's worth of output and a `git diff` of a small hunk; 1 000 starts cutting the JUnit line and the stack frame that `guidelines/BuildEvidence.md` §4d attributes a red run with. The evidence class is not the reason to stop at 2 000 — that class is already held at `GATE_EVIDENCE_BOUND=8000` by the extension, so a gate log and a suite XML keep their 8 000 either way — it is the ordinary `ctx_shell` result that this bound is about.
- **Fix** ➜: record 2 000 at `tools/loop/loop.conf:481`. One line, no code: the adapter reads the key, the trim line names the cut and the `sed -n` range that gets the rest back, and `LOOP_TOOL_RESULT_MAX_CHARS=0` still turns the rule off. `state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst` pins the run's record to the file's own number and goes on passing — the record follows the edit, which is the point of that case.
- **Honest bound on the gain** ⊨: #791 is the warning this item files against itself — 69 trims cut 817 168 characters to 552 000 and the item still ended at a 200 104-token context, so a lower bound alone does not save an item and this item must claim its 20 % share and not a bound cut. Every cut result is also a fact the child may have to fetch again, and a re-fetch is a turn: the trim line names the range so the second read is the cheap form of it, but a child that wants the whole file pays a turn for it.
- **Red case** ➜: a new `trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut` — a 3 000-character result, above 2 000 and under the old 4 000, must arrive cut and named; it is red on this tree today because the recorded bound hands it over whole. `trim#aResultUnderTheBoundIsHandedOverWhole` stays as the other arm (1 000 characters, and the exact-length boundary it drives), and both read the bound from `LOOP_TOOL_RESULT_MAX_CHARS`, so the arm that must stay whole is measured against the value the tree records rather than against a literal.
<!-- fingerprint: e0d435bb052c4e873d58a0ba49df923829504e91190127d727365c974a0093d1 -->
<!-- tier: a -->
ISSUE_TEXT
```
