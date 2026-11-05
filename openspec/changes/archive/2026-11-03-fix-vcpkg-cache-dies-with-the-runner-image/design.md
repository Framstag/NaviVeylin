# Design — the cache's client is part of the contract

## Root cause

`ci-vcpkg-cache`'s requirement "Cache self-heals after toolchain or runner-image changes" assumes the image
change only moves ABI hashes: new versions, one rebuild, done. What the image change actually moved on
2026-11-03 is the **client**, `nuget.exe` under `mono` (`.github/workflows/build.yml:432-435` installs
`mono-complete` for it; `:461-472` configures it). On `ubuntu-26.04` the handshake fails:

```
NuGet.Protocol.Core.Types.FatalProtocolException: Unable to load the service index for source
  https://nuget.pkg.github.com/Framstag/index.json.
  ---> System.Net.Http.HttpRequestException: The SSL connection could not be established …
       at ./external/boringssl/ssl/handshake_client.c:1132
       at Mono.Btls.MonoBtlsContext.ProcessHandshake ()
```

Because the failure is per request and vcpkg treats a cache failure as non-fatal, nothing in the run says "the
cache is gone". The consequences compound: restore returns `Restored 0 package(s)` (15 attempts, 12 s … 1.9 min
each), 73 ports compile, and each compiled port attempts a push that fails after NuGet's retry backoff — 99
pushes spanning 62 minutes inside a step that costs 1–2 minutes when the transport works. The image is chosen by
the `ubuntu-latest` label (`.github/workflows/build.yml:44`), so the repository has no say in when that happens
again.

## The fix, in two halves

1. **Pin the image.** `runs-on: ubuntu-24.04`. A released image is a value this repository bumps on purpose;
   `-latest` is a value GitHub bumps for it. Measured cost of not pinning: 60 minutes of a 109-minute job, twice
   in one day, and every one of the five runs that landed on `ubuntu-24.04` spent 1–2 minutes in the step.
2. **Make a broken transport cheap and loud.** A probe step runs
   `tools/ci-vcpkg-cache.sh --probe` between the NuGet configuration and the install, with
   `continue-on-error: true`; its outcome selects `VCPKG_BINARY_SOURCES`: `clear;nuget,…` when the feed answered,
   `clear` when it did not. Then a future image (or a Packages outage) costs the source build and one warning
   line naming the measurement — not 99 failed pushes.

## Why not the alternatives

- **The `files` provider** (`VCPKG_BINARY_SOURCES=clear;files,…` + `actions/cache`) — forbidden by
  `guidelines/BuildNative.md` section 12e, with a measured reason: it restored stale archives on an exact key hit, vcpkg
  rejected them all, and the post-step never re-saved.
- **The `x-gha` provider** (vcpkg's GitHub Actions cache) — removes mono entirely, which is attractive, but it is a
  different cache with different failure modes, no measurement of this project's package set behind it, and it
  would rewrite the guideline's own section 12e in the same change that fixes an outage. Recorded as the
  next candidate, not taken.
- **Fixing mono** (`cert-sync`, `MONO_TLS_PROVIDER`) — an unmeasured guess about a stack nobody in this repository
  owns, on an image that may change again tomorrow.
- **Serialising `main` and PR builds** (`concurrency` group) — halves the worst case only while the transport is
  broken, and costs latency on every healthy run. The probe and the pin remove the worst case instead.

## Blast radius

| who reads the workflow | what it sees |
|---|---|
| the build job | a pinned image; one extra step (~2 s) that decides the cache source |
| `tools/check-action-runtimes.sh` | one more assertion: a `runs-on:` naming a hosted image is pinned |
| `tools/loop/gate.sh` | the new script's self-test fires for `.github/workflows/*` and `tools/ci-vcpkg-cache*` |
| a fork PR | unchanged: `permissions` still lacks `packages: write`, the probe passes, pushes are skipped as before |

No Gradle task, no native build and no resource changes, so the compiled gate is not owed.

## Rollback

Revert the workflow's three edits (pin, probe step, `VCPKG_BINARY_SOURCES` expression) and delete the probe
script; `runtimes#aHostedRunnerImageIsPinned` goes red again on the next `-latest` label and the suite names it.
No state migrates.

## Risks

- **A pinned image goes stale** (24.04 retires, or the SDK/NDK the build needs moves). The pin makes that visible
  and deliberate instead of silent — the bump is a one-line change with the measured step time as its check.
- **The probe passes and the pushes still fail** (a per-request flake rather than a dead transport). Then the run
  pays the retries it pays today, with the probe's line in the log saying the transport answered — which is what
  makes the next diagnosis a matter of reading two lines instead of diffing images.
- **`continue-on-error: true` could hide a real failure** of the probe script (a syntax error, say). The
  `VCPKG_BINARY_SOURCES` expression reads the *outcome*, so a script that errors is the same as a dead feed:
  cache off, source build, one warning. The run still builds.

## Open decisions

none
