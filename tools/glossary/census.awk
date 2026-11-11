# census.awk — one pass over the three main source sets, no judgement: every identifier occurrence of every
# Kotlin and Java file, with the token it belongs to, the spelling it is written in, the role it was written in
# and the file:line it sits at (`domain-vocabulary`, "Surfaces are declared per grammatical role"; design D1).
#
# Input: file names on argv, written by check-glossary.sh from the roots and extensions in tools/glossary/index.json.
# Output: one TSV line per record — token, spelling, role, file:line, flags — sorted by the caller.
#   token    the spelling lowercased, so `Basemap`, `BaseMap` and `baseMap` are one token with three spellings
#            and `KmH`/`Kmh` are one token whose spellings decide the split
#   spelling the spelling exactly as written
#   role     `type`, `constant`, `property`, `operation` for the name a declaration introduces on that line,
#            `component` for the head or tail word of a compound identifier, `use` for every other occurrence
#   flags    `bareboolean` on a property declared `: Boolean` whose name carries no is/has/can prefix
#
# Two records are written for a compound identifier: the whole identifier (`basemapviewmodel`), which is what a
# row's own surface matches when it declares a name no camel case splits, and each of its camel-case words
# (`basemap`, `viewmodel`), which is what a term is observed as when it is written at the head of a type name
# (`BasemapViewModel`, `BasemapDataFormat`) or as a unit suffix (`speedKmH`, `maxSpeedKmH`). A one-word
# identifier is written once, so no occurrence is counted twice. That is the measurement the proposal took:
# `Basemap` 511 is the head word of 511 identifiers, and the `KmH`/`Kmh` families are suffix occurrences.
#
# A comment and a string literal are not code: `//`, `/* */` and a double-quoted span are stripped before the
# identifiers are read, in that order, because a word in a comment is prose and a word in a string is data.
# No judgement is taken here — no lexicon, no anchor, no row is read. check-glossary.sh decides what the counts mean.

function strip_comments(line,    out, i, c, n, quote, esc) {
  out = ""; quote = 0; esc = 0
  n = length(line)
  for (i = 1; i <= n; i++) {
    c = substr(line, i, 1)
    if (inblock) {
      if (c == "*" && substr(line, i + 1, 1) == "/") { inblock = 0; i++ }
      continue
    }
    if (quote) {
      if (esc) { esc = 0; continue }
      if (c == "\\") { esc = 1; continue }
      if (c == "\"") { quote = 0 }
      out = out " "
      continue
    }
    if (c == "/" && substr(line, i + 1, 1) == "/") break
    if (c == "/" && substr(line, i + 1, 1) == "*") { inblock = 1; i++; continue }
    if (c == "\"") { quote = 1; out = out " "; continue }
    out = out c
  }
  return out
}

function declared_name(pat, line,    m) {
  if (!match(line, pat)) return ""
  m = substr(line, RSTART, RLENGTH)
  sub(/[^A-Za-z0-9_]+$/, "", m)
  sub(/^.*[^A-Za-z0-9_]/, "", m)
  return m
}

# split a camel-case identifier into its words: an upper-case letter that follows a lower-case letter or a
# digit starts a word, and so does the last capital of an acronym run before a lower-case letter.
function camel(text, parts,    n, i, c, prev, cur) {
  n = 0; cur = ""
  for (i = 1; i <= length(text); i++) {
    c = substr(text, i, 1)
    if (cur != "" && c ~ /[A-Z]/ && prev ~ /[a-z0-9]/) { parts[++n] = cur; cur = "" }
    cur = cur c; prev = c
  }
  if (cur != "") parts[++n] = cur
  return n
}

BEGIN { inblock = 0 }

FNR == 1 { inblock = 0 }

{
  line = strip_comments($0)
  where = FILENAME ":" FNR

  tname = declared_name("(^|[^A-Za-z0-9_])(class|interface|object)[[:space:]]+[A-Za-z_][A-Za-z0-9_]*", line)
  cname = declared_name("(^|[^A-Za-z0-9_])const[[:space:]]+val[[:space:]]+[A-Za-z_][A-Za-z0-9_]*", line)
  pname = declared_name("(^|[^A-Za-z0-9_])(val|var)[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*:", line)
  oname = declared_name("(^|[^A-Za-z0-9_])fun[[:space:]]+[A-Za-z_][A-Za-z0-9_]*", line)

  bare = 0
  if (pname != "" && line ~ /Boolean/ && pname !~ /^(is|has|can)[A-Z]/) bare = 1

  rest = line
  while (match(rest, /[A-Za-z_][A-Za-z0-9_]*/)) {
    id = substr(rest, RSTART, RLENGTH)
    role = "use"
    if (id == tname) role = "type"
    else if (id == cname) role = "constant"
    else if (id == pname) role = "property"
    else if (id == oname) role = "operation"
    flags = ""
    if (role == "property" && bare) flags = "bareboolean"
    if (flags == "") flags = "-"
    token = tolower(id)
    printf "%s\t%s\t%s\t%s\t%s\twhole\n", token, id, role, where, flags
    n = camel(id, part)
    if (n > 1) {
      for (k = 1; k <= n; k++) {
        prole = "component"
        if (k == 1 && role != "use") prole = role
        printf "%s\t%s\t%s\t%s\t%s\tpart\n", tolower(part[k]), part[k], prole, where, flags
      }
      split("", part)
    }
    rest = substr(rest, RSTART + RLENGTH)
  }
}
