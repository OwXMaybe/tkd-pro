import { api, badge, byId, card, dayToDate, emptyState, epochDay, fmt, h, pageHeader, remove, rows, save, sessionLoad, store } from '../core.js';
import { lang, t } from '../i18n.js';

const CATEGORIES = ['training', 'running', 'matches', 'motion', 'recovery', 'reaction', 'profile'];

export function aiPermissions() {
  try { return { enabled: false, ...JSON.parse(localStorage.getItem('tkd.ai') || '{}') }; } catch { return { enabled: false }; }
}
export function setAiPermissions(p) {
  try { localStorage.setItem('tkd.ai', JSON.stringify(p)); } catch { /* ignore */ }
}
export { CATEGORIES };

const day = (ms) => toIso(new Date(ms));
function toIso(d) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/**
 * Same structure as the phone's CoachContextBuilder: only permitted categories are included,
 * the rest are listed as not shared. Name, club, coach, photos, videos and GPS routes are never included.
 */
export function buildContext(perm, from, to) {
  const ctx = {
    today: toIso(new Date()), period_start: day(from), period_end: day(to - 1),
    notes: 'All values come from the athlete\'s saved records. Manual observations are labelled \'manual\'. Motion metrics are 2D computer-vision estimates. Session load = duration(min) x RPE, a simple subjective estimate.',
  };
  const sources = [];
  const missing = [];
  const inRange = (ms) => ms >= from && ms < to;

  if (perm.training) {
    const sessions = rows('training_sessions').filter((s) => inRange(s.startTime)).sort((a, b) => b.startTime - a.startTime).slice(0, 150);
    const recs = rows('technique_records');
    const name = (id) => byId('techniques', id)?.name ?? 'unknown';
    const total = (r) => r.reps ?? ((r.rightReps ?? 0) + (r.leftReps ?? 0));
    ctx.training_sessions = sessions.map((s) => ({
      date: day(s.startTime), name: s.name, category: s.category, status: s.status, duration_min: s.durationMin,
      ...(s.rpe != null && { rpe: s.rpe, session_load_estimate: sessionLoad(s) }), ...(s.difficulty != null && { difficulty_1_5: s.difficulty }),
      techniques: recs.filter((r) => r.sessionId === s.id).map((r) => ({ technique: name(r.techniqueId), total_reps: total(r), ...(r.rightReps != null && { right_reps: r.rightReps }), ...(r.leftReps != null && { left_reps: r.leftReps }), ...(r.sets != null && { sets: r.sets }) })),
      ...(s.notes && { notes: s.notes.slice(0, 400) }),
    }));
    sources.push({ category: 'training', records: sessions.length, fromMs: from, toMs: to });
    if (!sessions.some((s) => s.status === 'COMPLETED')) missing.push('training: no completed sessions in period');
  } else missing.push('training: not shared');

  if (perm.running) {
    const runs = rows('running_sessions').filter((r) => r.status === 'COMPLETED' && inRange(r.startTime)).slice(0, 150);
    ctx.running_sessions = runs.map((r) => ({
      date: day(r.startTime), type: r.type, distance_km: Math.round(r.distanceM) / 1000, moving_time_min: r.movingTimeMs / 60000,
      ...(r.distanceM > 50 && r.movingTimeMs && { avg_pace_sec_per_km: Math.round(r.movingTimeMs / 1000 / (r.distanceM / 1000)) }),
      ...(r.elevationGainM != null && { elevation_gain_m: Math.round(r.elevationGainM) }), ...(r.avgHeartRate != null && { avg_heart_rate_manual: r.avgHeartRate }),
    }));
    sources.push({ category: 'running', records: runs.length, fromMs: from, toMs: to });
    if (!runs.length) missing.push('running: no runs in period');
  } else missing.push('running: not shared');

  if (perm.matches) {
    const matches = rows('matches').filter((m) => inRange(m.date)).slice(0, 150);
    ctx.matches = matches.map((m) => {
      const rounds = rows('match_rounds').filter((r) => r.matchId === m.id);
      const sum = (k) => (rounds.some((r) => r[k] != null) ? rounds.reduce((a, r) => a + (r[k] ?? 0), 0) : undefined);
      return {
        date: day(m.date), competition: m.competitionName, stage: m.stage, result: m.result,
        ...(m.scoreFor != null && { score_for: m.scoreFor }), ...(m.scoreAgainst != null && { score_against: m.scoreAgainst }),
        rounds_recorded: rounds.length,
        manual_round_totals: { attack_attempts: sum('attackAttempts'), successful_attacks: sum('successfulAttacks'), defensive_actions: sum('defensiveActions'), counterattacks: sum('counterattacks') },
        ...(m.strengths && { strengths_manual: m.strengths.slice(0, 400) }), ...(m.improvements && { improvements_manual: m.improvements.slice(0, 400) }),
      };
    });
    sources.push({ category: 'matches', records: matches.length, fromMs: from, toMs: to });
    if (!matches.length) missing.push('matches: none in period');
  } else missing.push('matches: not shared');

  if (perm.motion) {
    const analyses = rows('motion_analyses').filter((a) => inRange(a.createdAt)).slice(0, 150);
    ctx.motion_analyses = analyses.map((a) => {
      let r = null;
      try { r = JSON.parse(a.resultJson); } catch { /* unreadable */ }
      return {
        date: day(a.createdAt), technique: a.techniqueName, status: a.status,
        ...(r && {
          repetitions: r.repetitions?.length ?? 0, quality_warnings: r.quality?.warnings ?? [],
          sides: (r.sides ?? []).map((s) => ({ side: s.side, repetitions: s.repetitions, peak_ankle_height_torso_lengths_mean: s.peakAnkleHeight?.mean, kick_knee_angle_at_peak_deg_mean: s.kickKneeAngleAtPeak?.mean, support_knee_angle_at_peak_deg_mean: s.supportKneeAngleAtPeak?.mean, trunk_lean_at_peak_deg_mean: s.trunkLeanAtPeak?.mean, time_to_peak_ms_mean: s.timeToPeakMs?.mean })),
        }),
      };
    });
    sources.push({ category: 'motion', records: analyses.length, fromMs: from, toMs: to });
  } else missing.push('motion analyses: not shared');

  if (perm.recovery) {
    const fromDay = epochDay(new Date(from)), toDay = epochDay(new Date(to - 1));
    const entries = rows('recovery_entries').filter((e) => e.epochDay >= fromDay && e.epochDay <= toDay).sort((a, b) => a.epochDay - b.epochDay);
    ctx.recovery_checkins = entries.map((e) => ({
      date: toIso(dayToDate(e.epochDay)), sleep_hours: e.sleepHours ?? undefined, sleep_quality_1_5: e.sleepQuality ?? undefined, energy_1_5: e.energy ?? undefined,
      fatigue_1_5: e.fatigue ?? undefined, soreness_1_5: e.soreness ?? undefined, perceived_recovery_1_5: e.recovery ?? undefined, resting_hr: e.restingHeartRate ?? undefined,
    }));
    sources.push({ category: 'recovery', records: entries.length, fromMs: from, toMs: to });
  } else missing.push('recovery: not shared');

  if (perm.reaction) {
    const tests = rows('reaction_tests').filter((r) => inRange(r.date));
    const perf = rows('performance_metrics').filter((p) => inRange(p.date));
    ctx.reaction_tests = tests.map((r) => ({ date: day(r.date), type: r.type, valid_trials: r.validTrials, total_trials: r.totalTrials, average_ms: r.averageMs != null ? Math.round(r.averageMs) : undefined, best_ms: r.bestMs ?? undefined, sd_ms: r.standardDeviationMs != null ? Math.round(r.standardDeviationMs) : undefined, device: r.deviceModel }));
    ctx.performance_tests = perf.map((p) => ({ date: day(p.date), test: p.testType, value: p.value, unit: p.unit, side: p.side ?? undefined, method: p.method || undefined }));
    sources.push({ category: 'reaction_and_performance_tests', records: tests.length + perf.length, fromMs: from, toMs: to });
  } else missing.push('reaction/performance tests: not shared');

  if (perm.profile) {
    const p = byId('athlete_profile', 1);
    ctx.athlete = p ? {
      training_since_year: p.startedTrainingYear ?? undefined, belt: p.currentBelt || undefined, weight_category: p.weightCategory || undefined,
      dominant_side: p.dominantSide ?? undefined, age_years_approx: p.birthYear ? new Date().getFullYear() - p.birthYear : undefined,
    } : {};
    const goals = rows('personal_goals');
    ctx.goals = goals.map((g) => ({ title: g.title, category: g.category, status: g.status, target_date: g.targetDate ? day(g.targetDate) : undefined }));
    sources.push({ category: 'profile_and_goals', records: goals.length + (p ? 1 : 0), fromMs: from, toMs: to });
  } else missing.push('profile/goals: not shared');

  ctx.missing_or_not_shared = missing;
  return { context: JSON.parse(JSON.stringify(ctx)), sources };
}

