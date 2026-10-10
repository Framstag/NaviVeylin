#!/usr/bin/env bash
# Project metrics report — the project's size and architecture-relevant distribution.
#
# Usage:
#   tools/project-metrics-report.sh [scope...]
#
#   scope...              one or more directories to scan (default: the repository root)
#   -h | --help           print this usage block
#
# What one run measures:
#   The project as it is on disk. Figures never depend on version-control state and the
#   tool needs no version-control command, so a dirty worktree, a clean checkout and an
#   export without version-control metadata produce the same figures for the same tree.
#
# Input rule — classify, never drop silently:
#   A filesystem walk prunes these classes at directory level, so nothing inside them is
#   read or counted, and every pruned path is listed in EXCLUDED CLASSES:
#     generated build tree   a directory named build or hostbuild that is not under /src/
#                            (a source package may legitimately be called build)
#     tooling state          .gradle, .kotlin, .cxx, vcpkg, .idea (caches and IDE state)
#     vcs internal           .git
#     agent tool state       .pi (machine-local: session logs, device captures,
#                            instruction dumps, extension config — including .pi/skills,
#                            which is versioned but not project source)
#     machine-local config   local.properties, mise.local.toml, keystore.properties,
#                            release-version.properties, *.keystore — files whose existence
#                            and content depend on the machine, so a local run and a clean
#                            CI clone would otherwise differ without a project change
#     build script           *.gradle, *.gradle.kts (includes settings.gradle.kts and the
#                            init scripts) — build logic, not source or documentation
#     vendored source        app/src/main/cpp/libosmscout (classified, not descended)
#   Files of other types are not counted as source; the header states how many and which.
#
# Output:
#   A complete text report on stdout for manual inspection — no JSON, no snapshots, no
#   thresholds. Every package and every file in scope is listed; nothing is truncated and
#   no option narrows the report. Only --help exists beside the scope arguments.
#
# Exit status:
#   0  a report was printed
#   2  usage error or environment error (unknown option, missing scope, unrecognized
#      source layout, a required tool missing) — never 1: this is an observation,
#      not a gate.
#
# Definitions of every metric and of the surface convention are guidelines/Metrics.md.
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

usage() { sed -n '3,38p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; }
fail() { printf 'project-metrics-report: %s\n' "$1" >&2; exit 2; }

scopes=()
while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    -*) printf 'project-metrics-report: unsupported option %s\n\n' "$1" >&2; usage >&2; exit 2 ;;
    *) scopes+=("$1"); shift ;;
  esac
