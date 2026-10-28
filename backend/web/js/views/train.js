import {
  badge, byId, card, emptyState, field, fmt, formDialog, fromLocalInput, h, inRange, input, intOrNull, numOrNull, pageHeader,
  remove, required, rows, save, saveKeyed, select, sessionLoad, stat, tabs, textarea, toLocalInput,
} from '../core.js';
import { enumLabel, enumValues, t } from '../i18n.js';

const opts = (type) => enumValues(type).map((v) => [v, enumLabel(type, v)]);
const optsWithNone = (type) => [['', t('none')], ...opts(type)];
const techniqueName = (id) => byId('techniques', id)?.name ?? '?';
const totalReps = (r) => r.reps ?? ((r.rightReps ?? 0) + (r.leftReps ?? 0));

export function trainView(tab) {
  const views = { sessions: sessionsTab, techniques: techniquesTab, combinations: combinationsTab, matches: matchesTab, competitions: competitionsTab };
  const actions = {
    sessions: () => sessionDialog(null), techniques: () => techniqueDialog(null), combinations: () => combinationDialog(null),
    matches: () => matchDialog(null), competitions: () => competitionDialog(null),
  };
  const addLabel = { sessions: 'session_new', techniques: 'tech_new', combinations: 'combo_new', matches: 'match_new', competitions: 'comp_new' }[tab];
  return h('div', { class: 'page' },
    pageHeader(t('nav_train'), h('button', { class: 'btn primary', onclick: actions[tab] }, t(addLabel))),
    tabs([['sessions', t('train_sessions')], ['techniques', t('train_techniques')], ['combinations', t('train_combinations')], ['matches', t('train_matches')], ['competitions', t('train_competitions')]],
      tab, (k) => { location.hash = `#/train/${k}`; }),
    (views[tab] ?? sessionsTab)());
}

// ---------- Sessions ----------
function sessionsTab() {
  const list = rows('training_sessions').sort((a, b) => b.startTime - a.startTime);
  if (!list.length) return emptyState();
  return h('div', { class: 'table-wrap card' }, h('table', { class: 'table clickable' },
    h('thead', null, h('tr', null, [t('session_start'), t('session_name'), t('session_category'), t('session_duration'), t('session_rpe'), t('home_load'), t('session_status')].map((c) => h('th', null, c)))),
    h('tbody', null, list.map((s) => h('tr', { onclick: () => { location.hash = `#/train/session/${s.id}`; } },
      h('td', null, fmt.dateTime(s.startTime)), h('td', null, h('a', { href: `#/train/session/${s.id}` }, s.name)),
      h('td', null, enumLabel('TrainingCategory', s.category)), h('td', null, s.durationMin), h('td', null, s.rpe ?? t('none')),
      h('td', null, sessionLoad(s) ?? t('none')), h('td', null, enumLabel('SessionStatus', s.status)))))));
}

export function sessionDialog(s) {
  const now = Date.now();
  formDialog(s ? t('session_edit') : t('session_new'), [
    field(t('session_name'), input('name', s?.name, { required: true, maxlength: 120 })),
    h('div', { class: 'grid-2' },
      field(t('session_category'), select('category', opts('TrainingCategory'), s?.category ?? 'TECHNICAL')),
      field(t('session_status'), select('status', opts('SessionStatus'), s?.status ?? 'COMPLETED'))),
    h('div', { class: 'grid-2' },
      field(t('session_start'), input('startTime', toLocalInput(s?.startTime ?? now), { type: 'datetime-local', required: true })),
      field(t('session_duration'), input('durationMin', s?.durationMin ?? 60, { type: 'number', min: 1, max: 600, required: true }))),
    h('div', { class: 'grid-3' },
      field(t('session_difficulty'), input('difficulty', s?.difficulty, { type: 'number', min: 1, max: 5 }), t('optional')),
      field(t('session_rpe'), input('rpe', s?.rpe, { type: 'number', min: 1, max: 10 }), t('optional')),
      field(t('session_location'), input('location', s?.location, { maxlength: 120 }))),
    field(t('session_notes'), textarea('notes', s?.notes)),
  ], async (v) => {
    const startTime = fromLocalInput(v.startTime);
    const name = required(v.name, t('session_name'));
    const duplicate = rows('training_sessions').some((o) => o.id !== s?.id && o.name === name && o.startTime === startTime && o.category === v.category);
    if (duplicate) throw new Error(t('session_duplicate'));
    await save('training_sessions', {
      ...(s ?? { createdAt: now }), name, category: v.category, status: v.status, startTime,
      durationMin: inRange(intOrNull(v.durationMin), 1, 600, t('session_duration')),
      difficulty: inRange(intOrNull(v.difficulty), 1, 5, t('session_difficulty')),
      rpe: inRange(intOrNull(v.rpe), 1, 10, t('session_rpe')),
      location: v.location?.trim() || null, notes: v.notes ?? '', updatedAt: Date.now(),
    });
  }, s ? { onDelete: () => deleteSession(s) } : {});
}

