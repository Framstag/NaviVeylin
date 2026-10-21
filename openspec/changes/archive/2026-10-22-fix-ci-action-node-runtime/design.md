# Design — fix-ci-action-node-runtime

## Root cause

A workflow pins an action *major*, and the major decides the runtime: `actions/upload-artifact@v5`'s committed
`action.yml` declares `runs: using: 'node20'`, `v6`'s declares `node24`. GitHub's runner has deprecated Node 20
and forces such an action onto Node 24 with a warning on every step (`github.blog/changelog/2025-09-19`), so the
symptom is a warning in a green run. Nothing in the tree reads an action version, which is why the pin survived
six places in two files.

## The single fix

Pin the current major of the action, and gate the class instead of the instance:

1. `.github/workflows/build.yml` — five `actions/upload-artifact@v5` → `@v7`.
2. `.github/workflows/sensors.yml` — `actions/checkout@v4` → `@v5` (the major `build.yml` already uses).
3. `tools/check-action-runtimes.sh` — the check, with the floor table and the `action.yml` URLs it was read
   from; it fails on a pin below its action's floor, on an action with no row, and on a ref it cannot resolve
   offline, and it skips a local and a container action.
4. `tools/check-action-runtimes-selftest.sh` — its ten cases, the repository's own pins being the case issue
   #240 failed.
5. `.github/workflows/build.yml` — the hygiene step `Check action runtimes`, beside `Check Compose sandbox rule`
   (same shape: a text-only invariant no unit test can assert).
6. `tools/loop/loop.conf` — `LOOP_GATE_SUITES` gains `tools/check-action-runtimes*` and `.github/workflows/*`,
   so the loop's gate runs the self-test for a workflow edit. Before this line, a workflow change owed nothing.
7. `guidelines/Build.md` §2 — "The actions' Node runtime", and the routing-table row in `AGENTS.md`.

## Blast radius

Six versions in two workflow files (build and sensors), one hygiene step, one gate-mapping entry, one guideline
subsection, one routing row, two shell scripts under `tools/`, one classification entry and one capability
spec. No compiled file, no
Gradle task, no app behaviour: the delta compiles nothing. The action's inputs and outputs are unchanged
between `upload-artifact@v5` and `@v7` for the parameters this repository passes (`name`, `path`,
`if-no-files-found`) — `v7`'s `action.yml` declares no removed or renamed input and adds `archive`, whose
default `'true'` keeps the previous behaviour; the release notes for `v6.0.0` and `v7.0.2` name only the
runtime and a download-retry improvement; `checkout@v5` likewise keeps the inputs this repository passes
(`fetch-depth` absent, defaults).

## Rollback

Revert the change: the six pins return to the previous majors (the workflow runs exactly as before, with the
deprecation warning), and the check, its self-test, the CI step, the gate mapping, the guideline subsection and
the capability spec are removed together. Nothing else reads them.

## Alternatives the evidence eliminated

- **Bump to `v6` instead of `v7`.** Both declare `node24`, so the notice goes either way; `v6` is the superseded
  major (`v7.0.2` is the current release, and the libosmscout submodule already pins `upload-artifact@v7`), so
  pinning `v6` would be a second edit for the same rule. Not a fork in the decision — one rule, "pin the current
  major", applied once.
- **Leave `sensors.yml` at `checkout@v4`** — the item names only `build.yml`. Eliminated because the mechanism
  is identical and the check that makes the class fail-safe necessarily sees it: shipping a check that is red on
  the very tree that introduces it would be a defect in the change, not scope discipline.
- **Fetch the runtime from GitHub at check time** (`api.github.com/.../action.yml`). Eliminated: a gate that
  needs egress fails on a runner without it and passes on a workstation with it, so the recorded floor table
  plus its URLs is the offline form of the same fact.
- **A unit test instead of a shell check.** Eliminated: the artefact is YAML, not code; there is no JUnit case
  that can read it without a new Gradle task, and the precedent for a text-only invariant
  (`tools/check-compose-sandbox-rule.sh`) is a shell check plus a self-test in the same hygiene job.
- **A warning-only step** (`continue-on-error`). Eliminated: the item exists because a warning is invisible —
  the notice sat in a green run for months. A check that cannot fail is the same signal again.
