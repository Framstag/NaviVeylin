# Readings — the report states what the run recorded, not what it can re-derive

Every anchor below was taken from this tree, on the branch that carries commit `86fec1a5` (merge `cda0fc5b`). The
change was written after the implementation landed, so these are readings of shipped code and of the shipped cases.

## R1 — the figures the report never stated

The run of `2026-11-13T05:47:11Z`, read by hand out of `items/*/` (quoted in `tools/loop/report.sh:443-450`, which is
where the reading became the line's own comment):

- `#853` — gate `4 568 s` of `5 709 s` item wall: **80 %**.
- `#865` — **60.4 %**; `#864` — **58.8 %**. Two items of one run told apart only by the tenth, so a whole-percent figure
  tells them apart not at all.
- The run's window `45 173 s` against item walls of `22 925 s`: **22 247 s, 49.3 %**, outside every item — the landings,
  the holds, the pre-screen.

- The refused landing was printed from a second reading of prose: `report.sh` parsed the branch and the paths back out
  of the note the run had composed as a sentence. The mutation set states the size of it in its own comment
  (`tools/loop/tests/loop-mutations.sh:1355-1359`): *"`report.sh` read 47 figures back out of text the run had already
  written — the note's branch, the paths after a refusal's `onto the base:`, a window dated from the clock the report
  happened to run at — so a report printed a landing, or a span, that the run's own record did not hold."*

## R2 — the window is the recorded stop minus the recorded start

`tools/loop/report.sh:164-171`:

- `:164-166` the comment: both instants are in the state, so the figure is their difference and never a second reading
  of the clock; a run that has not stopped has no window to print.
- `:167-171` `window_secs=""` unless both `run_epoch` and `stop_epoch` are numbers; the guard keeps it positive, so a
  record whose stop precedes its start prints a dash rather than a negative span.

## R3 — `gate share:`

`tools/loop/report.sh:626-644`:

- `:631-633` the set: items whose `metrics.phases.gateMs` and `metrics.wallSeconds` are both above zero.
- `:634-638` the four absences, each with its own reason: no phase duration recorded at all, a state written before the
  report read phases, no gate ran in this run, a state written before the phases existed.
- `:639-644` the share: the buckets summed over the walls summed, `round | . / 10` — **tenths** — with the worst two
  items named by their own share.

Printed at `:762` as `  gate share: $gateshare`, beside the phase line.

Sample line the case asserts (`tools/loop/tests/loop-cases.sh:14576-14578`):

```
58.8 % — 14.7 s of the 25 s of item wall over 2 items · worst #101 64 % · #102 53.6 %
```

## R4 — `outside items:`

`tools/loop/report.sh:645-659`:

- `:645-649` the comment: the window is the start and stop the run wrote, the walls are the ones each item wrote, so the
  difference is what the run spent between items.
- `:650-653` a state with no window prints a dash naming both reasons it may have none.
- `:654-655` a recorded window with no item wall says so rather than printing a share of nothing.
- `:656-659` the figure, in tenths, with the walls' total and their count; the recorded wait is named when there is one,
  and a wall sum past the window is named as the two records disagreeing instead of printing a negative share.

Sample line the case asserts (`tools/loop/tests/loop-cases.sh:14581-14583`):

```
91.7 % — 275 s of the 300 s window outside every item wall (25 s over 2 items)
```

## R5 — the run records the refusal where the merge met it

`tools/loop/run.sh`:

- `:486-487` `merge_refused_branch=""` and `merge_refused_paths=()`.
- `:513-521` on a refusal: the branch is kept, and each line after `onto the base:` is read into the paths array
  (`:518-521`).
- `:542-545` the arm where the base could not be recorded keeps the branch alone.
- `:4145-4165` the blocked arm builds `refused_args` — `--merge-refused-branch` and one `--merge-refused-path` per path
  — hands them to `state.sh done` (`:4157`), and clears both after the call (`:4164-4165`) so the next item cannot
  inherit a refusal.
- `:2584-2585` the per-item reset where the item's outcome is written.

## R6 — the state writes what it was handed, or refuses it

`tools/loop/state.sh`:

- `:686`, `:690` — the locals: `merge_refused_branch=""`, `local -a merge_refused_paths=()`.
- `:731-732` — the two arguments.
- `:824-825` — a path with no branch dies: "`--merge-refused-path` is one path a refused landing named, and the
  refusal's branch is given beside it with `--merge-refused-branch`".
- `:827-828` — a branch whose stop is not `unmergeable` dies: the refusal is recorded beside the mechanism that names it.
- `:831-833` — a path carrying a line break dies at its first line, so a record cannot hold half a list.
- `:924-928` — the record built with `jq -Rsc` over the paths: `{branch:$b, paths:(split("\n") | map(select(length > 0)))}`.
- `:952` — `mergeRefused:$mr,` written on the item beside `merged:`; `:996` — `--argjson mr "$merge_refused"`.

## R7 — the report reads the record, and says when it cannot

`tools/loop/report.sh:837-893`:

- `:853` — `IFS=$'\t' read -r refusal_kind refused_branch <<< "$refused"`.
- `:857`, `:878` — `if [ "$refusal_kind" = record ]`: the branch printed is the state's, and the paths are the state's
  list, verbatim.
- `:892-893` — the fallback, and its one sentence: *"this state records no refusal — its branch and paths are re-derived
  from the note, not read from a record"*. That sentence is the case's `assert_not_contains` target for a record
  (`tools/loop/tests/loop-cases.sh:13387-13388`) and its `assert_contains` target for a pre-field state
  (`:13346-13347`).

## R8 — the cases

`tools/loop/tests/loop-cases.sh`:

- `:14542` — `case_report_theTrendBlockNamesTheGateShareAndTheOutsideWindow`: two items of `1 s` and `8 s` of gate over
  the same `12.5 s` wall, and `2 s` and `6.7 s` over the same wall; `:14576-14578` the share and the worst two items;
  `:14581-14583` the window; `:14587-14593` a run whose gate cost nothing and whose record carries no stop, both as
  dashes with their reasons; `:14596-14602` a state written before the phases existed, which says the figure was never
  recorded rather than that no gate ran.
- `:13357` — `case_report_theRefusedLandingIsReadFromTheRecord`: one branch and two paths recorded beside a note naming
  a different branch and another path; `:13379-13389` the landing line is the record's, the note's are printed nowhere,
  the line is not said to be re-derived, and the item is one line; `:13392-13399` the two negative arms of `state.sh`.
- `:13283` — `case_report_anUnmergeableItemNamesItsLanding`, extended for the fallback (`:13346-13347`).
- Registry entries `:372`, `:394-395` (case set) and `:1120`, `:1139-1140` (dispatcher arms).

## R9 — the falsification the suite holds

`tools/loop/tests/loop-mutations.sh:1360`, `:1361`, `:1362`, verbatim:

```
'883.1@-@tools/loop/report.sh@report#theTrendBlockNamesTheGateShareAndTheOutsideWindow@the window read as the clock since the run started instead of the stop the state recorded, so a run reports a span no record holds and the figure dates itself from the moment the report ran (issue #883)@s|--argjson window "\${window_secs:-null}"|--argjson window "$(( $(date +%s) - ${run_epoch:-0} ))"|'
'883.2@-@tools/loop/report.sh@report#theRefusedLandingIsReadFromTheRecord@the refused landing read back out of the note the block quoted rather than the refusal the run recorded, so the branch and the paths a caller is handed are a second reading of prose (issue #883)@s|^  if \[ "$refusal_kind" = record \]; then$|  if false; then|'
'883.3@-@tools/loop/state.sh@report#theRefusedLandingIsReadFromTheRecord@the arm that writes a refusal the run handed to its item, so the branch and the paths a block was given are recorded as no refusal at all and the report re-derives them from the note (issue #883)@s|^  if \[ -n "$merge_refused_branch" \]; then$|  if false; then|'
```

Each name the case it must red: the window's source, the record-read branch of the report, and the state's arm that
writes it. `883.3` is the one that proves the field is load-bearing and not decoration — with it removed, `state.sh`
records no refusal and the report's only remaining source is the note.

## R10 — where the rule is written for the loop's readers

`guidelines/Backlog.recipes.md:749-761`: the paragraph "The gate share and the outside-items window are read from the
record, not from a log" — both figures' arithmetic, the tenths and why, the window taken from the recorded start and
stop, and the refusal record that keeps a landing from being a second reading of prose.

## R11 — the gate verdict

The gate that landed `86fec1a5` is the loop's own run. Its tally is **not retained** in this checkout: the run record is
gitignored and machine-local, and this change was written after the run had ended. Nothing here quotes a figure it
cannot show — `tools/loop/evidence-check.sh`'s C2 refuses exactly that — and the two figures' arithmetic, the sample
lines the cases assert, the two negative arms of `state.sh` and the three mutation records above are the readings a
reader can re-take.
