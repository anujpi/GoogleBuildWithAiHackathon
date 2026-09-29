# `GET /v1/reference/scope` (MASTER_SPEC §9.2)

This lists **exactly** the combinations the served artifact supports. It is derived from the
artifact's training series, so it always matches the model. Spring Boot syncs it into its reference
tables (§6.3), which feed the selectors and farm districts. The UI must not offer anything outside it.

It returns 503 `MODEL_NOT_LOADED` / `ARTIFACT_LOAD_ERROR` when no artifact is loaded.

```text
states[]        { stateId, label }
districts[]     { districtId, stateId, label }            label = S01 district name (title case)
crops[]         { cropId, label }
seasons[]       Season values present in the data
supplySeries[]  { districtId, cropId, season, firstYear, lastYear, yearsObserved }
                (years with positive reported production)
marketSeries[]  always [] until a market model is served (milestone M4)
datasets[]      { source, datasetVersion, dataThrough }
```

Golden file: `backend/src/test/resources/contracts/ml/reference-scope-response.json` (SYNTHETIC values, truncated).
