#!/usr/bin/env bash
# Shared helpers for the issue tooling.
#
# Two rules are implemented here for every script, because a rule re-implemented per script is a rule that
# drifts (design D8, D11):
#
#   * the exit codes are the contract — 0 acted or a deliberate no-op, 2 usage/malformed input,
#     3 policy refusal, 4 a duplicate or an already-present marker (reported, not an error);
#   * the project is discovered, never told: the root from the working tree, the repository from the
#     authenticated `gh` (with a git fallback), the profile from `<root>/tools/tooling.json`. No script
#     takes a project name, a repository or a path as a required argument, and `--profile <path>` is the
#     only override. The environment never decides the project.
#
# `issues_resolve` is called once, at the top of a script, and *not* inside a command substitution: a
# failure inside `$( )` would exit a subshell and be swallowed, leaving an empty root that silently
# resolves to `/`. Discovery therefore writes globals and dies in the caller's own shell.
#
# Usage: `source lib.sh`, or run `lib.sh discover` to see what the discovery rule resolves.
set -uo pipefail

ISSUES_EXIT_OK=0
ISSUES_EXIT_USAGE=2
ISSUES_EXIT_POLICY=3
ISSUES_EXIT_DUPLICATE=4

# The engine's own directory — where the Java core lives. The project facts are resolved from the working
# tree instead, so the engine can run inside somebody else's project.
ISSUES_LIB_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ISSUES_CORE_DIR="$ISSUES_LIB_DIR/core"

ISSUES_ROOT=""
ISSUES_REPO=""
ISSUES_PROFILE=""

issues_die() {
  printf '%s\n' "$*" >&2
  exit "$ISSUES_EXIT_USAGE"
}

issues_note() {
  printf '%s\n' "$*" >&2
}

# The repository: asked of `gh`, with `git remote get-url origin` as the fallback, so the tooling works
# with no network and no token for everything that does not need GitHub.
issues_repo_of() {
  local repo url
  repo=$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null) || repo=""
  if [ -z "$repo" ]; then
    url=$(git remote get-url origin 2>/dev/null) || url=""
    case "$url" in
      git@*:*) repo=${url#*:} ;;
      *://*/*) repo=${url#*://*/} ;;
      *) repo="" ;;
    esac
    repo=${repo%.git}
  fi
  printf '%s\n' "$repo"
}

# Resolve the project once: root from the working tree, repo from `gh`, profile from the root.
issues_resolve() {
  local override="${1:-}"
  local root
  root=$(git rev-parse --show-toplevel 2>/dev/null) || {
    printf 'issues: not inside a git working tree — run this from the project you mean to act on\n' >&2
    exit "$ISSUES_EXIT_USAGE"
  }
  ISSUES_ROOT="$root"
  ISSUES_REPO=$(issues_repo_of)
  if [ -n "$override" ]; then
    [ -f "$override" ] || issues_die "issues: --profile $override does not exist"
    ISSUES_PROFILE="$override"
  else
    ISSUES_PROFILE="$ISSUES_ROOT/tools/tooling.json"
  fi
}

# The identity the tooling runs as — the attributable term of the gate.
issues_login() {
  gh api user --jq .login 2>/dev/null || true
}

# Run one of the core's classes. The core is Java with no Gradle source set and no build system (design D1),
# but it is **compiled once and then run from the class path**, never handed to the single-file source
# launcher: `java core/Foo.java` resolves Foo's siblings only on some JDKs — JDK 27 finds them, JDK 21 does
# not, so every script that ran fine on the author's machine failed in CI with `cannot find symbol Json`
# (measured 2026-10-17: 13 of 33 self-test cases red under JDK 21, 0 under JDK 27). `javac` is the JDK the
# caller already has, and the build is cached by the sources' own digest, so it costs one compile.
# The cache directory's name carries no project value — the engine may not name one
# (`lib#noProjectValueInTheEngine` caught the first attempt); the sources' digest is what keys it.
ISSUES_CORE_CACHE="${ISSUES_CORE_CACHE:-${TMPDIR:-/tmp}/issues-core}"

