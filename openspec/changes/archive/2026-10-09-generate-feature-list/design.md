# Design

## Context

See `proposal.md` — Why. The state that shapes the approach, all measured on this checkout:

| fact | value |
|---|---|
| shipped specs (`openspec/specs/*/spec.md`) | 153, of which 146 flat and 7 nested (`auto/*`, `share/*`) |
| requirements (`### Requirement:`) | 929 — one capability key each |
| `spec.md` bytes | ~1 060 KB, ≈265 k tokens; a requirements-only projection of all specs is ~484 KB, ≈121 k tokens |
| per spec | mean 6 KB, median 4 KB, max 27 KB — ≈1.5 k tokens |
| plainly internal spec ids | 8 (`build-jvm-toolchain`, `build-sbom`, `build-test-gate`, `ci-unit-test-jni`, `ci-vcpkg-cache`, `documentation-ownership`, `license-compliance`, `test-coverage`), plus ~25 in the gray zone (`canvas-overrun`, `render-performance`, `path-text-font-caching`, `native-tile-data-cache`, `kover-aggregate-report`, …) |
| release version state | `app/release-version.properties`, **gitignored and machine-local**; `versionName = <yyyy>-<MM>-<dd>-<N>`, monotone, zero-padded |
| git tags | 0 — no tag anchor exists |
| archived changes | 241, directory-named `<date>-<change-name>` |

Constraints taken as given:

- `openspec/config.yaml` forbids `python3` for OpenSpec interaction and CLI output processing.
- `.pi/` is gitignored, so a skill is local tooling and cannot host anything a fresh clone, CI or another machine must run.
- `guidelines/Build.md` §5 owns release versioning; the release task is `./gradlew release`.
- `documentation-ownership` requires a recurring concern to have an owning document that the routing table names, and requires a new route's read cost to be recorded where the route is documented.

## Goals / Non-Goals

**Goals:**

- One deterministic layer that answers "which capabilities exist and which moved since version X" without a model, a build, a device or a network.
- Two renderers over that layer: a living catalogue and a per-version note.
- Regeneration cost proportional to what changed, not to the spec count.
- Reproducibility strong enough to be asserted: same inputs, byte-identical output; a release note is a pure function of its baseline, the specs, and the version.

**Non-Goals:**

- Not a Play listing. Character limits, store copy formatting and translation are out of scope; the catalogue is the source a listing is written from.
- Not wired to `./gradlew release`, CI, or a schedule in this change. The version is passed in; the caller is later work.
- No runtime code in `:app` or `:auto`; no manifest, resource, flavor, Gradle or native change.
- No judgement about whether a capability is good, and no capability that the specs do not state.

## Decisions

### D1 — Rules live in a new `guidelines/FeatureList.md`

`documentation-ownership` requires a recurring concern to have an owning normative document that the routing table names. Doc generation is such a concern and has none today.

Alternatives: **(b)** state the rules only in the capability specs — rejected, spec text is a behaviour contract and `documentation-ownership` keeps conventions next to the measurement that proved them; **(c)** a new section in `guidelines/Build.md` — rejected, `Build.md` is already ~29 k tokens and owns the build, not documentation generation; **(d)** a `tools/feature-list/README.md` — rejected, not a guideline, so the route check does not cover it and the concern still has no owning document.

Consequence to carry into tasks: the new document must be routed in `AGENTS.md`'s Documentation Map, and `documentation-ownership`'s "the routing cost is measured" requires that row to record the cost of the routed read. `tools/check-doc-routes.sh` picks the document up at run time without being edited — and fails while it is unrouted, which is the free enforcement of this decision.

### D2 — bash + `jq`, not `python3`, not a Gradle task

Chosen for consistency with `tools/check-doc-routes.sh`, `tools/declared-cases.sh` and their `-selftest.sh` siblings, and because `python3` is forbidden by project context.

Alternatives: **python3** — violates the project rule; **a `buildSrc` Gradle task** — rejected, it would make document generation need a Gradle configuration and a build, which contradicts the index requirement that it run without a build, and it would put a doc tool inside the release build's blast radius; **Node** — no other tool in `tools/` uses it, so it would add a second runtime for no gain.

### D3 — The phrasing step is a seam the harness fills, and the tool never calls a model

The deterministic tool never calls a model. Where prose must be produced it is filled one of three ways:

- `--emit-bundles <dir>` writes one JSON bundle per dirty area, lists them and stops. A harness — the local `feature-list` skill, or an unattended wrapper — fans out one subagent per bundle, each writing `<area-id>.md` into a sections directory.
- `--sections <dir>` reads that directory and assembles, gates and writes the documents. Together with `--emit-bundles`, this is how a subagent fanout drives the tool while the tool stays model-free and deterministic.
- `--phrase-cmd <cmd>` invokes a command once per dirty area with the bundle on stdin, for a wrapper that has a one-shot agent CLI instead of a subagent host.

With none of the three given, an area is assembled mechanically from its capabilities' own names. That degraded mode is why a run with nothing to do is provably model-free, and it is what the first real run uses.

