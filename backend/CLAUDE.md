# CLAUDE.md — Agricultural Intelligence Platform / Backend

## 1. Purpose

This file is the engineering and architecture contract for the backend of the Agricultural Intelligence Platform.

Claude Code is the implementation engineer. The product owner/architect determines product scope, development order, architecture changes, domain boundaries, infrastructure adoption, external integrations, and major technology changes.

Do not introduce major architectural changes without explicit instruction.

---

## 2. Product

We are building an Agricultural Decision Intelligence Platform.

The system combines:
- soil intelligence
- weather intelligence
- satellite/environmental intelligence
- agricultural history
- crop intelligence
- supply intelligence
- demand intelligence
- market intelligence
- production risk
- market risk
- scenario analysis
- crop-health intelligence
- regional supply-demand coordination
- explainable AI advisory

Core workflow:

Farm
→ Soil / Weather / Satellite / Agricultural History
→ Crop Intelligence
→ Expected Production
→ Supply Forecast
→ Demand Forecast
→ Supply-Demand Gap
→ Market Risk + Production Risk
→ Decision Engine
→ Explainability
→ AI Advisory
→ Scenario Analysis
→ Regional Supply/Demand Coordination

The MVP should demonstrate one complete loop well rather than attempting the full nationwide platform immediately.

Initial MVP:
- one Indian region/state
- 2–3 crops
- real data where available
- mock/synthetic data only where necessary
- synthetic/demo data must never be represented as measured real-world data

---

## 3. Backend responsibility

The backend owns:
- authentication and authorization
- user and role management
- farm management
- soil profiles
- environmental data orchestration
- crop domain
- market domain
- supply domain
- demand domain
- risk domain
- decision engine
- scenario orchestration
- disease diagnosis orchestration
- regional coordination
- AI advisory orchestration
- persistence
- validation
- API contracts
- data provenance

The backend does NOT own:
- React UI
- frontend presentation logic
- ML model implementation when that belongs in the Python service
- fabricated external data
- arbitrary LLM-generated business truth

---

## 4. Technology

Current backend stack:
- Java 21
- Spring Boot 4.1.x
- Maven
- Spring Web
- Spring Data JPA
- PostgreSQL
- PostGIS
- Validation
- Lombok
- Spring Boot Actuator

Planned later:
- Spring Security + JWT
- Redis
- Kafka/RabbitMQ
- object storage
- Spring AI / LLM integration
- Python/FastAPI ML service

Do not add future technologies before the corresponding feature requires them.

---

## 5. Architecture strategy

Start as a modular monolith.

Do NOT start with microservices.

Do NOT introduce service discovery, API gateway, Kafka, Kubernetes, or other distributed infrastructure simply because the project is large.

First establish clear module boundaries inside one Spring Boot application.

Only extract a service when:
- there is a concrete architectural reason
- the module has a stable boundary
- the separation provides a meaningful benefit
- the extraction is approved

---

## 6. Domain modules

Recommended structure:

com.argiintelligence.backend  (package name is intentional; keep it)
├── common
├── auth
├── user
├── farm
├── soil
├── weather
├── satellite
├── crop
├── market
├── supply
├── demand
├── risk
├── recommendation
├── scenario
├── disease
├── cooperation
├── advisory
├── dataingestion
└── configuration

Do not create dozens of empty classes. Create implementation when the module enters the current phase.

---

## 7. Layering

For mature modules, prefer:

module/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── mapper/
├── validation/
└── exception/

Controller → HTTP/API concerns only.

Service → orchestration and business logic.

Repository → persistence access only.

Entity → persistence/domain state.

DTO → API contract.

Mapper → DTO/entity conversion.

Do not expose JPA entities directly as API responses unless there is a deliberate reason.

Do not force every layer into trivial modules.

---

## 8. Package design

Prefer business/domain boundaries over technical dumping grounds.

Avoid giant packages containing every feature:

controller/
service/
repository/

Prefer:

farm/
  controller/
  service/
  repository/
  entity/
  dto/

crop/
  controller/
  service/
  repository/
  entity/
  dto/

This also makes later service extraction easier.

---

## 9. Initial data model

Expected domains include:

