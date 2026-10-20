#!/usr/bin/env bash
# The stale-citation resolver's cases: the classifier's definition of a citation, the repair ladder, the
# quarantine, the bounds and the two pipeline facts (`stale-citation-resolution` D1–D7).
#
# Sourced by `tools/resolve-stale-selftest.sh`, which provides the environment:
#   RESOLVE_ROOT   the repository under test        RESOLVE_TMP    a scratch directory under `.pi/tmp/`
#   ISSUES_CORE_CLASSES  the issue tooling's Java core, already compiled by the driver
# and puts the stub `gh` first on PATH.
#
# A case runs in a subshell; it prints nothing on success and a reason on failure, exiting non-zero.
set -uo pipefail

RESOLVE_CASES=(
  'classifier#fragmentIsNotACitation'
  'classifier#bareHeaderIsNotAPath'
  'classifier#externalLibraryPathIsNotAPath'
  'classifier#bareSourceNameResolvesBySuffix'
  'classifier#submodulePathResolves'
  'classifier#generatedArtefactIsNotMissing'
  'classifier#absentIsTheOnlyStale'
  'prescreen#refusalIsVisibleInTheTracker'
  'resolver#suffixedCitationIsCanonicalised'
  'resolver#doubledPrefixIsCollapsed'
  'resolver#renamedFileIsFollowed'
  'resolver#removedSymbolIsReAnchored'
  'resolver#markersSurviveTheRepair'
  'resolver#repairPostsOneComment'
  'resolver#unrepairableCitationIsParked'
  'resolver#ambiguityIsReportedNotRepaired'
  'resolver#neverWritesTheAuthorisation'
  'resolver#boundHoldsAndReportsTheRest'
  'resolver#ambiguityDoesNotConsumeTheBound'
  'resolver#secondRunChangesNothing'
  'pipeline#scheduledRunRepairs'
  'pipeline#residueIsADeclaredRow'
)

resolve_shell_cases() { printf '%s\n' "${RESOLVE_CASES[@]}"; }

resolve_run_case() {
  case "$1" in
    'classifier#fragmentIsNotACitation') case_classifier_fragmentIsNotACitation ;;
    'classifier#bareHeaderIsNotAPath') case_classifier_bareHeaderIsNotAPath ;;
    'classifier#externalLibraryPathIsNotAPath') case_classifier_externalLibraryPathIsNotAPath ;;
    'classifier#bareSourceNameResolvesBySuffix') case_classifier_bareSourceNameResolvesBySuffix ;;
    'classifier#submodulePathResolves') case_classifier_submodulePathResolves ;;
    'classifier#generatedArtefactIsNotMissing') case_classifier_generatedArtefactIsNotMissing ;;
    'classifier#absentIsTheOnlyStale') case_classifier_absentIsTheOnlyStale ;;
    'prescreen#refusalIsVisibleInTheTracker') case_prescreen_refusalIsVisibleInTheTracker ;;
    'resolver#suffixedCitationIsCanonicalised') case_resolver_suffixedCitationIsCanonicalised ;;
    'resolver#doubledPrefixIsCollapsed') case_resolver_doubledPrefixIsCollapsed ;;
    'resolver#renamedFileIsFollowed') case_resolver_renamedFileIsFollowed ;;
    'resolver#removedSymbolIsReAnchored') case_resolver_removedSymbolIsReAnchored ;;
    'resolver#markersSurviveTheRepair') case_resolver_markersSurviveTheRepair ;;
    'resolver#repairPostsOneComment') case_resolver_repairPostsOneComment ;;
    'resolver#unrepairableCitationIsParked') case_resolver_unrepairableCitationIsParked ;;
    'resolver#ambiguityIsReportedNotRepaired') case_resolver_ambiguityIsReportedNotRepaired ;;
    'resolver#neverWritesTheAuthorisation') case_resolver_neverWritesTheAuthorisation ;;
    'resolver#boundHoldsAndReportsTheRest') case_resolver_boundHoldsAndReportsTheRest ;;
    'resolver#ambiguityDoesNotConsumeTheBound') case_resolver_ambiguityDoesNotConsumeTheBound ;;
    'resolver#secondRunChangesNothing') case_resolver_secondRunChangesNothing ;;
    'pipeline#scheduledRunRepairs') case_pipeline_scheduledRunRepairs ;;
    'pipeline#residueIsADeclaredRow') case_pipeline_residueIsADeclaredRow ;;
    *) printf 'no such shell case: %s\n' "$1" >&2; exit 2 ;;
  esac
}

