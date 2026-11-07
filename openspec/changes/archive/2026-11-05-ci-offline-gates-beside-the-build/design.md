# Design — the gate's cost belongs to a runner that is not building

## Root cause

`.github/workflows/build.yml` declares one job, `build`, and a job is executed by one runner, step after step.
Everything the repository checks is therefore ordered against everything else the repository builds, and the only
order available is "the cheap ones first". Measured on 2026-11-04 (run 41810844107, job 126509396527): steps 5–24
occupy 09:56:24 → 10:20:00 — **23 min 36 s** — and the first step that needs the SDK (`Cache Android SDK`) starts
after them, while `Install vcpkg dependencies` starts at 10:22:55 and ends 57 min 10 s later.

Two facts make the split free rather than a trade:

1. **The checks need nothing the runner does not have.** Every offline step's own comment states it: no token, no
   network, no Gradle, no device, "nothing the build does not already have (git, jq, JDK 17)". They are shell and
   JDK work over a checkout.
2. **They are not an input to the build.** Nothing in the build reads the gate's verdict; the two are only joined
   by the run's final status. So their duration is latency on the job, not work the job depends on.

## The split

Two jobs, started together:

- `gates` — `runs-on: ubuntu-24.04`, `permissions: contents: read`, no cache, no Android SDK, no mono, no vcpkg.
  It carries the twenty offline steps, the slowest of which is `Check agent issue loop` at 17 min 15 s.
- `build` — unchanged, minus those steps, keeping the two checks that read the tree the build compiles
  (`Check libosmscout Android-free outside Android/`) and the ones that need the SDK or the build
  (`Check the JVM-only test graph`, `Check the dex gate's producer`).

The measured result to expect on the next dispatch run: the build job's own clock loses 23 min 36 s of
*preceding* steps, the gate job finishes long before `Install vcpkg dependencies` does, and the run's duration is
the build path alone.

## Why not the alternatives

- **Keep one job and order the checks better.** Ordering cannot help: the sum of the steps is the cost, and
  ordering only decides which sum is paid first. Measured: the five slowest offline steps are 22 min 59 s of the
  23 min 36 s.
- **`needs:` the gates from the build.** That is today's shape with extra steps — it keeps the serialisation and
  adds a scheduling hop.
- **Make the checks cheaper instead.** That is a separate item (the loop's self-test is 17 min 15 s on its own, and
  its own items exist for that). This change is worth taking first because it is twenty lines of YAML and it is
  the prerequisite for nothing.
- **A `matrix` over the gates' groups.** The right next step for the suite (`loop-suite-legs-across-runners`), not
  for this change: it must not be needed for the split to pay.

## Why no case

The property is a property of `.github/workflows/build.yml`: which job a step is written in. A case over it would
have to parse the workflow and assert a step's job — a check whose red is a YAML edit and whose green says nothing
about whether the moved steps still run. The measurement this change rests on is the run's own step clock, which
is read per run and recorded in `evidence/`; the alternative, a second definition of "offline" inside a checker,
is what `guidelines/BuildGate.md` §2a warns about when it says a measured baseline is not evidence for a rule.

## Blast radius

- No engine under `tools/` changes, so no case set, no mutation set and no tally changes shape.
- The repository's branch protection reads the workflow's job names; the run's verdict is now two jobs, so a
  required check configured as `Build debug APK (all ABIs)` alone would go green while `gates` is red. The change
  records both names in `guidelines/Build.md` §3 so the setting can be read against the workflow.
- A red `gates` job no longer stops the build before vcpkg spends 57 minutes; that is the accepted cost of the
  split, and it is why the cheap checks stay in front of the build.

## Open decisions

none
