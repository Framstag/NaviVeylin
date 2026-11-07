# Design — fix-loop-reset-worktree-submodule

## The root cause

`--reset-worktree` is the repair the run's own refusal names for a kept worktree a previous run left dirty
(`tools/loop/run.sh:515` on the base). It ran two verbs of the **superproject**: `git reset -q --hard`
(`tools/loop/run.sh:504`) and `git clean -qfd` (`:505`). A submodule's checkout is neither:

- the index inside the module already matches the module's own `HEAD`, so `git reset --hard` in the superproject has
  nothing to undo — it does not enter the module at all;
- the module's path is a *tracked* gitlink, so `git clean -fd` leaves it alone by its own rule.

So a module a previous build left on another commit stays there, `git status --porcelain` keeps printing the module
path as modified, and the next start's dirty-worktree refusal fires again with the same advice. Measured 2026-10-26
in a throwaway repository whose configured submodule path is a real module of two commits:

```
$ git -C "$wt" reset -q --hard && git -C "$wt" clean -qfd
$ git -C "$wt" status --porcelain
 M app/src/main/cpp/libosmscout
$ git -C "$wt" submodule update --init --checkout app/src/main/cpp/libosmscout
$ git -C "$wt" status --porcelain
                          # empty; the module is on the gitlink the superproject records
```

## The single fix

In the `--reset-worktree` branch of `run.sh`, after the two verbs:

1. Read the project's own submodule path (`loop_conf_value LOOP_SUBMODULE_PATH`, the key the fresh worktree's
   checkout already reads) and, only where the kept worktree carries a `.gitmodules`, run
   `git submodule update --init --checkout --force <path>`. Best-effort with a note, exactly as the fresh worktree's
   checkout is (D4) — the engine still names no project path, which is what `tools/check-portability.sh` asserts.
   `--force` because the flag's whole statement is "discard what the previous run left", and a module with a
   modified tracked file would otherwise refuse the checkout.
2. Read `git status --porcelain` **again**, and let the empty read decide the claim. Empty: the existing "is clean
   again" line. Not empty: print what the reset cannot discard, name those paths, and `loop_refuse` with exit 5 —
   the same status the dirty-worktree refusal has, and a reason printed rather than a run that ends silently.

## Why not the alternatives

- **Withdraw `--reset-worktree` from the refusal's advice** (the item's second candidate). Eliminated by the
  measurement: the dirt is reachable in one command, so the advice is right once the repair performs it. Dropping it
  gives the person the repair this project has measured as expensive twice — an item's work reconstructed by hand
  from `git status` and a diff (`guidelines/Backlog.md:394`, #308 and #311).
- **`git submodule foreach git reset --hard && git clean -fd`**, i.e. every module. Eliminated: the profile records
  one module (`LOOP_SUBMODULE_PATH`) and a tree may carry more, so `foreach` would discard work in modules the loop
  never checks out or builds; and it still would not restore the gitlink the superproject records, which is the
  dirt this item reports.
- **`git submodule update --init --checkout` alone, without the second status read.** Eliminated: `--checkout`
  leaves a module's untracked files in place (measured: ` M app/src/main/cpp/libosmscout` survives it), so the
  repair would print "is clean again" for a tree the next start refuses — the same false claim, one verb later.
- **Making the refusal's message weaker instead of the repair stronger.** Eliminated: the refusal is read by a
  person whose next run is refused identically, and a hedge ("may not be cleanable") costs the same round without
  repairing anything.

## Blast radius

- `tools/loop/run.sh` — the reset branch only; the refusal branch, the marker attribution and the escalation are
  untouched.
- `tools/loop-cases.sh` — two cases and the fixture that places a real module at the configured path.
- `tools/loop-mutations.sh` — two records (13.8, 13.9), one per new invariant.
- `tools/loop/loop.conf` and `PORTING.md` — the key's comment, which said "a fresh worktree" only.
- The spec delta; no compiled file, so no Gradle suite is owed by these paths and the gate ran only the shell
  suites the profile maps for `tools/loop/**`.

## Rollback

Revert the branch. The added block reads a key that already existed and is best-effort; a run started by an older
engine resets exactly what it reset before, and no state field, tally or report cell changes.