# --------------------------------------------------------------------------- helpers

fail() { printf '  %s\n' "$*" >&2; exit 1; }
assert_eq() { [ "$1" = "$2" ] || fail "$3: expected '$1', got '$2'"; }
assert_contains() { case "$1" in *"$2"*) ;; *) fail "$3: '$2' not found in: $1" ;; esac; }
assert_not_contains() { case "$1" in *"$2"*) fail "$3: '$2' unexpectedly present in: $1" ;; *) ;; esac; }
assert_empty() { [ -z "$1" ] || fail "$2: expected nothing, got: $1"; }

scratch() { mktemp -d "$RESOLVE_TMP/case.XXXXXX"; }

git_init() { # <dir>
  git -C "$1" init -q -b main >/dev/null 2>&1 || fail "could not create the throwaway repository"
  git -C "$1" add -A >/dev/null 2>&1
  git -C "$1" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm init >/dev/null 2>&1 ||
    fail "could not commit in the throwaway repository"
}
git_commit() { # <dir> <message>
  git -C "$1" add -A >/dev/null 2>&1
  git -C "$1" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm "$2" >/dev/null 2>&1
}

# Every tree the resolver runs in carries the project profile: the resolver discovers it the way every other
# tool does, from `<root>/tools/tooling.json`, and reads its bands, its vocabulary and its separator there.
plant_profile() { # <tree>
  mkdir -p "$1/tools"
  cp "$RESOLVE_ROOT/tools/tooling.json" "$1/tools/tooling.json" || fail "could not copy the profile"
}

# A tree with one source file, so a bare class-file name resolves by suffix and the full path is `exact`.
tree_with_file() { # [<path>] → the tree
  local d p="${1:-app/src/main/java/com/example/Foo.kt}"
  d=$(scratch); plant_profile "$d"; mkdir -p "$d/$(dirname "$p")"
  printf 'class Foo\n' > "$d/$p"
  git_init "$d"
  printf '%s\n' "$d"
}

# The classifier, asked with an index over the tree — the same call the pre-screen and the resolver make.
classify() { # <tree> <token> → the class
  local tree="$1" tok="$2" idx
  idx=$(mktemp "$RESOLVE_TMP/idx.XXXXXX")
  (
    set -uo pipefail
    # shellcheck source=tools/issues/lib.sh
    source "$RESOLVE_ROOT/tools/issues/lib.sh"
    ISSUES_CITATION_ROOT="$tree" ISSUES_CITATION_INDEX="$idx"
    issues_citation_index_build "$tree" "$idx" || true
    issues_citation_class "$tok"
  )
  rm -f "$idx"
}

candidates_of() { # <tree> <text> → every citation-shaped token, before the path rules filter them
  local tree="$1" text="$2" idx
  idx=$(mktemp "$RESOLVE_TMP/idx.XXXXXX")
  (
    set -uo pipefail
    # shellcheck source=tools/issues/lib.sh
    source "$RESOLVE_ROOT/tools/issues/lib.sh"
    ISSUES_CITATION_ROOT="$tree" ISSUES_CITATION_INDEX="$idx"
    issues_citation_index_build "$tree" "$idx" || true
    printf '%s\n' "$text" | issues_citation_candidates
  )
  rm -f "$idx"
}