User
Farm
FarmLocation
SoilProfile
Crop
FarmCrop
WeatherObservation
WeatherForecast
SatelliteObservation
MarketPrice
MarketArrival
AgriculturalProduction
SupplyForecast
DemandForecast
SupplyDemandGap
RiskAssessment
CropRecommendation
Scenario
DiseaseDiagnosis
Alert
DataSource
PredictionRun
Region

Do not create every entity immediately. Implement them incrementally according to the current phase.

---

## 10. Geographic data

Use PostgreSQL + PostGIS for:
- farm locations
- regions
- districts
- markets/mandis
- satellite areas
- supply-demand regions

Prefer spatial types and queries where appropriate instead of arbitrary coordinate strings.

---

## 11. Data-provider architecture

External data must not be hardcoded directly into business logic.

Use provider abstractions such as:

WeatherProvider
SoilProvider
MarketProvider
AgricultureStatisticsProvider
SatelliteProvider
DemandSignalProvider

Example:

interface WeatherProvider

Possible implementations:
- MockWeatherProvider
- RealWeatherProvider

Business logic depends on the abstraction, not a specific external API.

This allows development with mock data, provider replacement, testing, and future government/partner integrations.

---

## 12. Data provenance and confidence

Where applicable, track:
- source
- source type
- observedAt
- retrievedAt
- forecast period
- data classification
- confidence
- model/version
- status

Distinguish:

OBSERVED
FORECAST
MODEL_PREDICTION
REGIONAL_ESTIMATE
SYNTHETIC

Never present synthetic or simulated data as observed real-world data.

If required data is unavailable:
- reduce confidence
- use a clearly labeled fallback
- return an appropriate unavailable state

Do not invent values.

The project requires predictions to be traceable to inputs and to distinguish observed, forecast, model-prediction and simulated data.

---

## 13. API design

Use REST APIs initially.

Base path:
/api/...

Initial domain paths:
- /api/auth
- /api/users
- /api/farms
- /api/soil
- /api/weather
- /api/crops
- /api/market
- /api/supply
- /api/demand
- /api/risk
- /api/recommendations
- /api/scenarios
- /api/disease
- /api/cooperation
- /api/advisories
- /api/alerts

Use consistent:
- HTTP methods
- status codes
- response structures
- error structures
- validation errors

Do not leak internal implementation details in API responses.

---

## 14. DTO conventions

Prefer request/response DTOs.

Examples:
- FarmCreateRequest
- FarmResponse
- FarmSummaryResponse

Do not use one giant DTO for every operation.

Separate create, update, response, and list/summary DTOs when responsibilities differ.

Use Bean Validation:
- @NotBlank
- @NotNull
- @Positive
- @DecimalMin
- @DecimalMax

Keep domain/business validation in the service/domain layer.

---

## 15. Error handling

Use a consistent API error structure, for example:

{
  "timestamp": "...",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Invalid farm data",
  "path": "/api/farms",
  "details": [...]
}

Use centralized exception handling.

Do not expose stack traces to clients.

Domain errors should have meaningful application-level codes.

---

## 16. Database rules

Use PostgreSQL as the primary database.

As the schema becomes important, use explicit database migrations.

The early development environment may use Hibernate schema generation, but production schema management should move toward explicit migrations such as Flyway or Liquibase.

Prefer:
- explicit indexes
- meaningful constraints
- foreign keys
- deliberate enum strategy
- consistent timestamps

Avoid:
- reserved SQL table names
- unnecessary bidirectional relationships
- unnecessary eager loading
- unbounded collections
- accidental N+1 queries

---

## 17. JPA rules

Prefer LAZY relationships by default unless there is a concrete reason otherwise.

Be careful with:
- @OneToMany
- @ManyToOne
- cascade
- orphanRemoval

Do not blindly use CascadeType.ALL.

Avoid exposing entities directly to Jackson.

Prevent circular JSON serialization through DTOs.

Be deliberate about transaction boundaries.

Use @Transactional at service/application boundaries when a business operation needs atomicity.

---

## 18. Security

Security will be introduced after the domain foundation is working.

Planned stack:
- Spring Security
- JWT
- role-based authorization

Expected roles may include:
- FARMER
- FPO
- AGRICULTURAL_OFFICER
- ADMIN

Never store plaintext passwords.

Never hardcode:
- JWT secrets
- API keys
- database passwords