async function deleteSession(s) {
  const records = rows('technique_records').filter((r) => r.sessionId === s.id).map((r) => r.id);
  await remove('training_sessions', s.id, [['technique_records', records]]);
  location.hash = '#/train/sessions';
}

export function sessionDetailView(id) {
  const s = byId('training_sessions', id);
  if (!s) return h('div', { class: 'page' }, emptyState());
  const records = rows('technique_records').filter((r) => r.sessionId === s.id);
  return h('div', { class: 'page' },
    pageHeader(s.name,
      h('a', { href: '#/train/sessions', class: 'btn ghost' }, t('back')),
      h('button', { class: 'btn', onclick: () => sessionDialog(s) }, t('edit'))),
    card(null, h('div', { class: 'stats' },
      stat(t('session_start'), fmt.dateTime(s.startTime)),
      stat(t('session_category'), enumLabel('TrainingCategory', s.category)),
      stat(t('session_duration'), fmt.minutes(s.durationMin)),
      stat(t('session_rpe'), s.rpe ?? t('none'), badge('manual')),
      stat(t('home_load'), sessionLoad(s) ?? t('none'), badge('estimated'))),
      s.location ? h('p', { class: 'muted' }, s.location) : null,
      s.notes ? h('p', { class: 'prewrap' }, s.notes) : null),
    card(t('session_techniques'),
      records.length ? h('div', { class: 'table-wrap' }, h('table', { class: 'table clickable' },
        h('thead', null, h('tr', null, [t('rec_technique'), t('rec_sets'), t('rec_reps'), t('rec_right'), t('rec_left'), t('rec_side')].map((c) => h('th', null, c)))),
        h('tbody', null, records.map((r) => h('tr', { onclick: () => recordDialog(s, r) },
          h('td', null, techniqueName(r.techniqueId)), h('td', null, r.sets ?? t('none')), h('td', null, totalReps(r)),
          h('td', null, r.rightReps ?? t('none')), h('td', null, r.leftReps ?? t('none')), h('td', null, enumLabel('BodySide', r.side))))))) : emptyState(),
      h('button', { class: 'btn', onclick: () => recordDialog(s, null) }, t('session_add_technique'))));
}

function techniqueOptions() {
  return rows('techniques').filter((x) => !x.archived).sort((a, b) => a.name.localeCompare(b.name)).map((x) => [x.id, x.name]);
}

