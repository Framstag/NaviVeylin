#!/usr/bin/env bash
# The portability check's shell cases: the check's own value-set cases, the suite's own preconditions, the
# four discovery cases, the foreign-project run and the recipe that is executed rather than trusted.
#
# Sourced by `tools/check-portability-selftest.sh`, which provides the environment:
#   PORTABILITY_ROOT      the repository under test
#   PORTABILITY_FIXTURE   tools/fixtures/foreign-project
#   PORTABILITY_TMP       a scratch directory under the gitignored `.pi/tmp/`
#   ISSUES_CORE_CLASSES   the issue tooling's Java core, already compiled by the driver
#
# A case runs in a subshell; it prints nothing on success and a reason on failure, exiting non-zero. No
# token, no network, no Gradle and no device is needed: the stub `gh` and the stub `pi` answer from
# fixtures (guidelines/Build.md §1, §2).
set -uo pipefail

PORTABILITY_CASES=(
  'portability#plantedRepoNameFails'
  'portability#plantedCategoryFails'
  'portability#plantedValueInEveryNamespaceFails'
  'portability#plantedSubmodulePathFails'
  'portability#authorisationLabelIsTheOneException'
  'portability#excludesTheAdapterAndFixtures'
  'portability#valueSetIsReadNotHardCoded'
  'portability#passesOnTheRealTree'
  'discovery#fromASubdirectory'
  'discovery#fromAWorktree'
  'discovery#ignoresGithubRepositoryEnv'
  'discovery#outsideAGitTree'
  'preconditions#refusesOutsideAGitTree'
  'preconditions#refusesUnderMachineLocalState'
  'foreign#profileDiffersFromOurs'
  'foreign#noProjectNameInTheStub'
  'foreign#toolchainRunsEndToEnd'
  'foreign#duplicateIsRefused'
  'foreign#identityIsRefusedToo'
  'foreign#emptyGateCommandStillRunsThePhases'
  'foreign#fixtureIsVersioned'
  'foreign#statesWhatItDoesNotModel'
  'porting#everyCommandInTheRecipeRuns'
  'porting#recipeDoesNotAssumeOurBuild'
  'porting#namesTheAdaptationPoint'
  'porting#statesTheCopyCost'
)

portability_cases() {
  printf '%s\n' "${PORTABILITY_CASES[@]}"
}

portability_run_case() {
  case "$1" in
    'portability#plantedRepoNameFails') case_portability_plantedRepoNameFails ;;
    'portability#plantedCategoryFails') case_portability_plantedCategoryFails ;;
    'portability#plantedValueInEveryNamespaceFails') case_portability_plantedValueInEveryNamespaceFails ;;
    'portability#plantedSubmodulePathFails') case_portability_plantedSubmodulePathFails ;;
    'portability#authorisationLabelIsTheOneException') case_portability_authorisationLabelIsTheOneException ;;
    'portability#excludesTheAdapterAndFixtures') case_portability_excludesTheAdapterAndFixtures ;;
    'portability#valueSetIsReadNotHardCoded') case_portability_valueSetIsReadNotHardCoded ;;
    'portability#passesOnTheRealTree') case_portability_passesOnTheRealTree ;;
    'discovery#fromASubdirectory') case_discovery_fromASubdirectory ;;
    'discovery#fromAWorktree') case_discovery_fromAWorktree ;;
    'discovery#ignoresGithubRepositoryEnv') case_discovery_ignoresGithubRepositoryEnv ;;
    'discovery#outsideAGitTree') case_discovery_outsideAGitTree ;;
    'preconditions#refusesOutsideAGitTree') case_preconditions_refusesOutsideAGitTree ;;
    'preconditions#refusesUnderMachineLocalState') case_preconditions_refusesUnderMachineLocalState ;;
    'foreign#profileDiffersFromOurs') case_foreign_profileDiffersFromOurs ;;
    'foreign#noProjectNameInTheStub') case_foreign_noProjectNameInTheStub ;;
    'foreign#toolchainRunsEndToEnd') case_foreign_toolchainRunsEndToEnd ;;
    'foreign#duplicateIsRefused') case_foreign_duplicateIsRefused ;;
    'foreign#identityIsRefusedToo') case_foreign_identityIsRefusedToo ;;
    'foreign#emptyGateCommandStillRunsThePhases') case_foreign_emptyGateCommandStillRunsThePhases ;;
    'foreign#fixtureIsVersioned') case_foreign_fixtureIsVersioned ;;
    'foreign#statesWhatItDoesNotModel') case_foreign_statesWhatItDoesNotModel ;;
    'porting#everyCommandInTheRecipeRuns') case_porting_everyCommandInTheRecipeRuns ;;
    'porting#recipeDoesNotAssumeOurBuild') case_porting_recipeDoesNotAssumeOurBuild ;;
    'porting#namesTheAdaptationPoint') case_porting_namesTheAdaptationPoint ;;
    'porting#statesTheCopyCost') case_porting_statesTheCopyCost ;;
    *) printf 'no such shell case: %s\n' "$1" >&2; exit 2 ;;
  esac
}

# --------------------------------------------------------------------------- helpers

fail() {
  printf '  %s\n' "$*" >&2
  exit 1
}

assert_eq() {
  [ "$1" = "$2" ] || fail "$3: expected '$1', got '$2'"
}

assert_contains() {
  case "$1" in
    *"$2"*) ;;
    *) fail "$3: '$2' not found in: $1" ;;
  esac
}

