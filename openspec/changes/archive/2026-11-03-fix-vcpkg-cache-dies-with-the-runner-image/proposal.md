# The vcpkg binary cache must not die with the runner image

## Why

`ubuntu-latest` is moving under this repository, and the vcpkg binary cache is the first thing the new image
breaks. Measured over two days:

| run | runner image | `Install vcpkg dependencies` | build | unit tests | job |
|---|---|---|---|---|---|
| 41528122094 | **ubuntu-26.04** | **73 min** | 10 min | 4 min | 109 min |
| 41585715604 | **ubuntu-26.04** | **62 min** | 8 min | 6 min | 109 min |
| 41509739025 | ubuntu-24.04 | 1 min | 10 min | 7 min | 54 min |
| 41489972047 | ubuntu-24.04 | 2 min | 9 min | 7 min | 46 min |
| 41393192664 | ubuntu-24.04 | 2 min | 9 min | 5 min | 44 min |
| 41303297916 | ubuntu-24.04 | 1 min | 9 min | 8 min | 43 min |

**Root cause, in one sentence.** The cache's client is `nuget.exe` run under `mono`
(`.github/workflows/build.yml:432-435`, `.github/workflows/build.yml:461-472`), and on `ubuntu-26.04` mono's
BTLS handshake to `nuget.pkg.github.com` fails — so the run restores nothing, compiles 73 ports from source,
and then pays a failing `nuget push` per package:

```
NuGet.Protocol.Core.Types.FatalProtocolException: Unable to load the service index for source
  https://nuget.pkg.github.com/Framstag/index.json.
  ---> System.Net.Http.HttpRequestException: The SSL connection could not be established …
       at ./external/boringssl/ssl/handshake_client.c:1132
       at Mono.Btls.MonoBtlsContext.ProcessHandshake ()
```

In run 41585715604 that is 15 restore attempts, every one `Restored 0 package(s) from NuGet in 12 s … 1.9 min`,
and 99 × `note: while pushing NuGet package` failing, spanning **06:10:35 → 07:12:31** — 62 minutes inside a step
that costs 1–2 minutes when the transport works. `runs-on:` is `ubuntu-latest`
(`.github/workflows/build.yml:44`), so which image a run gets is GitHub's decision, not this repository's, and the
capability's own requirement — "Cache self-heals after toolchain or runner-image changes" — is false on the new
image for a reason the ABI-version design cannot fix.

**The red case.** `runtimes#aHostedRunnerImageIsPinned`, added to `tools/check-action-runtimes-selftest.sh`: the
case plants a workflow whose `runs-on:` is `ubuntu-latest` into a throwaway tree and requires
`tools/check-action-runtimes.sh` to fail naming the file and line. On base `4367cb4d` the checker reads `uses:`
pins only, so the case fails — the defect is that nothing in the tree reads `runs-on:` at all
(`evidence/red-on-HEAD.log`).

**Owning capability and guideline section.** `ci-vcpkg-cache`, "Cache self-heals after toolchain or runner-image
changes" (`openspec/specs/ci-vcpkg-cache/spec.md`), which this change extends with the transport's own contract;
the CI usage pattern is `guidelines/BuildNative.md` section 12e, which also states the pin and the probe.

**Surface.** `grep -c 'runs-on:' .github/workflows/*.yml` = 4, all four `ubuntu-latest` before this change and all
four `ubuntu-24.04` after; `grep -c 'VCPKG_BINARY_SOURCES' .github/workflows/build.yml` = 1 (moved onto the install
step); one checker gains one assertion; one probe script is added with its self-test.

surface: 1 assertion added to an existing checker (4 cases), 1 script added with its self-test (5 cases), 4
workflows edited (one step, one env line, four `runs-on` lines), 1 requirement extended, 1 guideline section
extended — `grep -c 'runs-on:' .github/workflows/*.yml` and `git diff --stat .github/workflows/ tools/ci-vcpkg-cache.sh`

## What Changes

- **`.github/workflows/*.yml`** — all four jobs pinned to `ubuntu-24.04`, and in `build.yml` a probe step between
  the NuGet configuration and the install: `bash tools/ci-vcpkg-cache.sh --probe`, `continue-on-error: true`, whose
  outcome decides `VCPKG_BINARY_SOURCES` (`clear;nuget,…` when the feed answered, `clear` when it did not). A broken
  transport then costs one step and one warning line instead of 60 minutes of failed pushes.
- **`tools/ci-vcpkg-cache.sh`** — the probe: it asks the feed for its service index through the same client vcpkg
  uses, prints the diagnosis and the measured cost when it fails, and exits non-zero. `--nuget` and `--source` are
  seams the self-test drives a stub through.
- **`tools/ci-vcpkg-cache-selftest.sh`** — five cases: a stub that answers (0), a stub that refuses (1, with the
  diagnosis, the alternative and the measured cost), a missing client (1, named), and the probe's output carrying no
  credential and no coordinate.
- **`tools/check-action-runtimes.sh`** — a fourth assertion: every `runs-on:` naming a hosted image must be pinned
  to a released image (`ubuntu-<major>.<minor>`), never a `-latest` label, because a label's contents change without
  this repository's knowledge — measured: one label flip cost 60 minutes of a 109-minute job.
- **`guidelines/BuildNative.md` section 12e** — the pin and the probe, with the measurement.
- **`openspec/specs/ci-vcpkg-cache/spec.md`** — the transport contract, one extended requirement and two scenarios.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ci-vcpkg-cache`: "Cache self-heals after toolchain or runner-image changes" gains the contract that the cache's
  transport is checked before it is relied on and that a run which cannot reach the feed degrades to a source build
  with one named warning, plus the scenario that the image the job runs on is pinned. No shipped scenario's text
  changes.

## Impact

| path | what changes |
|---|---|
| `.github/workflows/build.yml` | `runs-on` pinned; one probe step; `VCPKG_BINARY_SOURCES` derived from its outcome |
| `tools/ci-vcpkg-cache.sh` | new: the probe, with a `--nuget`/`--source` seam |
| `tools/ci-vcpkg-cache-selftest.sh` | new: four cases |
| `tools/check-action-runtimes.sh` | one assertion (a `runs-on:` label is not `-latest`) and its cases |
| `guidelines/BuildNative.md` | section 12e states the pin and the probe with the measurement |
| `openspec/specs/ci-vcpkg-cache/spec.md` | one requirement extended, two scenarios added |

**Additive.** A run whose transport works behaves exactly as it does today (1–2 minutes, cache hits); a run whose
transport does not now takes the source build without the 99 failed pushes (`clear` writes nothing), and prints one
line naming the cause. Rollback path: revert the workflow's two lines; the checker's assertion goes red again and the
probe script becomes unreferenced.

Scope: **CI configuration and one checker** — no `:app`, `:auto`, `:core`, `buildSrc`, native, resource or submodule
path, so no Gradle task and no device run is owed.

## Not in this change

- **Replacing the NuGet provider with the Actions cache (`x-gha`) or the `files` provider.** The `files` provider is
  forbidden by `guidelines/BuildNative.md` section 12e (it restored stale archives on an exact key hit and never re-saved);
  `x-gha` is a different cache with different failure modes and no measurement behind it yet. The pin and the probe
  fix what is measured; a provider change would be its own item.
- **Serialising concurrent builds.** `concurrency: build-${{ github.ref }}` still lets `main` and a PR build at once.
  With a pinned image and a healthy transport that is 1–2 minutes each; the probe makes the broken case loud. A
  workflow-wide group is a latency trade recorded as a candidate, not taken.
