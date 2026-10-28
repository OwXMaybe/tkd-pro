import {
  badge, card, dayToDate, emptyState, epochDay, field, fmt, formDialog, fromLocalInput, h, input, inRange, intOrNull, numOrNull,
  pageHeader, remove, required, rows, save, select, tabs, textarea, toLocalInput,
} from '../core.js';
import { enumLabel, enumValues, PERF_TESTS, t } from '../i18n.js';

export function performanceView(tab) {
  const views = { recovery: recoveryTab, tests: testsTab, reaction: reactionTab };
  const action = tab === 'recovery' ? h('button', { class: 'btn primary', onclick: () => recoveryDialog(null) }, t('rec_new'))
    : tab === 'tests' ? h('button', { class: 'btn primary', onclick: () => metricDialog(null) }, t('perf_new_metric')) : null;
  return h('div', { class: 'page' },
    pageHeader(t('nav_performance'), action),
    tabs([['recovery', t('perf_recovery')], ['tests', t('perf_tests')], ['reaction', t('perf_reaction')]], tab, (k) => { location.hash = `#/performance/${k}`; }),
    (views[tab] ?? recoveryTab)());
}

// ---------- Recovery ----------
function latestPerDay() {
  const byDay = new Map();
  for (const e of rows('recovery_entries')) {
    const cur = byDay.get(e.epochDay);
    if (!cur || e.updatedAt > cur.updatedAt) byDay.set(e.epochDay, e);
  }
  return [...byDay.values()].sort((a, b) => b.epochDay - a.epochDay);
}

function recoveryTab() {
  const list = latestPerDay();
  if (!list.length) return emptyState();
  return h('div', { class: 'table-wrap card' }, h('p', { class: 'muted small' }, `${t('rec_scale')} · `, badge('manual')), h('table', { class: 'table clickable' },
    h('thead', null, h('tr', null, [t('rec_day'), t('rec_sleep_h'), t('rec_sleep_q'), t('rec_energy'), t('rec_fatigue'), t('rec_soreness'), t('rec_recovery'), t('rec_rhr')].map((c) => h('th', null, c)))),
    h('tbody', null, list.map((e) => h('tr', { onclick: () => recoveryDialog(e) },
      h('td', null, fmt.weekday(dayToDate(e.epochDay).getTime())), h('td', null, fmt.num(e.sleepHours, 1)),
      ...['sleepQuality', 'energy', 'fatigue', 'soreness', 'recovery', 'restingHeartRate'].map((k) => h('td', null, e[k] ?? t('none'))))))));
}

const scale = (name, label, value) => field(label, h('div', { class: 'scale', role: 'radiogroup', 'aria-label': label },
  ['', 1, 2, 3, 4, 5].map((v) => h('label', { class: 'scale-opt' },
    h('input', { type: 'radio', name, value: v, checked: String(value ?? '') === String(v) ? true : null }),
    h('span', null, v === '' ? '–' : v)))));

export function recoveryDialog(entry) {
  const day = entry?.epochDay ?? epochDay();
  formDialog(t('rec_new'), [
    field(t('rec_day'), input('day', toLocalInput(dayToDate(day).getTime(), false), { type: 'date', required: true })),
    h('div', { class: 'grid-2' },
      field(t('rec_sleep_h'), input('sleepHours', entry?.sleepHours, { type: 'number', min: 0, max: 24, step: 0.25 })),
      field(t('rec_rhr'), input('restingHeartRate', entry?.restingHeartRate, { type: 'number', min: 25, max: 150 }))),
    h('p', { class: 'muted small' }, t('rec_scale')),
    scale('sleepQuality', t('rec_sleep_q'), entry?.sleepQuality),
    scale('energy', t('rec_energy'), entry?.energy),
    scale('fatigue', t('rec_fatigue'), entry?.fatigue),
    scale('soreness', t('rec_soreness'), entry?.soreness),
    scale('recovery', t('rec_recovery'), entry?.recovery),
    field(t('session_notes'), textarea('notes', entry?.notes)),
  ], async (v) => {
    const d = new Date(`${v.day}T12:00`);
    const chosenDay = epochDay(d);
    // One check-in per day: editing an existing day keeps its record.
    const existing = entry?.epochDay === chosenDay ? entry : latestPerDay().find((e) => e.epochDay === chosenDay);
    const now = Date.now();
    await save('recovery_entries', {
      ...(existing ?? { createdAt: now }), epochDay: chosenDay,
      sleepHours: inRange(numOrNull(v.sleepHours), 0, 24, t('rec_sleep_h')),
      sleepQuality: intOrNull(v.sleepQuality), energy: intOrNull(v.energy), fatigue: intOrNull(v.fatigue),
      soreness: intOrNull(v.soreness), recovery: intOrNull(v.recovery),
      restingHeartRate: inRange(intOrNull(v.restingHeartRate), 25, 150, t('rec_rhr')), notes: v.notes ?? '', updatedAt: now,
    });
  }, entry ? { onDelete: () => remove('recovery_entries', entry.id) } : {});
}