Alternatives: **call the harness directly from the tool** — rejected, it would make the tool untestable without a model and would tie a committed tool to one harness; **produce no prose at all** — rejected as the default, it cannot produce the marketing phrasing this change exists for.

### D4 — Selection by capability key, phrasing by area

Delta is set arithmetic over `<spec-id>#<requirement name>` keys; prose is produced per feature area (≈12 areas, not 153 specs).

Alternatives: **one pass over everything** — 121 k tokens and rising linearly, rejected; **one model call per spec, then a reduce** — 153 launches for text a 12-spec area produces with better cross-spec coherence, and its cache granularity (per spec) would not match its output granularity (per area), rejected; **one call per changed capability for the catalogue too** — cannot merge, which is the 8:1 compression (929 keys → ≈80–120 bullets) the catalogue is for, rejected for the catalogue and chosen for the note, where the output is near 1:1 with the changed set.

### D5 — One classification file per spec id, with hard failure

`tools/feature-list/specs.yaml` holds one entry per shipped spec id: `area`, `userVisible`, `surfaces` (`phone`, `car`). `tools/feature-list/areas.yaml` holds the area catalogue: id, heading, order, and whether the area is the car-only area. A shipped spec id named by neither file, an area id referenced by an unknown name, and a stale entry for a spec that no longer exists are each reported; the first two fail the run.

Alternatives: **area-level classification only** (~12 decisions instead of 153) — rejected, too coarse where an area holds both a feature and plumbing (`daylight-map-palette` beside `canvas-overrun`); **model classification with a review flag** — rejected, it drifts per run and the flag is unread on an unattended run; **a spec-id prefix rule** (`auto-*` ⇒ car, `build-*` ⇒ internal) — kept only as the *seed* for the first classification of the 153 ids, never as the authority, because the gray zone (~25 ids) is decided by reading the spec, not by its name.

### D6 — Snapshot per version in `tools/feature-list/snapshots/<version>.json`

Per key: the digest of the requirement text and the spec id it belongs to; per area: the area's cache key (D7). The baseline is the greatest snapshot version that is numerically below the requested one.

Alternatives: **record the requirement text instead of a digest** — rejected, ≈484 KB per version accumulates in git for information the delta does not need and the current specs always supply; **git tags as the baseline** — no tags exist; **one rolling snapshot** — rejected, an older version's entry could then no longer be regenerated; **store only the rendered documents** — rejected, prose cannot yield a delta.

**`versionName` is not lexicographically ordered.** `N` is unpadded, so the string order is `-1 < -10 < -2 < -3 < -4`, the reverse of the release order once `N >= 10`. The selector compares the four components numerically (`sort -t- -k1,1n -k2,2n -k3,3n -k4,4n`) and takes the record immediately before the requested version. This was got wrong first — `ki_processing_failures.log`, 2026-10-08 21:05 — and the case that caught it is the spec's own ordering scenario: with `-1`, `-2` and `-10` present, a run for `2026-10-09-1` must use `-10`.

Cost of the chosen shape: **≈177 KB** per snapshot for 929 keys — larger than the ≈40 KB estimated before it was built, because each key carries its own spec id and full requirement name. Pruning an old snapshot costs only the ability to regenerate that version's entry.

### D7 — Phrasing cache under `tools/feature-list/cache/`, keyed by content

An area's cache key is a digest over the area's definition, the area's member set, each member's classification entry (user-visible flag and surfaces), and each member capability's requirement-text digest, combined with the prompt digest. The key deliberately does **not** cover a member spec's own byte digest: the requirement is that an area is regenerated when a member's requirement text changed, so including the spec digest would regenerate on every wording change and break the prose-only case. A changed key regenerates that area only. An area whose prose section is missing or carries a stale key header is rendered too — a repair rather than a dirtiness signal, without which a deleted cache would silently leave an area out of the document.

Alternatives: **`mtime`-based caching** — rejected, a checkout rewrites mtimes and would regenerate everything; **a hand-bumped cache version** — rejected, it is exactly the failure mode where a tone change silently keeps the previous prose; **per-spec prose cache plus an area reduce** — rejected, the reduce still needs a model, so it saves less than it costs in two-level bookkeeping.

The prompt digest is a digest of the files under `tools/feature-list/prompts/`, so editing an instruction invalidates every area without anyone remembering to bump a number — the requirement that an instruction change regenerates every area.

### D8 — Bullets carry their sources as HTML comments

Each bullet is followed by `<!-- cap: <spec-id>#<requirement name>, … -->`. The gate parses those, so the traceability and the coverage check are both mechanical, and the marker is invisible in rendered Markdown and in a copy-paste of the rendered text.

Alternatives: **visible inline markers** — rejected, they would appear in the marketing copy; **a sidecar `FEATURES.md.sources.json`** — rejected, a hand edit to the document silently desynchronises it, while an inline marker is self-checking.

### D9 — Writes are whole-file and atomic; one writer per checkout

Both documents are written through a temporary file and renamed, and no run appends. A run for a version writes that version's entry, leaving other entries untouched, which is what makes re-running a version idempotent rather than duplicating.

