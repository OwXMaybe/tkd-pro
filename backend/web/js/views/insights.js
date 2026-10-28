import { byId, dayToDate, emptyState, epochDay, fmt, h, pageHeader, rows, sessionLoad, startOfWeek, tabs } from '../core.js';
import { barChart, lineChart, rankChart } from '../charts.js';
import { enumLabel, t } from '../i18n.js';

let range = '12w';
const WEEKS = { '4w': 4, '12w': 12, '1y': 52 };

function weekBuckets(n) {
  const first = startOfWeek();
  first.setDate(first.getDate() - 7 * (n - 1));
  return Array.from({ length: n }, (_, i) => {
    const start = new Date(first); start.setDate(first.getDate() + 7 * i);
    const end = new Date(start); end.setDate(start.getDate() + 7);
    return { start: start.getTime(), end: end.getTime(), label: fmt.shortDate(start.getTime()) };
  });
}

function perWeek(buckets, items, timeOf, valueOf) {
  return buckets.map((b) => ({ label: b.label, value: items.filter((x) => timeOf(x) >= b.start && timeOf(x) < b.end).reduce((a, x) => a + (valueOf(x) ?? 0), 0) }));
}

export function insightsView() {
  const n = WEEKS[range];
  const buckets = weekBuckets(n);
  const from = buckets[0].start;
  const sessions = rows('training_sessions').filter((s) => s.status === 'COMPLETED' && s.startTime >= from);
  const runs = rows('running_sessions').filter((r) => r.status === 'COMPLETED' && r.startTime >= from);
  const sessionIds = new Set(sessions.map((s) => s.id));

  const byCategory = new Map();
  for (const s of sessions) byCategory.set(s.category, (byCategory.get(s.category) ?? 0) + s.durationMin);
  const techReps = new Map();
  for (const r of rows('technique_records').filter((x) => sessionIds.has(x.sessionId))) {
    const name = byId('techniques', r.techniqueId)?.name ?? '?';
    techReps.set(name, (techReps.get(name) ?? 0) + (r.reps ?? ((r.rightReps ?? 0) + (r.leftReps ?? 0))));
  }

  const fromDay = epochDay(new Date(from));
  const recovery = new Map();
  for (const e of rows('recovery_entries')) {
    if (e.epochDay < fromDay || e.recovery == null) continue;
    const cur = recovery.get(e.epochDay);
    if (!cur || e.updatedAt > cur.updatedAt) recovery.set(e.epochDay, e);
  }
  const recoveryPoints = [...recovery.values()].sort((a, b) => a.epochDay - b.epochDay)
    .map((e) => ({ x: dayToDate(e.epochDay).getTime(), value: e.recovery, label: fmt.date(dayToDate(e.epochDay).getTime()) }));
  const reaction = rows('reaction_tests').filter((r) => r.date >= from && r.averageMs != null).sort((a, b) => a.date - b.date)
    .map((r) => ({ x: r.date, value: r.averageMs, label: `${fmt.date(r.date)} · ${enumLabel('ReactionTestType', r.type)}` }));

  const charts = [];
  if (sessions.length) {
    charts.push(barChart(t('ins_training_minutes'), perWeek(buckets, sessions, (s) => s.startTime, (s) => s.durationMin), { unit: ` ${fmt.minUnit()}` }));
    charts.push(barChart(t('ins_load'), perWeek(buckets, sessions, (s) => s.startTime, sessionLoad)));
    charts.push(rankChart(t('ins_categories'), [...byCategory].sort((a, b) => b[1] - a[1]).map(([k, v]) => ({ label: enumLabel('TrainingCategory', k), value: v }))));
  }
  if (techReps.size) charts.push(rankChart(t('ins_top_tech'), [...techReps].sort((a, b) => b[1] - a[1]).slice(0, 10).map(([label, value]) => ({ label, value }))));
  if (runs.length) charts.push(barChart(t('ins_running_km'), perWeek(buckets, runs, (r) => r.startTime, (r) => r.distanceM / 1000), { format: (v) => fmt.num(v, 1), unit: ' km' }));
  if (recoveryPoints.length) charts.push(lineChart(t('ins_recovery'), recoveryPoints, { min: 1, max: 5, format: (v) => fmt.num(v, 1) }));
  if (reaction.length) charts.push(lineChart(t('ins_reaction'), reaction, { min: 0 }));

  return h('div', { class: 'page' },
    pageHeader(t('nav_insights')),
    h('div', { class: 'row filters' }, h('span', { class: 'muted' }, t('ins_range')),
      tabs([['4w', t('ins_4w')], ['12w', t('ins_12w')], ['1y', t('ins_1y')]], range, (k) => { range = k; location.hash = '#/insights'; window.dispatchEvent(new HashChangeEvent('hashchange')); })),
    charts.length ? h('div', { class: 'grid-2 charts' }, charts) : emptyState());
}
