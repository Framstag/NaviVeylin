#!/usr/bin/env bash
# Self-test for tools/project-metrics-report.sh — device-free, network-free, fixture-driven.
#
# Usage:
#   bash tools/project-metrics-report-selftest.sh
#
#   -h | --help           print this usage block
#
# What it does:
#   Builds fixture trees under a temporary directory (no repository content is read), runs the
#   report over them, and asserts one named case per counting rule, exclusion class, refusal
#   path and output guarantee. Case names are the spec's scenario names.
#
# Exit status:
#   0  every case passed
#   1  at least one case failed (the failing case is named on stderr)
#   2  usage or environment error
set -uo pipefail

usage() { sed -n '3,18p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; }
case "${1:-}" in -h|--help) usage; exit 0 ;; -*) echo "selftest: unknown option $1" >&2; exit 2 ;; esac

tool="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/project-metrics-report.sh"
[ -f "$tool" ] || { echo "selftest: $tool not found" >&2; exit 2; }
for t in find awk sed sort grep date mktemp diff ln; do
  command -v "$t" >/dev/null 2>&1 || { echo "selftest: required tool '$t' missing" >&2; exit 2; }
done

work=$(mktemp -d) || { echo "selftest: cannot create a work directory" >&2; exit 2; }
trap 'if [ -z "${KEEP_WORK:-}" ]; then rm -rf "$work"; else echo "work kept: $work" >&2; fi' EXIT

pass=0; fail=0; total=0
ok()   { total=$((total + 1)); pass=$((pass + 1)); printf 'ok   %s\n' "$1"; }
bad()  { total=$((total + 1)); fail=$((fail + 1)); printf 'FAIL %s — %s\n' "$1" "$2" >&2; }
check() { # name condition-result detail
  if [ "$2" = 0 ]; then ok "$1"; else bad "$1" "$3"; fi
}
contains() { grep -qF -- "$2" "$1"; }

# ---------------------------------------------------------------- fixture trees
fx="$work/fixture"
mk() { mkdir -p "$(dirname "$1")"; printf '%s' "$2" > "$1"; }

# production, nested package, with all three comment styles
mk "$fx/app/src/main/java/com/naviveylin/ui/map/Foo.kt" \
'// one comment line
/* a block comment
   spanning three lines */
package com.naviveylin.ui.map

class Foo {
    val a = 1   // trailing comment counts as code
    val b = 2

}
'
# a second file in the same package, so package sums are checkable
mk "$fx/app/src/main/java/com/naviveylin/ui/map/Bar.kt" \
'package com.naviveylin.ui.map

class Bar {
    fun x() = 1
}
'
# car surface by name, and the app API form (…CarAppService)
mk "$fx/app/src/main/java/com/naviveylin/ui/map/CarSessionSurface.kt" \
'package com.naviveylin.ui.map

class CarSessionSurface
'
mk "$fx/app/src/main/java/com/naviveylin/NaviVeylinCarAppService.kt" \
'package com.naviveylin

class NaviVeylinCarAppService
'
# no package path at all, and a foreign package
mk "$fx/app/src/main/java/Rootless.kt" 'class Rootless
'
mk "$fx/app/src/main/java/com/framstag/libosmscout/client/Client.java" \
'package com.framstag.libosmscout.client;

class Client {}
'
# a Kotlin source root, a unit test and an instrumented test
mk "$fx/app/src/main/kotlin/com/naviveylin/kotlinsrc/Kot.kt" \
'package com.naviveylin.kotlinsrc

class Kot
'
mk "$fx/app/src/test/java/com/naviveylin/ui/map/FooTest.kt" \
'package com.naviveylin.ui.map

class FooTest
'
mk "$fx/app/src/androidTest/java/com/naviveylin/ui/map/FooDeviceTest.kt" \
'package com.naviveylin.ui.map

