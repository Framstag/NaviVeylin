# Sample pre-screen pass — `widen-fix-loop` task 6.1

The widened gate's own verification (design §Verification). The gate is cheap by design — a few greps and one
file read — so conditions 2, 3, 5 and 6 are decided by reading the entry, and only a candidate that could be
**eligible** needs its claim verified against the tree.

| entry | class | verdict | condition / evidence |
|---|---|---|---|
| §164 non-HTTP scheme → transport failure | improvement | **eligible** | all 7 hold. Verified in the tree: `app/src/main/java/com/naviveylin/data/HttpUrlFetcher.kt:125` — `val connection = parsed.openConnection() as HttpURLConnection` (the cast a non-HTTP scheme throws on), and `:153` — `return RepositoryFailure.MalformedUrl` for the unparseable case, which is the wording §164 says the parsed-but-non-HTTP case does not get. One mechanism, one fix (classify the scheme), one capability (`map-download-infrastructure`), host-decidable, and the red case asserts "the user sees the unusable-URL failure", a countable outcome |
| §120 car re-applies `daylight` → second stylesheet load | improvement | **`stale`** | condition 1 fails. The entry cites `MapScreen.kt:468` and `NavigationScreen.kt:477`; `auto/MapScreen.kt` does not exist and no `pushDark(force = …)` call exists anywhere. The mechanism is gone: `MapCanvasViewModel.kt:928-941` `ensureMapStyle` is documented as "the one entry point that applies the style/flag pair … a request whose pair equals the recorded one performs no native call", `pushDaylight` is called only when `reason.forceFlag \|\| appliedMapStyle?.daylight != daylight` (`:934`) — a plain style switch changes the style name, not the flag — and `AppliedMapStyle(styleName, daylight)` (`:885`) is the recorded pair |
| §163 cleartext repo, size+CRC on the same connection | improvement | `needs-decision` | condition 3 — "hash vs signature" is an owner/format decision |
| §77 `Bahnhofstraße` vs `Bahnhof Straße` | improvement | `needs-decision` | condition 3 — the entry's own fix candidate "needs its own boundary analysis" |
| §76 mid-name word unreachable | improvement | `too-wide` | condition 5 — an import-side change; every installed map would have to be re-imported |
| §111 follow anchor proven only as geometry | improvement | `needs-decision` | condition 3 — the fix candidate offers two alternatives ("drive the display block" *or* "extract a seam") |
| a `class: feature` entry | feature | not eligible | the class is out of scope by design D1 |

## Result and the one disagreement

Six of seven predictions matched. **§120 disagreed, and the disagreement falsifies the prediction, not the
gate.** The gate returned the correct verdict (`stale`, condition 1); the design had predicted `eligible` from
the `TODO.md` entry text instead of from the tree — the precise error the skill's own Phase A rule 1 warns
against ("the entry is a memory, not a fact"), committed in a design document. The design's table is corrected
in place; the gate wording is untouched.

**Yield of the widened gate, measured on this sample: 1 candidate of 6 improvements becomes eligible, and 1 of
the 2 improvement entries that a bug-only run would have skipped is refuted instead of analysable.** That is a
thin sample, and it is the honest reason the design keeps bugs ranked first (D1) and requires the first run
after this change to record a full pre-screen histogram over both classes.

## Finding left alone deliberately

§120 sits in `TODO.md` as `status: open` with a claim that no longer holds. Repointing and closing it belongs
to the loop's next pre-screen (which will produce exactly this `stale` verdict) or to `cleanup-todo`, not to a
change about the skill's rules: `dedupe-stylesheet-loads` is still in flight, so `fixed-by` would name an
unarchived change. Recorded here so the next run does not re-derive it.
