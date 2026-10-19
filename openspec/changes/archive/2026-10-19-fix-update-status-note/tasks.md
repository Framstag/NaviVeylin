# fix-update-status-note — tasks

- [x] 1.1 A case that makes a comment-only call and asserts the note is absent: red on HEAD
      (`update: #21 already carries  — no label write`), green with the guard.
- [x] 1.2 The note guarded by `status_label` being non-empty.
- [x] 2.1 Mutation A1.5 (the guard removed) red-then-green.
- [x] 2.2 Live proof: the comment posted on #182 during this change printed nothing, where the run of
      2026-10-19 saw the note six times.