done
[ ${#scopes[@]} -gt 0 ] || scopes=(".")

for tool in find awk sed sort; do
  command -v "$tool" >/dev/null 2>&1 || fail "required tool '$tool' is not on PATH"
done
for s in "${scopes[@]}"; do
  [ -e "$s" ] || fail "scan scope '$s' does not exist"
  [ -d "$s" ] || fail "scan scope '$s' is not a directory"
  ( cd "$s" ) 2>/dev/null || fail "scan scope '$s' is not a readable directory"
done

work=$(mktemp -d) || fail "cannot create a working directory"
trap 'rm -rf "$work"' EXIT
files="$work/files"
excluded="$work/excluded"
raw="$work/raw"
: > "$files"; : > "$excluded"; : > "$raw"

# One walk per scope. Each excluded class is pruned before anything inside it is read, and
# every pruned directory is recorded with its class so the report can state it.
for s in "${scopes[@]}"; do
  find "$s" \
    \( -type d -name .git \) \
      -prune -exec printf 'vcs internal\t%s\n' {} + \
    -o \( -type d -name .gradle -o -type d -name .kotlin -o -type d -name .cxx -o -type d -name vcpkg -o -type d -name .idea \) \
      -prune -exec printf 'tooling state\t%s\n' {} + \
    -o \( -type d -name .pi \) \
      -prune -exec printf 'agent tool state\t%s\n' {} + \
    -o \( -type d \( -name build -o -name hostbuild \) ! -path '*/src/*' \) \
      -prune -exec printf 'generated build tree\t%s\n' {} + \
    -o \( -type d -path '*/app/src/main/cpp/libosmscout' \) \
      -prune -exec printf 'vendored source\t%s\n' {} + \
    -o \( -type f \( -name local.properties -o -name mise.local.toml \
              -o -name keystore.properties -o -name release-version.properties \
              -o -name '*.keystore' \) \) \
      -exec printf 'machine-local config\t%s\n' {} + \
    -o \( -type f \( -name '*.gradle' -o -name '*.gradle.kts' \) \) \
      -exec printf 'build script\t%s\n' {} + \
    -o -type f -exec printf 'F\t%s\n' {} + \
    >> "$raw" 2>/dev/null
done

while IFS= read -r line; do
  case "$line" in
    F$'\t'*)
      p=${line#F$'\t'}; p=${p#./}
      printf '%s\n' "$p" >> "$files"
      ;;
    *)
      cls=${line%%$'\t'*}; rest=${line#*$'\t'}; rest=${rest#./}
      printf '%s\t%s\n' "$cls" "$rest" >> "$excluded"
      ;;
  esac
done < "$raw"

[ -s "$files" ] || fail "no files found under scan scope(s): ${scopes[*]}"

# Shape check: a scope with no source layout at all is refused before any figure is printed.
grep -qE '/src/[^/]+/(java|kotlin)/' "$files" \
  || fail "no source layout (<module>/src/<set>/{java,kotlin}/…) found under scan scope(s): ${scopes[*]}"

date_now=$(date -Is 2>/dev/null || date)
if [ ${#scopes[@]} -eq 1 ] && [ "${scopes[0]}" = "." ]; then
  scope_display=". (repository root)"
else
  scope_display="${scopes[*]}"
fi

# OpenSpec inventory comes from the CLI's own JSON output, never from walking the store.
openspec_cli=0
openspec_note="the openspec CLI is not available in this environment"
openspec_changes=""; openspec_archived=""; openspec_specs=""
if command -v openspec >/dev/null 2>&1; then
  command -v jq >/dev/null 2>&1 || fail "required tool 'jq' is not on PATH (needed for the OpenSpec inventory)"
  changes_json=$(openspec list --all --json 2>/dev/null) || changes_json=""
  specs_json=$(openspec list --specs --json 2>/dev/null) || specs_json=""
  if [ -n "$specs_json" ] && [ -n "$changes_json" ]; then
    openspec_changes=$(printf '%s' "$changes_json" | jq -r '[.changes[] | select((.archived // false) != true)] | length' 2>/dev/null)
    openspec_archived=$(printf '%s' "$changes_json" | jq -r '[.changes[] | select((.archived // false) == true)] | length' 2>/dev/null)
    openspec_specs=$(printf '%s' "$specs_json" | jq -r '.specs | length' 2>/dev/null)
    # Each count is validated on its own: an unreadable output must never print as a count of 0.
    openspec_cli=1
    for count in "$openspec_changes" "$openspec_archived" "$openspec_specs"; do
      case "$count" in ''|*[!0-9]*) openspec_cli=0 ;; esac
    done
    [ "$openspec_cli" = 1 ] || openspec_note="the openspec CLI did not return a usable inventory"
  else
    openspec_note="the openspec CLI did not return a usable inventory"
  fi
fi

awk -v date="$date_now" -v scope="$scope_display" -v excluded_file="$excluded" \
    -v tmpdir="$work" \
    -v openspec_cli="$openspec_cli" -v openspec_note="$openspec_note" \
    -v openspec_changes="$openspec_changes" \
    -v openspec_archived="$openspec_archived" -v openspec_specs="$openspec_specs" '
# Deterministic ordering without an O(n^2) sort in awk: rows are written with a sort key
# (figure, name) and ordered by one locale-fixed external sort — figure desc, name asc.
# The tab is written as \t inside double quotes: a single quote here would be consumed by the
# shell, because this whole program is itself single-quoted (measured: it silently turned the
# separator into the letter t, leaving -k2,2 inert).
function emit(file, fig, name, text) {
  printf "%d\t%s\t%s\n", fig, name, text >> file
}
function sp(file,   cmd, l, i, j) {
  close(file)                       # flush the rows emit() redirected into this file
  cmd = "LC_ALL=C sort -t\"\t\" -k1,1nr -k2,2 " file
  while ((cmd | getline l) > 0) {
    i = index(l, "\t")
    if (i == 0) { print l; continue }
    l = substr(l, i + 1)
    j = index(l, "\t")
    print (j == 0 ? l : substr(l, j + 1))
  }
  close(cmd)
}
function head(title, c1, c2, c3, c4, c5) {
  printf "\n%s\n", title
  printf "%-52s %8s %8s %8s %8s %8s\n", "", c1, c2, c3, c4, c5
}
function rowof(name, fl, co, cm, bl) {
  return sprintf("%-52s %8d %8d %8d %8d %8d", substr(name, 1, 52), fl, co, cm, bl, co + cm + bl)
}
function row(name, co, cm, bl) {
  return sprintf("%-52s %8d %8d %8d %8d", substr(name, 1, 52), co, cm, bl, co + cm + bl)
}
function lang_of(e) {
  if (e == "kt" || e == "kts")             return "Kotlin"
  if (e == "java")                        return "Java"
  if (e ~ /^(c|cpp|cc|cxx|h|hpp)$/)       return "C/C++"
  if (e == "py")                          return "Python"
  if (e == "sh" || e == "bash")           return "Shell"
  if (e == "awk")                         return "AWK"
  if (e == "gradle")                      return "Gradle"
  if (e == "xml")                         return "XML"
  if (e == "json")                        return "JSON"
  if (e ~ /^(yml|yaml|toml)$/)            return "YAML/TOML"
  if (e ~ /^(properties|pro|cmake|txt)$/) return "Properties/Config"
  if (e == "md")                          return "Markdown"
  return ""
}
function hash_comment(L) {
  return L == "Shell" || L == "AWK" || L == "Python" || L == "YAML/TOML" || L == "Properties/Config"
}
function block_comment(L) { return L == "Kotlin" || L == "Java" || L == "C/C++" || L == "Gradle" }
function html_comment(L)  { return L == "XML" }
function kind_of(path, L,   a, n, i, set) {
  if (L == "Markdown") return "documentation"
  n = split(path, a, "/")
  for (i = 1; i <= n - 3; i++) {
    if (a[i] != "src") continue
    if (a[i + 2] != "java" && a[i + 2] != "kotlin") continue
    set = a[i + 1]
    if (set == "main")        return "production"
    if (set == "test")        return "unit test"
    if (set == "androidTest") return "instrumented test"
    return set
  }
  return "outside a source root"
}
# Module, package and layer exist only for files under <module>/src/<set>/{java,kotlin}/
function layout_of(path,   a, n, i, pkg, junk, start) {
  n = split(path, a, "/")
  for (i = 1; i <= n - 3; i++) {
    if (a[i] != "src") continue
    if (a[i + 2] != "java" && a[i + 2] != "kotlin") continue
    MOD = a[i - 1]        # the module directory is the one owning src/, whatever precedes it
    pkg = ""
    for (start = i + 3; start <= n - 1; start++) pkg = pkg (pkg == "" ? "" : ".") a[start]
    PKG = (pkg == "" ? "(no package)" : pkg)
    if (PKG == "com.naviveylin")            LYR = "(root)"
    else if (PKG ~ /^com\.naviveylin\./) {
      split(substr(PKG, 16), junk, ".")
      LYR = (junk[1] != "" ? junk[1] : "(root)")
    } else if (PKG == "(no package)")       LYR = "(no package)"
    else { split(PKG, junk, "."); LYR = junk[1] "." junk[2] }
    return 1
  }
  return 0
}
# Car surface per guidelines/Metrics.md: the auto module, a word-start Car/Auto/Automotive
# token in a directory or file name, or the …Car<Upper> form the app API itself uses.
function is_car(path, base, bmod,   a, n, i) {
  if (bmod == "auto") return 1
  if (base ~ /(^|[^A-Za-z])(Car|Auto|Automotive)([A-Z0-9_]|$)/) return 1
  if (base ~ /Car[A-Z]/) return 1
  n = split(path, a, "/")
  for (i = 1; i < n; i++) if (a[i] ~ /^(Car|Auto|Automotive)([A-Z0-9_]|$)/) return 1
  return 0
}
BEGIN {
  while ((getline line < excluded_file) > 0) {
    split(line, excf, "\t")
    exc_n[excf[1]]++
    exc_path[excf[1] SUBSEP exc_n[excf[1]]] = excf[2]
  }
  close(excluded_file)
  printf "PROJECT METRICS REPORT\n"
  printf "  date              %s\n", date
  printf "  scope             %s\n", scope
}
{
  path = $0
  base = path; sub(/^.*\//, "", base)
  ext = base
  if (ext !~ /\./) ext = ""; else sub(/^.*\./, "", ext)
  L = lang_of(ext)
  if (L == "") { other_files++; other_ext[(ext == "" ? "(no extension)" : ext)]++; next }
  counted++
  KND = kind_of(path, L)
  mkey = "(unclassified)"; pkey = "(unclassified)"; lkey = "(unclassified)"
  car = 0
  if (layout_of(path)) {
    has_layout = 1
    mkey = MOD; pkey = MOD ":" PKG; lkey = MOD ":" LYR
    car = is_car(path, base, MOD)
  } else if (L != "Markdown") {
    uncl_path[++uncl_n] = path
  }
  c = 0; m = 0; b = 0; block = 0
  while ((getline line < path) > 0) {
    gsub(/\r$/, "", line)
    t = line; sub(/^[ \t]+/, "", t); sub(/[ \t]+$/, "", t)
    if (t == "") { b++; continue }
    if (block == 1) { m++; if (index(t, "*/") > 0) block = 0; continue }
    if (block == 2) { m++; if (index(t, "-->") > 0) block = 0; continue }
    if (block_comment(L) && substr(t, 1, 2) == "//") { m++; continue }
    if (hash_comment(L)  && substr(t, 1, 1) == "#")  { m++; continue }
    if (block_comment(L) && substr(t, 1, 2) == "/*") {
      m++; if (index(t, "*/") == 0) block = 1; continue
    }
    if (html_comment(L) && substr(t, 1, 3) == "<!--") {
      m++; if (index(t, "-->") == 0) block = 2; continue
    }
    c++
  }
  close(path)
  # language
  lf[L]++; lc[L] += c; lm[L] += m; lb[L] += b
  # dimensions
  pf[pkey]++; pc[pkey] += c; pm[pkey] += m; pb[pkey] += b
  if (mkey != "(unclassified)") { mf[mkey]++; mc[mkey] += c; mm[mkey] += m; mb[mkey] += b }
  if (lkey != "(unclassified)") { yf[lkey]++; yc[lkey] += c; ym[lkey] += m; yb[lkey] += b }
  df[KND]++; dc[KND] += c; dm[KND] += m; db[KND] += b
  # per file
  ff[path]++; fc[path] += c; fm2[path] += m; fb[path] += b; ft[path] = c + m + b
  # The per-file lists are source-only: documentation and OpenSpec store files are counted,
  # but they are aggregated in LANGUAGE and SOURCE SET instead of listed one by one.
  listed[path] = (L != "Markdown" && path !~ /(^|\/)openspec\//)
  if (!listed[path]) {
    if (L == "Markdown") { docn++; doct += ft[path] }
    else                 { storen++; storet += ft[path] }
  }
  if (car) { carf[path] = c; CAR_CODE += c; CAR_FILES++ }
  else if (KND != "documentation" && mkey != "(unclassified)") { phone_code += c; phone_files++ }
  TOTAL_C += c; TOTAL_M += m; TOTAL_B += b
  if (L == "Markdown") { doc_c += c; doc_m += m; doc_b += b }
}
END {
  printf "  files measured    %d\n", counted
  if (other_files > 0) {
    n = 0
    for (k in other_ext) { oe[++n] = k; ov[k] = other_ext[k] }
    for (i = 2; i <= n; i++) {
      key = oe[i]; v = ov[key]; j = i - 1
      while (j >= 1 && (ov[oe[j]] < v || (ov[oe[j]] == v && oe[j] > key))) { oe[j + 1] = oe[j]; j-- }
      oe[j + 1] = key
    }
    extlist = ""
    for (i = 1; i <= n && i <= 8; i++) extlist = extlist (i > 1 ? ", " : "") oe[i] " " ov[oe[i]]
    printf "  not counted       %d files of other types (%s)\n", other_files, extlist
  }
  cls = ""
  for (ex in exc_n) cls = cls (cls == "" ? "" : ", ") ex " (" exc_n[ex] ")"
  printf "  excluded classes  %s\n", (cls == "" ? "none present in scope" : cls)

  head("LANGUAGE", "FILES", "CODE", "COMMENT", "BLANK", "TOTAL")
  f = tmpdir "/language.rows"
  for (k in lc) emit(f, lc[k], k, rowof(k, lf[k], lc[k], lm[k], lb[k]))
  sp(f)
  printf "%-52s %8d %8d %8d %8d %8d\n", "TOTAL (files measured)", counted, TOTAL_C, TOTAL_M, TOTAL_B, TOTAL_C + TOTAL_M + TOTAL_B

  head("MODULE", "FILES", "CODE", "COMMENT", "BLANK", "TOTAL")
  f = tmpdir "/module.rows"
  for (k in mc) emit(f, mc[k], k, rowof(k, mf[k], mc[k], mm[k], mb[k]))
  sp(f)

  head("PACKAGE (complete, ranked by total lines)", "FILES", "CODE", "COMMENT", "BLANK", "TOTAL")
  f = tmpdir "/package.rows"
  for (k in pc) {
    if (k == "(unclassified)") continue
    depth = split(k, tmp, ".") - 1
    emit(f, pc[k], k, rowof(k "(d" depth ")", pf[k], pc[k], pm[k], pb[k]))
  }
  sp(f)

  head("LAYER (module: first level under the package root)", "FILES", "CODE", "COMMENT", "BLANK", "TOTAL")
  f = tmpdir "/layer.rows"
  for (k in yc) emit(f, yc[k], k, rowof(k, yf[k], yc[k], ym[k], yb[k]))
  sp(f)

  head("DISTRIBUTION", "PACKAGES", "", "", "", "")
  for (k in pc) {
    if (k == "(unclassified)") continue
    depth = split(k, tmp, ".") - 1
    dh["depth " depth]++
    t = pc[k] + pm[k] + pb[k]
    dh[(t < 100 ? "size under 100 lines" : (t < 500 ? "size 100-499 lines" : (t < 2000 ? "size 500-1999 lines" : "size 2000+ lines")))]++
  }
  f = tmpdir "/distribution.rows"
  for (k in dh) emit(f, 0, k, sprintf("%-52s %8d", k, dh[k]))
  sp(f)

  printf "\n%s\n", "FILES (source files, complete, ranked by total lines)"
  printf "%8s %8s %8s %8s  %s\n", "CODE", "COMMENT", "BLANK", "TOTAL", "path"
  f = tmpdir "/file.rows"
  for (p in ft) if (listed[p]) emit(f, ft[p], p, sprintf("%8d %8d %8d %8d  %s", fc[p], fm2[p], fb[p], ft[p], p))
  sp(f)
  printf "  not listed here: %d documentation file(s) (%d lines), %d OpenSpec store file(s) (%d lines) —\n", docn, doct, storen, storet
  printf "  both are counted and aggregated in LANGUAGE and SOURCE SET; only the per-file list is source-only\n"

  head("SOURCE SET", "FILES", "CODE", "COMMENT", "BLANK", "TOTAL")
  f = tmpdir "/sourceset.rows"
  for (k in dc) emit(f, dc[k], k, rowof(k, df[k], dc[k], dm[k], db[k]))
  sp(f)
  for (k in df) { sf += df[k]; sc += dc[k]; sm += dm[k]; sb += db[k] }
  printf "%-52s %8d %8d %8d %8d %8d\n", "TOTAL", sf, sc, sm, sb, sc + sm + sb

  printf "\nRATIOS (each printed with its operands; ratios about source never divide by documentation)\n"
  tot = TOTAL_C + TOTAL_M + TOTAL_B
  src_code = TOTAL_C - dc["documentation"]
  printf "  comment share        %7.1f%%   comment / total lines        = %d / %d\n", (tot ? 100 * TOTAL_M / tot : 0), TOTAL_M, tot
  printf "  test share           %7.1f%%   test code / source code lines = %d / %d\n", (src_code ? 100 * (dc["unit test"] + dc["instrumented test"]) / src_code : 0), dc["unit test"] + dc["instrumented test"], src_code
  printf "  documentation share  %7.1f%%   documentation code / code    = %d / %d\n", (TOTAL_C ? 100 * dc["documentation"] / TOTAL_C : 0), dc["documentation"], TOTAL_C
  printf "  average file size    %7.1f    total lines / files measured = %d / %d\n", (counted ? tot / counted : 0), tot, counted

  printf "\nPLATFORM (code under a module source root; documentation and files outside a source root excluded)\n"
  plat = CAR_CODE + phone_code
  printf "  car code     %8d lines, %8d files   %7.1f%% of %d code lines\n", CAR_CODE, CAR_FILES, (plat ? 100 * CAR_CODE / plat : 0), plat
  printf "  phone code   %8d lines, %8d files   %7.1f%% of %d code lines\n", phone_code, phone_files, (plat ? 100 * phone_code / plat : 0), plat
  printf "  car files (complete):\n"
  f = tmpdir "/car.rows"
  for (p in carf) emit(f, carf[p], p, sprintf("    %8d lines  %s", carf[p], p))
  sp(f)
  if (CAR_FILES == 0) printf "    (none)\n"

  printf "\nOPENSPEC\n"
  if (openspec_cli == 1) {
    printf "  changes (open)          %8d\n", openspec_changes + 0
    printf "  changes (archived)      %8d\n", openspec_archived + 0
    printf "  feature directories     %8d\n", openspec_specs + 0
    printf "  source                  openspec list --all --json, openspec list --specs --json\n"
  } else {
    printf "  unavailable             %s\n", openspec_note
  }

  printf "\nUNCLASSIFIED (source outside <module>/src/<set>/{java,kotlin}/, attributed to no module, package or layer)\n"
  f = tmpdir "/unclassified.rows"
  uncl_listed = 0
  for (i = 1; i <= uncl_n; i++) {
    if (!listed[uncl_path[i]]) continue
    uncl_listed++
    emit(f, ft[uncl_path[i]], uncl_path[i], sprintf("  %8d lines  %s", ft[uncl_path[i]], uncl_path[i]))
  }
  sp(f)
  if (uncl_listed == 0) printf "  (no source file outside a layout)\n"
  if (uncl_n > uncl_listed)
    printf "  %d further file(s) outside a source root are documentation or OpenSpec store files, aggregated above\n", uncl_n - uncl_listed

  printf "\nEXCLUDED CLASSES (directories pruned before descending, files excluded by name — never read)\n"
  if (cls == "") printf "  (none present in scope)\n"
  f = tmpdir "/excluded.rows"
  for (ex in exc_n) {
    emit(f, 0, ex, sprintf("  %s", ex))
    for (i = 1; i <= exc_n[ex]; i++) emit(f, 0, ex "/" exc_path[ex SUBSEP i], sprintf("    %s", exc_path[ex SUBSEP i]))
  }
  sp(f)
  printf "\nfiles of other types, not counted as source: %d\n", other_files
}' "$files"
exit $?
