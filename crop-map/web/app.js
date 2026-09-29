/* India crop map: pan and zoom to see what the states and districts in view grow.
 * Data: data/crops.json (scripts/build_web_data.py) and data/india.topo.json (scripts/build_geo.py). */
'use strict';

const DISTRICT_ZOOM = 6;     // at or above this zoom the map switches from states to districts
const LABEL_ZOOM = 8;        // crop names next to district markers
const DEBOUNCE_MS = 300;     // wait after the last pan/zoom before redrawing
const LIST_LIMIT = 150;      // longest result list we render

// Categorical slots in fixed order (validated palette) + neutral for the "other" tail.
// Every marker also carries the glyph, so colour never has to identify the group alone.
const GROUP_STYLE = {
  'Cereals':             { color: '#2a78d6', glyph: '🌾', short: 'Cereals' },
  'Pulses':              { color: '#eb6834', glyph: '🫘', short: 'Pulses' },
  'Oilseeds':            { color: '#1baf7a', glyph: '🌻', short: 'Oilseeds' },
  'Fibres':              { color: '#eda100', glyph: '🧶', short: 'Fibres' },
  'Sugar':               { color: '#e87ba4', glyph: '🍬', short: 'Sugar' },
  'Spices & condiments': { color: '#008300', glyph: '🌶️', short: 'Spices' },
  'Vegetables & tubers': { color: '#4a3aa7', glyph: '🥔', short: 'Vegetables' },
  'Fruits & plantation': { color: '#e34948', glyph: '🥥', short: 'Fruits & plantation' },
  'Other commercial':    { color: '#898781', glyph: '🌿', short: 'Other commercial' },
};
const RAMP = ['#cde2fb', '#86b6ef', '#3987e5', '#256abf', '#104281']; // one hue, light -> dark
const NODATA = '#c9c8c2';

const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const debounce = (fn, ms) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };
const isMobile = () => window.matchMedia('(max-width: 760px)').matches;

/* ---------------- formatting ---------------- */
const nf0 = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });
const nf1 = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 1 });
const nf2 = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });
const nfc = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 });
const fmt = (v) => v == null ? '—' : (Math.abs(v) >= 100 ? nf0 : Math.abs(v) >= 10 ? nf1 : nf2).format(v);
const fmtC = (v) => v == null ? '—' : Math.abs(v) < 1000 ? fmt(v) : nfc.format(v);
const unitShort = (u) => u === 'tonnes' ? 't' : u.startsWith('bales') ? 'bales' : u;
const unitLong = (u) => u === 'tonnes' ? 'tonnes' : u.startsWith('bales') ? `bales of ${u.match(/\d+ kg/)[0]}` : u;

function titleCase(s) {
  if (s === 'DELHI_TOTAL') return 'Delhi';
  return s.toLowerCase().replace(/(^|[\s(+\-])([a-z])/g, (m, p, c) => p + c.toUpperCase())
    .replace(/\b([A-Za-z]{1,4})\b/g, (w) => /^[^aeiouAEIOU]+$/.test(w) && w.length > 1 ? w.toUpperCase() : w);
}

/* ---------------- state ---------------- */
const S = {
  mode: null,            // 'state' | 'district'
  crop: null,            // crop index or null
  metric: 'area',
  hover: null,           // key being hovered: unit key or 'st:<state>'
  selected: null,        // unit key whose detail is open
  tableSort: { col: 'area', dir: -1 },
};

let D, CROPS, UNITS, STATES, POLYS, map, stateFill, stateLines, districtGroup, markerGroup, legend;
const drawn = new Set();       // polygons currently on the map
const markers = new Map();     // unit key -> L.Marker
const breaksCache = new Map();

/* ---------------- geometry helpers ---------------- */
function eachRing(geom, fn) {
  const polys = geom.type === 'Polygon' ? [geom.coordinates] : geom.type === 'MultiPolygon' ? geom.coordinates : [];
  polys.forEach((poly) => fn(poly[0], poly));
}
function bboxOf(geom) {
  let w = 180, s = 90, e = -180, n = -90, area = 0;
  eachRing(geom, (ring) => {
    let a = 0;
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const [x, y] = ring[i];
      if (x < w) w = x; if (x > e) e = x; if (y < s) s = y; if (y > n) n = y;
      a += (ring[j][0] + x) * (ring[j][1] - y);
    }
    area += Math.abs(a / 2);
  });
  return { bounds: L.latLngBounds([s, w], [n, e]), area };
}

/* ---------------- data ---------------- */
function prepare(topo) {
  CROPS = D.crops.map(([name, g, unit], i) => ({ i, name, group: D.groups[g], unit }));

  POLYS = new Map();
  for (const f of topojson.feature(topo, topo.objects.districts).features) {
    const p = f.properties;
    const { bounds, area } = bboxOf(f.geometry);
    POLYS.set(p.id, { id: p.id, name: p.name, state: p.state, feature: f, bounds, area,
      pt: L.latLng(p.ly, p.lx), unit: null, layer: null });
  }

  UNITS = new Map();
  for (const [key, u] of Object.entries(D.units)) {
    const polys = u.geo.map((id) => POLYS.get(id)).filter(Boolean);
    if (!polys.length) { console.warn('no polygon for', u.name); continue; }
    const unit = {
      key, raw: u.name, name: u.name.split(' + ').map(titleCase).join(' + '), state: u.state,
      area: u.area, rows: u.c, polys,
      byCrop: new Map(u.c.map((r) => [r[0], r])),
      bounds: polys.reduce((b, p) => b.extend(p.bounds), L.latLngBounds(polys[0].bounds.getSouthWest(), polys[0].bounds.getNorthEast())),
      pt: polys.reduce((a, b) => (b.area > a.area ? b : a)).pt,
    };
    polys.forEach((p) => { p.unit = unit; });
    UNITS.set(key, unit);
  }

  STATES = new Map();
  for (const f of topojson.feature(topo, topo.objects.states).features) {
    const name = f.properties.state;
    const d = D.states[name];
    STATES.set(name, {
      key: 'st:' + name, name, feature: f, bounds: bboxOf(f.geometry).bounds, pt: L.latLng(f.properties.ly, f.properties.lx),
      area: d ? d.area : null, rows: d ? d.c : [], districts: d ? d.districts : 0,
      byCrop: new Map(d ? d.c.map((r) => [r[0], r]) : []), layer: null,
    });
  }
  for (const s of Object.keys(D.states)) if (!STATES.has(s)) console.warn('state without boundary:', s);
}

