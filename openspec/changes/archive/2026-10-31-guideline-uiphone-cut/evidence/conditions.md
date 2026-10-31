# document-size conditions — 4 -> 3

Row `document-size` (tools/sensors/sensors.json): scope `normative-document`, bound 32 000 B,
comparison greater-than. One condition per normative document past the bound.

`bash tools/sensors/run.sh --only document-size --json` (dry-run):

```
doc-context: UIPhoneMap.md bytes=29340 lines=382 (bound 32000 bytes)   <- clear
doc-context: UIPhone.md    bytes=19375 lines=261 (bound 32000 bytes)   <- clear
doc-context: MapRendering.md bytes=46751   <- violation
doc-context: UI.md           bytes=45324   <- violation
doc-context: Backlog.md      bytes=35890   <- violation
```

Three violations filed (dry-run): Backlog.md 35890, MapRendering.md 46751, UI.md 45324.
Before the cut there were four (those three plus UIPhone.md 47926). **4 -> 3.** The two documents
this cut produces are both clear.
