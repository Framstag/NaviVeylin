#!/usr/bin/env bash
# check-portability.sh — an engine names no project value.
#
# The tooling is meant to be copied into another project: the gate, the taxonomy reader, the verbs, the
# loop's phases, the metadata pass and the sensors' runner are *engines*, and everything that is THIS
# project's lives in a committed config (`tools/tooling.json`, `tools/sensors/sensors.json`,
# `tools/loop/loop.conf`). One leaked literal — a repository name, an allowlisted author, a value of the
# vocabulary, a path the registry declares, the gate command — is the whole difference between "the tool
# runs in someone else's repository" and "the second project discovers the leaks one at a time"
# (`tooling-portability` D1, `guidelines/Backlog.md` §10).
#
# The rule is a check over a **declared value set**, not a hand-written ban list: the values it forbids
# are read from the project's own profile and per-tool configs at run time, so a value added to the
# profile is immediately forbidden in an engine and a stale list cannot fail open (D1).
#
# Two match modes, because the two kinds of value are not the same kind of thing:
#
#   * **literal** — a fixed substring, searched on every line. A repository name, an allowed author, a
#     command or path the registry declares, a gate command: a string that is this project's wherever it
#     appears, a comment included.
#   * **word** — a word-bounded token that must be *named*: the line counts only when the value sits inside
#     a quote, is the argument of a flag (a token of its own, not a substring of a flag's name and not the
#     left half of a `name=value` pair), or is the whole of a `<namespace><separator><value>` label. A value
#     inside an identifier, a path or a sentence is not named — which is what lets a project's category be
#     an ordinary word. A bare word standing before another tool's own option (`gh --state open` — GitHub's
#     word, not this project's) is not evidence of a leak: no scan can tell the two apart, and a rule that
#     fires on both is a rule nobody keeps. Comment lines are skipped for these, exactly as the repository's
#     own invariant case skips them: a comment that explains a value is not a leak.
#
# What is checked, and what is not, is printed on every run — an exclusion a reader cannot see is a
# proof that has quietly shrunk:
#
#   * the values: every namespace's values and their label forms, the profile's `roles` section (which
#     namespace value each engine decision means), the repository, the allowlisted authors, the registry's
#     declared commands, inputs and escalation labels, and the loop's configuration (its gate command and
#     its submodule path). The reserved labels are the one documented exception: the single read-only
#     declaration of the label that authorises work is asserted and printed, and any other occurrence is a
#     leak;
#   * the engines: the issue tooling's wrappers and the Java core, the sensors' runner, guards, report,
#     measurements and conditions, and the loop's scripts;
#   * the templates: the loop's prompt skeletons, where the value's placeholders are substituted at
#     render time and a hard-coded value would arrive in a child's prompt;
#   * excluded: the source adapter (it reads this project's own record format and is *allowed* to know
#     it), the case runner, the self-tests and the falsification harnesses, every test fixture, and the
#     project's own documents and machine-local state.
#
# Reads text files only: no build, no device, no token, no network. Exit 0 the tree is portable, 1 a
# project value was found in an engine, 2 the environment or a config is unusable.
#
# Usage: check-portability.sh [--root <dir>] [--values] [--quiet]
set -uo pipefail

root=""
show_values=0
quiet=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --values) show_values=1; shift ;;
    --quiet) quiet=1; shift ;;
    -h|--help) sed -n '2,50p' "$0"; exit 0 ;;
    *) printf 'check-portability: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done

if [ -z "$root" ]; then
  root=$(git rev-parse --show-toplevel 2>/dev/null) || root=""
fi
if [ -z "$root" ]; then
  printf 'check-portability: not inside a git working tree — pass --root <dir>\n' >&2
  exit 2
fi

for tool in jq grep sed awk sort; do
  command -v "$tool" >/dev/null 2>&1 || {
    printf 'check-portability: %s is required — the value set is JSON and the scan is a grep\n' "$tool" >&2
    exit 2
  }
done

