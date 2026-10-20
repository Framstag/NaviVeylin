# Design — fix-loop-review-diff-excludes-evidence

## Root cause, and what it leaves to be decided

A review round is handed a diff of everything an item's commit changed, built with no pathspec:

```sh
# tools/loop/run.sh, before this change — the round-1 site, and the same line again for every later round
git -C "$worktree" diff "$base"..HEAD > "$diff_file"
```

What an item commits is not only its change. Phase F of the worker's contract copies every cited XML into the
change's own `evidence/` directory, and the gate's own reports are HTML: item #126's round-3 diff measured
435 KB and 9 935 changed lines, of which 9 364 (94.3 %) were `evidence/gate-reports/*.html` (3 143 + 3 143 +
1 042 + 781 + 195 lines), `evidence/revert-check-mutated.xml` (400) and `evidence/red-buildsrc-scan.xml` (187),
against 112 lines of Kotlin — the figures issue #204 quotes for that round. Re-measured on the run's own copy
of the file (`evidence/red-round3-diff-size.txt`): 10 883 lines and 446 092 bytes, of which 9 574 are inside an
`evidence/` tree — the same shape, and the same conclusion, from a slightly later copy. The tree carries the
same shape at rest: 609 tracked files under `openspec/changes/archive/*/evidence/`, 23 MB.

So the decision the root cause leaves is narrow: **by what rule does the diff keep the change and drop the
generated output?** It is not "which files matter" — the reviewer's items 2, 4, 6 and 7 read the code hunks,
the tasks, the quoted numbers and the spec delta, and all of those must arrive. It is only what to exclude.

## The alternatives the evidence eliminated

- **Filter by file extension** (drop `*.html`, `*.xml`, `*.log`, `*.txt`) — eliminated by both directions. It
  deletes files a reviewer must read that live *outside* an evidence tree: the case's
  `notes-for-the-reviewer.txt` is exactly one, and this repository keeps `.txt` and `.md` evidence the reviewer
  is asked to check against a quoted number (invariant 1, "a row you can no longer reproduce is labelled *not
  retained*"). And it keeps what it was meant to drop: a `.md` or `.diff` blended into an evidence tree passes
  an extension filter untouched. The defect is *where* the bytes live, not what they are named.
- **Truncate the diff at N lines or N KB** — cuts the change, not the evidence. `git diff` orders by path, so
  400 lines of `evidence/*.xml` arrive before the Kotlin hunks under a truncation that would have to be large
  enough for any change; a reviewer given the first N lines of item #126's round 3 would have received the HTML
  reports and none of `HardcodedStringScanner.kt`. It also has no rule for what to cut, which is a judgement
  the run cannot make.
- **Hand the reviewer the file list and a summary** — loses the hunks the checklist quotes. Item 2 is "quote
  the hunk", item 5 is "quote the message and its timestamp"; a summary is the worker's own claim, and the
  reviewer exists because a claim is what cannot be trusted (D3, "the run verifies rather than trusts"). It also
  makes the review cheaper by making it unable to fail.
- **Exclude all of `openspec/changes/**`** — removes the change's own spec delta, `design.md` and `tasks.md`,
  which are the subject of reviewer items 1, 4 and 7. The archived change is the change as much as the Kotlin
  is; only its `evidence/` leaf is generated.
- **Instruct the reviewer to ignore the evidence sections of the diff** — the bytes are still read: the cost is
  in what the round's context carries, not in what the reviewer is told to do with it. Measured once already in
  a sibling shape: the reviewer prompt gained `LOOP-CORRECTIONS-ONLY` because a rule in the prompt could not
  make an artifact smaller.
- **Archive the evidence compressed** (one archived change already ships `gate-xml.tar.gz`) — a real
  improvement to the 23 MB at rest, and not this defect: the round that fails is the one whose change has not
  been archived yet, and it would rewrite 609 tracked files in the same change that fixes the reading surface.
  Recorded here as the follow-on the same measurement supports.

## The chosen shape

One function in `run.sh`, the only place a review diff is written:

```sh
write_review_diff() { # write_review_diff <base> <file>
  local spec
  spec=$(loop_conf_value LOOP_REVIEW_EXCLUDE_PATHSPEC)
  if [ -n "$spec" ]; then
    git -C "$worktree" diff "$1"..HEAD -- . "$spec" > "$2"
  else
    git -C "$worktree" diff "$1"..HEAD > "$2"
  fi
}
```

and the value itself in `loop.conf`, beside the other keys that are the project's:

```sh
LOOP_REVIEW_EXCLUDE_PATHSPEC=":(exclude,glob)**/evidence/**"
```

Three properties follow from the shape and are what the case asserts:

- **The exclusion is positional, not nominal.** `**/evidence/**` matches an `evidence` directory at any depth —
  the change's own `openspec/changes/archive/<change>/evidence/`, a stray top-level one — and matches
  `evidence2/` not at all. A file outside such a tree is untouched, whatever its extension.
- **`-- .` plus the exclusion, and not "-`--` `<dir>`" allow-listing.** An allow-list of the trees a reviewer
  needs would silently drop whatever a project adds next (a `notes/` directory, a `docs/` delta); an exclusion
  fails open — a new kind of file is reviewed until someone says it is generated.
- **The value is the project's.** A project with no generated tree sets the key empty and its rounds get the
  whole diff; a project that calls the tree something else names it. The phase keeps no path of its own, the
  same rule that keeps the gate command and the worker's skills in `loop.conf` (D16), and the reviewer prompt
  renders the same value through `{{REVIEW_EXCLUDE}}` so the sentence a reviewer reads cannot describe an
  exclusion the diff did not apply.

## Where this could go wrong

- **A project excludes too much.** The exclusion is a recorded value, so the blast radius is that project's
  `loop.conf`; the case pins the direction the defect was in (evidence out, everything else in) and would fail
  if the value grew to cover a tree carrying a change.
- **The reviewer reports the evidence as missing.** Mitigated by the prompt line, and by the run's own
  `EVIDENCE_DIR`, which already was the evidence route: the directory is verified on the file system by
  `carried=` before the round starts, so the reviewer is told where to look rather than what it cannot see.
- **The pathspec is a git-version fact.** `:(exclude,glob)` and the `**` wildcard are documented pathspec
  behaviour, verified here on git 2.56.0; a host with an older git fails the diff loudly (`git diff` errors
  rather than silently dropping the exclusion), so the failure mode is visible and not silent. The case runs
  the real git, not a stub (D13).

## Threading, lifecycle, verification

- Threading model: not applicable. The change touches shell tooling only — no dispatcher, no lifecycle, no new
  component, no app code. The one process concern is unchanged: the diff is written by the run's own process,
  between children and never during one (invariant 2).
- Verification: the shell case `run#reviewDiffExcludesTheEvidenceArchive` (real git, stub `gh` and stub `pi`,
  offline), red on the unpatched script (`evidence/case-red-unpatched.log`) and green after it
  (`evidence/case-green.log`); `run#engineNamesNoProjectFact` for the engine's text, since the phase's wording
  changed; no unit test, no device and no build, because no compiled file is touched.