/* value of the current metric for a unit or state */
function valueOf(ent, metric = S.metric, crop = S.crop) {
  if (crop == null) {
    if (!ent.rows.length) return null;
    return metric === 'n' ? ent.rows.length : ent.area;
  }
  const r = ent.byCrop.get(crop);
  if (!r) return null;
  return metric === 'area' ? r[1] : metric === 'yield' ? r[3] : r[2];
}
const grows = (ent) => S.crop == null || ent.byCrop.has(S.crop);

/* 5 quantile classes over every entity at this level (fixed nationwide, so colours don't shift as you pan) */
function breaks(level) {
  const k = `${level}|${S.metric}|${S.crop}`;
  if (breaksCache.has(k)) return breaksCache.get(k);
  const ents = level === 'state' ? [...STATES.values()] : [...UNITS.values()];
  const vals = ents.map((e) => valueOf(e)).filter((v) => v != null && v > 0).sort((a, b) => a - b);
  const t = [];
  for (let q = 1; q < 5; q++) {
    const v = vals[Math.floor((q / 5) * (vals.length - 1))];
    if (v != null && !t.includes(v)) t.push(v);
  }
  const n = t.length + 1;
  const colors = Array.from({ length: n }, (_, i) => RAMP[n === 1 ? 2 : Math.round((i * (RAMP.length - 1)) / (n - 1))]);
  const out = { t, colors, min: vals[0], max: vals[vals.length - 1] };
  breaksCache.set(k, out);
  return out;
}
function colorFor(v, level) {
  if (v == null || !(v > 0)) return NODATA;
  const b = breaks(level);
  let i = 0;
  while (i < b.t.length && v > b.t[i]) i++;
  return b.colors[i];
}

function metricLabel(metric = S.metric, crop = S.crop) {
  if (crop == null) return metric === 'n' ? 'Number of crops' : 'Total crop area (ha)';
  const c = CROPS[crop];
  return metric === 'area' ? `${c.name} area (ha)` : metric === 'yield'
    ? `${c.name} yield (${unitShort(c.unit)}/ha)` : `${c.name} production (${unitLong(c.unit)})`;
}
function metricText(v, metric = S.metric, crop = S.crop) {
  if (v == null) return crop != null && metric !== 'area' ? 'not reported' : '—';
  if (crop == null) return metric === 'n' ? `${v} crops` : `${fmtC(v)} ha`;
  const u = CROPS[crop].unit;
  return metric === 'area' ? `${fmtC(v)} ha` : metric === 'yield' ? `${fmt(v)} ${unitShort(u)}/ha` : `${fmtC(v)} ${unitShort(u)}`;
}

/* ---------------- styles ---------------- */
function stateStyle(st) {
  const v = valueOf(st);
  const base = { color: '#ffffff', weight: 1, opacity: 1, fillColor: colorFor(v, 'state'), fillOpacity: v == null ? 0.45 : 0.78 };
  if (S.hover === st.key) Object.assign(base, { color: '#0b0b0b', weight: 3 });
  return base;
}
function polyStyle(p) {
  const u = p.unit;
  const v = u ? valueOf(u) : null;
  const s = { color: '#ffffff', weight: 0.8, opacity: 1, fillColor: colorFor(v, 'district'), fillOpacity: u ? 0.66 : 0.4 };
  if (u && S.selected === u.key) Object.assign(s, { color: '#0b0b0b', weight: 3 });
  else if (u && S.hover === u.key) Object.assign(s, { color: '#0b0b0b', weight: 2.5 });
  return s;
}