assert_not_contains() {
  case "$1" in
    *"$2"*) fail "$3: '$2' unexpectedly present in: $1" ;;
    *) ;;
  esac
}

assert_empty() {
  [ -z "$1" ] || fail "$2: expected nothing, got: $1"
}

scratch() { mktemp -d "$PORTABILITY_TMP/case.XXXXXX"; }

# A scratch directory OUTSIDE any git tree: `.pi/tmp` is inside this repository's working tree, so it
# cannot be used for the case that proves a tool refuses when there is no project at all.
scratch_outside() { mktemp -d "${TMPDIR:-/tmp}/portability-selftest.XXXXXX"; }

git_init() { # <dir>
  git -C "$1" init -q -b main >/dev/null 2>&1 || fail "could not create the throwaway repository"
  git -C "$1" add -A >/dev/null 2>&1 || fail "could not stage the throwaway repository"
  git -C "$1" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm init >/dev/null 2>&1 ||
    fail "could not record the throwaway repository's first state"
}

git_commit() { # <dir> <message>
  git -C "$1" add -A >/dev/null 2>&1
  git -C "$1" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm "$2" >/dev/null 2>&1
}

copy_engines() { # <tree> — the engines, exactly as a copying project would take them
  mkdir -p "$1/tools"
  cp -a "$PORTABILITY_ROOT/tools/issues" "$1/tools/" || fail "could not copy tools/issues"
  cp -a "$PORTABILITY_ROOT/tools/sensors" "$1/tools/" || fail "could not copy tools/sensors"
  cp -a "$PORTABILITY_ROOT/tools/loop" "$1/tools/" || fail "could not copy tools/loop"
  cp "$PORTABILITY_ROOT/tools/check-portability.sh" "$1/tools/" || fail "could not copy the check"
  chmod +x "$1/tools/issues/fixtures/gh" "$1/tools/loop/fixtures/pi" 2>/dev/null || true
}

# The suite itself, copied the way someone verifying a commit from an export takes it: the engines it
# drives, its cases, and the driver — so a precondition can be exercised on a copy of the real thing.
copy_suite() { # <tree>
  copy_engines "$1"
  cp "$PORTABILITY_ROOT/tools/check-portability-selftest.sh" "$1/tools/" || fail "could not copy the suite"
  cp "$PORTABILITY_ROOT/tools/portability-cases.sh" "$1/tools/" || fail "could not copy the suite's cases"
}

local_tree() { # engines + THIS project's configs — the tree the value-set cases plant into
  local d
  d=$(scratch); mkdir -p "$d/project"
  copy_engines "$d/project"
  cp "$PORTABILITY_ROOT/tools/tooling.json" "$d/project/tools/tooling.json"
  git_init "$d/project"
  printf '%s\n' "$d/project"
}

foreign_tree() { # engines + the foreign fixture overlaid — the tree the foreign-project cases run in
  local d
  d=$(scratch); mkdir -p "$d/project"
  copy_engines "$d/project"
  cp -a "$PORTABILITY_FIXTURE/." "$d/project/" || fail "could not overlay the foreign fixture"
  chmod +x "$d/project/tools/sensors/fixtures/foreign-emit.sh" 2>/dev/null || true
  git_init "$d/project"
  printf '%s\n' "$d/project"
}

run_check() { # <tree> [check args…]
  local tree="$1"; shift
  ( cd "$tree" && bash tools/check-portability.sh "$@" )
}

engine_script() { # <tree> <relative script> [args…]
  local tree="$1" script="$2"; shift 2
  ( cd "$tree" && bash "$script" "$@" )
}

finding_json() { # <index> — one finding of the foreign fixture
  jq -c ".[$1]" "$PORTABILITY_FIXTURE/findings.json"
}

sensor_finding() { # <case-value> → a boolean violation finding for the foreign row
  local value="$1" fingerprint
  # the row's fingerprint fields are `case`, canonical order, one `key=value` line per field
  fingerprint=$(printf 'case=%s\n' "$value" | sha256sum | cut -d' ' -f1)
  jq -nc --arg fingerprint "$fingerprint" --arg measurement 1 --arg value "$value" \
    '{case: $value, fingerprint: $fingerprint, measurement: $measurement, verdict: "violation", baseline: ""}'
}

# The stub `gh`'s answers for the foreign repository. `issues.json` is rewritten per case; the static
# fixture carries an empty tracker so no case passes by inheriting another case's residue.
foreign_issues() { # <tree> <issues-json>
  printf '%s' "$2" > "$1/gh/issues.json"
}

foreign_issue() { # <number> <state> <state-reason> <body> [extra-labels-json] → ONE-element array
  local extra="${5:-[]}"
  jq -nc --argjson number "$1" --arg state "$2" --arg reason "$3" --arg body "$4" --argjson extra "$extra" \
    '[{number: $number, title: "a foreign fixture issue", state: $state, stateReason: $reason,
      body: $body, author: {login: "ada"},
      labels: ([{name: "class/bug"}, {name: "category/core"}, {name: "status/open"},
                {name: "source/agent"}] + $extra),
      comments: []}]'
}

