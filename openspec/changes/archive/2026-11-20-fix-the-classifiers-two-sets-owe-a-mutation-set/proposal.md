# Proposal — the classifier's two report-only states owe a versioned mutation set

## Why

**Root cause.** The split of `pending` from `stale` landed as four expressions and two figures
(`tools/gen-feature-list.sh:204`–`210`) and was held by assertions on those figures alone
(`tools/tests/gen-feature-list-selftest.sh:325`, `:353`, `:354`) — one suite, no versioned mutation set, and no way to
run one: the suite took `--verbose` and nothing else, so a record's `case-cmd` had no case to name. The tree declares
`tools/gen-feature-list*.sh` against the suite in `tools/loop/loop.conf` and against no mutation set, and the
project's declared sets name no record for the file:

```
$ bash tools/loop/tests/loop-mutations.sh --list | grep -c gen-feature-list
0
```

**The red case.** A case now reads the record set this change writes:

```
$ bash tools/tests/gen-feature-list-selftest.sh --case "mutation: the classification's two sets and their counts are held by a record set"
FAIL mutation: the classification's two sets and their counts are held by a record set
     expected [5], got [0]
gen-feature-list-selftest: 0 passed, 1 failed
gen-feature-list-selftest: 1 of 1 cases failed
```

(`evidence/red-on-base.log`, exit 1, 2026-11-20T06:07:36Z.) It fails on the base because the set it names does not
exist, and it is the case the delta's first scenario names.

**What the item's own Consequence claims, and what was measured instead.** The item says the four expressions "can be
mutated … and the suite still prints PASS". Measured on the base — HEAD's `tools/gen-feature-list.sh` and HEAD's
selftest in a throwaway root, one record per expression, the whole suite as the case command — the assertions notice
every one of them:

```
$ bash tools/mutate.sh --mutations .pi/issue-loop/items/926/base-suite-mutation-probe.json --root <base tree>
mutate: 5 mutation(s) run, 5 went red and restored green, 0 problem(s)
```

(`evidence/base-suite-mutation-probe.log`; the probe, its record set and its control run `all 138 cases passed` are
beside it.) So the item is **not** a report of unguarded behaviour, and the change does not claim one: what is absent
— and what the item's own `Observed` line states, correctly — is the *versioned* form. An assertion on a figure is
written and edited together with the expression beside it, which is how the split itself landed; a record that names
the case it reds survives that pair edit, is runnable in one command by anyone (`tools/mutate.sh`,
`.pi/skills/mutation-runner`), and is what every other invariant in this batch of items carries. That is the gap this
change closes, and the rule it serves is the one `guidelines/BuildEvidence.md` §4e/§4f states for every invariant — a
case that a single mutation makes fail — whose versioned form for a shell gate is `tools/mutate.sh` with the
`mutation-runner` skill the `guidelines/Build.md` table routes a `tools/**` change to.

**The owning requirement.** `openspec/specs/spec-feature-index/spec.md`, "An unclassified spec id stops the run" — the
requirement the two states belong to; the delta adds its sibling, "The classifier's two report-only states are held by
a mutation set".

**The owning guideline section.** `guidelines/FeatureList.md` §4, "The classification is the only human judgement, and
an unclassified spec stops the run" — its two-state bullet is where a reader learns the split exists, and it now names
the set and the command that hold it.

**Surface count.** `tools/look.sh 'gen-feature-list-mutations.json'` names the set this change writes and no other
holder; the case surface is one suite gained and one assertion set (`:325`, `:353`, `:354`) left exactly as it stands.

surface: 1 case added, 3 cases named by 5 records, 0 rewritten, 5 files edited outside this change's artifacts (one of
them new), 0 production files — tools/look.sh 'gen-feature-list-mutations' --files (16 matches in 8 files; of those,
`tools/loop/loop.conf`, `tools/tests/gen-feature-list-selftest.sh`, `tools/tests/gen-feature-list-mutations.json`,
`guidelines/FeatureList.md` and `.github/workflows/build.yml` are the tree's, the other three this change's own)

## What Changes

- **`tools/tests/gen-feature-list-mutations.json`** — a new versioned set, one record per expression: which set a
  not-shipped id is (`tools/gen-feature-list.sh:204`, `:205`, `:206`) and which set each figure counts (`:209`,
  `:210`). Each record names the case it reds; `case-cmd` runs that one case.
- **`tools/tests/gen-feature-list-selftest.sh`** — `--case <name>` runs one case of the suite. A case is identified by
  the name it is recorded with, which is the string the spec's `**Case**:` lines and a record's `case` field quote, so
  one string identifies the assertion in all three places; `--case` naming no case of the suite exits 2, so a record
  cannot report a red case that never ran. The suite gains the `mutation:` case that pins the set.
- **`tools/loop/loop.conf`** — four `LOOP_GATE_SUITES` entries: the tool, the selftest and the set each owe the set's
  `--list`, and the selftest owes itself, so a set that no longer parses fails the gate instead of rotting.
- **`.github/workflows/build.yml`** — the `--list` beside the suite in the feature-list tooling step, for the same
  reason the documentation-routes and rule-routes sets are listed there.
- **`guidelines/FeatureList.md` §4** — the two-state prose names the set, the command and the case that keeps it
  honest.

## Impact

Files: `tools/tests/gen-feature-list-mutations.json` (new), `tools/tests/gen-feature-list-selftest.sh`,
`tools/loop/loop.conf`, `.github/workflows/build.yml`, `guidelines/FeatureList.md`, and this change's own artifacts.
No production file changes: `tools/gen-feature-list.sh` is not edited, and no Android module, resource or manifest is
touched. **Additive** — a new set, a new flag with its previous behaviour as the default (the suite with no argument
runs exactly what it ran before), and four declarations. **Rollback** — delete the set and the four entries, and drop
the `--case` arm; nothing else reads them.

Spec delta: `spec-feature-index`, one requirement ADDED. No capability is introduced, so no classification line is
owed (`guidelines/FeatureList.md` §9).