class FooDeviceTest
'
# the auto module is car code whatever the file is called
mk "$fx/auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt" \
'package com.naviveylin.auto

class NavigationScreen
'
# a source package legitimately named build — must survive the generated-tree prune
mk "$fx/buildSrc/src/main/kotlin/com/naviveylin/build/Tool.kt" \
'package com.naviveylin.build

class Tool
'
# documentation, an unclassified shell script, shell and XML comment styles
mk "$fx/docs/Guide.md" '# Guide

Some prose.
'
mk "$fx/tools/helper.sh" '#!/usr/bin/env bash
# a comment
echo hi
'
mk "$fx/app/src/main/res/layout/strings.xml" \
'<?xml version="1.0" encoding="utf-8"?>
<!-- a comment -->
<resources>
  <string name="a">b</string>
</resources>
'
# excluded classes: generated build tree, agent tool state, vendored source, tooling state
mk "$fx/build/Generated.kt" 'class Generated
'
mk "$fx/app/build/Generated2.kt" 'class Generated2
'
mk "$fx/.pi/logs/dump.txt" 'a log line
'
mk "$fx/app/src/main/cpp/libosmscout/osmscout.cpp" 'int main() { return 0; }
'
mk "$fx/.gradle/cache.kt" 'class Cache
'
mk "$fx/local.properties" 'sdk.dir=/somewhere
'
mk "$fx/app/build.gradle.kts" 'plugins { id("com.android.application") }
'
mk "$fx/settings.gradle.kts" 'rootProject.name = "fixture"
'
# a nested OpenSpec store, which must not become this project's inventory
mk "$fx/nested/openspec/specs/decoy/spec.md" '# decoy
'
# version-control internals, for the vcs class
mk "$fx/.git/config" '[core]
'

nolayout="$work/nolayout"
mkdir -p "$nolayout/empty"
printf 'just text\n' > "$nolayout/empty/notes.txt"

# a fixture with no car code, for the empty-surface case
nocar="$work/nocar"
mk "$nocar/app/src/main/java/com/naviveylin/onlyphone/Only.kt" \
'package com.naviveylin.onlyphone

class Only
'

# ---------------------------------------------------------------- stub PATH
# Only the tools the report requires, plus a controllable openspec stub, so the run is
# provably device-free and network-free.
stub="$work/stub"
mkdir -p "$stub"
for t in find awk sed sort grep date mktemp rm mkdir rmdir cat tr bash sh ls printf wc head dirname; do
  p=$(type -P "$t" 2>/dev/null) && ln -sf "$p" "$stub/$t"
done
p=$(type -P jq 2>/dev/null) && ln -sf "$p" "$stub/jq"
# find -exec needs a real printf binary; a builtin name would create a self-referential link
[ -x "$stub/printf" ] || ln -sf "$(type -P printf 2>/dev/null || echo /usr/bin/printf)" "$stub/printf"
cat > "$stub/openspec" <<'STUB'
#!/usr/bin/env bash
# Fixture CLI: --specs reports 7 feature directories, and the change list holds 3 open and 11
# archived changes, while the file system holds different numbers, so the source is provable.
case "$*" in
  *--specs*) printf '{"specs":[%s]}\n' "$(printf '"x",%.0s' 1 2 3 4 5 6 7 | sed 's/,$//')" ;;
  *)         printf '{"changes":[%s]}\n' \
               "$(printf '{"archived":false},%.0s' 1 2 3; printf '{"archived":true},%.0s' 1 2 3 4 5 6 7 8 9 10 11 | sed 's/,$//')" ;;
esac
STUB
chmod +x "$stub/openspec"

