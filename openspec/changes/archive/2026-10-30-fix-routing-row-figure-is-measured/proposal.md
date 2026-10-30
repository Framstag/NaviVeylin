# Proposal — fix-routing-row-figure-is-measured

## Why

**Observed (#712, verified in the tree at base `2f954a04`).** `tools/check-doc-routes.sh:193` reads a routing
row's cost as **presence** and stops there:

```
printf '%s' "$row" | grep -qE '[0-9]+([.,][0-9]+)?[[:space:]]?kB' && continue
```

So a row that names a lettered sub-section and states *a* `kB` figure passes, whatever the figure is. Verified
on a throwaway fixture whose only section measures 1 012 bytes by the requirement's own ruler and whose row
states `0.1 kB` (`evidence/measure-the-rows.txt`, the fifth block):

```
$ bash tools/check-doc-routes.sh --root <fixture>
check-doc-routes: all 1 section references resolve
   exit 0 — 0 means the 0.1 kB figure passed, which is the defect this change closes
```

**Consequence — the tree already carries the drift.** Nine rows price lettered sub-sections, six with eleven
single figures and three with a range. The eleven single figures are true today; **two of the three ranges are
not** (`evidence/measure-the-rows.txt`, blocks two to four):

| row | states | its lettered sections measure |
|---|---|---|
| `Build.md` §2/§4 index | `1.7–5.3 kB per section` | `1.9 kB` (§2e, 1 904 bytes) – `7.0 kB` (§4f, 7 016 bytes) |
| `Build.md` §6a–§6h | `1.1–5.3 kB each` | `1.3 kB` (§6g, 1 336 bytes) – `5.3 kB` (§6b, 5 271 bytes) |
| `Build.md` §10a–§10k | `0.9–6.8 kB each` | `0.9 kB` (§10f, 913 bytes) – `6.8 kB` (§10i, 6 841 bytes) — true |

The figure is the half of the rule a reader acts on — it is how a section is chosen — and it is the half no
check can convict. Presence-only was the deliberate first step of #197 (a restated number goes stale by hand);
the restated number is now checkable against the same ruler `--sizes` already prints, so the step is finished
here rather than left as the shape that lets `0.1 kB` pass.

**Root cause, one sentence.** `tools/check-doc-routes.sh` matches a row's `kB` figure with a regular expression
and never compares it with the section cost `--sizes` computes, so the figure a reader chooses a section by is
the one number in the row that nothing measures.

**The red test.** `evidence/red-probe.sh` drives the real check over a throwaway fixture tree and runs two
cases — the figure beside a reference and the range over a row's references. On the check the base commit carries
(`evidence/red-on-head.sh` extracts it from `2f954a04` and runs the probe against it; `evidence/red-on-head.xml`,
`evidence/red-on-head.log`, `timestamp="2026-10-30T06:59:57Z"`):

```
FAIL routed-read#figure — a row stating 0.1 kB for a 1065-byte section was not reported: exit 0: check-doc-routes: all 1 section references resolve
FAIL routed-read#range — a row stating the range 1.2–2.0 kB for sections measuring 1.1–3.1 kB was not reported: exit 0: check-doc-routes: all 2 section references resolve
red-probe: 2 of 2 cases failed
```

`tests=2 failures=2 errors=0`. Each case asserts both sides — the figure that disagrees is reported with the
row, the reference and both numbers, and the figure that *is* the measurement passes — so neither can be
satisfied by reporting every priced row.

**Owning capability and guideline section.** Capability `documentation-ownership`, which already owns "The
routing cost is measured" and its "The route names the sub-section" scenario: the clause being widened is the
same clause's second half. The guideline that states the rule is `guidelines/Backlog.md` §9, lines 990-994,
which says in as many words that "the figure is read as presence and not as accuracy".

**Surface.** Six files assert or carry the rule being changed — the check, its self-test, its mutation set, the
entry document's sentence and two of its rows, and `Backlog.md` §9 — and two of the nine priced rows state a
figure the tree does not measure:

```
$ grep -rln 'states no cost\|prices nothing\|presence and\|not as accuracy\|states what those sections cost\|state its cost' AGENTS.md guidelines tools openspec/specs | sort
AGENTS.md
guidelines/Backlog.md
tools/check-doc-routes-mutations.json
tools/check-doc-routes-selftest.sh
tools/check-doc-routes.sh
$ awk '/^## Documentation Map$/{f=1;next} f && /^## /{exit} f{print}' AGENTS.md | grep -cE '§[0-9]+[a-z]'   # rows naming a lettered sub-section
10          # nine table rows plus the paragraph that introduces the table
$ awk '/^## Documentation Map$/{f=1;next} f && /^## /{exit} f{print}' AGENTS.md | grep -oE '[0-9]+([.,][0-9]+)?[ ]?kB' | wc -l   # the figures they state
18          # eleven single figures, three ranges, four past measurements
```

surface: 2 cases added, 1 case realigned (`routed-read#route`'s fixture priced its section at a made-up 1.2 kB
and now states the measurement), 2 mutations added, 1 mutation realigned (`section-cost-ruler`'s line moved with
the cost block it falsifies), 11 single figures verified, 2 stale ranges realigned, 1 guideline paragraph
rewritten, 1 entry-document sentence widened, 1 requirement ADDED — 6 files touched plus this change's artifacts.

## What Changes

- **`tools/check-doc-routes.sh`** — the section costs `--sizes` prints are computed once, before the row
  clauses (they are the ruler both clauses need), and a fifth clause compares each row's stated cost with them:
  a figure written after a lettered reference must equal that section's cost in kB of bytes/1000 rounded to one
  decimal, and a range must state the cheapest and the costliest of the lettered sections the row names. A
  disagreement is reported with the row's label, the reference, the stated figure and the measured one, and the
  check exits 1. A figure the row writes as a past measurement (`against 23.2 kB for §2 before the split`) is not
  one of its figures and is never compared. The header comment and the `--help` range state the new clause.
- **`tools/check-doc-routes-selftest.sh`** — two cases over the project's own tree's shape:
  `routed-read#figure` (a fixture row's figure beside a reference is the section's measurement; a disagreeing
  figure is convicted with both numbers, the measurement passes) and `routed-read#range` (a range is the span of
  the lettered sections the row names; a wrong span is convicted, the measured span passes, and a past
  measurement in the same row is not compared). Both cases also run over the project's own table, which must
  pass.
- **`tools/check-doc-routes-mutations.json`** — two records, each inverting one of the two comparisons so the
  new clause is falsifiable rather than merely asserted.
- **`AGENTS.md`** — the two stale ranges realigned to their measurement (`1.9–7.0 kB per section`, `1.3–5.3 kB
  each`) and the Documentation Map's own sentence widened from "prices nothing" to "prices nothing, or prices a
  section at a figure its measurement does not give".
- **`guidelines/Backlog.md` §9** — the presence-not-accuracy sentence replaced by the compared rule: what the
  clause reads, the two forms (a figure beside a reference, a range as the span), the one-decimal ruler, and that
  a past measurement is not compared.
- **`documentation-ownership`** — one requirement is ADDED, "A routing row's cost is the measurement it claims",
  with two scenarios, each naming one of the two cases. No shipped scenario is dropped: the requirement "The
  routing cost is measured" and its "The route names the sub-section" scenario are untouched, and the new
  requirement is the accuracy half that clause left open.
- **Additive, no breaking change.** No compiled file, no Kotlin, no C++, no Gradle logic, no manifest, no
  resource, no device: the whole change is a text-only check, its self-test, its mutation set and the prose that
  states the rule. Rollback path: revert this change's commit — the clause, the two cases, the two mutations, the
  two realigned ranges, the widened sentence and the §9 paragraph go together, and the check returns to reading
  a figure's presence with the two stale ranges as they were.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `documentation-ownership`: one requirement is ADDED — "A routing row's cost is the measurement it claims" —
  with two scenarios, each naming one case (`routed-read#figure`, `routed-read#range`). The delta lives at
  `specs/documentation-ownership/spec.md`, and it touches no shipped requirement: "The routing cost is measured"
  keeps every scenario it has, including the two that carry no case ("Numbers for representative changes", "An
  unmeasured saving is not claimed").

## Impact

The check, its cases and its falsification

- `tools/check-doc-routes.sh` — the costs computed once, the comparison clause, the header and `--help`
- `tools/check-doc-routes-selftest.sh` — `routed-read#figure` and `routed-read#range`, and the header's case list
- `tools/check-doc-routes-mutations.json` — two records, `route-figure-is-measured` and `route-range-is-the-span`

The documents that carry the rule

- `AGENTS.md` — the Documentation Map's sentence, and two rows' ranges
- `guidelines/Backlog.md` — §9, the routing-table paragraph

Specs and this change

- `openspec/specs/documentation-ownership/spec.md` — the ADDED requirement, applied when this change archives
- `openspec/changes/2026-10-30-fix-routing-row-figure-is-measured/**` — the planning artifacts and the evidence

Structural checks (no compiled file, no Gradle)

- `bash tools/check-doc-routes.sh` — every reference resolves and every stated figure is the measurement
- `bash tools/check-doc-routes-selftest.sh` — the check's whole suite, the two new cases included
- `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json` — every invariant goes red and is
  restored
- `bash tools/check-todo-references.sh`, `bash tools/check-openspec-hygiene.sh`,
  `bash tools/gen-feature-list.sh --check-classification` — the edited prose's own checks
- `./tools/loop/gate.sh` — the suites this change's pathspec owes