foreign_env() { # <tree> — the foreign stub `gh` and the stub `pi` on PATH, and no ambient git identity
  export GH_STUB_DIR="$1/gh"
  export PATH="$1/tools/loop/fixtures:$1/tools/issues/fixtures:$PATH"
  # A fresh machine — a container, a CI runner — has no `user.name`/`user.email` anywhere. The fixture
  # therefore runs with a synthetic global config that keeps the neutral defaults and carries no identity,
  # so a loop that depends on one fails here instead of on the runner (measured 2026-10-18, issue #167: this
  # case was green on the author's host and red in CI, and the only difference was `~/.gitconfig`).
  printf '[init]\n\tdefaultBranch = main\n' > "$1/nohome.gitconfig"
  mkdir -p "$1/nohome"
  export GIT_CONFIG_GLOBAL="$1/nohome.gitconfig" GIT_CONFIG_SYSTEM=/dev/null HOME="$1/nohome"
}

# --------------------------------------------------------------------------- the value-set cases

case_portability_plantedRepoNameFails() {
  local p out status
  p=$(local_tree)
  printf '\nREPO_LITERAL="Framstag/NaviVeylin"\n' >> "$p/tools/issues/create.sh"

  out=$(run_check "$p" --quiet 2>&1); status=$?
  assert_eq "1" "$status" "a planted repository name fails the check"
  assert_contains "$out" "tools/issues/create.sh:" "the file and line are named"
  assert_contains "$out" "Framstag/NaviVeylin" "the value is named"
  assert_contains "$out" "tools/tooling.json repo" "the source the value was read from is named"
}

case_portability_plantedCategoryFails() {
  local p out status
  p=$(local_tree)
  printf '\nAREA_LITERAL="car"\n' >> "$p/tools/issues/list.sh"

  out=$(run_check "$p" --quiet 2>&1); status=$?
  assert_eq "1" "$status" "a planted vocabulary value fails the check"
  assert_contains "$out" "tools/issues/list.sh:" "the file and line are named"
  assert_contains "$out" "'car'" "the value is named"
  assert_contains "$out" "namespaces.category" "the value set it came from is named"
}

# `plantable_value <tree> <namespace>` — a value of that namespace a case can plant, or nothing when the
# namespace declares none. A value that is also a namespace *name* (`agent` is one of the `source` values)
# is left out on purpose: the namespace names are the contract, and the check does not forbid them alone.
plantable_value() {
  jq -r --arg ns "$2" --argjson names "$(jq -r '.namespaces | keys[]' "$1/tools/tooling.json" | jq -R . | jq -sc .)" \
    '[.namespaces[$ns][] | select(. as $v | $names | index($v) | not)] | .[0] // empty' "$1/tools/tooling.json"
}

case_portability_excludesTheAdapterAndFixtures() {
  local p out status
  p=$(local_tree)
  # the adapter is allowed to know the record format, and a fixture is data: neither is an engine
  printf '\nADAPTER_REPO="Framstag/NaviVeylin"\n' >> "$p/tools/issues/import/todo-md.sh"
  printf '"Framstag/NaviVeylin"\n' >> "$p/tools/issues/fixtures/issues.json"

  out=$(run_check "$p" 2>&1); status=$?
  assert_eq "0" "$status" "a value in the adapter or a fixture is not reported: $out"
  assert_contains "$out" "todo-md.sh" "the adapter is named in the printed exclusions"
  assert_contains "$out" "fixtures" "the fixtures are named in the printed exclusions"
  assert_contains "$out" "the source adapter" "the adapter's reason is printed, not silent"
}

# Every namespace's value set is forbidden, not the category's alone (issue #162): the check reads the
# profile's `namespaces` section whole, so one planted value per namespace must be reported with the
# namespace it was read from.
case_portability_plantedValueInEveryNamespaceFails() {
  local p ns value out status checked=0
  p=$(local_tree)
  while IFS= read -r ns; do
    value=$(plantable_value "$p" "$ns")
    [ -n "$value" ] || continue
    printf '\nPROBE_%s="%s"\n' "$ns" "$value" >> "$p/tools/issues/list.sh"
    checked=$((checked + 1))
  done < <(jq -r '.namespaces | keys[]' "$p/tools/tooling.json")
  [ "$checked" -ge 5 ] || fail "the profile declares fewer than five namespaces with a value ($checked)"

  out=$(run_check "$p" --quiet 2>&1); status=$?
  assert_eq "1" "$status" "a planted value in every namespace fails the check: $out"
  while IFS= read -r ns; do
    value=$(plantable_value "$p" "$ns")
    [ -n "$value" ] || continue
    assert_contains "$out" "'$value'" "the planted $ns value is named"
    assert_contains "$out" "namespaces.$ns" "the value's source names the $ns namespace"
  done < <(jq -r '.namespaces | keys[]' "$p/tools/tooling.json")
}

# The submodule path is a project path, so it lives in the loop's own configuration (issue #162): the
# check forbids it because it reads loop.conf, and the loop's worktree reads it instead of naming it.
case_portability_plantedSubmodulePathFails() {
  local p path out status
  p=$(local_tree)
  path=$(sed -n 's/^LOOP_SUBMODULE_PATH="\(.*\)"$/\1/p' "$p/tools/loop/loop.conf")
  [ -n "$path" ] || fail "loop.conf declares no LOOP_SUBMODULE_PATH — the submodule path has no home"
  assert_empty "$(grep -F -- "$path" "$PORTABILITY_ROOT/tools/loop/worktree.sh" 2>/dev/null || true)" \
    "the loop's worktree names the submodule path instead of reading it from loop.conf"

  printf '\nSUBMODULE_PATH="%s"\n' "$path" >> "$p/tools/issues/create.sh"
  out=$(run_check "$p" --quiet 2>&1); status=$?
  assert_eq "1" "$status" "a planted submodule path fails the check: $out"
  assert_contains "$out" "$path" "the path is named"
  assert_contains "$out" "LOOP_SUBMODULE_PATH" "the value's source is the loop's configuration"
}

