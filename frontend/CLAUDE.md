# CLAUDE.md — Agricultural Intelligence Platform / Frontend

## 1. Role of this file

This file is the engineering and product contract for the **frontend** of the Agricultural Intelligence Platform.

You are working as the implementation engineer.

The product owner/architect decides:
- product scope
- feature order
- architecture decisions
- data contracts
- UX priorities
- when new dependencies or infrastructure are introduced

Do not invent major product capabilities or change the agreed architecture without asking first.

---

# 2. Product

We are building an **Agricultural Decision Intelligence Platform**.

This is not a generic crop recommendation app and not a generic AI chatbot.

The product combines:
- soil intelligence
- weather intelligence
- satellite/environmental intelligence
- crop intelligence
- agricultural production history
- supply intelligence
- demand intelligence
- market intelligence
- production/market risk
- scenario analysis
- regional supply-demand coordination
- explainable AI advisory

The central product workflow is:

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

The hackathon MVP should demonstrate one complete loop well rather than attempting a nationwide production system immediately.

Initial MVP assumptions:
- one selected Indian region/state
- 2–3 crops initially
- real data where available
- mock/synthetic data only where necessary
- all mock/synthetic data must be explicitly labeled

---

# 3. Frontend goals

The frontend must make the system feel like a serious **Agricultural Intelligence Command Center**.

The UI should communicate:

- intelligence
- evidence
- geography
- uncertainty
- risk
- market movement
- agricultural context
- actionable decisions

It should feel closer to a modern geospatial/scientific operations platform than a generic farming SaaS dashboard.

---

# 4. Technology

Current frontend stack:

- React
- TypeScript
- Vite
- Tailwind CSS
- shadcn/ui
- TanStack Query
- React Hook Form
- Zod
- Recharts
- MapLibre-compatible map implementation

Use the existing project setup unless a dependency is genuinely required.

Do not add a new UI library simply because it contains a component that could be built with existing tools.

Keep the visual system unified.

---

# 5. Repository boundaries

This file governs the frontend only.

Current repository:

AgriculturalIntelligence/
├── frontend/
├── backend/
└── ...

Frontend owns:
- pages
- routing
- components
- visual design
- client state
- server-state handling
- form validation
- data visualization
- map interactions
- API client layer
- loading/error/empty states

Frontend does NOT own:
- business truth
- database logic
- ML model implementation
- forecasting implementation
- agricultural calculations
- authorization decisions
- fake numerical recommendations

The backend is the source of business/domain truth.

---

# 6. Architecture

Prefer feature-oriented organization.

Recommended structure:

src/
├── app/
│   ├── router/
│   ├── providers/
│   └── config/
│
├── components/
│   ├── ui/
│   ├── layout/
│   ├── data-display/
│   ├── charts/
│   ├── maps/
│   └── feedback/
│
├── features/
│   ├── farms/
│   ├── crops/
│   ├── market/
│   ├── supply-demand/
│   ├── risk/
│   ├── scenarios/
│   ├── disease/
│   ├── cooperation/
│   ├── advisory/
│   └── alerts/
│
├── lib/
│   ├── api/
│   ├── validation/
│   ├── formatting/
│   └── utilities/
│
├── hooks/
├── types/
├── assets/
├── styles/
└── main.tsx

Do not create empty folders for every future feature just for the sake of structure.

Create a feature when implementation begins.

---

# 7. Design language

## Overall visual character

The product should feel:

- premium
- scientific
- calm
- precise
- spatial
- data-rich
- trustworthy
- modern

Avoid making it feel:
- cartoonish
- overly rustic
- like an agriculture marketplace
- like a generic admin dashboard
- like a generic AI landing page

## Avoid

Do not use:
- excessive green everywhere
- stock farming photos as primary UI
- generic leaf/plant icons as decoration
- purple AI gradients
- excessive glassmorphism
- excessive rounded cards
- repetitive three-card rows
- giant empty hero areas
- meaningless decorative graphics
- unnecessary animation

The interface should prioritize information hierarchy over decoration.