function dayRange(days) {
  const end = new Date(); end.setHours(0, 0, 0, 0); end.setDate(end.getDate() + 1);
  const start = new Date(end); start.setDate(end.getDate() - days);
  return [start.getTime(), end.getTime()];
}

const sourceText = (json) => {
  try { return JSON.parse(json || '[]').map((s) => `${s.category} (${s.records})`).join(', '); } catch { return ''; }
};

let busy = false;

export function coachView() {
  const perm = aiPermissions();
  const configured = store.info?.ai_configured;
  const messages = rows('chat_messages').sort((a, b) => a.createdAt - b.createdAt || a.id - b.id);
  const reports = rows('ai_reports').filter((r) => r.type === 'WEEKLY').sort((a, b) => b.createdAt - a.createdAt);

  const notice = !perm.enabled ? h('div', { class: 'notice' }, t('coach_disabled'), ' ', h('a', { href: '#/settings' }, t('nav_settings')))
    : configured === false ? h('div', { class: 'notice' }, t('coach_unavailable')) : null;

  const log = h('div', { class: 'chat-log', 'aria-live': 'polite' },
    messages.length ? messages.map((m) => h('div', { class: `bubble ${m.role === 'USER' ? 'me' : 'ai'}${m.failed ? ' failed' : ''}` },
      m.role === 'ASSISTANT' && !m.failed ? badge('ai') : null,
      h('div', { class: 'prewrap' }, m.failed ? t('coach_failed') : m.content),
      m.dataSourcesJson ? h('div', { class: 'muted small' }, t('coach_sources', { s: sourceText(m.dataSourcesJson) })) : null))
      : emptyState());
  if (busy) log.append(h('div', { class: 'bubble ai muted' }, t('coach_thinking')));

  const box = h('textarea', { rows: 2, placeholder: t('coach_placeholder'), maxlength: 4000, disabled: !perm.enabled || busy ? true : null, 'aria-label': t('coach_placeholder') });
  const send = async () => {
    const question = box.value.trim();
    if (!question || busy) return;
    busy = true;
    box.value = '';
    try {
      await save('chat_messages', { role: 'USER', content: question, dataSourcesJson: null, failed: false, createdAt: Date.now() });
      const [from, to] = dayRange(90);
      const { context, sources } = buildContext(perm, from, to);
      const history = rows('chat_messages').filter((m) => !m.failed).sort((a, b) => a.createdAt - b.createdAt).slice(-12)
        .map((m) => ({ role: m.role === 'USER' ? 'user' : 'assistant', content: m.content }));
      try {
        const res = await api('/coach/chat', { method: 'POST', body: { messages: history, context, locale: lang } });
        await save('chat_messages', { role: 'ASSISTANT', content: res.reply, dataSourcesJson: JSON.stringify(sources), failed: false, createdAt: Date.now() });
      } catch {
        await save('chat_messages', { role: 'ASSISTANT', content: '', dataSourcesJson: null, failed: true, createdAt: Date.now() });
      }
    } finally {
      busy = false;
      window.dispatchEvent(new HashChangeEvent('hashchange'));
    }
  };
  box.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } });

  const makeReport = async (btn) => {
    btn.disabled = true;
    try {
      const [from, to] = dayRange(14);
      const { context, sources } = buildContext(perm, from, to);
      const res = await api('/coach/weekly-report', { method: 'POST', body: { context, locale: lang } });
      await save('ai_reports', { type: 'WEEKLY', periodStart: from, periodEnd: to, content: res.report, dataSourcesJson: JSON.stringify(sources), model: res.model, createdAt: Date.now() });
    } catch (e) {
      btn.after(h('p', { class: 'form-error' }, e.message));
    } finally { btn.disabled = false; }
  };

  const reportBtn = h('button', { class: 'btn', disabled: !perm.enabled || !configured ? true : null }, t('coach_weekly_make'));
  reportBtn.addEventListener('click', () => makeReport(reportBtn));

  return h('div', { class: 'page' },
    pageHeader(t('coach_title'), messages.length ? h('button', { class: 'btn ghost', onclick: () => clearChat(messages) }, t('coach_clear')) : null),
    notice,
    h('p', { class: 'muted small' }, t('coach_disclaimer')),
    h('div', { class: 'grid-coach' },
      card(null, log,
        h('div', { class: 'chat-input' }, box, h('button', { class: 'btn primary', onclick: send, disabled: !perm.enabled || busy ? true : null }, t('coach_send')))),
      card(t('coach_weekly'), reportBtn,
        reports.length ? reports.slice(0, 5).map((r) => h('details', { class: 'report', open: r === reports[0] ? true : null },
          h('summary', null, `${fmt.date(r.periodStart)} – ${fmt.date(r.periodEnd - 1)}`),
          badge('ai'), h('div', { class: 'prewrap' }, r.content),
          h('div', { class: 'muted small' }, t('coach_sources', { s: sourceText(r.dataSourcesJson) })))) : emptyState())));
}

async function clearChat(messages) {
  // Removing chat history is synced: the phone's chat is cleared too.
  for (let i = 0; i < messages.length; i += 400) {
    const chunk = messages.slice(i, i + 400);
    await remove('chat_messages', chunk[0].id, [['chat_messages', chunk.slice(1).map((m) => m.id)]]);
  }
}