citations_of() { # <tree> <text> → the citation tokens, then the stale ones
  local tree="$1" text="$2" idx
  idx=$(mktemp "$RESOLVE_TMP/idx.XXXXXX")
  (
    set -uo pipefail
    # shellcheck source=tools/issues/lib.sh
    source "$RESOLVE_ROOT/tools/issues/lib.sh"
    ISSUES_CITATION_ROOT="$tree" ISSUES_CITATION_INDEX="$idx"
    issues_citation_index_build "$tree" "$idx" || true
    issues_citations_of "$text"
    issues_stale_citations_of "$text"
  )
  rm -f "$idx"
}

# A tracker for the stub `gh`: the fixture files the engine reads, plus one issue per body given.
tracker() { # <dir> <number> <title> <body> [extra-labels-json] [state]
  local d="$1" n="$2" title="$3" body="$4" extra="${5:-[]}" state="${6:-OPEN}"
  mkdir -p "$d"
  cp "$RESOLVE_ROOT/tools/issues/fixtures/repo.txt" "$d/repo.txt"
  cp "$RESOLVE_ROOT/tools/issues/fixtures/login.txt" "$d/login.txt"
  printf '[]\n' > "$d/labels.json"
  printf '[]\n' > "$d/prs.json"
  jq -nc --argjson n "$n" --arg t "$title" --arg b "$body" --argjson extra "$extra" --arg state "$state" \
    '[{number: $n, title: $t, state: $state, stateReason: "",
       body: $b, author: {login: "Framstag"},
       labels: ([{name: "class/bug"}, {name: "category/car"}, {name: "status/open"},
                 {name: "source/agent"}] + $extra),
       comments: []}]' > "$d/issues.json"
  printf '%s\n' "$d"
}

resolver() { # <tree> [args…] — the real resolver, in the tree
  local tree="$1"; shift
  ( cd "$tree" && bash "$RESOLVE_ROOT/tools/issues/resolve-stale.sh" "$@" )
}

# The body the stub recorded for an `issue edit --body-file`, decoded from the write log.
recorded_body() { # <log> <n> → the body of the n-th recorded write that carried one
  local log="$1" n="${2:-1}" file
  file="$log.bodies/$n"
  [ -f "$file" ] || fail "no recorded body at $file (log: $(cat "$log" 2>/dev/null))"
  cat "$file"
}
recorded_count() { # <log> <pattern>
  local count
  if [ ! -f "$1" ]; then printf '0\n'; return 0; fi
  count=$(grep -c -- "$2" "$1" 2>/dev/null)
  printf '%s\n' "${count:-0}"
}
recorded_text() { # <log> — the log's lines without the stub's shell quoting
  [ -f "$1" ] || return 0
  sed "s/'//g" "$1"
}

# A citation whose directory prefix is repeated names its own answer: `app/src/main/app/src/main/java/…` is
# `app/src/main/java/…` with the first three segments said twice. Nothing on disk resolves the doubled form, so
# the classifier answers `absent` — correctly — and before this rule the ladder fell through to `otherwise` and
# the item was excluded on every run without ever being parked (#189).
case_resolver_doubledPrefixIsCollapsed() {
  # The tree deliberately holds a file whose name is *not* a symbol in its own content (a shell script), so the
  # ladder's symbol step cannot answer this one: only the collapse can.
  local tree d
  tree=$(scratch); plant_profile "$tree"; mkdir -p "$tree/tools/alpha"
  printf '#!/bin/sh\n' > "$tree/tools/alpha/alpha.sh"
  git_init "$tree"
  d=$(tracker "$tree/fixture-gh" 7 "an item" 'the write is in `tools/alpha/tools/alpha/alpha.sh`')
  GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply >/dev/null || fail "the resolver failed"
  assert_contains "$(recorded_body "$d/writes.log" 1)" '`tools/alpha/alpha.sh`' \
    "the repeated prefix is collapsed to the tracked path"
  assert_contains "$(recorded_text "$d/writes.log")" "repeats its own directory" \
    "and the comment says why the collapsed path is the right one"
}