---

# 8. Color system

Use design tokens / CSS variables rather than scattering raw colors.

Base palette direction:
- deep graphite / near-black
- warm off-white
- restrained agricultural green
- muted earth tones
- amber for warnings
- red for critical risk
- teal/green for positive states
- neutral grays for secondary information

Important:

Do not use color as the only way to communicate meaning.

Risk states should include:
- icon
- text
- badge/state
- color

Example:

Medium Risk
[warning icon] Medium

not just an orange dot.

---

# 9. Typography

Typography should have a clear hierarchy.

Use:
- strong page titles
- readable section headings
- compact labels
- highly legible numerical values
- restrained secondary text

Do not use huge typography inside data dashboards unless it communicates a genuinely important headline metric.

Prefer:
- sentence case
- concise labels
- readable numbers
- consistent units

---

# 10. Layout philosophy

Avoid "card soup".

Do not build every section as:

[card] [card] [card]

Prefer:
- large primary workspace
- supporting insight panels
- tables
- charts
- map surfaces
- split views
- drawers/sheets
- contextual side panels

The dashboard should have a clear visual hierarchy.

For the primary intelligence dashboard, the map/data workspace should be visually important.

---

# 11. Core navigation

Initial navigation:

- Dashboard
- Farms
- Crop Intelligence
- Supply & Demand
- Market Intelligence
- Scenario Simulator
- Crop Doctor
- Regional Coordination
- Alerts
- Data Operations

Navigation should be implemented so additional modules can be added without redesigning the shell.

---

# 12. Core screens

## 12.1 Dashboard

Primary purpose:
Provide a high-level intelligence view for a selected farm/region.

Include:
- region selector
- farm selector
- season selector
- map workspace
- supply outlook
- demand outlook
- market pressure
- weather risk
- disease risk
- crop intelligence preview
- alerts
- data freshness
- confidence indicators

The dashboard should answer:
"What is happening right now?"

---

## 12.2 Farm onboarding

Flow:

1. Location
2. Farm area
3. Soil information
4. Current crop
5. Previous crop
6. Irrigation
7. Review
8. Save

Soil input modes:
- upload Soil Health Card
- manual entry
- regional estimate

Clearly label regional estimates as estimates.

---

## 12.3 Farm profile

Show:
- farm location
- area
- soil profile
- current crop
- previous crops
- irrigation
- season
- data sources
- data freshness

---

## 12.4 Crop Intelligence

The user can compare candidate crops.

Important:
Do NOT present one simplistic "winner".

Display trade-offs:
- agronomic suitability
- expected yield
- water constraints
- weather risk
- disease risk
- supply pressure
- demand trend
- projected market gap
- confidence

Use:
- comparison tables
- charts
- contextual panels

---

## 12.5 Supply & Demand

This is a first-class product area.

Show:
- historical supply
- historical demand
- forecast supply
- forecast demand
- projected gap
- surplus/shortage state
- confidence
- regional comparison

Use:
- time-series charts
- regional map
- filters
- legends
- clear units

---

## 12.6 Market Intelligence

Show:
- price trends
- mandi arrivals
- seasonality
- supply pressure
- anomalies
- relevant market signals

Always show date/time context.

Do not imply that wholesale mandi price is identical to retail consumer price.

---

## 12.7 Scenario Simulator

Allow controlled changes to scenario inputs such as:
- rainfall
- temperature
- demand
- supply
- production assumptions

Show impacts on:
- expected production
- supply
- demand
- gap
- market pressure
- risk
- decision state

The UI should clearly distinguish:
Baseline
vs
Scenario

---

## 12.8 Crop Doctor

Flow:

Upload crop/leaf image
→ diagnosis result
→ confidence
→ crop context
→ weather context
→ environmental context
→ advisory

Do not display a disease label without context.

---

## 12.9 Regional Coordination

Map-first interface.

Show:
- surplus regions
- shortage regions
- balanced regions
- candidate matches
- estimated quantity
- distance/location context
- confidence
- evidence