Use environment variables/secrets.

Authorization must be enforced in the backend.

---

## 19. Decision Engine

The Decision Engine is a core backend capability.

It combines:
- agronomic suitability
- expected production
- supply forecast
- demand forecast
- market conditions
- weather risk
- disease risk
- production constraints
- relevant farm constraints

Return structured facts and trade-offs.

Do NOT collapse the entire system into a meaningless single score.

Example:

{
  "crop": "chilli",
  "suitability": 0.81,
  "expectedYield": ...,
  "supplyPressure": 0.21,
  "demandTrend": 0.42,
  "weatherRisk": 0.22,
  "diseaseRisk": 0.18,
  "confidence": 0.72,
  "factors": [...]
}

The frontend decides how to visualize this.

---

## 20. Supply intelligence

Supply forecasting will eventually use:
- historical production
- cultivated area
- expected yield
- crop suitability
- weather
- crop health
- other validated signals

Initial implementation may use a mock provider.

Never claim a mock forecast is a real ML prediction.

Keep the supply forecasting interface ready for a future Python service.

---

## 21. Demand intelligence

Demand may use:
- historical consumption proxies
- seasonality
- mandi/market signals
- price trends
- available regional signals

Fine-grained retail/POS data may not be available.

Do not fabricate retail demand.

If synthetic demand is used for demonstration, persist/return its synthetic status.

Demand is not automatically equivalent to retail sales.

---

## 22. Supply-demand intelligence

This is a first-class domain.

Conceptually:

Supply forecast
+
Demand forecast
→
Supply-Demand Gap

gap = expectedSupply - expectedDemand

Classify appropriately:
- surplus
- shortage
- balanced

Preserve:
- quantity
- unit
- forecast period
- region
- crop
- confidence
- source/model metadata

Do not silently change units.

---

## 23. Risk engine

Keep production and market risks distinguishable.

Potential dimensions:
- weather
- soil
- disease
- production
- market
- supply pressure
- data confidence

Return structured risk information.

Avoid unsupported numerical risk values.

---

## 24. AI / LLM architecture

The LLM is NOT the source of agricultural truth.

Correct flow:

Structured data
→ prediction/model outputs
→ Decision Engine
→ structured facts
→ LLM
→ human-readable explanation

LLM responsibilities:
- explanation
- summarization
- natural-language interaction
- describing trade-offs
- answering questions from supplied context

LLM must NOT:
- invent data
- invent sources
- invent forecast values
- override deterministic business rules
- hide uncertainty

Keep LLM integration behind an interface so the provider can be changed later.

---

## 25. Scenario engine

The scenario system may accept controlled changes such as:
- rainfall
- temperature
- demand
- supply
- production assumptions

Then recompute affected outputs.

Do not mutate historical observations when running a scenario.

Represent scenarios as separate analysis contexts.

Return:
- changed assumptions
- changed outputs
- affected risks
- affected decision state

---

## 26. Disease module

Disease diagnosis will eventually call a Python/ML service.

Backend responsibilities:
- validate upload
- store image reference
- invoke ML service
- store diagnosis result
- attach crop/weather/context
- expose structured diagnosis and confidence

Do not put image-classification implementation inside normal Spring business logic.

---

## 27. Python ML service boundary

Future architecture:

Spring Boot
→ HTTP/API
→ Python FastAPI ML service

Potential ML responsibilities:
- supply forecasting
- demand forecasting
- crop suitability modeling
- disease classification
- anomaly detection

Spring Boot owns:
- orchestration
- persistence
- authorization
- domain rules
- API contract

Python owns:
- model execution
- data science
- model-specific feature processing

Do not create the Python service until the corresponding backend contracts are defined.

---

## 28. Async processing

Initially use synchronous REST where sufficient.

Later, asynchronous infrastructure may be introduced for:
- large data ingestion
- satellite processing
- forecast generation
- notifications
- scheduled prediction jobs

Potential future tools:
- Kafka
- RabbitMQ
- scheduled workers

Do not introduce a message broker prematurely.

---

## 29. Caching

Redis is a future optimization.

Potential candidates:
- weather
- market data
- geographic lookups
- expensive intelligence calculations

Do not cache blindly. Establish correctness first.

---

## 30. Scheduled jobs