case_classifier_fragmentIsNotACitation() {
  local tree out candidates
  tree=$(tree_with_file)
  out=$(citations_of "$tree" 'gradle.properties carries `-Dorg.gradle.java.home=/usr/lib/jvm` and the tail is `it.sh`')
  assert_not_contains "$out" "org.gradle.java.h" "the tail of a longer token is not a citation"
  # …and the extractor never offered them: the boundary is checked where the match is found, not afterwards.
  # Both shapes are real: `abcSomeTest.kt` is a citation glued to a word, and `it.showDetailsSheet` is a
  # sentence whose `.sh` looks like an extension (#78's verdict was `it.sh`).
  candidates=$(candidates_of "$tree" 'await the state (`uiState.first { it.showDetailsSheet })')
  assert_not_contains "$candidates" "it.sh" "a match followed by a word is dropped"
}

case_classifier_bareHeaderIsNotAPath() {
  local tree
  tree=$(tree_with_file)
  assert_eq "not-a-path" "$(classify "$tree" "jni.h")" "a bare header name is a mention"
  assert_eq "not-a-path" "$(classify "$tree" "dagger.h")" "and so is another"
  # …while the same name written as a path stays a citation
  mkdir -p "$tree/native" && printf '#pragma once\n' > "$tree/native/thing.h"
  assert_eq "exact" "$(classify "$tree" "native/thing.h")" "a header written as a path is a citation"
}

case_classifier_externalLibraryPathIsNotAPath() {
  local tree
  tree=$(tree_with_file)
  assert_eq "not-a-path" "$(classify "$tree" "androidx/car/app/utils/RemoteUtils.java")" \
    "a library namespace is not this repository's citation"
}

case_classifier_bareSourceNameResolvesBySuffix() {
  local tree
  tree=$(tree_with_file)
  assert_eq "suffix" "$(classify "$tree" "Foo.kt")" "exactly one tracked path ends with the name"
  assert_eq "exact" "$(classify "$tree" "app/src/main/java/com/example/Foo.kt")" "the full path is exact"
  assert_eq "not-a-path" "$(classify "$tree" "app/src/test/.../Foo.kt")" "an abbreviation with an ellipsis is a mention"
}

case_classifier_submodulePathResolves() {
  local tree sub sha
  tree=$(tree_with_file)
  sub="$tree/third_party/client"
  mkdir -p "$sub"; printf 'void f();\n' > "$sub/OSMScoutClient.cpp"
  git_init "$sub"
  sha=$(git -C "$sub" rev-parse HEAD)
  git -C "$tree" update-index --add --cacheinfo "160000,$sha,third_party/client" >/dev/null 2>&1 ||
    fail "could not register the gitlink"
  git_commit "$tree" "a submodule"
  assert_eq "submodule" "$(classify "$tree" "OSMScoutClient.cpp")" "a file inside the submodule resolves by name"
  assert_eq "submodule" "$(classify "$tree" "third_party/client/OSMScoutClient.cpp")" "and by path"
}

case_classifier_generatedArtefactIsNotMissing() {
  local tree
  tree=$(tree_with_file)
  assert_eq "generated" "$(classify "$tree" "RELEASE-NOTES.md")" "an artefact the project produces is not missing"
  assert_eq "not-a-path" "$(classify "$tree" "files/favorites.json")" "a runtime path is a mention, not a citation"
}

