# Load xgboost before any test module imports torch. Two OpenMP runtimes (torch's and xgboost's)
# in one process crash xgboost on macOS; see the note in agri_ml/inference/disease.py.
import xgboost  # noqa: F401