# The reserved label is the ONE documented exception: an engine must be able to read the label that
# authorises work. It is asserted, not tolerated — one read-only declaration, printed by the check; a
# second occurrence is a leak, because a write path that spells the label is what the invariant forbids.
case_portability_authorisationLabelIsTheOneException() {
  local p label out status
  label=$(jq -r '.reservedLabels[0] // empty' "$PORTABILITY_ROOT/tools/tooling.json")
  [ -n "$label" ] || fail "the profile declares no reserved label — there is no exception to assert"

  out=$(bash "$PORTABILITY_ROOT/tools/check-portability.sh" 2>&1); status=$?
  assert_eq "0" "$status" "the real tree passes with the reserved label declared once: $out"
  assert_contains "$out" "documented exception" "the check names the exception it accepts"
  assert_contains "$out" "$label" "the exception names the label"
  assert_contains "$out" "tools/loop/lib.sh" "the exception names the file that declares it read-only"

  p=$(local_tree)
  printf '\nAUTHORISATION="%s"\n' "$label" >> "$p/tools/issues/list.sh"
  out=$(run_check "$p" --quiet 2>&1); status=$?
  assert_eq "1" "$status" "a second declaration of the reserved label fails the check: $out"
  assert_contains "$out" "tools/issues/list.sh" "the extra occurrence is named"
}

case_portability_valueSetIsReadNotHardCoded() {
  local p out status out2 status2
  p=$(local_tree)
  # a value added to the profile is immediately forbidden in an engine
  jq '.namespaces.category += ["zonk"]' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json"
  printf '\nAREA="zonk"\n' >> "$p/tools/issues/update.sh"

  out=$(run_check "$p" --quiet 2>&1); status=$?
  assert_eq "1" "$status" "a value read from the profile is forbidden"
  assert_contains "$out" "'zonk'" "the newly declared value is named"

  # …and a value removed from the profile is no longer reported: a stale ban list would keep firing
  jq '.namespaces.category -= ["zonk"]' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json"
  out2=$(run_check "$p" --quiet 2>&1); status2=$?
  assert_eq "0" "$status2" "a value removed from the profile is no longer reported: $out2"
  assert_not_contains "$out2" "zonk" "the literal is gone from the output"
}

case_portability_passesOnTheRealTree() {
  local out status f
  out=$(bash "$PORTABILITY_ROOT/tools/check-portability.sh" 2>&1); status=$?
  assert_eq "0" "$status" "the check passes on the real tree: $out"
  assert_contains "$out" "excluded" "the excluded set is printed"
  assert_contains "$out" "todo-md.sh" "the adapter is among the exclusions"
  # every engine of the five preceding changes appears in the printed engine set
  for f in tools/issues/create.sh tools/issues/prioritise.sh tools/issues/classify.sh \
           tools/issues/check-metadata.sh tools/issues/core/Profile.java tools/sensors/run.sh \
           tools/sensors/guard.sh tools/loop/run.sh tools/loop/prescreen.sh tools/loop/state.sh; do
    assert_contains "$out" "$f" "the engine set names $f"
  done
}

# --------------------------------------------------------------------------- discovery (D3)

case_discovery_fromASubdirectory() {
  local p out
  p=$(local_tree)
  mkdir -p "$p/tools/issues/nested/deeper"
  foreign_env "$p"
  out=$(cd "$p/tools/issues/nested/deeper" && bash "$p/tools/issues/lib.sh" discover) ||
    fail "discovery from a subdirectory failed: $out"
  assert_eq "$p" "$(sed -n 's/^root=//p' <<< "$out")" "the root resolves upward"
  assert_eq "$p/tools/tooling.json" "$(sed -n 's/^profile=//p' <<< "$out")" "the profile is the tree's"
  assert_eq "$(cat "$p/tools/issues/fixtures/repo.txt")" "$(sed -n 's/^repo=//p' <<< "$out")" \
    "the repository is the tree's"
}

case_discovery_fromAWorktree() {
  local p wt out
  p=$(local_tree)
  wt="$(dirname "$p")/linked"
  git -C "$p" worktree add -q "$wt" -b linked >/dev/null 2>&1 || fail "could not create a linked worktree"
  printf '{\n  "repo": "linked/tree",\n  "allowedAuthors": ["ada"],\n  "requiredLabels": ["agent/eligible"],\n  "reservedLabels": ["agent/eligible"]\n}\n' \
    > "$wt/tools/tooling.json"
  foreign_env "$wt"

  out=$(cd "$wt/tools" && bash "$wt/tools/issues/lib.sh" discover) ||
    fail "discovery inside a linked worktree failed: $out"
  assert_eq "$wt" "$(sed -n 's/^root=//p' <<< "$out")" "the worktree's own root is used"
  assert_eq "$wt/tools/tooling.json" "$(sed -n 's/^profile=//p' <<< "$out")" \
    "the worktree's OWN profile is used"
  assert_not_contains "$out" "$p/tools/tooling.json" "no path of the main checkout is read"
}

