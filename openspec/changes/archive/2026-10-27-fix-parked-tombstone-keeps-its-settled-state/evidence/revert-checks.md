# The revert-check — mutation 5.17

One invariant is new beyond the symptom, so it carries one revert-check: only an item whose body keeps a
settled state is restored to it. `bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --only 5.17`,
2026-10-27, from the change root:

```
5.17	resolver#cleanParkedItemIsReopened	only an item whose body keeps a settled state being restored to it — a mutation that restores every reversal settles an item nobody wanted settled (#681)
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/resolve-stale.sh
  GREEN(exit 0)  restored, and the case passes again

mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)
mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore
```

The mutation replaces the guard `if records_settled_state "$body"; then` with `if true; then`, so every
reversal writes `status/retired`. `resolver#cleanParkedItemIsReopened` — a parked item with no settled record,
which must return to `status/open` — then goes red, which is the half a fix that restored every reversal would
have broken silently. The restore was byte-exact (`1 occurrence(s) replaced`, restored green), and the gate
afterwards ran with the file as this change leaves it (`gate.md`).
