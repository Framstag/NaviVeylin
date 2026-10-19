# fix-loop-harness-first — tasks

Each task states its own verification. The change compiles nothing: the gate it owes is the tooling's shell gate
(`tools/loop-selftest.sh`, `tools/check-portability.sh`, `tools/loop-mutations.sh`), per `guidelines/Build.md` §2
(the scope block).

## 1. The red case, before the fix

- [x] 1.1 Add `tools/loop/fixtures/scenarios/categories/issues.json`: the fixture that carries this change's cases —
      issue 21 (bug, `category/car`), 22 and 26 (bugs, `category/build-and-harness`), 23 (improvement,
      `category/verification`), 24 (improvement, `category/ui`) are the five eligible items, 25 (bug,
      `category/specs-and-process`) is on hold and 27 (bug, `category/build-and-harness`) is authored by someone
      off the allowlist — 22 and 26 added by 7.2, 27 by 7.4. Same label shape as `scenarios/six` (author on the
      allowlist where intended, `status/open`, `agent/eligible`). Verification: the fixture loads under the
      pre-screen's stub `gh` with no jq error, and the pre-screen ranks 5 and excludes 25 and 27.
- [x] 1.2 Add `case_prescreen_harnessItemsLeadTheQueue` to `tools/loop-cases.sh` and register it in the case list
      at the top of that file: the eligible ranks, read as `sort_by(.rank)`, must be harness bug 22, harness bug
      26, verification improvement 23, app bug 21, app improvement 24 — i.e. `22 26 23 21 24` for the
      fixture's numbers. Verification: **red run on HEAD**, `tools/loop-selftest.sh --case
      prescreen#harnessItemsLeadTheQueue` → 1 failed, and the failure text names the HEAD order with the app bug
      at rank 1. Record it as `evidence/red-on-head.txt`. (Red on HEAD `ef2c092` from a detached worktree, as
      re-recorded after 7.2 added issue 26: `expected '22 26 23 21 24', got '21 22 26 23 24'`.)

## 2. The role and the sort

- [x] 2.1 `tools/tooling.json` → `roles.firstCategories`: `["build-and-harness", "verification",
      "specs-and-process"]`, and the `roles.comment` sentence extended so the new key names the engine that reads
      it (`tools/loop/prescreen.sh`) like the other keys do. Verification: `jq -e '.roles.firstCategories' tools/tooling.json`
      resolves, `openspec doctor` still clean.
- [x] 2.2 `tools/fixtures/foreign-project/tools/tooling.json` → `roles.firstCategories: ["infra"]` (a value of
      that project's own category vocabulary), so `check-portability.sh` proves no engine spells this repository's
      values; that the list is *read* rather than spelled is proven by `prescreen#aRenamedLeadingCategoryLeads`,
      which leads on a value this project never names. Verification: `tools/check-portability.sh` OK, foreign
      fixture included.
- [x] 2.3 `tools/loop/lib.sh` (`LOOP_ROLE_KEYS`) declares `firstCategories`, so a profile that omits it stops with
      the existing named reason instead of silently ranking by class. Verification: a case that removes the key
      from a copied profile fails with that message; add it as
      `case_prescreen_priorityCategoriesAreReadFromTheProfile` — an empty list keeps the class order, an absent
      key dies.
- [x] 2.4 `tools/loop/prescreen.sh`: extend the sort's key to `[category leads, class order, issue number]`, where
      "leads" is the index of the item's category in `loop_role firstCategories` (`// length` for an unlisted
      category), read once before the jq call like `worked_classes` is. No verdict, label or record field
      changes. Verification: `tools/loop-selftest.sh` fully green (the new case and the existing
      `prescreen#ranksBugsFirstAndDropsFeatures`, which must keep its `21 22 23 24` order).

## 3. The rank-is-not-a-gate case

- [x] 3.1 `case_prescreen_priorityDoesNotBypassTheGate`: the held `specs-and-process` item of the `categories`
      fixture is excluded with its hold verdict, is not ranked, and its category writes no label. Verification:
      the case asserts the verdict string and the absence of the item from the ranked set; green after 2.4,
      and green on HEAD as well (it is the guard, not the red case).

## 4. The order is documented where it is claimed

- [x] 4.1 `.pi/skills/fix-loop/SKILL.md`, "The eligibility gate (phase B)": the sentence "it ranks bugs before
      improvements" becomes the two-key statement — the profile's leading categories first, then bugs before
      improvements, oldest first. Verification: `grep -n 'A leading category is a rank'
      .pi/skills/fix-loop/SKILL.md` finds the rule, and the surrounding paragraph names `firstCategories`.