case_discovery_ignoresGithubRepositoryEnv() {
  local p out
  p=$(local_tree)
  foreign_env "$p"
  out=$(cd "$p" && GITHUB_REPOSITORY="evil/evil" ISSUES_REPO="evil/evil" \
    bash "$p/tools/issues/lib.sh" discover) || fail "discovery with a hostile environment failed: $out"
  assert_eq "$p" "$(sed -n 's/^root=//p' <<< "$out")" "the environment does not move the root"
  assert_eq "$(cat "$p/tools/issues/fixtures/repo.txt")" "$(sed -n 's/^repo=//p' <<< "$out")" \
    "the repository comes from the tree, not from GITHUB_REPOSITORY"

  # the explicit profile is the one override that is documented, and it moves only the profile
  cp "$p/tools/tooling.json" "$p/other-profile.json"
  out=$(cd "$p" && bash "$p/tools/issues/lib.sh" discover "$p/other-profile.json") ||
    fail "the explicit profile override failed: $out"
  assert_eq "$p/other-profile.json" "$(sed -n 's/^profile=//p' <<< "$out")" \
    "the explicit profile argument is the override"
  assert_eq "$p" "$(sed -n 's/^root=//p' <<< "$out")" "the override leaves the root alone"

  # no tool takes a repository argument: `--repo` is not a flag of the engine at all
  assert_empty "$(grep -rn -- '--repo' "$PORTABILITY_ROOT/tools/issues/create.sh" \
    "$PORTABILITY_ROOT/tools/issues/list.sh" 2>/dev/null || true)" \
    "no engine accepts a --repo argument that could contradict the tree"
}

case_discovery_outsideAGitTree() {
  local d out status
  d=$(scratch_outside)
  out=$(cd "$d" && bash "$PORTABILITY_ROOT/tools/issues/lib.sh" discover 2>&1); status=$?
  assert_eq "2" "$status" "discovery outside a project exits 2"
  assert_contains "$out" "not inside a git working tree" "the message names the reason"
  assert_not_contains "$out" "root=" "no project is invented"
}

# --------------------------------------------------------------------------- preconditions

# The suite reads two things a copy cannot provide: the real tree's check (its case `passesOnTheRealTree`
# drives it) and the fixture's versioned path (its case `fixtureIsVersioned` asserts it). Run it from a copy
# outside a git working tree, or from a path under `.pi/`, and it refuses with a named reason instead of
# reporting a case as failed — two sessions read that red as a regression of the code under test (issue
# #172). The header states both preconditions, so a reader knows before running it.
case_preconditions_refusesOutsideAGitTree() {
  local d out status
  d=$(scratch_outside)
  mkdir -p "$d/snapshot"
  copy_suite "$d/snapshot"

  out=$(cd "$d/snapshot" && bash tools/check-portability-selftest.sh -h 2>&1)
  assert_contains "$out" "outside a git working tree" "the header names the first precondition"
  assert_contains "$out" ".pi/" "the header names the second precondition"

  out=$(cd "$d/snapshot" && bash tools/check-portability-selftest.sh --list 2>&1); status=$?
  assert_eq "2" "$status" "the suite refuses outside a git working tree: $out"
  assert_contains "$out" "not inside a git working tree" "the refusal names the reason"
  assert_not_contains "$out" "FAIL " "no case is reported failed by where the suite was run"
}

case_preconditions_refusesUnderMachineLocalState() {
  local d out status
  d=$(scratch_outside)
  # a checkout whose machine-local state holds a copy of the suite: the root IS inside a git working tree,
  # so only the `.pi/` precondition can refuse — which is the second reading the issue measured
  git -C "$d" init -q -b main >/dev/null 2>&1 || fail "could not create the throwaway repository"
  mkdir -p "$d/.pi/snapshot"
  copy_suite "$d/.pi/snapshot"

  out=$(cd "$d/.pi/snapshot" && bash tools/check-portability-selftest.sh --list 2>&1); status=$?
  assert_eq "2" "$status" "the suite refuses from a copy under a machine-local path: $out"
  assert_contains "$out" "/.pi/" "the refusal names the machine-local path"
  assert_not_contains "$out" "FAIL " "no case is reported failed by where the suite was run"
}

# --------------------------------------------------------------------------- the foreign project (D2)

case_foreign_profileDiffersFromOurs() {
  local ours theirs value
  ours="$PORTABILITY_ROOT/tools/tooling.json"
  theirs="$PORTABILITY_FIXTURE/tools/tooling.json"

  assert_eq 0 "$(jq -e . "$ours" >/dev/null 2>&1; echo $?)" "our own profile is valid JSON"
  jq -e . "$theirs" >/dev/null 2>&1 || fail "the foreign profile is not valid JSON"
  [ "$(jq -r .repo "$ours")" != "$(jq -r .repo "$theirs")" ] ||
    fail "the foreign repository is the same as ours — a green run would prove nothing"

  while IFS= read -r value; do
    [ -n "$value" ] || continue
    jq -e --arg v "$value" '.allowedAuthors | index($v)' "$ours" >/dev/null &&
      fail "the foreign allowlist entry '$value' is also ours"
  done < <(jq -r '.allowedAuthors[]' "$theirs")

  while IFS= read -r value; do
    [ -n "$value" ] || continue
    jq -e --arg v "$value" '.namespaces.category | index($v)' "$ours" >/dev/null &&
      fail "the foreign category '$value' is also ours"
  done < <(jq -r '.namespaces.category[]' "$theirs")

  [ "$(jq -r '.bands.ttlDays' "$ours")" != "$(jq -r '.bands.ttlDays' "$theirs")" ] ||
    fail "the foreign TTL is ours"
  [ "$(jq -r '.bands.edges | length' "$ours")" != "$(jq -r '.bands.edges | length' "$theirs")" ] ||
    fail "the foreign band set is the same size as ours"
}

