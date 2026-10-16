# Provenance of this change's evidence — what a tally is worth

Two Gradle builds in one project directory write the same `app/build/test-results/*` and
`*/build/tmp/*`, so **both** tallies become inadmissible as evidence
(`ki_processing_failures.log` 2026-10-08; `AGENTS.md` "one builder per working tree", `TODO.md` §40 item 4).
That happened here on 2026-10-15: my 5.2 gate (`pid 1025947`, `test -PforceTests --rerun-tasks`) ran while
another session's gate (`pid 1036964`, `test -PforceTests --no-build-cache`) did, and the other session's
diagnostics show the symptom — 36 FAILED with 35 × `AppNotIdleException: Compose did not get idle … in 60
SECONDS`, i.e. 35 min of a 41 min wall clock spent in timeouts, not in assertions.

## Admissible regardless of other builders

| evidence | why it stands |
|---|---|
| `device-phone-search-lifecycle.md`, `device-phone-search-abort.md`, `device-car-search-lifecycle.md`, `aaos-device-recon.md` | device `adb logcat` lines and UI dumps, no Gradle involved |
| `revert-check-2.1{1,2,3}-*.txt` | the submodule's meson host suite, a separate build tree |
| `native-slice-verification.md` (native half) | `ninja -C build osmscout_client_java` and `meson test -C hostbuild` in the submodule |
| `standalone-forms-decision.md` (the grep + KDocs) | source reading, no build |
| `submodule-commit-and-pin.md` (the commit, the pin, the staged-blob verification) | git operations and content greps, no Gradle |

## Admissible only from a quiet tree — re-run when the tree has one builder

| evidence | state |
|---|---|
| `forced-gate-split.md`, first run | observed split, exclusivity **unproven** (several builders were active) |
| `forced-gate-split.md`, second run | **void** — concurrent with `pid 1036964` |
| `revert-check-3.6-*.txt`, `revert-check-3.7-*.txt` | focused `:core`/`:app` runs; admissible only if no other Gradle build wrote those modules then |
| `revert-check-4.5-*.txt` | focused `:auto` run, same caveat |
| the tallies quoted in `standalone-forms-decision.md` (`:core`, `:app`, `:auto` suites) | focused runs, same caveat |

## What that means

- **A gate is long and must not depend on the shell call that starts it.** The run attempted on 2026-10-15 for
  task 5.2 was killed when its own shell call was aborted (the log ends mid-failure-output, no test task alive),
  which voided it just as a second builder would have. Start it detached (`nohup ./gradlew … &`), then read the
  log and the XMLs; that way an aborted call cannot cost 20–40 minutes of machine time.
- No *code* claim in this change rests on a Gradle tally: the two red-case sets went green in focused runs,
  and the eight revert-checks' *red* observations are the load-bearing half (a mutation failing a named case);
  the green halves are confirmations.
- The one task that needs a full gate is 5.2, and its verdict is the operator's to take from a quiet tree:
  `./gradlew test -PforceTests --rerun-tasks`, then compare against the suites listed in
  `forced-gate-split.md`. Until then 5.2 stays unchecked — a void tally is not evidence either way.