/* ---------------- map ---------------- */
function initMap() {
  map = L.map('map', { zoomSnap: 0.5, zoomDelta: 0.5, minZoom: 3, maxZoom: 13, zoomControl: true });
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> · Boundaries: geoBoundaries (ODbL) · Crops: DES',
  }).addTo(map);
  map.createPane('states').style.zIndex = 400;
  map.createPane('districts').style.zIndex = 410;
  map.createPane('stateLines').style.zIndex = 420;
  map.getPane('stateLines').style.pointerEvents = 'none';

  const india = [...STATES.values()].reduce((b, s) => b.extend(s.bounds), L.latLngBounds([]));
  // on phones keep India above the bottom sheet
  map.fitBounds(india, isMobile()
    ? { paddingTopLeft: [10, 10], paddingBottomRight: [10, $('#panel').offsetHeight + 10] }
    : { padding: [10, 10] });
  map.setMaxBounds(india.pad(1.5));

  stateFill = L.geoJSON(null, { pane: 'states' });
  stateLines = L.geoJSON(null, { pane: 'stateLines', interactive: false });
  for (const st of STATES.values()) {
    st.layer = L.geoJSON(st.feature, { pane: 'states', style: () => stateStyle(st) });
    st.layer.bindTooltip(() => stateTip(st), { sticky: true, className: 'map-tip', direction: 'top', offset: [0, -10] });
    st.layer.on('mouseover', () => setHover(st.key, true));
    st.layer.on('mouseout', () => setHover(st.key, false));
    st.layer.on('click', () => zoomToState(st));
    stateFill.addLayer(st.layer);
    stateLines.addLayer(L.geoJSON(st.feature, { pane: 'stateLines', interactive: false,
      style: { color: '#3b3a37', weight: 1.6, opacity: 0.8, fill: false } }));
  }
  districtGroup = L.featureGroup().addTo(map);
  markerGroup = L.layerGroup().addTo(map);

  legend = L.control({ position: isMobile() ? 'topright' : 'bottomleft' });
  legend.onAdd = () => {
    const div = L.DomUtil.create('div', 'legend' + (isMobile() ? ' collapsed' : ''));
    L.DomEvent.disableClickPropagation(div);
    L.DomEvent.disableScrollPropagation(div);
    return div;
  };
  legend.addTo(map);

  map.on('moveend', debounce(refresh, DEBOUNCE_MS));
}

function zoomToState(st) {
  const z = Math.max(map.getBoundsZoom(st.bounds), DISTRICT_ZOOM);
  map.flyTo(st.bounds.getCenter(), Math.min(z, 9), { duration: 0.8 });
}

/* the part of the map not covered by the bottom sheet */
function viewBounds() {
  if (!isMobile()) return map.getBounds();
  const size = map.getSize();
  const covered = $('#panel').offsetHeight;
  const h = Math.max(size.y - covered, size.y * 0.25);
  return L.latLngBounds(map.containerPointToLatLng([0, h]), map.containerPointToLatLng([size.x, 0]));
}

function setMode(mode) {
  if (mode === S.mode) return;
  S.mode = mode;
  if (mode === 'state') {
    districtGroup.clearLayers(); drawn.clear();
    markerGroup.clearLayers(); markers.clear();
    stateLines.remove();
    stateFill.addTo(map);
  } else {
    stateFill.remove();
    stateLines.addTo(map);
  }
}

function refresh() {
  const z = map.getZoom();
  setMode(z >= DISTRICT_ZOOM ? 'district' : 'state');
  const el = map.getContainer();
  el.classList.toggle('show-labels', z >= LABEL_ZOOM);
  el.classList.toggle('z-low', z < 7);
  if (S.mode === 'state') restyleStates();
  else drawDistricts(map.getBounds().pad(0.15));
  renderList();
  renderLegend();
}
function restyleStates() {
  for (const st of STATES.values()) st.layer.setStyle(stateStyle(st));
}

/* ---------------- districts on the map ---------------- */
function polyLayer(p) {
  if (!p.layer) {
    p.layer = L.geoJSON(p.feature, { pane: 'districts', style: () => polyStyle(p) });
    p.layer.bindTooltip(() => districtTip(p), { sticky: true, className: 'map-tip', direction: 'top', offset: [0, -10] });
    if (p.unit) {
      p.layer.on('mouseover', () => setHover(p.unit.key, true, true));
      p.layer.on('mouseout', () => setHover(p.unit.key, false));
      p.layer.on('click', () => openDetail(p.unit.key));
    }
  }
  return p.layer;
}

function drawDistricts(bounds) {
  const want = new Set();
  for (const p of POLYS.values()) {
    if (S.crop != null && !(p.unit && grows(p.unit))) continue;
    if (p.bounds.intersects(bounds)) want.add(p);
  }
  for (const p of drawn) if (!want.has(p)) { districtGroup.removeLayer(p.layer); drawn.delete(p); }
  for (const p of want) {
    const l = polyLayer(p);
    if (!drawn.has(p)) { districtGroup.addLayer(l); drawn.add(p); }
    l.setStyle(polyStyle(p));
  }

  // markers: biggest first, skip any that would overlap one already placed (like map labels)
  const labels = map.getZoom() >= LABEL_ZOOM;
  const small = map.getZoom() < 7;
  const cand = [...UNITS.values()].filter((u) => grows(u) && bounds.contains(u.pt))
    .sort((a, b) => (b.key === S.selected) - (a.key === S.selected) || (valueOf(b) ?? -1) - (valueOf(a) ?? -1));
  const placed = [];
  const wantM = new Set();
  for (const u of cand) {
    const p = map.latLngToContainerPoint(u.pt);
    const g = small ? 22 : 28;
    const r = { x: p.x - g / 2, y: p.y - g / 2, w: g + (labels ? 8 + pinLabel(u).length * 6.8 : 0), h: g };
    const gap = labels ? 4 : 10;
    const hit = placed.some((q) => r.x < q.x + q.w + gap && q.x < r.x + r.w + gap && r.y < q.y + q.h + gap && q.y < r.y + r.h + gap);
    if (hit && u.key !== S.selected) continue;
    placed.push(r);
    wantM.add(u.key);
  }
  for (const [k, m] of markers) if (!wantM.has(k)) { markerGroup.removeLayer(m); markers.delete(k); }
  for (const k of wantM) {
    const u = UNITS.get(k);
    const html = pinHtml(u);
    let m = markers.get(k);
    if (!m) {
      m = L.marker(u.pt, { icon: L.divIcon({ className: 'pin-icon', html, iconSize: null }), keyboard: false, riseOnHover: true });
      m._html = html;
      m.on('mouseover', () => setHover(k, true, true));
      m.on('mouseout', () => setHover(k, false));
      m.on('click', () => openDetail(k));
      markers.set(k, m);
      markerGroup.addLayer(m);
    } else if (m._html !== html) {
      m._html = html;
      m.setIcon(L.divIcon({ className: 'pin-icon', html, iconSize: null }));
    }
    markClass(k);
  }
}