profile="$root/tools/tooling.json"
registry="$root/tools/sensors/sensors.json"
loopconf="$root/tools/loop/loop.conf"
for f in "$profile" "$registry"; do
  [ -f "$f" ] || { printf 'check-portability: no %s — the project profile is where the value set comes from\n' "$f" >&2; exit 2; }
  jq -e . "$f" >/dev/null 2>&1 || { printf 'check-portability: %s is not valid JSON\n' "$f" >&2; exit 2; }
done
[ -f "$loopconf" ] || { printf 'check-portability: no %s — the gate command and the worker skills are read there\n' "$loopconf" >&2; exit 2; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/check-portability.XXXXXX") || { printf 'check-portability: cannot create a scratch directory\n' >&2; exit 2; }
trap 'rm -rf "$tmp"' EXIT

# --------------------------------------------------------------------------- the value set (D1)

# value<TAB>source<TAB>token|literal|word
: > "$tmp/values"
add() { # <value> <source> [token|literal|word]
  [ -n "$1" ] || return 0
  case "$1" in *"	"*) return 0 ;; esac
  printf '%s\t%s\t%s\n' "$1" "$2" "${3:-literal}" >> "$tmp/values"
}

# The classifier: an occurrence is a *name* when the value opens a quoted string, ends one that is not the
# tail of a path or an identifier, or is the argument of a flag. A value inside an identifier, a path or a
# sentence is not named — which is what lets a project's category be an ordinary word.
cat > "$tmp/name.awk" <<'AWK'
function pathish(c) { return (c == "/" || c == "-" || c == ".") }
{
  text = $0; n = length(val); leak = 0; start = 1
  while (start <= length(text)) {
    i = index(substr(text, start), val)
    if (i == 0) break
    i = start + i - 1
    l = (i == 1) ? "" : substr(text, i - 1, 1)
    r = (i + n > length(text)) ? "" : substr(text, i + n, 1)
    if ((l == "" || l !~ /[A-Za-z0-9_]/) && (r == "" || r !~ /[A-Za-z0-9_]/)) {
      ll = (i <= 2) ? "" : substr(text, i - 2, 1)
      if ((l == "\"" || l == "'") && !pathish(ll)) { leak = 1 }
      else if ((r == "\"" || r == "'") && !pathish(l)) { leak = 1 }
      else if ((l == " " || l == "\t" || l == "=") && r != "=") {
        # …as the argument of a flag: `--status on-hold`. The value must be a token of its own, so a value
        # glued into a flag's name (`--eligible-only`) or into a `name=value` pair (`-v open=…`) is not an
        # argument at all — and a bare word standing before another tool's own option (`gh --state open`,
        # GitHub's vocabulary, not this project's) is where the rule stops, because no scan can tell the
        # two apart and a rule that fires on both is a rule nobody keeps.
        pre = substr(text, 1, i - 1); sub(/[ \t]+$/, "", pre)
        m = split(pre, a, /[ \t]+/)
        if (m > 0 && a[m] ~ /^-/) leak = 1
      }
    }
    if (leak) break
    start = i + n
  }
  if (leak) print
}
AWK

