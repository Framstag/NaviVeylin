# Evidence tally — 2026-10-21, base commit 2e732f0e00fa771d04569483ffe80bc2ca3fa974

| log | taken | result |
|---|---|---|
| red-on-HEAD.log | 2026-10-21T12:31:55+02:00 | `bash tools/gen-feature-list-selftest.sh` with the base commit's tool (`2e732f0e…`, restored in place): tests=108 failures=2 (106 passed). The catalogue case fails with `entry paragraph is 322 characters, more than the 320 allowed (line 32)`; the release-note case fails at the note gate and for its own reason, with `entry keyword is 32 words, more than six (line 11)` |
| red-on-half-fixed.log | 2026-10-21T12:31:40+02:00 | the same suite with the fix as first reviewed, whose `entry_paragraph` reserved only the full stop (`$max - 1`): tests=108 failures=1 (107 passed) — the release-note case fails at the note gate with `entry paragraph is 322 characters, more than the 320 allowed (line 11)`. This is the case the corrected reserve answers |
| green.log | 2026-10-21T12:32:10+02:00 | the same suite with the corrected tool (`entry_paragraph` reserving `$max - 5`, as the clip does): tests=108 failures=0 (108 passed). Taken after both mutations, not before |
| real-run-on-HEAD.log | 2026-10-21T12:21:15+02:00 | `gen-feature-list.sh --release 2026-10-10-1` with the base commit's tool: exit 1, seven catalogue paragraphs at 321-322 characters, neither document written |
| real-run-after-catalogue-fix.log | 2026-10-21T12:32:26+02:00 | the same run with the catalogue clip fixed and the note sites untouched (`clip($max - 5)` in `render_mechanical`, the base commit's tool otherwise): exit 1, catalogue written, 99 × `entry keyword is N words, more than six` (the first is 8 words), release notes not written — the note gate the catalogue gate had hidden |
| real-run-after-fix.log | 2026-10-21T12:32:36+02:00 | the same run with the whole fix: exit 0; catalogue 19 entries, longest paragraph 317; release note 144 entries, longest paragraph 81; longest keyword 6 tokens in both (doc-FEATURES-after-fix.md, doc-RELEASE-NOTES-after-fix.md, sha256 66bcaf14… and 2280ba78…) |
| real-run-after-fix-2nd.log | 2026-10-21T12:32:45+02:00 | second identical run: exit 0, both documents byte-identical to the first (same sha256) |
| check-doc-routes.log | 2026-10-21T12:32:57+02:00 | `bash tools/check-doc-routes.sh`: all 89 section references resolve |
| check-portability.log | 2026-10-21T12:32:57+02:00 | `bash tools/check-portability.sh`: OK — 44 engines and 2 templates name no project value |
| openspec-validate.log | 2026-10-21T12:33:49+02:00 | `openspec validate --all`, taken after the spec sentence in this change was extended: 176 passed, 0 failed (176 items) |
| pair-awk-empty-removed.log | 2026-10-21T12:21:18+02:00 | adjacent defect, not addressed by this change: an empty removal list and one added name yield `x#Some  -`, the addition reported as a removal |

How the real runs were taken: `--root` was the tree under review, `--data-dir` a fresh copy of `tools/feature-list/`
with its `cache/` removed (its `snapshots/` kept, so the baseline is `2026-10-08-4`), `--out-dir` a new empty
directory, `--release 2026-10-10-1`. `real-run-after-catalogue-fix.log` used the base commit's
`tools/gen-feature-list.sh` with one change — `clip($max)` to `clip($max - 5)` at `:496` — and the note sites
untouched; `real-run-after-fix*.log` used the tree's tool. The two documents those runs wrote are
`doc-FEATURES-after-fix.md` and `doc-RELEASE-NOTES-after-fix.md` (sha256 `66bcaf14…`, `2280ba78…`, unchanged from the
run taken before this correction).
