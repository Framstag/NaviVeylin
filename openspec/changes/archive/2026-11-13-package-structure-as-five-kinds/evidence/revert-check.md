# Revert-check — write-serialisation invariant (pre-gate finding, item #873)

Production invariant: the lock-holding writer `FavoriteRepository.writeMutex`
(`app/src/main/java/com/naviveylin/data/FavoriteRepository.kt:94`) serialises
mutate + refreshState + persist across every public write entry point.

## Mutation (2026-11-13)

`FavoriteRepository.kt:146` `addGroup` — dropped the lock wrapper:

- before: `open suspend fun addGroup(name: String): Boolean = writeMutex.withLock { addGroupLocked(name) }`
- mutated: `open suspend fun addGroup(name: String): Boolean = addGroupLocked(name)`

Command (change root, one run):

    ./gradlew :app:testMobileDebugUnitTest --tests "*FavoriteRepositoryTest*" -PforceTests --no-build-cache

## Red — `evidence/revert-check-red.log`, `evidence/revert-check-red.xml`

- exit 1, `BUILD FAILED in 50s`
- XML: `tests="50" skipped="0" failures="1" errors="0" timestamp="2026-11-13T08:31:35.787Z"`
- failure:
  `FavoriteRepositoryTest > overlappingWritesKeepBothWrites FAILED`
  `java.lang.AssertionError: expected:<[First, Second]> but was:<[First]>`
  at `FavoriteRepositoryTest.kt:816` (the persisted-order assertion)

Lost update reproduced deterministically: without the lock, the parked first
write persists its stale snapshot last and the second group is gone.

## Restore

Reverse edit applied; `git diff -- app/src/main/java/com/naviveylin/data/FavoriteRepository.kt`
empty against the worktree HEAD, `git status --porcelain` on the file empty —
no mutation marker remains.

## Green — `evidence/revert-check-green.log`, `evidence/revert-check-green.xml`

- exit 0, `BUILD SUCCESSFUL`
- XML: `tests="50" failures="0" errors="0"`

Copies also at `items/873/revert-check-{red,green}.{log,xml}`.
