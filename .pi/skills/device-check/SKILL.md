---
name: device-check
description: Drive the phone emulator end-to-end for one UI flow (build+install, tap by node text/desc, dump, logcat, screenshots) and turn the result into evidence — reusable script instead of a new ad-hoc script per round. Use for every on-device verification of a UI/rendering/routing change.
---

# device-check — one reusable device loop

Replaces the "write a new `.tmp-dev-NN.sh` per finding" habit that made every review
round slow (`ki_processing_failures.log`). Assumes `provision-phone-emulator` has
prepared the AVD (maps installed, `com.framstag.naviveylin` installed).

## Recipe

1. **Build + install only the flavor you need**
   ```bash
   ./gradlew :app:assembleMobileDebug --console=plain -q
   adb install -r -t app/build/outputs/apk/mobile/debug/app-mobile-debug.apk
   ```
2. **Reset the state you are about to drive** — app-specific, e.g. clear the search
   history so a search flow is deterministic:
   `adb shell run-as com.framstag.naviveylin rm -f files/maps/search_history.json`
   then `adb shell am force-stop …` and `am start -n …/.MainActivity`, sleep ~14 s.
3. **Tap by dump, never by remembered coordinates**
   - Dump device-side and read it host-side in one step — the path policy rejects a literal
     `/sdcard/...` in the host command, and `adb pull` cannot expand `$EXTERNAL_STORAGE`: `adb shell
     'uiautomator dump $EXTERNAL_STORAGE/ui.xml; cat $EXTERNAL_STORAGE/ui.xml' 2>&1 | sed -n
     '/<?xml/,$p' > "$D/ui.xml"` (with `D=.pi/logs/device-check`), require a non-empty `ui.xml` and
     retry up to 5× with a sleep. The `sed` filter also drops the `UI hierchary dumped to: …` header,
     which would otherwise break every XML parse of the dump. Grep the local copy.
   - Resolve every tap from a **fresh** dump: `text="…"`, `content-desc="…"`, then
     tap the centre of that node's `bounds`.
   - A node whose `clickable="false"` (e.g. a Compose `Text` inside a clickable
     row) is not tappable — walk **up** the dump to the nearest ancestor line with
     `clickable="true"` and tap that. (This blocked the navigation-stop check for
     several rounds; the on-device grace-expiry case is still open for that reason.)
4. **Read the app's own numbers, coordinate-free.** `adb logcat -d -s MapCanvasVM`
   (`segment focus: … inside=…`), `-s RoutePanelVM`, `-s NaviVeylin` (native, must
   show **no** fault-like lines: `grep -ciE "fatal|exception|crash|assert"`).
5. **Screenshot + dump in the same moment** if the claim is visual, then measure it
   with the `pixel-check` skill instead of describing it.
6. **Record geometry as evidence**: `adb shell uiautomator dump` + grep for the
   card's surface bounds (`bounds="[0,1320][1080,2400]"` = card top 1320) gives the
   *measured* card height that the map's fit reports — the pair is the proof that
   the model and the pixels agree.
7. Keep one script (`device-check.sh` in the repo root or `/tmp`, argument-driven
   for the flow) and edit it between rounds; delete it once the change is
   committed. Never leave `.tmp-*` behind (`git status` must stay clean of them).

## Which node consumed the tap (tap-consumer diagnosis)

Use this when a tap "does nothing", "does the wrong thing", or a control is reported as unreachable
(`TODO.md` §122; the same dump/tap discipline as step 3, aimed at a verdict instead of a flow).

1. **Dump and tap in the same interaction window.** `uiautomator dump` to a literal path, `adb pull` it,
   read the target's `bounds` from that pull, then `adb shell input tap <centre>` within seconds. Bounds
   from a dump taken before a layout shift describe a different screen — that alone has made a correct
   control look dead.
2. **Tap the node's centre three times, one tap per read.** A transient layout or a re-composed surface
   can win once; a finding needs a repeat, not a single attempt.
