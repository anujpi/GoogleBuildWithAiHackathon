# Kaggle crop_yield.csv (production history source)

This is an unmodified copy of the file the ML supply model was trained on. The Flyway migration
`db.migration.V5__load_kaggle_crop_yield` loads it into the `production_history` table.

| Field | Value |
|---|---|
| Dataset | Agricultural Crop Yield in Indian States Dataset |
| Publisher | Kaggle user `akshatgupta7` (a third-party republication of official statistics) |
| URL | https://www.kaggle.com/datasets/akshatgupta7/crop-yield-in-indian-states-dataset |
| License | CC-BY-SA-4.0. Attribution is required, and adaptations must be shared alike. |
| SHA-256 | `ab9bc356b1f8107d490376cec24450a0e2906322ad25788ab22fb32537ab1f8f` |
| Dataset version | `crop_yield-sha256-ab9bc356b1f8`. This matches the ML model's `provenance.datasetVersion`. |

## Limitations

- **State level only.** There is no district or farm data.
- **Historical.** Crop years run 1997–2019. Only one state (Uttarakhand) reports 2020.
- **Units are not independently verified.** Area in hectares and production in metric tons are
  what the publisher states. They haven't been checked against the government source.
- **Coconut and Cotton(lint) are not loaded.** Their Production values are not in tonnes, so they
  can't go into a `production_tonnes` column.

Full audit: `ml-service/docs/datasets/crop_yield.md`. Never edit this file. A new version is a
new file and a new migration.