- [x] 4.2 `.pi/skills/fix-loop/REFERENCE.md`, "Measured costs": the line "the class is a ranking input and not a
      gate" gains the category beside the class. Verification: structural read; no build.
- [x] 4.3 `openspec/changes/fix-loop-harness-first/proposal.md` and `design.md`: no further edit needed — they
      already state the two-key order; confirm they do not contradict 4.1. Verification: a read of the two
      sentences side by side.

## 5. Falsification

- [x] 5.1 Mutation 22.1 in `tools/loop-mutations.sh`: drop the category key from the sort (`prescreen.sh`, the
      `_rank` expression → the class index only). It must go red on `prescreen#harnessItemsLeadTheQueue` — and on
      `prescreen#aRenamedLeadingCategoryLeads`, which asserts the same key from a renamed set — and green again
      after the restore. Verification: `tools/loop-mutations.sh --only 22.1` → 1 red, restore green; record as
      `evidence/revert-check.txt`.

## 6. The change's own gate

- [x] 6.1 `tools/loop-selftest.sh` green, `tools/check-portability.sh` green (its engine and value counts quoted
      from that run). Record as `evidence/gate.txt`; also copy the red-run output to `evidence/red-on-head.txt`
      and the post-fix run to `evidence/green-on-fix.txt`. (Measured: 85 cases passed, 42 engines / 98 values.)
- [x] 6.2 `tools/loop/run.sh --dry-run --items 1 --minutes 1` against the real tracker: the printed queue names a
      `build-and-harness` item at rank 1 (#126 measured 2026-10-19, with #198 at rank 4 behind the three older
      harness bugs and above every app item). Verification: quoted in `evidence/gate.txt`; the dry run writes no
      label and creates nothing.
- [x] 6.3 The evidence pointers in the proposal resolve: every `file:line` names what it claims after the fix
      (`prescreen.sh:330-341` for the sort, `tooling.json:41` for the role, `check-portability.sh:172` for the
      derived value set). Verification: each re-read with `sed -n '<range>p'` — note that a range shifts when
      lines are added above it, which is why the citation was re-checked rather than trusted.

## 7. The review round's corrections

An independent read-only review reported FAIL with corrections-only findings; these tasks close them. All three are
inside the change's own scope — a corrected number, a case for a scenario arm the change itself wrote, and the role
list on the line this change edits.

- [x] 7.1 Corrected the change record's numbers against the artifacts that carry them: `design.md` D2 (the 15
      `improvement/build-and-harness` items the queue holds, not "8"; #189 is a bug, #197 is stale — the example
      list is gone), `design.md` §Verification (42 engines and 2 templates, not 40), `design.md` D3 (#194 open at
      writing, `settled` since), `proposal.md` §Why (the 14-item listing as it was read on 2026-10-19, beside the
      dry run's 19-of-24 measurement), and the elision marker in `evidence/gate.txt`. Verification:
      `.pi/skills/fix-loop/scripts/evidence-check.sh` PASS and each number re-read from `.pi/issue-loop/run.json`
      or from the gate output it names.
- [x] 7.2 Two cases for the scenario arms the delta wrote but no case exercised:
      `prescreen#aRenamedLeadingCategoryLeads` (a profile that leads on `ui`, so the declared value is ordered on
      rather than merely scanned) and a second harness bug in the `categories` fixture, which gives
      `prescreen#harnessItemsLeadTheQueue` the class order *inside* the leading group (two leading bugs, then the
      leading improvement). Verification: both cases PASS, and `prescreen#priorityCategoriesAreReadFromTheProfile`
      still asserts the empty-list order — 85 cases in the suite.
- [x] 7.3 The role list on the edited line: `LOOP_ROLE_KEYS` gains `heldVerdict`, which `prescreen.sh` reads and
      `tools/tooling.json` declares, so a half-adapted profile stops with a named reason instead of reporting an
      empty verdict; `tools/fixtures/foreign-project/tools/tooling.json` declares it too, and
      `prescreen#anUndeclaredRoleStopsTheScreen` holds it. Verification: that case PASSES, the full suite is green
      and `tools/check-portability.sh` is OK with the foreign fixture.
- [x] 7.4 The review round 2's corrections: the gate script's own timings and an omission marker in
      `evidence/gate.txt` (written after the run, not before it), `design.md` §Verification and 5.1 no longer claim
      mutation 22.1 fails one case only (the renamed-set case asserts the same key), 6.1's count corrected to the
      artifact's 85, and the third scenario's author arm given a case — issue 27 in the `categories` fixture, a
      leading-category bug by an unauthorised author, asserted `author-not-allowlisted` and unranked in
      `prescreen#priorityDoesNotBypassTheGate`. Verification: the full suite green, `evidence-check.sh` PASS.
