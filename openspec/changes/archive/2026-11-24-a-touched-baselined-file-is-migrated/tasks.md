# Tasks — a touched baselined file is migrated

## 1. The rule

- [x] 1.1 Add the rule to `guidelines/Structure.md` §5, with the `comm -12` recipe a reviewer runs over the diff.
- [x] 1.2 Record in `guidelines/Structure.why.md` §5 why the rule is judged by review and not by a gate arm.
- [x] 1.3 Add the requirement to `openspec/specs/architecture-test-suite/spec.md` through this change's delta.

## 2. The verification

- [x] 2.1 `tools/check-doc-routes.sh` — the routing table still resolves every row it names.
- [x] 2.2 `tools/check-doc-values.sh` — the figures the documents state still match the tree.
- [x] 2.3 `tools/check-rule-routes.sh` — the rule's own citations resolve.
- [x] 2.4 `tools/check-change-readiness.sh openspec/changes/a-touched-baselined-file-is-migrated` → `ok`.
- [x] 2.5 `openspec validate a-touched-baselined-file-is-migrated --strict` → valid.

No build and no suite: the change touches no compiled file (`guidelines/Build.md` §2, its scope block).
