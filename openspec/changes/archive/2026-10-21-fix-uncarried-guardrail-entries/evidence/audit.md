# Audit — issue #69 (`nvid` §134) against the tree at base `44b673f`

One row per entry of the record (62–98). The record's convention: `[open]` marks an entry whose named
artifact does not carry the rule yet; an entry without the marker is a cross-reference to a rule that already
exists. Every line number below was taken with `grep -n` on the tree at `44b673f424a75dbfae97e704f7e8ece03f3670b2`
(2026-10-21). No entry in the record's body carries `[open]` — `grep -n '\[open\]'` matches the legend line only.

| entry | carrier the entry names | where the rule is stated today | state |
|---|---|---|---|
| 62 | `Build.md` §2 "Liveness guard and shell recipes" | `guidelines/Build.md:141` heading; `pgrep -af 'gradle-wrapper\.ja[r]'` at `:147` | carried |
| 63 | `AGENTS.md` rule 4, `Build.md` §2 | `guidelines/Build.md:132` "One builder per working tree" | carried |
| 64 | "(Extends §40.4)" | `guidelines/Build.md:172-177` carries the `--status`/`--stop` half, results removal and per-module split; `grep -n 'max-workers'` and `grep -n 'free -m'` over `guidelines/`, `.pi/skills/`, `openspec/specs/` → 0 hits | **partial** |
| 65 | "(The skills carry it)" | tasks at `guidelines/Build.md:311`, `:210-221`; executed-task rule at `openspec/specs/build-test-gate/spec.md:50`. The *rule* — a whole-suite run adds `--continue` — is stated nowhere. The flag itself does occur, and only incidentally: `guidelines/Build.md:211` inside a recorded baseline invocation, `.pi/skills/run-tests/SKILL.md:50` (re-run one failing module) and `:133` (a measured coverage invocation), and `guidelines/Backlog.md:34` (a different flag, `tools/issues/answer.sh --continue`). No section states the rule, so the skill that the entry names uses `--continue` without stating when a whole-suite run needs it | **partial** |
| 66 | `Build.md` §2 | `guidelines/Build.md:172-177` (`in-progress-results-generic.bin`, `rm -rf app/build/test-results/<flavor>`) | carried |
| 67 | `Build.md` §2 | `guidelines/Build.md:152-157` (`xargs -r`, `awk` vs `bc`, `perl -0pi`, `lean-ctx allow desktop-head-unit`) | carried |
| 68 | `Build.md` §2 (shell traps; init-script traps) | `guidelines/Build.md:158-162` (zsh split, `adb` stdin), `:163-167` (init-script traps) | carried |
| 69 | `Build.md` §2 | `guidelines/Build.md:168-171` (`prune-native-configs.sh`, refuse an ABI grandchild, same arguments) | carried |
| 70 | `Design.md` §12 "Change hygiene when editing files" | `guidelines/Design.md:765-768` | carried |
| 71 | `Design.md` §12 | `guidelines/Design.md:767-769` ("One writer per file per turn") | carried |
| 72 | `Design.md` §12 (via §40.9–§40.11) | `guidelines/Design.md:769-771` ("Split a large or function-spanning `oldText` on function boundaries") | carried |
| 73 | "(Cross-ref §40.24)" | nothing: `grep -rn 'KotlinNothingValueException' guidelines/` → 0; §40.24 itself is `guidelines/Design.md:338`, the `@JvmStatic`-object rule | **absent** |
| 74 | `Build.md` §4 "Native verification" | `guidelines/Build.md:529-533` | carried |
| 75 | `Design.md` §11 | `guidelines/Design.md:627-630` | carried |
| 76 | §40.20/§40.21/§40.29 (extends) | `Design.md:676-680` and `Build.md:669-681` carry the 320×470 dp clamp and the shadow canvas; `grep -rn 'PartiallyExpanded\|getLatestDialog\|LifecycleRegistry\|assertIsDisplayed' guidelines/` → the `assertIsDisplayed`-below-the-edge fact only (`Build.md:677`), none of the other five clauses | **partial** |
| 77 | `UI.md` §1 "Drag surface checklist" | `guidelines/UI.md:156-176` | carried |
| 78 | `Design.md` §11 (and `Build.md` §4) | `guidelines/Design.md:631-637` | carried |
| 79 | `Design.md` §11 | `guidelines/Design.md:638-645` | carried |
| 80 | `Build.md` §4 | `guidelines/Build.md:476-479` | carried |
| 81 | `Build.md` §4, `revert-check` skill | `guidelines/Build.md:514-526`, "One mutation per revert check" at `:520` | carried |
| 82 | `Build.md` §6 | `guidelines/Build.md:806-810` | carried |
| 83 | `Build.md` §4 | `guidelines/Build.md:487-492` | carried |
| 84 | `Build.md` §4, `Design.md` §4 | `guidelines/Build.md:492-497`, `guidelines/Design.md:665-668` | carried |
| 85 | `Build.md` §10 "Device recipes that cost a round every time" | `guidelines/Build.md:1460-1474` | carried |
| 86 | `Build.md` §10 | `guidelines/Build.md:1475-1479` | carried |
| 87 | `Build.md` §10 | `guidelines/Build.md:1481-1486` | carried |
| 88 | `Build.md` §10 | `guidelines/Build.md:1488-1492` | carried |
| 89 | `Build.md` §10 "Multi-user and a11y-less car surfaces" | `guidelines/Build.md:1493-1499` | carried |
| 90 | `Build.md` §10 | `guidelines/Build.md:1501-1504` | carried |
| 91 | "(Extends §40.48)" | `guidelines/Build.md:546-552` (`meson test -C hostbuild --list`, `-v` tally) | carried |
| 92 | `Build.md` §4 "Native verification" | `guidelines/Build.md:535-541` | carried |
| 93 | `MapRendering.md` §15a | `guidelines/MapRendering.md:888-892` (`NODE.TEXT`/`NODE.ICON`, level selection, `GetErrors()`) | carried |
| 94 | `Design.md` §3, `UI.md` §1 | `guidelines/Design.md:110-115` | carried |
| 95 | `Design.md` §4 | `guidelines/Design.md:251-260` | carried |
| 96 | `Design.md` §4 | `guidelines/Design.md:261-267` (test switch, `runCatching`, write target with the batch, high-water flush) | carried |
| 97 | `Design.md` §12 | `guidelines/Design.md:777-787` | carried |
| 98 | `Regulatory.md` §9 | `guidelines/Regulatory.md:277-284` | carried |

Counts: 37 entries, 33 `carried`, 3 `partial` (64, 65, 76), 1 `absent` (73) — four entries whose
rule is not stated in an artifact the entry names.

The two `partial` and one `absent` entries are answered by `guidelines/Design.md` §11 (73, 76) and
`guidelines/Build.md` §2 (64) / §3 (65); `evidence/red-probe.sh` asks each of those sections for the rule and
is red on this tree (`evidence/red-HEAD.txt`).
