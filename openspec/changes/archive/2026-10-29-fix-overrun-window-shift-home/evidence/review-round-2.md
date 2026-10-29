# Review round 2 — independent, read-only (`delegate`, 2026-10-29)

Verdict: **FAIL** — three bookkeeping lines, no claim about the code.

- **1. evidence.md claims vs files** — FAIL. Everything checked out except two lines:
  `evidence.md`'s "Each selection's own XML is committed beside this file" named `green-core.xml` as one of
  the focused selections' XMLs, but that file is the guard's green run of the red→green pair (16:54:38Z)
  while the nine `TEST-*.xml` files are the later focused runs (17:04Z); and the three build durations
  ("9 s", "1 m 12 s", "15 s") are carried by no file. Red-on-HEAD, the nine tallies, the revert-check pair,
  the gate verdict and executed tasks, the six shell steps and the four hand-run checks all matched.
- **2. Counts** — FAIL. `proposal.md`'s surface sentence said the change touches "the 14 Kotlin files listed
  in Impact", while the Impact section enumerates 19. (16 rewritten + 3 added, 17 tracked + 3 untracked and
  the ten moved cases were all correct.)
- **3. Tree agreement** — PASS. No live `displayOffsetPx` in `*.kt`; the only two `*.md` mentions are the two
  shipped spec lines the deltas replace; `guidelines/MapRendering.md` carries the nine repointed mentions.
- **4. Tasks** — PASS, one note: `tasks.md` 5.2 spells the gate with `-PforceTests --PjvmTestsOnly`, which is
  `guidelines/Build.md` §3's line; `gate.log` does not repeat its invocation.

Corrections applied in response (round 3 re-read them):

1. `evidence.md` §2 relabels `green-core.xml` as the guard's red→green half, keeps the nine `TEST-*.xml`
   files as the focused runs' record, and states that the runs' wall-clock times are not carried by any file
   — the timestamps are the record.
2. `proposal.md`'s "14" → "19" Kotlin files.
