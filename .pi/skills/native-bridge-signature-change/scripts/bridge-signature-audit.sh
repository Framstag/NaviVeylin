#!/usr/bin/env bash
# Audit the NaviVeylin JNI bridge: for each native method declared in the local
# override, show how the JVM would resolve it and whether the C++ side actually
# defines it.
#
#   short symbol  Java_..._<name>            -> one native declaration: a signature
#                                              change is SILENT (old library reads the
#                                              old argument order, no link error)
#   long symbol   Java_..._<name>__<desc>    -> overloaded: the descriptor is part of
#                                              the name, so a change FAILS LOUDLY
#
# Usage: bridge-signature-audit.sh [method-name] [--with-descriptor]
#
# Exit status: 0 = every audited method resolves; 1 = a method has no symbol in the
# .cpp at all (UnsatisfiedLinkError on the first call); 2 = environment problem.

set -u

JAVA_CLASS="com.framstag.libosmscout.client.OSMScoutClient"
CLASS_PREFIX="Java_com_framstag_libosmscout_client_OSMScoutClient_"

root=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "not in a git repository" >&2; exit 2; }
java_file="$root/osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java"
cpp_file="$root/app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp"

[ -f "$java_file" ] || { echo "missing override declaration: $java_file" >&2; exit 2; }
[ -f "$cpp_file" ] || { echo "missing JNI implementation: $cpp_file (submodule initialized?)" >&2; exit 2; }

name_filter=""
with_descriptor=0
for arg in "$@"; do
  case "$arg" in
    --with-descriptor) with_descriptor=1 ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    -*) echo "unknown option: $arg" >&2; exit 2 ;;
    *) name_filter="$arg" ;;
  esac
done

jar=$(ls "$root"/osmscout-client-java/build/libs/libosmscoutclientjava-*.jar 2>/dev/null | head -1 || true)

# Native declarations in the compiled override: "<count> <name>", sorted by count desc.
decls=$(grep -E 'public native' "$java_file" |
  sed -E 's/.*[[:space:]]([A-Za-z0-9_]+)\(.*/\1/' |
  grep -E '^[A-Za-z0-9_]+$' |
  sort | uniq -c | sort -rn)

[ -n "$decls" ] || { echo "no native declarations found in $java_file" >&2; exit 2; }

printf 'bridge audit — %s\n' "$cpp_file"
[ -n "$jar" ] && printf 'jar            %s\n' "$jar" || printf 'jar            (absent — build :osmscout-client-java:assemble for descriptors)\n'
printf 'override       %s\n\n' "$java_file"

failures=0
while read -r count name; do
  [ -n "$name" ] || continue
  if [ -n "$name_filter" ] && [ "$name" != "$name_filter" ]; then continue; fi

  short_hits=$(grep -c "^${CLASS_PREFIX}${name}(" "$cpp_file" || true)
  long_hits=$(grep -c "^${CLASS_PREFIX}${name}__" "$cpp_file" || true)
  long_names=$(grep -o "^${CLASS_PREFIX}${name}__[A-Za-z0-9_]*" "$cpp_file" | sort -u | tr '\n' ' ' || true)

  if [ "$count" -gt 1 ]; then
    mode="overloaded -> LONG symbol (a signature change fails loudly)"
  else
    mode="single    -> SHORT symbol (a signature change is SILENT)"
  fi

  verdict=""
  if [ "$count" -gt 1 ]; then
    if [ "$long_hits" -gt 0 ]; then
      verdict="resolves via long name(s): $long_names"
    elif [ "$short_hits" -gt 0 ]; then
      verdict="WARNING: only a short symbol exists for an overloaded name — the JVM will not resolve it"
      failures=$((failures + 1))
    else
      verdict="MISSING: no symbol in the .cpp (UnsatisfiedLinkError)"
      failures=$((failures + 1))
    fi
  else
    if [ "$short_hits" -gt 0 ]; then
      verdict="resolves via short name"
      [ "$long_hits" -gt 0 ] && verdict="$verdict; also carries dead long name(s): $long_names"
    elif [ "$long_hits" -gt 0 ]; then
      verdict="resolves via long-name fallback only: $long_names (short name absent — presence ≠ match)"
    else
      verdict="MISSING: no symbol in the .cpp (UnsatisfiedLinkError)"
      failures=$((failures + 1))
    fi
  fi

  printf '%-28s decls=%s  %s\n' "$name" "$count" "$mode"
  printf '%-28s %s\n' "" "$verdict"

  if [ "$with_descriptor" -eq 1 ]; then
    if [ -n "$jar" ]; then
      desc=$(javap -s -p -classpath "$jar" "$JAVA_CLASS" 2>/dev/null |
        awk -v n="$name" '
          $0 ~ ("[^A-Za-z0-9_]" n "\\(") { want = 1; next }
          want && /descriptor:/ { sub(/^[[:space:]]*descriptor:[[:space:]]*/, ""); print; exit }')
      [ -n "$desc" ] && printf '%-28s descriptor %s\n' "" "$desc"
    fi
  fi
  printf '\n'
done <<< "$decls"

if [ -n "$name_filter" ] && ! grep -qE "[[:space:]]${name_filter}\$" <<< "$decls"; then
  echo "note: '$name_filter' is not declared native in $java_file" >&2
  exit 2
fi

if [ "$failures" -gt 0 ]; then
  echo "FAIL: $failures method(s) would not resolve at call time"
  exit 1
fi

echo "OK: every audited native method has a symbol the JVM can resolve"
