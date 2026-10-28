// App shell: sign-in check, data load, navigation and hash routing.
import { api, h, onStoreChange, pull, startPolling, store } from './core.js';
import { lang, setLang, t } from './i18n.js';
import { applyTheme } from './theme.js';
import { loginView } from './views/login.js';
import { homeView } from './views/home.js';
import { trainView, sessionDetailView, matchDetailView, combinationDetailView } from './views/train.js';
import { runningView, runDetailView } from './views/running.js';
import { performanceView } from './views/performance.js';
import { insightsView } from './views/insights.js';
import { coachView } from './views/coach.js';
import { motionView, motionDetailView } from './views/motion.js';
import { profileView } from './views/profile.js';
import { settingsView } from './views/settings.js';

const ICONS = {
  home: '<path d="M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
  train: '<path d="M6 6h3v12H6zM15 6h3v12h-3zM3 9h3v6H3zM18 9h3v6h-3zM9 11h6v2H9z"/>',
  coach: '<path d="M12 3l1.8 4.7L18.5 9.5 13.8 11.3 12 16l-1.8-4.7L5.5 9.5l4.7-1.8zM18 15l.9 2.1L21 18l-2.1.9L18 21l-.9-2.1L15 18l2.1-.9z"/>',
  insights: '<path d="M4 20V10h3v10zM10.5 20V4h3v16zM17 20v-7h3v7z"/>',
  profile: '<path d="M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9zm0 2c-4.4 0-8 2.2-8 5v2h16v-2c0-2.8-3.6-5-8-5z"/>',
  running: '<path d="M13.5 5.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM9.8 8.9 7 22h2.1l1.8-8 2.1 2v6h2v-7.5l-2.1-2 .6-3A7.3 7.3 0 0 0 19 12v-2a5.2 5.2 0 0 1-4.5-2.5l-1-1.6A2 2 0 0 0 11 5L6 7.2V12h2V8.6z"/>',
  performance: '<path d="M12 4a9 9 0 0 0-9 9h2a7 7 0 1 1 14 0h2a9 9 0 0 0-9-9zm4.2 4.4-4.9 4.3a1.5 1.5 0 1 0 2 2z"/>',
  motion: '<path d="M4 5h11a2 2 0 0 1 2 2v2.5l4-2.5v10l-4-2.5V17a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z"/>',
  settings: '<path d="M19.4 13a7.6 7.6 0 0 0 0-2l2-1.6-2-3.4-2.4 1a7.4 7.4 0 0 0-1.7-1L15 3.5h-4l-.4 2.5a7.4 7.4 0 0 0-1.7 1l-2.4-1-2 3.4L6.6 11a7.6 7.6 0 0 0 0 2l-2 1.6 2 3.4 2.4-1a7.4 7.4 0 0 0 1.7 1l.4 2.5h4l.4-2.5a7.4 7.4 0 0 0 1.7-1l2.4 1 2-3.4zM13 15.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7z" transform="translate(-1 0)"/>',
};
export const icon = (name) => h('span', { class: 'icon', 'aria-hidden': 'true', html: `<svg viewBox="0 0 24 24">${ICONS[name]}</svg>` });

const PRIMARY = [['home', '#/', 'nav_home'], ['train', '#/train', 'nav_train'], ['coach', '#/coach', 'nav_coach'], ['insights', '#/insights', 'nav_insights'], ['profile', '#/profile', 'nav_profile']];
const SECONDARY = [['running', '#/running', 'nav_running'], ['performance', '#/performance', 'nav_performance'], ['motion', '#/motion', 'nav_motion'], ['settings', '#/settings', 'nav_settings']];

function route() {
  const parts = (location.hash.replace(/^#\/?/, '') || '').split('/').filter(Boolean);
  const [a, b, c] = parts;
  switch (a) {
    case undefined: return ['home', () => homeView()];
    case 'train':
      if (b === 'session' && c) return ['train', () => sessionDetailView(c)];
      if (b === 'match' && c) return ['train', () => matchDetailView(c)];
      if (b === 'combination' && c) return ['train', () => combinationDetailView(c)];
      return ['train', () => trainView(b || 'sessions')];
    case 'running': return ['running', () => (b ? runDetailView(b) : runningView())];
    case 'performance': return ['performance', () => performanceView(b || 'recovery')];
    case 'insights': return ['insights', () => insightsView()];
    case 'coach': return ['coach', () => coachView()];
    case 'motion': return ['motion', () => (b ? motionDetailView(b) : motionView())];
    case 'profile': return ['profile', () => profileView()];
    case 'settings': return ['settings', () => settingsView()];
    default: return ['home', () => homeView()];
  }
}

const root = document.getElementById('app');
let main;
let offlineBanner;

function navLink([key, href, label], active) {
  return h('a', { href, class: key === active ? 'nav-link active' : 'nav-link', 'aria-current': key === active ? 'page' : null }, icon(key), h('span', null, t(label)));
}

function shell(active) {
  offlineBanner = h('div', { class: 'offline-banner', role: 'alert', hidden: store.online }, t('offline'));
  main = h('main', { class: 'main', id: 'main' });
  root.replaceChildren(
    h('a', { href: '#main', class: 'skip' }, 'Skip to content'),
    h('aside', { class: 'sidebar' },
      h('div', { class: 'brand' }, h('img', { src: '/icon.svg', alt: '', width: 32, height: 32 }), h('div', null, h('strong', null, t('app')), h('small', null, t('tagline')))),
      h('nav', { 'aria-label': 'Main' }, PRIMARY.map((n) => navLink(n, active)), h('hr'), SECONDARY.map((n) => navLink(n, active)))),
    h('div', { class: 'content' }, offlineBanner, main),
    h('nav', { class: 'bottom-nav', 'aria-label': 'Main' }, PRIMARY.map((n) => navLink(n, active))));
}

let currentKey = null;
function render() {
  const hash = location.hash;
  if (hash.startsWith('#/login')) {
    currentKey = null;
    root.replaceChildren(loginView(start));
    return;
  }
  if (!store.loaded) return;
  const [key, view] = route();
  if (key !== currentKey || !main?.isConnected) { shell(key); currentKey = key; }
  else root.querySelectorAll('.nav-link').forEach((a) => a.classList.toggle('active', a.getAttribute('href') === `#/${key === 'home' ? '' : key}`));
  const scroll = window.scrollY;
  const sameRoute = main.dataset.hash === hash;
  main.replaceChildren(view());
  main.dataset.hash = hash;
  if (sameRoute) window.scrollTo(0, scroll); // background refresh keeps the position
  else window.scrollTo(0, 0);
}

async function start() {
  root.replaceChildren(h('div', { class: 'splash' }, h('img', { src: '/icon.svg', alt: '', width: 64, height: 64 }), h('p', null, t('loading'))));
  try {
    store.session = await api('/auth/session');
  } catch {
    location.hash = '#/login';
    render();
    return;
  }
  if (location.hash.startsWith('#/login')) location.hash = '#/';
  await pull();
  api('/server/info').then((info) => { store.info = info; }).catch(() => {});
  startPolling();
  render();
}

window.addEventListener('hashchange', render);
onStoreChange(() => {
  if (offlineBanner) offlineBanner.hidden = store.online;
  // Re-render on data changes, but never while the user is typing in a dialog.
  if (!document.querySelector('dialog[open]') && !document.activeElement?.matches('input, textarea, select')) render();
});

export function rerender() { currentKey = null; render(); }

setLang(lang);
applyTheme();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
start();
