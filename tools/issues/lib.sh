#!/usr/bin/env bash
# Shared helpers for the issue tooling.
#
# Two rules are implemented here for every script, because a rule re-implemented per script is a rule that
# drifts (design D8, D11):
#
#   * the exit codes are the contract — 0 acted or a deliberate no-op, 2 usage/malformed input,
#     3 policy refusal, 4 a duplicate or an already-present marker (reported, not an error),
#     75 the tracker could not be reached, so no answer exists (#231);
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

# need_value FLAG [VALUE...] — a flag arm's own argv, passed as `need_value "$@"` before the arm reads "$2".
#
# A value whose flag was written without it is a usage error, and it is one *before* the arm consumes the two
# words. The shape it replaces was `--push) push="${2:-}"; shift 2 ;;`: `shift 2` with one word left fails,
# leaves `$#` unchanged and re-enters the same iteration for ever, so the parser never returns and the
# validation after the loop that would have refused the empty value is never reached (issue #190: `run.sh
# --items 100 --minutes 480 --push` printed nothing, stdout and stderr both empty, until it was killed by
# hand). A word that is itself a flag is refused as a value too, so `--items --minutes 30` names `--items`
# instead of reading `--minutes` as the count; no flag of this tooling takes a value that begins with `--`.
# Nothing is shifted here — the arm keeps its own `shift` — and the tool is named as `$0` names it, so one
# message shape serves every script that sources this library and the ones that source `loop/lib.sh` or
# `sensors/lib.sh` through it.
need_value() {
  local flag="${1:-}" tool
  if [ $# -lt 2 ] || [ "${2#--}" != "$2" ]; then
    tool=${0##*/}
    issues_die "${tool%.sh}: $flag needs a value"
  fi
}

issues_note() {
  printf '%s\n' "$*" >&2
}

# A failure that is the transport's, not the tracker's answer: the request never arrived, or its reply was
# cut off. What `gh` prints for those — a TCP timeout, a DNS failure, a reset or refused connection, a TLS
# or response timeout, a 5xx, GitHub's secondary rate limit — is retried. Everything else (400, 401, 403
# policy, 404, a validation refusal) is the tracker *answering*, and retrying it would only repeat the
# answer, so it is passed straight through (issue #231).
ISSUES_GH_TRANSIENT_RE='i/o timeout|dial tcp|no such host|temporary failure in name resolution|network is unreachable|connection reset|connection refused|unexpected EOF|TLS handshake timeout|timeout awaiting response|context deadline exceeded|HTTP 50[0-9]|502 Bad Gateway|503 Service Unavailable|504 Gateway Timeout|secondary rate limit'

issues_gh_transient() { # <the failed call's stderr>
  printf '%s' "$1" | grep -qE "$ISSUES_GH_TRANSIENT_RE"
}

# Every `gh` call the tooling makes goes through this one seam (issue #231). A transient failure is retried
# with the backoff `ISSUES_GH_RETRY_DELAYS` names — three retries after the first attempt by default (5 s,
# 20 s, 60 s) — and a failure that survives them is reported as *unknown*: exit `ISSUES_EXIT_UNREACHABLE`
# with `tracker-unreachable` on stderr, which a caller can branch on, rather than the empty answer a caller
# used to read as "the tracker says no" (that is what turned one 182-minute run's timeout into a policy
# verdict about an item).
#
# It is a shell *function* named `gh`, so no existing call site had to be edited; the real CLI is reached by
# `command gh`, which skips functions. A *new* call site can still bypass the seam by writing `command gh`
# itself, so the tooling keeps the rule rather than a mechanism: no call site does, and the self-test's
# `lib#onlyTheSeamCallsTheRealCli` is what notices a new one that does.
#
# Two calls are exempt because each one is an *append*: the tracker may have accepted it before the socket
# died, so a retry would file the same finding or post the same comment a second time. Their failure is
# therefore passed straight to the caller, which must not read it as "nothing happened".
ISSUES_GH_RETRY_DELAYS="${ISSUES_GH_RETRY_DELAYS-5 20 60}"
ISSUES_EXIT_UNREACHABLE=75

gh() {
  local errfile status err delay attempt=1
  case " $* " in
    *" issue comment "*|*" issue create "*) command gh "$@"; return $? ;;
  esac
  errfile=$(mktemp) || { command gh "$@"; return $?; }
  while :; do
    if command gh "$@" 2>"$errfile"; then
      rm -f "$errfile"
      return 0
    else
      status=$?
    fi
    err=$(cat "$errfile")
    if ! issues_gh_transient "$err"; then
      printf '%s\n' "$err" >&2
      rm -f "$errfile"
      return "$status"
    fi
    delay=$(printf '%s\n' $ISSUES_GH_RETRY_DELAYS | sed -n "${attempt}p")
    if [ -z "$delay" ]; then
      printf 'tracker-unreachable: gh %s did not answer after %s attempt(s) — %s\n' \
        "${1:-}" "$attempt" "${err##*$'\n'}" >&2
      rm -f "$errfile"
      return "$ISSUES_EXIT_UNREACHABLE"
    fi
    printf 'tracker: gh %s failed transiently, retrying in %ss — %s\n' \
      "${1:-}" "$delay" "${err##*$'\n'}" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
  done
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

# The identity the tooling runs as — the attributable term of the gate. It has three outcomes, and the
# first two used to be the same empty string: a login (the tracker answered), an empty string with status 0
# (the tracker answered and names no identity — a refusal), and `ISSUES_EXIT_UNREACHABLE` (the tracker did
# not answer at all, so no policy verdict may be given). `issues_preflight` and `policy.sh` read the third
# (#231); a call site that captures the login itself would fold it back into the first, so none does.
issues_login() {
  local login status
  login=$(gh api user --jq .login 2>/dev/null)
  status=$?
  if [ "$status" -eq 0 ]; then
    printf '%s\n' "$login"
    return 0
  fi
  [ "$status" -eq "$ISSUES_EXIT_UNREACHABLE" ] && return "$ISSUES_EXIT_UNREACHABLE"
  return 0
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
#
# The login is read *here*, not by the caller, because this is the one path where the read has a policy
# meaning: an assignment in the caller (`login=$(issues_login)`) captures the empty string of an outage and
# discards the status that says so, and every write path then reported `author-not-allowlisted` for a network
# failure (#231). Reading it here makes the distinction unlosable by a call site.
issues_preflight() {
  local profile="$1" login out status
  login=$(issues_login)
  status=$?
  if [ "$status" -eq "$ISSUES_EXIT_UNREACHABLE" ]; then
    printf 'tracker-unreachable: the tracker API did not answer — no identity could be read, so no policy verdict is available\n' >&2
    exit "$ISSUES_EXIT_UNREACHABLE"
  fi
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

# --------------------------------------------------------------------------- the state namespace
#
# A state write replaces the namespace's value: two `status/*` labels on one issue make every status query
# answer twice, so the write that sets a state is also the write that removes the value it displaces. The scan
# lives here because it has two writers — `update.sh` and `reopen.sh` — and two copies of it would drift; the
# namespace *name* is the contract, which is why the portability check reads the names and forbids only the
# project's values (`check-portability.sh`, "the vocabulary").
ISSUES_STATUS_NAMESPACE="status"

# issues_status_prefix → the status namespace as a label prefix, e.g. `status/`. Reading the profile starts a
# JVM, so a writer resolves it once into a variable and passes it on; the function refuses to answer before
# `issues_resolve`, because a prefix without its separator (`status` instead of `status/`) would silently make
# the scan match nothing at all.
issues_status_prefix() {
  [ -n "${ISSUES_PROFILE:-}" ] ||
    issues_die "issues: the profile is not resolved — call issues_resolve before issues_status_prefix"
  printf '%s%s\n' "$ISSUES_STATUS_NAMESPACE" "$(issues_profile_value namespaceSeparator)"
}

# issues_labels_of <issue-json> → the issue's labels, one per line. Both spellings are accepted: the API
# returns objects with a `name`, and a fixture may carry the bare string.
issues_labels_of() {
  jq -r '[.labels[]? | if type == "string" then . else .name end] | .[]' <<< "${1:-}"
}

# The question a comment carries (issue #243). The run's escalation comment opens with its verdict word
# (`needs-decision`), then a blank line, then the sentence — so "the first non-empty line" is the verdict, and an
# answer quoting it binds to `<!-- answers: needs-decision -->`. The sentence is the line the run marks
# `**The decision:**`, and only a comment without that marker falls back to its first non-empty line.
#
# One program, read by the queue and the answer path below; `tools/issues/core/WorkPackage.java` restates it, because
# the core is standalone Java — the two are held together by asserting the same fixture in
# `decisions#theQuestionIsNotTheVerdictWord` and `WorkPackage#questionIsNotTheVerdictWord`.
ISSUES_QUESTION_JQ='split("\n")
  | map(select(test("\\S")))
  | (map(select(startswith("**The decision:**"))) | .[0]) // .[0] // ""'

issues_question_of() { # <comment-json> → the question it carries, one line
  local json="${1:-}"
  [ -n "$json" ] || json='{}'
  printf '%s' "$json" | jq -r '.body // "" | '"$ISSUES_QUESTION_JQ"
}

# issues_status_displaced <issue-json> <target-label> <prefix> → one label per line: every value of the
# status namespace the issue carries other than the target. The prefix is the caller's, so this stays a pure
# filter: it reads the issue the caller already has and makes no `gh` call and no profile read of its own.
issues_status_displaced() {
  local issue="$1" target="$2" prefix="$3"
  jq -r --arg prefix "$prefix" --arg target "$target" \
    '[.labels[]? | if type == "string" then . else .name end]
     | .[] | select(startswith($prefix)) | select(. != $target)' <<< "$issue"
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

# issues_prio_marker_line <body>  → the 1-based line number of the marker, or nothing
#
# The marker's *shape* has the same one home as its grammar, and a reader that searched the body for the
# delimiters instead of for the line the pass writes let an item's prose decide whether it carries a marker
# (issue #293: #292's own body quoted the grammar, and the check reported three violations against it). The
# shape is: a line of its own, at column 0, closing at its end, and not inside a fenced code span — every other
# occurrence is a quotation of the grammar, a guideline excerpt pasted into a body, or a comment copied in.
#
# The fence is not an extra exemption, it is the boundary of the same rule: a fenced line renders as code rather
# than as a comment, so a body that shows the grammar in a fence is quoting it rather than recording one.
# Leading indentation is therefore not trimmed (it changes the block the line belongs to), while trailing
# whitespace is (it changes no rendering). An unterminated fence swallows what follows it, as a renderer does.
issues_prio_marker_line() {
  printf '%s\n' "${1:-}" | awk -v mo="$ISSUES_PRIO_MARKER_OPEN" -v mc="$ISSUES_PRIO_MARKER_CLOSE" '
    function is_fence(line,   t) {
      t = line
      sub(/^[ \t]+/, "", t)
      return t ~ /^(```|~~~)/
    }
    function is_marker(line,   t) {
      t = line
      sub(/\r$/, "", t)
      sub(/[ \t]+$/, "", t)
      return length(t) >= length(mo) + length(mc) && index(t, mo) == 1 &&
             substr(t, length(t) - length(mc) + 1) == mc
    }
    { if (is_fence($0)) { infence = !infence; next }
      if (infence) next
      if (is_marker($0)) { print NR; exit } }'
}

# issues_prio_marker <body>  → the marker's line, or nothing
issues_prio_marker() {
  local line
  line=$(issues_prio_marker_line "${1:-}")
  [ -n "$line" ] || return 0
  printf '%s' "${1:-}" | sed -n "${line}p" | sed -e 's/[[:space:]]*$//'
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

# Set one record entry, or append it: the writer of a record entry and its reader share this one home, so
# a body record the metadata pass reads cannot be spelled differently by the pass that writes it.
issues_body_set() { # <body> <key> <value> → the body with that entry set
  local body="$1" key="$2" value="$3"
  if printf '%s' "$body" | grep -q "<!--[[:space:]]*${key}:"; then
    printf '%s' "$body" |
      sed -E "s|<!--[[:space:]]*${key}:[^>]*-->|<!-- ${key}: ${value} -->|"
  else
    printf '%s\n<!-- %s: %s -->' "$body" "$key" "$value"
  fi
}

# --------------------------------------------------------------------------- the sensor registry
#
# The `sensor` namespace is *declared* in the profile and its *values* are the check registry's rows
# (`sensor-registry-and-metric-issues`, D9/D12), so the vocabulary keeps one owner and a project without
# a registry has no sensor labels at all rather than the same list written twice. The lookup lives here,
# beside the label gate it is used by, and the path is resolved from the working tree like every other
# project fact.
issues_sensor_registry() {
  printf '%s\n' "${ISSUES_SENSOR_REGISTRY:-$ISSUES_ROOT/tools/sensors/sensors.json}"
}

issues_sensor_namespace() {
  local registry
  registry=$(issues_sensor_registry)
  [ -f "$registry" ] || return 0
  jq -r '.namespace // empty' "$registry" 2>/dev/null || true
}

issues_sensor_ids() {
  local registry
  registry=$(issues_sensor_registry)
  [ -f "$registry" ] || return 0
  jq -r '.rows[]?.id' "$registry" 2>/dev/null || true
}

# The label the registry's id owns, or nothing when the registry declares no such id: the caller refuses
# with `label-opaque`, so a label outside the vocabulary stays refused.
issues_sensor_label() { # <id>
  local namespace separator
  namespace=$(issues_sensor_namespace)
  [ -n "$namespace" ] || return 0
  issues_sensor_ids | grep -qx -- "$1" || return 0
  separator=$(issues_profile_value namespaceSeparator)
  printf '%s%s%s\n' "$namespace" "${separator:-/}" "$1"
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

# The claim's own digest: the body with the priority marker's line removed and every whitespace run collapsed,
# so a reflow, a re-indent or a blank line does not read as a change of claim — while an edit to a *quotation* of
# the grammar does, because a quotation is part of the claim (issue #293).
issues_body_hash() { # <body> → the digest
  local line
  line=$(issues_prio_marker_line "${1:-}")
  if [ -n "$line" ]; then
    printf '%s' "${1:-}" | awk -v n="$line" 'NR != n'
  else
    printf '%s' "${1:-}"
  fi |
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
# and so exactly one marker is ever in a body — and so a body that quotes the grammar keeps its quotation
# instead of having that line overwritten (issue #293).
issues_body_with_marker() { # <body> <marker> → the new body
  local body="${1:-}" marker="$2" line
  line=$(issues_prio_marker_line "$body")
  if [ -n "$line" ]; then
    printf '%s' "$body" | awk -v n="$line" -v marker="$marker" '
      NR == n { print marker; next }
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

# --------------------------------------------------------------------------- citations
#
# **What a citation is, in one home** (`stale-citation-resolution`, D1–D3). The loop's pre-screen and
# `tools/issues/resolve-stale.sh` both ask these functions, so the two cannot disagree about the same body —
# which is exactly what went wrong when each extracted tokens for itself (#164: 44 of 49 staleness verdicts
# were prose, and the extraction cut `Dorg.gradle.java.h` out of `-Dorg.gradle.java.home`).
#
# The rules are data (`tools/issues/citations.json`): the extensions that make a token look like a source file,
# the header extensions that only count when the token is written as a path, and the artefacts the project
# *generates* rather than tracks. The classes:
#
#   exact       <root>/<token> exists
#   suffix      exactly one tracked path ends with the token (a bare class-file name is a citation, not a bug)
#   submodule   it resolves inside a submodule, whose contents the superproject does not track
#   generated   it names an artefact the project produces, so its absence from the tree is not staleness
#   not-a-path  the token is not a citation at all (a bare header name)
#   absent      no file of that name anywhere — the only class that is staleness
#
# The index is built once per run by the caller (`issues_citation_index_build`), because the suffix test needs
# the tracked file list and a per-token `git ls-files` would fork per citation.
ISSUES_CITATIONS_FILE="$ISSUES_LIB_DIR/citations.json"
ISSUES_CITATIONS_AWK="$ISSUES_LIB_DIR/citations.awk"

issues_citations_rules() { # <jq path> → one value per line; fails loudly, because the rules are data
  [ -f "$ISSUES_CITATIONS_FILE" ] ||
    issues_die "issues: no $ISSUES_CITATIONS_FILE — the citation rules are data and the file ships with the engine"
  jq -r "$1" "$ISSUES_CITATIONS_FILE" 2>/dev/null ||
    issues_die "issues: $ISSUES_CITATIONS_FILE does not parse — expected a JSON object with the citation rules"
}

issues_citation_extensions() { issues_citations_rules '.extensions[]?'; }
issues_citation_header_extensions() { issues_citations_rules '.headerExtensions[]?'; }
issues_citation_mention_only_extensions() { issues_citations_rules '.mentionOnlyExtensions[]?'; }
issues_citation_external_prefixes() { issues_citations_rules '.externalPrefixes[]?'; }
issues_citation_generated() { issues_citations_rules '.generated[]?'; }

# The candidate tokens of a text: boundary-checked, still including the ones that turn out not to be paths.
issues_citation_candidates() { # <text> → one token per line
  local exts pattern
  exts=$(issues_citation_extensions | paste -sd '|' -)
  [ -n "$exts" ] || return 0
  pattern="[A-Za-z0-9_][A-Za-z0-9_./-]*[.]($exts)"
  awk -v pattern="$pattern" -f "$ISSUES_CITATIONS_AWK" | sort -u
}

issues_citation_is_path() { # <token> → 0 when it is a path rather than a mention
  local tok="$1" ext
  [ -n "$tok" ] || return 1
  # An abbreviated mention is not a path: `app/src/test/.../XTest.kt` names no file, and nor does a token
  # that ends in a separator.
  case "$tok" in *..*) return 1 ;; esac
  case "$tok" in */|*//*) return 1 ;; esac
  case "$tok" in *.*) ext="${tok##*.}" ;; *) return 1 ;; esac
  issues_citation_extensions | grep -qxF -- "$ext" || return 1
  if issues_citation_header_extensions | grep -qxF -- "$ext"; then
    case "$tok" in */*) return 0 ;; *) return 1 ;; esac
  fi
  return 0
}

issues_citation_matrix_of() { # <text> → `<class>\t<token>` per citation-shaped token, in text order
  local tok
  while IFS= read -r tok; do
    [ -n "$tok" ] || continue
    printf '%s\t%s\n' "$(issues_citation_class "$tok")" "$tok"
  done < <(printf '%s\n' "$1" | issues_citation_candidates)
}

# The path-shaped tokens: what a consumer asks about. `not-a-path` tokens are dropped here and are still
# *reported* by `issues_citation_matrix_of`, so a reader can see why a name was ignored.
issues_citations_of() { # <text> → one path-shaped token per line
  local text="$1" tok
  while IFS= read -r tok; do
    [ -n "$tok" ] || continue
    issues_citation_is_path "$tok" && printf '%s\n' "$tok"
  done < <(printf '%s\n' "$text" | issues_citation_candidates)
  return 0
}

issues_citation_index_build() { # <root> <file> — the tracked paths, once per run; prints nothing
  local root="$1" file="$2" r sub
  ( cd "$root" && git ls-files --cached --others --exclude-standard ) | sort > "$file" || return 1
  # A submodule's files are not in the superproject's index, and a citation of one is perfectly good: a
  # `OSMScoutClient.cpp` inside `libosmscout-client` resolves. They join the same index, prefixed by their
  # gitlink root, so one suffix test covers both and the class tells them apart.
  while IFS= read -r r; do
    [ -n "$r" ] || continue
    sub="$root/$r"
    [ -d "$sub" ] || continue
    ( cd "$sub" && git ls-files ) 2>/dev/null | sed "s|^|$r/|" >> "$file" || true
  done < <(issues_citation_submodule_roots "$root")
  sort -u "$file" -o "$file"
}

issues_citation_submodule_roots() { # <root> → the gitlink roots, one per line
  ( cd "$1" && git ls-files --stage ) 2>/dev/null | awk '$1 == "160000" { print $4 }'
}

# A citation whose own directory is said twice — `tools/issues/tools/issues/close.sh` — names its answer: the
# collapsed form is the tracked path. Nothing on disk resolves the doubled form, so the classifier answers
# `absent` (correctly, and it must: the token does not exist), while the author meant the path inside it (#189).
issues_citation_collapsed() { # <token> → the token with its repeated leading directory removed, or nothing
  local tok="$1" segments k prefix rest
  segments=$(printf '%s' "$tok" | awk -F/ '{print NF}')
  k=1
  while [ "$k" -le $((segments / 2)) ]; do
    prefix=$(printf '%s' "$tok" | cut -d/ -f1-"$k")
    rest="${tok#"$prefix"/}"
    case "$rest" in
      "$prefix"/*) printf '%s\n' "$rest"; return 0 ;;
    esac
    k=$((k + 1))
  done
  return 0
}

# The canonical path a citation denotes, when the repository states it uniquely: the tracked path itself, or a
# path inside a submodule. Nothing here guesses — an ambiguous suffix prints nothing and is reported.
issues_citation_resolve() { # <token> → the path, or nothing
  local tok="$1" root="${ISSUES_CITATION_ROOT:-$ISSUES_ROOT}" idx="${ISSUES_CITATION_INDEX:-}" roots r found="" n=0
  issues_citation_is_path "$tok" || return 0
  [ -e "$root/$tok" ] && { printf '%s\n' "$tok"; return 0; }
  roots=$(issues_citation_submodule_roots "$root")
  while IFS= read -r r; do
    [ -n "$r" ] || continue
    if [ -e "$root/$r/$tok" ]; then printf '%s\n' "$r/$tok"; return 0; fi
  done <<< "$roots"
  if [ -n "$idx" ] && [ -f "$idx" ]; then
    while IFS= read -r found; do n=$((n + 1)); [ "$n" -eq 1 ] && first="$found"; done \
      < <(awk -v t="$tok" -v s="/$tok" '$0 == t || substr($0, length($0) - length(s) + 1) == s' "$idx")
    [ "$n" -eq 1 ] && printf '%s\n' "$first"
  fi
  return 0
}

issues_citation_class() { # <token> → exact|suffix|submodule|generated|not-a-path|absent
  local tok="$1" root="${ISSUES_CITATION_ROOT:-$ISSUES_ROOT}" idx="${ISSUES_CITATION_INDEX:-}" ext="${1##*.}" prefix r roots n=0 hits=0 first="" match=""
  issues_citation_is_path "$tok" || { printf 'not-a-path\n'; return 0; }
  while IFS= read -r prefix; do
    [ -n "$prefix" ] || continue
    case "$tok" in "$prefix"*) printf 'not-a-path\n'; return 0 ;; esac
  done < <(issues_citation_external_prefixes)
  if [ -n "$idx" ] && [ -f "$idx" ]; then
    while IFS= read -r match; do n=$((n + 1)); [ "$n" -eq 1 ] && first="$match"; done \
      < <(awk -v t="$tok" -v s="/$tok" '$0 == t || substr($0, length($0) - length(s) + 1) == s' "$idx")
    if [ "$n" -gt 0 ]; then
      roots=$(issues_citation_submodule_roots "$root")
      while IFS= read -r r; do
        [ -n "$r" ] || continue
        case "$first" in "$r"/*) printf 'submodule\n'; return 0 ;; esac
      done <<< "$roots"
      [ "$tok" = "$first" ] && { printf 'exact\n'; return 0; }
      printf 'suffix\n'
      return 0
    fi
  fi
  [ -e "$root/$tok" ] && { printf 'exact\n'; return 0; }
  if issues_citation_generated | grep -qxF -- "$tok" || issues_citation_generated | grep -qxF -- "${tok##*/}"; then
    printf 'generated\n'
    return 0
  fi
  # An unresolvable token with a data or a property extension is a runtime path or a property name —
  # `db.json`, `files/favorites.json`, `org.gradle.java.home` — not a source citation.
  if issues_citation_mention_only_extensions | grep -qxF -- "$ext"; then
    printf 'not-a-path\n'
    return 0
  fi
  printf 'absent\n'
}

issues_stale_citations_of() { # <text> → the tokens whose class is absent, one per line
  local tok
  while IFS= read -r tok; do
    [ -n "$tok" ] || continue
    [ "$(issues_citation_class "$tok")" = "absent" ] && printf '%s\n' "$tok"
  done < <(issues_citations_of "$1")
  return 0
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