case_classifier_absentIsTheOnlyStale() {
  local tree out
  tree=$(tree_with_file)
  assert_eq "absent" "$(classify "$tree" "Ghost.kt")" "a name no file carries is absent"
  out=$(citations_of "$tree" 'cites `Foo.kt`, `jni.h` and `Ghost.kt`')
  assert_contains "$out" "Foo.kt" "the resolvable citation is reported"
  assert_contains "$out" "Ghost.kt" "the absent one is too"
  assert_not_contains "$out" "jni.h" "and the mention is not"
  # the stale filter keeps exactly the absent one
  assert_eq "Ghost.kt" "$(printf '%s\n' "$out" | tail -1)" "only the absent citation is stale"
}

# --------------------------------------------------------------------------- the pre-screen (D1, requirement 2)

case_prescreen_refusalIsVisibleInTheTracker() {
  local tree d out status
  tree=$(scratch); mkdir -p "$tree/tools" "$tree/app"
  cp -a "$RESOLVE_ROOT/tools/issues" "$tree/tools/" || fail "could not copy the issue tooling"
  cp -a "$RESOLVE_ROOT/tools/loop" "$tree/tools/" || fail "could not copy the loop"
  plant_profile "$tree"
  printf 'class Foo\n' > "$tree/app/Foo.kt"
  git_init "$tree"
  d=$(tracker "$tree/fixture-gh" 7 "the resolvable item" 'cites `Foo.kt:12`' '[{"name":"agent/eligible"}]')
  jq '. + [{number: 8, title: "the unresolvable item", state: "OPEN", stateReason: "",
             body: "cites `Ghost.kt:3`", author: {login: "Framstag"},
             labels: [{name: "class/bug"}, {name: "category/car"}, {name: "status/open"},
                      {name: "source/agent"}, {name: "agent/eligible"}], comments: []}]' \
    "$d/issues.json" > "$d/issues.tmp" && mv "$d/issues.tmp" "$d/issues.json"
  out=$( cd "$tree" && GH_STUB_DIR="$d" bash tools/loop/state.sh init --items 2 --minutes 30 >/dev/null 2>&1; \
         cd "$tree" && GH_STUB_DIR="$d" bash tools/loop/prescreen.sh --json 2>"$tree/prescreen.err" ); status=$?
  assert_eq "0" "$status" "the pre-screen ran: $out / $(cat "$tree/prescreen.err" 2>/dev/null)"
  assert_eq "eligible" "$(jq -r '.[] | select(.issue == 7) | .verdict' <<< "$out")" \
    "a citation that resolves by suffix is not stale"
  assert_eq "stale" "$(jq -r '.[] | select(.issue == 8) | .verdict' <<< "$out")" \
    "a citation nothing accounts for is"
  assert_contains "$(jq -r '.[] | select(.issue == 8) | .reason' <<< "$out")" "Ghost.kt" \
    "and the refusal names the citation"

  # The refusal is visible where a person can read it: the resolver parks the item (requirement 2).
  d2=$(tracker "$tree/fixture-gh2" 8 "the unresolvable item" 'cites `Ghost.kt:3`')
  GH_STUB_DIR="$d2" GH_STUB_RECORD="$d2/writes.log" resolver "$tree" --apply >/dev/null || fail "the resolver failed"
  assert_contains "$(recorded_text "$d2/writes.log")" "status/on-hold" "the item is parked in the tracker"
}

# --------------------------------------------------------------------------- the ladder (D4)

case_resolver_suffixedCitationIsCanonicalised() {
  local tree d
  tree=$(tree_with_file)
  d=$(tracker "$tree/fixture-gh" 7 "an item" 'the case lives in `Foo.kt:12`')
  GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply >/dev/null || fail "the resolver failed"
  assert_contains "$(recorded_body "$d/writes.log" 1)" "app/src/main/java/com/example/Foo.kt:12" \
    "the citation is rewritten to its full path"
}