case_foreign_noProjectNameInTheStub() {
  local hits
  hits=$(grep -rn -e 'Framstag' -e 'NaviVeylin' "$PORTABILITY_FIXTURE" 2>/dev/null || true)
  assert_empty "$hits" "the foreign fixture names the upstream project"
}

case_foreign_toolchainRunsEndToEnd() {
  local p log scores out status
  p=$(foreign_tree)
  foreign_env "$p"
  log="$p/gh.log"; : > "$log"; export GH_STUB_RECORD="$log"

  # list: the empty tracker reads cleanly
  engine_script "$p" tools/issues/list.sh --json >/dev/null ||
    fail "list.sh failed in the foreign project"

  # create: finding 0 is filed, the fingerprint it reports is the identity the dedup will use
  out=$(finding_json 0 | engine_script "$p" tools/issues/create.sh --json) ||
    fail "create.sh failed in the foreign project: $out"
  assert_eq "created" "$(jq -r '.outcome' <<< "$out")" "the first finding is filed"

  # the tracker now holds the foreign project's issue #7, with the fingerprint of finding 0
  foreign_issues "$p" "$(jq -n --arg fp "$(jq -r .fingerprint <<< "$out")" \
    '[{number: 7, title: "a foreign fixture issue", state: "OPEN", stateReason: "",
       body: ("<!-- fingerprint: " + $fp + " -->"),
       author: {login: "ada"},
       labels: [{name: "class/bug"}, {name: "category/core"}, {name: "status/open"},
                {name: "source/agent"}, {name: "agent/eligible"}],
       comments: []}]')"

  # list again, now over a tracker with an issue in the foreign vocabulary
  out=$(engine_script "$p" tools/issues/list.sh --json --unranked) ||
    fail "list.sh --unranked failed: $out"
  assert_eq "1" "$(jq 'length' <<< "$out")" "the foreign issue is listed"

  # update: a label and a comment, through the same gate as everywhere
  engine_script "$p" tools/issues/update.sh 7 --comment "a foreign comment" >/dev/null ||
    fail "update.sh failed in the foreign project"

  # close with a reason and evidence
  engine_script "$p" tools/issues/close.sh 7 --reason "fixed in the fixture" \
    --evidence "the fixture's own case" >/dev/null || fail "close.sh failed in the foreign project"

  # reopen: the sensor's own issue returns from a completed closure
  foreign_issues "$p" "$(jq -n '[{number: 11, title: "a foreign sensor issue", state: "CLOSED",
       stateReason: "COMPLETED", body: "<!-- occurrence: 1 -->\n<!-- fingerprint: deadbeef -->",
       author: {login: "ada"},
       labels: [{name: "class/bug"}, {name: "category/core"}, {name: "status/fixed-by"},
                {name: "sensor/foreign-row"}],
       comments: []}]')"
  out=$(engine_script "$p" tools/issues/reopen.sh 11 --status open --json \
    --measurement "the condition returned") || fail "reopen.sh failed in the foreign project: $out"
  assert_eq "2" "$(jq -r '.occurrence' <<< "$out")" "the recurrence count is incremented"

  # one sensor row: the registry's command indirection, in the foreign vocabulary
  foreign_issues "$p" "$(foreign_issue 7 OPEN "" "" '[]')"
  printf '%s\n' "$(sensor_finding alpha)" > "$p/findings.jsonl"
  out=$(engine_script "$p" tools/sensors/run.sh --only foreign-row --apply \
    --input "$p/findings.jsonl" 2>&1) || fail "the sensor row failed in the foreign project: $out"
  assert_contains "$out" "verdict=violation" "the row reports the violation it was given"
  assert_contains "$out" "foreign-row" "the foreign row id is the one that ran"

  # one metadata pass: the foreign bands score the foreign issue
  foreign_issues "$p" "$(foreign_issue 7 OPEN "" "" '[]')"
  scores="$p/scores.json"
  jq -n '[{issue: 7, severity: 3, reach: 2, confidence: 2, rationale: "a foreign fixture rationale"}]' > "$scores"
  out=$(engine_script "$p" tools/issues/prioritise.sh --scores "$scores" --write --json) ||
    fail "the metadata pass failed in the foreign project: $out"
  assert_contains "$(cat "$log")" "prio/p2" "the foreign band set decides the band"

  # a dry-run loop: the phases run and nothing is created — one authorised candidate in the foreign tree
  foreign_issues "$p" "$(foreign_issue 7 OPEN "" "" '[{"name": "agent/eligible"}]')"
  : > "$log"
  out=$(engine_script "$p" tools/loop/run.sh --dry-run --items 1 --minutes 30) ||
    fail "the dry-run loop failed in the foreign project: $out"
  assert_contains "$out" "dry run" "the loop reports its dry run"
  assert_eq "0" "$(grep -c 'issue create' "$log" 2>/dev/null)" "a dry run creates no issue"
}