function pinLabel(u) {
  return S.crop != null ? metricText(valueOf(u)) : CROPS[u.rows[0][0]].name;
}
function pinHtml(u) {
  const row = S.crop != null ? u.byCrop.get(S.crop) : u.rows[0];
  if (!row) return '';
  const c = CROPS[row[0]];
  const g = GROUP_STYLE[c.group];
  const label = pinLabel(u);
  return `<div class="pin"><span class="glyph" style="--c:${g.color}" title="${esc(c.group)}">${g.glyph}</span>` +
    `<span class="pin-label">${esc(label)}</span></div>`;
}
function markClass(key) {
  const m = markers.get(key);
  const el = m && m.getElement();
  if (!el) return;
  const pin = el.querySelector('.pin');
  if (!pin) return;
  pin.classList.toggle('is-hover', S.hover === key);
  pin.classList.toggle('is-selected', S.selected === key);
  m.setZIndexOffset(S.hover === key || S.selected === key ? 1000 : 0);
}

function districtTip(p) {
  const u = p.unit;
  if (!u) return `<b>${esc(p.name)}</b><small>${esc(p.state)} · no DES crop data for ${esc(D.meta.year)}</small>`;
  const alias = u.polys.length > 1 || titleCase(u.raw).toLowerCase() !== p.name.toLowerCase() ? ` (${esc(p.name)})` : '';
  let body;
  if (S.crop != null) {
    const r = u.byCrop.get(S.crop);
    const c = CROPS[S.crop];
    body = `${esc(c.name)}: ${metricText(r[2], 'prod')} · ${fmtC(r[1])} ha`;
  } else {
    body = `${u.rows.length} crops · ${fmtC(u.area)} ha · main: ${esc(CROPS[u.rows[0][0]].name)}`;
  }
  return `<b>${esc(u.name)}${alias}</b><small>${esc(u.state)}<br>${body}</small>`;
}
function stateTip(st) {
  if (!st.rows.length) return `<b>${esc(st.name)}</b><small>No DES crop data for ${esc(D.meta.year)}</small>`;
  const v = valueOf(st);
  return `<b>${esc(st.name)}</b><small>${esc(metricLabel())}: ${metricText(v)}<br>` +
    `Top crops: ${st.rows.slice(0, 3).map((r) => esc(CROPS[r[0]].name)).join(', ')}</small>`;
}

/* ---------------- hover sync ---------------- */
function setHover(key, on, fromMap = false) {
  if (!on && S.hover !== key) return;
  const prev = S.hover;
  S.hover = on ? key : null;
  [prev, S.hover].forEach((k) => k && restyle(k));
  document.querySelectorAll('#results li.is-hover').forEach((li) => li.classList.remove('is-hover'));
  if (S.hover) {
    const li = document.querySelector(`#results li[data-key="${CSS.escape(S.hover)}"]`);
    if (li) {
      li.classList.add('is-hover');
      if (fromMap && !isMobile()) li.scrollIntoView({ block: 'nearest' });
    }
  }
}
function restyle(key) {
  if (key.startsWith('st:')) {
    const st = STATES.get(key.slice(3));
    if (st && st.layer) { st.layer.setStyle(stateStyle(st)); if (S.hover === key) st.layer.bringToFront(); }
    return;
  }
  const u = UNITS.get(key);
  if (!u) return;
  for (const p of u.polys) {
    if (!drawn.has(p)) continue;
    p.layer.setStyle(polyStyle(p));
    if (S.hover === key || S.selected === key) p.layer.bringToFront();
  }
  markClass(key);
}

/* ---------------- side list ---------------- */
function renderList() {
  const vb = viewBounds();
  const ol = $('#results');
  const cropName = S.crop != null ? CROPS[S.crop].name : null;
  let items;
  if (S.mode === 'state') {
    items = [...STATES.values()].filter((s) => s.rows.length && grows(s) && vb.contains(s.pt));
  } else {
    items = [...UNITS.values()].filter((u) => grows(u) && vb.contains(u.pt));
  }
  items.sort((a, b) => (valueOf(b) ?? -1) - (valueOf(a) ?? -1) || a.name.localeCompare(b.name));

  const what = S.mode === 'state' ? (items.length === 1 ? 'state' : 'states') : (items.length === 1 ? 'district' : 'districts');
  $('#list-title').textContent = cropName
    ? `${items.length} ${what} growing ${cropName} in view`
    : `${items.length} ${what} in view`;
  $('#list-sub').textContent = `Ranked by ${metricLabel().replace(/^./, (c) => c.toLowerCase())} · ` +
    (S.mode === 'state' ? 'zoom in for districts' : 'hover to find on map, click for details');

  if (!items.length) {
    ol.innerHTML = `<li class="empty">${cropName ? `No ${S.mode === 'state' ? 'state' : 'district'} in view grows ${esc(cropName)}.`
      : 'Nothing here. Pan or zoom out.'}</li>`;
    return;
  }
  const html = items.slice(0, LIST_LIMIT).map((e, i) => (S.mode === 'state' ? stateItem(e, i) : districtItem(e, i))).join('');
  const more = items.length > LIST_LIMIT ? `<li class="more">+ ${items.length - LIST_LIMIT} more · zoom in to narrow down</li>` : '';
  ol.innerHTML = html + more;
  ol.scrollTop = 0;
}

