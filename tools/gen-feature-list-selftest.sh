#!/usr/bin/env bash
# Self-test for tools/gen-feature-list.sh.
#
# Builds its own fixture tree in a scratch directory — synthetic specs, a classification, prompt stubs and a
# stub phrasing command — and drives the tool as a subprocess. It reads no project document and needs no build,
# no device, no network and no language model, as tools/check-doc-routes-selftest.sh does.
#
# Usage: gen-feature-list-selftest.sh [--verbose]
# Exit 0 when every case passes, 1 when one fails.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
tool="$here/gen-feature-list.sh"

verbose=0
[ "${1:-}" = "--verbose" ] && verbose=1

work=$(mktemp -d) || { echo "gen-feature-list-selftest: cannot create a scratch directory" >&2; exit 1; }
trap 'rm -rf "$work"' EXIT

pass=0
fail=0

ok()  { pass=$((pass + 1)); printf 'ok   %s\n' "$1"; }
bad() {
  fail=$((fail + 1))
  printf 'FAIL %s\n' "$1"
  [ -n "${2:-}" ] && printf '     %s\n' "$2"
  if [ -n "${last_log:-}" ] && [ -f "$last_log" ]; then
    printf '     last run: %s\n' "$(printf '%s' "$last_cmd")"
    tail -4 "$last_log" | sed 's/^/       /'
  fi
}

# is <name> <expected> <actual>
is() {
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "expected [$2], got [$3]"; fi
}
# rc <name> <expected exit code> <command...>
rc() {
  local name="$1" want="$2" rcout rcg
  shift 2
  rcout=$("$@" 2>&1); rcg=$?
  if [ "$rcg" = "$want" ]; then ok "$name"; else bad "$name" "expected exit $want, got $rcg: $(printf '%s' "$rcout" | tail -3)"; fi
}

# ---------------------------------------------------------------------------
# The fixture tree
# ---------------------------------------------------------------------------

spec() {  # spec <id> <requirement name> [<requirement name> ...]
  id="$1"; shift
  mkdir -p "$repo/openspec/specs/$id"
  {
    printf '# %s Specification\n\n## Purpose\n\nFixture spec %s, used by the self-test.\n\n## Requirements\n\n' "$id" "$id"
    for r in "$@"; do
      printf '### Requirement: %s\n\n%s SHALL do the thing its name says.\n\n#### Scenario: %s holds\n\n- **WHEN** the fixture says so\n- **THEN** %s is %s in 2 frames\n\n' \
        "$r" "$r" "$r" "$r" "$r"
    done
  } > "$repo/openspec/specs/$id/spec.md"
}

areas_() {
  cat > "$data/areas.json" <<'JSON'
{
  "surfaceVocabulary": ["phone", "car"],
  "areas": [
    { "id": "one",   "heading": "Area One",   "order": 10, "carOnly": false },
    { "id": "two",   "heading": "Area Two",   "order": 20, "carOnly": false },
    { "id": "three", "heading": "Area Three", "order": 30, "carOnly": false },
    { "id": "car",   "heading": "In The Car", "order": 40, "carOnly": true }
  ]
}
JSON
}

specs_() {
  cat > "$data/specs.json" <<'JSON'
{
  "specs": {
    "alpha": { "area": "one",   "userVisible": true,  "surfaces": ["phone"] },
    "beta":  { "area": "two",   "userVisible": true,  "surfaces": ["phone", "car"] },
    "delta": { "area": "one",   "userVisible": true,  "surfaces": ["car"] },
    "epsilon": { "area": "car", "userVisible": true,  "surfaces": ["car"] },
    "gamma": { "area": "three", "userVisible": false, "surfaces": [] },
    "probe/nested": { "area": "three", "userVisible": false, "surfaces": [], "note": "a nested spec id, internal" }
  }
}
JSON
}

# The shortlist: `delta` is deliberately absent, so the catalogue must not publish it however user-visible it is.
highlights_() {
  cat > "$data/highlights.json" <<'JSON'
{
  "highlights": [
    { "id": "alpha-selling-point", "area": "one", "keyword": "Alpha capability", "specs": ["alpha"] },
    { "id": "beta-selling-point",  "area": "two", "keyword": "Beta capability",  "specs": ["beta"] },
    { "id": "car-selling-point",   "area": "car", "keyword": "Epsilon car platform capability", "specs": ["epsilon"] }
  ]
}
JSON
}

