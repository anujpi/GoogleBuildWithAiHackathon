# ML ⇄ Spring Boot contracts

Write a contract here, and get the backend team to agree to it, before you implement any
prediction endpoint. You can't move to implementation until the contract is agreed.

There are no contracts yet. The only live endpoint is `GET /health`, and you can see its
response in the service README.

## Conventions

- JSON field names are camelCase.
- Every prediction response carries `dataClassification`, `modelVersion`, `featureVersion`,
  `datasetVersion` and `generatedAt` (ISO-8601 UTC).
- Units are explicit enum strings such as `TONNES`, `HECTARES` or `TONNES_PER_HECTARE`. Never
  mix units silently.
- Confidence and uncertainty are `null` when the model can't produce a meaningful value. Never
  fill them with a placeholder.
- Responses contain no natural-language advice.

## Template (`<capability>.md`)

```text
Purpose
Endpoint
Request            (fields, types, units, required/optional)
Response           (fields, types, units, required/optional)
Null behavior
Confidence/uncertainty
Data classification
Model / feature / dataset version
Error cases        (HTTP status + error code)
Example request / response
```
