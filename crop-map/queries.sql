-- Example queries for output/crop_production.sqlite

-- 1. Everything one district grows (all seasons combined)
SELECT crop, crop_group, seasons, area_ha, production, production_unit, yield_per_ha
FROM v_district_crop
WHERE state = 'Karnataka' AND district = 'BELAGAVI'
ORDER BY area_ha DESC;

-- 2. District profiles for a state
SELECT district, crops_grown, total_crop_area_ha, top5_crops_by_area
FROM v_district_summary
WHERE state = 'Karnataka'
ORDER BY total_crop_area_ha DESC;

-- 3. Top 10 rice-producing districts in India
SELECT state, district, production AS tonnes, area_ha, yield_per_ha
FROM v_district_crop
WHERE crop = 'Rice'
ORDER BY production DESC
LIMIT 10;

-- 4. What each state produces most of (by area), with units
SELECT state, crop, area_ha, production, production_unit, districts_growing
FROM (
  SELECT *, ROW_NUMBER() OVER (PARTITION BY state ORDER BY area_ha DESC) AS rk
  FROM v_state_crop
) WHERE rk <= 3
ORDER BY state, rk;

-- 5. Which districts grow a given crop, and in which season
SELECT state, district, season, area_ha, production, yield_per_ha
FROM v_production
WHERE crop = 'Turmeric'
ORDER BY production DESC;

-- 6. Crop-group mix per state (area share), excluding aggregate rows
SELECT state, crop_group,
       ROUND(100.0 * SUM(area_ha) / SUM(SUM(area_ha)) OVER (PARTITION BY state), 1) AS pct_of_state_crop_area
FROM v_district_crop
GROUP BY state, crop_group
ORDER BY state, pct_of_state_crop_area DESC;

-- 7. Highest-yield districts for wheat (at least 10,000 ha grown)
SELECT state, district, yield_per_ha, area_ha
FROM v_district_crop
WHERE crop = 'Wheat' AND area_ha >= 10000
ORDER BY yield_per_ha DESC
LIMIT 15;
