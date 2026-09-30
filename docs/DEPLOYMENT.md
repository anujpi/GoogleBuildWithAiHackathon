# Deployment

**Status (2026-09-30): not deployed.** No cloud credentials were available while this was prepared. The Dockerfiles
and compose file below were written but **not test-built** (Docker isn't installed on the dev machine). The stack
was verified running natively (see README → Local setup).

## Architecture

| Component | Recommended target | Why |
|---|---|---|
| Frontend (static React) | Cloud Run (`frontend/Dockerfile`, nginx) or Firebase Hosting (`npm run build` → `dist/`) | Static files; the API URL is baked in at build time |
| Backend (Spring Boot) | Cloud Run (`backend/Dockerfile`) | Stateless, JWT auth |
| ML service (FastAPI) | Cloud Run, **internal only** (`ml-service/Dockerfile`), 2 GiB RAM | Must only be reachable by the backend |
| Database | Cloud SQL for PostgreSQL 17 with the `postgis` extension | Flyway migration V1 runs `CREATE EXTENSION postgis`; V5 loads about 19k rows of history |
| LLM | Gemini API (`GEMINI_API_KEY`), called only by the backend | |
| Weather | Open-Meteo public API, called by the backend | No key needed; check its terms before commercial use |

Simplest option with no cloud account: one VM with Docker, running `docker compose up --build` (see `docker-compose.yml`).

## Before building

1. **Model artifacts** are git-ignored. On the build machine:
   - `ml-service/artifacts/supply/supply-xgb-v1/`: `python scripts/train_supply.py`
   - `ml-service/artifacts/disease/disease-mnv3-probe-v1/`: `python scripts/prepare_plantvillage.py`, then
     `python scripts/train_disease_probe.py` (CPU, about 30 minutes). This needs the PlantVillage download (see the plantvillage dataset
     doc) and about 20–30 minutes on Apple silicon.

   Without them, `/v1/predict/supply` and `/v1/predict/disease` return 503, and the backend shows those sections as
   UNAVAILABLE.
2. Generate `JWT_SECRET` (`openssl rand -base64 48`). Get `GEMINI_API_KEY` from Google AI Studio.

## Google Cloud Run (manual steps)

```bash
PROJECT=<gcp-project>; REGION=asia-south1
gcloud config set project $PROJECT
gcloud services enable run.googleapis.com sqladmin.googleapis.com artifactregistry.googleapis.com
gcloud artifacts repositories create agri --repository-format=docker --location=$REGION
REPO=$REGION-docker.pkg.dev/$PROJECT/agri

# Database
gcloud sql instances create agri-db --database-version=POSTGRES_17 --tier=db-g1-small --region=$REGION
gcloud sql databases create agri --instance=agri-db
gcloud sql users create agri --instance=agri-db --password=<db-password>
# PostGIS: the V1 migration runs CREATE EXTENSION postgis; on Cloud SQL the migrating user needs
# cloudsqlsuperuser (the default for users created as above).

# ML service (build from repo root)
gcloud builds submit --tag $REPO/ml -f ml-service/Dockerfile .   # or: docker build + docker push
gcloud run deploy agri-ml --image $REPO/ml --region $REGION --memory 2Gi --ingress internal --no-allow-unauthenticated
ML_URL=$(gcloud run services describe agri-ml --region $REGION --format 'value(status.url)')

# Backend
gcloud builds submit backend --tag $REPO/backend
gcloud run deploy agri-backend --image $REPO/backend --region $REGION --allow-unauthenticated \
  --add-cloudsql-instances $PROJECT:$REGION:agri-db \
  --set-env-vars "DB_URL=jdbc:postgresql:///agri?cloudSqlInstance=$PROJECT:$REGION:agri-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory,DB_USERNAME=agri,ML_SERVICE_URL=$ML_URL,ML_READ_TIMEOUT=PT30S" \
  --set-secrets "DB_PASSWORD=db-password:latest,JWT_SECRET=jwt-secret:latest,GEMINI_API_KEY=gemini-key:latest"
BACKEND_URL=$(gcloud run services describe agri-backend --region $REGION --format 'value(status.url)')

# Frontend
gcloud builds submit frontend --tag $REPO/frontend   # pass --build-arg VITE_API_BASE_URL=$BACKEND_URL/api via a cloudbuild.yaml or docker build
gcloud run deploy agri-frontend --image $REPO/frontend --region $REGION --allow-unauthenticated
gcloud run services update agri-backend --region $REGION --update-env-vars CORS_ALLOWED_ORIGINS=<frontend-url>
```

Open issues for a real deployment:

- The Cloud SQL socket factory (`com.google.cloud.sql:postgres-socket-factory`) isn't in `pom.xml`. Either add
  it, or use the instance's private IP in `DB_URL`.
- An internal-only ML service needs the backend on the same VPC (a Serverless VPC connector). The alternative is
  service-to-service IAM auth, which `MlClient` doesn't implement yet.