function recordDialog(session, r) {
  formDialog(t('session_add_technique'), [
    field(t('rec_technique'), select('techniqueId', techniqueOptions(), r?.techniqueId, { required: true })),
    h('div', { class: 'grid-3' },
      field(t('rec_sets'), input('sets', r?.sets, { type: 'number', min: 0, max: 100 })),
      field(t('rec_right'), input('rightReps', r?.rightReps, { type: 'number', min: 0, max: 10000 })),
      field(t('rec_left'), input('leftReps', r?.leftReps, { type: 'number', min: 0, max: 10000 }))),
    h('div', { class: 'grid-3' },
      field(t('rec_reps'), input('reps', r?.reps, { type: 'number', min: 0, max: 10000 }), t('optional')),
      field(t('rec_side'), select('side', opts('BodySide'), r?.side ?? 'BOTH')),
      field(t('rec_duration'), input('durationSec', r?.durationSec, { type: 'number', min: 0 }))),
    field(t('session_notes'), textarea('notes', r?.notes)),
  ], async (v) => {
    if (!v.techniqueId) throw new Error(`${t('rec_technique')}: ?`);
    await save('technique_records', {
      ...(r ?? { createdAt: Date.now() }), sessionId: session.id, techniqueId: Number(v.techniqueId),
      sets: intOrNull(v.sets), reps: intOrNull(v.reps), rightReps: intOrNull(v.rightReps), leftReps: intOrNull(v.leftReps),
      side: v.side, durationSec: intOrNull(v.durationSec), notes: v.notes ?? '', coachFeedback: r?.coachFeedback ?? '',
    });
  }, r ? { onDelete: () => remove('technique_records', r.id) } : {});
}

// ---------- Techniques ----------
function techniquesTab() {
  const usage = new Map();
  for (const r of rows('technique_records')) {
    const u = usage.get(r.techniqueId) ?? { sessions: new Set(), reps: 0 };
    u.sessions.add(r.sessionId); u.reps += totalReps(r);
    usage.set(r.techniqueId, u);
  }
  const list = rows('techniques').filter((x) => !x.archived).sort((a, b) => a.name.localeCompare(b.name));
  if (!list.length) return emptyState();
  return h('div', { class: 'cards' }, list.map((x) => {
    const u = usage.get(x.id);
    return h('button', { class: 'card tile', onclick: () => techniqueDialog(x) },
      h('div', { class: 'row between' }, h('strong', null, x.name), x.builtInKey ? h('span', { class: 'chip' }, t('tech_builtin')) : null),
      h('span', { class: 'muted small' }, enumLabel('TechniqueCategory', x.category)),
      u ? h('span', { class: 'small' }, t('tech_usage', { n: u.sessions.size, r: u.reps })) : null);
  }));
}

function techniqueDialog(x) {
  formDialog(x ? x.name : t('tech_new'), [
    field(t('tech_name'), input('name', x?.name, { required: true, maxlength: 80, readonly: x?.builtInKey ? true : null })),
    field(t('tech_category'), select('category', opts('TechniqueCategory'), x?.category ?? 'KICK')),
    field(t('tech_description'), textarea('description', x?.description)),
    field(t('tech_notes'), textarea('notes', x?.notes)),
    field(t('tech_coach'), textarea('coachFeedback', x?.coachFeedback)),
  ], async (v) => {
    const name = required(v.name, t('tech_name'));
    if (rows('techniques').some((o) => o.id !== x?.id && o.name.toLowerCase() === name.toLowerCase())) throw new Error(t('session_duplicate'));
    const now = Date.now();
    await save('techniques', {
      ...(x ?? { createdAt: now, builtInKey: null, archived: false }), name, category: v.category,
      description: v.description ?? '', notes: v.notes ?? '', coachFeedback: v.coachFeedback ?? '', updatedAt: now,
    });
  });
}

// ---------- Combinations ----------
function combinationsTab() {
  const list = rows('combinations').sort((a, b) => b.updatedAt - a.updatedAt);
  if (!list.length) return emptyState();
  return h('div', { class: 'cards' }, list.map((c) => {
    const steps = byId('combination_steps', c.id)?.steps ?? [];
    return h('a', { class: 'card tile', href: `#/train/combination/${c.id}` },
      h('strong', null, c.name),
      h('span', { class: 'small' }, steps.map((s) => `${techniqueName(s.techniqueId)} (${enumLabel('BodySide', s.side)})`).join(' → ') || t('none')));
  }));
}

