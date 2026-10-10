# Metrics Guidelines — the project metrics report

What one run of `tools/project-metrics-report.sh` measures, every metric's definition, and the classes it
deliberately excludes — so a size or share claim about this project can be read from a run instead of estimated,
and a figure can be attributed to the tree it describes.

This document owns those definitions and the tool's invocation. The facts — where the tool lives, how to run it
— are `AGENTS.md` "Documentation Map"; the behaviour contract is the capability spec
`openspec/specs/project-metrics-report`. The tool's counting rules are covered without a device by
`tools/project-metrics-report-selftest.sh`.

**Maintenance rule** — when a change supersedes a definition here, update this document in the same change. A
definition that the tool and this document disagree about is a defect in one of them: the self-test is the case
that catches it, and the excluded-class list is stated in every report so a reader can check it against §2.

## 1. Invocation and exit status

```bash
tools/project-metrics-report.sh                # whole repository
tools/project-metrics-report.sh app core       # one or more scopes
tools/project-metrics-report.sh --help         # the usage block, from the script's own header
```

Output is a **complete** text report on stdout, for manual inspection: no JSON, no snapshots, no baseline, no
thresholds, and no option that narrows the report. `--help` is the only option beside scope paths.

Exit status is an outcome, never a verdict: **0** when a report was printed, **2** on a usage or environment
error (unknown option, missing or unreadable scope, no source layout in scope, a required tool missing). The
tool never exits 1 and no figure influences the status.

## 2. What one run measures, and what it does not

The subject is the **project as it is on disk**. Figures do not depend on version-control state, the tool runs no
version-control command, and a worktree with uncommitted work is measured as it stands — which means a local run
and a clean CI clone legitimately differ when their trees differ. The report's header states the run's date, its
scope, how many files were measured, and every excluded class with the paths it removed, so a figure is never
read without its subject.

Every class below is excluded **and stated** with each path in the report's `EXCLUDED CLASSES` section. Nothing
is filtered silently.

| class | what it is | why |
|---|---|---|
| generated build tree | a directory named `build` or `hostbuild` that is not under `/src/` | build output is not source; the `/src/` exception keeps a source package legitimately named `build` (it exists: `buildSrc/src/main/kotlin/com/naviveylin/build/`) |
| tooling state | `.gradle`, `.kotlin`, `.cxx`, `vcpkg`, `.idea` | caches, vendored dependency checkouts and IDE state |
| vcs internal | `.git` | version-control internals |
| agent tool state | `.pi` | machine-local session logs, device captures, instruction dumps, extension config — including `.pi/skills`, which is versioned but is not project source |
| machine-local config | `local.properties`, `mise.local.toml`, `keystore.properties`, `release-version.properties`, `*.keystore` | the file exists and differs per machine, so counting it would make a local run and a CI clone differ with no project change behind it |
| build script | `*.gradle`, `*.gradle.kts` (this includes `settings.gradle.kts` and the init scripts) | build logic, not source or documentation; `buildSrc`'s Kotlin sources are still counted |
| vendored source | `app/src/main/cpp/libosmscout` | third-party source: it is classified at its root and never descended — 3.3 GB and ~304 k lines that would otherwise swamp every figure |

A directory of an excluded class is pruned before anything inside it is read, so an excluded tree's size never
costs a run. Files of types the tool does not count as source (images, binaries, archives, logs, and similar)
are counted in the header as `not counted  N files of other types (…)`, with the most frequent types named.

## 3. Layout, shape check and the unclassified bucket

A file is attributed to a module, package and layer only when its path matches
`<module>/src/<sourceSet>/{java,kotlin}/<package>/…`:

- **module** — the directory owning `src/`, whatever path precedes it;
- **package** — the path segments after the source root, joined with dots; `(no package)` when there are none;
- **layer** — the first segment after `com.naviveylin`; `(root)` for a file directly in `com.naviveylin`,
  `(no package)` when there is no package, and the first two segments for a foreign package (`com.framstag`);