case_resolver_renamedFileIsFollowed() {
  local tree d
  tree=$(scratch); plant_profile "$tree"; mkdir -p "$tree/old" "$tree/new"
  printf 'class Place\n' > "$tree/old/Place.kt"
  git_init "$tree"
  git -C "$tree" mv old/Place.kt new/Place.kt >/dev/null 2>&1 || fail "could not move the file"
  git_commit "$tree" "move Place.kt"
  d=$(tracker "$tree/fixture-gh" 7 "an item" 'the case lives in `old/Place.kt:4`')
  GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply >/dev/null || fail "the resolver failed"
  assert_contains "$(recorded_body "$d/writes.log" 1)" "new/Place.kt" "the citation follows the rename"
  assert_contains "$(recorded_text "$d/writes.log")" "new/Place.kt" "and the comment records it"
}

case_resolver_removedSymbolIsReAnchored() {
  local tree d
  tree=$(scratch); plant_profile "$tree"; mkdir -p "$tree/src"
  printf 'class Widget\n' > "$tree/src/Widget.kt"
  git_init "$tree"
  d=$(tracker "$tree/fixture-gh" 7 "an item" 'the widget lives in `ui/Widget.kt:1`')
  GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply >/dev/null || fail "the resolver failed"
  assert_contains "$(recorded_body "$d/writes.log" 1)" "src/Widget.kt" \
    "a gone path whose symbol survives in one file is re-anchored"
}

case_resolver_markersSurviveTheRepair() {
  local tree d body before after
  tree=$(tree_with_file)
  before='the case lives in `Foo.kt:12`

<!-- nvid: 903 -->
<!-- fingerprint: 0123456789abcdef -->'
  d=$(tracker "$tree/fixture-gh" 7 "an item" "$before")
  GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply >/dev/null || fail "the resolver failed"
  after=$(recorded_body "$d/writes.log" 1)
  assert_contains "$after" "<!-- nvid: 903 -->" "the source marker survives"
  assert_contains "$after" "<!-- fingerprint: 0123456789abcdef -->" "the fingerprint marker survives"
  assert_contains "$after" "app/src/main/java/com/example/Foo.kt:12" "and the citation was rewritten"
  assert_contains "$after" "the case lives in" "the prose around it is untouched"
}

case_resolver_repairPostsOneComment() {
  local tree d
  tree=$(tree_with_file)
  d=$(tracker "$tree/fixture-gh" 7 "an item" 'cites `Foo.kt` and `Foo.kt` again')
  GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply >/dev/null || fail "the resolver failed"
  assert_eq "1" "$(recorded_count "$d/writes.log" 'issue comment')" "exactly one comment per item per run"
  assert_contains "$(recorded_text "$d/writes.log")" "citation repaired" "and it says what it did"
}

case_resolver_unrepairableCitationIsParked() {
  local tree d out
  tree=$(tree_with_file)
  d=$(tracker "$tree/fixture-gh" 7 "an item" 'cites `Ghost.kt:3`')
  out=$(GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply 2>&1) || fail "the resolver failed: $out"
  assert_contains "$(recorded_text "$d/writes.log")" "status/on-hold" "the item is parked"
  assert_contains "$(recorded_text "$d/writes.log")" "unresolved citation" "and the comment says why"
  assert_eq "0" "$(recorded_count "$d/writes.log" 'issue close')" "nothing is closed"
}

case_resolver_ambiguityIsReportedNotRepaired() {
  local tree d out
  tree=$(scratch); plant_profile "$tree"; mkdir -p "$tree/a" "$tree/b"
  printf 'class Dup\n' > "$tree/a/Dup.kt"; printf 'class Dup\n' > "$tree/b/Dup.kt"
  git_init "$tree"
  d=$(tracker "$tree/fixture-gh" 7 "an item" 'cites `Dup.kt:1`')
  out=$(GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply --json 2>&1) || fail "the resolver failed: $out"
  assert_eq "1" "$(jq -r '.ambiguous' <<< "$out")" "the ambiguity is counted"
  assert_eq "0" "$(jq -r '.repaired' <<< "$out")" "nothing is repaired"
  assert_eq "0" "$(recorded_count "$d/writes.log" 'issue edit')" "and no body is written"
  assert_eq "1" "$(jq -r '[.items[] | select(.ambiguous > 0)] | length' <<< "$out")" "the item reports it"
}

