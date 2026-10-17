# Import evidence — the live run, and what the tracker holds

The migration's own record, and the table `tools/check-todo-references.sh` resolves a `§N` against when it
runs with no token (design D6, D11). Two tables: one row per imported section, one row per retired entry.

Machine: `gh` authenticated as **Framstag** against **Framstag/NaviVeylin**, JDK 27, no Gradle, no device.

## 1. The dry run, re-measured before anything was written (task 3.1)

```
bash tools/issues/import/todo-md.sh > findings.json
bash tools/issues/import.sh --findings findings.json --json > /tmp/dry.json
jq -S '{previewList,histogram,renames}' /tmp/dry.json > new.json
jq -S '{previewList,histogram,renames}' \
  openspec/changes/archive/2026-10-17-github-issue-tooling/evidence/todo-dry-run.json > old.json
diff old.json new.json        # empty — the two are identical
```

Counts: **137** sections in `TODO.md` (`grep -cE '^## [0-9]+\.'`), **137** metadata lines
(`grep -c '^\*\*id:\*\*'`), **137** findings from the adapter, **137** previews from the reconciler, **0**
already present, **2** renames (`category rendering → map-rendering`, `category build → build-and-harness`).

**Measured drift from the plan's prose.** `proposal.md` and `design.md` say 136 sections and 136 metadata
lines; the file holds 137, and the recorded dry run in the prerequisite change already reads 137. The
histogram in `design.md` (`open 97 · fixed-by 17 · on-hold 14 · in-flight 8`) is stale too: the file's own
statuses are `open 75, open-device 21, fixed-by 17, on-hold 14, in-flight 8, retired 1, unverified 1`. No
section and no metadata line was added or removed by this change; the plan's numbers were written from an
earlier measurement.

## 2. The import

```
bash tools/issues/import.sh --findings findings.json --apply --json
```

`2026-10-17T09:02:18Z → 09:16:35Z`, exit 0: **137 created**, 0 skipped, 0 already present.

| assertion | command | result |
|---|---|---|
| one issue per section | `gh issue list --state all --json number \| jq length` | 137 |
| every section covered | the 137 `§N` titles vs `grep -o '^## [0-9]*' TODO.md` | 0 missing, 0 titles without an id |
| every issue open | `gh issue list --state all --json state` | 137 open, 0 closed |
| one `class/*` | `jq '[.[] \| select(([.labels[].name \| select(startswith("class/"))] \| length) != 1)] \| length'` | 0 |
| one `category/*` | the same, `category/` | 0 |
| one `status/*` | the same, `status/` | 0 |
| provenance on all | `jq '[.[] \| select([.labels[].name] \| index("source/todo-import") == null)] \| length'` | 0 missing |
| nothing pre-authorised | `jq '[.[] \| select([.labels[].name] \| index("agent/eligible") != null)] \| length'` | 0 |
| no closure | `gh issue list --state closed --json number` | empty |
| the taxonomy and nothing else | `gh label list --limit 100 --json name` | the profile's 36 values plus GitHub's 9 defaults |

**Idempotence (task 3.4), on the live tracker:**

```
bash tools/issues/import.sh --findings findings.json --apply     # exit 4
0 preview(s), 137 already present, 0 created
```

The script is its own resume — the `<!-- nvid: N -->` marker is the identity, so a re-run creates nothing
and a failure halfway is continued by re-running (design D6).

## 3. Why the tracker also holds 21 retired entries

Measured on 2026-10-17, before the deletion: **87 live citations, in 55 files, name 23 ids whose `## N.`
section had already been removed** from `TODO.md` by earlier cleanup passes (the entries were closed, and the
citations stayed as provenance for a rule or a test). The check's second half is absolute — a citation that
resolves to nothing fails it — so the migration had to give those ids something to resolve to. Two mechanisms,
both recorded here because both changed what the tree says:

**Redirected** — four citations named an id that never carried a section at all:

| id | cited in | now cites | why |
|---|---|---|---|
| `§140` | `RoutePanel.kt:145`, `MapCanvasViewModelModeTest.kt:220`, `RoutePanelStoppedStateTest.kt:34`, `TODO.md` | `§165` | `§140` was filed by `fix-nav-overlay-stop-tap` but never reached the file; the entry was re-filed as §165 |
| `§80a` | `HardcodedStringScanner.kt:20` | `§80` | `§80a` never existed; the entry is §80 |
| `§813` | `guidelines/Build.md:876` | `§22` | a typo; the conclusion it names is the Gradle-10 deprecation sweep |
| `§125` | `tools/prune-native-configs.sh:73` | `§134` | never existed; the sentence it names lives in §134 |

**Tombstoned** — the other 21 ids keep their citations and gained one `status/retired` issue each, so `§N`
resolves exactly as it did against the file. Each tombstone's body is the entry's **last revision in git**,
recovered from the parent of the commit that removed it, plus a provenance block naming that commit and the
entry's recorded state at removal. Nothing is inferred: `openspec/changes/migrate-todo-to-github-issues/tombstones.sh`
builds the findings document, and `tombstones.json` is the record.