build() {
  repo="$work/repo"; data="$work/data"; out="$work/out"
  rm -rf "$work/repo" "$work/data" "$work/out"
  mkdir -p "$repo/openspec/specs" "$data/prompts" "$out"
  spec alpha "Alpha first capability" "Alpha second capability" "Alpha third capability"
  spec beta "Beta sole capability" "Beta paired capability"
  spec delta "Delta car capability"
  spec epsilon "Epsilon car platform capability"
  spec gamma "Gamma internal capability"
  spec probe/nested "Nested internal capability"
  areas_; specs_; highlights_
  printf 'Compose a section. One key per marker line.\n' > "$data/prompts/area-section.md"
  printf 'Compose a release note.\n' > "$data/prompts/notes-section.md"
}

run() {  # run the tool against the fixture, keeping its output for a failure's diagnostics
  last_cmd="run $*"
  last_log="$work/last-run.log"
  bash "$tool" --root "$repo" --data-dir "$data" --out-dir "$out" "$@" > "$last_log" 2>&1
  local rc=$?
  cat "$last_log"
  return $rc
}

# run_split [args...] — stdout in $work/g.out, stderr in $work/g.err, exit code in $last_rc.
# The tool reports a gate failure on stderr and an advisory (`prose worth a reviewer's eye`, and the unsourced
# words under it) on stdout, so the two streams have to be asserted separately.
run_split() {
  last_cmd="run $*"
  last_log="$work/g.out"
  bash "$tool" --root "$repo" --data-dir "$data" --out-dir "$out" "$@" > "$work/g.out" 2> "$work/g.err"
  last_rc=$?
}

# section <file> <heading> — the text of one `## ` section, printed to stdout
section() {
  awk -v want="## $2" '$0 == want {inside = 1; next} /^## / {inside = 0} inside' "$1"
}

# A sections directory with one entry per selling point, written by hand so the gates can be exercised. Each entry
# leads with a bold keyword and a paragraph (at most 320 characters), carries its keys as one marker line per
# key — and its words are drawn from the fixture specs' own text, so that a fully-sourced entry exists and the
# advisory word report can be asserted empty. `delta` is user-visible but not shortlisted, so no entry cites it.
sections() {
  mkdir -p "$work/sec"
  cat > "$work/sec/one.md" <<'MD'
- **Alpha capability** — Alpha first capability in 2 frames, Alpha second capability, Alpha third capability.
  <!-- cap: alpha#Alpha first capability -->
  <!-- cap: alpha#Alpha second capability -->
  <!-- cap: alpha#Alpha third capability -->
MD
  cat > "$work/sec/two.md" <<'MD'
- **Beta capability** — Beta sole capability in 2 frames and Beta paired capability.
  <!-- cap: beta#Beta sole capability -->
  <!-- cap: beta#Beta paired capability -->
MD
  cat > "$work/sec/car.md" <<'MD'
- **Epsilon car platform capability** — Epsilon car platform capability.
  <!-- cap: epsilon#Epsilon car platform capability -->
MD
}

build
sections

# ---------------------------------------------------------------------------
# 1. Enumeration and the capability key
# ---------------------------------------------------------------------------

run --release 1-1-1-1 --report-only > "$work/r.out" 2>&1
is "keys: one per requirement of every shipped spec" "9" "$(sed -n 's/^capability keys: //p' "$work/r.out")"
is "keys: a nested spec id is enumerated too" "1" \
  "$(run --list 2>/dev/null | awk -F'\t' '$2 == "probe/nested"' | wc -l | tr -d ' ')"

run --list 2>/dev/null | grep -q 'probe/nested#Nested internal capability' \
  && ok "keys: derived as <spec id>#<requirement name>" \
  || bad "keys: derived as <spec id>#<requirement name>"

mkdir -p "$repo/openspec/changes/some-change/specs/zz"
cat > "$repo/openspec/changes/some-change/specs/zz/spec.md" <<'MD'
# zz

## Requirements

### Requirement: A capability still in flight

#### Scenario: not shipped
- **WHEN** a change is in flight
- **THEN** the index does not see it
MD
run --release 1-1-1-1 --report-only > "$work/r.out" 2>&1
is "keys: an in-flight change contributes no key" "9" "$(sed -n 's/^capability keys: //p' "$work/r.out")"
rm -rf "$repo/openspec/changes"

