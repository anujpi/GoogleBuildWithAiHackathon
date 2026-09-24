from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# ml-service/ (src/agri_ml/config/settings.py -> parents[3])
SERVICE_ROOT = Path(__file__).resolve().parents[3]


class Settings(BaseSettings):
    """Service configuration, read from environment variables prefixed with AGRI_ML_."""

    model_config = SettingsConfigDict(env_prefix="AGRI_ML_", env_file=".env", extra="ignore")

    environment: str = "local"
    # Local SQLite tracking store by default; point at a tracking server via env when one exists.
    mlflow_tracking_uri: str = f"sqlite:///{SERVICE_ROOT / 'mlflow.db'}"
    mlflow_artifact_root: str = (SERVICE_ROOT / "mlartifacts").as_uri()
    artifacts_dir: Path = SERVICE_ROOT / "artifacts"
    data_dir: Path = SERVICE_ROOT / "data"


@lru_cache
def get_settings() -> Settings:
    return Settings()
