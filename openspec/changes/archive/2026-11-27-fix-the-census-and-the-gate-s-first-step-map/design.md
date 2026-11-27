# Design — the case set is a directory, and three readers are taught it

LOOP-ROUTE: guidelines/Backlog.recipes.md §7c

## The root cause

Three places answer a question about "the suite's cases file" by naming one path:

- `tools/loop/census.sh:31` — the `--cases` argument, and until this change the only shape it took: the refusal at
  `tools/loop/census.sh:55` took a file and nothing else, the path was read whole into `case_text` (a variable nothing
  read) and the phrase test at `tools/loop/census.sh:166` grepped that one path.
- `tools/loop/loop.conf:1084` — `LOOP_GATE_FIRST_STEPS`' left side was the literal path of the one cases file, and
  `tools/loop/gate.sh:338` matches a path against it with `case "$p" in $glob`, so a nested path under
  `tools/loop/tests/cases/` matched no pair of that row until the directory's pair was added beside it.
- `tools/loop/tests/loop-cases.sh:616` — `loop_case_core_files` printed one token, and
  `tools/loop/tests/loop-selftest.sh:887` compared a core token to a path by **equality**, so a directory token was
  dead weight in that list until `files_select` read a trailing `/` as owning the paths below it.

`LOOP_GATE_SUITES` needs no row: its `tools/loop/*` glob already matches a nested case file (probed against
`tools/loop/tests/cases/run.sh`), which is why only the *first-step* row is wrong.

## The single fix

1. **`tools/loop/census.sh`** — the case set is resolved once, into an array: a directory is every `*.sh` directly
   below it, a file is itself. An empty directory is a usage refusal naming the directory
   (`tools/loop/census.sh:51`), anything that is neither is the refusal naming the argument
   (`tools/loop/census.sh:55`), `case_text` (dead) goes, and the phrase test greps the whole array
   (`tools/loop/census.sh:166`).
2. **`tools/loop/loop.conf:1084`** — the row gains a pair `tools/loop/tests/cases/*|<the same command>`, in the profile
   and not in the prompt: which command is cheap, and which paths owe it, stay the profile's (issue #792's rule).
3. **`tools/loop/tests/loop-cases.sh:616`** — the core set's token for the suite's cases files becomes the directory
   they live in, `tools/loop/tests/cases/`.
4. **`tools/loop/tests/loop-selftest.sh:893`** — `files_select` reads the core list with the same rule its own
   declaration loop already applies at `tools/loop/tests/loop-selftest.sh:907`: a token ending in `/` owns the paths
   below it, anything else matches by equality.

## The repair the item's recorded decision names

The first attempt rewrote `files_select`'s loop to iterate the **core** tokens instead of the delta's paths, and the
guard `[ "$from_git_narrowed" -eq 1 ] && [ "$p" = "$case_file" ] && continue` survived only in the new directory
branch. So a delta whose hunks all sit inside one case body of `tools/loop/tests/loop-cases.sh` — the narrowing issue
#917 added — selected the whole set again, and `driver#aHunkInACaseBodySelectsThatCaseOnly` went red on this change's
own path. The owner's recorded decision is option **A**: repair the dropped guard in the non-directory branch and
re-gate; the directory branch, the new stub case and the two new assertions stay exactly as written.

That repair is `tools/loop/tests/loop-selftest.sh:899`:

```bash
      *) [ "$from_git_narrowed" -eq 1 ] && [ "$tok" = "$case_file" ] && continue
         case " $only_files " in *" $tok "*) return 0 ;; esac ;;
```

It is the same predicate the directory branch carries one line above, applied where the token is compared by
equality — so both branches answer the narrowed cases file the same way, and #917's narrowing survives the rewrite.

Evidence: `evidence/red2-*.log` (three legs red on `0e278789`) and `evidence/green2-*.log` (the same legs plus the
three #917 cases green on the change), each with a JUnit-shaped XML beside it.

## Blast radius

`files_select` decides which cases a delta owes; a wrong answer there either refuses a delta for naming no case (the
red leg) or runs the whole set for a narrowed one (the repair). Both directions are covered by existing cases
(`driver#aHunkInACaseBodySelectsThatCaseOnly`, `driver#aHunkOutsideEveryCaseBodyOwesEveryCase`,
`driver#aCoreFileOwesEveryCase`, `driver#aFileSetThatNamesNoCaseIsRefused`), and the gate's own suite leg runs the full
set. `census.sh` is read only by its own cases and the loop's report; `loop.conf`'s row is read only by
`tools/loop/gate.sh --plan`.

## Rollback

Revert the five files. Nothing persists: the core-set token, the profile row and the census's argument shape are each
read at the moment they are needed, and no state is written by any of them.

## Alternatives the evidence eliminated

- **A new case file** for the sibling-cases-file rule — eliminated: the rule is a leg of the case that already owns
  `LOOP_GATE_FIRST_STEPS` (`gate#aPathACheckReadsOwesThatStepsCommandSet`), and a second owner of one row is the
  duplication the case's own docstring refuses.
- **`LOOP_GATE_SUITES`' own row** — eliminated by the probe the item records: `case "$p" in tools/loop/*` already
  matches a nested path, so the suite is owed and only the first step was not.
- **Narrowing the change to drop the core-directory token** — eliminated by the owner's decision: it leaves a split's
  case file owed nothing and uncaught, the guarantee six open items need (#1100, #1101, #1103, #1105, #1106, #1108).
- **`--cases` taking a repeated flag** instead of a directory — eliminated: the caller is the suite's own case, which
  holds a directory and not a list, and a repeated flag would owe every future caller the same expansion loop.