This is an operations-style interface, not a consumer card grid.

---

## 12.10 Alerts

Examples:
- projected oversupply
- projected shortage
- weather risk
- disease risk
- abnormal arrivals
- abnormal pricing
- data-source issues

Alerts should be actionable and contextual.

---

## 12.11 Data Operations

Admin-style interface showing:
- source health
- last update
- ingestion status
- model version
- prediction run state
- data quality
- failed jobs

---

# 13. Explainability and data confidence

Every major prediction/recommendation should have a way to answer:

"Why?"

Display where appropriate:
- contributing factors
- data source
- timestamp
- observed/forecast/model/synthetic state
- confidence
- limitations

Never hide uncertainty.

Never turn missing data into a fake precise number.

Examples of honest labels:

Observed
Forecast
Model prediction
Regional estimate
Prototype / synthetic

---

# 14. Mock data rules

Mock data is allowed during frontend development.

However:

- never present mock data as real
- label demo/synthetic values
- keep mock data behind a clean data-access layer
- design API types as though they will later come from the backend
- do not bake mock values directly into reusable UI components

Preferred pattern:

features/
  supply-demand/
    api/
    hooks/
    types/
    components/
    pages/

The UI should be replaceable from mock provider → real backend without rewriting presentation code.

---

# 15. API rules

Use a centralized API client.

Do not call `fetch()` randomly inside components.

Prefer:

components
→ feature hooks
→ API client
→ backend

Use TanStack Query for server state.

Separate:
- server state
- local UI state
- form state

Do not store fetched server data in arbitrary global state unless there is a concrete reason.

---

# 16. Forms

Use:
- React Hook Form
- Zod schemas

Forms must provide:
- validation
- loading state
- disabled submit during request
- field-level errors
- accessible labels
- success feedback
- error feedback

Do not silently fail.

---

# 17. Data visualization rules

Charts must answer a question.

Before adding a chart, identify:
"What decision or insight does this chart support?"

Every chart should have:
- title
- units
- time context when applicable
- legend when needed
- accessible text/label
- empty state
- loading state
- error state

Avoid decorative charts.

Prefer:
- trend lines
- supply vs demand comparisons
- gap visualization
- historical vs forecast sections
- risk breakdown
- scenario before/after comparisons

---

# 18. Maps

Maps are a core part of the product.

Use maps for:
- farm location
- crop suitability
- supply
- demand
- surplus
- shortage
- regional coordination
- environmental context

Always provide:
- legend
- layer controls
- selected-region state
- loading state
- empty state
- accessible fallback where possible

Do not make the map the only way information can be understood.

---

# 19. Responsive behavior

Desktop is the primary target because the product is data-heavy.

Support:
- desktop
- tablet
- mobile

On smaller screens:
- side panels become drawers/sheets
- dense tables become scrollable or condensed
- map controls simplify
- filters move into sheets
- critical insights remain visible

Do not simply shrink desktop layouts.

---

# 20. Accessibility

Target WCAG 2.1 AA.

Requirements:
- keyboard navigation
- visible focus states
- semantic HTML
- labels for controls
- accessible dialogs
- accessible form errors
- chart summaries where appropriate
- map interaction alternatives where possible
- color-independent status communication
- reduced-motion support

---

# 21. Loading / error / empty states

Every asynchronous section needs intentional states.

Examples:

Loading:
"Loading market intelligence..."

Error:
"Market data is temporarily unavailable."
[Retry]

Empty:
"No supply forecast is available for this region yet."

Never leave a blank white area.

Do not use endless skeleton loaders where a useful message is possible.

---

# 22. Component rules

Prefer reusable components when:
- the same visual pattern appears more than once
- the interaction is complex
- the component expresses a product concept

Examples:

AppShell
Sidebar
TopBar
PageHeader
MetricPanel
InsightPanel
RiskIndicator
StatusBadge
ConfidenceBadge
DataFreshnessIndicator
DataSourceBadge
ChartContainer
MapPanel
AlertPanel
ScenarioControl
EvidencePanel
EmptyState
LoadingState
ErrorState

