// Theme preference (system / light / dark), stored per browser.
export function themePref() {
  try { return localStorage.getItem('tkd.theme') || 'system'; } catch { return 'system'; }
}

export function applyTheme(pref = themePref()) {
  const root = document.documentElement;
  if (pref === 'system') root.removeAttribute('data-theme');
  else root.setAttribute('data-theme', pref);
  try { localStorage.setItem('tkd.theme', pref); } catch { /* ignore */ }
  const dark = pref === 'dark' || (pref === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#101114' : '#f5f6f8');
}
