// Small SVG charts. One series per chart (single accent hue, so identity never depends on color),
// thin marks, rounded data ends on the baseline, recessive grid, hover tooltips, and a table view.
import { h, svg } from './core.js';
import { t } from './i18n.js';

const W = 640;
const H = 220;
const PAD = { top: 16, right: 12, bottom: 28, left: 44 };

function niceMax(v) {
  if (v <= 0) return 1;
  const p = 10 ** Math.floor(Math.log10(v));
  const n = v / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p;
}

function tooltip(wrap) {
  const tip = h('div', { class: 'chart-tip', role: 'tooltip' });
  wrap.append(tip);
  return {
    show(x, y, text) {
      tip.textContent = text;
      tip.style.display = 'block';
      const box = wrap.getBoundingClientRect();
      const left = Math.min(Math.max(x - tip.offsetWidth / 2, 0), box.width - tip.offsetWidth);
      tip.style.left = `${left}px`;
      tip.style.top = `${Math.max(y - tip.offsetHeight - 8, 0)}px`;
    },
    hide() { tip.style.display = 'none'; },
  };
}

function frame(title, chartEl, tableRows, columns) {
  let showTable = false;
  const body = h('div', { class: 'chart-body' }, chartEl);
  const toggle = h('button', { class: 'btn ghost small', type: 'button' }, t('ins_table'));
  toggle.addEventListener('click', () => {
    showTable = !showTable;
    toggle.textContent = showTable ? t('ins_chart') : t('ins_table');
    body.replaceChildren(showTable ? dataTable(tableRows, columns) : chartEl);
  });
  return h('section', { class: 'card chart-card' },
    h('div', { class: 'row between' }, h('h3', { class: 'card-title' }, title), toggle),
    body);
}

function dataTable(rows, columns) {
  return h('div', { class: 'table-wrap' }, h('table', { class: 'table' },
    h('thead', null, h('tr', null, columns.map((c) => h('th', null, c)))),
    h('tbody', null, rows.map((r) => h('tr', null, r.map((v) => h('td', null, v)))))));
}

function grid(root, max, unitFmt) {
  const plotH = H - PAD.top - PAD.bottom;
  for (let i = 0; i <= 4; i++) {
    const v = (max / 4) * i;
    const y = PAD.top + plotH - (v / max) * plotH;
    root.append(svg('line', { x1: PAD.left, x2: W - PAD.right, y1: y, y2: y, class: i === 0 ? 'axis' : 'gridline' }));
    root.append(svg('text', { x: PAD.left - 6, y: y + 4, 'text-anchor': 'end', class: 'tick' }, unitFmt(v)));
  }
}

/** Vertical bars, e.g. one per week. points: [{label, value, tip}] */
export function barChart(title, points, { format = (v) => String(Math.round(v)), unit = '' } = {}) {
  const max = niceMax(Math.max(0, ...points.map((p) => p.value || 0)));
  const root = svg('svg', { viewBox: `0 0 ${W} ${H}`, class: 'chart', role: 'img', 'aria-label': title });
  const wrap = h('div', { class: 'chart-wrap' }, root);
  const tip = tooltip(wrap);
  grid(root, max, format);
  const plotW = W - PAD.left - PAD.right;
  const plotH = H - PAD.top - PAD.bottom;
  const slot = plotW / Math.max(points.length, 1);
  const barW = Math.max(2, Math.min(28, slot - 2)); // 2px surface gap between neighbours
  const labelEvery = Math.ceil(points.length / 8);
  points.forEach((p, i) => {
    const v = p.value || 0;
    const bh = (v / max) * plotH;
    const x = PAD.left + i * slot + (slot - barW) / 2;
    const y = PAD.top + plotH - bh;
    if (bh > 0) {
      const r = Math.min(4, barW / 2, bh);
      // Rounded top, square base anchored to the baseline.
      root.append(svg('path', {
        class: 'bar',
        d: `M${x},${y + bh} V${y + r} Q${x},${y} ${x + r},${y} H${x + barW - r} Q${x + barW},${y} ${x + barW},${y + r} V${y + bh} Z`,
      }));
    }
    const hit = svg('rect', { x: PAD.left + i * slot, y: PAD.top, width: slot, height: plotH, class: 'hit' });
    hit.addEventListener('mousemove', (e) => {
      const b = wrap.getBoundingClientRect();
      tip.show(e.clientX - b.left, (y / H) * b.height, p.tip ?? `${p.label}: ${format(v)}${unit}`);
    });
    hit.addEventListener('mouseleave', tip.hide);
    root.append(hit);
    if (i % labelEvery === 0) root.append(svg('text', { x: x + barW / 2, y: H - 8, 'text-anchor': 'middle', class: 'tick' }, p.label));
  });
  return frame(title, wrap, points.map((p) => [p.label, `${format(p.value || 0)}${unit}`]), [t('ins_week'), title]);
}

