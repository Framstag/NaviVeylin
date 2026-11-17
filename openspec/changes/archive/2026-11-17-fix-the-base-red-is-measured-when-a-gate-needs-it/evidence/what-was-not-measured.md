# What this change does not measure, and how a night would

The issue asks for two figures **after** the change, and neither can be taken by one session: they need a run of the
loop with real items.

1. **The run's start to its first item.** Before: `progress base-red` printed the pre-flight at
   `loop: base-red · <elapsed> · measuring the profile's own steps on <base>`, and the two giants were 828 s and 423 s of
   that block (1 772 s of step time in the measurement of 2026-11-16, 29 minutes of wall). After: no measurement runs
   before the first item, so the run's own `progress` lines go from the checkout to the pre-screen without a
   `base-red` phase. Reading: `jq -r '.run.startedAt'` against the first item's `startedAt`, or the run's own
   `loop: …` lines.
2. **`run.baseRed` keys against the steps the items ran.** The record should now hold only steps some item's gate went
   red at. Reading, over the archive's runs after this change:

   ```
   jq -r '.run.baseRed // {} | keys[]' .pi/issue-loop/runs/*.json
   jq -r '.items[]?.metrics.gate // empty' .pi/issue-loop/runs/*.json
   ```

   and the same over `run.baseRedUsed.steps`, which is the only half a run writes today: a record key that
   `run.baseRedUsed` never names is a step a gate measured and no gate subtracted — which is a step that was red for a
   case the delta also broke, and is therefore read as a pair and never as a wasted measurement.

The figure the issue starts from — 1 251 of 1 772 s in steps no item consults — is the pre-flight's own measurement, and
it is what the change removes from the start of every run; the per-night figure is the sum of the `LOOP-GATE-BASERED-MEASURE`
lines' steps, which the item's own wall now carries.