# A CLI that answers with unreadable output: the section must say so, not print a 0.
badout="$work/stub-bad-output"
mkdir -p "$badout"
for f in "$stub"/*; do
  case "$(basename "$f")" in openspec) ;; *) ln -sf "$f" "$badout/$(basename "$f")" ;; esac
done
printf '#!/usr/bin/env bash\nprintf "not json at all\\n"\n' > "$badout/openspec"
chmod +x "$badout/openspec"

noclis="$work/stub-no-cli"
mkdir -p "$noclis"
for f in "$stub"/*; do
  case "$(basename "$f")" in openspec|jq) ;; *) ln -sf "$f" "$noclis/$(basename "$f")" ;; esac
done
nojq="$work/stub-no-jq"
mkdir -p "$nojq"
for f in "$stub"/*; do
  case "$(basename "$f")" in jq) ;; *) ln -sf "$f" "$nojq/$(basename "$f")" ;; esac
done

run() { # scope... -> report on stdout
  PATH="$stub" bash "$tool" "$@"
}
rc=0
report="$work/report.txt"
PATH="$stub" bash "$tool" "$fx" > "$report" 2>"$work/report.err" || rc=$?
if [ $rc -ne 0 ]; then
  echo "selftest: the fixture report failed (exit $rc)" >&2
  cat "$work/report.err" >&2
  exit 2
fi

# ---------------------------------------------------------------- cases
# 1. header and input rule
contains "$report" "date " && contains "$report" "scope " && contains "$report" "files measured" \
  && contains "$report" "excluded classes"
check "Header names the subject" $? "header is missing date, scope, files measured or excluded classes"

measured=$(sed -n 's/^  files measured *//p' "$report")
notlisted=$(sed -n 's/^  not listed here: \([0-9]*\) documentation.*/\1/p' "$report")
notlisted2=$(sed -n 's/^  not listed here: [0-9]* documentation file(s) ([0-9]* lines), \([0-9]*\) OpenSpec.*/\1/p' "$report")
rows=$(awk '/^FILES \(source files/{f=1;next} /^  not listed here/{f=0} f && /^ *[0-9]/{n++} END{print n+0}' "$report")
[ "$measured" = "$((rows + notlisted + notlisted2))" ]
check "Every source file appears" $? "source rows ($rows) + aggregated ($notlisted docs, $notlisted2 store) != files measured ($measured)"

# every figure column is headed: the header names all five figures and has one cell fewer than a data
# row's fields (the row's first field is its label)
awk '
  /^LANGUAGE/{f=1;next}
  f && /^ *FILES +CODE +COMMENT +BLANK +TOTAL *$/ { h = NF; named = 1 }
  f && $NF ~ /^[0-9]+$/ && $(NF-1) ~ /^[0-9]+$/ { d = NF - 1; seen = 1; f = 0 }
  END { exit !(seen && named && h == d && h >= 5) }
' "$report"
check "Every figure column is headed" $? "the header does not name all five columns or its cell count differs from a data row"

# documentation and store files appear in no per-file list, and their counts are stated
if awk '/^FILES \(source files/{f=1} /^SOURCE SET/{f=0} f && /\.md$/{print}' "$report" | grep -q .; then
  bad "Documentation is aggregated, not listed (no document row)" "a document path appears in the source file list"
else
  ok "Documentation is aggregated, not listed (no document row)"
fi
if sed -n '/^FILES (source files/,/^SOURCE SET/p;/^UNCLASSIFIED/,/^EXCLUDED/p' "$report" | grep -qE '/openspec/'; then
  bad "Documentation is aggregated, not listed (no store row)" "an OpenSpec store path appears in a per-file list"
else
  ok "Documentation is aggregated, not listed (no store row)"
fi
grep -qE '^  not listed here: [0-9]+ documentation file\(s\) \([0-9]+ lines\), [0-9]+ OpenSpec store file\(s\) \([0-9]+ lines\)' "$report"
check "Documentation is aggregated, not listed (counts stated)" $? "the aggregate counts are not stated beside the list"

contains "$report" "generated build tree" && contains "$report" "agent tool state" \
  && contains "$report" "tooling state" && contains "$report" "vcs internal" \
  && contains "$report" "machine-local config" && contains "$report" "build script"