case_foreign_duplicateIsRefused() {
  local p out status
  p=$(foreign_tree)
  foreign_env "$p"
  export GH_STUB_RECORD="$p/gh.log"; : > "$GH_STUB_RECORD"

  out=$(finding_json 0 | engine_script "$p" tools/issues/create.sh --json) ||
    fail "the first filing failed: $out"
  foreign_issues "$p" "$(jq -n --arg fp "$(jq -r .fingerprint <<< "$out")" \
    '[{number: 7, title: "a foreign fixture issue", state: "OPEN", stateReason: "",
       body: ("<!-- fingerprint: " + $fp + " -->"), author: {login: "ada"},
       labels: [{name: "class/bug"}, {name: "category/core"}], comments: []}]')"

  out=$(finding_json 1 | engine_script "$p" tools/issues/create.sh 2>&1); status=$?
  assert_eq "4" "$status" "the duplicate is refused with the duplicate exit code"
  assert_contains "$out" "7" "the existing foreign issue is named"
  assert_contains "$out" "duplicate" "the refusal says what it is"
}

case_foreign_identityIsRefusedToo() {
  local p out status
  p=$(foreign_tree)
  foreign_env "$p"

  # the gate is a behaviour read from the foreign profile, not a local configuration: an identity the
  # foreign allowlist does not carry is refused there exactly as here
  printf 'outsider\n' > "$p/gh/login.txt"
  out=$(engine_script "$p" tools/issues/policy.sh preflight 2>&1); status=$?
  assert_eq "3" "$status" "a foreign outsider is refused"
  assert_contains "$out" "author-not-allowlisted" "the refusal names the reason code"

  # …and the allowlisted foreign identity still passes, so the refusal is the profile's allowlist
  printf 'ada\n' > "$p/gh/login.txt"
  out=$(engine_script "$p" tools/issues/policy.sh preflight 2>&1); status=$?
  assert_eq "0" "$status" "the foreign allowlist still lets its own author in: $out"
}

