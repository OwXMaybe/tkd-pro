import { api, card, fmt, h, pageHeader, store, tabs, toast } from '../core.js';
import { lang, setLang, t } from '../i18n.js';
import { applyTheme, themePref } from '../theme.js';
import { aiPermissions, CATEGORIES, setAiPermissions } from './coach.js';

let installPrompt = null;
window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); installPrompt = e; });

const refresh = () => window.dispatchEvent(new HashChangeEvent('hashchange'));

export function settingsView() {
  const info = store.info;
  return h('div', { class: 'page' },
    pageHeader(t('set_title')),
    h('div', { class: 'grid-2' },
      card(t('set_language'), tabs([['tr', 'Türkçe'], ['en', 'English']], lang, (v) => { setLang(v); location.reload(); })),
      card(t('set_theme'), tabs([['system', t('theme_system')], ['light', t('theme_light')], ['dark', t('theme_dark')]], themePref(), (v) => { applyTheme(v); refresh(); }))),
    connectCard(info),
    devicesCard(),
    aiCard(),
    installCard(),
    card(t('set_server'),
      info ? h('ul', { class: 'list plain' },
        h('li', null, t('set_version', { v: info.version })),
        h('li', null, t('set_ai_configured', { v: info.ai_configured ? t('yes') : t('no') })),
        h('li', null, t('set_motion_available', { v: info.motion_available ? t('yes') : t('no') }))) : null,
      h('button', { class: 'btn ghost', onclick: logout }, t('set_logout'))));
}

function connectCard(info) {
  const out = h('div', { class: 'pair-out' });
  const btn = h('button', { class: 'btn primary' }, t('set_connect_make'));
  btn.addEventListener('click', async () => {
    try {
      const res = await api('/auth/pairing-code', { method: 'POST' });
      const addresses = info?.lan_urls?.length ? info.lan_urls : [location.origin];
      out.replaceChildren(
        h('div', { class: 'pair-code', 'aria-label': t('login_code') }, res.code.slice(0, 3), h('span', { class: 'gap' }), res.code.slice(3)),
        h('p', { class: 'muted small' }, t('set_connect_expires', { t: new Date(res.expires_at).toLocaleTimeString(lang === 'tr' ? 'tr-TR' : 'en-GB', { hour: '2-digit', minute: '2-digit' }) })),
        h('p', null, h('strong', null, `${t('set_connect_address')}: `)),
        h('ul', { class: 'list plain' }, addresses.map((u) => h('li', null, h('code', { class: 'address' }, u)))),
        h('p', { class: 'muted small' }, t('set_connect_wifi')));
    } catch (e) { toast(e.message, 'error'); }
  });
  return card(t('set_connect'), h('p', { class: 'muted' }, t('set_connect_desc')), btn, out);
}

function devicesCard() {
  const list = h('ul', { class: 'list' }, h('li', { class: 'muted' }, t('loading')));
  api('/devices').then((devices) => {
    list.replaceChildren(...devices.map((d) => h('li', { class: 'list-item' },
      h('span', null, h('strong', null, d.name), ` · ${d.kind}`, d.id === store.session?.device_id ? ` (${t('set_device_this')})` : ''),
      h('span', { class: 'muted small' }, d.last_used_at ? t('set_device_last', { t: fmt.dateTime(d.last_used_at) }) : fmt.dateTime(d.created_at)),
      d.id === store.session?.device_id ? null : h('button', { class: 'btn ghost small', onclick: async () => { await api(`/devices/${d.id}`, { method: 'DELETE' }); refresh(); } }, t('set_device_revoke')))));
  }).catch(() => list.replaceChildren());
  return card(t('set_devices'), list);
}

function aiCard() {
  const perm = aiPermissions();
  const toggle = (key, label) => {
    const box = h('input', { type: 'checkbox', checked: perm[key] ? true : null, disabled: key !== 'enabled' && !perm.enabled ? true : null });
    box.addEventListener('change', () => { setAiPermissions({ ...perm, [key]: box.checked }); refresh(); });
    return h('label', { class: 'check' }, box, h('span', null, label));
  };
  return card(t('set_ai'),
    h('p', { class: 'muted' }, t('set_ai_desc')),
    toggle('enabled', t('set_ai_enable')),
    h('div', { class: 'checks' }, CATEGORIES.map((c) => toggle(c, t(`ai_${c}`)))));
}

function installCard() {
  const standalone = matchMedia('(display-mode: standalone)').matches;
  if (standalone) return null;
  const btn = h('button', { class: 'btn', disabled: installPrompt ? null : true }, t('set_install'));
  btn.addEventListener('click', async () => { if (installPrompt) { installPrompt.prompt(); installPrompt = null; } });
  return card(t('set_install'), h('p', { class: 'muted' }, t('set_install_desc')), btn);
}

async function logout() {
  try { await api('/auth/logout', { method: 'POST' }); } catch { /* already signed out */ }
  location.hash = '#/login';
  location.reload();
}
