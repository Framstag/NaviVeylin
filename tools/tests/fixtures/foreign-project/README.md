# A synthetic foreign project — the portability proof's fixture

This directory is **not** a second project. It is the fixture the portability check's self-test overlays
onto a throwaway working tree, so the whole toolchain can be run in a project whose repository, allowlist,
vocabulary and bands all differ from the project this tooling was written in (`tooling-portability`, D2).

```
tools/tooling.json          the foreign profile: repo `acme/widget`, allowlist ['ada'],
                            categories core/docs/infra, bands p0–p2, TTL 30
tools/sensors/sensors.json  one row whose command is `tools/sensors/fixtures/foreign-emit.sh`
tools/loop/loop.conf        the agent command points at the stub `pi`, the gate command is EMPTY
findings.json               the foreign "backlog": three findings, one of them a duplicate
gh/                         the stub `gh`'s canned answers for `acme/widget` (repo, login, labels,
                            issues, pull requests) — no name of the upstream project appears here
```

The self-test copies the repository's engines (`tools/issues`, `tools/sensors`, `tools/loop`, the
portability check) into a throwaway git repository under the gitignored `.pi/tmp/`, overlays this
directory on top of it, and runs the toolchain there. **The engines are copied at test time, never
carried here**, so this fixture cannot drift away from the engines it proves.

## What it models

- a different repository, a different allowlisted author, a different category vocabulary, a different
  band set and a different band TTL — the four ways a project differs from ours;
- a foreign label set (`core`, `docs`, `infra`) that no engine knows;
- one sensor row, so the registry's command indirection is exercised;
- a project with **no gate command**, which the loop's report must read as "skipped" rather than as a
  failure.

## What it does not model

Three differences a real adopting project could have, and this fixture deliberately does not exercise —
so a reader knows what a green run here does *not* prove:

1. **A non-GitHub tracker.** `gh` is the assumed forge client; the fixture stubs it rather than replacing
   it, so a project on GitLab or a local tracker is not covered.
2. **A shell other than `bash`, or a host without `jq`.** The engines are `bash` scripts and the core is
   Java, both assumed present; the fixture runs on the same shell and the same `jq` as the upstream
   project.
3. **A build system other than the upstream one.** The gate command is empty here — that is the point —
   but the fixture does not prove that an arbitrary build command integrates; it proves the loop's
   phases run when the project declares none.
