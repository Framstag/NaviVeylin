# fix-mutation-harness-keeps-the-tree

## Why

Measured 2026-10-21 (issue #234): a full-set mutation run ended `red-when-mutated: 16 · not-noticed: 51 ·
restore-failures: 12` on a tree that had been 65 red / 0 not-noticed minutes earlier, and left six engine files
at 0 bytes. The cause is in the run's own transcript: `cp: … '/tmp/loop-mutations.1ZzZR1/9.6.run.sh': Der
zugewiesene Plattenplatz (Quota) ist überschritten` — `/tmp` is a 7.7 G tmpfs on this host (measured 88 % full).

Two defects, one class: the harness that exists to prove invariants could silently destroy the tree it runs in.
`apply_mutation` ignored `cp "$file" "$backup"`, so a failed backup left a zero-length file; the restore then
copied that emptiness back over the engine file, and `cmp -s` reported `MUTATION-DID-NOT-APPLY`, which reads as
"fix the mutation" — and was followed by another copy of the empty backup.

The change is the harness's own: three of the emptied files were re-applied from the commit by hand, and the
recovery cost ~35 min of a 4 h 40 m change.

## What Changes

- `tools/loop-mutations.sh` — the scratch root defaults to the project's gitignored `.pi/tmp` (same filesystem
  as the tree, not a tmpfs) and is named once on stderr; `TMPDIR` still moves it. `backup_file` stops the
  harness before anything is mutated when the backup is empty; `restore_file` refuses an empty or missing
  backup, verifies the result with `cmp`, and is used by the in-flight restore path, the not-applied path and
  the `EXIT` trap alike. A new `--probe empty-backup` has the shape the full tmpfs produced, so the guard is
  asserted and not assumed; `--probe` takes an optional victim path through
  `LOOP_MUTATIONS_PROBE_VICTIM`, so a test can look at the protected file after the harness has exited.
- `tools/loop-mutations-selftest.sh` — two cases: the scratch root is the project's unless `TMPDIR` says
  otherwise (and is removed afterwards), and a restore from an empty backup exits 2 with the protected file
  still holding its content.
- `openspec/specs/agent-issue-loop/spec.md` — one requirement added (below).

## Cases and evidence

- `tools/loop-mutations-selftest.sh` (the harness's own gate) — 15 assertions, of which the two new ones are
  `the scratch tree is the project's, not the host's tmpfs` and `a restore from an empty backup exits 2 … the
  file the empty backup would have replaced still holds its content`: `evidence/harness-selftest.txt`, exit 0.
- The whole mutation set through the guarded harness: `evidence/mutations.txt`,
  `red-when-mutated: 67 · not-noticed: 0 · restore-failures: 0`, exit 0, and `git status` clean afterwards.
- The loop's own suite as the control that the engine is untouched: `evidence/suite.txt`, 131/131, exit 0.

No case was red on the pre-change tree, because the defect is in the harness and not in the engine: the red case
here is the *probe*, which fails the old harness by design (`--probe empty-backup` did not exist; an empty backup
was copied over the file). That is the same shape the harness's other two probes already had.

## Impact

| file | module / area |
|---|---|
| `tools/loop-mutations.sh` | the revert-check harness for `tools/loop/**` |
| `tools/loop-mutations-selftest.sh` | the harness's own gate (two cases) |
| `openspec/specs/agent-issue-loop/spec.md` | one requirement added |

No Android module, resource, native source, JNI bridge, manifest, Gradle file, engine file or prompt skeleton is
touched; `tools/check-portability.sh` reads the same configuration.

## Previous specifications changed

- `openspec/specs/agent-issue-loop/spec.md` — **ADDED** "The mutation harness leaves the tree exactly as it
  found it": the backups live beside the tree, a backup that did not land stops the harness, and a restore
  never writes a backup it could not take. No existing requirement is modified.

## Additive or breaking, and rollback

**Additive.** One default path, two guards on paths that already existed, one probe, two cases. The harness's
output gains one line (`loop-mutations: scratch …`) and its exit code 2 now also covers a backup or restore it
cannot take safely.

Rollback: revert this commit. The scratch tree goes back to `/tmp`, a failed backup goes back to being copied
over the file it protects, and the two new assertions fail.