issues_core_classes() { # print the class directory for the current sources, compiling at most once
  # a caller that has already compiled the core (the self-test does) hands its directory in, so nothing is
  # compiled twice and the tooling runs exactly the classes the caller verified
  if [ -n "${ISSUES_CORE_CLASSES:-}" ]; then
    printf '%s\n' "$ISSUES_CORE_CLASSES"
    return 0
  fi
  local digest dir staging
  if command -v sha256sum >/dev/null 2>&1; then
    digest=$(cat "$ISSUES_CORE_DIR"/*.java | sha256sum | cut -c1-16)
  else
    digest=$(cat "$ISSUES_CORE_DIR"/*.java | cksum | tr -d ' ' | cut -c1-16)
  fi
  # `--release 17` makes the class files loadable by any JDK that can run the selftest, and the key carries
  # the release, so a cache built by a newer JDK can never be handed to an older one (measured 2026-10-17:
  # a JDK-27 build left class-file v71 in a shared cache and every JDK-21 run died on LinkageError)
  dir="$ISSUES_CORE_CACHE/17-$digest"
  if [ ! -d "$dir" ]; then
    staging="$ISSUES_CORE_CACHE/.staging.$$"
    mkdir -p "$staging" || return 1
    javac --release 17 -d "$staging" "$ISSUES_CORE_DIR"/*.java >&2 || { rm -rf "$staging"; return 1; }
    # a parallel invocation may have won the race: then its directory is the one to use
    mv "$staging" "$dir" 2>/dev/null || rm -rf "$staging"
  fi
  [ -d "$dir" ] || return 1
  printf '%s\n' "$dir"
}

issues_core() {
  local class="$1"; shift
  local classes
  classes=$(issues_core_classes) ||
    issues_die "issues: the Java core does not compile — JDK 17 or newer (javac) is required to run these scripts"
  java -cp "$classes" "$class" "$@"
}

# Read one section of the resolved profile.
issues_profile_value() {
  local section="$1"
  issues_core Profile "$ISSUES_PROFILE" "$section"
}

# Print a refusal with its reason code and stop with the policy exit code.
issues_refuse() {
  local reason="$1" detail="${2:-}"
  if [ -n "$detail" ]; then
    printf '%s: %s\n' "$reason" "$detail"
  else
    printf '%s\n' "$reason"
  fi
  exit "$ISSUES_EXIT_POLICY"
}

# Check a label set against the profile: a reserved label, then the closed vocabulary. Callers pass the
# labels a write is about to set, whatever flag or field produced them.
issues_check_labels() {
  local profile="$1"; shift
  [ "$#" -gt 0 ] || return 0
  local out status
  out=$(issues_core Policy labels --profile "$profile" "$@" 2>&1)
  status=$?
  if [ "$status" -eq 3 ]; then
    printf '%s: %s\n' "${out%%:*}" "${out#*: }"
    exit "$ISSUES_EXIT_POLICY"
  fi
  [ "$status" -eq 0 ] || issues_die "issues: policy check failed: $out"
}

# The gate's preflight: refuse to run at all when the identity is not on the allowlist.
issues_preflight() {
  local profile="$1" login="$2"
  local out status
  out=$(issues_core Policy preflight --profile "$profile" --login "$login" 2>&1)
  status=$?
  if [ "$status" -eq 3 ]; then
    printf '%s: %s\n' "${out%%:*}" "${out#*: }"
    exit "$ISSUES_EXIT_POLICY"
  fi
  [ "$status" -eq 0 ] || issues_die "issues: preflight failed: $out"
}

# The live issue list, as the dedup and selection queries read it.
issues_issue_list() {
  gh issue list --state all --limit 400 --json number,title,labels,body,author
}

# --------------------------------------------------------------------------- the priority marker
#
# The marker is written by `prioritise.sh`, read by `check-metadata.sh` and by `list.sh`/`triage-todo`, and
# its grammar lives here — one home, so a writer and a reader cannot disagree about the shape
# (`issue-metadata-loop`, D1). The value is the ticket's own vocabulary spelling, lowercase, exactly as the
# `prio/<band>` label carries it.
ISSUES_PRIO_MARKER_OPEN="<!-- prio:"
ISSUES_PRIO_MARKER_CLOSE="-->"
ISSUES_PRIO_FIELDS="band score sev reach conf occ basis at"

# issues_prio_marker <body>  → the marker line, or nothing
issues_prio_marker() {
  printf '%s' "${1:-}" | grep -o "${ISSUES_PRIO_MARKER_OPEN}[^>]*${ISSUES_PRIO_MARKER_CLOSE}" | head -1
}

# issues_prio_field <marker> <name>  → the field's value, or nothing
issues_prio_field() {
  local marker="$1" name="$2"
  printf '%s' "$marker" | grep -o "\b${name}=[^ ]*" | head -1 | cut -d= -f2-
}

# --------------------------------------------------------------------------- the body record
#
# The body record is the block of `<!-- key: value -->` markers `create.sh` already writes
# (`<!-- fingerprint: … -->`, `<!-- nvid: … -->`). Two keys matter to the metadata pass: its own priority
# marker above, and the sensor lifecycle's `occurrence` count, whose absence means one — so the pass works
# whether or not `sensor-registry-and-metric-issues` has been applied (task 5.2).

# issues_body_field <body> <key>  → the value, or nothing
issues_body_field() {
  printf '%s' "${1:-}" | grep -o "<!--[[:space:]]*${2}:[[:space:]]*[^>]*-->" | head -1 |
    sed -e "s/^<!--[[:space:]]*${2}:[[:space:]]*//" -e 's/[[:space:]]*-->$//' -e 's/[[:space:]]*$//'
}

# The recurrence count the sensor lifecycle keeps in the body record; no record means one.
issues_occurrence() {
  local value
  value=$(issues_body_field "${1:-}" occurrence)
  value=$(printf '%s' "$value" | grep -oE '[0-9]+' | head -1)
  printf '%s\n' "${value:-1}"
}

# --------------------------------------------------------------------------- the basis (D3)
#
# basis = sha256( the labels the score depended on · the body's digest · the citations' existence flag ·
#                 the recurrence count · the freshness bucket )
#
# Five inputs, one digest, in this order, one per line — so the digest changes exactly when one of the five
# moves, and `prioritise.sh --json` can print the inputs beside the digest. Two of them are not raw text:
# the body is hashed *normalised and without the priority marker*, because a reflow is not a context change
# (spec, "A surface edit is not a context change") and because writing the marker must not change the basis
# it records (that is idempotence, task 2.4); and the freshness bucket is measured against the marker's own
# date, never a clock reading taken during the run (task 8.4's mutation).

# base64 is used wherever a field must survive a newline, a tab or a quote — an issue body does all three.
# `-w0` is GNU's flag, so the wrapping is removed with `tr` instead, and a decode of nothing is nothing
# rather than an error.
issues_b64_encode() { # stdin → one unwrapped base64 line
  base64 | tr -d '\n'
}

issues_b64_decode() { # <base64> → the text (nothing when the input is empty or not base64)
  [ -n "${1:-}" ] || return 0
  printf '%s' "$1" | base64 -d 2>/dev/null || true
}

# Wall time in milliseconds. `%3N` is GNU's; the fallback is whole seconds, so the measurement is exact
# where the host can give it and honest where it cannot.
issues_now_ms() {
  local value
  value=$(date -u +%s%3N 2>/dev/null || true)
  case "$value" in
    ''|*[!0-9]*) printf '%s000\n' "$(date -u +%s)" ;;
    *) printf '%s\n' "$value" ;;
  esac
}

