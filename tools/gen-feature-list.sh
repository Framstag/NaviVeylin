#!/usr/bin/env bash
# Generate the user-facing feature catalogue and the per-version release notes from the shipped specs.
#
# The shipped capability set is `openspec/specs/`: a capability reaches it when a change archives, so the
# directory is the product's shipped surface and needs no filter. One capability key identifies one
# requirement: `<spec-id>#<Requirement name>`.
#
# This script is the deterministic half of the pipeline: it enumerates specs, derives the keys, digests
# each capability's text, computes the delta against a version's baseline snapshot, decides which feature
# areas are dirty, and gates the generated prose. It invokes no language model of its own — where prose is
# wanted it runs the command named by --phrase-cmd (or GEN_FEATURE_LIST_PHRASE_CMD), which is also how a
# no-op run is provably model-free.
#
# Reads text files only: no build, no device, no network, no python3 (openspec/config.yaml).
#
# Usage:
#   gen-feature-list.sh --release <versionName> [options]
#   gen-feature-list.sh --check-classification
#
# Options:
#   --root <dir>          repository root                          (default: this script's parent)
#   --data-dir <dir>      classification, prompts, snapshots      (default: <script dir>/feature-list)
#   --out-dir <dir>       where the documents are written         (default: <root>)
#   --release <version>   the release version, e.g. 2026-10-08-1  (required for a normal run)
#   --emit-bundles <dir>  write one bundle per area needing prose and stop; a harness phrases them
#   --sections <dir>      read <area-id>.md per area needing prose from this directory
#   --phrase-cmd <cmd>    command producing one area's prose; receives a bundle on stdin
#   --check-classification  run the classification gate and exit
#   --list                print every capability with its area, visibility and surfaces, then exit
#   --report-only         compute and report, write nothing
#   -h | --help
#
# Exit status: 0 ok, 1 a gate failed, 2 usage or environment error.
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

root=""
data_dir=""
out_dir=""
release=""
phrase_cmd="${GEN_FEATURE_LIST_PHRASE_CMD:-}"
bundles_dir=""
sections_dir=""
mode="run"
write=1
listing=0

usage() { sed -n '3,30p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    --root) root="${2:-}"; shift 2 ;;
    --data-dir) data_dir="${2:-}"; shift 2 ;;
    --out-dir) out_dir="${2:-}"; shift 2 ;;
    --release) release="${2:-}"; shift 2 ;;
    --phrase-cmd) phrase_cmd="${2:-}"; shift 2 ;;
    --emit-bundles) bundles_dir="${2:-}"; shift 2 ;;
    --sections) sections_dir="${2:-}"; shift 2 ;;
    --check-classification) mode="check"; shift ;;
    --list) listing=1; shift ;;
    --report-only) write=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "gen-feature-list: unknown argument '$1'" >&2; exit 2 ;;
  esac
done

[ -n "$root" ] || root=$(cd "$script_dir/.." && pwd)
[ -n "$data_dir" ] || data_dir="$script_dir/feature-list"
[ -n "$out_dir" ] || out_dir="$root"

command -v jq >/dev/null 2>&1 || { echo "gen-feature-list: jq is required" >&2; exit 2; }
command -v sha256sum >/dev/null 2>&1 || { echo "gen-feature-list: sha256sum is required" >&2; exit 2; }

specs_dir="$root/openspec/specs"
areas_file="$data_dir/areas.json"
specs_file="$data_dir/specs.json"
highlights_file="$data_dir/highlights.json"
maxpara=320   # the paragraph length bound, spec `feature-list-generation` "A paragraph stays short"
prompts_dir="$data_dir/prompts"
snapshots_dir="$data_dir/snapshots"
cache_dir="$data_dir/cache"

[ -d "$specs_dir" ] || { echo "gen-feature-list: no $specs_dir" >&2; exit 2; }
[ -f "$areas_file" ] || { echo "gen-feature-list: no $areas_file" >&2; exit 2; }
[ -f "$specs_file" ] || { echo "gen-feature-list: no $specs_file" >&2; exit 2; }
jq -e . "$areas_file" >/dev/null 2>&1 || { echo "gen-feature-list: $areas_file is not valid JSON" >&2; exit 2; }
jq -e . "$specs_file" >/dev/null 2>&1 || { echo "gen-feature-list: $specs_file is not valid JSON" >&2; exit 2; }
jq -e . "$highlights_file" >/dev/null 2>&1 || { echo "gen-feature-list: $highlights_file is not valid JSON — the shortlist decides what the catalogue publishes" >&2; exit 2; }

if [ "$mode" = "run" ] && [ "$listing" -eq 1 ] && [ -z "$release" ]; then
  # --list reports the index and needs no version; it is a reading of the classification, not a release run.
  release="listing"
fi
if [ "$mode" = "run" ] && [ -z "$release" ]; then
  echo "gen-feature-list: --release <versionName> is required (the build's version-state file is" >&2
  echo "gen-feature-list: gitignored and machine-local, so the version is passed in, never discovered)" >&2
  exit 2
fi

US=$'\037'   # field separator
RS=$'\036'   # record separator

work=$(mktemp -d) || { echo "gen-feature-list: cannot create a work directory" >&2; exit 2; }
trap 'rm -rf "$work"' EXIT

problems=0
warn() { echo "gen-feature-list: $*" >&2; }
fail() { echo "gen-feature-list: $*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. Enumerate the shipped specs and extract each requirement's text
# ---------------------------------------------------------------------------

# Requirement block = `### Requirement: <name>` through the line before the next `### ` or `## ` heading.
# Trailing whitespace is trimmed and blank-line runs collapse, so a whitespace-only edit is not a change.
awk -v prefix="$specs_dir/" '
  function flush() {
    if (reqname != "") printf "%s\037%s\037%s\036", sid, reqname, reqtext
    reqname = ""; reqtext = ""; lastblank = 0
  }
  FNR == 1 {
    flush()
    sid = FILENAME
    sub("^" prefix, "", sid)
    sub("/spec.md$", "", sid)
  }
  /^### Requirement:/ {
    flush()
    reqname = substr($0, 17)
    sub(/^[ \t]+/, "", reqname); sub(/[ \t]+$/, "", reqname)
    inreq = 1
    next
  }
  /^#{1,3} / { if (inreq) flush(); inreq = 0; next }
  inreq {
    line = $0
    sub(/[ \t]+$/, "", line)
    if (line == "") { if (lastblank) next; lastblank = 1 } else lastblank = 0
    reqtext = reqtext (reqtext == "" ? "" : "\n") line
  }
  END { flush() }
