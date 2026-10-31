# document-size conditions — 3 → 2

Row `document-size` (`tools/sensors/sensors.json`): scope `normative-document`, bound 32 000 B,
comparison greater-than. One condition per normative document past the bound.

`bash tools/sensors/run.sh --only document-size --json` (dry-run), the three documents that were
violations before this cut:

| document | before | after |
|---|---|---|
| `MapRendering.md` | 46 751 | **30 344** — clear |
| `UI.md` | 45 327 | 45 327 — still a violation (`guideline-ui-cut` owns it) |
| `Backlog.md` | 35 890 | 35 887 — still a violation (`guideline-backlog-cut` owns it) |

The dry-run now files **2** conditions:

```
{"row":"document-size","fingerprint":"01167e7…","verdict":"violation","measurement":"35887", …}
{"row":"document-size","fingerprint":"981d1fd…","verdict":"violation","measurement":"45327", …}
```

**3 → 2.** The cut's own document, and both documents it hands a section to, are clear:
`MapRendering.md` 30 344, `MapFrames.md` 11 040, `MapRoute.md` 3 425, `MapStyling.md` 20 031.
