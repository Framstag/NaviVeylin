# Feature-list recipes — the invocations, the gates and the provenance the rules are read against

Procedure and history for `guidelines/FeatureList.md`. Each anchor names a section of that document; the rules
stay there.

## §4 The classification is the only human judgement, and an unclassified spec stops the run

- The widened check has two entry points: the loop's gate maps any `openspec/*` path to
  `--check-classification` (`tools/loop/loop.conf`), and the CI classification step names the id on the pull
  request that introduces the delta. Provenance: the CI step that names the id was added alongside the gate
  (issue #101, `TODO.md` §157's second candidate).
- Run it directly with `bash tools/gen-feature-list.sh --check-classification`.

## §6 The prose gates, not a reviewer's memory

- `tools/feature-list/gate.awk` holds the prose gates; read or run it on its own.
- Provenance of the word report's cap: the first real generation produced 352 `unsourced word:` lines for 225
  bullets, which buried the mismatch the report exists to surface (issue #103). It was that report which found
  the British/American vocabulary slip now ruled in `prompts/tone.md`.

## §8 The version is an input

- Generate with `bash tools/gen-feature-list.sh --release <versionName>`.
- The build's version-state file is `app/release-version.properties`; the baseline snapshot compares the four
  version components numerically because the release format is not lexicographically ordered once `N >= 10`.
