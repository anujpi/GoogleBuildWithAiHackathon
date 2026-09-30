# Submission material

## Project description (2–3 sentences)

Agricultural Intelligence Platform is an evidence-first advisor for Indian farmers and FPOs. For any farm in any
state it combines live weather, crop suitability from 20+ years of state production history, an image-based leaf
disease classifier, an ML supply forecast and a FAOSTAT demand proxy into one decision, which Google Gemini then
explains in English or Hindi with read-aloud. Every value carries its provenance (observed, forecast, model
prediction or estimate), market demand never overrides land suitability, and every number in the AI's answer is
checked against the evidence it was given.

## Pitch deck outline (11 slides)

1. **Problem.** Crop decisions are made without local agronomic evidence or a view of supply against demand. The
   result is surplus gluts (e.g. potato) and shortages elsewhere. Advice is rarely in the farmer's language.
2. **Users.** Small farmers (choice of crop, crop health). FPOs and extension officers (regional balance, which
   crop to promote where). Later: state agriculture departments.
3. **Solution.** A single farm journey: conditions → suitability → crop health → supply, demand and gap → risk →
   decision → AI explanation. There is a two-way Land ↔ Market principle: demand is a matching signal, never an
   override.
4. **Architecture.** React → Spring Boot (auth, orchestration, decision rules, Gemini client) → FastAPI ML service,
   plus PostgreSQL/PostGIS. The boundary is "ML predicts, Spring decides, Gemini explains". Each section degrades on
   its own and never fakes a value.
5. **AI/ML stack.**
   - MobileNetV3 transfer learning (disease)
   - XGBoost against baselines (supply)
   - Back-tested statistical baseline (demand)
   - Transparent suitability index
   - Robust z-score anomalies
   - Rule-based risk
   - Gemini with JSON-schema output and a number-grounding check
6. **Data and trust.**
   - Kaggle crop_yield (30 states, 1997–2020)
   - PlantVillage
   - FAOSTAT Food Balance Sheets
   - Census 2011
   - Open-Meteo (live)

   Every value is badged Observed, Forecast, Model prediction or Estimate. We say what is missing: no mandi prices,
   because the data.gov.in API was unreachable.
7. **Disease intelligence.** Held-out test: macro-F1 0.942 (accuracy 0.951) against 0.660 for a colour-histogram
   baseline; the weakest class is Tomato early blight (F1 0.82). The expert-review flag fires below probability 0.70. State the lab-imagery limitation plainly.
8. **Crop and market intelligence.** Agra potato example: suitability index 0.83, but UP supply is about 83 % above
   the state demand proxy (2020). The backend returns "suitable with market risk" and suggests Wheat, Mustard and
   Gram.
9. **India scale.** Every call is keyed by state, season and crop; add a farm anywhere and it works. The data covers
   30 states, and demand apportionment covers 36 states and UTs. The path to district level is data.gov.in S01.
   Multilingual today in English and Hindi, and the design extends to more Indian languages.
10. **Demo and deployment.** Live demo. Cloud Run + Cloud SQL (PostGIS) + Gemini API is the target architecture;
    the Dockerfiles are prepared.
11. **Future scope.**
    - Live mandi prices and arrivals (Agmarknet/CEDA)
    - District-level data and Earth Engine NDVI
    - Field-photo disease data (PlantDoc)
    - More languages and voice input
    - Market → Land matching across FPOs
    - Calibrated risk models

## Demo script (≈4 minutes)

**0:00 – Hook (20 s).**
"Last season, potato prices crashed in UP because everyone planted potato. Our platform tells a farmer what their
land supports *and* what the market needs, and says exactly how sure it is."

**0:20 – Login and farm (20 s).**
Sign in. The page opens on Farm Intelligence. Point at the farm selector: "This is a sample farm in Agra, Uttar
Pradesh, Rabi season, borewell-irrigated." Point at the region line and the "any Indian state" note.

**0:40 – Conditions and decision (40 s).**
- "Live weather from Open-Meteo, labelled Forecast."
- "The decision panel is computed by our backend rules, not by the AI: *Potato is suitable, but regional supply is
  well above the demand proxy*."
- "Demand never overrides suitability, so instead of telling the farmer to stop, we show suitable alternatives:
  Wheat, Mustard, Gram."

**1:20 – Suitability (30 s).**
Show the ranked crops and the evidence line ("median yield ranks at the 87th percentile of 24 states…"). "This is an
evidence index, not a probability, and the page says so." Click Wheat to switch the focus, then switch back.

**1:50 – Crop doctor (40 s).**
Upload a potato leaf photo (early or late blight). Show the class, probability and top-3 classes. "The model is
MobileNetV3 transfer learning (frozen ImageNet backbone plus a linear head) on PlantVillage, with held-out test metrics in the model card. Below 70 % probability we
ask for expert review. We also state that it was trained on lab photos."

**2:30 – Supply, demand and gap (30 s).**
"The XGBoost supply forecast has an 80 % interval. The demand proxy comes from FAOSTAT, apportioned by population.
UP shows a surplus of about 83 %, so market risk is high. Each badge says whether a number is Model prediction,
Estimate or Forecast."

**3:00 – Risk and anomalies (15 s).**
"Every risk factor shows the rule that fired. The anomaly checks use robust z-scores on the history."

**3:15 – AI explanation (40 s).**
Click **Explain with AI**. "Gemini receives only this structured evidence, which you can inspect here, and must
explain it without adding facts. Our backend checks every number in the answer against the evidence." Switch the
language to हिन्दी, click again, then press **Read aloud**.

**3:55 – Close (15 s).**
"It's one architecture that works for any state: ML predicts, the backend decides, Gemini explains. Next steps are
live mandi data, district-level data and satellite NDVI."

**Backup if something fails live.**
- Every section shows its own UNAVAILABLE reason.
- Without a Gemini key, the advisory is visibly labelled "Template fallback (not AI)".
- Say so rather than hide it.