function combinationDialog(c) {
  const existing = c ? (byId('combination_steps', c.id)?.steps ?? []) : [];
  const stepsBox = h('div', { class: 'steps' });
  const addStep = (s) => {
    const row = h('div', { class: 'row step' },
      select('stepTechnique', techniqueOptions(), s?.techniqueId),
      select('stepSide', opts('BodySide'), s?.side ?? 'RIGHT'),
      h('button', { type: 'button', class: 'btn ghost small', onclick: () => row.remove(), 'aria-label': t('delete') }, '×'));
    stepsBox.append(row);
  };
  (existing.length ? existing : [null]).forEach(addStep);
  formDialog(c ? c.name : t('combo_new'), [
    field(t('session_name'), input('name', c?.name, { required: true, maxlength: 80 })),
    h('div', { class: 'field' }, h('span', { class: 'field-label' }, t('combo_steps')), stepsBox,
      h('button', { type: 'button', class: 'btn ghost small', onclick: () => addStep(null) }, t('combo_add_step'))),
    field(t('session_notes'), textarea('notes', c?.notes)),
  ], async (v, form) => {
    const steps = [...form.querySelectorAll('.step')].map((row) => ({
      techniqueId: Number(row.querySelector('[name=stepTechnique]').value), side: row.querySelector('[name=stepSide]').value,
    })).filter((s) => s.techniqueId);
    if (!steps.length) throw new Error(t('combo_steps'));
    const now = Date.now();
    const saved = await save('combinations', { ...(c ?? { createdAt: now, videoAssetId: null }), name: required(v.name, t('session_name')), notes: v.notes ?? '', updatedAt: now });
    await saveKeyed('combination_steps', saved.id, { steps });
  }, c ? {
    onDelete: async () => {
      const practices = rows('combination_practices').filter((p) => p.combinationId === c.id).map((p) => p.id);
      await remove('combinations', c.id, [['combination_practices', practices], ['combination_steps', [c.id]]]);
      location.hash = '#/train/combinations';
    },
  } : {});
}

export function combinationDetailView(id) {
  const c = byId('combinations', id);
  if (!c) return h('div', { class: 'page' }, emptyState());
  const steps = byId('combination_steps', c.id)?.steps ?? [];
  const practices = rows('combination_practices').filter((p) => p.combinationId === c.id).sort((a, b) => b.date - a.date);
  return h('div', { class: 'page' },
    pageHeader(c.name, h('a', { href: '#/train/combinations', class: 'btn ghost' }, t('back')), h('button', { class: 'btn', onclick: () => combinationDialog(c) }, t('edit'))),
    card(t('combo_steps'), h('ol', { class: 'list numbered' }, steps.map((s) => h('li', null, `${techniqueName(s.techniqueId)} · ${enumLabel('BodySide', s.side)}`))),
      c.notes ? h('p', { class: 'prewrap' }, c.notes) : null),
    card(t('combo_practices'),
      practices.length ? h('ul', { class: 'list' }, practices.map((p) => h('li', { class: 'list-item' }, h('span', null, fmt.date(p.date)), h('span', null, `${p.reps} × `), h('span', { class: 'muted small' }, p.notes)))) : emptyState(),
      h('button', { class: 'btn', onclick: () => practiceDialog(c) }, t('combo_practice_add'))));
}

function practiceDialog(c) {
  formDialog(t('combo_practice_add'), [
    h('div', { class: 'grid-2' },
      field(t('match_date'), input('date', toLocalInput(Date.now(), false), { type: 'date', required: true })),
      field(t('combo_reps'), input('reps', 10, { type: 'number', min: 1, max: 10000, required: true }))),
    field(t('session_notes'), textarea('notes', '')),
  ], async (v) => {
    await save('combination_practices', { combinationId: c.id, sessionId: null, date: fromLocalInput(`${v.date}T12:00`), reps: intOrNull(v.reps), notes: v.notes ?? '', videoAssetId: null, createdAt: Date.now() });
  });
}

