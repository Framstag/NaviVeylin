# Design — fix-highlight-longest-run

## Root cause

`find_casing` (`tools/measure-highlight.py:57-100`) scans each row left to right and, at HEAD, kept only
one per-row fact: the run currently being scanned (`run`, and the run's `first`/`last`), which at the end of
the row is always the **last** run — either the one the row ends on (`run == last - first + 1`) or, when
the row ends on a gap, the previous run with `run` reset to 0 while `first`/`last` keep its extent. The row
test (HEAD `tools/measure-highlight.py:72-73`) therefore had no memory of any earlier, longer run:

```python
if run >= MIN_RUN_PX or (last - first + 1) >= MIN_RUN_PX   # both terms: the last run's length
```

Both terms measure the same thing (the second is redundant, not a span), so a row qualified only when its
*rightmost* run reached `MIN_RUN_PX`. §169's synthetic row — a 41 px casing run with a 5 px one to its
right — was therefore invisible: no highlight at all (exit 2), not merely a smaller box.

## THE single fix

One predicate, in the row scan: track the **longest** run of the row (its length and its extent) and use
that run for the qualification test, the count and the box.

- `find_casing` keeps `best`/`best_first`/`best_last` per row, updated when a run ends (`best` starts at 0
  and the final run is closed after the `x` loop, so the `width - 1` end of a row that finishes on a match
  is included).
- `if best >= MIN_RUN_PX:` replaces the two-term test; `count += best` and the box extend over
  `best_first`/`best_last`.
- `MIN_RUN_PX` is **not** raised — §169 forbids it, the script's own comment forbids it (`:48-53`, "must
  stay low enough to see one"), and `SKILL.md`'s closing note repeats it. The threshold keeps its meaning; only the
  quantity it is compared against changes from "the run the scan ended on" to "the row's longest run".
- The rule is stated where the code is: `find_casing`'s docstring, the `MIN_RUN_PX` comment, and the
  detector bullet in `.pi/skills/pixel-check/SKILL.md`.

Which run's extent a multi-run row contributes — the part §169 leaves to this change — is **the longest
run's**, because that is the run the row is qualified by; counting one run while boxing another would make
`px` and `bbox` describe different pixels. The harness case `longest-run-per-row` pins it.

## Blast radius

Callers of the detector, and what the fix does to each (found with
`grep -rn 'MIN_RUN_PX\|measure-highlight' tools/ .pi/skills/ guidelines/ openspec/specs/ openspec/config.yaml`):

| caller | effect |
|---|---|
| `tools/measure-highlight.py` itself (`main`, `verdict`) | unchanged interface: same JSON keys, same exit codes, same `verdict` wording |
| `.pi/skills/pixel-check/SKILL.md` (the on-device recipe; its verdict goes into `tasks.md`) | the recipe is unaffected; its dense-run bullet now names the longest-run rule and the new harness |
| `guidelines/Build.md` sections 2 and 11 (look-then-measure rule, precondition tells) | section 11 names the new device-free harness; the precondition advice (`band` above the canvas bottom, `px` must be a stroke) is unchanged and still necessary |
| `openspec/config.yaml` design guidance (names the tool's verdict as the on-device measurement) | unaffected |
| `.pi/skills/pixel-check/selftest.sh` (ImageMagick: inside / behind card / no highlight) | still green — re-run recorded in the change's evidence |
| `TODO.md` §147 (the tool's no-route false positive, on hold for a decision) | out of scope and untouched: its frame's rows each hold a single run (8 px and 10 px, both ≥ `MIN_RUN_PX`), and it was re-measured on the **fixed** tool only (`px=18`, `evidence/synthetic-samples-after-fix.txt`) |

Direction of the change, so a reader can bound it: a row the old rule qualified still qualifies (its
longest run is ≥ its rightmost run), so no row is ever lost and `px` never falls. The box's edges do not
move one way only: a newly counted row's longest run can lie further right than the old box reached, which
extends `x1` and can turn `inside` into a right-`CLIPPED` — **unmeasured** by this change's own runs
(reviewer round 1's fixture, 400×200 with `--band-bottom 200 --margin 60`: row y=70 a 9 px run x=70..78 and
row y=100 a 41 px run x=301..341 plus a 5 px run x=380..384, HEAD `bbox=[70, 78, 70, 70] px=9` / `inside`
exit 0 → fixed `bbox=[70, 341, 70, 100] px=50` / `CLIPPED at right` exit 1; the fixture is not retained
here). A row the old rule dropped can also lie nearer a margin than the old box did, so a verdict can read
`CLIPPED` where it previously read `inside` — that is the defect being fixed, not a regression, and it is
*arithmetic, not measured* (no harness case asserts it).

No Gradle-visible source, no Kotlin/Java, no app, Auto or native code: both distribution flavors are
untouched, and no test expectation in the Gradle suites moves (nothing there names the tool).

## Rollback

Revert the change commit. The detector's row predicate and the comments that state it revert together with
the harness; nothing outside `tools/measure-highlight.py` depends on the row rule, and the docs that name
the harness are reverted in the same commit.

## Alternatives the evidence eliminated

- **Keep the rightmost-run test and add a second test for a longer earlier run.** Eliminated: the row
  would then be qualified by one run and counted by another depending on which test fired, and the script's
  own stated intent ("requires a **dense run** of matching pixels per row", `:39`) becomes two rules. The
  measured case needs no second test: `longest-run-not-last` has exactly one longer run.
- **Qualify by the row's total matching pixels.** Eliminated by §147's measured frame: 18 px of parking-glyph
  colour across two rows passes a low total, which is the false positive that entry files; and summing is
  precisely what the `two-short-runs` case forbids (two 5 px runs in one row must not reach the floor).
- **Qualify by the row's extent (`last - first + 1` over the whole row).** Eliminated: that quantity is not
  what the current code computes (both terms measure the last run, at HEAD `:72-73`), and read
  as a span it would qualify a row of two far-apart short runs, which `two-short-runs` pins as a
  non-highlight (the removed two-term test is at HEAD `tools/measure-highlight.py:72-73`, and its second term
  repeats the first for the last run, not a span over the row).
- **Raise `MIN_RUN_PX` instead.** Eliminated by the entry's own reasoning and by the script's comment
  (`:48-53`) and `SKILL.md`'s closing note: the floor must stay low enough to see a short analysed leg, and
  raising it would drop short real highlights rather than fix the row rule. It is also the operating point
  §147's decision is about, which is not this change's to take.
- **Leave the tool and treat every multi-run row as a documented limitation.** Eliminated: `SKILL.md`
  already declares the script's numbers authoritative evidence for a pixel claim, and a row silently
  dropped by scan order is a false "no highlight" / false `CLIPPED` that no caller can see.

## Verification

- Red on HEAD and green after the fix: `bash tools/measure-highlight-selftest.sh` (7 cases; retained runs
  in `evidence/red-on-HEAD.txt` and `evidence/green-after-fix.txt`).
- The older detector harness stays green: `bash .pi/skills/pixel-check/selftest.sh`.
- Revert-check (falsification): restore the rightmost-run test in `find_casing` only; the named case
  `longest-run-not-last` must fail with the measured `"highlight": null` / exit 2, then restore and re-run
  the harness green (recorded in `tasks.md`).
- No Gradle gate: the change touches no `app/src`, `core/src`, `auto/src`, `:osmscout-client-java` or
  Gradle-visible test source, so `./gradlew test -PforceTests --no-build-cache` has nothing new to execute
  (see `tasks.md` task 3 for the shape decision). `bash tools/check-doc-routes.sh` runs because
  `guidelines/Build.md` section 11 text changed.
- Device follow-up stays pending: the entry states the device-side impact is inference, and the fix is
  decidable host-side.
