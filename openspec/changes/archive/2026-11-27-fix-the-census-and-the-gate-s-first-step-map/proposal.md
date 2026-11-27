# The census and the gate's first-step map take the case set, not one file

Item #1104. Three places answer a question about "the suite's cases file" by naming one path.
`tools/loop/census.sh:31` takes `--cases` as a file and nothing else, and its phrase test greps that one path
(`tools/loop/census.sh:166`); the profile keys its first-step pair on that exact path (`tools/loop/loop.conf:1084`);
and `loop_case_core_files` returns that same single token as a core file
(`tools/loop/tests/loop-cases.sh:616`). A cases file a split puts **beside** the one the suite has today is therefore
counted by nobody, owes no first step, and is owed no whole-set selection: the census under-counts, and a delta that
writes a `# files:` declaration into `tools/loop/tests/cases/run.sh` is refused only by the full suite twelve minutes
in — the cost the first-step pair was added to remove (issue #792).

LOOP-ROUTE: guidelines/Backlog.recipes.md §7c

**Root cause, in one sentence:** the two readers that answer "is this guard's phrase carried by a case" and "which
cheap step does this case-file delta owe", and the one list that says "a case-file delta owes the whole set", are each
written against the single path `tools/loop/tests/loop-cases.sh` — as a `cat` and a `grep` argument, as the left side
of a `<glob>|<command>` pair, and as one token of `loop_case_core_files` — so all three answers are a function of that
path rather than of the set of files the suite's bodies live in.

**The red test:** three legs of cases that exist today, each red on the run's base commit `0e278789`
(`evidence/red2-*.log`, transcribed to `evidence/red2-*.xml` because a shell self-test prints `PASS`/`FAIL` lines):

- `tools/loop/tests/loop-selftest.sh --case census#theCensusIsANumber` asks the census over a **directory** holding
  the same fixture case text as `tools/loop/tests/fixtures/census/cases.sh`, split into two files, and reads the same
  `guards:`/`no-case:` counts the file form gives. On the base the run exits 1 with
  `census: no cases at <dir>` — `tools/loop/census.sh:55` resolved the argument as a file and nothing else.
- `tools/loop/tests/loop-selftest.sh --case driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns` gives the nested suite a
  core list naming `tools/loop/tests/cases/` and a file set naming `tools/loop/tests/cases/run.sh`: on the base the
  run is refused with "the file set names no case of the 4 declared", because `files_select`
  (`tools/loop/tests/loop-selftest.sh:887`) compared a core token by equality.
- `tools/loop/tests/loop-selftest.sh --case gate#aPathACheckReadsOwesThatStepsCommandSet` commits a sibling cases file
  `tools/loop/tests/cases/run.sh` into its throwaway repository and reads the plan: on the base the plan carries no
  `driver#everyCaseDeclaresTheFilesItCanReach` step at all.

**The single fix:** the case set is resolved once, in each of the three places — a directory of `*.sh` files for the
census, a `<dir>/*` row for the first-step map, and a directory token in the core list that `files_select` reads as
owning the paths below it.

**One repair, decided and recorded on the item.** The first attempt (branch
`fix/issue-1104-the-census-and-the-gate-s-first-step-map`, commit `14193daa`) rewrote `files_select`'s loop over the
core tokens and dropped the guard that skips the cases file when `--from-git` has already narrowed to one case body,
so `driver#aHunkInACaseBodySelectsThatCaseOnly` fell back to the whole set and the gate came back red on this
change's own path. The owner's recorded decision is option **A**: repair the dropped guard in the non-directory branch
(`tools/loop/tests/loop-selftest.sh:899`) and re-gate, leaving the directory branch, the new stub case and the two new
assertions untouched.

**Surface, counted before the plan was fixed** (`tools/look.sh`):
`LOOP_GATE_FIRST_STEPS` — 30 matches in 16 files, of which the readers are `tools/loop/lib.sh`, `tools/loop/loop.conf`,
`tools/loop/gate.sh:338` and the cases that read the profile's row; `loop_case_core_files` — 24 matches in 14 files;
`files_select` — 6 matches in 4 files. Cases added: 3 legs of existing cases (no new case), fixtures: one nested-suite
fixture `STUB_CORE_DIR_CASES`. Files edited: 5 (`census.sh`, `loop.conf`, `loop-cases.sh`, `loop-selftest.sh`,
`guidelines/Backlog.recipes.md`), plus the spec delta.

**Bound it does not claim:** it adds no case and moves no case body; it is the wiring a split cannot be safe without,
not a step of the split.