// ---------- Matches ----------
function matchesTab() {
  const list = rows('matches').sort((a, b) => b.date - a.date);
  if (!list.length) return emptyState();
  return h('div', { class: 'table-wrap card' }, h('table', { class: 'table clickable' },
    h('thead', null, h('tr', null, [t('match_date'), t('match_competition'), t('match_opponent'), t('match_stage'), t('match_result')].map((c) => h('th', null, c)))),
    h('tbody', null, list.map((m) => h('tr', { onclick: () => { location.hash = `#/train/match/${m.id}`; } },
      h('td', null, fmt.date(m.date)), h('td', null, h('a', { href: `#/train/match/${m.id}` }, m.competitionName)), h('td', null, m.opponent || t('none')),
      h('td', null, m.stage || t('none')),
      h('td', null, `${enumLabel('MatchResult', m.result)}${m.scoreFor != null ? ` ${m.scoreFor}–${m.scoreAgainst ?? '?'}` : ''}`))))));
}

function matchDialog(m) {
  formDialog(m ? m.competitionName : t('match_new'), [
    h('div', { class: 'grid-2' },
      field(t('match_competition'), input('competitionName', m?.competitionName, { required: true, maxlength: 120 })),
      field(t('match_date'), input('date', toLocalInput(m?.date ?? Date.now(), false), { type: 'date', required: true }))),
    h('div', { class: 'grid-3' },
      field(t('match_opponent'), input('opponent', m?.opponent, { maxlength: 80 })),
      field(t('match_category'), input('category', m?.category, { maxlength: 60 })),
      field(t('match_stage'), input('stage', m?.stage, { maxlength: 60 }))),
    h('div', { class: 'grid-3' },
      field(t('match_result'), select('result', opts('MatchResult'), m?.result ?? 'NOT_RECORDED')),
      field(t('match_score_for'), input('scoreFor', m?.scoreFor, { type: 'number', min: 0 })),
      field(t('match_score_against'), input('scoreAgainst', m?.scoreAgainst, { type: 'number', min: 0 }))),
    field(t('match_strengths'), textarea('strengths', m?.strengths)),
    field(t('match_improvements'), textarea('improvements', m?.improvements)),
    field(t('session_notes'), textarea('notes', m?.notes)),
  ], async (v) => {
    const now = Date.now();
    await save('matches', {
      ...(m ?? { createdAt: now, durationSec: null, videoAssetId: null }), competitionName: required(v.competitionName, t('match_competition')),
      date: fromLocalInput(`${v.date}T12:00`), opponent: v.opponent ?? '', category: v.category ?? '', stage: v.stage ?? '', result: v.result,
      scoreFor: intOrNull(v.scoreFor), scoreAgainst: intOrNull(v.scoreAgainst), strengths: v.strengths ?? '', improvements: v.improvements ?? '',
      notes: v.notes ?? '', updatedAt: now,
    });
  }, m ? {
    onDelete: async () => {
      const rounds = rows('match_rounds').filter((r) => r.matchId === m.id).map((r) => r.id);
      const events = rows('match_events').filter((e) => e.matchId === m.id).map((e) => e.id);
      await remove('matches', m.id, [['match_rounds', rounds], ['match_events', events]]);
      location.hash = '#/train/matches';
    },
  } : {});
}