repo=$(jq -r '.repo // empty' "$profile")
[ -n "$repo" ] || { printf 'check-portability: %s declares no repo\n' "$profile" >&2; exit 2; }
# fixed strings on a word boundary: a login can be a short sequence of letters that occurs inside another
# word (`ada` in `metadata`), so a substring search would report the engine's own vocabulary
add "$repo" "tools/tooling.json repo" token
name=${repo##*/}
add "$name" "tools/tooling.json repo" token
add "$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')" "tools/tooling.json repo" token

while IFS= read -r author; do add "$author" "tools/tooling.json allowedAuthors" token; done < <(jq -r '.allowedAuthors[]?' "$profile")

# the vocabulary: EVERY namespace's values and each one's label form — not the category namespace alone.
# the reserved labels are the one documented exception: an engine must be able to READ the label that
# authorises work. Added before the namespaces, so the exception wins when a namespace's label form is the
# same string; asserted below and printed in the header, never silently tolerated.
while IFS= read -r label; do add "$label" "tools/tooling.json reservedLabels" exception; done < <(jq -r '.reservedLabels[]?' "$profile")

# The namespace *names* stay the contract, so they are read and never forbidden on their own: a value that
# is also a namespace name (`agent` is one of the `source` values) cannot be told from the name, while the
# label form `<namespace><separator><value>` can.
separator=$(jq -r '.namespaceSeparator // "/"' "$profile")
namespace_names=$(jq -r '.namespaces | keys[]?' "$profile")
while IFS= read -r namespace; do
  while IFS= read -r value; do
    [ -n "$value" ] || continue
    if ! printf '%s\n' "$namespace_names" | grep -qxF "$value"; then
      add "$value" "tools/tooling.json namespaces.${namespace}" word
    fi
    add "${namespace}${separator}${value}" "tools/tooling.json namespaces.${namespace} label"
  done < <(jq -r --arg ns "$namespace" '.namespaces[$ns][]?' "$profile")
done < <(printf '%s\n' "$namespace_names")

# the profile's `roles` section: which namespace value each of the engines' decisions means, so its values
# are project values exactly as the namespaces' are. A numeric or boolean role is a count or a switch, not a
# name — `firstCategoryEvery: 4` would otherwise forbid every `$4` in the engines.
while IFS= read -r value; do add "$value" "tools/tooling.json roles" word; done < <(
  jq -r '.roles | to_entries[] | select(.key != "comment") | .value
    | if type == "array" then .[] else . end
    | select(type != "number" and type != "boolean")' "$profile" 2>/dev/null)

# the registry's declared commands, inputs and escalation labels: paths this project declared belong in
# the registry, and a label the registry writes belongs there too
while IFS= read -r value; do add "$value" "tools/sensors/sensors.json rows[].cmd"; done < <(jq -r '.rows[]?.cmd // empty' "$registry")
while IFS= read -r value; do add "$value" "tools/sensors/sensors.json rows[].input"; done < <(jq -r '.rows[]?.input // empty' "$registry")
while IFS= read -r value; do add "$value" "tools/sensors/sensors.json escalation.labels"; done < <(jq -r '.escalation.labels[]? // empty' "$registry")

# loop.conf: the gate command and the skills the worker is told to use are this project's, not the loop's
conf_value() { # <KEY> → the double-quoted value, or nothing
  sed -n "s/^${1}=\"\\(.*\\)\"\$/\1/p" "$loopconf" | head -1
}
add "$(conf_value LOOP_GATE_CMD)" "tools/loop/loop.conf LOOP_GATE_CMD"
# the submodule path is a project path: it belongs in the loop's configuration, not in its worktree
add "$(conf_value LOOP_SUBMODULE_PATH)" "tools/loop/loop.conf LOOP_SUBMODULE_PATH"
# The module build directories the loop removes from a kept worktree are this project's paths as well (issue
# #210): a directory a build regenerates is named by the project that builds it, never by an engine.
while IFS= read -r value; do
  add "$value" "tools/loop/loop.conf LOOP_WORKTREE_SCRATCH_PATHS"
done < <(printf '%s' "$(conf_value LOOP_WORKTREE_SCRATCH_PATHS)" | tr ' ' '\n' | grep -v '^$')
# The paths the evidence check reads are the project's as well (issue #255): the check is an engine, so its scan
# roots and its tally directory are configuration, and an engine that writes one of them is a leak like any other.
while IFS= read -r value; do
  add "$value" "tools/loop/loop.conf LOOP_EVIDENCE_SCAN_PATHS"
done < <(printf '%s' "$(conf_value LOOP_EVIDENCE_SCAN_PATHS)" | tr ' ' '\n' | grep -v '^$')
add "$(conf_value LOOP_EVIDENCE_XML_DIR)" "tools/loop/loop.conf LOOP_EVIDENCE_XML_DIR"
while IFS= read -r skill; do add "$skill" "tools/loop/loop.conf LOOP_WORKER_SKILLS" word; done \
  < <(printf '%s' "$(conf_value LOOP_WORKER_SKILLS)" | tr ' ' '\n')

# One occurrence per value: a value the profile declares twice (a namespace value, and the role or label
# that names it) is one fact, and the source reported is the one it was read from first.
awk -F'\t' '!seen[$1]++' "$tmp/values" > "$tmp/values.unique"
mv "$tmp/values.unique" "$tmp/values"

# --------------------------------------------------------------------------- the engine set (D1)

list_engines() { # the code engines, relative to the root
  (
    cd "$root" || exit 1
    ls tools/issues/*.sh tools/issues/*.awk tools/issues/core/*.java tools/sensors/*.sh \
       tools/sensors/conditions/*.sh tools/sensors/metrics/*.sh tools/loop/*.sh 2>/dev/null
  ) | sort -u | grep -vE '/(Cases\.java|TodoAdapter\.java|TodoSections\.java|metadata-mutations\.sh|[a-z0-9-]+-selftest\.sh|[a-z0-9-]+-cases\.sh|[a-z0-9-]+-mutations\.sh)$'
}

list_templates() { # the loop's prompt skeletons
  (
    cd "$root" || exit 1
    ls tools/loop/prompts/*.md 2>/dev/null
  ) | sort -u
}

list_excluded() {
  cat <<'EOF'
tools/issues/import/todo-md.sh	the source adapter — reads the one-time prose record's format, and is allowed to
tools/issues/core/TodoAdapter.java	the same adapter: it names the record format and the provenance value it
tools/issues/core/TodoSections.java	the adapter's grammar — the headings and the metadata line of that format
tools/issues/core/Cases.java	the case runner
tools/issues/metadata-mutations.sh	the falsification harness
tools/issues/check-metadata-selftest.sh	the self-test
tools/sensors/fixtures/**	test data
tools/issues/fixtures/**	test data
tools/loop/fixtures/**	test data
tools/fixtures/**	test data — the synthetic project and the mutation runner's toy tree
tools/*-mutations*.json	the mutation sets — they plant the very values the check forbids
.pi/**	machine-local state: worktrees, captures, session logs
openspec/**	the project's documents
guidelines/**	the project's documents
EOF
}

engines=$(list_engines)
templates=$(list_templates)
[ -n "$engines" ] || { printf 'check-portability: no engine found under %s — the engine set would be empty\n' "$root" >&2; exit 2; }

count_lines() { printf '%s\n' "$1" | grep -c . ; }

if [ "$quiet" -eq 0 ]; then
  printf 'check-portability: engines checked (%s files):\n' "$(count_lines "$engines")"
  printf '  %s\n' $engines
  printf 'check-portability: templates checked (%s files):\n' "$(count_lines "$templates")"
  printf '  %s\n' $templates
  printf 'check-portability: excluded, printed so the proof names its own scope:\n'
  list_excluded | sed 's/^/  /'
fi

if [ "$show_values" -eq 1 ]; then
  printf 'check-portability: values forbidden (%s):\n' "$(count_lines "$(cut -f1 "$tmp/values")")"
  while IFS=$'\t' read -r value source mode; do
    printf '  %-40s %s [%s]\n' "$value" "$source" "$mode"
  done < "$tmp/values"
fi

# --------------------------------------------------------------------------- the scan

# A value that carries a space or a path separator is a fixed string; a single token is also matched on a
# word boundary, so the two-letter category `ui` does not fire on `build` (measured: a substring scan
# reported 14 false positives over this tree, a word-boundary scan reported none).
engine_paths=()
while IFS= read -r f; do [ -n "$f" ] && engine_paths+=("$root/$f"); done <<< "$engines"
template_paths=()
while IFS= read -r f; do [ -n "$f" ] && template_paths+=("$root/$f"); done <<< "$templates"

leaks=0
exception_note=""
report_hits() { # <hits> <comment-filter 0|1> — one line at a time, the absolute prefix stripped
  local hits="$1" filtered
  filtered="$hits"
  if [ "$2" = "1" ] && [ -n "$filtered" ]; then
    filtered=$(printf '%s\n' "$filtered" | grep -vE ':[[:space:]]*(#|//|\*|/\*)' || true)
  fi
  [ -n "$filtered" ] || return 0
  local hit
  while IFS= read -r hit; do
    [ -n "$hit" ] || continue
    printf 'check-portability: FAIL %s: names the project value '\''%s'\'' (%s)\n' \
      "${hit#"$root"/}" "$value" "$source"
    leaks=$((leaks + 1))
  done <<< "$filtered"
}

scan() { # <mode> <comment-filter 0|1> <paths…> — the current $value over one file list
  local match_mode="$1" filter="$2"; shift 2
  # an empty file list would leave grep reading the caller's stdin: a project that copied the engines
  # without the prompt skeletons must still be checkable
  [ "$#" -gt 0 ] || return 0
  local hits=""
  if [ "$match_mode" = "exception" ]; then
    # The reserved label is the ONE documented exception, and it is ASSERTED rather than tolerated: the
    # only accepted occurrence is a single read-only declaration (`NAME="<label>"`, nothing else on the
    # line), printed with its file and line. A second declaration, or any other occurrence — which is what
    # a write path spelling the label looks like — is a leak.
    local hit text declared="" count=0
    hits=$(grep -n -F -- "$value" "$@" 2>/dev/null | grep -vE ':[[:space:]]*(#|//|\*|/\*)' || true)
    while IFS= read -r hit; do
      [ -n "$hit" ] || continue
      text=$(printf '%s' "$hit" | cut -d: -f3-)
      if [[ "$text" =~ ^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*=\"$value\"[[:space:]]*$ ]]; then
        declared="${declared}${hit}
"; count=$((count + 1))
      fi
    done <<< "$hits"
    if [ "$count" -gt 1 ]; then
      printf 'check-portability: FAIL %s read-only declarations of the reserved label '\''%s'\'' — one is the documented exception\n' \
        "$count" "$value"
      while IFS= read -r hit; do
        [ -n "$hit" ] || continue
        printf '  %s\n' "${hit#"$root"/}"
      done <<< "$declared"
      leaks=$((leaks + 1))
      return 0
    fi
    if [ "$count" -eq 1 ]; then
      exception_note="${exception_note}${declared}"
      hits=$(printf '%s\n' "$hits" | grep -vxF "$(printf '%s' "$declared" | head -1)" || true)
    fi
    report_hits "$hits" "$filter"
    return 0
  fi
  case "$match_mode" in
    word)
      hits=$(grep -n -w -F -- "$value" "$@" 2>/dev/null | awk -v val="$value" -f "$tmp/name.awk" || true)
      ;;
    token)
      hits=$(grep -n -w -F -- "$value" "$@" 2>/dev/null || true)
      ;;
    *)
      hits=$(grep -n -F -- "$value" "$@" 2>/dev/null || true)
      ;;
  esac
  report_hits "$hits" "$filter"
}

while IFS=$'\t' read -r value source mode; do
  [ -n "$value" ] || continue
  # A repository name or an allowlisted author is this project's wherever it appears, a comment included;
  # every other value is a code fact, and a comment that explains one is not a leak. A template has no
  # comment syntax this filter knows, so it is scanned unfiltered.
  case "$mode" in
    token) filter=0 ;;
    *) filter=1 ;;
  esac
  scan "$mode" "$filter" "${engine_paths[@]}"
  scan "$mode" 0 "${template_paths[@]}"
done < "$tmp/values"

# the one documented exception, asserted above and printed here, so a reader can see what the check does
# NOT report and why
if [ "$quiet" -eq 0 ] && [ -n "$exception_note" ]; then
  printf 'check-portability: the documented exception — the label that authorises work is declared read-only, once:\n'
  while IFS= read -r hit; do
    [ -n "$hit" ] || continue
    printf '  %s\n' "${hit#"$root"/}"
  done <<< "$exception_note"
fi

if [ "$leaks" -gt 0 ]; then
  printf 'check-portability: %s leak(s) — a project value belongs in a config, never in an engine\n' "$leaks"
  exit 1
fi

printf 'check-portability: OK — %s engines and %s templates name no project value (%s value(s) read from the project)\n' \
  "$(count_lines "$engines")" "$(count_lines "$templates")" "$(count_lines "$(cut -f1 "$tmp/values")")"
exit 0
