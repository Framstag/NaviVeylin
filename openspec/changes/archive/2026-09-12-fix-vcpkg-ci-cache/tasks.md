## 1. Workflow changes (spec: ci-vcpkg-cache)

- [x] 1.1 Add job-level `permissions` block (`contents: read`, `actions: write`, `packages: write`) to the `build` job in `.github/workflows/build.yml` and verify checkout, cache, and artifact steps still work
- [x] 1.2 Add `mono-complete` to the "Install host build dependencies" apt step and verify the step installs cleanly (runner log)
- [x] 1.3 Add "Configure NuGet source for vcpkg binary cache" step: `mono "$($VCPKG_ROOT/vcpkg fetch nuget | tail -n 1)" sources add` + `setapikey` against `https://nuget.pkg.github.com/Framstag/index.json` with `$GITHUB_TOKEN`, `-ConfigFile` pointing at the user-level NuGet config; verify step runs green on first workflow launch
- [x] 1.4 Replace workflow-level env `VCPKG_BINARY_SOURCES` with `clear;nuget,https://nuget.pkg.github.com/Framstag/index.json,readwrite` and add `VCPKG_NUGET_REPOSITORY`; verify no `files,` provider remains in the workflow
- [x] 1.5 Remove the "Compute vcpkg cache key" and "Cache vcpkg binary cache" steps (old files provider + actions/cache entry); verify `.vcpkg-bincache` is no longer referenced anywhere in the file

## 2. Repo hygiene and docs

- [x] 2.1 Add `NuGet.config` to `.gitignore` and verify `git check-ignore NuGet.config` reports it
- [x] 2.2 Update the CI/vcpkg section in `AGENTS.md`: replace the old cache-key mechanics (VCPKG_COMMIT + deps-hash + binary cache) with the NuGet feed description and self-heal semantics; verify the section matches the new workflow
- [x] 2.3 Update `guidelines/Build.md` wherever it documents the vcpkg binary cache / CI caching; verify no stale "cache key" claims remain

## 3. Validation and CI verification

- [x] 3.1 Run `openspec validate --change fix-vcpkg-ci-cache` and verify the change validates (proposal → spec → design → tasks all complete)
- [x] 2.4 Run the workflow once on the main branch; verify the configure step rebuilds dependencies and the NuGet push step reports success (feed now holds per-port ABI-versioned packages)
- [x] 3.3 Re-run the workflow; verify vcpkg logs "Restored N package(s)" with N > 0 and the dependency-install phase drops from ~60 min to minutes
- [ ] 3.4 Trigger a PR run and verify the job completes with restore-only behavior (no push, no failure) — not exercised; by design per vcpkg docs + libosmscout precedent
- [x] 3.5 Verify the Android APK build and unit tests still pass in the workflow run (existing steps unchanged)