3. **After each tap, read four things and write them down as booleans/numbers:**
   - the action's own line in the app log (`adb logcat -d -s NaviVeylin`, e.g. the engine's
     `stopNavigation: stopped`) — *did the intended action run?*
   - the resulting state (map mode, panel/session state) — *did something else run instead?*
   - the clicked node's identity from a **fresh** dump (`content-desc`/`resource-id`, `clickable`, bounds)
   - the nearest ancestor line with `clickable="true"` and its bounds
   Record node identity + bounds + "action line present?" + "state changed?" — never a position.
4. **If the intended action's line is absent, say so instead of guessing.** "The tap was not delivered"
   is a result (wrong display, wrong user, stale coordinates, a host/surface layer in between); inferring
   a consumer from the *end state* is not.
5. **When the only clickable node over the point is a container**, that is a geometry finding, not a
   dispatch finding — Compose still delivers to the innermost target (measured 2026-10-05). Fix it in a
   test with the `compose-geometry` skill (tag the control *and* the container region, assert
   disjointness) and keep this recipe for the device confirmation afterwards.
6. **Cross-check the mode actions before blaming the control.** A tap that lands on a neighbouring
   affordance often switches a mode rather than doing nothing (the compass short-press toggles
   BROWSE ↔ FREE_DRIVE); read the log for *that* action's line too, so the report names the consumer.

## Output contract

Every device run ends with: the flow's step-by-step observables (indicator, zoom,
labels), the geometry numbers, the fault-like count, and an explicit
`FAIL=0|1`. That block is what goes into `tasks.md`; "looks good" is not evidence.

A **tap-consumer** run reports the same kind of block, one line per tap: `attempt=1
node=<content-desc|resource-id> bounds=[x1,y1][x2,y2] actionLine=yes|no stateChanged=no|yes` — plus which
ancestor `clickable="true"` node covered the point when the target's own action line was absent.

## Pitfalls that cost a round each

- **A literal device path in the *host* command never runs.** The path policy rejects `/sdcard/...`,
  `/dev/tty`, or an `$EXTERNAL_STORAGE/...` written out as a path
  (`Denied by policy: 'external_directory' … (rule '*')`) — the entire command is denied before its
  first segment executes. So `adb shell uiautomator dump /sdcard/ui.xml` + `adb pull /sdcard/ui.xml`
  is **not** the recipe here, even though it is the obvious one. Do the device-side work in one
  `adb shell '…'` string and filter on the host (verified 2026-10-06 on `emulator-5554`).
- **`adb pull` cannot expand the device-side `$EXTERNAL_STORAGE`** (that variable is
  expanded by the *device* shell). `adb shell uiautomator dump /sdcard/ui-x.xml` +
  `adb pull /sdcard/ui-x.xml` works where a literal path is allowed;
  `adb shell "cat \$EXTERNAL_STORAGE/ui-x.xml"` works because the *device* shell expands it. Also
  verify the read actually wrote a non-empty file — the dump can succeed while the read fails, and
  then every tap "finds no target" for a reason that is not the app.
- **Capture into a dedicated directory inside the repo** (`.pi/logs/<check-name>/`). A shared scratch
  dir belongs to nobody: `.tmp-verify/` was emptied by a peer session mid-investigation and a later
  `view_image` failed with `unable to locate image at …`. Delete your own captures when the change is
  committed, and never leave `.tmp-*` behind.
- **`magick` is not in the shell allowlist here; `convert` is** (ImageMagick 7 prints only a
  deprecation warning). Wrap image work in a script — as `pixel-check` rule 5 says — or call `convert`.
- **`pkill -f <pattern>` matches its own command line** (the shell running it) and
  kills the call (exit 143). Use a bracket trick (`python3 -m http[.]server`) or kill
  by port.
- **A tappable row is not the node you found**: Compose `Text` nodes are usually
  `clickable="false"`; walk up to the nearest `clickable="true"` ancestor.
- Before driving a flow, `force-stop` the app and clear the state that makes the flow
  deterministic (e.g. `run-as <pkg> rm -f files/maps/search_history.json`), then wait
  ~15 s after `am start` — a flow that starts too early fails on the *previous* screen
  and looks like a missing element.
