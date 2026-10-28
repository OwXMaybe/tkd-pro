import {
  byId, card, emptyState, field, fmt, formDialog, fromLocalInput, h, input, intOrNull, numOrNull, pageHeader, remove, required, rows, save, select,
  stat, textarea, toLocalInput,
} from '../core.js';
import { enumLabel, enumValues, t } from '../i18n.js';

const opts = (type) => enumValues(type).map((v) => [v, enumLabel(type, v)]);

export function profileView() {
  const p = byId('athlete_profile', 1);
  const belts = rows('belt_records').sort((a, b) => b.achievedOn - a.achievedOn);
  const goals = rows('personal_goals').sort((a, b) => (a.status === b.status ? b.updatedAt - a.updatedAt : a.status === 'ACTIVE' ? -1 : 1));
  return h('div', { class: 'page' },
    pageHeader(t('prof_title'), h('button', { class: 'btn', onclick: () => profileDialog(p) }, t('edit')), h('a', { href: '#/settings', class: 'btn ghost' }, t('nav_settings'))),
    p ? card(p.displayName, h('div', { class: 'stats' },
      stat(t('prof_belt'), p.currentBelt || t('none')), stat(t('prof_started'), p.startedTrainingYear ?? t('none')),
      stat(t('prof_weight'), p.weightCategory || t('none')), stat(t('prof_side'), p.dominantSide ? enumLabel('BodySide', p.dominantSide) : t('none'))),
      (p.club || p.coach) ? h('p', { class: 'muted' }, [p.club, p.coach].filter(Boolean).join(' · ')) : null) : emptyState(),
    h('div', { class: 'grid-2' },
      card(t('prof_belts'),
        belts.length ? h('ul', { class: 'list' }, belts.map((b) => h('li', null, h('button', { class: 'list-link', onclick: () => beltDialog(b) }, h('strong', null, b.belt), h('span', { class: 'muted small' }, fmt.date(b.achievedOn)))))) : emptyState(),
        h('button', { class: 'btn', onclick: () => beltDialog(null) }, t('prof_belt_new'))),
      card(t('prof_goals'),
        goals.length ? h('ul', { class: 'list' }, goals.map((g) => h('li', null, h('button', { class: 'list-link', onclick: () => goalDialog(g) },
          h('span', null, h('strong', null, g.title), ` · ${enumLabel('GoalStatus', g.status)}`),
          g.targetValue != null ? h('span', { class: 'small' }, `${fmt.num(g.currentValue ?? 0, 1)} / ${fmt.num(g.targetValue, 1)} ${g.unit}`) : h('span', { class: 'muted small' }, g.targetDate ? fmt.date(g.targetDate) : ''))))) : emptyState(),
        h('button', { class: 'btn', onclick: () => goalDialog(null) }, t('goal_new')))));
}

function profileDialog(p) {
  formDialog(t('prof_title'), [
    field(t('prof_name'), input('displayName', p?.displayName, { required: true, maxlength: 60 })),
    h('div', { class: 'grid-3' },
      field(t('prof_started'), input('startedTrainingYear', p?.startedTrainingYear, { type: 'number', min: 1950, max: 2100 })),
      field(t('prof_birth'), input('birthYear', p?.birthYear, { type: 'number', min: 1920, max: 2100 })),
      field(t('prof_side'), select('dominantSide', [['', t('none')], ...opts('BodySide')], p?.dominantSide ?? ''))),
    h('div', { class: 'grid-2' },
      field(t('prof_belt'), input('currentBelt', p?.currentBelt, { maxlength: 40 })),
      field(t('prof_weight'), input('weightCategory', p?.weightCategory, { maxlength: 40 }))),
    h('div', { class: 'grid-2' },
      field(t('prof_club'), input('club', p?.club, { maxlength: 80 })),
      field(t('prof_coach'), input('coach', p?.coach, { maxlength: 80 }))),
  ], async (v) => {
    const now = Date.now();
    await save('athlete_profile', {
      ...(p ?? { photoPath: null, createdAt: now }), id: 1, displayName: required(v.displayName, t('prof_name')),
      startedTrainingYear: intOrNull(v.startedTrainingYear), birthYear: intOrNull(v.birthYear), dominantSide: v.dominantSide || null,
      currentBelt: v.currentBelt ?? '', weightCategory: v.weightCategory ?? '', club: v.club ?? '', coach: v.coach ?? '', updatedAt: now,
    });
  });
}

function beltDialog(b) {
  formDialog(b ? b.belt : t('prof_belt_new'), [
    h('div', { class: 'grid-2' },
      field(t('prof_belt'), input('belt', b?.belt, { required: true, maxlength: 40 })),
      field(t('match_date'), input('achievedOn', toLocalInput(b?.achievedOn ?? Date.now(), false), { type: 'date', required: true }))),
    field(t('session_notes'), textarea('notes', b?.notes)),
  ], async (v) => {
    await save('belt_records', { ...(b ?? {}), belt: required(v.belt, t('prof_belt')), achievedOn: fromLocalInput(`${v.achievedOn}T12:00`), notes: v.notes ?? '' });
  }, b ? { onDelete: () => remove('belt_records', b.id) } : {});
}

function goalDialog(g) {
  formDialog(g ? g.title : t('goal_new'), [
    field(t('goal_title'), input('title', g?.title, { required: true, maxlength: 120 })),
    h('div', { class: 'grid-3' },
      field(t('goal_category'), select('category', opts('GoalCategory'), g?.category ?? 'TECHNICAL')),
      field(t('goal_status'), select('status', opts('GoalStatus'), g?.status ?? 'ACTIVE')),
      field(t('goal_target_date'), input('targetDate', g?.targetDate ? toLocalInput(g.targetDate, false) : '', { type: 'date' }))),
    h('div', { class: 'grid-3' },
      field(t('goal_target'), input('targetValue', g?.targetValue, { type: 'number', step: 'any' })),
      field(t('goal_current'), input('currentValue', g?.currentValue, { type: 'number', step: 'any' })),
      field(t('goal_unit'), input('unit', g?.unit, { maxlength: 20 }))),
    field(t('goal_description'), textarea('description', g?.description)),
  ], async (v) => {
    const now = Date.now();
    const achieved = v.status === 'ACHIEVED';
    await save('personal_goals', {
      ...(g ?? { createdAt: now }), title: required(v.title, t('goal_title')), description: v.description ?? '', category: v.category,
      targetDate: v.targetDate ? fromLocalInput(`${v.targetDate}T12:00`) : null, targetValue: numOrNull(v.targetValue),
      currentValue: numOrNull(v.currentValue), unit: v.unit ?? '', status: v.status,
      achievedAt: achieved ? (g?.achievedAt ?? now) : null, updatedAt: now,
    });
  }, g ? { onDelete: () => remove('personal_goals', g.id) } : {});
}