// ---------- Performance tests ----------
function testsTab() {
  const list = rows('performance_metrics').sort((a, b) => b.date - a.date);
  if (!list.length) return emptyState();
  const bestByType = new Map();
  for (const m of list) {
    const higher = PERF_TESTS[m.testType]?.[1] ?? true;
    const cur = bestByType.get(m.testType);
    if (!cur || (higher ? m.value > cur.value : m.value < cur.value)) bestByType.set(m.testType, m);
  }
  return h('div', null,
    h('div', { class: 'cards' }, [...bestByType.values()].map((m) => h('div', { class: 'card tile' },
      h('span', { class: 'muted small' }, enumLabel('PerformanceTestType', m.testType)),
      h('strong', { class: 'big' }, `${fmt.num(m.value, 1)} ${m.unit}`),
      h('span', { class: 'small' }, `${t('perf_best')} · ${fmt.date(m.date)}`)))),
    h('div', { class: 'table-wrap card' }, h('table', { class: 'table clickable' },
      h('thead', null, h('tr', null, [t('match_date'), t('perf_test'), t('perf_value'), t('rec_side'), t('perf_method')].map((c) => h('th', null, c)))),
      h('tbody', null, list.map((m) => h('tr', { onclick: () => metricDialog(m) },
        h('td', null, fmt.date(m.date)), h('td', null, enumLabel('PerformanceTestType', m.testType)), h('td', null, `${fmt.num(m.value, 1)} ${m.unit}`),
        h('td', null, m.side ? enumLabel('BodySide', m.side) : t('none')), h('td', null, m.method || t('none'))))))));
}

function metricDialog(m) {
  const types = enumValues('PerformanceTestType');
  formDialog(m ? enumLabel('PerformanceTestType', m.testType) : t('perf_new_metric'), [
    field(t('perf_test'), select('testType', types.map((v) => [v, `${enumLabel('PerformanceTestType', v)} (${PERF_TESTS[v][0]})`]), m?.testType ?? 'SIT_AND_REACH')),
    h('div', { class: 'grid-3' },
      field(t('match_date'), input('date', toLocalInput(m?.date ?? Date.now(), false), { type: 'date', required: true })),
      field(t('perf_value'), input('value', m?.value, { type: 'number', step: 0.1, required: true })),
      field(t('rec_side'), select('side', [['', t('none')], ...enumValues('BodySide').map((v) => [v, enumLabel('BodySide', v)])], m?.side ?? ''))),
    field(t('perf_method'), input('method', m?.method, { maxlength: 80, required: true }), t('perf_method_hint')),
    field(t('session_notes'), textarea('notes', m?.notes)),
  ], async (v) => {
    const value = numOrNull(v.value);
    if (value == null) throw new Error(`${t('perf_value')}: ?`);
    await save('performance_metrics', {
      ...(m ?? { createdAt: Date.now() }), testType: v.testType, date: fromLocalInput(`${v.date}T12:00`), value,
      unit: PERF_TESTS[v.testType][0], side: v.side || null, method: required(v.method, t('perf_method')), notes: v.notes ?? '',
    });
  }, m ? { onDelete: () => remove('performance_metrics', m.id) } : {});
}

// ---------- Reaction ----------
function reactionTab() {
  const list = rows('reaction_tests').sort((a, b) => b.date - a.date);
  return h('div', null,
    h('p', { class: 'muted' }, t('perf_reaction_phone')),
    list.length ? h('div', { class: 'table-wrap card' }, h('table', { class: 'table' },
      h('thead', null, h('tr', null, [t('match_date'), t('perf_test'), t('perf_avg'), t('perf_best'), t('perf_valid'), t('perf_device')].map((c) => h('th', null, c)))),
      h('tbody', null, list.map((r) => h('tr', { title: r.conditions },
        h('td', null, fmt.dateTime(r.date)), h('td', null, enumLabel('ReactionTestType', r.type)),
        h('td', null, r.averageMs != null ? `${Math.round(r.averageMs)} ms` : t('none')), h('td', null, r.bestMs != null ? `${r.bestMs} ms` : t('none')),
        h('td', null, `${r.validTrials}/${r.totalTrials}`), h('td', null, r.deviceModel)))))) : emptyState());
}