issues_sha256() { # stdin → a hex digest (sha256sum, shasum, cksum — in that order)
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 | cut -d' ' -f1
  else
    cksum | tr -d ' '
  fi
}

# Days since 1970-01-01, from pure shell arithmetic — the calendar formula is used instead of `date -d`,
# which is GNU-only, so the TTL works on every host the tooling runs on. All divisions are on positive
# integers, so shell truncation is the floor here.
issues_day_number() { # <YYYY-MM-DD> → days since the epoch, or nothing when the date is not a date
  local date="${1:-}" a y m d
  case "$date" in
    [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;;
    *) return 1 ;;
  esac
  y=$((10#${date%%-*}))
  m=${date#*-}; m=$((10#${m%%-*}))
  d=$((10#${date##*-}))
  [ "$m" -ge 1 ] && [ "$m" -le 12 ] && [ "$d" -ge 1 ] && [ "$d" -le 31 ] || return 1
  a=$(((14 - m) / 12))
  printf '%s\n' "$((d + (153 * (m + 12 * a - 3) + 2) / 5 + 365 * (y + 4800 - a) + (y + 4800 - a) / 4 \
    - (y + 4800 - a) / 100 + (y + 4800 - a) / 400 - 32045 - 2440588))"
}

# The freshness bucket: how many whole TTL periods the item's stored score has been alive for. It is one of
# the basis's inputs, so a score nobody revisits invalidates itself, and a run an hour after the previous
# one computes the same bucket (spec, "An old value is revisited").
issues_ttl_bucket() { # <at-date> <today> <ttl-days>
  local at today ttl from now
  at="${1:-}"; today="${2:-}"; ttl="${3:-180}"
  case "$ttl" in ''|*[!0-9]*) ttl=180 ;; esac
  [ "$ttl" -ge 1 ] || ttl=1
  from=$(issues_day_number "$at") || { printf '0\n'; return 0; }
  now=$(issues_day_number "$today") || { printf '0\n'; return 0; }
  [ "$now" -ge "$from" ] || { printf '0\n'; return 0; }
  printf '%s\n' "$(((now - from) / ttl))"
}

# The claim's own digest: the body with the priority marker removed and every whitespace run collapsed, so
# a reflow, a re-indent or a blank line does not read as a change of claim.
issues_body_hash() { # <body> → the digest
  printf '%s' "${1:-}" |
    sed "s|${ISSUES_PRIO_MARKER_OPEN}[^>]*${ISSUES_PRIO_MARKER_CLOSE}||g" |
    tr -s '[:space:]' ' ' | sed -e 's/^ //' -e 's/ $//' | issues_sha256
}

# The extensions a cited path can end in. One home: the extractor, the flag and the documentation read the
# same list, and a language added to the repo is added here.
ISSUES_ANCHOR_EXTENSIONS='kt|java|cpp|hpp|h|sh|kts|gradle|json|xml|yml|yaml|md|py|pro|sql|toml|properties|conf'

# The code the item cites: `path`, `path:Symbol` and `path#anchor` tokens in its prose. A URL's host would
# look like a path, so URLs go first; the set is deduplicated and sorted, so the flag is deterministic.
issues_cited_anchors() { # <body> → one citation per line
  printf '%s' "${1:-}" |
    sed -e 's#https\?://[^][()<>[:space:]]*##g' -e 's#`##g' |
    grep -oE "[A-Za-z0-9_][A-Za-z0-9_./@-]*\.($ISSUES_ANCHOR_EXTENSIONS)(:[A-Za-z0-9_.<>#/-]+)?" |
    sed -e 's#^\./##' | sort -u
}

# Whether the cited code still exists. One `git ls-files` call covers the whole set — the tracked tree is
# the authority, not the working directory's strays — and a named symbol is looked up inside its file with
# `git grep`, so `path` gone and `path:Symbol` gone are both a change (`task 2.1`). The result is a
# comma-separated token per citation, in the same sorted order, or `none` when the item cites nothing.
issues_anchor_flag() { # <body> → the flag
  local citations=() paths=() citation path symbol state tracked flag=""
  mapfile -t citations < <(issues_cited_anchors "${1:-}")
  if [ "${#citations[@]}" -eq 0 ]; then
    printf 'none\n'
    return 0
  fi
  for citation in "${citations[@]}"; do
    paths+=("${citation%%:*}")
  done
  tracked=$(git -C "$ISSUES_ROOT" ls-files -- "${paths[@]}" 2>/dev/null || true)
  for citation in "${citations[@]}"; do
    path="${citation%%:*}"
    symbol=""
    case "$citation" in *:*) symbol="${citation#*:}" ;; esac
    state="absent"
    if printf '%s\n' "$tracked" | grep -qxF "$path"; then
      if [ -n "$symbol" ]; then
        if git -C "$ISSUES_ROOT" grep -qF -- "$symbol" -- "$path" 2>/dev/null; then
          state="symbol"
        else
          state="symbol-gone"
        fi
      else
        state="present"
      fi
    fi
    flag="${flag:+$flag,}$state"
  done
  printf '%s\n' "$flag"
}

# The basis itself. `label-pattern` is built by the caller from the profile's `bands.basisNamespaces`, so the
# engine names no namespace of its own (`lib#noProjectValueInTheEngine`).
issues_basis() { # <labels: one per line> <body> <occurrence> <ttl-bucket> <label-pattern>
  local labels="${1:-}" body="${2:-}" occ="${3:-1}" bucket="${4:-0}" pattern="${5:-}"
  local label_key body_key anchor_key
  label_key=$(printf '%s\n' "$labels" | grep -E "^(${pattern})/" | sort -u | paste -sd, -)
  body_key=$(issues_body_hash "$body")
  anchor_key=$(issues_anchor_flag "$body")
  printf 'labels=%s\nbody=%s\nanchors=%s\nocc=%s\nttl=%s\n' \
    "$label_key" "$body_key" "$anchor_key" "$occ" "$bucket" | issues_sha256
}

# --------------------------------------------------------------------------- reading the tracker
#
# Both halves of the pass read the tracker through this function, so "we examined every issue" is one rule
# with one implementation: the whole page set in one call, then a refusal if any issue failed to arrive —
# skipping an item that had changed would leave a wrong band in place indefinitely, so the selection is by
# *basis*, never by sampling or an early exit (spec "Selection never samples"; design D3, D9).
#
# The answer lands in `<dir>/page.tsv` as number, labels, title (base64), body (base64) — base64 because
# an issue body contains newlines, tabs and quotes — with the decoded bodies under `<dir>/bodies/<number>`.
issues_page_records() { # <scratch dir> → the number of issues read
  local dir="$1" issues count read_count
  issues=$(issues_issue_list) || issues_die "the tooling could not read the tracker"
  printf '%s' "$issues" | jq -e 'type == "array"' >/dev/null 2>&1 ||
    issues_die "the tracker answer is not an issue array"
  count=$(printf '%s' "$issues" | jq 'length')
  mkdir -p "$dir/bodies"
  : > "$dir/page.tsv"
  printf '%s' "$issues" |
    jq -r '.[] | [ (.number | tostring),
                    ([(.labels // [])[] | if type == "string" then . else .name end] | join(",")),
                    ((.title // "") | @base64),
                    ((.body // "") | @base64) ] | join("|")' |
    while IFS='|' read -r number labels_csv title_b64 body_b64; do
      [ -n "$number" ] || continue
      issues_b64_decode "$body_b64" > "$dir/bodies/$number"
      printf '%s|%s|%s|%s\n' "$number" "$labels_csv" "$title_b64" "$body_b64" >> "$dir/page.tsv"
    done
  read_count=$(grep -c . "$dir/page.tsv" || true)
  [ "$read_count" = "$count" ] ||
    issues_die "read $read_count of $count issues — refusing to work on a partial page set"
  printf '%s\n' "$read_count"
}

# --------------------------------------------------------------------------- the pass's writers

# The marker's text, exactly as the grammar declares it: the fields in their declared order.
issues_prio_marker_text() { # band score sev reach conf occ basis at
  printf '%s band=%s score=%s sev=%s reach=%s conf=%s occ=%s basis=%s at=%s %s\n' \
    "$ISSUES_PRIO_MARKER_OPEN" "$1" "$2" "$3" "$4" "$5" "$6" "$7" "$8" "$ISSUES_PRIO_MARKER_CLOSE"
}

# The body this pass stores: the marker replaced in place when the body already carries one, appended
# below a blank line when it does not. Composed here so the writer and the `--body-of` view cannot differ,
# and so exactly one marker is ever in a body.
issues_body_with_marker() { # <body> <marker> → the new body
  local body="${1:-}" marker="$2"
  if [ -n "$(issues_prio_marker "$body")" ]; then
    printf '%s' "$body" | awk -v marker="$marker" -v open="$ISSUES_PRIO_MARKER_OPEN" '
      index($0, open) > 0 && !replaced { print marker; replaced = 1; next }
      { print }'
  else
    printf '%s\n\n%s\n' "$body" "$marker"
  fi
}

# The pass's write log: one line per *successful* write, in the same argument shape the tooling takes, so
# `check-metadata.sh --write-log` can assert what the pass wrote instead of trusting it (D7). A write that
# was refused is not logged — the log is a record, not an intention.
issues_write_log() { # <file|-> <logical-write> <args…>
  local file="${1:-}"; shift || true
  [ -n "$file" ] || return 0
  local logical="${1:-}"; shift || true
  {
    printf '%s' "$logical"
    for a in "$@"; do printf ' %q' "$a"; done
    printf '\n'
  } >> "$file"
}

# --------------------------------------------------------------------------- the pass's report

# One line, the same shape from either entry point, because `prioritise.sh` and `classify.sh` are two halves
# of one pass (D4/D5): a reader does not have to know which one ran. The eight fields are the measurement;
# `waiting` and `unclassified` are the two queues a run leaves behind (D9).
issues_report_line() { # read skipped due scored classified transitions batches wall-ms waiting unclassified
  printf 'metadata: read=%s skipped=%s due=%s scored=%s classified=%s transitions=%s batches=%s wall_ms=%s waiting=%s unclassified=%s\n' \
    "$1" "$2" "$3" "$4" "${5:-0}" "${6:-0}" "${7:-0}" "${8:-0}" "${9:-0}" "${10:-0}"
}

# --------------------------------------------------------------------------- the model handoff
#
# An issue's title and body are *data*. They arrive in one fenced block per issue, and the fence is what the
# skill's untrusted-input rule points at (`issue-metadata-loop` task 4.2; the same boundary
# `github-issue-tooling` D7 fixed for a third-party issue). A body that carries the fence token itself is
# refused rather than escaped, because a fence that can be closed from inside is not a fence.
ISSUES_PLAN_FENCE_OPEN='<<<ISSUE-DATA'
ISSUES_PLAN_FENCE_CLOSE='ISSUE-DATA>>>'

issues_plan_block() { # <number> <title> <labels-comma> <body>
  printf -- '--- issue %s\n' "$1"
  printf 'labels: %s\n' "$3"
  printf '%s\n' "$ISSUES_PLAN_FENCE_OPEN"
  printf 'title: %s\n' "$2"
  printf '%s\n' "$4"
  printf '%s\n' "$ISSUES_PLAN_FENCE_CLOSE"
}

issues_plan_refuses() { # <body> → 0 when the body cannot be placed in a fence
  case "${1:-}" in
    *"$ISSUES_PLAN_FENCE_OPEN"*|*"$ISSUES_PLAN_FENCE_CLOSE"*) return 0 ;;
    *) return 1 ;;
  esac
}

# --------------------------------------------------------------------------- discovery, for the self-test

issues_discover() {
  issues_resolve "${1:-}"
  printf 'root=%s\n' "$ISSUES_ROOT"
  printf 'repo=%s\n' "$ISSUES_REPO"
  printf 'profile=%s\n' "$ISSUES_PROFILE"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  case "${1:-discover}" in
    discover) shift || true; issues_discover "${1:-}" ;;
    *) issues_die "usage: lib.sh [discover] [--profile <path>]" ;;
  esac
fi
