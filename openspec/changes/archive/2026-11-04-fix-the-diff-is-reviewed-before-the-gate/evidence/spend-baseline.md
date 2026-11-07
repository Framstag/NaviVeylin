# The spend baseline this change is measured against

`python3 tools/loop-spend.py --json`, 2026-11-04, over this project's own archive at `.pi/issue-loop/items`
(237 item directories, 227 of them carrying an event stream):

```
items=227 sessions=558 turns=33176
tokens total=2591297925  (input 68712975 + cacheRead 2502371194 + output 20213756)
afterFirstGate=1581306283   -> 61.0 % of the archive is charged after the item's first gate
byRole worker   sessions=332  tok=2168195971  turns=25654   (84.5 k per turn)
byRole reviewer sessions=226  tok=402888198   turns=7522    (53.6 k per turn)
baseline median=17053 charged=471764956
```

The numbers are the ones issue #758 cites: 1 581 of 2 591 Mtok — 61 % — after the first gate, and the reviewer at
53.6 k tokens per turn against the worker's 84.5 k. `spend-baseline.json` is the run's own output, unfiltered for the
keys this change's claim rests on.

## What is **not** measured here

The other side of the comparison. This change cannot measure its own saving: the archive above is the *before* set,
and the *after* set is what the loop spends on items landed once this change is in the base. The command that takes
it is the same one, per day, and the comparison is per item — the change's `design.md` names the shape it measures
(one round before the gate, one gate child, the outcome arm) and leaves the saving as a model estimate:

```
python3 tools/loop-spend.py --json --since <the day this landed>
```

A useful reading of the after set, when it exists:

- `afterFirstGate` as a share of `tokens.total` — 61.0 % here; the pre-gate round and the gate child move work from
  after the first gate to before it, so this share is the number this change is about.
- the count of sessions that ran the gate three or more times: 117 of 227 items here, and the shape a refusal before
  the gate no longer produces.
- `baseline.median` per item, against the extra child a **passing** item now pays.
