# Group 2 (tasks 2.1–2.6) — where it landed, and what its tallies were

Group 2 was worked by the tracker item #899 ("The phone has no settings surface"), which merged on
2026-11-16 as commit `905d1dcc` (*fix: the-phone-has-no-settings-surface-21-con (#899)*), merge commit
`2017553d`, and was followed by `024f435d` (the documentation routes the landed rows added).

The boxes are ticked in this change's `tasks.md` rather than in a second archive, because this change
still carries groups 3–8 and archives once when the last of them lands. This page is the record the
ticks point at, so a reader does not have to trust a checkbox.

## What the commit contains, per task

| task | files in `905d1dcc` |
|---|---|
| 2.1 | `app/src/main/java/com/naviveylin/ui/settings/SettingsSurface.kt` (root page, topic page, row renderer) |
| 2.2 | `ui/map/MapCanvasScreenModalBands.kt` — `SettingsSurfaceHost` composed in `MapLayer.MODAL` |
| 2.3 | `ui/map/MapCanvasScreenParts.kt` (the `MapMenu` entry), `ui/MainScreen.kt` (the start screen's entry and host), `ui/map/LocationOptionsOverlay.kt` (the sheet's entry row) and the three `MapCanvasScreenChromeBand.kt` call sites |
| 2.4 | `BackHandler` in `SettingsSurface.kt`; `SettingsSurfaceBackTest` |
| 2.5 | the conditional-row and link-row renderers in `SettingsSurface.kt`; `SettingsSurfaceTest` |
| 2.6 | `guidelines/UIPhoneMap.md` sections 7/8 and `guidelines/UIPhone.md` section 20, with the `AGENTS.md` route |

## The case tallies, quoted

The XML reports the run produced are no longer on disk — the worktree of #899 was removed after the
merge. The figures below are quoted from the item's own record, `.pi/issue-loop/items/899/worker-continuation-report.md`,
which copied each file at production time:

```
green-settings-surface-testMobileDebugUnitTest.xml      tests=12 failures=0  (not retained)
green-settings-surface-testAutomotiveDebugUnitTest.xml  tests=12 failures=0  (not retained)
green-settings-back-testMobileDebugUnitTest.xml         tests=1  failures=0  (not retained)
green-settings-back-testAutomotiveDebugUnitTest.xml     tests=1  failures=0  (not retained)
red-start-screen-entry-mobile.xml                       tests=12 failures=1   (revert-check, 2026-11-15T22:13:33Z, not retained)
green-after-restore-mobile.xml                          tests=12 failures=0   (2026-11-15T22:15:37Z, not retained)
```

The revert-check the change's own discipline owes is the `red-start-screen-entry` pair: `MainScreen`'s
Settings entry was mutated to call nothing, `SettingsSurfaceTest#theStartScreenMenuOpensTheRoot` failed
with "The component is not displayed!", and the exact restore ran green.

The four cases the issue's own `**Verify**` line names all exist in the merged tree and are green on
the base of the run that read this change:

- `SettingsSurfaceTest#theMapKeepsRenderingWhileTheSurfaceIsOpen`
- `SettingsSurfaceBackTest#backLeavesTheTopicThenTheSurface`
- `SettingsSurfaceTest#theStartScreenMenuOpensTheRoot`

## Re-taken on 2026-11-17

Rebuilding this change directory after the local loss, the readiness gate and the artifact hygiene
check were re-run over it:

```
$ bash tools/check-change-readiness.sh openspec/changes/settings-taxonomy-and-surface
LOOP-CHANGE-READY: ok
```

The tasks 3.x–8.x boxes are untouched by this page: they are open, one tracker item each.

## Why this directory carries no other evidence

Group 1's tallies, logs and review records are in the archive `2026-11-14-settings-taxonomy-and-surface`,
where they belong to the commit they were taken on. They are deliberately **not** copied here:
`tools/loop/measure.sh` sums the `*.xml` under a change's `evidence/` into the item's reported tally, so a
copy taken on another tree reads as this tree's measurement — which is exactly what item #903 reported
when it found the killed #898 attempt's `SettingsControllerTest` and `SettingsRegistryTest` XMLs here, and
the red `gate.log` behind them, on a base where those classes did not exist. The copies were moved to
`.pi/recovery-untracked/openspec-stale/settings-taxonomy-and-surface-evidence/` when this directory was
rebuilt; a reader who wants them finds them by that path or in the archive.
