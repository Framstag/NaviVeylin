# AAOS emulator recon (task 5.4, 5.5 car half) — why the car UI is not reachable from this session

Device: `emulator-5556`, AVD `Automotive_Distant_Display_with_Goog…`, SDK 33, x86_64, boot completed.
App `com.framstag.naviveylin` installed (code 19), reinstalled **today 12:37** — by the other session, from the
shared worktree, so whether the installed APK carries this change's JNI code is **unknown until a search runs**.
Maps present: `basemap`, `europe` — the warmup log names the database it opened:

```
D Diag/WARMUP: openDatabase(nordrhein-westfalen-27-20260820-0826) -> true
D Diag/SESSION: Pop to RootScreen
```

i.e. the **NRW** database, whose searches are 993–4310 ms — exactly the slow database task 5.3's `aborted=true`
half and 5.6 need.

## Why it cannot be driven from here

```
display 0   INTERNAL   1080x600   car launcher / no accessibility nodes at all
display 2   EXTERNAL    400x600   HDMI
display 3   EXTERNAL   3000x600   HDMI
display 4   VIRTUAL     400x525   cluster            (ClusterOsDouble-VD)
display 5   VIRTUAL    1500x588   DistantDisplay-NavigationViewSurface (systemui)
display 6   VIRTUAL    1500x588   DistantDisplay-RootViewSurface       (systemui)
            VIRTUAL    1440x704   com.framstag.naviveylin,CarAppService   (state OFF)
```

- `uiautomator dump` of the default display lists **no** text or content-desc nodes, and `mCurrentFocus=null`
  — the car templates render on the systemui *distant display* virtual displays, not on display 0.
- `input` supports `-d DISPLAY_ID`, so a tap *could* be aimed at display 6 — but only with coordinates, and
  coordinates would have to come from a screenshot of that display.
- `screencap -d 6` works (a screenshot of display 0 came out as 21 139 bytes, i.e. something *is* drawn), but
  **this session has no way to view an image**: the tool list has no `view_image` and the MCP gateway reports
  `0/0 servers, 0 tools`. Per `AGENTS.md`, screenshot reading needs that pinned Pi package.
- A deep-link detour was tried and rejected: `am start -a android.intent.action.VIEW -d "geo:0,0?q=Bochum"`
  reached the device mangled (`dat=geo:`) and, even unmangled, the car path turns a parsed destination into
  *navigation* (spec `auto-deep-links`), not into the search template this change measures.

## Update after the human-in-the-loop attempt (2026-10-15, later)

**The car search cannot be driven on this AVD at all**, for three independent reasons, each verified:

1. **The host's search field takes no text.** The host renders the template's field as its own toolbar widget and
   exposes it with a null input type; the host log says so twice:
   ```
   W InputMethodManager: Ignoring showSoftInput() as view=com.android.car.ui.toolbar.CarUiEditText{... car_ui_toolbar_search_bar} is not served.
   I GoogleInputMethodService: onStartInputView(EditorInfo{packageName=com.framstag.naviveylin, inputType=0, inputTypeString=NULL …})
   ```
   So the soft keyboard shows but commits nothing, desktop typing commits nothing, and `onSearchTextChanged` never
   fires — no letters, and no `search: slot=car` line. (The search *screen* itself did open: the host logged
   `Host received new template (type: androidx.car.app.model.SearchTemplate)` twice, i.e. the initial build plus
   the one after the history load.)
2. **Injected touch does not reach the car UI.** The templates render into a `TemplateSurfaceView`
   (`0,0-1080,600`) — a Surface, so `uiautomator` reports no nodes on any display — and the app's own car display
   reports `touch NONE`. A sweep of `input -d 6 tap …` and `input -d 10 tap …` produced no screen-stack line while
   the app was confirmed alive. The AVD window's mouse *does* work (that is how the screens were opened), so only
   a human at the window can click rows here.
3. **The app runs in user 10, and I can neither write nor read its data.** `run-as` defaults to the current user
   and this build rejects `run-as --user` (`run-as: unknown package: --user`), while `adb root` is refused
   (`adbd cannot run as root in production builds`). The history file seeded through plain `run-as` therefore went
   to *user 0's* data directory, which is why the car's "Letzte Suchen" and the "Suchverlauf" screen both stayed
   empty — a useful trap for the next run: on an AAOS AVD, seed the history with `run-as --user 10 …` or not at all.

**Consequence:** tasks 5.4 and 5.5's car half need a different instrument: a **userdebug AAOS AVD** (where
`adb root` works, so the history can be seeded and only two taps are needed from a human), or a **phone AVD +
Desktop Head Unit** (projection forwards real keyboard input), or a real head unit.

## Gap found in this change: the car POI screen is not wired to the lifecycle

`auto/src/main/java/com/naviveylin/auto/PoiSearchScreen.kt` is the only car search screen besides `SearchScreen`,
and it is the only car search a person *can* trigger on this AVD (mode row → category → button: taps only). It
still calls `OSMScoutClient.searchPOIs(...)`, i.e. the **standalone wrapper**, which begins and ends its own POI
request: sound, but not abandonable — leaving that screen cancels the screen's job, not the native walk.

The change's `poi-search` delta says "A POI search SHALL run in the POI surface's own search slot … and leaving the
POI search view SHALL abandon the running POI search". As worded, "the POI surface" includes the car POI screen, so
the requirement is only half implemented (the phone sheet is wired, task 4.2; the car screen is not). Either wire
that screen onto a gate of its own slot, or narrow the requirement's wording to the phone sheet at archive time.
Wiring it has a second benefit: it is the one car search a human can drive on this AVD, which would make a
car-surface abort measurement possible with two taps.

## Instruments that would unblock the car tasks

1. **A userdebug AAOS AVD** (an AOSP image without Google Play, where `adb root` works): with root I can seed the
   history in *user 10*, and then a human needs only two taps (Search row → the seeded query row) — no typing.
   This is the cheapest route now that the type-in path is proven dead on the Play image.
2. **A phone AVD + Desktop Head Unit** (projection): the DHU runs on the host and forwards real keyboard input, so
   the car search *can* be typed there — by a human, or by me with `view_image` available to aim.
3. **`view_image`** (the pinned Pi package `AGENTS.md` names) on its own is necessary for any automated car work:
   without it a screenshot cannot be read, so neither the screen state ("the recent-searches rows are empty") nor
   the coordinates for `input -d …` can be established. Note it is *not* sufficient on this AVD: the car display
   reports `touch NONE`, so injected taps do not reach it even with correct coordinates.
4. **A real head unit** — the reference instrument the specs are written for.

For **5.3's second half and 5.6** the missing piece is a *phone-flavour* device with a **slow map**, nothing about
vision: the phone search UI is fully addressable (search button at (85,338), field at (540,226) on the mobile
build; see `device-phone-search-lifecycle.md`). The copy-from-the-car idea is dead — that map lives in user 10,
which is unreadable without root — so the phone AVD needs its own map, and its guest network works (ping 8.8.8.8:
21 ms), so the app's own map manager can fetch one while I drive that UI.
