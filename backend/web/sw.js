// Caches the app shell so the installed app opens instantly. Data always comes from the server
// (API responses are never cached), so nothing stale is shown as current.
const CACHE = 'tkd-shell-v1';
const SHELL = [
  '/', '/index.html', '/css/app.css', '/icon.svg', '/manifest.webmanifest',
  '/js/app.js', '/js/core.js', '/js/i18n.js', '/js/theme.js', '/js/charts.js', '/js/map.js',
  '/js/views/login.js', '/js/views/home.js', '/js/views/train.js', '/js/views/running.js', '/js/views/performance.js',
  '/js/views/insights.js', '/js/views/coach.js', '/js/views/motion.js', '/js/views/profile.js', '/js/views/settings.js',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api/') || url.pathname.startsWith('/auth/')) return;
  // Network first, so updates arrive as soon as the server has them; cache when offline.
  e.respondWith(
    fetch(e.request)
      .then((res) => {
        if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
        return res;
      })
      .catch(() => caches.match(e.request).then((hit) => hit || caches.match('/'))),
  );
});
