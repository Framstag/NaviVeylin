# Proposal

## Why

The project's shipped behaviour is described by 153 capability specs (~1 MB of `spec.md`, 929 requirements), and that
number only rises — but nothing turns it into something a user or a shopper can read. Today the only user-facing
description is `README.md`, which is a build-and-run document, not a feature list, and the only feature-shaped text in
the repository is `TODO.md`'s table of *unimplemented* ideas. Every release therefore ships with no honest,
spec-derived account of what the product does or what changed in it, and hand-writing one cannot stay content-wise
correct as specs move.

The cost of the work is the other half: a single pass over every spec does not fit one context window (requirements-only
JSON projection is ~121 k tokens and grows linearly), and regenerating all prose on every run pays for 153 specs to
learn that one changed. Both problems have the same answer — the *selection* of what to write about must be
deterministic code, and only the *phrasing* may be model work.

## What Changes

- **New deterministic index of specs, keyed per capability.** A committed shell + `jq` tool enumerates
  `openspec/specs/`, hashes each `spec.md`, and derives one stable key per requirement: `<spec-id>#<Requirement name>`.
  The delta between two runs is set arithmetic over those keys — code, not model judgement.
- **Two curated files carry the only human judgement.** `areas.yaml` maps a spec id to a feature area (and its
  default surface), `user-facing.yaml` marks a spec id as user-visible or internal. A spec id appearing in neither
  **fails the run** rather than being silently dropped or silently published.
- **A snapshot per release version.** `snapshots/<version>.json` records the capability keys and their fact base at
  the moment a version was generated. The version is always **passed in** (`--release <versionName>`), never
  discovered: `app/release-version.properties` is gitignored and machine-local, so CI and another workstation cannot
  read it.
- **`FEATURES.md`** (repository root): the living, marketing-shaped catalogue. Regenerated **only for dirty areas**;
  unchanged areas are spliced from cache with zero model launches. Car is a **cross-cutting tag** on every area, plus
  one small *In your car* area for the capabilities that are car-only.
- **`RELEASE-NOTES.md`** (repository root): append-only history, one entry per version, listing only capabilities
  whose key or text moved **and** that are classified user-visible. Re-running a version regenerates that version's
  entry from its own baseline — never appends a second one — so the entry is a pure function of
  `(baseline snapshot, specs now, version)`.
- **A refusal path with a verdict.** A release that moved nothing user-visible emits **no** entry and says so
  explicitly, instead of letting a phrasing step invent filler. `fix-aggregate-run-test-flakes` or `slim-agents-md`
  archiving must produce no What's-new.
- **A gate over the generated prose.** Every bullet in `FEATURES.md` carries the spec ids it came from; a bullet with
  none fails the run, and so does a number that no cited spec contains. Pep is thereby constrained to phrasing and
  cannot introduce facts.
- **A self-tested tool, offline.** `tools/gen-feature-list-selftest.sh` exercises enumeration, delta, the
  unknown-spec-id failure, the empty-release refusal and the bullet gate against fixtures — no build, no device, no
  network, no model.
- **No model in the deterministic layer, and no model at release time.** `./gradlew release` is untouched: the
  version string is the entire interface between the two.

## Capabilities

### New Capabilities

- `spec-feature-index`: the project derives a stable per-capability index of its specs — enumeration, spec hashing,
  `<spec-id>#<Requirement name>` keys, the curated area and user-visibility classification with its hard failure on
  an unclassified spec id, and the delta between two snapshots. This is the shared core both renderers consume.
- `feature-list-generation`: a user-facing feature catalogue is generated from the index, clustered by feature area,
  spliced from cache for unchanged areas, with car expressed as a cross-cutting tag plus one car-only area, and with
  every bullet traceable to the spec ids it came from.
- `release-notes-generation`: a per-version, user-visible-only note is derived by diffing the index against the
  version's baseline, regenerated idempotently when the same version is run twice, and withheld with an explicit
  verdict when nothing user-visible moved.

### Modified Capabilities

None. No existing capability's requirements change:

- `documentation-ownership` already states generically that a concern is given an owning document and that the
  routing table names it; adding a document satisfies those requirements rather than changing them. Its route check
  reads `guidelines/*.md` at run time, so a new guideline is covered without editing the check.
- `release-target` is deliberately **not** modified. Attaching note generation to the release target would make a
  release depend on a model; the version string stays a value passed to a separate command.
- No app-facing capability changes: this change adds no runtime behaviour to `:app` or `:auto`.

## Impact

**Additive; no breaking change.** Nothing in the application, the build, or the JNI bridge consumes these artifacts
yet. Rollback is `git rm` of the tool, the two documents and the `AGENTS.md` row — no migration, no version-code
implication. The only forward coupling is a later CI job or scheduled run naming the tool, which is out of scope here.

Affected files and modules:

- `tools/gen-feature-list.sh`, `tools/gen-feature-list-selftest.sh` — new. Shell + `jq`, matching
  `tools/check-doc-routes.sh` and `tools/declared-cases.sh`; **not** `python3`, because `openspec/config.yaml` forbids
  `python3` for OpenSpec interaction and CLI output processing.
- `tools/feature-list/` — new: `areas.yaml`, `user-facing.yaml`, `prompts/*.md` (hashed to yield the prompt version),
  `snapshots/<version>.json`. Committed: a cache that is not in the repository cannot save a CI run anything.
- `FEATURES.md`, `RELEASE-NOTES.md` — new, repository root.
- `guidelines/FeatureList.md` — new owning document for the generation rules (ownership, classification, regeneration
  contract, refusal path). Whether the rules live in a new guideline, in a section of an existing one, or only in the
  capability specs is the one decision left open here; `design.md` carries it with alternatives.
- `AGENTS.md` — one routing row and a short note in the module/tech-stack area; `README.md` — links to the two
  documents. Both follow the `documentation-ownership` requirements that already exist.
- `guidelines/Build.md` §5 — referenced, not modified: it owns versioning and is where the "the version is passed in,
  the gitignored file is never read" rule belongs.
- No Android component is touched: no manifest, resource, flavor, Gradle configuration, module or ABI.

**Not applicable:** no native or JNI code, no submodule patch and no bridge-module override; no Android framework API.

**Scope:** general project tooling and documentation. It reads the specs of every area, phone and Android Auto alike,
but it is neither a phone-only nor an auto-only feature.