Do not abstract everything prematurely.

---

# 23. State and status semantics

Use explicit product states.

Examples:

Risk:
- Low
- Moderate
- High
- Critical

Data:
- Observed
- Forecast
- Model
- Estimate
- Synthetic

Availability:
- Healthy
- Delayed
- Degraded
- Unavailable

Keep naming consistent across the entire app.

---

# 24. Engineering quality

Before considering a feature complete:

1. TypeScript must compile.
2. ESLint must pass.
3. No avoidable console errors.
4. No obvious accessibility violations.
5. Loading/error/empty states exist.
6. Desktop layout works.
7. Mobile behavior is intentional.
8. Components remain reusable.
9. Mock data is clearly identifiable.
10. API boundaries are clean.

Do not ignore TypeScript errors merely to finish a screen.

Do not disable ESLint rules just to make code pass.

---

# 25. Dependency discipline

Before adding a new package:
- determine whether the current stack already solves the problem
- prefer existing project dependencies
- avoid multiple libraries with overlapping purposes
- add only when there is a clear product/engineering benefit

Do not introduce:
- a second component library
- a second charting library
- a second state-management system
without an explicit reason.

---

# 26. Visual implementation process

For every major screen:

1. Establish information hierarchy.
2. Reuse the existing design system.
3. Search available component resources when helpful.
4. Implement the screen.
5. Run the frontend.
6. Inspect it in a real browser.
7. Fix layout/spacing/interaction issues.
8. Check responsive behavior.
9. Check accessibility.
10. Only then consider the screen complete.

Do not assume generated code looks correct.

---

# 27. Current phase

CURRENT PHASE: **Phase 0 — Frontend Design System + Application Shell**

Build only:
- design tokens
- typography
- colors
- layout primitives
- app shell
- sidebar
- top bar
- navigation
- routing
- reusable status/metric/data components
- dashboard visual shell
- map placeholder/workspace
- mock visualization examples

Do NOT yet implement:
- authentication
- real APIs
- PostgreSQL
- ML
- forecasting
- disease model
- real agricultural calculations
- real external data integrations
- business decisions

Use realistic but clearly marked mock data for visual development.

---

# 28. Phase 0 completion criteria

Phase 0 is complete only when:

- the app has a coherent visual identity
- all main routes exist
- navigation works
- desktop shell is polished
- responsive behavior is intentional
- reusable design primitives exist
- dashboard shell communicates the product's purpose
- charts/maps have meaningful visual placeholders
- no major TypeScript/ESLint issues remain
- browser inspection has been performed
- the UI does not look like a generic dashboard template

---

# 29. Future phases

Phase 1 — Farm onboarding
Phase 2 — Farm profile + soil
Phase 3 — Weather/environmental intelligence
Phase 4 — Crop intelligence
Phase 5 — Supply forecasting
Phase 6 — Demand forecasting
Phase 7 — Supply-demand intelligence
Phase 8 — Risk engine
Phase 9 — Decision engine
Phase 10 — Explainability + AI advisory
Phase 11 — Scenario simulator
Phase 12 — Crop Doctor
Phase 13 — Regional coordination
Phase 14 — Real data + ML integrations
Phase 15 — Production hardening

Do not jump ahead unless explicitly instructed.

---

# 30. Git discipline

Make focused commits.

Examples:

feat: establish frontend design system
feat: add application shell
feat: add farm onboarding
feat: add crop intelligence workspace
feat: add supply demand dashboard

Avoid commits such as:
- final
- final2
- latest
- changes
- stuff

---

# 31. First task

Before implementing Phase 0:

1. Inspect the existing frontend.
2. Inspect `package.json`.
3. Inspect the current Vite/TypeScript/Tailwind setup.
4. Do not replace working configuration unnecessarily.
5. Report what is already configured.
6. Identify only the minimum setup changes required for Phase 0.
7. Then implement Phase 0 in small, verifiable steps.

Do not build the full application yet.