function cropList(rows, n, highlight) {
  return rows.slice(0, n).map((r) => {
    const name = esc(CROPS[r[0]].name);
    return r[0] === highlight ? `<b>${name}</b>` : name;
  }).join(', ');
}
function valueCell(e) {
  const v = valueOf(e);
  let sub = '';
  if (S.crop != null) {
    const r = e.byCrop.get(S.crop);
    sub = S.metric === 'area' ? metricText(r[2], 'prod') : `${fmtC(r[1])} ha`;
  } else if (S.metric === 'n') {
    sub = `${fmtC(e.area)} ha`;
  }
  return `<span class="value">${esc(metricText(v))}${sub ? `<small>${esc(sub)}</small>` : ''}</span>`;
}
function districtItem(u, i) {
  const main = CROPS[(S.crop != null ? S.crop : u.rows[0][0])];
  const g = GROUP_STYLE[main.group];
  return `<li data-key="${esc(u.key)}" tabindex="0" class="${S.selected === u.key ? 'is-selected' : ''}">
    <span class="rank"><span class="glyph" style="--c:${g.color}" title="${esc(main.name)} (${esc(main.group)})">${g.glyph}</span></span>
    <span class="name">${esc(u.name)}<br><span class="where">${esc(u.state)}</span></span>
    ${valueCell(u)}
    <span class="crops">${u.rows.length} crops · ${cropList(u.rows, 5, S.crop)}</span>
  </li>`;
}
function stateItem(st, i) {
  return `<li data-key="${esc(st.key)}" tabindex="0">
    <span class="rank"><span class="state-dot" style="--c:${colorFor(valueOf(st), 'state')}"></span></span>
    <span class="name">${esc(st.name)}<br><span class="where">${st.districts} districts · ${st.rows.length} crops</span></span>
    ${valueCell(st)}
    <span class="crops">Top 3: ${cropList(st.rows, 3, S.crop)}</span>
  </li>`;
}

function onListEvent(e) {
  const li = e.target.closest('li[data-key]');
  if (!li) return;
  const key = li.dataset.key;
  if (e.type === 'mouseover') setHover(key, true);
  else if (e.type === 'mouseout') { if (!li.contains(e.relatedTarget)) setHover(key, false); }
  else if (e.type === 'click' || (e.type === 'keydown' && e.key === 'Enter')) {
    if (key.startsWith('st:')) zoomToState(STATES.get(key.slice(3)));
    else openDetail(key, { fly: true });
  }
}

/* ---------------- legend ---------------- */
function renderLegend() {
  const div = legend.getContainer();
  const level = S.mode;
  const b = breaks(level);
  const f = (v) => S.metric === 'yield' ? fmt(v) : S.metric === 'n' && S.crop == null ? String(v) : fmtC(v);
  const rows = b.colors.map((c, i) => {
    const lo = i === 0 ? b.min : b.t[i - 1];
    const hi = i === b.t.length ? b.max : b.t[i];
    return `<div class="row"><span class="sw" style="--c:${c}"></span>${f(lo)} – ${f(hi)}</div>`;
  }).join('');
  const nod = S.crop != null
    ? (S.mode === 'state' ? 'Not grown / no data' : 'Grown, value not reported')
    : 'No data';
  let groups = '';
  if (level === 'district' && S.crop == null) {
    groups = `<div class="groups">${Object.entries(GROUP_STYLE).map(([name, g]) =>
      `<span title="${esc(name)}"><i class="g" style="--c:${g.color}">${g.glyph}</i>${esc(g.short)}</span>`).join('')}</div>
      <div class="hint">Marker: main crop (largest area)</div>`;
  }
  const hint = level === 'state' ? `<div class="hint">Zoom in for districts</div>`
    : S.crop != null ? `<div class="hint">Only districts growing ${esc(CROPS[S.crop].name)}</div>` : '';
  const title = `${esc(metricLabel())} · ${level === 'state' ? 'states' : 'districts'}`;
  const collapsed = div.classList.contains('collapsed');
  div.innerHTML = `<button class="legend-toggle" type="button" aria-expanded="${!collapsed}">Legend</button>
    <div class="body"><h4>${title}</h4>${rows}
    <div class="row"><span class="sw" style="--c:${NODATA}"></span>${nod}</div>${groups}${hint}</div>`;
  div.querySelector('.legend-toggle').onclick = () => {
    div.classList.toggle('collapsed');
    div.querySelector('.legend-toggle').setAttribute('aria-expanded', !div.classList.contains('collapsed'));
  };
}

/* ---------------- detail panel ---------------- */
function openDetail(key, { fly = false } = {}) {
  const u = UNITS.get(key);
  if (!u) return;
  const prev = S.selected;
  S.selected = key;
  if (prev && prev !== key) restyle(prev);
  restyle(key);
  document.querySelectorAll('#results li.is-selected').forEach((li) => li.classList.remove('is-selected'));
  $('#list-view').hidden = true;
  $('#detail-view').hidden = false;
  renderDetail();
  if (isMobile() && $('#panel').dataset.sheet === 'peek') setSheet('half');
  if (fly) {
    const pad = isMobile() ? { paddingTopLeft: [20, 20], paddingBottomRight: [20, $('#panel').offsetHeight + 20] } : { padding: [40, 40] };
    map.flyToBounds(u.bounds, { maxZoom: 10, duration: 0.8, ...pad });
  }
}
function closeDetail() {
  const prev = S.selected;
  S.selected = null;
  if (prev) restyle(prev);
  $('#detail-view').hidden = true;
  $('#list-view').hidden = false;
  renderList();
}

