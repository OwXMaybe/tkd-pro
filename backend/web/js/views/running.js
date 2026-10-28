import { badge, byId, card, emptyState, field, fmt, formDialog, h, input, intOrNull, pageHeader, rows, save, stat, textarea } from '../core.js';
import { enumLabel, t } from '../i18n.js';
import { routeMap } from '../map.js';

export function runningView() {
  const runs = rows('running_sessions').filter((r) => r.status === 'COMPLETED').sort((a, b) => b.startTime - a.startTime);
  return h('div', { class: 'page' },
    pageHeader(t('nav_running')),
    h('p', { class: 'muted' }, t('run_record_on_phone')),
    runs.length ? h('div', { class: 'table-wrap card' }, h('table', { class: 'table clickable' },
      h('thead', null, h('tr', null, [t('match_date'), t('session_category'), t('run_distance'), t('run_time'), t('run_pace'), t('run_elev')].map((c) => h('th', null, c)))),
      h('tbody', null, runs.map((r) => h('tr', { onclick: () => { location.hash = `#/running/${r.id}`; } },
        h('td', null, h('a', { href: `#/running/${r.id}` }, fmt.dateTime(r.startTime))), h('td', null, r.customName || enumLabel('RunType', r.type)),
        h('td', null, fmt.km(r.distanceM)), h('td', null, fmt.duration(r.movingTimeMs)), h('td', null, fmt.pace(r.distanceM, r.movingTimeMs)),
        h('td', null, r.elevationGainM != null ? `${Math.round(r.elevationGainM)} m` : t('none'))))))) : emptyState());
}

export function runDetailView(id) {
  const r = byId('running_sessions', id);
  if (!r) return h('div', { class: 'page' }, emptyState());
  const route = byId('run_routes', r.id);
  const best = [['1 km', r.best1kMs], ['5 km', r.best5kMs], ['10 km', r.best10kMs]].filter(([, v]) => v != null);
  return h('div', { class: 'page' },
    pageHeader(r.customName || enumLabel('RunType', r.type), h('a', { href: '#/running', class: 'btn ghost' }, t('back')), h('button', { class: 'btn', onclick: () => runDialog(r) }, t('edit'))),
    card(fmt.dateTime(r.startTime), h('div', { class: 'stats' },
      stat(t('run_distance'), fmt.km(r.distanceM), badge('measured')),
      stat(t('run_time'), fmt.duration(r.movingTimeMs)),
      stat(t('run_pace'), fmt.pace(r.distanceM, r.movingTimeMs)),
      stat(t('run_elev'), r.elevationGainM != null ? `${Math.round(r.elevationGainM)} m` : t('none')),
      stat(t('run_hr'), r.avgHeartRate ?? t('none'), r.avgHeartRate ? badge('manual') : null)),
      r.notes ? h('p', { class: 'prewrap' }, r.notes) : null),
    card(null, route?.points?.length >= 2 ? routeMap(route.points) : emptyState(t('run_no_route'))),
    h('div', { class: 'grid-2' },
      card(t('run_splits'), route?.splits?.length ? h('div', { class: 'table-wrap' }, h('table', { class: 'table' },
        h('thead', null, h('tr', null, h('th', null, 'km'), h('th', null, t('run_time')), h('th', null, t('run_pace')), h('th', null, t('run_elev')))),
        h('tbody', null, route.splits.map((s) => h('tr', null, h('td', null, s.idx + 1), h('td', null, fmt.duration(s.durationMs)),
          h('td', null, fmt.pace(s.distanceM, s.durationMs)), h('td', null, `${Math.round(s.elevationGainM)} m`)))))) : emptyState()),
      card(t('run_best'), best.length ? h('div', { class: 'stats' }, best.map(([l, v]) => stat(l, fmt.duration(v)))) : emptyState())));
}

function runDialog(r) {
  formDialog(r.customName || enumLabel('RunType', r.type), [
    h('div', { class: 'grid-2' },
      field(t('run_hr_manual'), input('avgHeartRate', r.avgHeartRate, { type: 'number', min: 30, max: 240 })),
      field(t('run_maxhr_manual'), input('maxHeartRate', r.maxHeartRate, { type: 'number', min: 30, max: 240 }))),
    field(t('run_notes'), textarea('notes', r.notes)),
  ], async (v) => {
    await save('running_sessions', { ...r, avgHeartRate: intOrNull(v.avgHeartRate), maxHeartRate: intOrNull(v.maxHeartRate), notes: v.notes ?? '', updatedAt: Date.now() });
  });
}
