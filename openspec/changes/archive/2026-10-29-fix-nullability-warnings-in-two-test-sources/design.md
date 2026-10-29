# Design — fix-nullability-warnings-in-two-test-sources

## Root cause

A unit test reads a value whose Kotlin declaration is nullable and uses it where a Java signature declares
non-null: `StartMapResolver.resolve(): String?` feeds `List<String>.contains` and `File.parentFile: File?`
feeds `File.relativeTo`. Kotlin's platform-type leniency accepts the call, so the case compiles and passes and
the only trace is one `w:` line per flavor compile.

## The single fix

State the assumption where the fixture guarantees it: `requireNotNull(…)` with a message that names the
fixture, in both places. The issue's direction allows a non-null assertion or `requireNotNull`/`checkNotNull`
with a message; the message-bearing form is chosen because a future failure then says which input was missing
rather than throwing a bare NPE.

The assumption is held by a source scan, `UnitTestSourceNullabilityBaselineTest` (two scan cases and one
positive control), because a unit test cannot read the compiler's log of the module it runs in. The scans name
a construct family — a `…resolve()` result handed to `.contains(…)`; a `parentFile` member access behind
neither `?.` nor `!!` nor `requireNotNull` — so they are call-site guards against the two spellings returning,
not proofs that no nullable crosses a Java signature; a nullable reached through an intermediate local is
outside what a Kotlin text scan can follow. This is the shape the existing source-set warning guards use
(`AutoTestSourceWarningBaselineTest`, `QualifierUseSiteTargetTest`).

## Blast radius

Four files: the two test sources, the new guard class, and one `guidelines/Build.md` §4a bullet. No production
source, no spec requirement text outside `build-test-gate`'s delta, no build script, no fixture. The affected
cases are the two files' own (`StartMapResolverTest` 9 cases, `ShippedSpecClassificationTest` 1 case); the new
guard adds 3.

## Rollback

`git revert` of the change's commit; nothing outside the change reads the new guard or the messages.

## Alternatives the evidence eliminated

- **Non-null assertion only (`!!`)**: removes the warning, but a failure would throw a bare NPE with no
  fixture named — the item's direction asks for a message that names the input. Kept only as an *accepted*
  form in the scan's positive control, because a site that knows the value is present may use it.
- **Suppressing the diagnostic at the site or turning the family off**: `guidelines/Build.md` §4a states the
  build must have no warnings — "report them, don't ignore" — so a suppression would hide the family rather
  than resolve it.
- **`-Werror` for the module**: §4a records that the tree's four unit-test compiles carry ~100 other `w:` lines
  owned by other items (the `ExperimentalCoroutinesApi` opt-in family and the non-opt-in remainder), so a
  warning-as-error flag would fail the gate on warnings this change does not own.
