# Design — fix-loop-build-output-accumulation

The root cause is established: the loop has exactly two worktree outcomes and neither of them is about what
the tree *contains*, and no tool owns the scratch trees of a suite that dies without its trap. The
alternatives below are the ones the evidence eliminates, not a menu — each one is impossible for the reason
stated, which is what makes this a `fix-*` change rather than a feature (`.pi/skills/fix-loop`,
`openspec/config.yaml`, `rules.design`).

## The alternatives the evidence eliminated

**1. Remove the whole worktree at the end of every run (make the default `--clean-worktree`).**
Eliminated by the measurement the kept tree exists for: a cold loop worktree's first build costs **11 min**
(`REFERENCE.md`, "Measured costs") against 1–3 min for a test class inside a warm one, and the seed step
(`worktree.sh create`) has to re-check out the submodule and re-copy `local.prop*` and the gitignored skills.
The cost this change removes is the one nothing reads; removing the tree would remove exactly the part that
makes the next run cheap. It is also *already* an option — `--clean-worktree` — so this alternative is a flag
change, not a fix.

**2. Let a cron job or a systemd timer clean `.pi/worktrees/loop` and `.pi/tmp`.**
Eliminated because it answers a different question: the loop's leftovers become someone else's job, nothing
reports them, and a timer cannot tell a live suite's scratch tree from a dead one — it would delete the tree of
a suite that is running right now, which is what `clean#reportsWithoutApplyAndRemovesWithIt` asserts must not
happen. The issue's own words ("the disk cost is a decision rather than an accumulation") also rule out an
unattended policy: a decision has a report.

**3. Delete the build output inside the child's own phase (let the worker run `./gradlew clean` or `rm -rf`).**
Eliminated because the child's own gate needs it: the run reads `test-results/**/*.xml` out of `app/build`
after the child exits (`run.sh` `gate_xmls`, `cited_evidence`), and the change's evidence must travel inside
the archived change. A child that cleaned up after itself would delete the run's proof that the gate passed,
which is `run#claimedGateWithoutEvidenceIsBlocked`'s invariant.

**4. Name the module build directories in `tools/loop/run.sh` or `worktree.sh`.**
Eliminated by the repository's portability rule: a build directory is a fact about *this* project, and the
engine must run against another project's tree (`tooling-portability` D1, `tools/check-portability.sh`). The
list therefore goes where the gate command and the submodule path already are — `tools/loop/loop.conf` — and
the check reads it as a value set (five new values, 103 in total). The alternative is testable and the test
now exists: `run#engineNamesNoProjectFact` asserts the key is in the configuration, and the portability check
fails any engine naming one of its values.

**5. Remove everything under the loop's temporary root, without an age.**
Eliminated because the self-test's scratch trees are indistinguishable from one another by name alone: a tree
being written into right now is a `loop-selftest.*` tree. Age is the only signal a killed suite leaves (it
holds no lock that outlives it), so the threshold is recorded (`LOOP_TMP_STALE_MINUTES`, default 120 min) and
the case proves both halves: the stale tree goes, the fresh one stays. Deleting the whole `.pi/tmp` would also
reach other suites' scratch directories (`portability-selftest`, `mutate-selftest`, `loop-mutations-selftest`),
which are not the loop's to remove.

**6. One more flag on `run.sh` (`--strip-worktree`) instead of an end-step plus a command.**
Eliminated because the defect is not "the run cannot be asked to clean"; it is that nothing does it and nothing
reports what is left. A flag would leave the default exactly as it is — the accumulation the issue measured —
and would still leave the scratch trees of killed suites with no reader. The end step is unconditional (with
the two documented exceptions) and `loop-clean.sh` is the reader.

## The chosen shape, and why it is safe

Three pieces, one owner each:

| piece | owner | what it decides |
|---|---|---|
| `worktree.sh strip-scratch` | the tree | which recorded directories a kept tree loses |
| `loop-selftest.sh`'s traps + scratch root | the suite | that an interrupted run leaves nothing behind |
| `loop-clean.sh` | the leftovers | what is left, how much, and whether to remove it |

The removal itself is implemented **once**, in `lib.sh` (`loop_scratch_remove`), because the run's end step and
`loop-clean.sh` must not drift: a rule re-implemented per script is a rule that drifts (D16). Its four refusals
are the whole safety argument, and each is a case:

1. an entry that is absolute, `.`, `..` or carries `../` is not a path inside the tree;
2. a recorded path that **is** a symlink is never followed — it is left in place and named (a symlink *inside* a
   build directory costs the link, because `rm -rf` and `du` both act on the link, never its target);
