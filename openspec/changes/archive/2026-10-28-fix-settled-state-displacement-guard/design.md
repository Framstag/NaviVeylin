# Design — fix-settled-state-displacement-guard

## Root cause

One mechanism. `tools/issues/update.sh` completes a state write by removing every other value of the status
namespace in the same edit — `issues_status_displaced` (`tools/issues/lib.sh:331-336`) is read into
`implied_removals` (`tools/issues/update.sh:172-175`) and handed to `gh` beside the add (`:184-186`) — and the
scan is a pure namespace filter: it knows the prefix, not what the values *mean*. The profile already says
which values mean "the item's premise is settled" (`roles.settledStatuses`, `tools/tooling.json:53`, read by
the pre-screen at `tools/loop/prescreen.sh:258`), so a park, a verdict's hold or a resolver's reopen removes
`fixed-by`/`retired` with the same call it uses for `open`, silently. #681 corrected the *reading* side of one
of those reversals; the writing side is untouched, and it is the side every writer shares.

## The single fix

The meaning the scan lacks is added where the scan lives, so both writers keep one home for it:

- `issues_settled_statuses` (`tools/issues/lib.sh`) — the profile's `roles.settledStatuses`, one per line,
  refused by name when the profile does not declare the role (`lib.sh`'s "state namespace" section).
- `issues_status_settled_guard <number> <named> <displaced-label>…` — fills `ISSUES_SETTLED_DISPLACED` with the
  settled labels this write would remove and refuses (`issues_refuse status-settled`, exit 3) naming them when
  the call did not name the transition. It takes the labels the caller is about to displace rather than the
  issue, so `update.sh` hands it what it already computed and the guard makes no `gh` call of its own.
- `tools/issues/update.sh` — `--displace-settled` names the transition; the guard runs after the read and
  before the write, so a refusal leaves the item as it was; the write that passes it reports the settled value
  it removed (`issues_note`, stderr, so the `--json` document on stdout stays pipeable — the convention
  `update#statusWriteReportsAnEmptyTransition` holds). The flag names a transition, so a call that sets no
  state is a usage error rather than an ignored argument.
- `tools/issues/close.sh` — passes `--displace-settled`: a closure exists to record a settled state, and the
  loop's own closure path already writes it through this gate (`close.sh:156-179`).
- `tools/issues/reopen.sh` — calls the guard with `named=1` on its own write path (`reopen.sh:96-115`). The
  verb itself is the named transition: it may only reverse a closure recorded `completed`
  (`reopen.sh:73-86`, `closure-not-reopenable`) and it carries the returning measurement, so the state it
  displaces is the completed fix, deliberately. It passes the guard's `named` argument rather than the flag,
  and it reports the state it returns to (`reopened #N (occurrence X, status/open)`): naming the *removed*
  settled value is the flag-bearing routine write's obligation, which is where the requirement scopes it.

Nothing else moves: the pre-screen's settled rule, the resolver's restore (#681), the one-state rule, the
replacement report and every write that displaces a non-settled value are unchanged.

## Alternatives the evidence eliminates

- **Keep the profile value out of the write path and let the pre-screen catch it.** Impossible as a fix: the
  pre-screen already reads `settledStatuses` (`tools/loop/prescreen.sh:258`) and that is precisely why #146's
  loss hurt — the queue saw a candidate because the label was gone. Detection after a destructive write is a
  report about a state that no longer exists anywhere (the park's comment never carried it, #681's evidence).
- **Report the removal instead of refusing it.** `update#statusReportNamesImpliedRemoval` already reports the
  displaced value in the `--json` document, and the drift still happened: the caller that means "park this"
  does not read the report, and a report is not a gate. The refusal is what makes the routine writer stop.
- **One flag per settled target** (`--allow-fixed-by`, `--allow-retired`). A project value in an engine is the
  leak `tools/check-portability.sh` exists for; the values live in the profile, and a flag naming one would
  have to be a profile-derived spelling, i.e. the same single flag with a longer name.
- **Make the settled state a second namespace value (`status/open` beside `status/retired`).** That abandons
  the one-state rule the write path exists to hold (`issue-backlog`, "A state transition replaces the item's
  state label") and every status query would have to learn two spellings.
- **Stop the routine writer from writing a state at all** (the hold becomes a label of the agent namespace).
  That is a change to what the loop's verdicts *mean* (`tools/loop/labels.sh:95-101`, `holdStatus`), far wider
  than this defect, and it would break the pre-screen's reads of `holdStatus` — a second capability delta for
  one write.

## Blast radius

- `tools/issues/**` (lib, update, close, reopen), one case file, one guideline section, one spec requirement.
  No compiled file: `tools/**`, `guidelines/**` and `openspec/**` are outside the build's scope block
  (`guidelines/Build.md` §2), so the gate's own path map decides which shell suites are owed and no Kotlin
  suite is.
- Every caller of `update.sh` keeps its behaviour except in the one case the change is about: a write that
  displaces `fixed-by`/`retired`. `tools/loop/labels.sh` is untouched and inherits the refusal — a verdict's
  hold on a settled item now stops the path that would have erased the tombstone, which is the coverage the
  item asked for ("every writer instead of the one that was measured"). `resolve-stale.sh` inherits it the same
  way; its tombstone branch writes `retiredStatus` (a settled *target*), which displaces a hold and is
  unaffected.
- Risk: a call that used to land now refuses. The refusal names the flag, and the two verbs that mean the
  transition pass it, so the only call that newly refuses is one no path in the tooling issues.
- Rollback: revert the commit. The guard stores nothing and writes nothing, so there is no state to migrate.

## Verification

- The red case (`update#refusesToDisplaceASettledState`) is green, and the regression case for the closure
  (`close#namesTheSettledStateItDisplaces`) proves the deliberate writers still land.
- The suites and checks the pathspec owes, which the map states and `./tools/loop/measure.sh` prints before
  the run: `tools/issue-tooling-selftest.sh` (the whole issue tooling), `tools/check-portability.sh` + its
  self-test (the guard names no project value), `tools/issues/check-metadata.sh`, the mutation lists, and the
  documentation checks — `check-doc-routes`, `check-todo-references`, `check-openspec-hygiene` +
  `check-openspec-hygiene-selftest`, `gen-feature-list --check-classification` — through the project's one
  gate, `./tools/loop/gate.sh`, run once on the archived tree, so the `openspec/` pathspec that owes the last
  three is in the run rather than landing after it.
- Revert-check: one mutation in `tools/status-mutations.json` — the guard's `named` branch forced open — with
  `update#refusesToDisplaceASettledState` as the case that must go red.
- No device or screen is involved: the change is tooling.
