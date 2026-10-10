# The prose gates for the feature catalogue.
#
# Invoked as:
#   awk -f gate.awk -v keyfile=<resolved keys out> <spec-text.tsv> <catalogue>
#
# Input 1 — one line per spec: `<spec id> \t <1|0 user-visible> \t <the spec's text, one line>`.
# Input 2 — the assembled catalogue.
#
# An ENTRY is what the catalogue is made of: a line starting `- `, its continuation lines (a paragraph may wrap),
# the bold keyword it must lead with, and the source-marker lines that follow it. The entry ends at a blank line,
# a heading, or the next entry.
#
#   - **Works without a connection** — Download the maps you need and browse them offline…
#     <!-- cap: basemap-download#Basemap archive downloaded -->
#     <!-- cap: basemap-ui#Basemap state shown in the map manager -->
#
# What it enforces, per entry:
#   * the entry leads with a bold keyword of at most six words, and that keyword's content words appear in the
#     specs the entry cites, so the scan word is the product's word and not an invention;
#   * the entry carries a source marker, and every key in it names a spec that exists and is user-visible;
#   * every figure the entry states appears in at least one spec the entry cites — a failure;
#   * every content word that appears in none of those specs — a report, not a failure, because a claim the gate
#     cannot settle should be visible to a reviewer rather than silently accepted.
#
# The marker holds ONE KEY PER LINE, repeated as needed. A comma-separated list would be ambiguous: requirement
# names in this project contain commas, so a reader could not tell a separator from a name.
#
# Exit 0 when no entry fails, 1 when one does. Reported words go to stderr; failures to stdout; the keys that
# resolved are appended to `keyfile`, which the caller uses for the coverage check.

function flush_entry(   n, i, k, p, s, rest, m, w, low, kw, kwlen, kww, kwwl, isgone, para, q) {
  if (pending == "") return
  if (!markers_seen) {
    printf "entry carries no source marker (line %d): %s\n", pline, substr(pending, 1, 120)
    FAIL = 1
  }
  # An entry whose subject has left the shipped specs: a removal in a release note names a capability that is no
  # longer there, so its keyword and its words cannot be checked against the specs they came from — its source is
  # the baseline snapshot, not the current specs. It is still held to the marker and the keyword-shape rules.
  isgone = 0
  for (k in entrykeys) if (k in vanished) isgone = 1
  if (ok && !isgone) {
    # the keyword: bold, at most six words, drawn from the cited specs
    p = index(pending, "**")
    if (p == 0) {
      printf "entry does not lead with a bold keyword (line %d): %s\n", pline, substr(pending, 1, 120)
      FAIL = 1
    } else {
      kw = substr(pending, p + 2)
      q = index(kw, "**")
      if (q == 0) {
        printf "entry keyword is not closed (line %d): %s\n", pline, substr(pending, 1, 120)
        FAIL = 1
      } else {
        kw = substr(kw, 1, q - 1)
        gsub(/^[[:space:]]+/, "", kw); gsub(/[[:space:]]+$/, "", kw)
        # the paragraph is everything after the keyword's closing **, and it is what the length bound measures
        para = substr(pending, p + q + 3)
        gsub(/^[[:space:]]+/, "", para); gsub(/[[:space:]]+$/, "", para)
        if (length(para) > maxpara) {
          printf "entry paragraph is %d characters, more than the %d allowed (line %d): %s\n", \
            length(para), maxpara, pline, substr(pending, 1, 120)
          FAIL = 1
        }
        kwlen = split(kw, KW, /[^A-Za-z0-9]+/)
        n = 0
        for (i = 1; i <= kwlen; i++) if (KW[i] != "") n++
        if (n == 0) {
          printf "entry keyword is empty (line %d)\n", pline
          FAIL = 1
        } else if (n > 6) {
          printf "entry keyword is %d words, more than six (line %d): %s\n", n, pline, kw
          FAIL = 1
        }
        low = tolower(union)
        for (i = 1; i <= kwlen; i++) {
          kww = KW[i]
          kwwl = tolower(kww)
          if (length(kwwl) < 4) continue
          if (kwwl in stop) continue
          if (index(low, kwwl) == 0) {
            # the keyword is the entry's promise, so an invented word in it is a false claim, not a matter of tone
            printf "keyword word in none of the cited specs: %s | %s\n", kww, kw
            FAIL = 1
          }
        }
      }
    }
    low = tolower(union)
    rest = pending
    while (match(rest, /[0-9]+/)) {
      d = substr(rest, RSTART, RLENGTH)
      rest = substr(rest, RSTART + RLENGTH)
      if (index(low, tolower(d)) == 0) {
        printf "figure in none of the cited specs: %s | %s\n", d, substr(pending, 1, 120)
        FAIL = 1
      }
    }
    rest = tolower(pending)
    gsub(/[^a-z0-9]+/, " ", rest)
    m = split(rest, W, / +/)
    for (i = 1; i <= m; i++) {
      w = W[i]
      if (length(w) < 4) continue
      if (w in stop) continue
      if (index(low, w) == 0) printf "unsourced word: %s | %s\n", w, substr(pending, 1, 120) > "/dev/stderr"
    }
  }
  pending = ""; markers_seen = 0; union = ""; seen = "|"; ok = 1
  # the specs this entry cited, for the caller's shortlist checks: `<start line> \t <spec> <spec> …`
  if (entryspecfile != "" && nspecs > 0) {
    printf "%d\t", pline > entryspecfile
    for (i = 1; i <= nspecs; i++) printf "%s%s", cited[i], (i < nspecs ? " " : "\n") > entryspecfile
  }
  delete entrykeys; delete cited; nspecs = 0
}

BEGIN {
  split("the and for with that this from your you are not can will all any does how into its more most only other over same should than then there these they those very what when where which while without would about after again against because been before being below between both but did doing down during each few further has have having here once out own such their them under until was were", S, " ")
  for (i in S) stop[S[i]] = 1
  ok = 1
  if (vanishedfile != "") {
    while ((getline line < vanishedfile) > 0) if (line != "") vanished[line] = 1
    close(vanishedfile)
  }
}

NR == FNR { spec[$1] = $3; vis[$1] = $2; next }

/^- / {
  flush_entry()
  pending = substr($0, 3); pline = NR; seen = "|"
  next
}

/^[[:space:]]*<!-- cap:/ {
  if (pending == "") next
  k = $0
  sub(/^[[:space:]]*<!-- cap:[[:space:]]*/, "", k)
  sub(/[[:space:]]*-->[[:space:]]*$/, "", k)
  if (k == "" || index($0, "-->") == 0) {
    printf "marker is empty or unterminated (line %d)\n", NR
    FAIL = 1
    next
  }
  markers_seen = 1
  p = index(k, "#")
  if (p == 0) { printf "source key has no spec id: %s\n", k; ok = 0; next }
  s = substr(k, 1, p - 1)
  if (!(s in spec)) { printf "source key cites a spec that does not exist: %s\n", k; ok = 0; next }
  if (vis[s] != "1") { printf "source key cites a spec that is not user-visible: %s\n", k; ok = 0; next }
  print k > keyfile
  entrykeys[k] = 1
  if (index(seen, "|" s "|") == 0) { seen = seen s "|"; union = union " " spec[s]; cited[++nspecs] = s }
  next
}

/^#/ { flush_entry(); next }

/^[[:space:]]*$/ { flush_entry(); next }

{ if (pending != "") pending = pending " " $0 }

END {
  flush_entry()
  exit FAIL
}