- **depth** — the number of dots in the package path, printed with each package row.

A source file that matches no source root is listed in `UNCLASSIFIED` with its path, and is attributed to no
module, package or layer figure. A scope in which no source layout is found at all is **refused** with exit 2,
naming the scope, and no figures are printed: the tool never reports a shape it did not recognize.

## 4. Counting definitions

A line is exactly one of **code**, **comment** or **blank**, and the three sum to the file's total.

- Comments are full-line `//` and `/* … */` (Kotlin, Java, C/C++, Gradle), full-line `#` (Shell, AWK, Python,
  YAML/TOML, Properties/Config) and full-line `<!-- … -->` (XML). Multi-line block comments continue across
  lines. A trailing comment counts as code.
- `.kts` counts as Kotlin; Markdown counts as **documentation**, and its headings are code lines, because
  Markdown has no comment syntax.
- Source sets come from the source-root directory: `main` → production, `test` → unit test, `androidTest` →
  instrumented test, any other set under its own name. Documentation is its own kind, and a file outside every
  source root is reported as `outside a source root`, so the source-set figures sum to the files measured.
- **Documentation is counted but never mixed into a source figure.** A ratio that describes source — the test
  share — divides by code lines *outside* documentation, and the per-file lists cover source files only:
  documents and OpenSpec store files (`openspec/**`) are aggregated, with their own file and line counts
  printed beside the list. Nothing about them is hidden, and nothing about them dilutes a source metric.

## 5. Metrics

| metric | definition |
|---|---|
| language | files, code, comment, blank and total lines per language |
| module | the same figures per module |
| package | the same figures per `module:package`, complete and ranked by total lines |
| layer | the same figures per `module:layer` |
| distribution | how many packages sit at each depth, and how many fall in each total-line band (under 100, 100-499, 500-1999, 2000+) |
| file list | every counted **source** file with its code, comment, blank and total lines, ranked by total lines; documents and OpenSpec store files are aggregated with their own counts |
| source set | the figures per source set, as defined in §4 |
| comment share | comment lines / total lines |
| test share | (unit test + instrumented test code lines) / code lines outside documentation |
| documentation share | documentation code lines / total code lines |
| average file size | total lines / files measured |
| car code, phone code | code lines and files per surface, as shares of the code under module source roots |
| OpenSpec | open changes, archived changes and feature directories, read from the OpenSpec CLI's own JSON output |

Every ratio is printed together with the operands it was computed from, so a reader can reproduce it by hand
from the same report. A ratio about source never divides by documentation: a test share that counted prose
would not be a test share.

## 6. Surface convention

Car code is the `auto` module, or a file whose name or enclosing directory carries a car word — a `Car`, `Auto`
or `Automotive` token starting where no letter precedes it (`CarSessionSurface.kt`, `di/AutoServiceModule.kt`,
`AutomotiveDevice.kt`), or a name containing `Car` immediately followed by an upper-case letter, which is the
form this project's own car API uses (`NaviVeylinCarAppService.kt`). Everything else under a module source root
is phone code.

The platform shares are computed over code under module source roots; documentation and files outside a source
root are excluded, and the report states the denominator it used. Two limits are deliberate: a car file that
names neither the module nor the car surface is counted as phone (the report prints the complete car file list,
so this is visible rather than hidden), and dual-purpose files that merely import car APIs from the phone path
(`MainActivity.kt`, the notification services) stay phone code, because the rule reads the layout and not the
source.

## 7. Ordering and completeness

Every list is ordered by its figure, descending, with ties ordered by name ascending, under `LC_ALL=C`. Two runs
over an unchanged tree therefore print the same report apart from the header's date, and a different environment
locale cannot reorder a list. Nothing is truncated: every package and every source file in scope is listed,
however long the report becomes, and every figure column carries a header. Documents and OpenSpec store files
are counted and aggregated instead of listed, with the number of files and lines it aggregated printed in place
of the rows that were left out — so the file list is source-only without anything becoming invisible.