case_foreign_fixtureIsVersioned() {
  local f engines
  f="$PORTABILITY_FIXTURE"
  [ -d "$f" ] || fail "the fixture directory does not exist"
  # part of the repository's tracked content — not a path outside it, and not machine-local state
  case "$f" in "$PORTABILITY_ROOT"/*) ;; *) fail "the fixture lives outside the repository" ;; esac
  case "$f" in */.pi/*) fail "the fixture lives in the machine-local state" ;; esac
  git -C "$PORTABILITY_ROOT" check-ignore -q "tools/fixtures/foreign-project/README.md" &&
    fail "the fixture is gitignored — a fresh clone would not carry it"

  # the fixture carries config and its own sensor, never a copy of an engine: the harness copies the
  # repository's engines at test time, so a fixture engine cannot drift away from the one under test
  engines=$(find "$f" -name '*.sh' ! -name 'foreign-emit.sh' 2>/dev/null || true)
  assert_empty "$engines" "the fixture carries a shell engine of its own"
  [ ! -d "$f/tools/issues" ] || fail "the fixture carries the issue tooling"
  [ -z "$(find "$f/tools/loop" -name '*.sh' 2>/dev/null)" ] || fail "the fixture carries the loop engine"
}

case_foreign_statesWhatItDoesNotModel() {
  local readme text count
  readme="$PORTABILITY_FIXTURE/README.md"
  [ -f "$readme" ] || fail "the fixture has no README.md"
  text=$(cat "$readme")
  count=$(grep -cE '^[0-9]+\. \*\*' "$readme")
  [ "$count" -ge 3 ] || fail "the fixture names fewer than three unmodelled differences ($count)"
  assert_contains "$text" "GitHub" "the forge client is named as an assumption"
  assert_contains "$text" "bash" "the shell is named as an assumption"
  assert_contains "$text" "build system" "the build is named as an assumption"
}

case_foreign_emptyGateCommandStillRunsThePhases() {
  local p out status state prompt
  p=$(foreign_tree)
  foreign_env "$p"

  # the scaffolding a run needs in any project: a worktree source, an archive, the skills the loop copies
  mkdir -p "$p/.pi/skills/fix-loop" "$p/.pi/skills/openspec-apply-change" \
           "$p/app/src/main/cpp/libosmscout" "$p/openspec/changes/archive"
  printf '.pi/\napp/build/\n' > "$p/.gitignore"
  printf 'cmake_minimum_required(VERSION 3.22)\n' > "$p/app/src/main/cpp/libosmscout/CMakeLists.txt"
  printf -- '---\nname: fix-loop\n---\nfixture skill\n' > "$p/.pi/skills/fix-loop/SKILL.md"
  printf 'fixture skill\n' > "$p/.pi/skills/openspec-apply-change/SKILL.md"
  git_commit "$p" "the loop's scaffolding"

  # one authorised candidate in the foreign vocabulary
  foreign_issues "$p" "$(jq -n '[{number: 1, title: "a foreign eligible bug", state: "OPEN", stateReason: "",
     body: "- **Observed**: the fixture is eligible.", author: {login: "ada"},
     labels: [{name: "class/bug"}, {name: "category/core"}, {name: "status/open"},
              {name: "source/agent"}, {name: "agent/eligible"}], comments: []}]')"

  # the stub child: it archives a change with its evidence, so the wrapper's own commit and verification run
  {
    printf 'case "$1" in\n'
    printf '  worker*)\n'
    printf '    mkdir -p openspec/changes/archive/2026-01-01-foreign-demo/evidence\n'
    printf '    printf "archived\\n" > openspec/changes/archive/2026-01-01-foreign-demo/proposal.md\n'
    printf '    printf "a test result\\n" > openspec/changes/archive/2026-01-01-foreign-demo/evidence/TEST-demo.xml\n'
    printf '    printf "the fix\\n" > foreign-fix.txt\n'
    printf '    ;;\n'
    printf 'esac\n'
  } > "$p/worker-action.sh"
  printf 'LOOP-VERDICT: closed\nLOOP-CHANGE: foreign-demo\nLOOP-ROOT-CAUSE: the fixture has one cause\nLOOP-RED-TEST: Foreign#case fails on HEAD — XML tests=1 failures=1 (2026-01-01T00:00:00Z)\nLOOP-GATE: no gate configured\n' \
    > "$p/worker-report.txt"
  export PI_STUB_ACTION="$p/worker-action.sh" PI_STUB_REPORT_WORKER="$p/worker-report.txt" \
         PI_STUB_LOG="$p/pi.log" GH_STUB_RECORD="$p/gh.log"
  : > "$PI_STUB_LOG"; : > "$GH_STUB_RECORD"

  out=$(engine_script "$p" tools/loop/run.sh --items 1 --minutes 30 --review off --max-decisions 1 2>&1)
  status=$?
  assert_eq "0" "$status" "the run completes with an empty gate command: $out"

  state="$p/.pi/issue-loop/run.json"
  assert_eq "done" "$(jq -r '.items[0].result' "$state")" "the item completed"
  assert_eq "skipped (no gate configured)" "$(jq -r '.items[0].metrics.gate' "$state")" \
    "the report says the gate was skipped rather than inventing a tally"

  # …and the phases call the project's gate: the rendered prompt carries the (empty) configured value,
  # never a gate command of its own. A hard-coded gate in the skeleton would appear here.
  prompt="$p/.pi/issue-loop/items/1/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered at $prompt"
  assert_not_contains "$(cat "$prompt")" "gradlew" "the rendered prompt names no gate of its own"
  assert_contains "$(cat "$PORTABILITY_ROOT/tools/loop/prompts/worker.md")" "{{GATE_CMD}}" \
    "the skeleton substitutes the gate from loop.conf"
}

# --------------------------------------------------------------------------- the recipe (D4, D6)

# The recipe's executable steps: lines inside a ```sh porting-step fence in PORTING.md. Everything else in
# the document is prose or a description of what to copy, and is not executed.
porting_steps() {
  awk '
    /^```sh porting-step$/ { inblock = 1; next }
    /^```/ { if (inblock) { inblock = 0 }; next }
    inblock && /^[^[:space:]#]/ { print }
  ' "$PORTABILITY_ROOT/PORTING.md"
}

case_porting_everyCommandInTheRecipeRuns() {
  local p step count=0 out status
  [ -f "$PORTABILITY_ROOT/PORTING.md" ] || fail "PORTING.md does not exist"
  p=$(foreign_tree)
  foreign_env "$p"

  while IFS= read -r step; do
    [ -n "$step" ] || continue
    count=$((count + 1))
    out=$(cd "$p" && GH_STUB_RECORD="$p/gh.log" bash -c "$step" 2>&1); status=$?
    assert_eq "0" "$status" "the recipe's step '$step' exits 0 (output: $out)"
  done < <(porting_steps)
  [ "$count" -ge 3 ] || fail "the recipe has fewer than three executable steps ($count) — it is not a recipe"
}

case_porting_namesTheAdaptationPoint() {
  local doc
  [ -f "$PORTABILITY_ROOT/PORTING.md" ] || fail "PORTING.md does not exist"
  doc=$(cat "$PORTABILITY_ROOT/PORTING.md")
  assert_contains "$doc" "tools/tooling.json" "the recipe names the project profile"
  assert_contains "$doc" "tools/sensors/sensors.json" "the recipe names the sensor registry"
  assert_contains "$doc" "tools/loop/loop.conf" "the recipe names the loop configuration"
  assert_contains "$doc" "tools/issues/import/todo-md.sh" "the recipe names the source adapter to delete"
  assert_contains "$doc" "## Prerequisites" "the recipe names its prerequisites"
  assert_contains "$doc" "namespace *names* are the contract" "the recipe states the label-taxonomy assumption"
}

case_porting_statesTheCopyCost() {
  local doc
  doc=$(cat "$PORTABILITY_ROOT/PORTING.md")
  assert_contains "$doc" "re-copy" "an upstream engine fix is a re-copy"
  assert_contains "$doc" "own the merge" "a local engine edit means owning the merge"
  assert_contains "$doc" "deferred" "extraction to a shared repository is deferred"
  assert_contains "$doc" "seam" "the profile boundary is the reason it stays cheap"
}

case_porting_recipeDoesNotAssumeOurBuild() {
  local doc steps
  doc=$(cat "$PORTABILITY_ROOT/PORTING.md")
  steps=$(porting_steps)

  assert_not_contains "$doc" "./gradlew" "the recipe names this project's build"
  assert_not_contains "$doc" "gradlew" "the recipe names Gradle at all"
  assert_not_contains "$doc" "NaviVeylin" "the recipe names this project"
  assert_not_contains "$steps" "../" "a step points outside the copied tree"
  assert_not_contains "$steps" "/home/" "a step carries an absolute host path"
  # a project without a build system follows it unchanged: the gate command is the project's to set
  assert_contains "$doc" "tools/loop/loop.conf" "the recipe names the file that carries the gate command"
}
