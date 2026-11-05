# Review round 2 — fix-calls-per-turn-is-a-reported-number (issue #757)

Read-only review of the **committed** change, `HEAD` = `d65bdb69` ("fix: a run reports the shape it spent its
budget in (#757)") on `5e2de33e`, 2026-11-03. Working tree clean; `git show HEAD` is the whole delta. No file was
edited except this one.

Run for this review: `bash -n` over the four shell files; `bash tools/loop-mutations.sh --only '78.1 78.2 78.3
78.4' --check`; `openspec validate fix-calls-per-turn-is-a-reported-number --strict`; `bash
tools/loop/evidence-check.sh openspec/changes/fix-calls-per-turn-is-a-reported-number --root .`; and read-only
counts over `.pi/issue-loop/items`. The self-test and `tools/loop/gate.sh` were **not** run.

## Review
- Correct: the change is in the tree and the diff is the nine files it claims
  (`git show --stat HEAD`: `state.sh`, `report.sh`, `loop.conf`, `prompts/worker.md`, `loop-cases.sh`,
  `loop-mutations.sh`, `Backlog.recipes.md`, `PORTING.md`, `.pi/skills/loop-spend/SKILL.md`), and no compiled file
  is touched.
- Correct: the engine derives what the delta says it derives — the two counts in `tools/loop/state.sh:672-679`
  (null when the child reported no `turn_end`, `:699-700` fallback gains both keys), stored at `:543-544`, the
  column at `:759-768` appended after `over`, and the trend line at `tools/loop/report.sh:262-284` guarded against
  a zero denominator on both halves (`report.sh:264`, `state.sh:767`).
- Fixed: nothing — this is a review-only pass.
- Finding: P1, P2 ×8 — listed below.
- Merge verdict: **OK with notes** — the one P1 is a claim in `tasks.md`, not a behaviour; nothing in the shipped
  engine or case is wrong.

## 1. Round 1's findings, one by one

| round-1 finding | state | evidence |
|---|---|---|
| **P0** — an apostrophe inside the `state.sh` jq program closed the string, and the report printed no table | **OK (closed)** | `bash -n` passes on all four: `tools/loop/state.sh`, `tools/loop/report.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`. The new jq-internal comments carry no apostrophe (`state.sh:759-765`, `report.sh:266-270`), and `report.sh:230` now reads "the run own rate" instead of "rate's". |
| **P0** — the change was not in the working tree | **OK (closed)** | `git show --stat HEAD` carries all nine files; `git status --short` is empty. |
| **P1** — the gate evidence did not cover the content it was cited for | **OK (closed)** | `evidence/gate.log:1551` reads `… HEAD d65bdb69 … 2026-11-03T21:20:54Z`; the log's own stamps run `21:05:45Z` (`gate.log:43`) to `21:20:54Z`, against a commit at `2026-11-03 22:05:22 +0100` = `21:05:22Z` — the gate began 17 s **after** the commit and named the commit. `gate.log:809` PASSes the new case on that revision. |
| **P1** — a case asserted a behaviour no scenario stated and no mutation covered (the predating-state dash) | **OK (closed)** | Stated: `specs/agent-issue-loop/spec.md:10` ("a state written before the two counts existed says so rather than naming a run of items that answered without a tool"), `design.md` "Risk", `tasks.md` task 4. Covered: mutation `78.4` (`tools/loop-mutations.sh:745`) and the assertion `tools/loop-cases.sh:8663`. |
| **P2** — "33 184 `turn_end` lines" did not reproduce | **OK (closed)** | It is **33 183** in every file that quotes it (`tools/loop/loop.conf:128`, `guidelines/Backlog.recipes.md:317`, `tools/loop/report.sh:229`, `tools/loop/prompts/worker.md:85`, `tools/loop-cases.sh:8573`, `tools/loop-mutations.sh:742`, `proposal.md:14`); no `33 184` survives anywhere. My own count over the 227 streams: `LC_ALL=C grep -F -h -o '"type":"turn_end"' … \| wc -l` → `33183`, and `36825` for `tool_execution_end`. |
| **P2** — the floor's rejection of p50 cited p90's *direction* for tokens | **OK (closed)** | `loop.conf:132-133` now argues the share ("a flag on half the items is a banner rather than a measurement — the argument that kept the token band off its own median and at the p75"), which is the band's own shape (`loop.conf:109-113`: it sits at p75 and flags "about one item in four"). Nuance, not a defect: the band's recorded reason for *not* going higher is that p90 flags too few (`loop.conf:110-112`), so "flags half the archive" summarises the share argument rather than quoting the band's text. |
| **P2** — the floor-off clause could not fail on its negative half | **OK (closed)** | `tools/loop-cases.sh:8638` asserts the off text and `:8641` now asserts `assert_not_contains "$calls_line" "#22"` — both directions, as the token band's mirror case does (`:8557-8569`). |
| **P2** — the sanitiser let a value through that jq refuses | **OK functionally, comment inaccurate** | `tools/loop/report.sh:239` refuses everything the regex `^[0-9]+(\.[0-9]+)?$` rejects; the accepted set is a subset of what `jq 1.8.2` parses (checked: `--argjson f 05` / `007` / `00` all exit 0). The comment at `:238` nevertheless claims "`05`, `1.` and `.` are all refused by the same pattern" — `05` is **not** refused. See finding 4. |
| **P2** — the mutation description lost its possessive | **OK (closed)** | `tools/loop-mutations.sh:744` reads "the table column that prints the per-item calls per turn". |
| **P2** — the recorded p75 | **BROKEN (over-corrected)** | It was `1.22`, now `1.21` (`loop.conf:129`, `Backlog.recipes.md:318`, `proposal.md:15`, `worker.md:85`, `loop-cases.sh:8573`, `loop-mutations.sh:742`, `design.md`, `tasks.md` task 12). Raw per-item ratios over the same 227 streams give **p75 = 1.2158** by linear interpolation (1.2174 nearest-rank) → **1.22**; `1.21` reproduces only if each item's ratio is rounded to two decimals *before* the percentile (1.2150). See finding 3. |

Other archive figures, recomputed over the streams `tools/loop-spend.py` reads (227 items):

- `36 825` `tool_execution_end`, `33 183` `turn_end`, ratio **1.1098 → 1.11** — exact.
- tokens **2 591 297 925** over 33 183 turns = **78 091 → "78 k"**; `cacheRead` share **96.6 % → "97 %"** — exact.
- p10 **0.9874 → 0.99**, p25 **1.0500 → 1.05**, p50 **1.1053 → 1.11**, p90 **1.3236 → 1.32**, max **1.6111 → 1.61** —
  exact; "every item of the archive is under two calls a turn" holds (max 1.61).
- The floor `LOOP_ITEM_CALLS_PER_TURN_MIN=1.05` (`loop.conf:141`) is the interpolated p25 exactly, and the comment's
  "rounded to the two decimals a ratio is read at" matches the report's own rounding (`report.sh:277`,
  `state.sh:768`).
- `proposal.md:16-17`: "8 160 `ctx_read` calls" reproduces to within two — `8158` `tool_execution_end` lines name
  `ctx_read`; the "3 909 … a second read" half was not verified (it needs the per-session path history).

## 2. The spec's scenario against the case

`SCENARIO report#anItemBelowTheCallsPerTurnFloorIsNamed` — **OK**, every `THEN` clause is asserted:

| `THEN` clause (`specs/agent-issue-loop/spec.md:10`) | assertion |
|---|---|
| the run's rate with the two counts (`1.33 calls per turn (8 calls / 6 turns)`) | `loop-cases.sh:8619` |
| the item below the floor named with its own ratio (`#22 0.5`) | `loop-cases.sh:8622` |
| the last column reads `3` and `0.5` | `loop-cases.sh:8628-8629` |
| a floor of `0` prints as off **and names no item** | `loop-cases.sh:8638` (positive) and `:8641` (negative) |
| an item that recorded no turn prints `-` with its reason | `loop-cases.sh:8647`, column `:8651` |
| a state written before the two counts existed says so | `loop-cases.sh:8663` |
| both items keep the outcome their work earned | `loop-cases.sh:8630` |

Asserted by one and not the other:

- `loop-cases.sh:8624` (`assert_not_contains "$calls_line" "#21"`, the item above the floor is not named) is stated
  only in the scenario **title** ("An item below the recorded floor is named, and one above it is not"), never in
  a `THEN` clause — finding 6.
- Conversely, the *case* does not reach `design.md`'s "it prints whether or not a floor is recorded": under the
  floor-off phase it re-reads only the trend, not column 17. That claim is true by construction
  (`state.sh:766-768` reads no floor), so this is a coverage note, not a defect.
- The `WHEN` clause's own preconditions are set up faithfully: the floor is a floor of the case's own (`=2`,
  `loop-cases.sh:8584`), the two streams are the case's own, and the counts are asserted on the state first
  (`:7369-7372`).

## 3. The four mutation records

`bash tools/loop-mutations.sh --only '78.1 78.2 78.3 78.4' --check` → `selected 4 of 231 records`, `all 4 cases
passed — every selected record applies to the file it names`. All four patterns still match the committed lines
(`report.sh:264` for `78.1`, `state.sh:767` for `78.2`, `state.sh:673` for `78.3`, `report.sh:268` for `78.4`), so
the records were not left describing an earlier revision.

`evidence/mutations.log` is that run: four `RED`, four `restore … green again`, `red-when-mutated: 4 ·
not-noticed: 0 · restore-failures: 0`, stamped `2026-11-03T21:21:04Z`–`21:21:22Z` — after the commit (`21:05:22Z`).
One caveat on the reason line of `78.4`: see finding 5.

## 4. The evidence against what the artifacts claim

- **The gate is the commit's, and after it** — `gate.log:1551` names `HEAD d65bdb69`; the stamps run
  `21:05:45Z`–`21:20:54Z`; `LOOP-GATE: tests=899 failures=0 errors=0` (`gate.log:1605`) equals the sum of the
  per-step tallies (`310 + 231 + 34 + 78 + 29 + 10 + 34 + 7 + 155 + 11 = 899`), so no step is silently missing from
  the total.
- **The two tallies match their logs** — `gate-tally-loop-selftest.xml` `tests="310" failures="0" errors="0"`
  against `gate.log:896`; `gate-tally-mutations-check.xml` `tests="231" failures="0"` against `gate.log:900`.
- **`red-on-head.log` is the absence-shaped failure, not a moved number** — `expected '6', got 'null'` on the
  new case. The artifact itself records neither a revision nor a timestamp, so "on the base `5e2de33e`" rests on
  `proposal.md:23` and `tasks.md:5-10`, not on the log (finding 8).
- **Claim that outruns its evidence (P1)** — `tasks.md:77` says `tools/loop/evidence-check.sh …` "reports no
  dangling pointer". It exits 1 on this tree with two `FAIL C1 dangling §7 in … (no row for §7 …)` for
  `evidence/review-round-1.md:136-137`, where round 1 cites `Backlog.recipes.md` §7b/§7e — guideline section
  anchors, not backlog ids (finding 1).
- **Claim that outruns its evidence (P2)** — `tasks.md:79` marks `- [x]` for "the change is archived and the delta
  merged into `openspec/specs/agent-issue-loop/spec.md`"; the shipped spec carries no such requirement yet
  (`grep -n "A report reports the shape" openspec/specs/agent-issue-loop/spec.md` → no match). The archive is the
  step after this review, so the checkbox is ahead of the act (finding 7).
- **Claim with no derivation (P2)** — "halving the calls per turn is worth roughly a third of the archive's spend"
  (`proposal.md:19`), repeated as fact in the engine's own comment (`report.sh:229-230`). No derivation exists in
  the change, as round 1 also noted (finding 9).

## 5. Project rules

- `openspec validate fix-calls-per-turn-is-a-reported-number --strict` → `Change '…' is valid` (`tasks.md` task 10,
  the step round 1 found missing, is now present and passes).
- The delta is `ADDED` only, so no `MODIFIED` block owes a verbatim repeat; the capability exists, so no
  `## Purpose` is owed; the requirement body is **385** characters (limit 500); `tasks.md` uses `- [x]` markers
  (`AGENTS.md`, "OpenSpec Workflow"; `openspec/specs/documentation-ownership/spec.md`).
- Documentation routing: the new prose sits in `guidelines/Backlog.recipes.md` §7b/§7e, the sections that own it,
  and `tools/check-doc-routes.sh` is green in `gate.log:1158` ("all 329 section references resolve"). No AGENTS.md
  row needs editing — no new document, and `Backlog.md` §7b is still the 177-byte index stub the row prices at
  0.2 kB.
- AGENTS.md "Agent iteration loop": a defect fix owes a test that fails on HEAD before the fix (`red-on-head.log`)
  and an independent read-only reviewer before archive (this file). Both are discharged.
- No log or diagnostics line is added by the change, so `guidelines/Logging.md` is not in play.

## Findings

1. **P1 — `tasks.md:77` claims a check that fails.** `bash tools/loop/evidence-check.sh
   openspec/changes/fix-calls-per-turn-is-a-reported-number --root .` → `FAIL C1 dangling §7` twice (from
   `evidence/review-round-1.md:136-137`, which names `Backlog.recipes.md` §7b/§7e). Smallest fix: state the true
   result in `tasks.md` — the two `§7b`/`§7e` pointers are guideline section anchors in the retained round-1
   report and are report-only once the change is under `openspec/changes/archive/` (the checker exempts `§N` in
   archived changes) — rather than rewriting round 1's historical record.
2. **P2 — the table's rule row has one cell too many.** `tools/loop/state.sh:727` carries 18 `---` cells for the
   17-column header at `:726` (19 pipes vs 18). `HEAD~1` was consistent (16/16), so this diff added two cells where
   the header gained one. It renders as an empty trailing column and no reader breaks (every reader indexes 1–17),
   but the change's own claim is "the row keeps its seventeen columns" (`loop-cases.sh:8421`), and
   `report#headerAndTableShape` does not read the rule row, which is why the gate stays green. Smallest fix: drop
   one `|---` at `state.sh:727`.
3. **P2 — the corrected p75 does not reproduce.** The committed set says p75 `1.21`; raw per-item ratios over the
   same 227 streams give 1.2158 (linear interpolation) / 1.2174 (nearest-rank) → `1.22`. `1.21` is reachable only
   by rounding each item's ratio to two decimals before taking the percentile, while the neighbouring p90 `1.32`
   is the raw linear value (1.3236; nearest-rank would be 1.33) — so the quoted set mixes two definitions.
   Smallest fix: record the definition beside the set, or restore `1.22` and note the raw measurement.
4. **P2 — the sanitiser's comment names a value it does not refuse.** `tools/loop/report.sh:238` says "`05`, `1.`
   and `.` are all refused by the same pattern". `05` matches `^[0-9]+(\.[0-9]+)?$` (`report.sh:239`) and jq 1.8.2
   accepts it (`jq -n --argjson f '05' '$f'` → `5`), so nothing is lost — but the comment is false. Smallest fix:
   drop `05` from the sentence, or refuse a leading zero if that was the intent.
5. **P2 — mutation `78.4`'s recorded red reason is not the failing assertion.** `evidence/mutations.log:11-12`
   quotes "state: the state being replaced never recorded a stop reason — its run is kept at …", a benign engine
   banner that the same case prints in a green run (`gate.log:803`). `case_reason` (`tools/loop-mutations.sh:951-957`)
   takes the first line of the `<case>|   <message>` shape, and this banner comes first. The record is red and
   restores green, but the log does not show *which* assertion caught the mutation. Smallest fix: re-run that one
   record and quote the `a state that predates the two counts says so…` message, or make `case_reason` skip a line
   whose tag is an engine diagnostic rather than the case name.
6. **P2 — the case asserts one thing the `THEN` does not state.** `tools/loop-cases.sh:8624` asserts the item above
   the floor is not named; that is in the scenario title (`specs/agent-issue-loop/spec.md:8`) but no `THEN` clause.
   Smallest fix: add "and names no item above it" to the `THEN` at `spec.md:10`.
7. **P2 — `tasks.md:79` is checked ahead of the act.** The archive and the merge into
   `openspec/specs/agent-issue-loop/spec.md` have not happened (the requirement is absent from the shipped spec).
8. **P2 — `evidence/red-on-head.log` carries no revision or time.** It proves the case fails while the counts are
   absent (`expected '6', got 'null'`), but not that the tree was `5e2de33e`; that fact lives only in
   `proposal.md:23`. Smallest fix: record the base beside the log, as `mutations.log` stamps each record.
9. **P2 — an unquantified claim is quoted as fact.** "halving the calls per turn is worth about a third of the
   spend" (`proposal.md:19`, `report.sh:229-230`) has no derivation in the change; it should either be derived
   (turns saved × context per turn) or marked as an estimate.

## What I did not verify

- The "3 909 of them a second read" half of `proposal.md:16-17` (needs per-session path history; the `8 160` half
  reproduces to 8 158).
- The case's own green run: I did not run `tools/loop-selftest.sh` or the gate; `evidence/gate.log:809` PASSes the
  case on `d65bdb69`, and the gate's tally covers 310 of 310 loop cases.
- Behaviour beyond what the case and the mutation set read: no state was written, no report was produced by hand.

VERDICT: NOTES
