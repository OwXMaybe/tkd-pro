import { badge, byId, card, emptyState, epochDay, fmt, h, pageHeader, rows, sessionLoad, startOfWeek, stat } from '../core.js';
import { enumLabel, t } from '../i18n.js';
import { recoveryDialog } from './performance.js';

export function homeView() {
  const profile = byId('athlete_profile', 1);
  const weekStart = startOfWeek().getTime();
  const weekEnd = weekStart + 7 * 86400000;
  const sessions = rows('training_sessions').filter((s) => s.status === 'COMPLETED' && s.startTime >= weekStart && s.startTime < weekEnd);
  const minutes = sessions.reduce((a, s) => a + s.durationMin, 0);
  const loads = sessions.map(sessionLoad).filter((v) => v != null);
  const runs = rows('running_sessions').filter((r) => r.status === 'COMPLETED' && r.startTime >= weekStart && r.startTime < weekEnd);
  const km = runs.reduce((a, r) => a + r.distanceM, 0);

  const today = epochDay();
  const checkin = rows('recovery_entries').filter((e) => e.epochDay === today).sort((a, b) => b.updatedAt - a.updatedAt)[0];

  const recent = [
    ...rows('training_sessions').map((s) => ({ at: s.startTime, text: `${s.name} · ${enumLabel('TrainingCategory', s.category)} · ${fmt.minutes(s.durationMin)}`, href: `#/train/session/${s.id}` })),
    ...rows('running_sessions').filter((r) => r.status === 'COMPLETED').map((r) => ({ at: r.startTime, text: `${enumLabel('RunType', r.type)} · ${fmt.km(r.distanceM)}`, href: `#/running/${r.id}` })),
    ...rows('matches').map((m) => ({ at: m.date, text: `${m.competitionName}${m.opponent ? ` · ${m.opponent}` : ''} · ${enumLabel('MatchResult', m.result)}`, href: `#/train/match/${m.id}` })),
    ...rows('motion_analyses').map((a) => ({ at: a.createdAt, text: `${a.techniqueName} · ${enumLabel('AnalysisStatus', a.status)}`, href: `#/motion/${a.id}` })),
  ].sort((a, b) => b.at - a.at).slice(0, 8);

  const goals = rows('personal_goals').filter((g) => g.status === 'ACTIVE').slice(0, 4);
  const nextComp = rows('competitions').filter((c) => c.date >= Date.now() - 86400000).sort((a, b) => a.date - b.date)[0];

  return h('div', { class: 'page' },
    pageHeader(profile?.displayName ? t('home_hello', { name: profile.displayName }) : t('home_hello_anon')),
    card(t('home_week'),
      h('div', { class: 'stats' },
        stat(t('home_sessions'), String(sessions.length)),
        stat(t('home_minutes'), fmt.minutes(minutes)),
        stat(t('home_load'), loads.length ? fmt.num(loads.reduce((a, b) => a + b, 0)) : t('none'), badge('estimated')),
        stat(t('home_km'), fmt.km(km), badge('measured'))),
      h('p', { class: 'muted small' }, `${t('home_load')}: ${t('home_load_hint')}`)),
    h('div', { class: 'grid-2' },
      card(t('home_recovery_today'),
        checkin
          ? h('div', { class: 'stats' },
            stat(t('rec_sleep_h'), fmt.num(checkin.sleepHours, 1)),
            stat(t('rec_energy'), checkin.energy ?? t('none')),
            stat(t('rec_fatigue'), checkin.fatigue ?? t('none')),
            stat(t('rec_recovery'), checkin.recovery ?? t('none')))
          : emptyState(t('home_no_checkin'), h('button', { class: 'btn primary', onclick: () => recoveryDialog(null) }, t('home_checkin')))),
      card(t('home_next_comp'),
        nextComp
          ? h('div', null, h('p', { class: 'big' }, nextComp.name), h('p', { class: 'muted' }, `${fmt.date(nextComp.date)}${nextComp.location ? ` · ${nextComp.location}` : ''}`),
            h('p', null, t('home_days_left', { n: Math.max(0, Math.ceil((nextComp.date - Date.now()) / 86400000)) })))
          : emptyState())),
    h('div', { class: 'grid-2' },
      card(t('home_recent'),
        recent.length ? h('ul', { class: 'list' }, recent.map((r) => h('li', null, h('a', { href: r.href, class: 'list-link' }, h('span', null, r.text), h('span', { class: 'muted small' }, fmt.date(r.at)))))) : emptyState()),
      card(t('home_goals'),
        goals.length ? h('ul', { class: 'list' }, goals.map((g) => h('li', { class: 'list-item' },
          h('span', null, g.title),
          g.targetValue != null ? h('progress', { max: g.targetValue, value: g.currentValue ?? 0, 'aria-label': g.title }) : null,
          h('span', { class: 'muted small' }, g.targetDate ? fmt.date(g.targetDate) : enumLabel('GoalCategory', g.category))))) : emptyState())),
    h('nav', { class: 'quick-links' },
      h('a', { href: '#/running', class: 'btn ghost' }, t('nav_running')),
      h('a', { href: '#/performance', class: 'btn ghost' }, t('nav_performance')),
      h('a', { href: '#/motion', class: 'btn ghost' }, t('nav_motion')),
      h('a', { href: '#/settings', class: 'btn ghost' }, t('nav_settings'))));
}