check "An excluded class is stated" $? "the header does not name every class present in the fixture"

for excluded_path in "local.properties" "app/build.gradle.kts" "settings.gradle.kts"; do
  if grep -qE "^ *[0-9]+ +[0-9]+ +[0-9]+ +[0-9]+ +.*${excluded_path}$" "$report"; then
    bad "Machine-local config and build scripts are excluded" "$excluded_path is counted"
  else
    ok "Machine-local config and build scripts are excluded ($excluded_path)"
  fi
done

grep -qF "build/Generated.kt" "$report" && false || true
if grep -qF "build/Generated.kt" "$report"; then
  bad "Excluded files are never listed" "a generated build tree file appears in the report"
else
  ok "Excluded files are never listed"
fi
if grep -qF "libosmscout/osmscout.cpp" "$report"; then
  bad "Vendored source is external" "a vendored file appears in the report"
else
  ok "Vendored source is external"
fi
contains "$report" "vendored source"
check "Vendored source is external (stated)" $? "the vendored class is not stated"

# 2. no version control at all: a fixture without .git, and a stub PATH without git
if [ -e "$nocar/.git" ] || [ -e "$stub/git" ]; then
  bad "No version-control tooling is needed" "the fixture or the stub PATH exposes version control"
else
  PATH="$stub" bash "$tool" "$nocar" > "$work/novcs.txt" 2>/dev/null
  novcs=$?
  [ "$novcs" = 0 ] && contains "$work/novcs.txt" "Only.kt"
  check "No version-control tooling is needed" $? "a run without git did not produce a report (exit $novcs)"
fi
contains "$report" "app/src/main/java/com/naviveylin/ui/map/Foo.kt"
check "Uncommitted files are measured" $? "an uncommitted fixture file is not counted"

# files measured tracks the scope: one added file raises it by exactly one
cp -r "$fx" "$work/plus1"
mk "$work/plus1/app/src/main/java/com/naviveylin/ui/map/Extra.kt" 'package com.naviveylin.ui.map

class Extra
'
PATH="$stub" bash "$tool" "$work/plus1" > "$work/plus1.txt" 2>/dev/null
m2=$(sed -n 's/^  files measured *//p' "$work/plus1.txt")
[ "$m2" = "$((measured + 1))" ]
check "Files measured tracks the scope" $? "files measured went $measured -> $m2, expected $((measured + 1))"

# every dimension is present
missing=""
for s in LANGUAGE MODULE "PACKAGE (complete" "LAYER (module:" "FILES (source files"; do
  contains "$report" "$s" || missing="$missing $s"
done
[ -z "$missing" ]
check "Figures are per dimension" $? "missing dimension section(s):$missing"

# 3. layout variants
contains "$report" "app:com.naviveylin.ui.map"
check "Layout variants are covered (nested package)" $? "the nested package is not attributed"
contains "$report" "app:(no package)"
check "Layout variants are covered (no package path)" $? "the package-less file is not attributed"
contains "$report" "app:com.framstag"
check "Layout variants are covered (foreign package)" $? "the foreign package is not attributed"
contains "$report" "app:com.naviveylin.kotlinsrc"
check "Layout variants are covered (kotlin source root)" $? "src/main/kotlin is not recognized"
contains "$report" "buildSrc:com.naviveylin.build"
check "A source package named build survives the prune" $? "buildSrc/…/build was pruned as a build tree"

# 4. unexpected file stays visible, and is attributed to nothing
contains "$report" "tools/helper.sh"
check "An unexpected file stays visible" $? "the unclassified bucket does not list the file"
if awk '/^MODULE/{f=1;next} /^PACKAGE/{f=0} f && /^tools /{found=1} END{exit !found}' "$report"; then
  bad "An unexpected file stays visible (unattributed)" "the unclassified file was attributed to a module"
else
  ok "An unexpected file stays visible (unattributed)"
