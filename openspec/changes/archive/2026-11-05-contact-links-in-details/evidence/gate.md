# Gate — contact links in the phone details dialog

One run, by the loop's gate child, after the pre-gate review of this diff (task 7.2, `guidelines/Build.md` §2 scope
block). The run's own transcript is `gate.log` beside this file; the XMLs it left are `gate-*.xml`.

## The command and its result

```
./tools/loop/gate.sh
```

- Exit 0, `BUILD SUCCESSFUL in 5m 12s` (`167 actionable tasks: 45 executed, 122 up-to-date`).
- Compiled step `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` took 313 s; its tally is
  `gate-build-tally.xml` (no per-suite counts — the step's own summary is the pass).
- The gate printed 458 tests with zero failures, zero errors: the compiled step plus the 31 `tools/*` suites
  (`gate.log`, its closing gate line). Aggregate only — no single XML carries that total; the per-suite counts below
  are what is carried.

## The change's own suites, as the gate re-ran them

| suite | XML | tally | timestamp |
|---|---|---|---|
| `:core` — `ContactLinksTest` | `gate-contact-links-suite.xml` | `tests="7" failures="0"` | `2026-11-05T09:57:03.274Z` |
| `:app` — `LocationDetailsDialogComposeTest` | `gate-details-dialog-suite.xml` | `tests="22" failures="0"` | `2026-11-05T09:58:15.212Z` |
| `:auto` — `DetailsScreenTest` | `gate-car-details-suite.xml` | `tests="33" failures="0"` | `2026-11-05T09:57:37.518Z` |

The red case of this change is green in the gate's run: `gate-details-dialog-suite.xml` carries
`<testcase name="websiteValueStartsTheBrowser" classname="com.naviveylin.ui.map.LocationDetailsDialogComposeTest">`
with no `<failure>` child — the same case `red-on-HEAD.xml` failed on HEAD at `2026-11-05T09:44:36.004Z`.

## Attribution

No step of the gate is red, so there is nothing to attribute to work outside this change's paths.

## The revert-check's two runs, carried

`revert-check.md` quotes the mutated run's failing tally and the restored run's passing one; both XMLs sit beside it:
`revert-check-mutated.xml` (`2026-11-05T09:48:12.598Z`, `aNonWebSchemeResolvesToNoTarget` failed) and
`revert-check-restored.xml` (`2026-11-05T09:48:18.235Z`).
