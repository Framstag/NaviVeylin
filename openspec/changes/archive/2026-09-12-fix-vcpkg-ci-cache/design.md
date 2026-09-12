## Context

See proposal.md - Why. The CI workflow (`.github/workflows/build.yml`) currently uses `VCPKG_BINARY_SOURCES=files,${{ github.workspace }}/.vcpkg-bincache,readwrite` restored via `actions/cache` keyed `vcpkg-bincache-${{ env.VCPKG_COMMIT }}-${{ deps-hash }}` (deps-hash = SHA-256 of `setup-vcpkg.sh` + all of `vcpkg-overlays/**`).

This is byte-for-byte the design libosmscout already diagnosed and rejected in `Framstag/libosmscout` change `vs-vcpkg-cache-fix` (empirical evidence from their run logs):

- Runner image updates (~weekly, e.g. `20260810.198.2` → `20260818.207`) change vcpkg ABI hashes even when tool version strings are identical.
- `actions/cache` restores the stale archives ("Cache hit") but vcpkg rejects all of them ("Restored 0 package(s)") and rebuilds all ports (~60 min).
- The post-step never saves on an exact primary-key hit ("Cache hit occurred on the primary key ..., not saving cache"), so the stale cache persists and every subsequent run rebuilds.

Additional local footgun: `deps-hash` covers `vcpkg-overlays/**`, including the unused `ports/libosmscout` overlay — touching any overlay file invalidates the whole cache key.

## Goals / Non-Goals

**Goals:**
- Warm runs restore vcpkg dependencies without rebuilding (~60 min saved).
- Cache self-heals after toolchain/runner-image changes: one rebuild, then cached again.
- Dependency-list / overlay-port changes rebuild only affected ports.
- No repository secrets; runs on `GITHUB_TOKEN` alone for main-branch pushes.
- Keep `setup-vcpkg.sh`, DEPS list, triplet overlays, and `VCPKG_COMMIT` pin unchanged.

**Non-Goals:**
- No switch to vcpkg manifest mode (`vcpkg.json`) — classic-mode DEPS list stays.
- No change to the Android SDK / Gradle `actions/cache` entries.
- No offline/self-hosted runner support.
- No local-developer cache migration (local `./vcpkg` checkout is deliberately unpinned).

## Decisions

### Decision 1: NuGet binary cache on GitHub Packages, versioned by ABI hash

`VCPKG_BINARY_SOURCES=clear;nuget,https://nuget.pkg.github.com/Framstag/index.json,readwrite`. vcpkg stores each compiled port as a NuGet package whose version is its ABI hash and restores per package. Feed is the Framstag org feed — the same one libosmscout already publishes to, so it exists and the auth pattern is proven. Per-package ABI versions make recovery exact:

```
runner image update changes toolchain ABI
        │
        ▼
vcpkg install: no NuGet package matches new ABI hash
   → rebuilds affected ports only
   → pushes each as a NEW package version
        │
        ▼
next run: per-port restore from feed (fast)
   → one rebuild total, then self-healed
```

**Alternatives considered:**