case_resolver_neverWritesTheAuthorisation() {
  local tree d
  tree=$(tree_with_file)
  d=$(tracker "$tree/fixture-gh" 7 "an item" 'cites `Foo.kt` and `Ghost.kt`')
  GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply >/dev/null || fail "the resolver failed"
  assert_eq "0" "$(recorded_count "$d/writes.log" 'agent/eligible')" "it never writes the authorisation"
  assert_eq "0" "$(recorded_count "$d/writes.log" 'agent/')" "nor any other agent verdict"
  assert_eq "0" "$(recorded_count "$d/writes.log" '--add-label class/')" "nor a kind"
  assert_eq "0" "$(recorded_count "$d/writes.log" 'issue close')" "and it closes nothing"
  # the closed vocabulary applies to it exactly as to a person
  out=$( cd "$tree" && bash "$RESOLVE_ROOT/tools/issues/update.sh" 7 --status nonsense --profile "$tree/tools/tooling.json" 2>&1 )
  assert_contains "$out" "label-opaque" "an unknown status is refused by the tooling's own gate"
}

case_resolver_boundHoldsAndReportsTheRest() {
  local tree d out
  tree=$(tree_with_file)
  d=$(tracker "$tree/fixture-gh" 7 "first" 'cites `Foo.kt`')
  jq '. + [{number: 8, title: "second", state: "OPEN", stateReason: "", body: "cites `Foo.kt`",
             author: {login: "Framstag"},
             labels: [{name: "class/bug"}, {name: "category/car"}, {name: "status/open"}], comments: []}]' \
    "$d/issues.json" > "$d/issues.tmp" && mv "$d/issues.tmp" "$d/issues.json"
  out=$(GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply --json --max-items 1) ||
    fail "the resolver failed: $out"
  assert_eq "1" "$(jq -r '.due' <<< "$out")" "the bound holds"
  assert_eq "1" "$(jq -r '.waiting' <<< "$out")" "and the rest is reported as waiting"
}

# The bound exists so a run changes the tracker. An item the ladder can only *report* — an ambiguous suffix,
# which is never guessed (D4) — writes nothing, so counting it as due spends a slot of the bound on a
# non-action and the item behind it never gets one (#199). Tracker order decides nothing here: the ambiguous
# item is first and the writable one is second, exactly as the live tracker had them.
case_resolver_ambiguityDoesNotConsumeTheBound() {
  local tree d out
  tree=$(scratch); plant_profile "$tree"; mkdir -p "$tree/a" "$tree/b" "$tree/app/src"
  printf 'class Dup\n' > "$tree/a/Dup.kt"; printf 'class Dup\n' > "$tree/b/Dup.kt"
  printf 'class Foo\n' > "$tree/app/src/Foo.kt"
  git_init "$tree"
  d=$(tracker "$tree/fixture-gh" 7 "the reported one" 'cites `Dup.kt:1`')
  jq '. + [{number: 8, title: "the writable one", state: "OPEN", stateReason: "", body: "cites `Foo.kt`",
             author: {login: "Framstag"},
             labels: [{name: "class/bug"}, {name: "category/car"}, {name: "status/open"}], comments: []}]' \
    "$d/issues.json" > "$d/issues.tmp" && mv "$d/issues.tmp" "$d/issues.json"
  out=$(GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply --json --max-items 1) ||
    fail "the resolver failed: $out"
  assert_eq "1" "$(jq -r '.repaired' <<< "$out")" \
    "the one slot the bound allowed went to the item a run can write"
  assert_eq "1" "$(jq -r '.due' <<< "$out")" "and the bound still holds at one"
  assert_eq "0" "$(jq -r '.waiting' <<< "$out")" "nothing waits behind the item that can only be reported"
  assert_eq "1" "$(jq -r '.ambiguous' <<< "$out")" "the ambiguity is still counted, and nothing is written for it"
  assert_contains "$(recorded_body "$d/writes.log" 1)" "app/src/Foo.kt" "the writable citation is the one written"
}