run --release 1-1-1-1 --report-only > "$work/r.out" 2>&1
is "keys: every shipped spec is classified" "0" "$(sed -n 's/^unclassified spec ids: //p' "$work/r.out")"
run --list 2>/dev/null | awk -F'\t' '$2 == "delta" || $2 == "epsilon"' | grep -q 'car' \
  && ok "keys: the surface a spec is classified for is reported" \
  || bad "keys: the surface a spec is classified for is reported"

# ---------------------------------------------------------------------------
# 2. The delta compares requirement text
# ---------------------------------------------------------------------------

rm -rf "$repo/openspec/specs/alpha"
spec alpha "Alpha first capability" "Alpha second capability" "Alpha third capability"
run --release 2-1-1-1 > /dev/null 2>&1

sed -i 's/Fixture spec alpha, used by the self-test\./Fixture spec alpha, reworded in its purpose only./' \
  "$repo/openspec/specs/alpha/spec.md"
run --release 2-1-1-2 --report-only > "$work/r.out" 2>&1
is "delta: a prose-only edit is not a change" "0" "$(sed -n 's/^changed: //p' "$work/r.out")"
is "delta: a prose-only edit does not dirty an area" "0" "$(sed -n 's/^dirty areas: //p' "$work/r.out")"

run --release 2-1-1-3 > /dev/null 2>&1
sed -i 's/Alpha first capability SHALL do the thing its name says\./Alpha first capability SHALL do the very thing its name says./' \
  "$repo/openspec/specs/alpha/spec.md"
run --release 2-1-1-4 --report-only > "$work/r.out" 2>&1
is "delta: a requirement text edit is a change" "1" "$(sed -n 's/^changed: //p' "$work/r.out")"
is "delta: a requirement text edit dirties its area" "1" "$(sed -n 's/^dirty areas: //p' "$work/r.out")"

run --release 2-1-1-5 > /dev/null 2>&1
printf '### Requirement: Alpha fourth capability\n\nAlpha fourth capability SHALL do the thing its name says.\n\n#### Scenario: Alpha fourth capability holds\n\n- **WHEN** the fixture says so\n- **THEN** Alpha fourth capability is Alpha fourth capability\n\n' \
  >> "$repo/openspec/specs/alpha/spec.md"
run --release 2-1-1-6 --report-only > "$work/r.out" 2>&1
is "delta: an added requirement is an addition" "1" "$(sed -n 's/^added: //p' "$work/r.out")"
is "delta: the spec's other keys are unchanged" "0" "$(sed -n 's/^changed: //p' "$work/r.out")"

# ---------------------------------------------------------------------------
# 3. The classification gate
# ---------------------------------------------------------------------------

jq 'del(.specs.epsilon)' "$data/specs.json" > "$data/specs.json.tmp" && mv "$data/specs.json.tmp" "$data/specs.json"
rc "gate: an unclassified shipped spec id fails the run" 1 run --release 3-1-1-1
sections; run --release 3-1-1-2 --sections "$work/sec" > "$work/r.out" 2>&1
is "gate: an unclassified spec id writes no document" "0" "$(grep -c '^catalogue' "$work/r.out" || true)"
specs_

jq '.specs["not-a-shipped-spec"] = {"area":"one","userVisible":false,"surfaces":[]}' \
  "$data/specs.json" > "$data/specs.json.tmp" && mv "$data/specs.json.tmp" "$data/specs.json"
rc "gate: a stale classification entry does not fail the run" 0 run --release 3-1-1-3 --report-only
run_split --release 3-1-1-3 --report-only > /dev/null
is "gate: a stale entry is reported" "1" "$(sed -n 's/^stale classification entries: //p' "$work/g.out")"
grep -q 'not-a-shipped-spec' "$work/g.err" && ok "gate: the stale entry is named" || bad "gate: the stale entry is named"
specs_

jq '.specs.alpha.surfaces = ["tablet"]' "$data/specs.json" > "$data/specs.json.tmp" && mv "$data/specs.json.tmp" "$data/specs.json"
rc "gate: a surface outside the vocabulary fails the run" 1 run --release 3-1-1-4
run_split --release 3-1-1-4 > /dev/null
grep -q 'tablet' "$work/g.err" && ok "gate: the unknown surface is named" || bad "gate: the unknown surface is named"
specs_

jq '.specs.alpha.surfaces = []' "$data/specs.json" > "$data/specs.json.tmp" && mv "$data/specs.json.tmp" "$data/specs.json"
rc "gate: a user-visible spec with no renderable surface fails" 1 run --release 3-1-1-5
specs_