function renderDetail() {
  const u = UNITS.get(S.selected);
  if (!u) return;
  $('#d-title').textContent = u.name;
  $('#d-sub').textContent = `${u.state} · ${D.meta.year}`;

  const notes = [];
  const polyNames = u.polys.map((p) => p.name);
  if (u.raw.includes(' + ')) notes.push(`DES reports these districts separately, but they share one boundary (${esc(polyNames.join(', '))}), so their crops are added together here.`);
  else if (u.polys.length > 1) notes.push(`Drawn over ${u.polys.length} boundaries: ${esc(polyNames.join(', '))}. DES reports them as one district for ${esc(D.meta.year)}.`);
  else if (polyNames[0].toLowerCase() !== titleCase(u.raw).toLowerCase()) notes.push(`Boundary name: ${esc(polyNames[0])}.`);

  const main = CROPS[u.rows[0][0]];
  const top = u.rows.filter((r) => r[1] > 0).slice(0, 10);
  $('#d-body').innerHTML = `
    ${notes.map((n) => `<p class="note">${n}</p>`).join('')}
    <div class="tiles">
      <div class="tile"><span>Total crop area</span><b>${fmtC(u.area)} ha</b></div>
      <div class="tile"><span>Crops grown</span><b>${u.rows.length}</b></div>
      <div class="tile"><span>Main crop</span><b>${GROUP_STYLE[main.group].glyph} ${esc(main.name)}</b></div>
    </div>
    <h3>Top ${top.length} crops by area (ha)</h3>
    <div class="chart" id="d-chart">${barChart(top)}</div>
    <div class="legend-inline">${[...new Set(top.map((r) => CROPS[r[0]].group))].map((g) =>
      `<span><i style="--c:${GROUP_STYLE[g].color}"></i>${esc(GROUP_STYLE[g].short)}</span>`).join('')}</div>
    <h3>All ${u.rows.length} crops</h3>
    <div class="table-wrap">${cropTable(u)}</div>
    <p class="foot">Production is in each crop's own unit: tonnes, except Coconut (nuts), Cotton (bales of 170 kg) and
      Jute, Mesta, Sannhamp (bales of 180 kg). Seasons are added together. Source: ${esc(D.meta.source)}.</p>`;
  bindChart();
  $('#d-body').querySelectorAll('th[data-col]').forEach((th) => th.addEventListener('click', () => {
    const col = th.dataset.col;
    S.tableSort = { col, dir: S.tableSort.col === col ? -S.tableSort.dir : (col === 'name' || col === 'season' ? 1 : -1) };
    renderDetail();
  }));
}

function barChart(rows) {
  if (!rows.length) return '<p class="panel-sub">No area reported.</p>';
  const W = 360, rowH = 24, barH = 16, labelW = 124, valW = 58;
  const plotW = W - labelW - valW;
  const max = rows[0][1];
  const H = rows.length * rowH;
  const r = 4;
  const bars = rows.map((row, i) => {
    const c = CROPS[row[0]];
    const w = Math.max(2, (row[1] / max) * plotW);
    const y = i * rowH + (rowH - barH) / 2;
    const x = labelW;
    const rr = Math.min(r, w / 2);
    // square at the baseline, rounded at the data end
    const d = `M${x},${y}h${w - rr}a${rr},${rr} 0 0 1 ${rr},${rr}v${barH - 2 * rr}a${rr},${rr} 0 0 1 -${rr},${rr}h-${w - rr}z`;
    const name = c.name.length > 19 ? c.name.slice(0, 18) + '…' : c.name;
    return `<g data-i="${i}">
      <rect class="hit" x="0" y="${i * rowH}" width="${W}" height="${rowH}" fill="transparent"></rect>
      <text class="lbl" x="${labelW - 8}" y="${y + barH / 2}" text-anchor="end" dominant-baseline="central">${esc(name)}</text>
      <path class="bar" d="${d}" fill="${GROUP_STYLE[c.group].color}"></path>
      <text class="val" x="${x + w + 6}" y="${y + barH / 2}" dominant-baseline="central">${fmtC(row[1])}</text>
    </g>`;
  }).join('');
  return `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Top crops by area, bar chart">
    <line class="grid" x1="${labelW}" x2="${labelW}" y1="0" y2="${H}"></line>${bars}</svg>`;
}
function bindChart() {
  const svg = $('#d-chart svg');
  if (!svg) return;
  const tip = $('#chart-tip');
  const u = UNITS.get(S.selected);
  const rows = u.rows.filter((r) => r[1] > 0).slice(0, 10);
  svg.querySelectorAll('g[data-i]').forEach((g) => {
    const row = rows[+g.dataset.i];
    const c = CROPS[row[0]];
    g.addEventListener('mousemove', (e) => {
      tip.hidden = false;
      tip.innerHTML = `<b>${esc(c.name)}</b>${esc(c.group)}<br>Area: ${fmt(row[1])} ha<br>` +
        `Production: ${row[2] == null ? 'not reported' : `${fmt(row[2])} ${esc(unitLong(c.unit))}`}<br>` +
        `Yield: ${row[3] == null ? '—' : `${fmt(row[3])} ${esc(unitShort(c.unit))}/ha`}`;
      const x = Math.min(e.clientX + 14, window.innerWidth - tip.offsetWidth - 8);
      tip.style.left = x + 'px';
      tip.style.top = (e.clientY + 14) + 'px';
      g.querySelector('.bar').classList.add('is-hover');
    });
    g.addEventListener('mouseleave', () => { tip.hidden = true; g.querySelector('.bar').classList.remove('is-hover'); });
  });
}