' $(find "$specs_dir" -name spec.md | sort) > "$work/reqs.bin" || fail "spec parsing failed"

spec_count=$(find "$specs_dir" -name spec.md | wc -l | tr -d ' ')

: > "$work/keys.tsv"
while IFS="$US" read -r -d "$RS" sid name text; do
  digest=$(printf '%s' "$text" | sha256sum)
  digest=${digest%% *}
  printf '%s#%s\t%s\t%s\t%s\n' "$sid" "$name" "$sid" "$name" "$digest" >> "$work/keys.tsv"
done < "$work/reqs.bin"
key_count=$(wc -l < "$work/keys.tsv" | tr -d ' ')

# key \t spec \t name \t digest  ->  { key: {spec,name,digest} }, written to a file: 929 keys is far too
# much to pass on a command line.
jq -Rs '
  split("\n") | map(select(length > 0)) | map(split("\t"))
  | map({key: .[0], spec: .[1], name: .[2], digest: .[3]})
  | reduce .[] as $k ({}; .[$k.key] = {spec: $k.spec, name: $k.name, digest: $k.digest})
' "$work/keys.tsv" > "$work/index.json" || fail "building the index failed"

# ---------------------------------------------------------------------------
# 2. Classification gate: every shipped spec id classified exactly once
# ---------------------------------------------------------------------------

shipped_ids=$(find "$specs_dir" -name spec.md | sed "s|^$specs_dir/||; s|/spec.md$||" | sort)
classified_ids=$(jq -r '.specs | keys[]' "$specs_file" | sort)

unclassified=$(comm -23 <(printf '%s\n' "$shipped_ids") <(printf '%s\n' "$classified_ids"))
stale=$(comm -13 <(printf '%s\n' "$shipped_ids") <(printf '%s\n' "$classified_ids"))

unclassified_n=$(printf '%s\n' "$unclassified" | grep -c . || true)
stale_n=$(printf '%s\n' "$stale" | grep -c . || true)

# area ids referenced by the classification must exist
bad_areas=$(jq -r --slurpfile a "$areas_file" \
  '[.specs[].area] | unique | .[] | select(. as $x | ($a[0].areas | map(.id) | index($x)) == null)' "$specs_file")