jq '.areas |= map(.carOnly = false)' "$data/areas.json" > "$data/areas.json.tmp" && mv "$data/areas.json.tmp" "$data/areas.json"
rc "gate: not exactly one car-only area fails the run" 1 run --release 3-1-1-6
areas_

jq '.specs.alpha.area = "nope"' "$data/specs.json" > "$data/specs.json.tmp" && mv "$data/specs.json.tmp" "$data/specs.json"
rc "gate: an unknown area id fails the run" 1 run --release 3-1-1-7
specs_

# ---------------------------------------------------------------------------
# 4. Snapshot, baseline and the report
# ---------------------------------------------------------------------------

rm -rf "$data/snapshots" "$data/cache" "$out/FEATURES.md" "$out/RELEASE-NOTES.md"
run --release 4-1-1-1 > "$work/r.out" 2>&1
is "snapshot: the requested version is recorded" "4-1-1-1" "$(jq -r '.version' "$data/snapshots/4-1-1-1.json")"
jq -e '[.keys[] | has("digest")] | all' "$data/snapshots/4-1-1-1.json" >/dev/null \
  && ok "snapshot: every key carries a digest" || bad "snapshot: every key carries a digest"
jq -e '[.keys[] | has("text")] | any | not' "$data/snapshots/4-1-1-1.json" >/dev/null \
  && ok "snapshot: no requirement text is recorded, only a digest" \
  || bad "snapshot: no requirement text is recorded, only a digest"
is "snapshot: no earlier snapshot is reported" "none" "$(sed -n 's/^baseline: //p' "$work/r.out")"

run --release 4-1-1-2 > "$work/r.out" 2>&1
case "$(sed -n 's/^baseline: //p' "$work/r.out")" in
  */4-1-1-1.json) ok "snapshot: the baseline is the most recent earlier snapshot" ;;
  *) bad "snapshot: the baseline is the most recent earlier snapshot" "got $(sed -n 's/^baseline: //p' "$work/r.out")" ;;
esac
run --release 4-1-1-2 --report-only > "$work/r.out" 2>&1
is "report: a no-op run is visibly a no-op" "0 0 0 10 0" \
  "$(printf '%s' "$(sed -n 's/^added: //p' "$work/r.out") $(sed -n 's/^changed: //p' "$work/r.out") $(sed -n 's/^removed: //p' "$work/r.out") $(sed -n 's/^unchanged: //p' "$work/r.out") $(sed -n 's/^dirty areas: //p' "$work/r.out")")"

run --release 4-1-1-10 > /dev/null 2>&1
run --release 4-1-2-1 --report-only > "$work/r.out" 2>&1
case "$(sed -n 's/^baseline: //p' "$work/r.out")" in
  */4-1-1-10.json) ok "snapshot: versions order as the release format orders them (-1 < -10 < -2)" ;;
  *) bad "snapshot: versions order as the release format orders them" "got $(sed -n 's/^baseline: //p' "$work/r.out")" ;;
esac

run --release 4-1-3-1 --report-only > "$work/r.out" 2>&1
is "report: a one-spec change is visibly one change" "0" "$(sed -n 's/^changed: //p' "$work/r.out")"
is "report: the whole fixture set is indexed" "6" "$(sed -n 's/^specs read: //p' "$work/r.out")"

# ---------------------------------------------------------------------------
# 5. The catalogue: rendering, areas, surfaces
# ---------------------------------------------------------------------------

rm -rf "$data/cache" "$out/FEATURES.md"
sections; run --release 5-1-1-1 --sections "$work/sec" > "$work/r.out" 2>&1
cat="$out/FEATURES.md"
is "catalogue: every user-visible area is named" "3" "$(grep -c '^## ' "$cat" || true)"
grep -q '^## Area Three$' "$cat" && bad "catalogue: an area holding only internal capabilities gets no heading" \
  || ok "catalogue: an area holding only internal capabilities gets no heading"
grep -q '^## In The Car$' "$cat" && ok "catalogue: the car-only area is rendered under its own heading" \
  || bad "catalogue: the car-only area is rendered under its own heading"
grep -q 'Gamma internal capability' "$cat" && bad "catalogue: an internal capability is absent" \
  || ok "catalogue: an internal capability is absent"
grep -q 'Nested internal capability' "$cat" && bad "catalogue: a nested internal spec is absent" \
  || ok "catalogue: a nested internal spec is absent"
sed -n '/^## Area One$/,/^## /p' "$cat" | grep -q '^_Phone_$' \
  && ok "catalogue: the surface tag follows the shortlist, not every user-visible spec" \
  || bad "catalogue: the surface tag follows the shortlist, not every user-visible spec"
