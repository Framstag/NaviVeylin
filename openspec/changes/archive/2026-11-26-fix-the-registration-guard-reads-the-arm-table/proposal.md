# The registration guard reads the arm table beside the bodies

Item #1107. The suite's three-set registration guard (issue #929) is handed exactly one path on the base commit
`37b3b83f` — `' "$root/tools/loop/tests/loop-cases.sh")` at `tools/loop/tests/loop-selftest.sh:566` — so the arm half of the
comparison is a fact about one file and nothing else.

LOOP-ROUTE: guidelines/Backlog.recipes.md §7c

**Root cause, in one sentence:** the guard's awk collects bodies and arms from the lines of the single file its command
line names, so a tree whose dispatch arm table sits in a second file the suite reads finds arms in neither pass —
`na == 0` takes the "a cases file that defines no body at all dispatches by convention" branch at
`tools/loop/tests/loop-selftest.sh:551`, whose body checks only that every declared row has a body (`:552-556`) and
exits 0 — and the arm set stops being compared at all, which is the silence the guard was built to end (issue #929).
Those line numbers are the base commit's; in this change's tree the scan's input list is `:523-528` and the invocation
`${case_scan_files[@]}` is `:575`.

**The red test:** `driver#anArmTableInASecondFileIsCompared` in `tools/loop/tests/loop-cases.sh`. It builds a nested
suite whose cases file carries two bodies and two rows and whose dispatch arm table lives in the second file beside it,
sourced by that cases file. Leg 1 runs one of the cases through the second file's arm (`PASS`, exit 0 — true before and
after). Leg 2 removes one arm from that second file and asserts exit 1 naming
`stub#lost is a row of LOOP_SHELL_CASES with no dispatch arm`; on the base commit the driver exits 0 and runs the case
instead, because the second file is never read (`tools/loop/tests/loop-cases.sh:19957`, the #929 guard case, is green
throughout).

**What the item delivers:** the scan is handed the cases file **and** the file a dispatch arm table of its own is read
from — `tools/loop/tests/loop-case-index.sh`, the file issue #1102's change moves the declared set and the arm table
into — so the three sets are compared once over both; and the comment block at
`tools/loop/tests/loop-selftest.sh:510-517` says "the files that were just sourced" instead of "the file".

**Bound it does not claim:** it does not make the other two readers take a set, and it moves no case body and renames
no id — the `# files:` declaration reader and the hunk→case map (`tools/loop/tests/loop-selftest.sh:606`, `:643`,
`case_file=`) are issue #1101's remaining two thirds, and the profile key that would source a second cases file
(`LOOP_CASE_FILES`) is that change's mechanism, absent from this tree today (`tools/look.sh 'LOOP_CASE_FILES' --path
tools/loop` → 0 matches).

surface: 1 case added, 0 rewritten, 1 re-verified unchanged (`driver#aCaseWithNoSelectionRowIsRefused`), 2 files
touched — 579 cases in `bash tools/loop/tests/loop-selftest.sh --list`, 578 on the base commit; `./tools/look.sh
'loop-selftest\|loop-cases' --path tools/loop/layout.tsv` prints `0 matches`, so neither file carries a length bound;
the cases file's own row and arm are the two registry lines the suite's selection reads.
