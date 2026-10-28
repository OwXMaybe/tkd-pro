// Shared building blocks: DOM helper, API client, synced data store, formatting, dialogs.
import { lang, t } from './i18n.js';

// ---------- DOM ----------
export function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  if (attrs) {
    for (const [k, v] of Object.entries(attrs)) {
      if (v == null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
      else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
      else if (k === 'html') el.innerHTML = v; // only used with static, trusted markup (icons)
      else if (v === true) el.setAttribute(k, '');
      else el.setAttribute(k, v);
    }
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const c of children) {
    if (c == null || c === false) continue;
    if (Array.isArray(c)) append(el, c);
    else el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
}

export function svg(tag, attrs, ...children) {
  const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [k, v] of Object.entries(attrs || {})) if (v != null) el.setAttribute(k, v);
  for (const c of children.flat()) if (c != null) el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  return el;
}

// ---------- API ----------
export class ApiError extends Error {
  constructor(status, detail) { super(detail || `HTTP ${status}`); this.status = status; }
}

export async function api(path, { method = 'GET', body, form } = {}) {
  let res;
  try {
    res = await fetch(`/api/v1${path}`, {
      method,
      credentials: 'same-origin',
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: form ?? (body ? JSON.stringify(body) : undefined),
    });
  } catch {
    throw new ApiError(0, t('offline'));
  }
  if (res.status === 401 && !path.startsWith('/auth/')) {
    location.hash = '#/login';
    throw new ApiError(401, 'Unauthorized');
  }
  if (!res.ok) {
    let detail = '';
    try { detail = (await res.json()).detail; } catch { /* not json */ }
    throw new ApiError(res.status, typeof detail === 'string' ? detail : JSON.stringify(detail));
  }
  return res.status === 204 ? null : res.json();
}

// ---------- Synced store ----------
// The server keeps the newest copy of every record written by the phone app or this web app.
// The store mirrors it in memory, follows the change feed, and writes back through /sync/push.
const listeners = new Set();
export const store = {
  data: {},          // entity -> Map(id -> record)
  cursor: 0,
  loaded: false,
  online: true,
  info: null,        // server info (AI configured, LAN URLs…)
};

export function onStoreChange(fn) { listeners.add(fn); return () => listeners.delete(fn); }
function emit() { for (const fn of listeners) fn(); }

export function table(entity) {
  if (!store.data[entity]) store.data[entity] = new Map();
  return store.data[entity];
}
export function rows(entity) { return [...table(entity).values()]; }
export function byId(entity, id) { return table(entity).get(Number(id)); }

function applyChange(c) {
  const m = table(c.entity);
  if (c.op === 'DELETE') m.delete(c.entity_id);
  else m.set(c.entity_id, { ...c.payload, id: c.payload.id ?? c.entity_id });
}

export async function pull() {
  let changed = false;
  for (;;) {
    const page = await api(`/sync/changes?after=${store.cursor}&limit=2000`);
    page.items.forEach(applyChange);
    changed ||= page.items.length > 0;
    store.cursor = page.next_after;
    if (!page.has_more) break;
  }
  store.loaded = true;
  return changed;
}

let pollTimer = null;
export function startPolling() {
  clearInterval(pollTimer);
  const tick = async () => {
    try {
      const changed = await pull();
      if (!store.online) { store.online = true; emit(); }
      if (changed) emit();
    } catch (e) {
      if (e.status === 0 && store.online) { store.online = false; emit(); }
    }
  };
  pollTimer = setInterval(tick, 10_000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) tick(); });
}

/** Ids created here are negative, so they never collide with the phone's auto-increment ids. */
export function newId() {
  return -(Date.now() * 1000 + Math.floor(Math.random() * 1000));
}

export async function save(entity, record) {
  const now = Date.now();
  const rec = { ...record };
  if (!rec.id && entity !== 'athlete_profile') rec.id = newId();
  await api('/sync/push', { method: 'POST', body: { items: [{ entity, entity_id: rec.id, op: 'UPSERT', payload: rec, client_updated_at: now }] } });
  table(entity).set(rec.id, rec);
  emit();
  return rec;
}