[ "$(section "$cat" "In The Car" | grep -c '^_Car_$')" = "1" ] \
  && ok "catalogue: the car-only area states the car surface" \
  || bad "catalogue: the car-only area states the car surface"
sed -n '/^## Area Two$/,/^## /p' "$cat" | grep -q '_Phone and car_' \
  && ok "catalogue: a dual-surface capability tags both surfaces" \
  || bad "catalogue: a dual-surface capability tags both surfaces"
is "catalogue: a dual-surface capability appears once" "1" \
  "$(grep -c '^- \*\*Beta capability\*\*' "$cat" || true)"
is "catalogue: a capability outside the shortlist is absent" "0" \
  "$(grep -c 'Delta car capability' "$cat" || true)"
grep -q 'Delta car capability' "$cat" && bad "catalogue: a shortlisted-away capability is not published" \
  || ok "catalogue: a shortlisted-away capability is not published"
grep -qE '[0-9]{4}-[0-9]{2}-[0-9]{2}' "$cat" && bad "catalogue: no generation timestamp" \
  || ok "catalogue: no generation timestamp"
# an entry is a `- ` line, its wrapped paragraph, and its marker lines: every entry must carry at least one
awk '
  /^- / { if (inentry && !marked) bad = 1
           inentry = 1; marked = 0; next }
  /^[[:space:]]*<!-- cap:/ { marked = 1; next }
  /^[[:space:]]*$/ { if (inentry && !marked) bad = 1; inentry = 0; next }
  /^## / { if (inentry && !marked) bad = 1; inentry = 0 }
  END { if (inentry && !marked) bad = 1; exit bad }
' "$cat" \
  && ok "catalogue: every entry carries a marker" \
  || bad "catalogue: every entry carries a marker"
# every entry leads with a bold keyword
awk '/^- / { if ($0 !~ /^- \*\*[^*]+\*\*/) bad = 1 } END { exit bad }' "$cat" \
  && ok "catalogue: every entry leads with a bold keyword" \
  || bad "catalogue: every entry leads with a bold keyword"
is "catalogue: the report states what it published" "1" \
  "$(grep -cE '^catalogue: .*[0-9]+ entries for [0-9]+ selling points' "$work/last-run.log" || true)"

# the degraded mode: no seam at all still produces a catalogue
rm -rf "$data/cache" "$out/FEATURES.md"
rc "catalogue: mechanical assembly works with no model available" 0 run --release 5-1-1-2
grep -q 'Alpha first capability' "$out/FEATURES.md" && ok "catalogue: the mechanical mode names the capability" \
  || bad "catalogue: the mechanical mode names the capability"

# ---------------------------------------------------------------------------
# 6. The prose gates
# ---------------------------------------------------------------------------

# gate_run <release> <sed program> — rebuilds the sections, applies the mutation, clears the cache so the
# mutated file is genuinely read, then runs the tool with both streams separated and the exit code in $last_rc.
gate_run() {
  sections
  [ -n "$2" ] && sed -i "$2" "$work/sec/one.md"
  rm -rf "$data/cache"
  run_split --release "$1" --sections "$work/sec"
}

gate_run 6-1-1-1 ''
is "gates: a well-sourced section passes" "0" "$last_rc"

gate_run 6-1-1-2 '2,4d'
is "gates: an entry with no key fails the run" "1" "$last_rc"
grep -q 'entry carries no source marker' "$work/g.err" && ok "gates: the keyless entry is named" \
  || bad "gates: the keyless entry is named"

gate_run 6-1-1-3 '1s/$/ 77777 items/'
is "gates: an invented figure fails the run" "1" "$last_rc"
grep -q 'figure in none of the cited specs: 77777' "$work/g.err" \
  && ok "gates: the invented figure is named" || bad "gates: the invented figure is named"

# the fixture writes `within 2 frames` into every requirement text, so `2` is a sourced figure
gate_run 6-1-1-4 '1s/$/ in 2 frames/'
is "gates: a figure taken from a cited spec passes" "0" "$last_rc"
grep -q 'figure in none of the cited specs' "$work/g.err" \
  && bad "gates: a sourced figure is accepted" || ok "gates: a sourced figure is accepted"

gate_run 6-1-1-5 '1s/$/ quantum/'
is "gates: an unsourced word is reported and the run stays green" "0" "$last_rc"
grep -q 'unsourced word: quantum' "$work/g.out" && ok "gates: the unsourced word is named" \
  || bad "gates: the unsourced word is named"
