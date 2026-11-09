# listing.awk — render one version entry of the release note into one store-listing block.
#
# The entry's bullets are the whole input: each is published as one line, its keyword kept and its markdown and
# capability comment dropped, because a store field renders no markdown and a comment is not text a user reads.
# The budget is the field's character count, so the block is trimmed by whole bullets — never inside a paragraph
# and never inside a comment — and the changes that do not fit are named by one general line instead.
#
# Ranking: a bullet whose spec is a shortlisted selling point ranks above one whose is not, the shortlist's own
# order breaking ties and the entry's order after that. That is the impact signal the tree already carries: a
# selling point is the shortlist's own claim that a user can see or feel the capability.
#
# Invoked as: awk -v rankfile=... -v budget=... -v summary=... -v counts=... -f listing.awk <entry>
# Writes the block's lines to stdout and `shown=`, `bullets=` and `chars=` to the counts file.
#
# Reads text files only: no build, no device, no network (openspec/config.yaml).

BEGIN {
  while ((getline line < rankfile) > 0) {
    n = split(line, f, "\t")
    if (n >= 2 && !(f[2] in rank)) rank[f[2]] = f[1] + 0
  }
  close(rankfile)
  nbul = 0
}

# One bullet block ends: `text` is the bullet's body without its `- `, `keys` the capability keys its comment
# lines named. The impact rank is the best rank among those keys' specs, and 99999 is a capability no selling
# point names.
function flush(   t, k, rest, j, after, i, best, spec) {
  if (text == "") return
  t = text
  text = ""
  line = t
  k = index(t, "**")
  if (k > 0) {
    rest = substr(t, k + 2)
    j = index(rest, "**")
    if (j > 0) {
      after = substr(rest, j + 2)
      sub(/^[[:space:]]*—[[:space:]]*/, "", after)
      line = "- " substr(rest, 1, j - 1) " — " after
    }
  }
  best = 99999
  for (i = 1; i <= nkey; i++) {
    spec = keys[i]
    sub(/#.*$/, "", spec)
    if ((spec in rank) && rank[spec] < best) best = rank[spec]
  }
  nbul++
  bullet[nbul] = line
  order[nbul] = best
  nkey = 0
}

/^- / { flush(); text = substr($0, 3); next }
/^[ \t]*<!-- cap:/ {
  if (text == "") next
  k = $0
  sub(/^[ \t]*<!-- cap:[ \t]*/, "", k)
  sub(/[ \t]*-->.*$/, "", k)
  keys[++nkey] = k
  next
}
/^#/ { flush(); next }
{ if (text != "") text = text " " $0 }

END {
  flush()
  # Ranked order, the entry's own order breaking ties. Selection sort: a release carries tens of bullets.
  for (i = 1; i <= nbul; i++) pick[i] = i
  for (i = 1; i <= nbul; i++) {
    m = i
    for (j = i + 1; j <= nbul; j++) if (order[pick[j]] < order[pick[m]]) m = j
    t = pick[i]; pick[i] = pick[m]; pick[m] = t
  }
  # The summary line is reserved first, so a block that has to summarise still fits the field.
  detail = budget - length(summary) - 1
  total = 0
  shown = 0
  for (i = 1; i <= nbul; i++) {
    l = bullet[pick[i]]
    add = length(l) + (shown > 0 ? 1 : 0)
    if (total + add > detail) break
    total += add
    shown++
    out[shown] = l
  }
  for (i = 1; i <= shown; i++) print out[i]
  chars = total
  if (shown < nbul) {
    print summary
    chars += (shown > 0 ? 1 : 0) + length(summary)
  }
  report = sprintf("shown=%d\nbullets=%d\nchars=%d\n", shown, nbul, chars)
  printf "%s", report > counts
  close(counts)
}
