# Design — the classifier's two report-only states owe a versioned mutation set

Change: `fix-the-classifiers-two-sets-owe-a-mutation-set`. Spec delta: `spec-feature-index` (one requirement ADDED,
six scenarios). Item: #926.

## Root cause

One mechanism, and it is the absence the item's `Observed` line names: the split of `pending` from `stale` landed as
four expressions and two figures held by assertions in one suite
(`tools/tests/gen-feature-list-selftest.sh:325`, `:353`, `:354`), and the tree carries no mutation record for
`tools/gen-feature-list.sh` in any declared set (`bash tools/loop/tests/loop-mutations.sh --list | grep -c
gen-feature-list` → 0) — nor could it, because a record's `case-cmd` needs a case it can run alone and the suite took
`--verbose` and nothing else. So the invariant lived exactly as long as nobody edited the assertion and the expression
in the same commit, which is how the split itself landed.

The item's own *Consequence* sentence claims more than that: that the four expressions "can be mutated … and the
suite still prints PASS". That was measured and does not hold — HEAD's tool and HEAD's suite, five records, the whole
suite as the case command: `5 mutation(s) run, 5 went red and restored green`
(`evidence/base-suite-mutation-probe.log`). This change therefore does not repair unguarded behaviour; it writes the
versioned artifact the item asks for, and the delta's scenarios state that artifact and what it reds, not a coverage
gap that was not there.

## The single fix

Three edits and one declaration, none of them production code:

1. `tools/tests/gen-feature-list-selftest.sh` gains `--case <name>`: a case is identified by the name it is recorded
   with — the same string the spec's `**Case**:` lines and a record's `case` field quote — the filter is applied in
   the `ok`/`bad` helpers the file's assertions already go through, and a filter that names no case exits 2 after
   the run. The fixture commands a case shares still run, because a case here is an assertion over output a shared
   preamble wrote.
2. `tools/tests/gen-feature-list-mutations.json` — five records, one per expression: `:204`, `:205`, `:206`, `:209`,
   `:210`, each naming the case it reds.
3. `tools/loop/loop.conf` gains four `LOOP_GATE_SUITES` entries, so the tool, the suite, the set and the set's own
   `--list` are what the gate selects for these paths, and `.github/workflows/build.yml` prints the set's `--list`
   beside the suite.
4. `guidelines/FeatureList.md` §4 names the set, the command and the pin, beside the two-state bullet a reader meets
   first.

## Blast radius

- `tools/tests/gen-feature-list-selftest.sh` — one argument arm, one filter in `ok`/`bad`, one new case name with six
  assertions. With no argument the suite runs exactly what it ran before: `144 passed, 0 failed`
  (`evidence/selftest-green.log`).
- `tools/tests/gen-feature-list-mutations.json` — new, read by `tools/mutate.sh` and by the new case.
- `tools/loop/loop.conf` — four entries on the declared-checks line. A `tools/**` path now selects this set's
  `--list`, which is the cheapest form of the entry (`guidelines/BuildGate.md` §2a's plan puts the cheapest check
  first).
- `.github/workflows/build.yml` — one command inside an existing step.
- `guidelines/FeatureList.md` §4 — one paragraph; no route, no figure, no `**Rule:**` name is added, so the
  documentation checks read it as prose.
- No production file, no Android module, resource, manifest or test source outside `tools/`.

## Alternatives the evidence eliminated

- **Put the records in `tools/loop/tests/loop-mutations.sh`, which is what the item proposes.** Impossible: that
  harness's `case` field is a case of `tools/loop/tests/loop-selftest.sh` (the file's record format, `label @ fifo @
  file @ case @ …`, and its `case-cmd` equivalent runs the loop's own suite), and the classifier's cases live in
  `tools/tests/gen-feature-list-selftest.sh`. A record naming a case the harness cannot run reds nothing.
- **Run the whole suite per record (`case-cmd` with no `{case}`).** Rejected on what a record claims: the harness
  reads the exit status only, so a record would go red on *any* failing case and its `case` field would name nothing
  that was measured. Measured cost of the alternative is no cheaper either — the suite builds its fixture tree per
  invocation, and a filtered run takes the whole run's time (19 s against 18 s, `time` on both).
- **Give every assertion a `suite#case` id, the way `tools/tests/check-doc-routes-selftest.sh` does.** Rejected: the
  suite's case names are already quoted by `openspec/specs/spec-feature-index/spec.md`'s `**Case**:` lines and by
  `guidelines/FeatureList.md` §4's case list, so a second identifier per case would be a second name for one thing —
  and the reason a record cannot name a case today is not a missing id form but a missing filter.
- **Fix the item's premise instead of the artifact** (skip it as falsified). Rejected as the *change*: the absence
  itself is real and verified by the item's own command, and one mutation per invariant, versioned so that it outlives
  the assertion beside the expression, is what `guidelines/BuildEvidence.md` §4e/§4f requires through
  `.pi/skills/mutation-runner`. The contradicted sentence is stated in `proposal.md` rather than hidden, so a reader is
  not handed the item's claim as if it were measured.

## Open decisions

none

The change touches no open fork — one fix, four edits and one declaration. The three forks this item could have
opened (where the records live, whether a record pays one case or the whole suite, whether the premise the base probe
contradicts is a reason to skip the item instead) are settled by the measurements in `## Alternatives the evidence
eliminated`, so nothing is left for a reader to choose.

## Risks

- A record's `search` can be orphaned by an edit to the tool. Pinned by the new case, which requires each search to
  match exactly one line, so the failure arrives as a red case in the suite the gate runs.
- A `case` field can be renamed out from under a record. Pinned by the exit-2 arm: the harness then sees a case that
  never passes and reports `RESTORE-STAYS-RED` instead of a green run.
- The four `loop.conf` entries widen what a `tools/gen-feature-list*.sh` change owes by one `--list` call (seconds,
  no Gradle).

## Verification

Focused, not a gate: the red case on the base (`evidence/red-on-base.log`), the set through `tools/mutate.sh`
(`evidence/mutation-set-per-record.log`, five `--only` invocations, each `1 mutation(s) run, 1 went red and restored
green, 0 problem(s)`), the whole suite green (`evidence/selftest-green.log`), and the structural checks a
delta that compiles nothing owes (`evidence/check-*.log`). The gate belongs to the child this run starts after the
diff (`tools/loop/prompts/worker-gate.md`).
