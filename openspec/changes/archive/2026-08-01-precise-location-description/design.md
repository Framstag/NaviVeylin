## Context

See `proposal.md` — Why. Current `SearchPanel.kt` renders each `LocationEntry` as a `SearchResultItem` composable showing `label` + `adminRegionHierarchy`. All needed disambiguation fields (`objectTypeName`, `postalArea`, `region`) already exist in `LocationEntry` — no data plumbing required.

## Goals / Non-Goals

**Goals:**
- Detect duplicate-label groups in search results
- Render extra detail line on duplicate-group items using existing `LocationEntry` fields
- Keep single-result items unchanged
- Pure UI change — no ViewModel, native, or data layer modifications

**Non-Goals:**
- No backend/query changes to libosmscout search
- No new JNI methods
- No changes to `LocationDetailsSheet` or map rendering
- No changes to `MapCanvasViewModel` or `MapCanvasUiState`

## Decisions

**Decision 1: Grouping in composable layer, not ViewModel**
- *Choice*: Detect duplicate labels inside `SearchPanel.kt` / `SearchResultItem` composable
- *Rationale*: Grouping is pure presentation logic. ViewModel shouldn't know about display grouping. Keeps ViewModel unchanged.
- *Alternative considered*: Pre-process in ViewModel — rejected because it adds state without benefit; grouping is trivially derived from the result list.

**Decision 2: Disambiguation format — single detail line**
- *Choice*: Format as `"objectTypeName · postalArea · regionTail"` on a single `bodySmall` line below the label
- *Rationale*: Matches existing `adminRegionHierarchy` line style. Keeps item height consistent. Fields omitted if empty.
- *Alternative considered*: Multi-line detail — rejected; too much vertical space per item in the results list.

**Decision 3: Grouping scope — per search result batch**
- *Choice*: Grouping computed fresh each time the results list changes
- *Rationale*: Results are ephemeral — no need to persist grouping state. Simple `groupBy { it.label }` on the list.

## Risks / Trade-offs

- [False positives] Two different locations with same label but different context (e.g., "Berlin" as city vs "Berlin" as street) get grouped. → Mitigation: disambiguation fields make the difference visible; user can still pick correctly.
- [Performance] `groupBy` on every recomposition. → Mitigation: results list is small (max 20 items); `groupBy` is O(n) and negligible at this scale.
- [Empty fields] Some `LocationEntry` results may have null/empty `objectTypeName`, `postalArea`, or `region`. → Mitigation: only render non-empty fields; if all are empty, fall back to original display.

## Open Questions

None.