function seasonsOf(mask) {
  return D.seasons.filter((_, i) => mask & (1 << i)).join(', ');
}
function cropTable(u) {
  const { col, dir } = S.tableSort;
  const key = { name: (r) => CROPS[r[0]].name, area: (r) => r[1], prod: (r) => r[2], yield: (r) => r[3], season: (r) => seasonsOf(r[4]) }[col];
  const rows = [...u.rows].sort((a, b) => {
    const va = key(a), vb = key(b);
    if (va == null && vb == null) return 0;
    if (va == null) return 1;
    if (vb == null) return -1;
    return (typeof va === 'string' ? va.localeCompare(vb) : va - vb) * dir;
  });
  const th = (c, label) => `<th data-col="${c}" ${col === c ? `aria-sort="${dir > 0 ? 'ascending' : 'descending'}"` : ''}>${label}${col === c ? (dir > 0 ? ' ↑' : ' ↓') : ''}</th>`;
  const body = rows.map((r) => {
    const c = CROPS[r[0]];
    const g = GROUP_STYLE[c.group];
    return `<tr class="${r[0] === S.crop ? 'is-crop' : ''}">
      <td><span class="dot" style="--c:${g.color}" title="${esc(c.group)}"></span>${esc(c.name)}</td>
      <td>${fmt(r[1])}</td>
      <td>${r[2] == null ? '<small title="not reported">—</small>' : `${fmt(r[2])} <small>${esc(unitLong(c.unit))}</small>`}</td>
      <td>${r[3] == null ? '—' : `${fmt(r[3])} <small>${esc(unitShort(c.unit))}/ha</small>`}</td>
      <td>${esc(seasonsOf(r[4]))}</td></tr>`;
  }).join('');
  return `<table class="crops"><thead><tr>${th('name', 'Crop')}${th('area', 'Area (ha)')}${th('prod', 'Production')}${th('yield', 'Yield')}${th('season', 'Seasons')}</tr></thead><tbody>${body}</tbody></table>`;
}

/* ---------------- controls ---------------- */
function initControls() {
  $('#year').textContent = D.meta.year;
  const sel = $('#crop');
  for (const g of D.groups) {
    const og = document.createElement('optgroup');
    og.label = g;
    CROPS.filter((c) => c.group === g).sort((a, b) => a.name.localeCompare(b.name)).forEach((c) => {
      const o = document.createElement('option');
      o.value = c.i;
      o.textContent = c.unit === 'tonnes' ? c.name : `${c.name} (${unitShort(c.unit)})`;
      og.appendChild(o);
    });
    sel.appendChild(og);
  }
  sel.addEventListener('change', () => {
    S.crop = sel.value === '' ? null : +sel.value;
    S.metric = S.crop == null ? 'area' : 'prod';
    fillMetrics();
    onFilterChange();
  });
  $('#metric').addEventListener('change', (e) => { S.metric = e.target.value; onFilterChange(); });
  fillMetrics();

  const ol = $('#results');
  ['mouseover', 'mouseout', 'click', 'keydown'].forEach((t) => ol.addEventListener(t, onListEvent));
  $('#back').addEventListener('click', closeDetail);
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && S.selected) closeDetail(); });
  initSearch();
  initSheet();
}

function fillMetrics() {
  const opts = S.crop == null
    ? [['area', 'Total crop area'], ['n', 'Number of crops']]
    : [['prod', 'Production'], ['area', 'Area'], ['yield', 'Yield']];
  $('#metric').innerHTML = opts.map(([v, l]) => `<option value="${v}" ${v === S.metric ? 'selected' : ''}>${l}</option>`).join('');
}

function onFilterChange() {
  if (S.hover) { const h = S.hover; S.hover = null; restyle(h); }
  if (S.mode === 'district') drawDistricts(map.getBounds().pad(0.15));
  restyleStates();
  renderList();
  renderLegend();
  if (S.selected) renderDetail();
}

