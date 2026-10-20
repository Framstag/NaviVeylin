# Design

## Context

The run's Phase A already produces everything a falsification needs: the item's blamed mechanism, the
measurement that contradicts it, and the reviewer's reading of the diff (empty, for a falsification). What
is missing is the *permission* to act on it and a place to record that it happened. Measured 2026-10-20:
three of the first four items of one run ended as falsifications, each escalated.

Invariants this design may not break (they are the reason the current behaviour exists):

- **Closure needs a reason and evidence.** `tools/issues/close.sh` refuses without both, and the reason is
  posted before the close. A falsification has both; it just has a different reason ("the premise does not
  hold") than a fix ("the change landed").
- **The run never closes what its caller did not allow.** `--may-close no` exists so a person decides.
- **The writer is not the reviewer.** A claim about the tree is reviewed by a fresh process, even when no
  file changed.
- **Reserved labels stay unwritten by the loop.** `agent/eligible` is not touched by any of this.

## Decisions

### D1 — Where a falsification is acted on: inside the item, not in a later pass

**Alternatives.**

1. **A second, cheaper pass after the run** re-reads each skipped item's report and closes the falsified
   ones. Cost: the run must keep the reports for a later reader, and the pass is another code path that can
   disagree with the run about what "falsified" means (the report is text; the run's state is data). It also
   runs *after* the decision label was written, so it must undo an escalation.
2. **A periodic GitHub worker** (`sensors.yml` cadence) reads the falsified skips and closes them. Rejected:
   that worker has no model, and its whole contract is "a check measures a condition and closes what it
   owns". "The premise does not hold" is a judgement about prose and code together — it is exactly the class
   of decision the tracker's tooling refuses to automate (`close.sh` needs a reason a script cannot derive,
   `update.sh` refuses reserved labels).
3. **Inside the item, at the moment the worker reports it** — chosen. The evidence is in hand, the reviewer
   reads it in the same round budget, the state records the outcome, and the tracker write goes through the
   paths that already exist.

**Rationale.** (3) is the only option that keeps one definition of "falsified" — the worker's field as the
reviewer checked it — and the only one whose failure mode is a *wrong escalation* (harmless, today's
behaviour) rather than a wrong closure made by a party that never saw the tree.

**Risk.** A worker over-claims falsification to avoid work. Mitigations, all cheap: the field requires a
measurement that names how it was taken (case name or command) and a diff that is empty; the reviewer is
told to check that the measurement covers the blamed mechanism; the item's closure comment names the
measurement, so a wrong one is readable; and the run's report counts
"mis-diagnosed" separately, so a run that closes five items this way is visible rather than triumphant.

### D2 — The closure rides the existing `--may-close`, not a new flag

**Alternatives.** (a) A separate `--may-close-falsified` flag — rejected: two permissions for one question
("may the run end items?") and a caller who sets one and forgets the other gets surprising behaviour.
(b) Always close, ignore `--may-close` — rejected: it breaks the invariant that the run closes only what its
caller allowed. **Chosen: (a)'s opposite — the existing flag.** A caller who wants the run to think but not
to end items keeps `--may-close no`: the measurement is still posted as a comment and the item is left exactly as
it arrived — no settled state, no decision label, and it is queued again next run.

### D3 — The reviewer must see it, even though the diff is empty

**Alternatives.** (a) No review for a zero-diff item: cheapest, and the run would need a special case in the
round budget. (b) Reviewed like any change — chosen. A falsification is the one outcome where the diff
cannot show whether the worker is right; the report is the whole claim, so removing the reviewer would
remove the only independent read. Cost: the item still pays one review round; that is the price of not
turning "I could not do it" into a closure.

**Guard.** The reviewer's brief keeps its current shape ("blocking or a note"), and a falsification with no
measurement, or with a measurement that does not cover the blamed mechanism, is a blocking finding — which
puts the item back on today's path (escalation) instead of closing it. **The caveat the fix path already has:**
`--review off` waives the review for every closure, so a run started that way closes a falsification with
`review round 0`; the change states it rather than implying a guarantee the flag does not give.

### D4 — Which skips keep escalating

Unchanged, and the design's most important boundary:

| skip | today | after this change |
|---|---|---|
| the cause does not hold, measured | escalated | **closed as mis-diagnosed** (`--may-close yes`) |
| too big for one fix / spec-shaped | held (`comment_only`) | held |
| needs a device | held + `agent/needs-device` | held + `agent/needs-device` |
| needs diagnosis (mechanism not isolated) | escalated | escalated |
| the worker reached a decision the owner must make | escalated | escalated |
| blocked (child claim without commit, gate, timeout, review refusal) | escalated | escalated |

The test for the first row is deliberately narrow: an empty diff **and** a measurement **and** the named
mechanism, all three in the machine-readable answer. Two of three is an escalation.

## Files and structure

| file | change |
|---|---|
| `tools/loop/run.sh` | the skip path routes the falsified verdict to `close.sh --not-planned` after `loop_labels unpick`; the item is recorded with result `falsified` and the reviewer's round |
| `tools/loop/prompts/worker.md` | the answer block gains `LOOP-FALSIFIED:` (blamed mechanism), `LOOP-MEASURED:` (number and how it was taken), and the requirement that the diff is empty |
| `tools/loop/prompts/reviewer.md` | one paragraph: a falsification is a claim, checked like a change |
| `tools/loop/state.sh` | accepts `--result falsified` and counts it in the progress line, so the run's bookkeeping knows the outcome it writes |
| `tools/loop/report.sh` | counter and column for "closed as mis-diagnosed", read from `--result falsified`; a falsified item is not asked for a gate it never had |
| `tools/loop-cases.sh` | `run#falsifiedSkipClosesWithItsMeasurement`, `run#falsificationWithoutAMeasurementIsEscalated`, `run#mayCloseNoKeepsAFalsificationOpen`, `run#falsificationWithAChangedTreeIsBlocked`, `report#misdiagnosedIsCountedApart` |
| `tools/loop-mutations.sh` | `30.1` — route the falsified verdict back to the skip, and `30.2` — drop the second read of the tree; each named case must go red |
| `guidelines/Backlog.md` §5 | the rule: a reviewed falsification closes as mis-diagnosed, only on an unchanged tree, under `--may-close` |
| `openspec/specs/agent-issue-loop/spec.md` | the delta |

No threading, lifecycle or Compose surface is touched: this is a shell gate, offline, no token, no model,
no device.

## Verification

- Unit level = the loop's own suite, offline: the three cases above, red before the change (the first one is
  the defect's red case: today the run escalates an item whose premise it measured away) and green after,
  plus `revert-check` via mutations `30.1` and `30.2`.
- Structural: `tools/check-portability.sh --quiet` (the new field names no project value),
  `openspec validate falsified-items-close-in-the-loop --strict`.
- No Gradle gate: the delta touches no compiled file (`guidelines/Build.md` §2, the scope block) — the
  suite and the validation are the gate, and the exclusion is named in `tasks.md`.
- On-device: none; the change has no application surface.
- Live check after merge: one run on the real tracker whose queue still holds §-imported items, and its
  report must show `closed as mis-diagnosed` for the ones it falsifies instead of a decision per item.
