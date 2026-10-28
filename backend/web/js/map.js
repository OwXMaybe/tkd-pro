// Route view: MapLibre with OpenFreeMap tiles (same as the phone app), or an offline outline
// drawn from the synced GPS points when the map library or tiles are unavailable.
import { h, svg } from './core.js';
import { t } from './i18n.js';

let loading = null;
function loadMapLibre() {
  if (window.maplibregl) return Promise.resolve(window.maplibregl);
  loading ??= new Promise((resolve, reject) => {
    document.head.append(h('link', { rel: 'stylesheet', href: '/vendor/maplibre-gl.css' }));
    const s = h('script', { src: '/vendor/maplibre-gl.js' });
    s.onload = () => resolve(window.maplibregl);
    s.onerror = reject;
    document.head.append(s);
  });
  return loading;
}

function accent() {
  return getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#d92f3d';
}

/** Offline outline: an equirectangular projection of the points, one path per segment. */
export function routeSketch(points) {
  const lat0 = points.reduce((a, p) => a + p.lat, 0) / points.length;
  const k = Math.cos((lat0 * Math.PI) / 180);
  const xs = points.map((p) => p.lon * k), ys = points.map((p) => -p.lat);
  const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
  const size = Math.max(maxX - minX, maxY - minY) || 1e-6;
  const W = 600, H = 360, pad = 20;
  const scale = Math.min((W - 2 * pad) / size, (H - 2 * pad) / size);
  const ox = (W - (maxX - minX) * scale) / 2, oy = (H - (maxY - minY) * scale) / 2;
  const segments = new Map();
  points.forEach((p, i) => {
    const seg = segments.get(p.seg) ?? [];
    seg.push(`${(xs[i] - minX) * scale + ox},${(ys[i] - minY) * scale + oy}`);
    segments.set(p.seg, seg);
  });
  const root = svg('svg', { viewBox: `0 0 ${W} ${H}`, class: 'route-sketch', role: 'img', 'aria-label': 'route' });
  for (const seg of segments.values()) root.append(svg('polyline', { points: seg.join(' '), class: 'route-line' }));
  const first = points[0], last = points[points.length - 1];
  const at = (p) => [(p.lon * k - minX) * scale + ox, (-p.lat - minY) * scale + oy];
  const [sx, sy] = at(first), [ex, ey] = at(last);
  root.append(svg('circle', { cx: sx, cy: sy, r: 6, class: 'route-start' }), svg('circle', { cx: ex, cy: ey, r: 6, class: 'route-end' }));
  return root;
}

export function routeMap(points) {
  const container = h('div', { class: 'map' });
  const note = h('p', { class: 'muted small' });
  const wrap = h('div', null, container, note);
  const fallback = () => {
    container.replaceChildren(routeSketch(points));
    container.classList.add('offline');
    note.textContent = t('run_map_offline');
  };
  loadMapLibre().then((maplibregl) => {
    const map = new maplibregl.Map({
      container,
      style: 'https://tiles.openfreemap.org/styles/liberty',
      attributionControl: { compact: true },
      cooperativeGestures: true,
    });
    const timeout = setTimeout(fallback, 8000);
    map.on('error', () => { clearTimeout(timeout); fallback(); });
    map.on('load', () => {
      clearTimeout(timeout);
      const bySeg = new Map();
      for (const p of points) { const s = bySeg.get(p.seg) ?? []; s.push([p.lon, p.lat]); bySeg.set(p.seg, s); }
      map.addSource('route', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [...bySeg.values()].map((c) => ({ type: 'Feature', geometry: { type: 'LineString', coordinates: c }, properties: {} })) },
      });
      map.addLayer({ id: 'route', type: 'line', source: 'route', paint: { 'line-color': accent(), 'line-width': 4 }, layout: { 'line-cap': 'round', 'line-join': 'round' } });
      const lons = points.map((p) => p.lon), lats = points.map((p) => p.lat);
      map.fitBounds([[Math.min(...lons), Math.min(...lats)], [Math.max(...lons), Math.max(...lats)]], { padding: 40, duration: 0 });
    });
  }).catch(fallback);
  return wrap;
}
