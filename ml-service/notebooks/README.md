# Notebooks

- `exploratory/`: EDA and data audits.
- `experiments/`: modelling experiments. Log every experiment to MLflow.

Rules:

- A notebook is never the source of truth. Put reusable logic in `src/agri_ml/`, and record
  results in MLflow and `docs/`.
- Load data from the configured `data/` directory. Never commit data or large outputs.
- Clear outputs before committing if they contain large tables or images.
- Name notebooks `YYYY-MM-DD-<topic>.ipynb`.
