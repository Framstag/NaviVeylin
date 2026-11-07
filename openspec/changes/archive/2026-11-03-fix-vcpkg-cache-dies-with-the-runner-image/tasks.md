# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/ci-vcpkg-cache/spec.md` for the contract.

## 1. The red case

Requirement: "Cache self-heals after toolchain or runner-image changes", scenarios "A run names the image it is
given" and "An unreachable cache costs the build only".

- [x] 1.1 Add `runtimes#aHostedRunnerImageIsPinned` to `tools/check-action-runtimes-selftest.sh`: a fixture whose
      `runs-on:` is `ubuntu-latest` must be reported with its file and line, a fixture pinned to `ubuntu-24.04` must
      pass, and a `self-hosted` or matrix expression must be skipped. Verify: the case fails on base `4367cb4d`
      (`evidence/red-on-HEAD.log`) and passes after task 2.1.
- [x] 1.2 Record the measurement the change rests on as `evidence/runner-image-measurement.txt`: the per-run step
      durations and images of the runs named in `proposal.md`, with the command that read each. Verify: the two
      `ubuntu-26.04` rows read 62 and 73 minutes and every `ubuntu-24.04` row reads 1–2.

## 2. The pin

- [x] 2.1 Set `runs-on: ubuntu-24.04` in `.github/workflows/build.yml` and update the mono comment beside it to
      state why the image is pinned (the measurement). Verify: `grep -c 'runs-on: ubuntu-24.04'
      .github/workflows/build.yml` is 1, and `bash tools/check-action-runtimes.sh` exits 0.
- [x] 2.2 Add the assertion to `tools/check-action-runtimes.sh`: every `runs-on:` that names a hosted image must be
      a released one, and its header states the measurement. Verify: on a tree whose `runs-on:` is `ubuntu-latest`
      the check exits 1 naming the file and line.

## 3. The probe

- [x] 3.1 Add `tools/ci-vcpkg-cache.sh`: `--probe` asks the feed for its service index through the cache's own
      NuGet client, prints one line naming the diagnosis (and the measured cost) when it fails, and exits 0/1/2.
      `--nuget <cmd>` and `--source <url>` are the seams the self-test drives. Verify: `bash -n`, and
      `bash tools/ci-vcpkg-cache.sh --probe --nuget /bin/false` exits 1 with the diagnosis on stderr.
- [x] 3.2 Add `tools/ci-vcpkg-cache-selftest.sh` with five cases: a stub that answers, a stub that refuses, a
      missing client, and the probe's output carrying no coordinate and no token. Verify: `all 4 cases passed`.
- [x] 3.3 Wire the probe into `.github/workflows/build.yml`: one step after "Configure NuGet source for vcpkg
      binary cache" with `continue-on-error: true`, and `VCPKG_BINARY_SOURCES` on the install step derived from its
      outcome (`clear;nuget,…` on success, `clear` otherwise). Verify: `jq`-free YAML read — the install step's env
      references `steps.cache.outcome`, and the two values differ.

## 4. Wiring the checks

- [x] 4.1 Add the new self-test to the pipeline: a `Check the vcpkg cache probe` step beside "Check action
      runtimes" in `.github/workflows/build.yml`. Verify: `grep -c 'ci-vcpkg-cache-selftest' .github/workflows/build.yml`
      is 1.
- [x] 4.2 Add the gate rows to `tools/loop/loop.conf`: `tools/ci-vcpkg-cache*` → its self-test, and
      `.github/workflows/*` → the new self-test (the pin's own row already exists). Verify: `bash tools/loop/gate.sh`
      runs both for a change touching `.github/workflows/build.yml`.
- [x] 4.3 Extend `guidelines/BuildNative.md` section 12e with the pin, the probe and the measurement. Verify:
      `bash tools/check-doc-routes.sh` exits 0.

## 5. Evidence and the record

- [x] 5.1 Record the red run of 1.1 as `evidence/red-on-HEAD.log`, the probe's two outputs as
      `evidence/probe-feed-dead.log` and `evidence/probe-feed-ok.log`, and the gate as `evidence/gate.log` with its
      tallies.
- [x] 5.2 Run `bash tools/loop/evidence-check.sh openspec/changes/fix-vcpkg-cache-dies-with-the-runner-image --root .`
      and record `PASS`. Verify: no `FAIL` line.
- [x] 5.3 Archive the change and confirm the delta merged into `openspec/specs/ci-vcpkg-cache/spec.md`. Verify:
      `openspec show ci-vcpkg-cache --type spec --json | jq '[.requirements[] | select(.text | test("pinned image"))] | length'`
      is 1.

## 6. The correction round — the probe rejected its own argument

Requirement: the same requirement, the sentence that the run probes the cache before the install.

- [x] 6.1 Record what the merged probe did on its first real run: PR #762's own workflow run printed
      `##[warning]ci-vcpkg-cache: … did not answer (1):   at NuGet.CommandLine.Program.MainCore …` and the install
      then ran with `VCPKG_BINARY_SOURCES: clear`, compiling every port from source
      (`evidence/probe-rejected-its-own-argument.log`). Verify: the log carries both lines.
- [x] 6.2 Fix the invocation and the diagnosis: `nuget list -Source <url> -NonInteractive` (`-Take` is an option of
      `search`, not of `list`), and the reported line is the first that names a cause rather than the last, which
      is always a stack frame. Add `vcpkg-cache#aRejectedArgumentIsNotReadAsADeadFeed` so a client that refuses the
      probe's own arguments cannot be read as a dead feed again. Verify:
      `bash tools/ci-vcpkg-cache-selftest.sh` reports `all 7 cases passed`.
- [x] 6.3 Re-run the gate and record it; the probe's own run on the next branch is the measurement that it now
      reports an answering feed. Verify: `LOOP-GATE: tests=… failures=0 errors=0`, and a workflow run whose probe
      step concludes `success` with the install restoring packages.
