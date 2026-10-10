# Pair a vanished capability key with the key that replaced it, so a renamed requirement is reported as a
# change rather than as a removal and an addition.
#
# Invoked as:
#   awk -f pair.awk <removed.tsv> <added.tsv>          # each line: <spec id> \t <requirement name>
#
# Output, one line per removed key:
#   <spec id>#<old name> \t <spec id>#<new name>   the two are the same capability, renamed
#   <spec id>#<old name> \t -                      nothing in that spec looks like its replacement
#
# The snapshot deliberately keeps only a digest per capability, so the old requirement *text* is not available
# to compare — the decision is made on the requirement NAMES, which the key itself carries. A pairing needs at
# least one content word (four characters or more) in common AND that word to cover half of the longer name, so
# a short name survives a rename and two unrelated names in one spec are not paired.

function lower(s) { return tolower(s) }

function words(s,   out) {
  out = lower(s)
  gsub(/[^a-z0-9]+/, " ", out)
  return out
}

# content words of a name: four characters or more, stopwords excluded
function content(s,   i, n, w, t) {
  n = split(words(s), W, / +/)
  t = ""
  for (i = 1; i <= n; i++) {
    w = W[i]
    if (length(w) < 4) continue
    t = t (t == "" ? "" : " ") w
  }
  return t
}

function count(t,   n) { return t == "" ? 0 : split(t, TMP, / +/) }

function shared(a, b,   i, j, na, nb, cnt) {
  na = split(content(a), A, / +/)
  nb = split(content(b), B, / +/)
  cnt = 0
  for (i = 1; i <= na; i++) {
    for (j = 1; j <= nb; j++) {
      if (A[i] == B[j]) { cnt++; break }
    }
  }
  return cnt
}

NR == FNR {
  rn++
  rspec[rn] = $1
  rname[rn] = $2
  next
}

{
  aspec = $1
  aname = $2
  best = 0
  besti = 0
  for (i = 1; i <= rn; i++) {
    if (paired[i]) continue
    if (rspec[i] != aspec) continue
    s = shared(rname[i], aname)
    if (s > best) { best = s; besti = i }
  }
  if (besti > 0) {
    ca = count(content(rname[besti]))
    cb = count(content(aname))
    longest = ca > cb ? ca : cb
    # at least one shared content word, covering half of the longer of the two names
    if (best > 0 && best * 2 >= longest) {
      paired[besti] = 1
      pairedwith[besti] = aname
    }
  }
}

END {
  for (i = 1; i <= rn; i++) {
    if (paired[i]) printf "%s#%s\t%s#%s\n", rspec[i], rname[i], rspec[i], pairedwith[i]
    else           printf "%s#%s\t-\n", rspec[i], rname[i]
  }
}
