# ML service image. Build from the REPOSITORY ROOT so the committed crop_yield.csv can be copied:
#   docker build -f ml-service/Dockerfile -t agri-ml .
# Model artifacts are git-ignored: build them first (scripts/train_supply.py, scripts/train_disease.py)
# or the matching endpoints answer 503.
FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1 \
    AGRI_ML_ENVIRONMENT=production MLFLOW_DISABLE_AGENT_HINT=1
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 && rm -rf /var/lib/apt/lists/*
WORKDIR /app

COPY ml-service/requirements.lock ./
RUN pip install -r requirements.lock \
 && pip install --index-url https://download.pytorch.org/whl/cpu "torch==2.14.*" "torchvision==0.29.*"

COPY ml-service/pyproject.toml ml-service/README.md ./
COPY ml-service/src ./src
RUN pip install --no-deps -e .

COPY ml-service/reference_data ./reference_data
COPY ml-service/artifacts ./artifacts
COPY backend/src/main/resources/data/kaggle-crop-yield/crop_yield.csv ./data/raw/crop_yield.csv

EXPOSE 8000
CMD ["sh", "-c", "uvicorn agri_ml.api.app:app --host 0.0.0.0 --port ${PORT:-8000}"]
