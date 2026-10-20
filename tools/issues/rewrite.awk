# rewrite.awk — replace one citation token in a body, boundary-checked, all occurrences
# (`stale-citation-resolution`, D4/D7).
#
# Two things matter and neither is a convenience: the replacement is **literal** (awk, with the old and new
# values in `-v`, so no regex and no escape processing — a path is a path), and it happens only where the old
# token stands alone, exactly as the extractor defines a citation. Everything else on the line — prose, the
# `fingerprint` and `nvid` markers, whitespace — is copied through untouched, which is what makes the repair
# reviewable at a glance.
#
# `standalone=1` tightens the left boundary by one character. `/` has to be a boundary for a full path —
# `old/Place.kt` follows a separator in prose — but a *basename* repair that rewrote a `/`-preceded occurrence
# rewrites the tail of a longer citation, giving it a second copy of a directory the absent loop then collapses
# back, and the two rewrites cancel (#200). The suffix loop passes it; the absent loop, which writes full paths,
# does not.
{
  text = $0; out = ""; start = 1
  while (start <= length(text)) {
    rest = substr(text, start)
    p = index(rest, old)
    if (p == 0) { out = out rest; break }
    i = start + p - 1
    l = (i == 1) ? "" : substr(text, i - 1, 1)
    r = (i + length(old) > length(text)) ? "" : substr(text, i + length(old), 1)
    if (l ~ /[A-Za-z0-9_.-]/ || (standalone && l == "/") || r ~ /[A-Za-z0-9_]/) {
      out = out substr(text, start, p)
      start = i + 1
      continue
    }
    out = out substr(text, start, i - start) new
    start = i + length(old)
  }
  print out
}