Alternative: **append with a "already generated" marker file** — rejected, the marker is a second source of truth that a failed run leaves inconsistent.

### D10 — Threading and process model

This change adds no Android component, so `guidelines/Design.md` §4 does not apply in the usual sense. Stated explicitly because the change is still concurrent code:

- The tool is a single-threaded shell process plus `jq`; it holds no state across invocations beyond the files on disk.
- Fan-out happens only at the phrasing seam: the tool invokes the phrasing command once per dirty area, and the degree of parallelism is the command's business, not the tool's. Sequential invocation is correct and is what the self-test asserts.
- No coroutine, dispatcher, handler, Android lifecycle, or `:app`/`:auto` code is involved. The documents are generated on a workstation or in CI, never on a device.
- Concurrent runs of the tool against one checkout are not supported; the project already requires one builder per working tree, and the atomic rename keeps a reader from seeing a half-written document.

### D11 — No generation timestamp in either generated document

Neither document carries a "generated on" date or any other value that changes between two runs with identical inputs. A timestamp would make the byte-identity requirement unsatisfiable and would turn every run into a diff. Where a document must be anchored in time, the release *version* is the anchor — it is an input, and it names a release rather than the moment a script ran.

Alternative: **a generated-at header** — rejected, it would conflict with the catalogue being a function of its inputs, and would make a no-op run look like a change in every review.

### D12 — Verification

No on-device, logcat or GPX verification applies: the change adds no runtime code and no rendering path, so there is no frame budget, surface or device behaviour to measure. Saying so rather than implying device proof.

What is verified, and how, with the numbers to record:

| claim | measurement |
|---|---|
| a no-op run invokes no model | run with a phrasing command that writes its invocation to a log; the log is empty and the catalogue is byte-identical (`sha256sum` before/after) |
| a one-requirement change dirties exactly one area | the run's report names one dirty area and the log shows one invocation |
| the index is model-free and fast | the same run with no phrasing command configured completes and reports keys and delta; `time` stays under the 30 s the spec requires |
| idempotency | two runs for one version produce one entry, byte-identical |
| the classification gate fires | fixtures: an unclassified spec id fails, a stale id reports without failing |
| the coverage and sourcing gates fire | fixtures: a bullet with no key fails, a user-visible key carried by no bullet fails, an invented figure fails |
| the empty-release refusal | a version whose only changes are internal writes no entry, exits 0, and reports the verdict |
| baseline selection | snapshots `…-1`, `…-2`, `…-10` select `…-10` for `…-09-1` |

Every one of those is exercised by `tools/gen-feature-list-selftest.sh` against fixtures, with no build, no device, no network and no model — the same shape as `tools/check-doc-routes-selftest.sh`.

## Risks / Trade-offs

- **A new spec archives and a scheduled run fails on the unclassified id while nobody watches** → the failure names the id and the one-line fix; the guideline states that classifying a new spec belongs to the change that archives it; the run is designed to fail rather than publish, because publishing an unclassified spec is the failure this design exists to prevent.
- **The ~25 gray-zone ids are classified wrong on the first pass** → the classification is a reviewable file, one line per spec, and the coverage and sourcing gates make a wrong `userVisible: true` visible as an internal bullet in the catalogue rather than silent.
- **8:1 merging drops a nuance the specs state** → the merged bullet carries every key it covers and the coverage gate fails on a user-visible key no bullet carries, so a drop is loud; the nuance inside a bullet is a phrasing matter and is not gated.
- **A requirement rename reads as churn** → the note pairs a vanished key with an added key in the same spec and reports a rename as a change; a pairing that is not confident is reported in the run report for review rather than published.
- **Committed caches grow** → ≈40 KB per snapshot, small text sections per area, prompt files; the delta needs the snapshot and nothing else, so pruning old snapshots only costs the ability to regenerate an old version's entry.
- **The prose is a model's, and a wrong claim could reach a store listing** → the sourcing gate limits statements to figures and details present in cited specs, so the risk is confined to phrasing rather than to invented facts; store copy is written by a human from the catalogue, not published from it.
- **A first run over 153 ids classifies a large file by hand** → one-off; the prefix rule seeds it and the gray zone is the review.

## Migration Plan

Additive; nothing existing is modified or deleted. Order: classification files first (they gate everything), then the tool, then the first catalogue, then the first snapshot, which establishes the baseline and deliberately writes no entry.

Rollback: `git rm` the tool directory, the two documents and the new guideline, and remove the `AGENTS.md` row. No application, build, native or version-code consequence, because nothing in the app or the build reads any of it. The only forward-coupled artefact is a later CI or scheduled caller naming the tool, which this change does not add.

## Open Questions

- The area headings' final wording and count — deferred; `areas.yaml` is the single place it is decided, and changing it dirties the areas involved without touching the specs.
- Whether the note is additionally trimmed to a store-listing character limit — deferred; it is a renderer on top of the note, not a change to what is selected.
- Which wrapper fills the phrasing seam in an unattended run (a one-shot `pi -p` command, or a scheduled subagent fanout) — deferred by D3; the seam is filled by a command or a sections directory, and the self-test asserts both the no-model and the stub paths.