export function matchDetailView(id) {
  const m = byId('matches', id);
  if (!m) return h('div', { class: 'page' }, emptyState());
  const rounds = rows('match_rounds').filter((r) => r.matchId === m.id).sort((a, b) => a.number - b.number);
  const cols = roundColumns();
  return h('div', { class: 'page' },
    pageHeader(m.competitionName, h('a', { href: '#/train/matches', class: 'btn ghost' }, t('back')), h('button', { class: 'btn', onclick: () => matchDialog(m) }, t('edit'))),
    card(null, h('div', { class: 'stats' },
      stat(t('match_date'), fmt.date(m.date)), stat(t('match_opponent'), m.opponent || t('none')), stat(t('match_stage'), m.stage || t('none')),
      stat(t('match_result'), `${enumLabel('MatchResult', m.result)}${m.scoreFor != null ? ` ${m.scoreFor}–${m.scoreAgainst ?? '?'}` : ''}`)),
      m.strengths ? h('p', null, h('strong', null, `${t('match_strengths')}: `), m.strengths) : null,
      m.improvements ? h('p', null, h('strong', null, `${t('match_improvements')}: `), m.improvements) : null,
      m.notes ? h('p', { class: 'prewrap' }, m.notes) : null),
    card(t('match_rounds'), badge('manual'),
      rounds.length ? h('div', { class: 'table-wrap' }, h('table', { class: 'table clickable' },
        h('thead', null, h('tr', null, h('th', null, '#'), cols.map(([, l]) => h('th', null, l)))),
        h('tbody', null, rounds.map((r) => h('tr', { onclick: () => roundDialog(m, r, rounds) }, h('td', null, r.number), cols.map(([k]) => h('td', null, r[k] ?? t('none')))))))) : emptyState(),
      h('button', { class: 'btn', onclick: () => roundDialog(m, null, rounds) }, t('add'))));
}

function roundColumns() {
  return [['attackAttempts', t('round_attacks')], ['successfulAttacks', t('round_success')], ['defensiveActions', t('round_defence')],
    ['counterattacks', t('round_counter')], ['pointsFor', t('match_score_for')], ['pointsAgainst', t('match_score_against')]];
}

function roundDialog(m, r, rounds) {
  const number = r?.number ?? (Math.max(0, ...rounds.map((x) => x.number)) + 1);
  const fields = roundColumns().map(([name, label]) => field(label, input(name, r?.[name], { type: 'number', min: 0 })));
  formDialog(t('match_round', { n: number }), [
    h('div', { class: 'grid-3' }, fields.slice(0, 3)),
    h('div', { class: 'grid-3' }, fields.slice(3)),
    field(t('round_tactical'), textarea('tacticalNotes', r?.tacticalNotes)),
  ], async (v) => {
    await save('match_rounds', {
      ...(r ?? { combinationsObserved: '', timingDistanceNotes: '' }), matchId: m.id, number,
      attackAttempts: intOrNull(v.attackAttempts), successfulAttacks: intOrNull(v.successfulAttacks), defensiveActions: intOrNull(v.defensiveActions),
      counterattacks: intOrNull(v.counterattacks), pointsFor: intOrNull(v.pointsFor), pointsAgainst: intOrNull(v.pointsAgainst), tacticalNotes: v.tacticalNotes ?? '',
    });
  }, r ? { onDelete: () => remove('match_rounds', r.id) } : {});
}

// ---------- Competitions ----------
function competitionsTab() {
  const list = rows('competitions').sort((a, b) => b.date - a.date);
  if (!list.length) return emptyState();
  return h('ul', { class: 'list card' }, list.map((c) => h('li', null, h('button', { class: 'list-link', onclick: () => competitionDialog(c) },
    h('span', null, h('strong', null, c.name), c.location ? ` · ${c.location}` : ''), h('span', { class: 'muted small' }, fmt.date(c.date))))));
}

function competitionDialog(c) {
  formDialog(c ? c.name : t('comp_new'), [
    field(t('comp_name'), input('name', c?.name, { required: true, maxlength: 120 })),
    h('div', { class: 'grid-3' },
      field(t('match_date'), input('date', toLocalInput(c?.date ?? Date.now(), false), { type: 'date', required: true })),
      field(t('comp_location'), input('location', c?.location, { maxlength: 120 })),
      field(t('match_category'), input('category', c?.category, { maxlength: 60 }))),
    field(t('session_notes'), textarea('notes', c?.notes)),
  ], async (v) => {
    await save('competitions', { ...(c ?? { createdAt: Date.now() }), name: required(v.name, t('comp_name')), date: fromLocalInput(`${v.date}T12:00`), location: v.location ?? '', category: v.category ?? '', notes: v.notes ?? '' });
  }, c ? { onDelete: () => remove('competitions', c.id) } : {});
}

export { numOrNull };
