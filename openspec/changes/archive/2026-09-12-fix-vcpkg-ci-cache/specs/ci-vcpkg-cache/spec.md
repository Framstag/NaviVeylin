## Purpose

Defines how vcpkg native dependencies are cached in the NaviVeylin GitHub Actions build so unchanged dependencies are restored between runs, the cache recovers automatically after toolchain or runner-image changes, and dependency-list edits do not trigger full rebuilds.

## ADDED Requirements

### Requirement: vcpkg dependencies restored between CI builds

The GitHub Actions build SHALL restore compiled vcpkg dependencies from a remote binary cache before building, so a run with an unchanged toolchain and dependency list does not recompile dependencies.

#### Scenario: Warm cache run

- **WHEN** a CI run starts with an unchanged runner toolchain and unchanged dependency list
- **THEN** vcpkg restores the previously compiled packages and the dependency-install phase completes without recompiling them

#### Scenario: Fresh repository

- **WHEN** the first CI run ever executes (empty remote cache)
- **THEN** all dependencies compile and are pushed to the remote cache for subsequent runs

### Requirement: Cache self-heals after toolchain or runner-image changes

The dependency cache SHALL recover after a runner-image or toolchain change that alters vcpkg ABI hashes: exactly one rebuild, after which subsequent runs use the cache again. Runs SHALL NOT rebuild on every execution while a stale cache is present.

#### Scenario: Runner image update invalidates ABI hashes

- **WHEN** a runner image update changes any vcpkg ABI hash and vcpkg rejects previously cached packages
- **THEN** the affected packages compile once, are re-cached under their new ABI versions, and the next run restores them

#### Scenario: Stale cache never blocks recovery

- **WHEN** vcpkg has restored cached packages that it subsequently rejects
- **THEN** the new build output is stored so the rejected stale packages are not restored again on later runs

### Requirement: Dependency-list changes rebuild only affected packages

A change to the vcpkg dependency list or an overlay port SHALL cause only the affected packages to rebuild, not the entire dependency set.

#### Scenario: Single dependency added

- **WHEN** one port is added to the dependency list
- **THEN** only that port (and its missing transitive dependencies) rebuild while all other packages restore from cache

### Requirement: CI caching requires no repository secrets

The vcpkg cache SHALL operate with only the built-in `GITHUB_TOKEN` and job-level permissions scoped to the workflow; no personal access token secret required for main-branch runs.

#### Scenario: Main branch run with GITHUB_TOKEN only

- **WHEN** a CI run executes on the main branch with `GITHUB_TOKEN` and the declared `packages: write` permission
- **THEN** vcpkg restores from and pushes to the GitHub Packages NuGet feed without any PAT secret

#### Scenario: Fork pull request degrades gracefully

- **WHEN** a CI run executes from a fork PR with a read-only `GITHUB_TOKEN`
- **THEN** vcpkg restores what it can and the build completes; package pushes are skipped without failing the job
