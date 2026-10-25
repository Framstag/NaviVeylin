# Evidence — fix-metadata-drift-check-never-runs

Every number below is a command's own output, taken in this worktree on base
`0f3426564b22faf1c2b8cf022050fef498470323`.

## 1. The defect, verified against the tree

`live-check-head.log` — the check's own live mode, 2026-10-25T19:22:52Z:

```
$ bash tools/issues/check-metadata.sh --live; echo "exit=$?"
#305 label-without-marker: prio/p3
#304 label-without-marker: prio/p3
check-metadata: 306 issue(s) checked · 2 violation(s)
exit=1
```

`surface-head.log` — why nothing saw it: the only tracker-reading caller in the tree is a person's helper
(`helper/backlog-pass.sh:144`), CI runs the token-free mode (`.github/workflows/build.yml:195`), and the
registry holds eight rows, none of them this invariant:

```
$ jq -r '.rows | length' tools/sensors/sensors.json
8
```

`gate-attribution.log` repeats the registry listing on the pristine base commit (`jq -r '.rows[].id'` → the eight
ids), so "8 before, 9 after" is measured on both sides.

## 2. The red case, and its green

`red-case-head.log` — the change's own two cases on HEAD with only the case text applied, 2026-10-25T19:24:04Z:

```
$ bash tools/sensor-selftest.sh --case sensors#metadataDriftRow
  the registry declares no metadata-invariant row
FAIL sensors#metadataDriftRow
exit=1

$ bash tools/sensor-selftest.sh --case sensors#metadataDriftClearsUnderItsOwnIdentity
  the drift keeps the identity the issue holds: … metadata-invariant.sh not found in:
FAIL sensors#metadataDriftClearsUnderItsOwnIdentity
exit=1
```

`focused-green.log` — after the fix, 2026-10-25T19:24:44Z: `PASS sensors#metadataDriftRow`,
`PASS sensors#metadataDriftClearsUnderItsOwnIdentity`, `PASS workflow#aRedRecordRunsNoRecordRow`,
`PASS Registry#everyRowIsComplete`, `PASS sensors#engineNamesNoProjectValue`,
`PASS sensors#theDocumentedCredentialCoversEveryDeclaredRow`, and the falsification
`bash tools/sensor-mutations.sh --only 8.19` → `MUTATION 8.19: sensors#metadataDriftRow red under the mutation,
green after the restore` (`1 behaved, 0 did not`).

## 3. The gate — run once, and what it says

`gate.log`, `./tools/loop/gate.sh --base 0f342656…` — the line it ends with is `LOOP-GATE: tests=211 failures=0 errors=1 (…) — failing: tools/build-inputs.sh --check`, an aggregate of the 23 per-suite tallies `gate.log` lists one by one, so no single XML carries it (not retained as one file). One failing suite, `tools/build-inputs.sh --check`, and it carries no case at all.

The suite the delta's own pathspec owns is green — `tools_sensor-selftest.sh.xml`:
`tests="51" failures="0" errors="0" time="41"`, `sensor-selftest: all 51 cases passed (JDK 27, offline, no
token)`; the neighbouring ones are green too: `tools_issue-tooling-selftest.sh.xml` (`tests="121"
failures="0" errors="0" time="56"`), `tools_check-portability-selftest.sh.xml` (`tests="29" failures="0"
errors="0" time="23"`), `tools_issues_check-metadata.sh.xml` (the token-free half,
`check-metadata: 0 issue(s) checked · 0 violation(s)` — its `--live` half is the new row's job).

**The one error is not this change's, and it is not a test failure.** `tools_build-inputs.sh_--check.xml` carries
no case: `./tools/loop/gate.sh: Zeile 166: tools/build-inputs.sh: Keine Berechtigung` — the profile invokes that
script directly (`tools/build-inputs*|tools/build-inputs.sh --check`) and the file is committed without its
executable bit. `gate-attribution.log` measures it on the pristine base commit, extracted with
`git archive 0f342656 | tar -x -C /tmp/baseprobe` (ephemeral, and re-runnable in one command from the base —
not retained):

```
$ git ls-tree 0f342656 -- tools/build-inputs.sh
100644 blob 65a907c975e64541feb4a386eb143cf88623e3cf	tools/build-inputs.sh
$ (cd /tmp/baseprobe && tools/build-inputs.sh --check); echo "exit=$?"    # ephemeral, not retained
zsh:1: keine Berechtigung: tools/build-inputs.sh
exit=126
```

So the red line is a defect of the committed tree that any plan pulling `tools/build-inputs*` into it inherits —
this change's plan pulls it through `tools/sensors/sensors.json`'s mapping — and the residual is left named
rather than repaired here (`guidelines/Build.md` §4d: quote both numbers, name the residual, do not claim the
run). The gate was run once and is quoted, not retried.

## 4. Measured, not quoted from the tracker

`measure.log` — the block `tools/loop/measure.sh --base 0f342656… --change
fix-metadata-drift-check-never-runs` printed:

```
LOOP-MEASURE base=0f3426564b22faf1c2b8cf022050fef498470323 paths=7 insertions=168 deletions=5
LOOP-MEASURE-SUITE cases=231 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-CHANGE fix-metadata-drift-check-never-runs scenarios=4 cases=4 unnamed=0
LOOP-MEASURE-SURFACE 1 registry row added, 1 pipeline `--rows` list extended, 2 cases added, 0 rewritten, …
```

The four scenarios the delta states each name one of the change's two cases, and the block reports none unnamed.