grep -q '^catalogue:' "$work/g.out" && ok "gates: the report does not stand in for the figure gate" \
  || bad "gates: the report does not stand in for the figure gate"

gate_run 6-1-1-6 ''
grep -q 'unsourced word' "$work/g.out" && bad "gates: a fully sourced bullet is not reported" \
  || ok "gates: a fully sourced bullet is not reported"

gate_run 6-1-1-7 's/alpha#Alpha first capability/ghost#Nowhere/'
is "gates: a key citing a spec that does not exist fails the run" "1" "$last_rc"
gate_run 6-1-1-8 's/alpha#Alpha first capability/gamma#Gamma internal capability/'
is "gates: a key citing an internal spec fails the run" "1" "$last_rc"

gate_run 6-1-1-9 '1,4d'
is "gates: an unreached selling point fails the run" "1" "$last_rc"
grep -q 'selling point not reached: alpha-selling-point' "$work/g.err" \
  && ok "gates: the unreached selling point is named" || bad "gates: the unreached selling point is named"

# the keyword gates: an entry must lead with a bold keyword, short enough to scan, in the cited specs' words
gate_run 6-1-2-1 '1s/\*\*[^*]*\*\* //'
is "gates: an entry with no bold keyword fails the run" "1" "$last_rc"
grep -q 'entry does not lead with a bold keyword' "$work/g.err" \
  && ok "gates: the missing keyword is named" || bad "gates: the missing keyword is named"

gate_run 6-1-2-2 '1s/\*\*Alpha capability\*\*/**Alpha capability for the whole area one**/'
is "gates: a keyword longer than six words fails the run" "1" "$last_rc"
grep -qE 'entry keyword is [0-9]+ words' "$work/g.err" \
  && ok "gates: the over-long keyword is named with its length" \
  || bad "gates: the over-long keyword is named with its length"

gate_run 6-1-2-3 '1s/\*\*Alpha capability\*\*/**Quantum capability**/'
is "gates: a keyword word the cited specs lack fails the run" "1" "$last_rc"
grep -qiE 'keyword word in none of the cited specs: *quantum' "$work/g.err" \
  && ok "gates: the invented keyword word is named" || bad "gates: the invented keyword word is named"

gate_run 6-1-2-4 ''
is "gates: a well-formed entry passes the keyword gates" "0" "$last_rc"

# a capitalised stopword in a keyword is still a stopword: "Your" must not be reported as an invented word
gate_run 6-1-2-5 '1s/\*\*Alpha capability\*\*/**Your Alpha capability**/'
is "gates: a capitalised stopword in a keyword is accepted" "0" "$last_rc"

# the shortlist: a capability it does not name may not be published, one entry sells one thing, and a paragraph
# stays within the bound
sections
cat > "$work/sec/one.md" <<'MD'
- **Alpha and delta capability** — Alpha first capability in 2 frames, Alpha second capability, Alpha third
  capability, Delta car capability.
  <!-- cap: alpha#Alpha first capability -->
  <!-- cap: alpha#Alpha second capability -->
  <!-- cap: alpha#Alpha third capability -->
  <!-- cap: delta#Delta car capability -->
MD
rm -rf "$data/cache"
run_split --release 6-1-3-1 --sections "$work/sec"
is "shortlist: a capability outside the shortlist fails the run" "1" "$last_rc"
grep -q 'cites specs the shortlist does not name' "$work/g.err" \
  && ok "shortlist: the unlisted spec is named" || bad "shortlist: the unlisted spec is named"

sections
printf '  <!-- cap: beta#Beta sole capability -->\n' >> "$work/sec/one.md"
rm -rf "$data/cache"
run_split --release 6-1-3-2 --sections "$work/sec"
is "shortlist: an entry spanning two selling points fails the run" "1" "$last_rc"
grep -q 'spans more than one selling point' "$work/g.err" \
  && ok "shortlist: the spanning entry is named" || bad "shortlist: the spanning entry is named"

sections
sed -i '1s/$/ Alpha first capability in 2 frames, Alpha second capability in 2 frames, Alpha third capability in 2 frames, and then one more round of Alpha first capability in 2 frames, Alpha second capability in 2 frames, Alpha third capability in 2 frames./' "$work/sec/one.md"
rm -rf "$data/cache"
run_split --release 6-1-3-3 --sections "$work/sec"
is "length: an over-long paragraph fails the run" "1" "$last_rc"
grep -qE 'entry paragraph is [0-9]+ characters, more than the 320' "$work/g.err" \
  && ok "length: the paragraph and its length are named" || bad "length: the paragraph and its length are named"