3. a path whose directory, resolved with `pwd -P`, lies outside the tree is skipped (this is the check that
   catches a symlinked parent such as `app -> /somewhere`);
4. a path git tracks is the project's source and is skipped — `git ls-files --error-unmatch` in the tree, which
   is the mechanical form of "it never touches a dirty worktree's tracked or untracked source".

Untracked source outside the recorded paths is untouched by construction: the command only ever acts on the
recorded list. `du -sk` (never `-L`) supplies the bytes, so the number a report prints is what removing the
path actually reclaims.

**Two flags the removal respects.** `--clean-worktree` removes the tree whole, so there is nothing to strip.
`--reset-worktree` is the caller's own statement about what a previous run left in the tree — the start-time
repair — and the end step makes no further decision about that tree's contents; a caller who wants it stripped
runs `loop-clean.sh --apply`, which is the same implementation and prints what it does.

**Why the age, and not a lock.** A live suite holds no lock that a killed one releases; a lock file written by
the suite would survive `SIGKILL` exactly like the tree. The mtime of the tree is the honest signal: a suite
writes into it while it works. 120 minutes is a wide margin over the longest recorded suite (the full offline
suite runs in minutes; the change that owns `tools/loop/**` measured one case at 40 s). The case sets the
threshold through the configuration (a copied `loop.conf`), so both branches are exercised deterministically
rather than by waiting.

**What the change deliberately does not touch.** `run_child`, the report, the state, the floor, the label
transitions and the closure are untouched: this change is about bytes on disk, and the loop's accounting
already has an owner. The report's existing action line ("keep or remove the worktree …") stays as it is; the
skill's `SKILL.md` section states what the kept tree now is.

## Threading, lifecycle, and the native boundary

None arises: every file here is a bash script run by the developer or by CI, there is no JVM, no Compose, no
dispatch and no JNI call. The loop's existing rules hold unchanged — a script cannot be talked into "one more",
the child process is started in the project and told the worktree by its prompt, and the run writes the state
between children only. `strip-scratch` runs at the very end, after the last child, the last commit and the
report, so it cannot observe or disturb a running item.

## Risks

| risk | mitigation |
|---|---|
| a recorded path is wrong (a typo, a source directory) | the list is reviewed configuration, `git ls-files` refuses a tracked path, and the removal is confined to `$LOOP_WORKTREE` |
| a project's build directory is not gitignored, so `git status` shows nothing but the build is committed | refusal 4 catches it: a tracked path is never removed |
| a live suite's scratch tree is deleted | the age gate, and the case asserts the fresh tree survives |
| the run loses its own gate evidence | `strip-scratch` runs only in `end_run`, after `gate_xmls` and the closure; the child's phase is untouched (alternative 3) |
| the engine becomes project-specific | the list is configuration; `tools/check-portability.sh` reads it as five forbidden values and `run#engineNamesNoProjectFact` asserts where it lives |

## Verification

- **Red on HEAD `5dd5b35`**, `evidence/red/`: `worktree#keepsSourcesNotBuildOutput` (`the kept worktree still
  carries its build output: 116K`), `clean#interruptedSuiteRemovesItsScratchTree` (`the suite never created a
  scratch tree under the recorded root …`), `clean#reportsWithoutApplyAndRemovesWithIt` (`…/loop-clean.sh:
  Datei oder Verzeichnis nicht gefunden`).
- **Green on the fix**, `evidence/green/`: the same three cases, plus the two existing cases on this surface
  (`worktree#keepsByDefaultAndRemovesOnRequest`, `run#resetWorktreeDiscardsWhatItNames`), plus the measurement
  of the developer host's kept worktree: 4 paths, **55 271 424 B**, nothing removed.
- **Structural checks**: `bash -n` on every edited script, `tools/check-portability.sh --quiet` (43 engines,
  2 templates, 103 values, no leak), `openspec validate fix-loop-build-output-accumulation --strict`,
  `openspec doctor`.
- **Not run here**: the whole offline suite (`tools/loop-selftest.sh` without `--case`) — the orchestrator runs
  it once on the merged tree. No device, emulator, JVM or Gradle run belongs to this change: no compiled file
  of the application is touched.
- **Revert-check**: not owed for the symptom (it is proven by the red case), and owed for the one invariant the
  fix introduces beyond it — "the removal never leaves the tree". It is proven twice: by the case's symlink
  half (mutating `loop_scratch_remove`'s symlink refusal to `:` removes the link and the case fails with `a
  recorded path that is a symlink was removed from the kept worktree`), and by the portability check over the
  recorded list, whose mutation is a value in an engine.
