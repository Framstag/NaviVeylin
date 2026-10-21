# fix-ci-action-node-runtime

## Why

Issue #240. Verified on the run's base commit `80df3a3` on 2026-10-22, in the loop's worktree.

**The claim holds, at six lines in two files.** `.github/workflows/build.yml:506,529,540,547,557` pin
`actions/upload-artifact@v5`, and the `action.yml` committed at that tag declares `runs: using: 'node20'`
(`raw.githubusercontent.com/actions/upload-artifact/v5/action.yml`), while `v6` — the first Node 24 major —
declares `node24`. The `Build debug APK (all ABIs)` run therefore prints `Node.js 20 is deprecated. The
following actions target Node.js 20 but are being forced to run on Node.js 24: actions/upload-artifact@v5` on
those five steps, which is the notice the item quotes. The same rule found a sixth pin the item does not name:
`.github/workflows/sensors.yml:59` pins `actions/checkout@v4`, whose metadata at `v4` also declares `node20`
(`v5` declares `node24`). One mechanism, two files.

**Root cause, one sentence:** a workflow pins an action *major* whose committed `action.yml` declares
`runs.using: node20`, and nothing in the tree reads an action version, so the pin's runtime is a fact only the
runner notices — as a warning that fails nothing.

**Red test on HEAD, before the fix.** `tools/check-action-runtimes-selftest.sh` runs the new checker against
this repository's own workflows as its first case; on HEAD that case fails, and the eight fixture cases beside
it pass, so the tally is the repository's six pins and not the harness:

```
$ bash tools/check-action-runtimes-selftest.sh
FAIL: this repository's workflows pin a Node 24 major
       ::error file=.github/workflows/build.yml,line=506::'uses: actions/upload-artifact@v5' targets Node 20 — the first Node 24 major is v6 (…)
       ::error file=.github/workflows/build.yml,line=529::…
       ::error file=.github/workflows/build.yml,line=540::…
       ::error file=.github/workflows/build.yml,line=547::…
       ::error file=.github/workflows/build.yml,line=557::…
       ::error file=.github/workflows/sensors.yml,line=59::'uses: actions/checkout@v4' targets Node 20 — the first Node 24 major is v5 (…)
ok: pins at or above the Node 24 floor pass
…
check-action-runtimes-selftest: 1 of 10 cases failed
EXIT=1
```

XML `tests=10 failures=1` (`evidence/red-check-action-runtimes-selftest.xml`, `evidence/red-check-action-runtimes-selftest.log`,
2026-10-22T01:33:31+02:00; the run copy is beside the item's record). The green half of the same command is
`evidence/green-check-action-runtimes-selftest.log`.

**Owning requirement and its home.** The delta belongs to a capability this change creates,
`ci-action-runtimes` — "A workflow pin names a major whose committed metadata declares Node 24" — because no
existing capability owns a workflow's action versions: the nearest ones own the gate (`build-test-gate`), the
cache (`ci-vcpkg-cache`), the sensors pipeline's shape (`backlog-sensors`) and the SBOM. The rule's prose home
is `guidelines/Build.md` §2, a new subsection "The actions' Node runtime" beside "The build JVM (daemon JVM
criteria)", which is the section that already owns a CI-step-enforced rule of this shape; the routing table in
`AGENTS.md` names it.

**Surface, counted before the plan.** `surface: 6 pins rewritten in 2 workflow files, 10 cases added (10 of
them new, 1 of them the repository's own pins), 0 rewritten, 0 fixtures realigned — grep -rn 'uses:'
.github/workflows/*.yml | wc -l` (10 pins across `build.yml` and `sensors.yml`), `grep -rln
'upload-artifact\|node20\|node24\|Node 20\|Node 24' tools/ .github/ guidelines/ AGENTS.md openspec/specs/`
(before this change: no file — no case, fixture, prompt or engine asserted an action version, which is why the
notice could sit in a green run), `grep -rln 'action.yml\|runs.using\|upload-artifact' tools/*-cases.sh
tools/fixtures` (nothing).

## What changes

- `.github/workflows/build.yml` — the five `actions/upload-artifact@v5` pins become `@v7` (the action's current
  major; it and `v6` both declare `node24`; `v7` is what the libosmscout submodule already pins), and the
  hygiene job gains the step `Check action runtimes`.
- `.github/workflows/sensors.yml` — `actions/checkout@v4` becomes `@v5`, the same major
  `.github/workflows/build.yml` already uses.
- `tools/check-action-runtimes.sh` — the check: every `uses:` pin under `.github/workflows/*.yml` is at or
  above its action's recorded first Node 24 major; an action the table does not record, a pin below its floor
  and a ref that cannot be resolved offline all fail. Text only: no build, no device, no network.
- `tools/check-action-runtimes-selftest.sh` — its ten cases, the repository's own pins among them.
- `tools/loop/loop.conf` — the gate's path mapping: a workflow or a change to this tool now owes that
  self-test, so a future pin is gated by the loop and not only by the pipeline.
- `guidelines/Build.md` §2, `AGENTS.md` — the rule and its route.
- `tools/feature-list/specs.json` — the new capability's classification entry (`build-tooling`, internal, no
  surface): the pipeline's `Check capability classification` fails on a shipped spec that has none.
- `openspec/changes/fix-ci-action-node-runtime/specs/ci-action-runtimes/spec.md` — the capability this change
  creates, with the `## Purpose` it archives (now `openspec/specs/ci-action-runtimes/spec.md`).