```
bash tombstones.sh                                             # 21 findings
bash tools/issues/import.sh --findings tombstones.json --apply --json
```

`2026-10-17T09:17:26Z → 09:19:48Z`, exit 0: **21 created**. 15 of the 21 predate the backlog's metadata
convention (their sections carry no `**id:**` line), so they carry the tombstone's own labels —
`class/improvement` and `category/specs-and-process` — and each of those bodies says so, in the second line of
its provenance block. The other 6 (`§51`, `§100`, `§117`, `§121`, `§122`, `§169`) carry their own recorded class
and category.

## 4. The `§N → issue` table

### Imported — one row per `## N.` section (137)

| § | issue |
|---|---|
| 1 | #47 |
| 2 | #48 |
| 3 | #49 |
| 4 | #50 |
| 5 | #51 |
| 6 | #52 |
| 8 | #53 |
| 9 | #54 |
| 10 | #57 |
| 14 | #55 |
| 15 | #58 |
| 16 | #46 |
| 17 | #45 |
| 19 | #44 |
| 22 | #56 |
| 23 | #59 |
| 24 | #60 |
| 27 | #61 |
| 29 | #43 |
| 34 | #42 |
| 35 | #62 |
| 36 | #63 |
| 37 | #64 |
| 39 | #67 |
| 40 | #68 |
| 44 | #65 |
| 46 | #41 |
| 49 | #70 |
| 56 | #71 |
| 57 | #72 |
| 64 | #40 |
| 65 | #39 |
| 66 | #38 |
| 70 | #37 |
| 71 | #36 |
| 72 | #35 |
| 73 | #34 |
| 74 | #33 |
| 75 | #12 |
| 76 | #32 |
| 77 | #31 |
| 78 | #73 |
| 81 | #30 |
| 82 | #29 |
| 83 | #28 |
| 84 | #27 |
| 91 | #26 |
| 92 | #25 |
| 93 | #24 |
| 95 | #22 |
| 99 | #23 |
| 102 | #21 |
| 103 | #75 |
| 106 | #76 |
| 108 | #77 |
| 109 | #20 |
| 110 | #19 |
| 111 | #18 |
| 113 | #17 |
| 118 | #66 |
| 119 | #74 |
| 120 | #16 |
| 123 | #78 |
| 126 | #79 |
| 127 | #80 |
| 128 | #81 |
| 129 | #1 |
| 130 | #82 |
| 131 | #2 |
| 132 | #83 |
| 133 | #84 |
| 134 | #69 |
| 136 | #85 |
| 138 | #86 |
| 139 | #3 |
| 141 | #87 |
| 142 | #88 |
| 143 | #89 |
| 144 | #4 |
| 145 | #91 |
| 146 | #92 |
| 147 | #90 |
| 148 | #93 |
| 149 | #94 |
| 150 | #95 |
| 151 | #96 |
| 152 | #97 |
| 153 | #5 |
| 154 | #98 |
| 155 | #99 |
| 156 | #100 |
| 157 | #101 |
| 158 | #102 |
| 159 | #103 |
| 160 | #104 |
| 161 | #105 |
| 162 | #106 |
| 163 | #14 |
| 164 | #15 |
| 165 | #6 |
| 166 | #7 |
| 167 | #8 |
| 168 | #9 |
| 170 | #10 |
| 171 | #11 |
| 172 | #13 |
| 173 | #107 |
| 174 | #108 |
| 175 | #109 |
| 176 | #110 |
| 177 | #111 |
| 178 | #112 |
| 179 | #113 |
| 180 | #114 |
| 181 | #115 |
| 182 | #116 |
| 183 | #117 |
| 184 | #118 |
| 185 | #119 |
| 186 | #120 |
| 187 | #121 |
| 188 | #122 |
| 189 | #123 |
| 190 | #124 |
| 191 | #125 |
| 192 | #126 |
| 193 | #127 |
| 194 | #128 |
| 195 | #129 |
| 196 | #130 |
| 197 | #131 |
| 198 | #132 |
| 199 | #133 |
| 200 | #134 |
| 201 | #135 |
| 202 | #136 |
| 203 | #137 |

### Retired — one row per tombstone (21)

| § | issue |
|---|---|
| 13 | #138 |
| 26 | #139 |
| 31 | #140 |
| 33 | #141 |
| 43 | #142 |
| 45 | #143 |
| 51 | #144 |
| 59 | #145 |
| 68 | #146 |
| 80 | #147 |
| 85 | #148 |
| 87 | #149 |
| 88 | #150 |
| 98 | #151 |
| 100 | #152 |
| 104 | #153 |
| 105 | #154 |
| 117 | #155 |
| 121 | #156 |
| 122 | #157 |
| 169 | #158 |