/* ---------------- search ---------------- */
const norm = (s) => s.toLowerCase().normalize('NFKD').replace(/[^a-z0-9]/g, '');
let SEARCH = [];
function initSearch() {
  const seen = new Set();
  for (const st of STATES.values()) SEARCH.push({ label: st.name, sub: 'State', kind: 'state', ref: st });
  for (const u of UNITS.values()) {
    SEARCH.push({ label: u.name, sub: u.state, kind: 'unit', ref: u });
    seen.add(norm(u.name));
    for (const p of u.polys) {
      if (!seen.has(norm(p.name))) SEARCH.push({ label: p.name, sub: `${u.state} · data: ${u.name}`, kind: 'unit', ref: u });
    }
  }
  for (const p of POLYS.values()) {
    if (!p.unit) SEARCH.push({ label: p.name, sub: `${p.state} · no data`, kind: 'poly', ref: p });
  }
  SEARCH.forEach((s) => { s.n = norm(s.label); s.words = s.label.toLowerCase().split(/[\s()+\-]+/).map(norm); });

  const input = $('#search');
  const list = $('#search-results');
  const box = input.parentElement;
  let results = [], active = -1;

  const show = () => {
    list.innerHTML = results.length
      ? results.map((r, i) => `<li role="option" id="sr-${i}" aria-selected="${i === active}" data-i="${i}"><span>${esc(r.label)}</span><small>${esc(r.sub)}</small></li>`).join('')
      : '<li class="empty">No district or state by that name</li>';
    list.hidden = false;
    box.setAttribute('aria-expanded', 'true');
    input.setAttribute('aria-activedescendant', active >= 0 ? `sr-${active}` : '');
  };
  const hide = () => { list.hidden = true; box.setAttribute('aria-expanded', 'false'); active = -1; };
  const choose = (r) => {
    hide();
    input.value = r.label;
    input.blur();
    if (r.kind === 'state') {
      if (S.selected) closeDetail();
      map.flyToBounds(r.ref.bounds, { duration: 0.8, padding: [20, 20] });
    } else if (r.kind === 'unit') {
      openDetail(r.ref.key, { fly: true });
    } else {
      map.flyToBounds(r.ref.bounds, { maxZoom: 9, duration: 0.8 });
    }
  };

  input.addEventListener('input', () => {
    const q = norm(input.value);
    if (!q) { hide(); return; }
    const scored = [];
    for (const s of SEARCH) {
      let sc = s.n.startsWith(q) ? 0 : s.words.some((w) => w.startsWith(q)) ? 1 : s.n.includes(q) ? 2 : -1;
      if (sc < 0) continue;
      if (s.kind === 'state') sc -= 0.5;
      scored.push([sc, s]);
    }
    scored.sort((a, b) => a[0] - b[0] || a[1].label.localeCompare(b[1].label));
    results = scored.slice(0, 8).map((x) => x[1]);
    active = results.length ? 0 : -1;
    show();
  });
  input.addEventListener('keydown', (e) => {
    if (list.hidden) return;
    if (e.key === 'ArrowDown') { active = Math.min(active + 1, results.length - 1); show(); e.preventDefault(); }
    else if (e.key === 'ArrowUp') { active = Math.max(active - 1, 0); show(); e.preventDefault(); }
    else if (e.key === 'Enter' && results[active]) { choose(results[active]); e.preventDefault(); }
    else if (e.key === 'Escape') hide();
  });
  list.addEventListener('mousedown', (e) => {
    const li = e.target.closest('li[data-i]');
    if (li) { e.preventDefault(); choose(results[+li.dataset.i]); }
  });
  input.addEventListener('blur', () => setTimeout(hide, 150));
  input.addEventListener('focus', () => { if (input.value) input.dispatchEvent(new Event('input')); });
}

/* ---------------- mobile bottom sheet ---------------- */
function setSheet(state) {
  const panel = $('#panel');
  panel.style.removeProperty('--sheet-h');
  panel.dataset.sheet = state;
  setTimeout(syncSheetOffset, 250);
}
function syncSheetOffset() {
  const h = isMobile() ? $('#panel').offsetHeight : 0;
  document.documentElement.style.setProperty('--sheet-offset', `${Math.min(h, map.getSize().y * 0.6)}px`);
  if (S.mode) renderList();
}
function initSheet() {
  const panel = $('#panel');
  const handle = $('#sheet-handle');
  const order = ['peek', 'half', 'full'];
  let startY = null, startH = 0, moved = false;
  handle.addEventListener('click', () => {
    if (moved) { moved = false; return; }
    setSheet(order[(order.indexOf(panel.dataset.sheet) + 1) % order.length]);
  });
  handle.addEventListener('pointerdown', (e) => {
    startY = e.clientY; startH = panel.offsetHeight; moved = false;
    handle.setPointerCapture(e.pointerId);
    panel.classList.add('dragging');
  });
  handle.addEventListener('pointermove', (e) => {
    if (startY == null) return;
    const dy = startY - e.clientY;
    if (Math.abs(dy) > 4) moved = true;
    const max = panel.parentElement.offsetHeight - 12;
    panel.style.setProperty('--sheet-h', `${Math.max(90, Math.min(max, startH + dy))}px`);
  });
  const end = () => {
    if (startY == null) return;
    startY = null;
    panel.classList.remove('dragging');
    if (!moved) return;
    const h = panel.offsetHeight, total = panel.parentElement.offsetHeight;
    const targets = { peek: 132, half: total * 0.45, full: total - 12 };
    const best = Object.entries(targets).sort((a, b) => Math.abs(a[1] - h) - Math.abs(b[1] - h))[0][0];
    setSheet(best);
  };
  handle.addEventListener('pointerup', end);
  handle.addEventListener('pointercancel', end);
  window.addEventListener('resize', debounce(() => { syncSheetOffset(); map.invalidateSize(); }, 200));
  syncSheetOffset();
}

/* ---------------- boot ---------------- */
(async function main() {
  try {
    const [topo, data] = await Promise.all([
      fetch('data/india.topo.json').then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); }),
      fetch('data/crops.json').then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); }),
    ]);
    D = data;
    prepare(topo);
  } catch (err) {
    console.error(err);
    $('#list-title').textContent = 'Could not load the data files.';
    $('#list-sub').textContent = 'Serve this folder over HTTP: run "python -m http.server" in web/ and open http://localhost:8000.';
    return;
  }
  initMap();
  initControls();
  refresh();
  window.cropMap = { map, S, UNITS, STATES };   // handy from the browser console
})();
