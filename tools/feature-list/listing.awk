# listing.awk — the store listing: one version entry, ordered by user impact and fitted to the store field.
#
# The input is the version entry the release note just wrote and gated — `## <version>`, its `### <area>` headings
# and their bullets — and the output is that version's listing body: one bullet per line, each followed by the
# `<!-- cap: … -->` lines it carried. Nothing else is published; the headings and the provenance comments are not
# what a store field carries.
#
# The budget is the characters of the published bullets including their newlines, and the comments are counted in
# neither. Order is impact, then the entry's own order: a bullet whose spec the shortlist names is a selling point
# a user can see or feel, and ranks above one it does not. The fit keeps whole bullets, then the whole leading
# sentences of a bullet, and never a comment — a `<!-- cap: … -->` line is emitted complete or not at all. What
# does not fit is summarised under one general bullet with the count of the changes it stands for, so a change is
# trimmed, or summarised, and never dropped in silence.
#
# Usage: awk -v budget=<chars> -v shortlist=<file> [-v statusfile=<file>] -f listing.awk <entry>
#   statusfile receives one line: chars=<n> published=<n> trimmed=<n> summarised=<n>
#
# Exit status: 0 ok, 2 a missing argument. It reads no project file but the shortlist it is given.

BEGIN {
  if (budget == "" || budget !~ /^[0-9]+$/ || budget < 1) {
    print "listing.awk: -v budget=<characters> is required" > "/dev/stderr"
    failed = 1
    exit 2
  }
  if (shortlist == "") {
    print "listing.awk: -v shortlist=<file> is required" > "/dev/stderr"
    failed = 1
    exit 2
  }
  while ((getline line < shortlist) > 0) if (line != "") named[line] = 1
  close(shortlist)
  n = 0
  ncap = 0
  cur = ""
}

function cap_key(s) {           # `  <!-- cap: <spec>#<requirement> -->` -> `<spec>#<requirement>`
  sub(/^[[:space:]]*<!--[[:space:]]*cap:[[:space:]]*/, "", s)
  sub(/[[:space:]]*-->.*$/, "", s)
  return s
}

function flush(   i) {
  if (cur == "") {
    ncap = 0
    return
  }
  n++
  text[n] = cur
  ncapof[n] = ncap
  key[n] = (ncap > 0) ? capkey[1] : ""
  for (i = 1; i <= ncap; i++) cap[n, i] = capl[i]
  ncap = 0
  cur = ""
}

# A bullet may wrap its paragraph over several lines; the published listing prints it as one line, which is the
# shape the entry itself is generated in. A comment line ends nothing: it belongs to the bullet before it.
/^[[:space:]]*<!--[[:space:]]*cap:/ { capl[++ncap] = $0; capkey[ncap] = cap_key($0); next }
/^- / { flush(); cur = $0; next }
/^#/ { flush(); next }
/^[[:space:]]*$/ { flush(); next }
{ if (cur != "") cur = cur " " $0 }

END {
  if (failed) exit 2
  flush()

  # Impact first: a bullet whose spec the shortlist names is one the product tells a user about. The entry's own
  # order settles both groups, so the listing is a stable function of the entry and the shortlist.
  for (i = 1; i <= n; i++) {
    spec = key[i]
    sub(/#.*/, "", spec)
    rank[i] = (spec != "" && (spec in named)) ? 0 : 1
  }

  used = 0; out = 0; trimmed = 0; summarised = 0
  for (p = 0; p <= 1; p++) {
    for (i = 1; i <= n; i++) {
      if (rank[i] != p) continue
      t = text[i]
      if (used + length(t) + 1 <= budget) {
        out++
        pick[out] = i
        cut[out] = t
        width[out] = length(t) + 1
        used += width[out]
        continue
      }
      # Whole leading sentences, and only those: a bullet is never cut mid-sentence, so a store field that
      # cannot hold the whole of it holds the part that still reads.
      keep = fit_sentences(t, budget - used - 1)
      if (keep > 0) {
        out++
        pick[out] = i
        cut[out] = substr(t, 1, keep)
        width[out] = keep + 1
        used += width[out]
        trimmed++
        continue
      }
      summarised++
    }
  }

  # The summary stands for every change that did not fit and makes room for itself from the end of the list: the
  # last change published is the one a reader loses when the general bullet arrives, and it is counted there.
  while (summarised > 0) {
    summary = summary_line(summarised)
    if (used + length(summary) + 1 <= budget) break
    if (out == 0) break
    used -= width[out]
    out--
    summarised++
  }

  for (j = 1; j <= out; j++) {
    print cut[j]
    i = pick[j]
    for (k = 1; k <= ncapof[i]; k++) print cap[i, k]
  }
  if (summarised > 0) print summary_line(summarised)

  if (statusfile != "") {
    total = used
    published = out
    if (summarised > 0) {
      total += length(summary_line(summarised)) + 1
      published++
    }
    printf "chars=%d\npublished=%d\ntrimmed=%d\nsummarised=%d\n", total, published, trimmed, summarised > statusfile
    close(statusfile)
  }
}

# The greatest prefix of `t` that ends at a sentence boundary — a `.` that ends the text or is followed by a space —
# and fits in `room` characters. `0` when not even the first sentence fits. The prefix always reaches past the
# bold keyword, so a shortened bullet still leads with what its sentence is about.
function fit_sentences(t, room,   i, best, kw_end, len) {
  len = length(t)
  kw_end = index(t, "**")
  if (kw_end > 0) kw_end = kw_end + index(substr(t, kw_end + 2), "**") + 1
  best = 0
  for (i = 1; i <= len && i <= room; i++) {
    if (substr(t, i, 1) != ".") continue
    if (i < len && substr(t, i + 1, 1) != " ") continue
    if (i <= kw_end) continue
    best = i
  }
  return best
}

# One general bullet for everything the field could not carry. It names no capability, so it carries no comment.
function summary_line(count) {
  return "- **General improvements and optimisations** — " count (count == 1 ? " further change." : " further changes.")
}