sections; rm -rf "$data/cache"; run_split --release 6-1-3-4 --sections "$work/sec"
is "length: a short paragraph passes" "0" "$last_rc"

# ---------------------------------------------------------------------------
# 7. Regeneration and byte-identity
# ---------------------------------------------------------------------------

sections
rm -rf "$data/cache" "$out/FEATURES.md"
run --release 7-1-1-1 --sections "$work/sec" > /dev/null 2>&1
first=$(sha256sum < "$out/FEATURES.md")
run --release 7-1-1-2 --sections "$work/sec" > "$work/r.out" 2>&1
is "regeneration: nothing changed means no dirty area" "0" "$(sed -n 's/^dirty areas: //p' "$work/r.out")"
[ "$first" = "$(sha256sum < "$out/FEATURES.md")" ] \
  && ok "regeneration: two runs produce the same file" \
  || bad "regeneration: two runs produce the same file"

# an instruction change dirties every area
printf '\n' >> "$data/prompts/tone.md" 2>/dev/null || printf 'A note.\n' > "$data/prompts/tone.md"
run --release 7-1-1-3 --report-only > "$work/r.out" 2>&1
is "regeneration: an instruction change dirties every area" "4" "$(sed -n 's/^dirty areas: //p' "$work/r.out")"

# a spec that is no longer shipped leaves the catalogue on the next full run
sections
run --release 7-1-1-4 --sections "$work/sec" > /dev/null 2>&1
sed -i '/beta#Beta paired capability/d' "$work/sec/two.md"
rm -rf "$data/cache"
run --release 7-1-1-5 --sections "$work/sec" > /dev/null 2>&1
run --release 7-1-1-6 --report-only > /dev/null 2>&1
is "regeneration: a merged capability survives" "1" "$(grep -c '^- \*\*Alpha capability\*\*' "$out/FEATURES.md" || true)"

# ---------------------------------------------------------------------------
# 8. The release note
# ---------------------------------------------------------------------------

rm -rf "$data/snapshots" "$data/cache" "$out/RELEASE-NOTES.md"
run --release 8-1-1-1 > "$work/r.out" 2>&1
[ -f "$out/RELEASE-NOTES.md" ] && bad "notes: the first run writes no entry" \
  || ok "notes: the first run writes no entry"
grep -q 'the version established the baseline' "$work/r.out" \
  && ok "notes: the first run says the baseline was established" \
  || bad "notes: the first run says the baseline was established"

sed -i 's/Beta sole capability SHALL do the thing its name says\./Beta sole capability SHALL do exactly the thing its name says./' \
  "$repo/openspec/specs/beta/spec.md"
run --release 8-1-1-2 > "$work/r.out" 2>&1
is "notes: one entry per version carrying a change" "1" "$(grep -c '^## ' "$out/RELEASE-NOTES.md" || true)"
grep -q '^## 8-1-1-2$' "$out/RELEASE-NOTES.md" && ok "notes: the entry names its version" \
  || bad "notes: the entry names its version"
grep -q 'Beta sole capability' "$out/RELEASE-NOTES.md" && ok "notes: the changed capability appears" \
  || bad "notes: the changed capability appears"
grep -q '^### Area Two$' "$out/RELEASE-NOTES.md" && ok "notes: the entry is grouped by feature area" \
  || bad "notes: the entry is grouped by feature area"

cp "$out/RELEASE-NOTES.md" "$work/notes-before.md"
run --release 8-1-1-2 > /dev/null 2>&1
is "notes: re-running a version leaves one entry" "1" "$(grep -c '^## ' "$out/RELEASE-NOTES.md" || true)"
[ "$(sha256sum < "$work/notes-before.md")" = "$(sha256sum < "$out/RELEASE-NOTES.md")" ] \
  && ok "notes: re-running a version replaces its entry byte for byte" \
  || bad "notes: re-running a version replaces its entry byte for byte"

# an internal-only change writes no entry and reports
sed -i 's/Gamma internal capability SHALL do the thing its name says\./Gamma internal capability SHALL do the internal thing./' \
  "$repo/openspec/specs/gamma/spec.md"