/** Saves a parent-keyed record (run_routes, combination_steps) whose id is the parent's id. */
export async function saveKeyed(entity, id, payload) {
  await api('/sync/push', { method: 'POST', body: { items: [{ entity, entity_id: id, op: 'UPSERT', payload, client_updated_at: Date.now() }] } });
  table(entity).set(id, { ...payload, id });
  emit();
}

export async function remove(entity, id, children = []) {
  const now = Date.now();
  const items = [{ entity, entity_id: id, op: 'DELETE', payload: {}, client_updated_at: now }];
  // Children are deleted explicitly so the server copy matches the phone's cascade.
  for (const [childEntity, ids] of children) for (const cid of ids) items.push({ entity: childEntity, entity_id: cid, op: 'DELETE', payload: {}, client_updated_at: now });
  await api('/sync/push', { method: 'POST', body: { items } });
  for (const it of items) table(it.entity).delete(it.entity_id);
  emit();
}

// ---------- Formatting ----------
const locale = () => (lang === 'tr' ? 'tr-TR' : 'en-GB');
export const fmt = {
  date: (ms) => (ms == null ? t('none') : new Date(ms).toLocaleDateString(locale(), { day: 'numeric', month: 'short', year: 'numeric' })),
  shortDate: (ms) => new Date(ms).toLocaleDateString(locale(), { day: 'numeric', month: 'short' }),
  dateTime: (ms) => (ms == null ? t('none') : new Date(ms).toLocaleString(locale(), { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })),
  weekday: (ms) => new Date(ms).toLocaleDateString(locale(), { weekday: 'short', day: 'numeric', month: 'short' }),
  num: (v, d = 0) => (v == null || Number.isNaN(v) ? t('none') : Number(v).toLocaleString(locale(), { maximumFractionDigits: d, minimumFractionDigits: d })),
  km: (m) => `${fmt.num((m || 0) / 1000, 2)} km`,
  duration: (ms) => {
    if (ms == null) return t('none');
    const s = Math.round(ms / 1000);
    const hh = Math.floor(s / 3600), mm = Math.floor((s % 3600) / 60), ss = s % 60;
    return hh ? `${hh}:${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')}` : `${mm}:${String(ss).padStart(2, '0')}`;
  },
  pace: (distanceM, ms) => {
    if (!distanceM || distanceM < 50 || !ms) return t('none');
    const secPerKm = ms / 1000 / (distanceM / 1000);
    return `${Math.floor(secPerKm / 60)}:${String(Math.round(secPerKm % 60)).padStart(2, '0')} /km`;
  },
  minutes: (min) => {
    const [hu, mu] = lang === 'tr' ? ['sa', 'dk'] : ['h', 'min'];
    return min >= 60 ? `${Math.floor(min / 60)} ${hu} ${min % 60} ${mu}` : `${min} ${mu}`;
  },
  minUnit: () => (lang === 'tr' ? 'dk' : 'min'),
};

/** Local date helpers. Recovery entries are keyed by epoch day (days since 1970-01-01, local calendar). */
export function epochDay(date = new Date()) {
  return Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86400000);
}
export function dayToDate(epochDayValue) {
  const d = new Date(epochDayValue * 86400000);
  return new Date(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
}
export function startOfWeek(date = new Date()) {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const day = (d.getDay() + 6) % 7; // Monday first
  d.setDate(d.getDate() - day);
  return d;
}
export function toLocalInput(ms, withTime = true) {
  const d = new Date(ms);
  const pad = (n) => String(n).padStart(2, '0');
  const date = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  return withTime ? `${date}T${pad(d.getHours())}:${pad(d.getMinutes())}` : date;
}
export function fromLocalInput(value) {
  return value ? new Date(value).getTime() : null;
}
export function sessionLoad(s) {
  return s.rpe ? s.durationMin * s.rpe : null;
}

// ---------- Feedback ----------
export function toast(message, kind = 'info') {
  const el = h('div', { class: `toast toast-${kind}`, role: 'status' }, message);
  document.body.append(el);
  setTimeout(() => el.classList.add('show'), 10);
  setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 300); }, 3200);
}

