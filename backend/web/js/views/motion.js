import { api, badge, byId, card, emptyState, field, fmt, h, pageHeader, rows, select, stat, store } from '../core.js';
import { enumLabel, t } from '../i18n.js';

export function motionView() {
  const list = rows('motion_analyses').sort((a, b) => b.createdAt - a.createdAt);
  return h('div', { class: 'page' },
    pageHeader(t('motion_title')),
    h('p', { class: 'muted' }, t('motion_hint')),
    list.length ? h('div', { class: 'table-wrap card' }, h('table', { class: 'table clickable' },
      h('thead', null, h('tr', null, [t('match_date'), t('motion_technique'), t('session_status'), t('motion_reps')].map((c) => h('th', null, c)))),
      h('tbody', null, list.map((a) => {
        let reps = t('none');
        try { reps = JSON.parse(a.resultJson).repetitions?.length ?? t('none'); } catch { /* ignore */ }
        return h('tr', { onclick: () => { location.hash = `#/motion/${a.id}`; } },
          h('td', null, h('a', { href: `#/motion/${a.id}` }, fmt.dateTime(a.createdAt))), h('td', null, a.techniqueName),
          h('td', null, enumLabel('AnalysisStatus', a.status)), h('td', null, reps));
      })))) : emptyState(),
    serverAnalysisCard());
}

function resultView(r) {
  if (!r) return emptyState();
  if (!r.quality?.passed) {
    return h('div', null, h('p', { class: 'notice' }, t('motion_quality_fail')),
      h('ul', null, (r.quality?.blocking ?? []).map((b) => h('li', null, b))));
  }
  const metric = (label, m, d = 1) => (m ? stat(label, `${fmt.num(m.mean, d)} (${fmt.num(m.min, d)}–${fmt.num(m.max, d)})`, badge('estimated')) : null);
  return h('div', null,
    (r.quality.warnings ?? []).length ? h('p', { class: 'muted small' }, r.quality.warnings.join(' · ')) : null,
    (r.sides ?? []).map((s) => card(`${enumLabel('BodySide', s.side)} · ${t('motion_reps')}: ${s.repetitions}`,
      h('div', { class: 'stats' },
        metric(t('motion_ankle'), s.peakAnkleHeight, 2), metric(t('motion_knee'), s.kickKneeAngleAtPeak), metric(t('motion_support'), s.supportKneeAngleAtPeak),
        metric(t('motion_trunk'), s.trunkLeanAtPeak), metric(t('motion_ttp'), s.timeToPeakMs, 0)))),
    (r.notes ?? []).length ? h('ul', { class: 'muted small' }, r.notes.map((n) => h('li', null, n))) : null);
}

export function motionDetailView(id) {
  const a = byId('motion_analyses', id);
  if (!a) return h('div', { class: 'page' }, emptyState());
  let r = null;
  try { r = JSON.parse(a.resultJson); } catch { /* ignore */ }
  return h('div', { class: 'page' },
    pageHeader(a.techniqueName, h('a', { href: '#/motion', class: 'btn ghost' }, t('back'))),
    h('p', { class: 'muted' }, `${fmt.dateTime(a.createdAt)} · ${enumLabel('AnalysisStatus', a.status)}`),
    h('p', { class: 'muted small' }, t('motion_hint')),
    resultView(r),
    a.aiInterpretation ? card(t('motion_ai'), badge('ai'), h('div', { class: 'prewrap' }, a.aiInterpretation)) : null,
    a.notes ? card(t('session_notes'), h('p', { class: 'prewrap' }, a.notes)) : null);
}

function serverAnalysisCard() {
  if (!store.info?.motion_available) return card(t('motion_server'), h('p', { class: 'muted' }, t('motion_server_off')));
  const out = h('div');
  const file = h('input', { type: 'file', accept: 'video/*', name: 'video', required: true });
  const technique = select('technique', rows('techniques').filter((x) => x.category === 'KICK').map((x) => [x.name, x.name]), '');
  const side = select('side', [['', t('motion_auto')], ['RIGHT', enumLabel('BodySide', 'RIGHT')], ['LEFT', enumLabel('BodySide', 'LEFT')]], '');
  const form = h('form', { class: 'form' }, field(t('motion_technique'), technique), field(t('motion_side'), side), field('Video', file),
    h('button', { class: 'btn primary', type: 'submit' }, t('motion_upload')), out);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    out.replaceChildren(h('p', { class: 'muted' }, t('motion_working')));
    try {
      const res = await api('/motion/analyze', { method: 'POST', form: new FormData(form) });
      out.replaceChildren(resultView(res.result));
    } catch (err) {
      out.replaceChildren(h('p', { class: 'form-error' }, err.message));
    }
  });
  return card(t('motion_server'), form);
}
