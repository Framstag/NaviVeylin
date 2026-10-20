# Tasks — fix-loop-item-floor-from-measured-cost

Parent spec for every task below: `openspec/specs/agent-issue-loop/spec.md`, requirement **"One command starts a
budgeted run, with no dialogue"**. The delta is
`openspec/changes/fix-loop-item-floor-from-measured-cost/specs/agent-issue-loop/spec.md`
(two scenarios added: "The floor the run refuses by is what its own items cost", "A run with no measurement
keeps the configured minimum").

## 1. Reproduce the defect

- [x] 1.1 Add `state#floorIsDerivedFromTheFinishedItemsWallTimes` to `tools/loop-cases.sh`, registered in
  **both** the `--list` array and the dispatch map: `init`, a queue, then three `done --wall 3600`, then `next`
  with 28 min left must refuse naming `90 min floor`, `median 60 min over 3 finished items` and
  `configured minimum 45`; the same floor must not block a run with 180 min left; three 10 min skips must keep
  the configured 45; and one 100 min item among cheap ones must not raise it. **Parent:** the delta scenario
  "The floor the run refuses by is what its own items cost", case
  `state#floorIsDerivedFromTheFinishedItemsWallTimes`. **Verification:** on HEAD `10a832f` the case fails with
  `the floor is the measured one, not the constant: '90 min floor' not found in: only 27 min left, below the
  45 min floor for the next item` — `evidence/red/floorIsDerivedFromTheFinishedItemsWallTimes.txt`.
- [x] 1.2 Add `state#floorWithNoHistoryIsTheConfiguredMinimum` the same way: a run with nothing finished, and a
  run whose only finished item carries no `--wall`, both keep the configured minimum and both refusals say
  `no finished item records a wall time, configured minimum 45`. **Parent:** the delta scenario "A run with no
  measurement keeps the configured minimum", case `state#floorWithNoHistoryIsTheConfiguredMinimum`.
  **Verification:** on HEAD it fails with `the refusal names the number it compared against: 'configured
  minimum 45' not found in: only 29 min left, below the 45 min floor for the next item` —
  `evidence/red/floorWithNoHistoryIsTheConfiguredMinimum.txt`.

## 2. The fix

- [x] 2.1 `tools/loop/state.sh` `cmd_next`: derive the floor as
  `max(LOOP_ITEM_FLOOR_MINUTES, ceil(median(wallSeconds) / 60 * 1.5))` from
  `[.items[].metrics.wallSeconds | select(. > 0)]`, and make the refusal name the floor, the median, the item
  count and the configured minimum (or, with no measurement, say so). The file header's summary of `next`'s
  conditions says the floor is derived and bounded below. **Parent:** both delta scenarios. **Verification:**
  the two cases of task 1 go green — `evidence/green/`.
- [x] 2.2 `tools/loop/loop.conf`: document `LOOP_ITEM_FLOOR_MINUTES` as the floor's **lower bound**, with the
  derivation (median × 1.5, rounded up) and the measurements behind the margin (40–65 min a change, 10–15 min
  a skip, 100 min for #126). **Parent:** the delta scenario "The floor the run refuses by is what its own items
  cost". **Verification:** `grep -n -A8 LOOP_ITEM_FLOOR_MINUTES tools/loop/loop.conf` names the derivation and
  the same 45; `state#projectKeysAreReadFromLoopConf` and `state#defaultsComeFromLoopConf` still pass with the
  substituted configuration.

## 3. The documents agree with the code

- [x] 3.1 `.pi/skills/fix-loop/SKILL.md`: the stop-condition list states the measured floor and quotes the
  two-number refusal, and the "never start an item you cannot finish" paragraph states the other direction
  (never stop with time a cheap item could have used) and why `done --wall` is what makes the next floor
  honest. **Parent:** both delta scenarios. **Verification:** read back against `state.sh` — the format string
  in the skill is the one the case asserts.
- [x] 3.2 `.pi/skills/fix-loop/REFERENCE.md` "Measured costs": the #126 item (100 min) joins the table, and the
  floor arithmetic with the 1.5 spread is stated beside the numbers it is derived from. **Parent:** the delta
  scenario "The floor the run refuses by is what its own items cost". **Verification:**
  `grep -n 'median' .pi/skills/fix-loop/REFERENCE.md .pi/skills/fix-loop/SKILL.md tools/loop/loop.conf` names the
  same rule in all three.
- [x] 3.3 `guidelines/Backlog.md` needs no edit: §11's key table lists the loop's recorded defaults, and
  `LOOP_ITEM_FLOOR_MINUTES` was never in it (`grep -n 'ITEM_FLOOR' guidelines/*.md` — no match before or after
  this change); §7 describes the child wall-clock, not the floor. Named here so the absence is deliberate.
  **Parent:** "The run's mechanics are the project's configuration, not its code". **Verification:** the grep
  above.

## 4. Verification (offline; this change compiles nothing)

- [x] 4.1 Structural check instead of a build: no Kotlin, Java, C++, Gradle, manifest or resource file is
  touched, so `guidelines/Build.md` §2's scope block applies and the structural check is `bash -n` on the two
  edited shell files plus the self-test gate for `tools/loop/**`. **Verification:**
  `bash -n tools/loop/state.sh tools/loop-cases.sh`, and the cases below. **Exclusion:** no Gradle build and no
  JVM suite is run, because nothing they compile is touched.
- [x] 4.2 The two new cases, green on the fix: `bash tools/loop-selftest.sh --case
  'state#floorIsDerivedFromTheFinishedItemsWallTimes'` and `--case
  'state#floorWithNoHistoryIsTheConfiguredMinimum'` — `evidence/green/`.
- [x] 4.3 The two existing cases that own this surface stay green: `state#nextHonoursEveryStopCondition` (its
  fifth condition is the floor, and it now takes the no-history branch) and `run#stopReasonIsPrintedAndReported`
  (the run records and prints the reason `next` named, whatever the wording) —
  `evidence/green/existing-surface-cases.txt`. **Exclusion:** the whole offline suite is **not** run here; the
  orchestrator runs it once on the merged tree, and no full-suite tally is quoted as green by this change.
- [x] 4.4 Falsification: restoring HEAD's `cmd_next` floor branch (the single mutation — the constant without
  the median) turns **both** new cases red, which is exactly `evidence/red/`. No further revert-check is owed:
  the derived floor *is* the fix, not an invariant added beside the original symptom (config `tasks` rule for
  `fix-*` changes). **Verification:** `evidence/red/` against `evidence/green/` on the same host.
- [x] 4.5 The derivation on the histories the design argues from, driven through `state.sh` (no run, no token,
  no child): `evidence/green/floor-derivation-numbers.txt` — no history 45 · skips 10/10/10 → 45 · changes
  45/55/60 → 83 · mixed 12/12/12/100 → 45 · one 100 min item → 150. **Parent:** the delta scenario "The floor
  the run refuses by is what its own items cost". **Verification:** the refusal line printed for each.
- [x] 4.6 `bash tools/check-portability.sh --quiet`, `openspec validate
  fix-loop-item-floor-from-measured-cost --strict` and `openspec doctor`, all clean.
  **Verification:** their output in `evidence/green/validation.txt`.
