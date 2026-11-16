# Tasks

Reference: `proposal.md` for the four reported defects and their pointers, `design.md` for the alternatives and the
risks, `specs/decision-queue/spec.md` for the two requirements.

No compiled file is touched by this change, so the verification is the structural one (`guidelines/Build.md` §2, the
scope block): the offline issue-tooling self-test and the documentation checks, not the build gate.

## 1. The option's shape

Requirement: "An option states what the owner gets, whether it solves it, and its risk" — its four scenarios.

- [x] 1.1 `.pi/skills/answer-decision/SKILL.md` §6: an option becomes five labelled lines in this order — **What you
      get**, **Does it solve it** (full / partial / no + the residue in the same line), **How you'll know**, **Risk**
      (the level with the clause that sets it, plus if-it-is-wrong, who it hits, undo, what would catch it, each on its
      own line), and **For the architect** (mechanism, files, gate minutes, rounds to land, the `Example:`).
      Verify: the four field labels each sit on one line (`grep -c` per label = 1), so the case can assert them; the
      worked example at the end of the file is rewritten in the new shape, so the skill does not teach the old one.
- [x] 1.2 `.pi/skills/answer-decision/references/answer-shape.md` (new, 92 lines / 4.5 kB): the skeleton to fill —
      header (what you are choosing, recommendation with its risk, solved-means, if-you-choose-nothing), the six
      contents lines, the five option lines, the criteria table, the risk table with the `do nothing` column, the two
      architect views, the recommendation — and seven checks before sending.
      Verify: `wc -c` → 4 433 bytes (92 lines); every block the cases assert is present by heading or label.
- [x] 1.3 §4 states the two lanes and refuses a code token in the owner's; §7 states the criteria before the ranking;
      §8 puts the risk levels side by side with the `do nothing` row; §9's recommendation carries its own risk and the
      first sign of failure.
      Verify: `grep -c "No file path, no identifier, no tool name"` → 1; the `do nothing` row appears in §8.
- [x] 1.4 Add `decisions#everyOptionSaysWhatTheOwnerGetsAndWhatItRisks`: reads the skill and requires the four labels,
      the three solve-terms, the four risk fields, the priced `do nothing` and the lane rule.
      Verify: red with the `**What you get**` bullet removed (`FAIL decisions#everyOptionSaysWhatTheOwnerGetsAndWhatItRisks`,
      "…'**What you get**' not found"), green restored — 2026-11-16, offline, no token.

## 2. One pass, verdict first, and the listing as an answer

Requirement: "The answer is presented once, verdict first, and the listing answers what waits" — its three scenarios.

- [x] 2.1 §2: the listing answers "which are waiting" and ends the turn; "answer one" asks once and says what the wait
      buys; one waiting item is answered without a question. §3 becomes one batched read pass whose next message is the
      sheet, with the narrated turn named as the roundtrip it costs. §10 presents the sheet in one message and calls
      `ask_user` once, the choices carrying what each option gets and the context carrying the recommendation and the
      risks.
      Verify: `tools/check-doc-routes.sh` green (the new `references/answer-shape.md` path resolves).
- [x] 2.2 Add `decisions#theSheetLeadsWithTheVerdict`: line-number ordering in the skeleton — what-you-are-choosing <
      recommendation < solved-means < the item's state < the options.
      Verify: red when the recommendation block is moved below `## Options`
      (`FAIL decisions#theSheetLeadsWithTheVerdict`, "the three verdict lines sit above the detail"), green restored.
- [x] 2.3 Add `decisions#theSheetIsTheFirstThingAfterTheReads` and `decisions#theListingIsTheAnswerToWhichAreWaiting`,
      both reading the skill.
      Verify: both red on `HEAD`'s skill (2026-11-16), both green on this change.

## 3. The contract and the guideline

- [x] 3.1 `openspec/specs/decision-queue` gains the two requirements through this change's delta
      (`specs/decision-queue/spec.md`), each scenario naming the case that exercises it; the shipped capability text
      moves at archive.
      Verify: `openspec validate decision-answer-states-outcome-and-risk --strict` → "is valid"; both bodies are under
      the 500-character limit (436 and 470 characters with the joining space stripped, 437 and 471 with it).
- [x] 3.2 `guidelines/Backlog.md` §5 carries the same shape in its own words — the two lanes, the option's three
      questions and its risk, the priced "do nothing", one pass, one question, the listing as an answer — and names the
      six cases that hold it.
      Verify: `tools/check-rule-routes.sh` → "all 651 named rule(s) are reached"; the section restates no command the
      skill owns.

## 4. Verification

- [x] 4.1 `bash tools/issues/tests/issue-tooling-selftest.sh` → `all 177 cases passed (JDK 27, offline, no token,
      2026-11-16T10:23:41Z)`. The count moved 173 → 177 with the four new cases.
- [x] 4.2 Revert-check for the shape's new invariant, one mutation: delete the `**What you get**` bullet from §6 —
      `decisions#everyOptionSaysWhatTheOwnerGetsAndWhatItRisks` fails; restore — passes. Recorded above (1.4) and
      repeated for the ordering case (2.2).
- [x] 4.3 `tools/check-doc-routes.sh` → "all 356 section references resolve, all 7 skill references resolve";
      `tools/check-doc-values.sh` → green; `tools/check-openspec-hygiene.sh` → "OpenSpec artifacts are consistent";
      `tools/check-rule-routes.sh` → green; `openspec validate --strict` → valid.
      Named exclusions, both measured and neither this change's: `openspec validate --changes --strict` over the open
      set reports 37 passed / 8 failed (`dedupe-stylesheet-loads` and six `fix-*`, all red at `HEAD`), and
      `tools/check-change-readiness.sh` refuses this change on `design.md` / `proposal.md` declarations as it does
      15 of 45 open changes.
- [x] 4.4 No build, no Gradle task, no device: this delta touches no compiled file. Named exclusion: the issue-tooling
      self-test is the whole gate here, and it runs offline.
- [ ] 4.5 Read the next two answers to real items against the sheet, and record in the change's own note whether the
      owner still asks a follow-up question ("what do I get", "is it solved", "what is the risk"). This is the only
      check that measures the complaint rather than the shape: the four cases pin the blocks, not whether they land.
- [x] 4.6 Independent read-only review before archive, and its four blocking findings fixed rather than deferred:
      (1) `SKILL.md` §10 listed the blocks in a different order from the skeleton it points at — now named in the
      skeleton's order; (2) §4's lane rule pointed at §5/§6 for the mechanism where §6/§7 hold it; (3) the headline
      case asserted presence only, so the shape the design rejects passed it — it now asserts the option's own order
      (`**What you get**` before `**For the architect**`), the residue sentence, the risk clause and the skeleton's
      `do nothing` column; (4) `proposal.md` claimed 172 → 176 cases, measured 173 → 177.
      Verify: the ordering assertion is red when `**What you get**` is moved below `**For the architect**`
      (`FAIL decisions#everyOptionSaysWhatTheOwnerGetsAndWhatItRisks`), green restored.
