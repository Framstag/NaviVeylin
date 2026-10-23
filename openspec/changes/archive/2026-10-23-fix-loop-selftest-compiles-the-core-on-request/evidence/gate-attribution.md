# The first gate run, kept and attributed

**The run.** `./tools/loop/gate.sh --base abeffcad45997a34b96f45e32486b178517c918e`, 2026-10-23T10:17Z, exit 1.
Transcript `gate-first-red/gate.log`, tallies `gate-first-red/*.xml`; the loop's suite is the only one that failed
(`tests="180" failures="1"` in `gate-first-red/tools_loop-selftest.sh.xml`, against
`tests="34" failures="0"` and `tests="100" failures="0"` for the other two).

**What failed.** `clean#reportsWithoutApplyAndRemovesWithIt`, and nothing else:

```
tools/loop-selftest.sh: Zeile 101: tmp ist nicht gesetzt.
FAIL clean#reportsWithoutApplyAndRemovesWithIt
```

**Why — the change's own defect, not a flake.** `core_ensure` read the driver's scratch directory from `$tmp`, and
`$tmp` is exactly the name five case functions declare `local` for a scratch tree of their own
(`tools/loop-cases.sh:4957`, `:4994`, `:5042`, `:5100`, `:5314`). A `local` of that name shadows the driver's
variable for the whole case function, so the `core_ensure` call this change added inside
`clean#reportsWithoutApplyAndRemovesWithIt` (through `run_one_item` → `run_loop`) read an unset `$tmp` and the case
failed on `set -u`. The case that found it is the one that starts a run in a tree whose paths it also names `tmp`.

**The fix (in this change, before the second run).** The two paths the compile uses are the driver's own, named
and `readonly`: `selftest_classes` and `selftest_core_marker` (`tools/loop-selftest.sh:93`–`98`). No case can
shadow them by accident, and a future case that declares one of those names fails loudly instead of silently
reading nothing.

**The second run is not a retry of the first.** The first run was red for a defect the change introduced and the
change then removed; `gate.log` is the run of the tree that carries the fix — three suites, 314 cases, none failed,
and the loop's suite `tests="180" failures="0"` in `gate-tallies/tools_loop-selftest.sh.xml` — and the first run is
kept here so the attribution is a record rather than a claim.