/** Line with markers for sparse daily values. points: [{x (ms), value, label}] */
export function lineChart(title, points, { format = (v) => String(Math.round(v)), min = null, max = null } = {}) {
  const values = points.map((p) => p.value);
  const lo = min ?? Math.min(...values);
  const hi = max ?? niceMax(Math.max(...values));
  const span = hi - lo || 1;
  const xs = points.map((p) => p.x);
  const x0 = Math.min(...xs), x1 = Math.max(...xs);
  const root = svg('svg', { viewBox: `0 0 ${W} ${H}`, class: 'chart', role: 'img', 'aria-label': title });
  const wrap = h('div', { class: 'chart-wrap' }, root);
  const tip = tooltip(wrap);
  const plotW = W - PAD.left - PAD.right;
  const plotH = H - PAD.top - PAD.bottom;
  for (let i = 0; i <= 4; i++) {
    const v = lo + (span / 4) * i;
    const y = PAD.top + plotH - ((v - lo) / span) * plotH;
    root.append(svg('line', { x1: PAD.left, x2: W - PAD.right, y1: y, y2: y, class: i === 0 ? 'axis' : 'gridline' }));
    root.append(svg('text', { x: PAD.left - 6, y: y + 4, 'text-anchor': 'end', class: 'tick' }, format(v)));
  }
  const px = (x) => PAD.left + (x1 === x0 ? plotW / 2 : ((x - x0) / (x1 - x0)) * plotW);
  const py = (v) => PAD.top + plotH - ((v - lo) / span) * plotH;
  if (points.length > 1) {
    root.append(svg('path', { class: 'line', d: points.map((p, i) => `${i ? 'L' : 'M'}${px(p.x)},${py(p.value)}`).join(' ') }));
  }
  const cross = svg('line', { y1: PAD.top, y2: PAD.top + plotH, class: 'crosshair', style: 'display:none' });
  root.append(cross);
  points.forEach((p) => root.append(svg('circle', { cx: px(p.x), cy: py(p.value), r: 4, class: 'dot' })));
  [points[0], points[points.length - 1]].forEach((p, i) => {
    if (p) root.append(svg('text', { x: px(p.x), y: H - 8, 'text-anchor': i ? 'end' : 'start', class: 'tick' }, p.label));
  });
  const hit = svg('rect', { x: PAD.left, y: PAD.top, width: plotW, height: plotH, class: 'hit' });
  hit.addEventListener('mousemove', (e) => {
    const b = wrap.getBoundingClientRect();
    const sx = ((e.clientX - b.left) / b.width) * W;
    let best = points[0];
    for (const p of points) if (Math.abs(px(p.x) - sx) < Math.abs(px(best.x) - sx)) best = p;
    cross.setAttribute('x1', px(best.x)); cross.setAttribute('x2', px(best.x)); cross.style.display = '';
    tip.show((px(best.x) / W) * b.width, (py(best.value) / H) * b.height, `${best.label}: ${format(best.value)}`);
  });
  hit.addEventListener('mouseleave', () => { tip.hide(); cross.style.display = 'none'; });
  root.append(hit);
  return frame(title, wrap, points.map((p) => [p.label, format(p.value)]), [t('rec_day'), title]);
}

/** Ranked horizontal bars with direct value labels. items: [{label, value}] */
export function rankChart(title, items, { format = (v) => String(Math.round(v)) } = {}) {
  const max = Math.max(1, ...items.map((i) => i.value));
  const list = h('div', { class: 'rank' }, items.map((i) => h('div', { class: 'rank-row' },
    h('span', { class: 'rank-label' }, i.label),
    h('span', { class: 'rank-track' }, h('span', { class: 'rank-bar', style: { width: `${(i.value / max) * 100}%` } })),
    h('span', { class: 'rank-value' }, format(i.value)))));
  return frame(title, list, items.map((i) => [i.label, format(i.value)]), ['', title]);
}
