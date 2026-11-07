# Proposal — fix-routed-read-numbers-verbatim-cases

## Why

**The condition, verified in the tree at base `e51be3b` (2026-10-30).**
`openspec/specs/documentation-ownership/spec.md:154` opens the scenario *A split section keeps its number and
its citations* and `:159` names only `routed-read#numbers`. The scenario *The split moves text instead of
rewriting it* opens at `:169`, and its Case line at `:174`–`:175` points at `evidence/red-probe.sh` — a file
inside `openspec/changes/archive/2026-10-22-fix-build-md-routed-read-size/`, kept by the archive but run by
nothing. The suite that declares the route cases carries only two:

```
$ grep -rn 'routed-read' tools/ .github/
tools/check-doc-routes-selftest.sh:390:echo_case routed-read#bound case_routed_read_bound \
tools/check-doc-routes-selftest.sh:392:echo_case routed-read#route case_routed_read_route \
```

and the case the spec names for the split's number is not runnable at all:

```
$ bash tools/check-doc-routes-selftest.sh --case routed-read#numbers
check-doc-routes-selftest: no case with the id 'routed-read#numbers'
$ echo $?
2
```

**Root cause, one sentence.** Two Case lines of *The routing cost is measured* name a harness the tree does not
run — `routed-read#numbers` is declared nowhere in `tools/` or `.github/`, and `routed-read#verbatim` points at a
probe inside an archived change — so the shipped spec claims coverage that no suite can execute.

**Red test** (`evidence/red-on-head.xml`, `evidence/red-on-head.log`, `evidence/red-probe.sh`, run on the base
commit's content at 2026-10-30T07:22:14Z): the probe reads both scenario blocks out of the shipped spec, requires
`routed-read#numbers` to be declared and to pass, and requires the verbatim Case line to name no archived probe;
on HEAD it reports `tests=1 failures=1`:

```
FAIL routed-read#the-two-case-lines-resolve — the two Case lines do not resolve:
  [declared: routed-read#numbers]
  [run: routed-read#numbers: check-doc-routes-selftest: no case with the id 'routed-read#numbers']
  [verbatim: names evidence/red-probe.sh]
  [verbatim: says no harness asserts it]
```

**Surface.** On the base the command
`grep -rn 'routed-read#numbers\|routed-read#verbatim' --include='*.md' --include='*.sh' --include='*.json' . |
grep -v openspec/changes/archive` reports `2` lines in `1` file, both in the shipped spec (`:159`, `:174`), and
`grep -rn 'echo_case routed-read#' tools/check-doc-routes-selftest.sh` reports `2` lines (`:390`, `:392`,
`#bound` and `#route`). Nothing else asserts either name: no guideline restates them, no mutation record names
them, and no fixture carries them. This change adds one case to the route suite and restates two spec Case lines;
it adds no Gradle case, rewrites no case and realigns no fixture.

surface: 1 case added (`routed-read#numbers`), 1 mutation record added (`guideline-body-reference`), 2 spec Case lines restated (`#numbers`, `#verbatim`), 0 cases rewritten, 0 fixtures realigned, 0 compiled files — `grep -rn 'routed-read#numbers\|routed-read#verbatim' --include='*.md' --include='*.sh' --include='*.json' . | grep -v openspec/changes/archive` (`2 lines in 1 file` on the base tree) and `grep -n 'echo_case routed-read#' tools/check-doc-routes-selftest.sh` (`2 cases` on the base tree)

**Owning capability and guideline section.** The capability is `documentation-ownership`; the rule is its
requirement *The routing cost is measured* (`openspec/specs/documentation-ownership/spec.md:117`), which
`AGENTS.md`'s Documentation Map routes for "how the guidelines themselves are organized". The two scenarios
state the split's preservation half — the parent number and its citations, and the split's text — while the
sibling scenarios already own the ruler (`routed-read#bound`), the row's cost (`routed-read#route`) and the
bound's condition (`sensors#routedSectionSizeRow`). `guidelines/Build.md` §4f owns the evidence rules this
change's archive cites; it does not change.

## What Changes

- Add `routed-read#numbers` to `tools/check-doc-routes-selftest.sh`: a fixture document keeps `## 1.` beside
  `## 1a.` and `## 1b.`, its body cites all three, the routing table routes the two sub-sections, and the route
  check accepts the set; removing the parent heading makes the `§1` citation fail, and removing a sub-section
  heading makes its citation fail. The suite's own header comments gain the third case.
- Add one mutation record (`guideline-body-reference`) to `tools/check-doc-routes-mutations.json`, so the new
  case is falsified: with the body-reference extraction replaced by a no-op the case goes red, and the restore
  returns it green.
- Restate the two Case lines in a `documentation-ownership` delta: `#numbers` now names the fixture case and
  what it asserts; `#verbatim` says the property is a one-off judgement over the split's diff, recorded by the
  change that made it, and that no harness in the tree asserts it over a later tree — so it no longer points at
  a file in an archived change.
- Add the change's evidence probe (`evidence/red-probe.sh`), which fails on HEAD and passes once the case exists
  and the restated lines are archived into the shipped spec.
- No production code, no Gradle test source, no fixture and no other requirement changes; the requirement's
  normative text is carried whole in the delta because a MODIFIED block replaces it.

## Capabilities

### New Capabilities

- None — this corrects the cases two shipped scenarios name; it states no new behaviour.

### Modified Capabilities

- `documentation-ownership` — requirement *The routing cost is measured*: two scenarios' Case lines, plus the
  description of `routed-read#numbers`. The requirement's description and its other seven scenarios are carried
  unchanged, as the requirement *A modified requirement carries the scenarios its spec still has* requires.

## Impact

- `tools/check-doc-routes-selftest.sh` — one fixture case added beside `#bound` and `#route`, and the header
  comments that describe the routed-read cases.
- `tools/check-doc-routes-mutations.json` — one mutation record for the new case.
- `openspec/specs/documentation-ownership/spec.md` — via the delta
  `openspec/changes/fix-routed-read-numbers-verbatim-cases/specs/documentation-ownership/spec.md`, merged on
  archive: two Case lines restated, no requirement description changed.
- `openspec/changes/fix-routed-read-numbers-verbatim-cases/**` — the planning artifacts and evidence.
- Additive and non-breaking: no capability's contract changes, no case id changes, no bound moves, and no file a
  build compiles is touched. Rollback is deleting the change directory before archive, or reverting the one
  case in `tools/check-doc-routes-selftest.sh` and the two Case lines in the shipped spec afterwards — the tree
  carries no runtime behaviour to restore.
- Not touched: `tools/check-doc-routes.sh`, `tools/sensor-cases.sh`, `tools/sensors/sensors.json`, every Gradle
  module.
