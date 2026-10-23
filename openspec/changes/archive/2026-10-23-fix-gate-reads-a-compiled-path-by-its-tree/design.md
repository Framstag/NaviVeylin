# Design — fix-gate-reads-a-compiled-path-by-its-tree

## The shape of the defect

The gate answers "is this path compiled?" with one call, and that call only ever sees a file name:

```
tools/loop/loop.conf:136  LOOP_GATE_COMPILED_PATTERNS="*/src/* */lint.xml *.gradle.kts *.gradle *.toml CMakeLists.txt"
                          (at HEAD: "*.kt … *.xml … app/src/main/cpp/* app/src/main/res/*")
tools/loop/gate.sh:68-76  matches() { case $- in *f*) was_f=1 ;; *) set -f ;; esac; for glob in $1; do …
tools/loop/gate.sh:83     if matches "$compiled_patterns" "$p"; then owed=compiled_cmd
```

Two things are wrong inside that call, and the second was invisible until the first was measured.

1. **The list is matched anywhere in the tree.** `*.xml` is not a claim about a build input, it is a claim about an
   extension, so `openspec/changes/archive/<x>/evidence/gate-tallies/a.xml`, a gate-timings fixture that mimics a
   test report and a `tools/issues/core/*.java` the shell suites compile are all "compiled" to the gate. Measured
   on HEAD (`evidence/probes.log` probe 1): the diff carries 0 compiled-extension paths and 4 `.xml`, and the plan
   still names `./gradlew test -PforceTests --no-build-cache`.
2. **The list is expanded before it is matched.** `for glob in $1` is unquoted, so the shell performs pathname
   expansion on the *pattern list* against the directory the gate runs in; a pattern that names a tree is replaced
   by the names it happened to match, and then compared literally. Measured 2026-10-23
   (`evidence/probes.log` probe 2): with `LOOP_GATE_COMPILED_PATTERNS="app/src/main/res/*"` — a shape the profile
   already declares twice — and a new `app/src/main/res/values/colors.xml`, the plan says
   `LOOP-GATE: nothing owed — no path this profile gates changed`; the same tree with `set -f` in the matcher owes
   `fake-build`.

Both are one invariant: **a pattern is data, and what makes a path compiled is where it sits.**

## D1 — a compiled path is one a tree the build reads holds

**Chosen: bound the profile's patterns to the trees the build reads, and read them as patterns.**

```
LOOP_GATE_COMPILED_PATTERNS="*/src/* */lint.xml *.gradle.kts *.gradle *.toml CMakeLists.txt"
```

`*/src/*` is a module's source tree whatever the module is called — sources, resources, manifests, the native
`cpp/` tree are all under a module's `src/` — so a module added later is gated without this line being edited.
`*/lint.xml` is a module's lint configuration, which the build reads. `*.gradle.kts`, `*.gradle` and `*.toml` stay
name-based on purpose: a build script and a version catalog are identified by their naming convention, and no
artefact or fixture carries one.

The matcher gains the guard that makes any anchored pattern work at all:

```
matches() {
  local glob was_f=0
  case $- in *f*) was_f=1 ;; *) set -f ;; esac
  for glob in $1; do case "$2" in $glob) [ "$was_f" -eq 1 ] || set +f; return 0 ;; esac; done
  [ "$was_f" -eq 1 ] || set +f
  return 1
}
```

Saved and restored rather than set for the whole script: `gate.sh`'s other loops read `git` and the suites' own
output and are none of this function's business. `measure.sh` needs no change at all — it asks `gate.sh --plan`
for what a delta owes (`tools/loop/measure.sh:70`), so the two can never disagree.

## Alternatives the evidence eliminated

**A — keep the basename patterns and put the evidence out of their reach** (an exclusion for `**/evidence/**`,
mirroring the `LOOP_REVIEW_EXCLUDE_PATHSPEC` the profile already records). Eliminated by the tree, not by
preference: the same false positive exists in two other trees an evidence exclusion cannot name —
`tools/fixtures/gate-timings/root/app/build/test-results/*.xml` (3 files that *mimic* build output) and
`tools/issues/core/*.java` (10 files the shell suites compile with `javac`) — so the exclusion would have to grow
one entry per tree, and the item's own claim, "a delta that touches no compiled file owes the full build", would
stay false for both. The rule stays wrong; only one instance of it would be hidden.

**B — narrow the patterns to the module trees by name** (`app/src/* auto/src/* core/src/* buildSrc/src/*
osmscout-client-java/src/*`). Eliminated by what it costs the next module: adding one touches
`settings.gradle.kts` — which the build gate still catches — and after that every change inside the new module's
`src/` owes nothing, because the pattern list was not edited. A rule that needs a hand edit to stay true is a rule
no case can hold. `*/src/*` states the same intent without the enumeration.

**C — move a change's evidence out of the versioned tree** (into `.pi/`, which is gitignored). Eliminated by the
run's own rule: `<change>/evidence/` is the versioned half of a change's record and the reason an item cannot
close on a test result it does not carry; an archived change's numbers are read from there by later items.

## Blast radius

| file | change |
|---|---|
| `tools/loop/loop.conf` | the pattern line and the comment that states the rule behind it |
| `tools/loop/gate.sh` | `matches()` reads the list with pathname expansion off, saved and restored |
| `tools/loop-cases.sh` | `gate#anArchivedArtefactOwesNoBuild`, `gate#aModuleResourceOwesTheBuild`, helper `profile_value` |
| `tools/loop-mutations.sh` | records `44.1` (the profile line) and `44.2` (the matcher's `set -f`) |

What this delta makes the gate *stop* gating, by tree, is exactly what `guidelines/Build.md` §2a line 114 already
declares uncompiled: `openspec/**`, `tools/**`. What it makes the gate *start* gating is the one thing it should
have gated: a module's `src/` and `lint.xml`, through patterns that now mean what they say. No suite pair changes,
so the shell suites a delta owes are untouched; the three cases that build a profile of their own
(`gate#theDeltaSelectsTheSuitesItTouches`, `measure#namesTheOwedChecksInOneBlock`,
`measure#printsTheNumbersTheReviewerAsksFor`) assert the engine's use of *a* pattern list and pass unchanged.

## Rollback

Revert four files: the profile line, the matcher guard, the two cases and the two records. Nothing is persisted —
no state file, label, archive shape or evidence convention moves — and the change's archive carries the red run,
so a revert reintroduces exactly the defect `gate#anArchivedArtefactOwesNoBuild` names.

## Verification

The change touches no compiled file (`*.sh`, `*.conf` and `openspec/**`), so it owes no build: the structural
checks are `tools/loop/gate.sh` — whose profile maps `tools/loop/**` to `tools/loop-selftest.sh`, the record check
and `tools/check-doc-routes.sh`, and `openspec/*` to `tools/gen-feature-list.sh --check-classification` — plus
`openspec validate --strict`. The two cases are falsified by records `44.1` and `44.2` (each mutates the half its
case asserts), and a gate run after the fix must still name the five checks this tree's own delta owes.