export function confirmDialog(message) {
  return new Promise((resolve) => {
    const dlg = h('dialog', { class: 'dialog' },
      h('p', null, message),
      h('div', { class: 'row end' },
        h('button', { class: 'btn ghost', onclick: () => { dlg.close(); resolve(false); } }, t('cancel')),
        h('button', { class: 'btn danger', onclick: () => { dlg.close(); resolve(true); } }, t('delete'))));
    dlg.addEventListener('close', () => dlg.remove());
    document.body.append(dlg);
    dlg.showModal();
  });
}

/** Opens a modal form. `build(form)` returns the fields; `onSubmit(values)` saves and may throw to keep it open. */
export function formDialog(title, fields, onSubmit, { onDelete } = {}) {
  const form = h('form', { class: 'form', method: 'dialog' });
  const error = h('p', { class: 'form-error', role: 'alert' });
  const dlg = h('dialog', { class: 'dialog wide' },
    h('h2', null, title), form);
  form.append(...fields, error,
    h('div', { class: 'row end' },
      onDelete ? h('button', { type: 'button', class: 'btn danger ghost left', onclick: async () => {
        if (await confirmDialog(t('confirm_delete'))) { try { await onDelete(); dlg.close(); } catch (e) { error.textContent = e.message; } }
      } }, t('delete')) : null,
      h('button', { type: 'button', class: 'btn ghost', onclick: () => dlg.close() }, t('cancel')),
      h('button', { type: 'submit', class: 'btn primary' }, t('save'))));
  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    error.textContent = '';
    try {
      await onSubmit(Object.fromEntries(new FormData(form)), form);
      dlg.close();
      toast(t('saved'), 'ok');
    } catch (e) {
      error.textContent = e.message;
    }
  });
  dlg.addEventListener('close', () => dlg.remove());
  document.body.append(dlg);
  dlg.showModal();
  return dlg;
}

// ---------- Form fields ----------
export function field(label, input, hint) {
  return h('label', { class: 'field' }, h('span', { class: 'field-label' }, label), input, hint ? h('span', { class: 'field-hint' }, hint) : null);
}
export function input(name, value, attrs = {}) {
  return h('input', { name, value: value ?? '', ...attrs });
}
export function textarea(name, value, attrs = {}) {
  const el = h('textarea', { name, rows: 3, ...attrs });
  el.value = value ?? '';
  return el;
}
export function select(name, options, value, attrs = {}) {
  const el = h('select', { name, ...attrs }, options.map(([v, label]) => h('option', { value: v }, label)));
  el.value = value ?? '';
  return el;
}
export const numOrNull = (v) => (v === '' || v == null ? null : Number(v));
export const intOrNull = (v) => (v === '' || v == null ? null : Math.round(Number(v)));
export function required(value, label) {
  if (value == null || String(value).trim() === '') throw new Error(`${label}: ${lang === 'tr' ? 'zorunlu' : 'required'}`);
  return String(value).trim();
}
export function inRange(value, min, max, label) {
  if (value != null && (value < min || value > max)) throw new Error(`${label}: ${min}–${max}`);
  return value;
}

// ---------- Layout pieces ----------
export function card(title, ...body) {
  return h('section', { class: 'card' }, title ? h('h3', { class: 'card-title' }, title) : null, ...body);
}
export function stat(label, value, badge) {
  return h('div', { class: 'stat' }, h('div', { class: 'stat-value' }, value), h('div', { class: 'stat-label' }, label, badge ? [' ', badge] : null));
}
export function badge(kind) {
  const text = { measured: t('measured'), estimated: t('estimated'), manual: t('manual'), ai: t('ai_label') }[kind];
  return h('span', { class: `badge badge-${kind}` }, text);
}
export function emptyState(text = t('empty'), action) {
  return h('div', { class: 'empty' }, h('p', null, text), action);
}
export function pageHeader(title, ...actions) {
  return h('header', { class: 'page-header' }, h('h1', null, title), h('div', { class: 'row' }, ...actions));
}
export function tabs(items, active, onSelect) {
  return h('div', { class: 'tabs', role: 'tablist' },
    items.map(([key, label]) => h('button', { role: 'tab', class: key === active ? 'tab active' : 'tab', 'aria-selected': String(key === active), onclick: () => onSelect(key) }, label)));
}