run --release 8-1-1-3 > "$work/r.out" 2>&1
is "notes: an internal-only change writes no entry" "1" "$(grep -c '^## ' "$out/RELEASE-NOTES.md" || true)"
grep -q 'nothing user-visible changed in 8-1-1-3' "$work/r.out" \
  && ok "notes: the empty-release verdict is reported" || bad "notes: the empty-release verdict is reported"
is "notes: an internal-only change still reports the delta" "1" "$(sed -n 's/^changed: //p' "$work/r.out")"

# a renamed requirement is a change, not a removal
sed -i 's/^### Requirement: Alpha first capability/### Requirement: Alpha first ability/' "$repo/openspec/specs/alpha/spec.md"
run --release 8-1-1-4 > "$work/r.out" 2>&1
is "notes: a renamed requirement is a change, not a removal" "0" "$(sed -n 's/^note: .* and \([0-9]*\) removed.*/\1/p' "$work/r.out")"
grep -q 'renamed, reported as a change' "$work/r.out" \
  && ok "notes: the rename is reported in the run report" || bad "notes: the rename is reported in the run report"
grep -q 'No longer available: Alpha first capability' "$out/RELEASE-NOTES.md" \
  && bad "notes: a rename is not published as a removal" \
  || ok "notes: a rename is not published as a removal"
grep -qE '^## 8-1-1-4$' "$out/RELEASE-NOTES.md" && ok "notes: the renamed version still gets an entry" \
  || bad "notes: the renamed version still gets an entry"

# a requirement genuinely gone is a removal
sed -i '/^### Requirement: Beta paired capability/,/^### \|^## /{/^### Requirement: Beta paired capability/,/^\*\*THEN\*\*/d}' \
  "$repo/openspec/specs/beta/spec.md"
sed -i '/^### Requirement: Beta paired capability/,+8d' "$repo/openspec/specs/beta/spec.md"
run --release 8-1-1-5 > "$work/r.out" 2>&1
grep -q '^- \*\*Beta paired capability\*\*' "$out/RELEASE-NOTES.md" \
  && grep -q 'no longer available' "$out/RELEASE-NOTES.md" \
  && ok "notes: a deleted requirement is published as a removal" \
  || bad "notes: a deleted requirement is published as a removal"

# a spec leaving the shipped set is reported, not published
mv "$repo/openspec/specs/epsilon" "$work/epsilon.away"
entries_before=$(grep -c '^## ' "$out/RELEASE-NOTES.md" || true)
run --release 8-1-1-6 > "$work/r.out" 2>&1
grep -q 'spec left the shipped set' "$work/r.out" \
  && ok "notes: a spec leaving the shipped set is reported in the run report" \
  || bad "notes: a spec leaving the shipped set is reported in the run report"
grep -q 'Epsilon car platform capability' "$out/RELEASE-NOTES.md" \
  && bad "notes: a departed spec is not published as a change" \
  || ok "notes: a departed spec is not published as a change"
mv "$work/epsilon.away" "$repo/openspec/specs/epsilon"

# the note is derived without a model: the changed set comes from the snapshot and the specs
run --release 8-1-1-6 --report-only > "$work/r.out" 2>&1
grep -q '^changed:' "$work/r.out" && ok "notes: the changed set is reproducible with no model" \
  || bad "notes: the changed set is reproducible with no model"

# ---------------------------------------------------------------------------
# 9. The version is supplied, never discovered
# ---------------------------------------------------------------------------

rc "version: a run without a version fails with a usage error" 2 bash "$tool" --root "$repo" --data-dir "$data"
[ -f "$out/RELEASE-NOTES.md" ] && before=$(sha256sum < "$out/RELEASE-NOTES.md") || before=""
bash "$tool" --root "$repo" --data-dir "$data" --out-dir "$out" > /dev/null 2>&1
after="$([ -f "$out/RELEASE-NOTES.md" ] && sha256sum < "$out/RELEASE-NOTES.md" || echo "")"
is "version: a run without a version writes nothing" "$before" "$after"
grep -q 'release-version' "$tool" && bad "version: the version-state file is never read" \
  || ok "version: the version-state file is never read"
rc "version: the run works where the version-state file is absent" 0 run --release 9-1-1-1 --report-only

# ---------------------------------------------------------------------------
echo
if [ "${GITHUB_STEP_SUMMARY:-}" != "" ]; then
  printf 'gen-feature-list-selftest: %d passed, %d failed\n' "$pass" "$fail" >> "$GITHUB_STEP_SUMMARY"
fi
printf 'gen-feature-list-selftest: %d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ] || exit 1
exit 0