Future scheduled jobs may include:
- weather refresh
- market data ingestion
- forecast generation
- anomaly detection
- alert generation

Jobs must be:
- idempotent
- observable
- retryable where appropriate
- timestamped
- safe to rerun

---

## 31. Testing

Every mature module should eventually include:
- unit tests
- repository tests where needed
- integration tests
- controller/API tests

For external providers:
- mock them in unit tests
- use controlled fixtures for integration

Test:
- happy paths
- invalid input
- missing data
- provider failure
- low confidence
- boundary values
- authorization failures

Do not test only happy paths.

---

## 32. Observability

Use Spring Boot Actuator as the initial foundation.

Later:
- structured logging
- request correlation IDs
- metrics
- traces

Logs must not contain:
- passwords
- JWT secrets
- API keys
- unnecessary sensitive information

---

## 33. Configuration

Never hardcode environment-specific configuration.

Use:
- application.properties/yml
- environment variables
- Spring profiles

Separate:
- default
- local
- test
- production

Secrets must never be committed.

---

## 34. API/data contract discipline

When the frontend depends on a backend response:

1. Define the DTO.
2. Define field meaning.
3. Define units.
4. Define nullable/optional behavior.
5. Define data classification.
6. Define confidence where relevant.
7. Define error behavior.

Do not make the frontend guess what a field means.

Prefer explicit names such as:

expectedSupplyTonnes
expectedDemandTonnes
supplyDemandGapTonnes

when the unit is tonnes.

---

## 35. Performance

Do not optimize prematurely.

Do:
- paginate large lists
- avoid N+1 queries
- use indexes deliberately
- avoid loading whole datasets unnecessarily
- use projections for appropriate read-heavy endpoints

Optimize after understanding access patterns.

---

## 36. Current backend phase

CURRENT PHASE: Backend Foundation

Objectives:
1. Verify Spring Boot startup.
2. Establish package structure.
3. Add health endpoint.
4. Connect PostgreSQL.
5. Establish common exception handling.
6. Establish base API conventions.
7. Implement Farm domain.
8. Implement FarmLocation.
9. Implement SoilProfile.
10. Implement basic Farm CRUD.
11. Add tests for the above.

Do NOT yet implement:
- ML
- forecasting
- demand models
- disease model
- Kafka
- Redis
- LLM
- real satellite processing
- large external integrations
- microservices

---

## 37. Backend development phases

Phase 0 — Backend foundation
Phase 1 — User/auth foundation
Phase 2 — Farm + soil
Phase 3 — Weather/environmental providers
Phase 4 — Crop intelligence
Phase 5 — Supply forecasting contract
Phase 6 — Demand forecasting contract
Phase 7 — Supply-demand intelligence
Phase 8 — Risk engine
Phase 9 — Decision engine
Phase 10 — Explainability + advisory orchestration
Phase 11 — Scenario engine
Phase 12 — Disease orchestration
Phase 13 — Regional cooperation
Phase 14 — Python ML service integration
Phase 15 — Real data providers
Phase 16 — Production hardening

Do not jump ahead unless explicitly instructed.

---

## 38. Git discipline

Use focused commits.

Examples:
- chore: establish backend foundation
- feat: add farm domain
- feat: add soil profile
- feat: add weather provider abstraction
- feat: add crop intelligence
- feat: add supply forecast contract
- feat: add demand forecast contract
- feat: add decision engine

Avoid vague commits such as:
- final
- final2
- latest
- fixes
- stuff

---

## 39. First task

Before implementing domain functionality:

1. Inspect the current backend.
2. Inspect pom.xml.
3. Inspect application configuration.
4. Inspect the main Spring Boot class.
5. Inspect package names.
6. Check existing dependencies.
7. Report anything inconsistent with this CLAUDE.md.
8. Propose the minimum changes needed for Backend Phase 0.

Do NOT:
- rewrite the project
- add unnecessary dependencies
- generate all entities
- implement future phases

Wait for approval before making major architectural changes.

---

## 40. Definition of done

A backend feature is complete when:
- API contract is defined
- validation exists
- service logic is tested
- persistence is correct where applicable
- error cases are handled
- entities are not unnecessarily exposed
- relevant integration tests exist
- data provenance is preserved where relevant
- frontend contract is clear
- code fits existing module architecture