case_resolver_secondRunChangesNothing() {
  local tree d first second
  tree=$(tree_with_file)
  d=$(tracker "$tree/fixture-gh" 7 "an item" 'cites `Foo.kt` and `Ghost.kt`')
  GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply >/dev/null || fail "the first run failed"
  before=$(recorded_count "$d/writes.log" 'issue comment')
  # The second state is what the tracker would look like after that run: the item carries the parked label and
  # its citation is still the one nothing accounts for. The *guard* is what must stop the second run from
  # deciding it again — the citation alone cannot, so this case is sensitive to the guard and nothing else.
  jq '.[0].labels += [{"name":"status/on-hold"}]' \
    "$d/issues.json" > "$d/issues.tmp" && mv "$d/issues.tmp" "$d/issues.json"
  second=$(GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" resolver "$tree" --apply --json) ||
    fail "the second run failed: $second"
  assert_eq "0" "$(jq -r '.due' <<< "$second")" "a parked item is not due again"
  assert_eq "0" "$(jq -r '.writes' <<< "$second")" "and nothing is written"
  assert_eq "$before" "$(recorded_count "$d/writes.log" 'issue comment')" "no comment is posted by the second run"
}

# --------------------------------------------------------------------------- the pipeline (D6)

case_pipeline_scheduledRunRepairs() {
  local tree d out
  tree=$(tree_with_file)
  d=$(tracker "$tree/fixture-gh" 7 "an item" 'cites `Foo.kt:9`' '[{"name":"agent/eligible"}]')
  # the step's command, from `.github/workflows/sensors.yml`
  out=$( cd "$tree" && GH_STUB_DIR="$d" GH_STUB_RECORD="$d/writes.log" \
    bash "$RESOLVE_ROOT/tools/issues/resolve-stale.sh" --apply --max-items 25 2>&1 ) ||
    fail "the scheduled command failed: $out"
  assert_contains "$(recorded_body "$d/writes.log" 1)" "app/src/main/java/com/example/Foo.kt:9" \
    "the scheduled run repairs the citation, so the item is eligible again"
}

case_pipeline_residueIsADeclaredRow() {
  local row cmd tree d out
  row=$(jq -c '.rows[] | select(.id == "stale-citations")' "$RESOLVE_ROOT/tools/sensors/sensors.json")
  [ -n "$row" ] || fail "the registry declares no stale-citations row"
  cmd=$(jq -r '.cmd' <<< "$row")
  [ -f "$RESOLVE_ROOT/$cmd" ] || fail "the row's command does not exist: $cmd"
  assert_eq "greater-than" "$(jq -r '.comparison' <<< "$row")" "the row compares the count against a bound"
  assert_eq "kind" "$(jq -r '.fingerprint.fields | join(",")' <<< "$row")" "and its identity is the finding's kind"
  # the command reports the resolver's own residue — run it with one unresolvable item in the tracker
  tree=$(tree_with_file)
  d=$(tracker "$tree/fixture-gh" 7 "an item" 'cites `Ghost.kt`')
  out=$( cd "$tree" && GH_STUB_DIR="$d" SENSORS_THRESHOLD=0 SENSORS_ROOT="$RESOLVE_ROOT" \
    bash "$RESOLVE_ROOT/$cmd" 2>/dev/null ) || fail "the row's command failed"
  assert_eq "violation" "$(jq -r '.verdict' <<< "$out")" "a residue above the bound is a violation"
  assert_eq "1" "$(jq -r '.measurement' <<< "$out")" "and the measurement is the count"
}
