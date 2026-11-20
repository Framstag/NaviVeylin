## 1. The skill carries the rule

- [x] 1.1 `SKILL.md` §7 gains **The score**: the rubric, the three delta rules, `do nothing` on the same scale, and
      "the recommendation holds the highest number"
- [x] 1.2 The two-views sentence is restated as "never one averaged score — read off these columns, and never computed
      from the two views"
- [x] 1.3 §10's presentation order ends with the score table, and the `ask_user` context carries the recommendation's
      score beside its nearest rival
- [x] 1.4 The "never does" list gains: a number with no clause, a rejected option outscoiring the recommended one, and
      a four-point delta reported as a live choice
- [x] 1.5 The worked example (#788) carries its own table — A 9, B 5, C 2, D 1, do nothing 0 — and reads its deltas

## 2. The skeleton puts it last

- [x] 2.1 `references/answer-shape.md` gains `## The score (0–10, the last block)` with the columns
      `option | name | points | what set it`
- [x] 2.2 Its checklist grows from seven items to eight, the eighth being that the numbers agree with the sheet and
      every row carries its clause

## 3. The rule is pinned

- [x] 3.1 Case `decisions#everyOptionCarriesAScore` reads the six rubric anchors, the three delta rules, `do nothing`
      being scored, the table's header, and that the table sits after the recommendation
- [x] 3.2 Case `decision-queue#everyOptionCarriesAnExampleAndTwoViews` reads the restated ban, so the sentence and the
      table cannot drift apart

## 4. Verification

- [x] 4.1 `bash tools/issues/tests/issue-tooling-selftest.sh` — 184 of 184 cases pass, offline, no token
- [x] 4.2 Falsified twice, restored green: dropping the `| 2–3 |` anchor fails 3.1; reverting the ban's wording fails
      3.2
- [x] 4.3 `tools/check-portability.sh` — OK, 61 engines and 8 templates name no project value
