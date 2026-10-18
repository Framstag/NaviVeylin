# citations.awk — extract path-shaped citation tokens from a text stream (`stale-citation-resolution`, D2).
#
# One match at a time, because the two rules that make this true rather than plausible need the characters
# *around* the match: a token is a citation only when it is not preceded by `[A-Za-z0-9_.-]` (so the tail of
# `-Dorg.gradle.java.home` is not one) and not followed by `[A-Za-z0-9_]` (so `Dorg.gradle.java.h` is not one
# either, though it ends in a known extension). The pattern itself is handed in as `pattern`; `print tok`
# emits one candidate per line, and the caller decides which of them are paths (`headerExtensions`).
{
  text = $0
  start = 1
  while (start <= length(text)) {
    rest = substr(text, start)
    if (match(rest, pattern) == 0) break
    i = start + RSTART - 1
    tok = substr(text, i, RLENGTH)
    start = i + RLENGTH
    l = (i == 1) ? "" : substr(text, i - 1, 1)
    r = (i + RLENGTH > length(text)) ? "" : substr(text, i + RLENGTH, 1)
    if (l ~ /[A-Za-z0-9_.-]/) continue
    if (r ~ /[A-Za-z0-9_]/) continue
    print tok
  }
}
