# Evidence tally — 2026-10-21, base commit 2e732f0e00fa771d04569483ffe80bc2ca3fa974

| log | taken | result |
|---|---|---|
| red-on-HEAD.log | 2026-10-21T12:24:21+02:00 | `bash tools/gen-feature-list-selftest.sh` with the base commit's tool restored in place: tests=107 failures=2 (105 passed); both new cases fail with `entry paragraph is 322 characters, more than the 320 allowed` |
| green.log | 2026-10-21T12:24:09+02:00 | `bash tools/gen-feature-list-selftest.sh` with the fix: tests=107 failures=0 (107 passed) |
| real-run-on-HEAD.log | 2026-10-21T12:21:15+02:00 | `gen-feature-list.sh --release 2026-10-10-1` with the base commit's tool: exit 1, seven catalogue paragraphs at 321-322 characters, neither document written |
| real-run-after-catalogue-fix.log | 2026-10-21T12:16:51+02:00 | the same run with only the catalogue clip fixed: exit 1, 99 x `entry keyword is N words, more than six` — the note gate the catalogue gate had hidden |
| real-run-after-fix.log | 2026-10-21T12:22:29+02:00 | the same run with the whole fix: exit 0; catalogue 19 entries, longest paragraph 317; release note 144 entries, longest paragraph 81; longest keyword 6 tokens in both (doc-FEATURES-after-fix.md, doc-RELEASE-NOTES-after-fix.md, sha256 66bcaf14… and 2280ba78…) |
| real-run-after-fix-2nd.log | 2026-10-21T12:22:36+02:00 | second identical run: exit 0, both documents byte-identical to the first (same sha256) |
| check-doc-routes.log | 2026-10-21T12:23:44+02:00 | `bash tools/check-doc-routes.sh`: all 89 section references resolve |
| check-portability.log | 2026-10-21T12:23:44+02:00 | `bash tools/check-portability.sh`: OK — 44 engines and 2 templates name no project value |
| pair-awk-empty-removed.log | 2026-10-21T12:21:18+02:00 | adjacent defect, not addressed by this change: an empty removal list and one added name yield `x#Some  -`, the addition reported as a removal |