# surfaces outside the vocabulary — bind the surface first: inside `$vocab | index(.)` the `.` would be
# $vocab itself, so the comparison would test the vocabulary against itself and never fire.
vocab=$(jq -c '.surfaceVocabulary' "$areas_file")
bad_surfaces=$(jq -r --argjson vocab "$vocab" '
  .specs | to_entries[] | .key as $id | .value.surfaces[]?
  | . as $s | select(($vocab | index($s)) == null) | "\($id): \($s)"
' "$specs_file")
# a user-visible capability the catalogue can render no tag for
unrenderable=$(jq -r '.specs | to_entries[] | select(.value.userVisible and (.value.surfaces | length) == 0) | .key' "$specs_file")
caronly_n=$(jq '[.areas[] | select(.carOnly)] | length' "$areas_file")

gate_errors=0
if [ "$unclassified_n" -gt 0 ]; then
  gate_errors=1
  echo "gen-feature-list: unclassified shipped spec id(s):" >&2
  printf '%s\n' "$unclassified" | sed 's/^/  /' >&2
fi
if [ -n "$bad_areas" ]; then
  gate_errors=1
  echo "gen-feature-list: classification references unknown area id(s):" >&2
  printf '%s\n' "$bad_areas" | sed 's/^/  /' >&2
fi
if [ -n "$bad_surfaces" ]; then
  gate_errors=1
  echo "gen-feature-list: surface outside the vocabulary:" >&2
  printf '%s\n' "$bad_surfaces" | sed 's/^/  /' >&2
fi
if [ -n "$unrenderable" ]; then
  gate_errors=1
  echo "gen-feature-list: user-visible spec with no renderable surface:" >&2
  printf '%s\n' "$unrenderable" | sed 's/^/  /' >&2
fi
if [ "$caronly_n" -ne 1 ]; then
  gate_errors=1
  echo "gen-feature-list: expected exactly one car-only area, found $caronly_n" >&2
fi
if [ "$stale_n" -gt 0 ]; then
  echo "gen-feature-list: stale classification entry for a spec that no longer exists:" >&2
  printf '%s\n' "$stale" | sed 's/^/  /' >&2
fi

# A highlight may not name a spec the classification does not have, and must name at least one spec.
bad_highlights=$(jq -r -n --slurpfile h "$highlights_file" --slurpfile s "$specs_file" '
  ($s[0].specs) as $specs
  | $h[0].highlights[]
  | . as $hl
  | if ($hl.specs | length) == 0 then "\($hl.id): names no spec"
    else ($hl.specs[] | select($specs[.] == null) | "\($hl.id): unknown spec \(.)")
    end
' 2>/dev/null)
if [ -n "$bad_highlights" ]; then
  gate_errors=1
  echo "gen-feature-list: the shortlist is not usable:" >&2
  printf '%s\n' "$bad_highlights" | sed 's/^/  /' >&2
fi

if [ "$mode" = "check" ]; then
  echo "specs read: $spec_count"
  echo "classified: $(printf '%s\n' "$classified_ids" | grep -c . || true)"
  echo "unclassified: $unclassified_n"
  echo "stale: $stale_n"
  echo "car-only areas: $caronly_n"
  echo "highlights: $(jq '.highlights | length' "$highlights_file")"
  [ "$gate_errors" -eq 0 ] || fail "classification gate failed"
  exit 0
fi

[ "$gate_errors" -eq 0 ] || fail "classification gate failed; no document was written"

# ---------------------------------------------------------------------------
# 3. Prompt version, baseline snapshot and the delta
# ---------------------------------------------------------------------------

if [ -d "$prompts_dir" ]; then
  prompt_files=$(find "$prompts_dir" -type f | sort)
else
  prompt_files=""
fi
if [ -n "$prompt_files" ]; then
  prompt_digest=$(cat $prompt_files | sha256sum); prompt_digest=${prompt_digest%% *}
else
  prompt_digest=$(printf '' | sha256sum); prompt_digest=${prompt_digest%% *}
fi
prompt_n=$(printf '%s\n' "$prompt_files" | grep -c . || true)

baseline=""
baseline_file="none"
if [ -d "$snapshots_dir" ]; then
  # versionName is <yyyy>-<MM>-<dd>-<N> with an UNPADDED N, so lexicographic order is NOT chronological:
  # -1 < -10 < -2. The components are compared numerically instead, and the baseline is the greatest
  # snapshot version that is numerically below the requested one.
  versions=$(find "$snapshots_dir" -maxdepth 1 -name '*.json' -printf '%f\n' 2>/dev/null | sed 's/\.json$//')
  if [ -n "$versions" ]; then
    # Greatest version strictly less than the requested one, compared numerically component by component.
    # Sorting the list first means the last strict predecessor seen is that greatest version.
    baseline=$(printf '%s\n' $versions | sort -t- -k1,1n -k2,2n -k3,3n -k4,4n | awk -v r="$release" '
      BEGIN { split(r, R, "-") }
      {
        split($0, V, "-")
        for (i = 1; i <= 4; i++) {
          a = V[i] + 0; b = R[i] + 0
          if (a < b) { last = $0; break }
          if (a > b) break
        }
      }
      END { if (last != "") print last }
    ')
  fi
  [ -n "$baseline" ] && baseline_file="$snapshots_dir/$baseline.json"
fi

# Per-key classification of every shipped capability, joined for later steps
jq -n --slurpfile idx "$work/index.json" --slurpfile s "$specs_file" '
  $idx[0] | to_entries | map(
    .key as $k | .value as $v
    | ($s[0].specs[$v.spec] // {}) as $c
    | {key: $k, spec: $v.spec, name: $v.name, digest: $v.digest,
       text: null, area: ($c.area // null), userVisible: ($c.userVisible // null),
       surfaces: ($c.surfaces // null)}
  ) | map({(.key): .}) | add // {}
' > "$work/caps.json" || fail "joining the classification failed"

# --list: what the index holds, per capability, so the classification is readable without the document.
if [ "$listing" -eq 1 ]; then
  jq -r 'to_entries | sort_by(.value.area, .value.spec, .value.name)
         | .[] | "\(.value.area)\t\(.value.spec)\t\(if .value.userVisible then "visible" else "internal" end)\t\(.value.surfaces | join(","))\t\(.key)"' \
    "$work/caps.json"
  exit 0
fi

# delta: added / changed / removed / unchanged, comparing requirement-text digests
if [ "$baseline_file" != "none" ]; then
  delta=$(jq -n --slurpfile cur "$work/caps.json" --slurpfile base "$baseline_file" '
    ($cur[0]) as $c | ($base[0].keys // {}) as $b
    | {
        added:    [ $c | keys[] | select($b[.] == null) ],
        removed:  [ $b | keys[] | select($c[.] == null) ],
        changed:  [ $c | keys[] | select($b[.] != null and $b[.].digest != $c[.].digest) ],
        unchanged:[ $c | keys[] | select($b[.] != null and $b[.].digest == $c[.].digest) ]
      }
  ')
else
  delta=$(jq -n --slurpfile cur "$work/caps.json" '
    ($cur[0]) as $c | { added: [$c | keys[]], removed: [], changed: [], unchanged: [] }
  ')
fi

added_n=$(jq '.added | length' <<<"$delta")
changed_n=$(jq '.changed | length' <<<"$delta")
removed_n=$(jq '.removed | length' <<<"$delta")
unchanged_n=$(jq '.unchanged | length' <<<"$delta")

# ---------------------------------------------------------------------------
# 4. Dirty feature areas: an area is dirty unless its cache key still holds
# ---------------------------------------------------------------------------

# The cache key covers the area's definition, its member set, each member's classification entry, each
# member capability's text digest, and the prompt version — never the specs' own bytes, so a prose-only
# edit to a member spec does not dirty its area.
mkdir -p "$cache_dir" 2>/dev/null
: > "$work/areakeys.tsv"
area_count=0
while read -r area_id; do
  [ -n "$area_id" ] || continue
  area_count=$((area_count + 1))
  area_def=$(jq -c --arg id "$area_id" '.areas[] | select(.id == $id)' "$areas_file")
  members=$(jq -r --arg id "$area_id" --slurpfile s "$specs_file" '
    $s[0].specs | to_entries[] | select(.value.area == $id) | .key' "$specs_file" | sort)
  canonical=$(printf '%s\n%s\n%s\n' "$area_def" "$prompt_digest" "$members" | sha256sum)
  canonical=${canonical%% *}
  member_text=$(jq -r --arg id "$area_id" '
    to_entries | map(select(.value.area == $id)) | sort_by(.key)
    | .[] | "\(.key)\t\(.value.userVisible)\t\(.value.surfaces|join(","))\t\(.value.digest)"' \
    "$work/caps.json")
  cache_key=$(printf '%s\n%s\n' "$canonical" "$member_text" | sha256sum)
  cache_key=${cache_key%% *}
  printf '%s\t%s\n' "$area_id" "$cache_key" >> "$work/areakeys.tsv"
done < <(jq -r '.areas | sort_by(.order) | .[].id' "$areas_file")

jq -Rs 'split("\n") | map(select(length > 0)) | map(split("\t"))
        | reduce .[] as $r ({}; .[$r[0]] = $r[1])' "$work/areakeys.tsv" > "$work/areakeys.json" \
  || fail "computing area cache keys failed"

# An area is dirty when its cache key differs from the one its baseline recorded. The key never covers a
# spec's own bytes, so a prose-only edit to a member spec does not dirty its area.
if [ "$baseline_file" != "none" ]; then
  dirty_areas=$(jq -r -n --slurpfile cur "$work/areakeys.json" --slurpfile base "$baseline_file" '
    ($cur[0]) as $c | ($base[0].areas // {}) as $b
    | $c | keys[] | select($b[.] != $c[.])
  ')
else
  dirty_areas=$(jq -r -n --slurpfile cur "$work/areakeys.json" '$cur[0] | keys[]')
fi
dirty_n=$(printf '%s\n' "$dirty_areas" | grep -c . || true)

# ---------------------------------------------------------------------------
# 5. Report
# ---------------------------------------------------------------------------

echo "version: $release"
echo "specs read: $spec_count"
echo "capability keys: $key_count"
echo "added: $added_n"
echo "changed: $changed_n"
echo "removed: $removed_n"
echo "unchanged: $unchanged_n"
echo "dirty areas: $dirty_n"
echo "areas: $area_count"
echo "unclassified spec ids: $unclassified_n"
echo "stale classification entries: $stale_n"
echo "prompt files: $prompt_n"
echo "baseline: $baseline_file"

if [ "$write" -eq 0 ]; then
  echo "documents: not written (--report-only)"
  exit 0
fi

if [ "$(jq '.highlights | length' "$highlights_file")" -eq 0 ]; then
  echo "verdict: the shortlist names no selling point, so there is nothing to publish"
  exit 0
fi

# ---------------------------------------------------------------------------
# 5. Fill the phrasing seam for each area that needs prose: a sections directory a harness wrote, a
#    phrasing command, or mechanical assembly from the capabilities' own names.
# ---------------------------------------------------------------------------

# key -> requirement text, for the bundles a harness phrases
jq -Rs 'split("\u001e") | map(select(length > 0)) | map(split("\u001f"))
        | map({key: "\(.[0])#\(.[1])", text: .[2]})
        | reduce .[] as $r ({}; .[$r.key] = $r.text)' "$work/reqs.bin" > "$work/texts.json" \
  || fail "building the capability texts failed"

# spec id <TAB> <1|0 user-visible> <TAB> the spec's collapsed text — the gate's evidence for a bullet's
# figures and its words. One line per spec, so the gate can be a single awk pass over this file then the doc.
awk -v prefix="$specs_dir/" '
  FNR == 1 {
    if (sid != "") print sid "\t" txt
    sid = FILENAME; sub("^" prefix, "", sid); sub("/spec.md$", "", sid); txt = ""
  }
  { line = $0; gsub(/\t/, " ", line); txt = txt (txt == "" ? "" : " ") line }
  END { if (sid != "") print sid "\t" txt }
' $(find "$specs_dir" -name spec.md | sort) > "$work/spec-text.raw.tsv" || fail "reading the spec texts failed"

jq -Rrs --slurpfile s "$specs_file" '
  split("\n") | map(select(length > 0)) | map(split("\t"))
  | map({spec: .[0], text: .[1]})
  | map(. + {vis: ($s[0].specs[.spec].userVisible // false)})
  | .[] | "\(.spec)\t\(if .vis then "1" else "0" end)\t\(.text)"
' "$work/spec-text.raw.tsv" > "$work/spec-text.tsv" || fail "joining the spec texts failed"

# The area's surfaces: the union over the capabilities its highlights publish, taken from the classification.
area_surfaces() {
  jq -r --arg id "$1" --slurpfile h "$highlights_file" --slurpfile s "$specs_file" '
    ($s[0].specs) as $specs
    | [ . as $caps
        | $h[0].highlights[] | select(.area == $id) | .specs[] as $sp
        | $caps | to_entries[] | select(.value.spec == $sp and .value.userVisible) | .value.surfaces[] ]
    | unique | sort | join(",")
  ' "$work/caps.json"
}

# ``Phone`` / ``Car`` / ``Phone and car``
surface_label() {
  case "$1" in
    phone) echo "Phone" ;;
    car) echo "Car" ;;
    phone,car) echo "Phone and car" ;;
    *) echo "Phone and car" ;;
  esac
}

visible_in_area() {
  jq -r --arg id "$1" '[to_entries[] | select(.value.area == $id and .value.userVisible)] | length' "$work/caps.json"
}

# One bundle: the area, its prompt, and the highlights of that area with the capabilities each must cover.
write_bundle() {
  jq -n --arg id "$1" --arg pd "$prompt_digest" --arg pf "$prompts_dir/area-section.md" \
        --slurpfile a "$areas_file" --slurpfile t "$work/texts.json" --slurpfile c "$work/caps.json" \
        --slurpfile h "$highlights_file" --slurpfile s "$specs_file" '
    ($c[0]) as $caps | ($s[0].specs) as $specs
    | ($a[0].areas[] | select(.id == $id)) as $area
    | {area: ($area + {surfaces: ([$h[0].highlights[] | select(.area == $id) | .specs[] as $sp
                                | $caps | to_entries[] | select(.value.spec == $sp and .value.userVisible)
                                | .value.surfaces[]] | unique | sort)}),
       promptFile: $pf, promptVersion: $pd,
       highlights: ([$h[0].highlights[] | select(.area == $id)
                     | {id, keyword, why,
                        capabilities: ([.specs[] as $sp
                                        | $caps | to_entries[] | select(.value.spec == $sp and .value.userVisible)
                                        | {key: .key, spec: .value.spec, name: .value.name,
                                           surfaces: .value.surfaces, text: ($t[0][.key] // "")}]
                                       | sort_by(.spec, .name))}])}
  ' > "$2" || fail "writing the bundle for area '$1' failed"
}

# Does this area have a selling point at all? An area with no highlight is not rendered.
highlights_in_area() {
  jq -r --arg id "$1" '[.highlights[] | select(.area == $id)] | length' "$highlights_file"
}

# The degraded mode: no model, one entry per highlight — every capability of the highlight in one paragraph,
# truncated at the length bound, led by a keyword taken from the first capability's own name. The entry still
# carries its keys and still leads with a keyword, so the gates below hold in this mode too.
render_mechanical() {
  jq -r --arg id "$1" --argjson max "$maxpara" --slurpfile h "$highlights_file" --slurpfile s "$specs_file" '
    def tokens: gsub("[^A-Za-z0-9]+"; " ") | split(" ") | map(select(. != ""));
    def clip($n): if length <= $n then . else (.[0:$n] | rindex(" ") // ($n - 1)) as $c | .[0:$c] end;
    def caps($specs): [ to_entries[] | . as $e | select($specs | index($e.value.spec)) | select($e.value.userVisible) ];
    . as $caps
    | ($s[0].specs) as $specs
    | $h[0].highlights[] | select(.area == $id) | . as $hl
    | ($caps | caps($hl.specs) | sort_by(.value.spec, .value.name)) as $sel
    | select(($sel | length) > 0)
    | ($sel[0].value.name | tokens | .[0:6] | join(" ")) as $kw
    | "- **\($kw)** — " + (($sel | map(.value.name) | join("; ")) | clip($max)) + ".\n"
      + ($sel | map("  <!-- cap: \(.key) -->") | join("\n"))
  ' "$work/caps.json"
}

# Areas that must be rendered: dirty against the baseline, or whose cached section is missing or carries a
# stale key header. The second is a repair — without it a deleted cache would silently drop an area.
needs_render() {
  area_id="$1"
  cache_key=$(jq -r --arg id "$area_id" '.[$id]' "$work/areakeys.json")
  area_file="$cache_dir/$area_id.md"
  if [ -f "$area_file" ]; then
    stored=$(head -1 "$area_file" | sed -n 's/.*cachekey: \([a-f0-9]\{64\}\) .*/\1/p')
    [ "$stored" = "$cache_key" ] || return 0
  else
    return 0
  fi
  printf '%s\n' "$dirty_areas" | grep -qx "$area_id" && return 0
  return 1
}

# Selection for a release note: which capabilities moved, and how a vanished key is accounted for. Writes
# `$work/notes.tsv` — one line per capability to report, `<area order> \t <area id> \t <kind> \t <key>` — plus
# `$work/pairs.tsv` and the counters. Model-free: it is arithmetic over the delta and the classification.
select_notes() {
  changed_keys=$(jq -r -n --slurpfile c "$work/caps.json" \
    --argjson moved "$(jq -c '(.added + .changed) | unique' <<<"$delta")" '
      ($c[0]) as $c | $moved[] | select($c[.].userVisible) | .
    ' 2>/dev/null | sort -u)

  renames_n=0
  removals_n=0
  removed_specs_n=0
  : > "$work/notes.tsv"
  : > "$work/removed.tsv"
  : > "$work/added.tsv"

  if [ "$baseline_file" != "none" ]; then
    jq -r -n --slurpfile base "$baseline_file" --slurpfile c "$work/caps.json" '
      ($base[0].keys) as $b | ($c[0]) as $cur
      | $b | keys[] | select($cur[.] == null)
      | "\(split("#")[0])\t\(sub("^[^#]*#"; ""))"
    ' > "$work/removed.tsv"
    jq -r -n --argjson added "$(jq -c '.added' <<<"$delta")" \
      '$added[] | "\(split("#")[0])\t\(sub("^[^#]*#"; ""))"' > "$work/added.tsv"
    awk -F'\t' -f "$script_dir/feature-list/pair.awk" "$work/removed.tsv" "$work/added.tsv" > "$work/pairs.tsv"

    while IFS=$'\t' read -r old_key new_key; do
      [ -n "$old_key" ] || continue
      old_spec=${old_key%%#*}
      if [ "$new_key" != "-" ]; then
        # a requirement renamed inside a spec that still exists is a change to that capability
        renames_n=$((renames_n + 1))
        echo "gen-feature-list: renamed, reported as a change: $old_key -> $new_key"
        continue
      fi
      if printf '%s\n' "$shipped_ids" | grep -qx "$old_spec"; then
        if [ "$(jq -r --arg s "$old_spec" '.specs[$s].userVisible' "$specs_file")" = "true" ]; then
          removals_n=$((removals_n + 1))
          note_area=$(jq -r --arg s "$old_spec" '.specs[$s].area' "$specs_file")
          printf '%s\t%s\tremoved\t%s\n' \
            "$(jq -r --arg id "$note_area" '.areas[] | select(.id == $id) | .order' "$areas_file")" \
            "$note_area" "$old_key" >> "$work/notes.tsv"
        fi
      else
        removed_specs_n=$((removed_specs_n + 1))
        echo "gen-feature-list: spec left the shipped set, reported and not published as a change: $old_spec"
      fi
    done < "$work/pairs.tsv"
  fi

  # One key per line, read as a line: a requirement name contains spaces, so `for k in $changed_keys` would
  # iterate over words instead of keys.
  while IFS= read -r k; do
    [ -n "$k" ] || continue
    note_area=$(jq -r --arg k "$k" '$k as $key | .[$key].area' "$work/caps.json")
    printf '%s\t%s\tchanged\t%s\n' \
      "$(jq -r --arg id "$note_area" '.areas[] | select(.id == $id) | .order' "$areas_file")" \
      "$note_area" "$k" >> "$work/notes.tsv"
  done <<< "$changed_keys"

  changed_n_note=$(cut -f3 "$work/notes.tsv" | grep -c '^changed$' || true)
  sort -n -o "$work/notes.tsv" "$work/notes.tsv"

  # The keys that have left the shipped specs, for the notes gate: an entry about one of them cannot be checked
  # against the current specs, because they no longer contain it.
  : > "$work/vanished.txt"
  [ -s "$work/removed.tsv" ] && awk -F'\t' '{ print $1 "#" $2 }' "$work/removed.tsv" >> "$work/vanished.txt"
}

if [ -n "$bundles_dir" ]; then
  mkdir -p "$bundles_dir" || fail "cannot create $bundles_dir"
  bundles_n=0
  while read -r area_id; do
    [ -n "$area_id" ] || continue
    [ "$(highlights_in_area "$area_id")" -gt 0 ] || continue
    needs_render "$area_id" || continue
    write_bundle "$area_id" "$bundles_dir/$area_id.json"
    echo "$area_id"
    bundles_n=$((bundles_n + 1))
  done < <(jq -r '.areas | sort_by(.order) | .[].id' "$areas_file")
  echo "bundles written: $bundles_n"

  # The release note needs its own bundles: one per area that has a capability to report, holding only that
  # area's changed capabilities. A harness writes <area id>.notes.md next to <area id>.md.
  select_notes
  notes_bundles_n=0
  for area_id in $(cut -f2 "$work/notes.tsv" | sort -u); do
    [ -n "$area_id" ] || continue
    keys=$(awk -F'\t' -v a="$area_id" '$2 == a && $3 == "changed" { printf "%s\n", $4 }' "$work/notes.tsv")
    removals=$(awk -F'\t' -v a="$area_id" '$2 == a && $3 == "removed" { printf "%s\n", $4 }' "$work/notes.tsv")
    jq -n --arg id "$area_id" --arg v "$release" --arg pf "$prompts_dir/notes-section.md" \
          --argjson keys "$(printf '%s\n' "$keys" | jq -R -s 'split("\n")|map(select(length>0))')" \
          --argjson removed "$(printf '%s\n' "$removals" | jq -R -s 'split("\n")|map(select(length>0))')" \
          --slurpfile a "$areas_file" --slurpfile t "$work/texts.json" --slurpfile c "$work/caps.json" '
      ($c[0]) as $caps | ($a[0].areas[] | select(.id == $id)) as $area
      | {role: "release-note", version: $v, area: $area, promptFile: $pf,
         capabilities: ([$keys[] | {key: ., spec: $caps[.].spec, name: $caps[.].name, text: ($t[0][.] // "")}]),
         removed: ([$removed[] | {key: ., spec: (. | split("#")[0]), name: (. | sub("^[^#]*#"; ""))}])}
    ' > "$bundles_dir/$area_id.notes.json" || fail "writing the note bundle for area '$area_id' failed"
    echo "$area_id.notes.json"
    notes_bundles_n=$((notes_bundles_n + 1))
  done
  echo "note bundles written: $notes_bundles_n"
  exit 0
fi

render_area() {
  area_id="$1"
  if ! needs_render "$area_id"; then
    tail -n +2 "$cache_dir/$area_id.md" > "$work/section-$area_id.md"
    return 0
  fi
  if [ -n "$sections_dir" ]; then
    [ -f "$sections_dir/$area_id.md" ] \
      || fail "no section for area '$area_id' in $sections_dir — run --emit-bundles and fill it"
    cp "$sections_dir/$area_id.md" "$work/section-$area_id.md"
  elif [ -n "$phrase_cmd" ]; then
    bundle="$work/bundle-$area_id.json"
    write_bundle "$area_id" "$bundle"
    $phrase_cmd < "$bundle" > "$work/section-$area_id.md" \
      || fail "the phrasing command failed for area '$area_id'"
  else
    render_mechanical "$area_id" > "$work/section-$area_id.md"
  fi
  { printf '<!-- cachekey: %s area: %s -->\n' \
      "$(jq -r --arg id "$area_id" '.[$id]' "$work/areakeys.json")" "$area_id"
    cat "$work/section-$area_id.md"; } > "$cache_dir/$area_id.md"
}

mkdir -p "$cache_dir" || fail "cannot create $cache_dir"
catalogue="$work/FEATURES.md"
{
  printf '# NaviVeylin — Features\n\n'
  while read -r area_id; do
    [ -n "$area_id" ] || continue
    [ "$(highlights_in_area "$area_id")" -gt 0 ] || continue
    headings=$(jq -r --arg id "$area_id" '.areas[] | select(.id == $id) | .heading' "$areas_file")
    render_area "$area_id"
    printf '## %s\n\n_%s_\n\n' "$headings" "$(surface_label "$(area_surfaces "$area_id")")"
    cat "$work/section-$area_id.md"
    printf '\n'
  done < <(jq -r '.areas | sort_by(.order) | .[].id' "$areas_file")
} > "$catalogue" || fail "assembling the catalogue failed"

# ---------------------------------------------------------------------------
# 6. The prose gates. A failing gate writes nothing, so a bad run cannot advance the baseline.
# ---------------------------------------------------------------------------

gate_failed=0

# Per-bullet gates: the marker resolves and names a user-visible spec, each figure appears in a cited spec,
# and every content word that appears in none of them is reported for review without failing the run.
# The gate itself is tools/feature-list/gate.awk, so it can be read and run on its own.
awk -F'\t' -f "$script_dir/feature-list/gate.awk" -v keyfile="$work/cited.txt" \
  -v entryspecfile="$work/entryspec.tsv" -v maxpara="$maxpara" \
  -v vanishedfile="$work/vanished.txt" \
  "$work/spec-text.tsv" "$catalogue" > "$work/gate.out" 2> "$work/gate.err"
gate_rc=$?
if [ -s "$work/gate.out" ]; then
  gate_failed=1
  echo "gen-feature-list: the prose gates failed:" >&2
  sed 's/^/  /' "$work/gate.out" >&2
fi
if [ -s "$work/gate.err" ]; then
  echo "gen-feature-list: prose worth a reviewer's eye (not a failure):"
  sed 's/^/  /' "$work/gate.err"
fi
[ "$gate_rc" -eq 0 ] || gate_failed=1

: > "$work/cited.sorted"
[ -f "$work/cited.txt" ] && sort -u "$work/cited.txt" > "$work/cited.sorted"

# The shortlist checks, over the specs each entry cited (gate.awk wrote one line per entry):
#   * every cited spec is named by a highlight — the catalogue publishes nothing the shortlist did not choose;
#   * every highlight is reached by at least one entry;
#   * one entry is one selling point: a single highlight must contain all of an entry's specs.
if [ -s "$work/entryspec.tsv" ]; then
  printf '%s\n' "$shipped_ids" > "$work/shipped.txt"
  shortlist_out=$(jq -r -n --slurpfile h "$highlights_file" --rawfile es "$work/entryspec.tsv" \
    --rawfile shipped "$work/shipped.txt" '
    ($h[0].highlights) as $hl
    | ($hl | map({key: .id, value: .specs}) | from_entries) as $of
    | ($shipped | split("\n") | map(select(length > 0))) as $live
    # a highlight whose every spec has left the shipped set is reported and not required, the same way a stale
    # classification entry is: the departure is an event, not a mistake in the shortlist
    | ([ $hl[] | select(([ .specs[] as $s | $live | index($s) ] | any) | not) | "stale shortlist entry, not required: \(.id)" ]) as $stale
    | ([ $hl[] | select(([ .specs[] as $s | $live | index($s) ] | any)) ]) as $required
    | ($es | split("\n") | map(select(length > 0)) | map(split("\t"))
       | map({line: (.[0] | tonumber), specs: (.[1] | split(" "))})) as $entries
    | [ $entries[]
        | . as $e
        | ([ $hl[] | select(([ .specs[] as $hs | $e.specs | index($hs) ] | any)) | .id ] | unique) as $owners
        | if ($owners | length) == 0
          then empty
          else ([ $owners[] as $o | select([ $e.specs[] | . as $s | $of[$o] | index($s) ] | all) ] | first) as $single
               | if $single == null
                 then "entry at line \($e.line) spans more than one selling point: \($owners | join(", "))"
                 else empty end
          end ] as $entry_problems
    | ([ $entries[].specs[] ] | unique) as $cited
    | ([ $required[] | select(([ .specs[] | . as $s | $cited | index($s) ] | any) | not) | "selling point not reached: \(.id)" ]) as $unreached
    | [ $entries[]
        | . as $e
        | ([ $e.specs[] | . as $s | select(([ $hl[] | select(.specs | index($s)) ] | length) == 0) ])
        | select(length > 0)
        | "entry at line \($e.line) cites specs the shortlist does not name: \(join(", "))" ] as $unlisted
    | ($stale + $entry_problems + $unlisted + $unreached) | .[]
  ' 2>/dev/null)
  # A stale shortlist entry is reported, not a failure — the same rule a stale classification entry follows. The
  # jq tags it, so the shell can tell the two apart without a second pass.
  stale_shortlist=$(printf '%s\n' "$shortlist_out" | grep '^stale shortlist entry' || true)
  if [ -n "$stale_shortlist" ]; then
    echo "gen-feature-list: $(printf '%s\n' "$stale_shortlist" | sed 's/^stale //' | tr '\n' ' ')" >&2
  fi
  shortlist_out=$(printf '%s\n' "$shortlist_out" | grep -v '^stale shortlist entry' | grep . || true)
  if [ -n "$shortlist_out" ]; then
    gate_failed=1
    echo "gen-feature-list: the shortlist is not honoured:" >&2
    printf '%s\n' "$shortlist_out" | sed 's/^/  /' >&2
  fi
fi

[ "$gate_failed" -eq 0 ] || fail "the catalogue was not written; fix the report above and run again"

cp "$catalogue" "$out_dir/FEATURES.md.tmp" && mv "$out_dir/FEATURES.md.tmp" "$out_dir/FEATURES.md" \
  || fail "cannot write $out_dir/FEATURES.md"
entry_n=$(grep -c '^- ' "$catalogue" || true)
hl_n=$(jq '.highlights | length' "$highlights_file")
# An empty catalogue must not pass quietly: with no entry, no other check has anything to look at.
[ "$entry_n" -gt 0 ] || fail "the catalogue has no entry but the shortlist names $hl_n selling point(s)"
# The average is formatted by awk and passed as a string: the shell's printf would parse "8.1" as an invalid
# number under a locale whose decimal separator is a comma. The keyword is excluded, as the length bound does.
avg=$(awk -v n="$entry_n" '/^- / { k = index($0, "**"); rest = substr($0, k + 2); c += length(substr(rest, index(rest, "**") + 2)) } END { if (n > 0) printf "%d", c / n; else printf "0" }' "$catalogue")
printf 'catalogue: %s (%s entries for %s selling points, %s areas, about %s chars per entry)\n' \
  "$out_dir/FEATURES.md" "$entry_n" "$hl_n" "$(grep -c '^## ' "$catalogue")" "$avg"

# ---------------------------------------------------------------------------
# 7. The release note for this version: the capabilities that moved, grouped by area, replaced in place
# ---------------------------------------------------------------------------

# Selection is arithmetic over the delta, never a model's judgement: added and changed user-visible capabilities,
# plus a removal when the spec that lost the requirement still exists and is user-visible.
select_notes

if [ "$baseline_file" = "none" ]; then
  echo "note: none owed for $release; the version established the baseline"
elif [ ! -s "$work/notes.tsv" ]; then
  echo "note: none for $release; nothing user-visible changed ($renames_n renames, $removed_specs_n keys whose spec left the shipped set)"
  echo "verdict: nothing user-visible changed in $release"
else
  changed_n_note=$(grep -c 'changed$' <(cut -f3 "$work/notes.tsv") || true)
  echo "note: $release carries $changed_n_note changed and $removals_n removed user-visible capabilities ($renames_n renames)"

  entry="$work/entry.md"
  printf '## %s\n' "$release" > "$entry"
  current_area=""
  while IFS=$'\t' read -r _order area kind key; do
    [ -n "$area" ] || continue
    if [ "$area" != "$current_area" ]; then
      printf '\n### %s\n\n' "$(jq -r --arg id "$area" '.areas[] | select(.id == $id) | .heading' "$areas_file")" >> "$entry"
      current_area="$area"
      if [ -n "$sections_dir" ] && [ -f "$sections_dir/$area.notes.md" ]; then
        cat "$sections_dir/$area.notes.md" >> "$entry"
      fi
    fi
    if [ -n "$sections_dir" ] && [ -f "$sections_dir/$area.notes.md" ]; then
      continue
    fi
    if [ "$kind" = "removed" ]; then
      printf -- '- **%s** — no longer available.\n  <!-- cap: %s -->\n' "${key#*#}" "$key" >> "$entry"
    elif [ -n "$phrase_cmd" ]; then
      jq -n --arg id "$area" --arg v "$release" --arg pf "$prompts_dir/notes-section.md" \
            --slurpfile a "$areas_file" --slurpfile t "$work/texts.json" --slurpfile c "$work/caps.json" \
            --arg k "$key" '
        ($c[0]) as $caps | ($a[0].areas[] | select(.id == $id)) as $area
        | {role: "release-note", version: $v, area: $area, promptFile: $pf,
           capabilities: [{key: $k, spec: $caps[$k].spec, name: $caps[$k].name,
                           text: ($t[0][$k] // "")}]}
      ' > "$work/notes-bundle.json"
      GEN_FEATURE_LIST_ROLE=notes $phrase_cmd < "$work/notes-bundle.json" >> "$entry" \
        || fail "the phrasing command failed for the release note of area '$area'"
    else
      printf -- '- **%s** — %s.\n  <!-- cap: %s -->\n' "$(jq -r --arg k "$key" '$k as $key | .[$key].name' "$work/caps.json")" \
        "$(jq -r --arg k "$key" '$k as $key | .[$key].name' "$work/caps.json")" "$key" >> "$entry"
    fi
  done < <(sort -n "$work/notes.tsv")

  # Only user-visible capabilities may appear, so the entry passes the same gates as the catalogue: every
  # bullet carries its key, every key resolves, and every key names a spec classified user-visible.
  awk -F'\t' -f "$script_dir/feature-list/gate.awk" -v keyfile="$work/entry-cited.txt" \
    -v maxpara="$maxpara" -v vanishedfile="$work/vanished.txt" \
    "$work/spec-text.tsv" "$entry" > "$work/entry.gate.out" 2> "$work/entry.gate.err"
  entry_rc=$?
  if [ -s "$work/entry.gate.out" ]; then
    echo "gen-feature-list: the release-note gates failed:" >&2
    sed 's/^/  /' "$work/entry.gate.out" >&2
  fi
  [ -s "$work/entry.gate.err" ] && { echo "gen-feature-list: note prose worth a reviewer's eye:"; sed 's/^/  /' "$work/entry.gate.err"; }
  [ "$entry_rc" -eq 0 ] || fail "the release note was not written; fix the report above and run again"

  # Derived, not accumulated: this version's entry is replaced, every other entry is untouched, and the
  # entries stay ordered by version, newest first.
  mkdir -p "$work/rel"
  if [ -f "$out_dir/RELEASE-NOTES.md" ]; then
    awk -v dir="$work/rel" '
      /^## / {
        if (f != "") close(f)
        v = substr($0, 4); gsub(/[^A-Za-z0-9.-]/, "", v)
        f = dir "/" v ".md"
      }
      f != "" { print >> f }
    ' "$out_dir/RELEASE-NOTES.md"
  fi
  cp "$entry" "$work/rel/$release.md"
  {
    printf '# NaviVeylin — Release Notes\n\n'
    for v in $(find "$work/rel" -name '*.md' -printf '%f\n' | sed 's/\.md$//' \
               | sort -t- -k1,1nr -k2,2nr -k3,3nr -k4,4nr); do
      cat "$work/rel/$v.md"
      printf '\n'
    done
  } > "$work/RELEASE-NOTES.md"
  cp "$work/RELEASE-NOTES.md" "$out_dir/RELEASE-NOTES.md.tmp" \
    && mv "$out_dir/RELEASE-NOTES.md.tmp" "$out_dir/RELEASE-NOTES.md" \
    || fail "cannot write $out_dir/RELEASE-NOTES.md"
  entries=$(grep -c '^## ' "$out_dir/RELEASE-NOTES.md" || true)
  echo "release notes: $out_dir/RELEASE-NOTES.md ($entries entries, newest $release)"
fi

# ---------------------------------------------------------------------------
# 8. Snapshot: what a later run needs to compare, keyed by this version
# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------

jq -n --arg v "$release" --arg pd "$prompt_digest" \
      --slurpfile idx "$work/index.json" --slurpfile ak "$work/areakeys.json" '
  {version: $v, promptDigest: $pd,
   keys: ($idx[0] | with_entries(.value = {spec: .value.spec, digest: .value.digest})),
   areas: $ak[0]}
' > "$work/snapshot.json" || fail "building the snapshot failed"

mkdir -p "$snapshots_dir" || fail "cannot create $snapshots_dir"
cp "$work/snapshot.json" "$snapshots_dir/$release.json.tmp" \
  && mv "$snapshots_dir/$release.json.tmp" "$snapshots_dir/$release.json" \
  || fail "cannot write $snapshots_dir/$release.json"
echo "snapshot: $snapshots_dir/$release.json"

if [ "$baseline_file" = "none" ]; then
  echo "verdict: baseline established for $release; no release note is owed for a version with no baseline"
fi

exit 0