fi

# 5. source sets and documentation
contains "$report" "unit test" && contains "$report" "instrumented test" && contains "$report" "production"
check "Test and production are split" $? "SOURCE SET does not separate the source sets"
contains "$report" "documentation"
check "Documentation is its own kind" $? "documentation is not reported as its own kind"
if awk '/^MODULE/{f=1;next} /^PACKAGE/{f=0} f && /docs/{found=1} END{exit !found}' "$report"; then
  bad "Documentation is its own kind (unattributed)" "a document was attributed to a module"
else
  ok "Documentation is its own kind (unattributed)"
fi

# 6. line kinds are additive; per-file total equals the sum
awk '
  /^FILES \(source files/{f=1;next}
  /^SOURCE SET/{f=0}
  f && $NF ~ /app\/src\/main\/java\/com\/naviveylin\/ui\/map\/Foo\.kt$/ {
    if ($1 + $2 + $3 != $4) { print "mismatch", $1, $2, $3, $4; bad=1 }
    seen=1
  }
  END { exit (bad || !seen) }
' "$report"
check "Line kinds sum to the total" $? "a file's code+comment+blank does not equal its total"

# 7. dimension sums agree: module app equals the sum of its package rows
awk '
  /^PACKAGE/{f=1;next}
  /^LAYER/{f=0}
  f && /^app:/{ s += $3 }
  /^MODULE/{m=1;next}
  /^PACKAGE/{m=0}
  m && /^app /{ mod = $3 }
  END { exit !(s == mod && mod > 0) }
' "$report"
check "Dimension sums agree" $? "the app module figure does not equal the sum of its packages"

# 8. ratios are reproducible from their printed operands
awk '
  /^RATIOS/ { f = 1; next }
  /^PLATFORM/ { f = 0 }
  f && /comment share/ {
    for (i = 1; i <= NF; i++) if ($i == "=") { a = $(i+1); b = $(i+3); want = 100 * a / b }
    got = $3; sub(/%/, "", got)
    if (got < want - 0.06 || got > want + 0.06) exit 1
    seen = 1
  }
  END { exit !seen }
' "$report"
check "A printed ratio is reproducible" $? "a recomputed ratio disagrees with the printed one"
grep -qE 'comment share .*= [0-9]+ / [0-9]+' "$report"
check "The operands are named" $? "a ratio is printed without its operands"

# the test share divides by source code, not by documentation lines.
# SOURCE SET labels may contain spaces, so the figures are counted from the right: $NF total,
# $(NF-1) blank, $(NF-2) comment, $(NF-3) code.
src_code=$(awk '/^SOURCE SET/{f=1;next} /^TOTAL/{f=0} f && !/documentation/ && NF >= 5 {s += $(NF-3)} END{print s+0}' "$report")
doc_code=$(awk '/^SOURCE SET/{f=1;next} /^TOTAL/{f=0} f && /documentation/ && NF >= 5 {s += $(NF-3)} END{print s+0}' "$report")
test_code=$(awk '/^SOURCE SET/{f=1;next} /^TOTAL/{f=0} f && /(unit test|instrumented test)/ && NF >= 5 {s += $(NF-3)} END{print s+0}' "$report")
printed=$(sed -n 's/.*test share.*= [0-9]* \/ \([0-9]*\)$/\1/p' "$report")
[ "$printed" = "$src_code" ] && [ "$doc_code" -gt 0 ] && [ "$test_code" -gt 0 ]
check "Test share excludes documentation" $? "test share divides by $printed, expected source code $src_code (documentation $doc_code must not be in it)"

# 9. platform: both surfaces, car by module and by name, empty surface printed as zero
awk '/^PLATFORM/{f=1;next} /^OPENSPEC/{f=0} f && /^  car code/{c=$(3)} f && /^  phone code/{p=3} END{exit !(c > 0)}' "$report"
check "Both surfaces are named" $? "the car share is missing or zero in a fixture that has car code"
contains "$report" "NaviVeylinCarAppService.kt" && contains "$report" "auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt"
check "Both surfaces are named (car by module and by name)" $? "the car file list misses a car file"
PATH="$stub" bash "$tool" "$nocar" > "$work/nocar.txt" 2>/dev/null
awk '/^PLATFORM/{f=1;next} /^OPENSPEC/{f=0} f && /^  car code/{if ($3 != 0) exit 1; seen=1} END{exit !seen}' "$work/nocar.txt"
check "An empty surface is not omitted" $? "a surface with no code is not printed as zero"

# 10. OpenSpec inventory comes from the CLI, not from the file system
awk '/^OPENSPEC/{f=1;next} /^UNCLASSIFIED/{f=0} f && /feature directories/{print $NF}' "$report" > "$work/specs-count"
[ "$(cat "$work/specs-count")" = 7 ]
check "The CLI is the source" $? "feature directories do not come from the CLI ($(cat "$work/specs-count"))"
open_n=$(sed -n '/^OPENSPEC/,/^UNCLASSIFIED/p' "$report" | sed -n 's/.*changes (open) *//p')
arch_n=$(sed -n '/^OPENSPEC/,/^UNCLASSIFIED/p' "$report" | sed -n 's/.*changes (archived) *//p')
[ "$open_n" = 3 ] && [ "$arch_n" = 11 ]
check "The CLI is the source (change counts)" $? "open=$open_n archived=$arch_n, expected the stub's 3 and 11"
PATH="$noclis" bash "$tool" "$fx" > "$work/nocli.txt" 2>/dev/null
grep -qF "unavailable" "$work/nocli.txt" && grep -qF "OPENSPEC" "$work/nocli.txt"
check "A missing CLI does not fail the run" $? "a missing CLI did not print an unavailable section"
PATH="$badout" bash "$tool" "$fx" > "$work/badout.txt" 2>/dev/null
badrc=$?
[ "$badrc" = 0 ] && grep -qF "did not return a usable inventory" "$work/badout.txt" \
  && ! grep -qE 'changes \(open\) +0' "$work/badout.txt"
check "Unreadable CLI output is not a count of 0" $? "unusable CLI output printed a figure (exit $badrc)"
PATH="$stub" bash "$tool" "$fx" > /dev/null 2>&1
check "A printed report exits 0" $? "a successful run did not exit 0"

# 11. refusal paths
PATH="$stub" bash "$tool" "$nolayout" > "$work/nolayout.out" 2>"$work/nolayout.err"
[ $? = 2 ]
check "An unrecognizable root is refused" $? "an unrecognizable scope did not exit 2"
grep -qF "no source layout" "$work/nolayout.err" && ! grep -qF "LANGUAGE" "$work/nolayout.out"
check "An unrecognizable root is refused (no figures)" $? "the refusal printed figures or did not name the reason"
PATH="$stub" bash "$tool" "$fx" "$work/does-not-exist" > /dev/null 2>"$work/badscope.err"
[ $? = 2 ] && grep -qF "does not exist" "$work/badscope.err"
check "A bad scope exits 2" $? "a missing scope did not exit 2 naming the path"
PATH="$stub" bash "$tool" "$fx" --bogus > "$work/bogus.out" 2>"$work/bogus.err"
[ $? = 2 ] && ! grep -qF "LANGUAGE" "$work/bogus.out"
check "An unsupported option is refused" $? "an unsupported option did not exit 2 with usage only"
# every refusal path in one case: unrecognizable layout and a nonexistent scope
PATH="$stub" bash "$tool" "$nolayout" > "$work/refuse1.out" 2>"$work/refuse1.err"; r1=$?
PATH="$stub" bash "$tool" "$work/does-not-exist" > "$work/refuse2.out" 2>"$work/refuse2.err"; r2=$?
[ "$r1" = 2 ] && [ "$r2" = 2 ] && grep -qF "no source layout" "$work/refuse1.err" \
  && grep -qF "does not exist" "$work/refuse2.err"
check "Refusal paths are covered" $? "a refusal path did not exit 2 with its reason named (layout=$r1 scope=$r2)"
if PATH="$nojq" bash "$tool" "$fx" > /dev/null 2>"$work/nojq.err"; then
  bad "A missing required tool exits 2" "a run without jq exited 0"
else
  [ "$?" = 2 ] && grep -qF "jq" "$work/nojq.err"
  check "A missing required tool exits 2" $? "a missing jq was not reported as an environment error"
fi
PATH="$stub" bash "$tool" "$nocar" > /dev/null 2>&1
a=$?
PATH="$stub" bash "$tool" "$fx" > /dev/null 2>&1
b=$?
[ "$a" = 0 ] && [ "$b" = 0 ]
check "Figures never decide the status" $? "runs over different trees did not both exit 0"

# 12. stability
PATH="$stub" bash "$tool" "$fx" > "$work/run1.txt" 2>/dev/null
PATH="$stub" bash "$tool" "$fx" > "$work/run2.txt" 2>/dev/null
grep -v '^  date ' "$work/run1.txt" > "$work/run1.body"
grep -v '^  date ' "$work/run2.txt" > "$work/run2.body"
diff -q "$work/run1.body" "$work/run2.body" >/dev/null
check "Byte-identical apart from the date" $? "two consecutive runs differ beyond the date"
LC_ALL=de_DE.UTF-8 PATH="$stub" bash "$tool" "$fx" > "$work/run3.txt" 2>/dev/null
grep -v '^  date ' "$work/run3.txt" > "$work/run3.body"
diff -q "$work/run1.body" "$work/run3.body" >/dev/null
check "Locale does not reorder" $? "a different locale reordered the report"

# a tie: two packages with an identical figure must print in name order
tie="$work/tie"
mk "$tie/app/src/main/java/com/naviveylin/bbb/B.kt" 'package com.naviveylin.bbb

class B
'
mk "$tie/app/src/main/java/com/naviveylin/aaa/A.kt" 'package com.naviveylin.aaa

class A
'
PATH="$stub" bash "$tool" "$tie" > "$work/tie.txt" 2>/dev/null
tie_order=$(sed -n '/^PACKAGE/,/^LAYER/p' "$work/tie.txt" | grep -E '^app:com\.naviveylin\.(aaa|bbb)' | sed 's/(d[0-9]*)//' | awk '{print $1}' | paste -sd, -)
[ "$tie_order" = "app:com.naviveylin.aaa,app:com.naviveylin.bbb" ]
check "Ties are ordered by name" $? "equal figures are printed as '$tie_order', not name-ascending"

# 13. no truncation over many packages
many="$work/many"
for i in $(seq 1 40); do
  mk "$many/app/src/main/java/com/naviveylin/pkg$i/C$i.kt" "package com.naviveylin.pkg$i

class C$i
"
done
PATH="$stub" bash "$tool" "$many" > "$work/many.txt" 2>/dev/null
np=$(awk '/^PACKAGE/{f=1;next} /^LAYER/{f=0} f && /^app:com.naviveylin.pkg/{n++} END{print n+0}' "$work/many.txt")
[ "$np" = 40 ]
check "No truncation" $? "only $np of 40 packages are listed"

# 14. the tool itself is free of device and network access
if grep -nE '\b(adb|curl|wget|nc|ssh|scp)\b' "$tool" >/dev/null; then
  bad "No device and no network" "the tool mentions a device or network command"
else
  ok "No device and no network"
fi

printf '\n%d/%d cases passed\n' "$pass" "$total"
[ "$fail" = 0 ] || { printf '%d case(s) failed\n' "$fail" >&2; exit 1; }
exit 0
