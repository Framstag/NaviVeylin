# design — the compile moves from the top of the driver to the case that asks for it

## Root cause

`tools/loop-selftest.sh:95` compiled `tools/issues/core/*.java` into the suite's scratch tree before the case list
existed (`source "$root/tools/loop-cases.sh"`, `tools/loop-selftest.sh:114`), so the compile happened before the
invocation's selection was known and every invocation paid it — including a `--case` run of a case that never asks
for the core. (The two line numbers are the base commit `abeffcad`'s.) The compile
is the driver's own prerequisite (`$ISSUES_CORE_CLASSES`, `tools/loop-selftest.sh:107`), not the gate's: the issue
tooling compiles on demand for itself when it is handed nothing (`tools/issues/lib.sh:189`–`215`).

## The single fix

The driver gains one function, `core_ensure`, and loses the eager compile and the eager export:

```sh
readonly selftest_classes="$tmp/classes"
readonly selftest_core_marker="$tmp/core-compiled"

core_ensure() {
  if [ ! -f "$selftest_core_marker" ]; then
    javac --release 17 -d "$selftest_classes" "$root"/tools/issues/core/*.java || {
      rm -rf "$selftest_classes"
      echo "loop-selftest: the issue tooling's Java core does not compile" >&2
      exit 1
    }
    : > "$selftest_core_marker"
  fi
  export ISSUES_CORE_CLASSES="$selftest_classes"
}
```

and a case asks for it where it uses the tooling: `run_loop` (`tools/loop-cases.sh:478`), the five cases that
start `tools/loop/run.sh` directly (`:756`, `:765`, `:792`, `:805`, `:1960`), the case that runs
`tools/issues/update.sh` (`:1994`) and the case that runs `WorkPackage` through the class path (`:2214`).

The two paths are the driver's own names because `tmp` is not: five case functions declare `local … tmp …` for a
scratch tree of their own (`tools/loop-cases.sh:4957`, `:4994`, `:5042`, `:5100`, `:5314`), and a `local` of that
name shadows the driver's variable for the whole function — which is what this change's first gate run measured
(`evidence/gate-attribution.md`, `tmp ist nicht gesetzt` out of `clean#reportsWithoutApplyAndRemovesWithIt`).

Three properties of the fix are load-bearing:

- **once per suite.** A case runs in a subshell of the driver (`run_case`, `tools/loop-selftest.sh:138`–`148`), so
  a flag set in the driver's own shell is not visible to the next case's subshell; the marker in the suite's own
  scratch tree is, and it is removed with the tree.
- **the refusal is the driver's.** The message, the exit status 1 and the reader (stderr) are the ones
  `tools/loop-selftest.sh:95`–`98` printed before (base commit `abeffcad`'s numbering); a case that asks for a core
  that cannot compile ends the suite with that line, as a full suite did.
- **a forgotten call is not a failure.** A case that reaches the tooling without asking is served by
  `issues_core_classes` (`tools/issues/lib.sh:189`), which compiles into its own digest-keyed cache under
  `$TMPDIR` when `ISSUES_CORE_CLASSES` is empty. The invariant therefore costs a redundant compile in the worst
  case, never a red suite with no cause.

## Blast radius

| file | change |
|---|---|
| `tools/loop-selftest.sh` | 3 lines and 1 export → `core_ensure`; the tool and JDK checks above it unchanged |
| `tools/loop-cases.sh` | 1 header sentence, 1 call in `run_loop`, 7 calls at direct uses, 2 cases |
| `tools/loop-mutations.sh` | record `48.1` |
| `openspec/specs/agent-issue-loop/spec.md` | 1 requirement, 2 scenarios (on archive) |

No file format, no on-disk state, no prompt and no engine script changes; the case list, the case names and the
exit codes of every invocation path are unchanged.

## Rollback

Restore the four lines at `tools/loop-selftest.sh:95`–`107` and delete the `core_ensure` function and its call
sites. Nothing else reads the marker, and the scratch tree that carries it is removed by the driver's own
`on_exit`.

## Alternatives the evidence eliminated

1. **A per-case declaration list in `tools/loop-cases.sh`** ("these cases need the core"), read by the driver
   before each case. Eliminated: 97 of the file's case functions reach the engine, the file's dispatcher already
   carries all 178 names, and the list would be a second copy of it that drifts silently — and the item's own fix
   shape ("compile lazily, when the selected cases need the core") asks the selection, not a registry.
2. **Deriving the need by scanning the case function's text** (`declare -f "case_<name>"`, matched against
   `run.sh|run_loop|ISSUES_CORE_CLASSES`). Eliminated: it over-approximates the very case the item is about —
   the `usage#` cases run `tools/loop/run.sh` with a bad argument and are refused on argv before the tooling is
   reached, so they would look like they need the core — and it answers from source text rather than from what the
   case does.
3. **Dropping the driver's compile and letting `issues_core_classes` cache it on demand.** Eliminated by the
   item's shape: it removes the named refusal (`loop-selftest: the issue tooling's Java core does not compile`,
   exit 1) that a case needing the core must still get, and it moves the class path out of the suite's scratch tree
   into a cache under `$TMPDIR`, which is neither the suite's to remove nor what the failing case is told about.
4. **Compiling a class directory per needing case.** Eliminated by the bound the item states: a full suite compiles
   once for all cases (0 s amortized), and per-case directories would pay it 90-odd times.

## What is not changed

The five sibling drivers that precompile the same core
(`tools/issue-tooling-selftest.sh:49`, `tools/sensor-selftest.sh:38`, `tools/resolve-stale-selftest.sh:50`,
`tools/check-portability-selftest.sh:71`, `tools/issues/metadata-mutations.sh:45`). Their selections are the issue
tooling's own cases (`core`, `core_class`, `resolver`, the real scripts), so a `--case` run of theirs that asks for
no core does not exist; the probe that would justify the change there is a different measurement, and the item
measured the loop's driver. The proposal carries the count so the next reader can widen it if that measurement
changes.