1. **Files provider + `actions/cache`, keyed on a toolchain fingerprint** (e.g., hash of `gcc --version`, `cmake --version`, NDK path, runner image). Rejected — the fingerprint is a heuristic: libosmscout proved version strings are insufficient (ABI flipped while the toolset version string stayed identical), and a missed component reintroduces the stale-cache-forever bug. The NuGet provider computes the ABI hash itself, so it cannot drift from what vcpkg actually uses.
2. **Files provider + `actions/cache` with `restore-keys` prefix only** (never exact-hit, so the cache always re-saves and self-heals). Works, but every run uploads a fresh ~1 GB cache entry into the shared 10 GB repo budget alongside SDK/gradle caches, and correctness depends on actions/cache eviction behavior. NuGet is exact and keeps the repo cache budget clean.
3. **`x-gha` binary cache provider**. Rejected — removed from vcpkg-tool (microsoft/vcpkg-tool#1662, merged 2025-04-29); vcpkg team recommends NuGet.
4. **Cache the installed tree (`vcpkg/installed/<triplet>`) instead of archives**. Rejected — vcpkg re-verifies ABI on every install, so the same invalidation applies, and whole-tree granularity means any change invalidates all packages.

### Decision 2: Install mono on the runner; nuget.exe runs under mono

`nuget.exe` (acquired via `vcpkg fetch nuget`) requires mono on Linux. `ubuntu-24.04`/`ubuntu-latest` runners no longer preinstall mono (actions/runner-images#10636; vcpkg-docs PR microsoft/vcpkg-docs#495). The existing "Install host build dependencies" apt step gains `mono-complete`.

**Alternative:** pin the workflow to `ubuntu-22.04`, where mono is still preinstalled. Rejected — 22.04 ships older host tooling and moves closer to EOL; adding one apt package is cheaper than pinning an aging image.

### Decision 3: Feed auth via `GITHUB_TOKEN` with job-level `packages: write`

NuGet source registered runner-locally: `mono $(vcpkg fetch nuget) sources add -Source https://nuget.pkg.github.com/Framstag/index.json -Name GitHubPackages -UserName Framstag -Password $GITHUB_TOKEN` plus `setapikey`, using an explicit `-ConfigFile` pointing at the user-level NuGet config (`~/.config/NuGet/NuGet.Config`), outside the checkout. Job permissions `contents: read`, `actions: write`, `packages: write`. `NuGet.config` added to `.gitignore` defensively.

**Alternatives considered:**
- **PAT secret fallback** (`VCPKG_PAT_TOKEN`, scopes `packages:read`/`packages:write`). Kept as documented fallback only — first main-branch run verifies GITHUB_TOKEN push; if the feed rejects it, switch the password source to the secret. One-time owner setup.
- **Credential passthrough via `VCPKG_NUGET_*` env only**. Rejected — vcpkg's nuget provider reads credentials from NuGet.Config; registering the source is required.

### Decision 4: Remove files-provider cache steps entirely

The `Compute vcpkg cache key` and `Cache vcpkg binary cache` steps go away; the `.vcpkg-bincache` dir is no longer created. This also frees the repo's 10 GB actions/cache budget for the Android SDK and Gradle caches.

**Alternative:** keep the files provider as a secondary read-only fast path (`nuget,<feed>,readwrite;files,<dir>,read`). Rejected — added complexity for a warm-cache win NuGet already provides; the files dir would only matter when the feed is unreachable, which is not a target scenario.

## Risks / Trade-offs

- **GITHUB_TOKEN push rights on the NuGet feed** — vcpkg docs note the token may lack upload/download in some setups, and MS docs contradict themselves on read rights for fork PRs. → First main run verifies push; fallback documented: switch `-Password` to `secrets.VCPKG_PAT_TOKEN`. Fork PRs restore what they can and skip pushes without failing (spec: "degrades gracefully").
- **Token written to NuGet config on runner** — `-StorePasswordInClearText` stores it in the user-level NuGet.Config of an ephemeral runner destroyed after the job. → Explicit `-ConfigFile` keeps it out of the checkout; `NuGet.config` is gitignored anyway.
- **One full rebuild per ABI-relevant image update** — unavoidable and correct; binaries built against an old toolchain must not be reused. → Designed recovery cost; happens once, not every run.
- **Feed accumulation** — each ABI generation adds ~250 MB of per-port packages. → GitHub Packages storage is free for public repos; old ABI versions are exactly what should be pruned, manually if ever needed.
- **`vcpkg fetch nuget` network fetch on first run** — small one-time download per run (~a few MB, cached by vcpkg in `$VCPKG_ROOT`). → Negligible vs. 60-min rebuilds.

## Migration Plan

1. Edit `.github/workflows/build.yml`: add job `permissions` (`contents: read`, `actions: write`, `packages: write`); add `mono-complete` to the existing apt step; add the NuGet source registration step; replace the `VCPKG_BINARY_SOURCES` env value; delete the two vcpkg-cache steps; add `VCPKG_NUGET_REPOSITORY` env for parity with libosmscout.
2. Edit `.gitignore` (add `NuGet.config`) and update the CI documentation in `AGENTS.md` and `guidelines/Build.md`.
3. Push to a branch / main and run the workflow: first run rebuilds all deps and pushes them to the feed.
4. Re-run: verify `Restored N package(s)` with N > 0 and the configure step drops from ~60 min to minutes.
5. Rollback: revert the workflow edit; the old `actions/cache` entry remains until evicted; feed packages can be deleted from GitHub Packages.

## Open Questions

None — approach fully determined by libosmscout's documented evidence and the MS binary-caching tutorial; the GITHUB_TOKEN-vs-PAT question is resolved by a verify-then-fallback path, not a spec change.
