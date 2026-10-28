import { api, h } from '../core.js';
import { t } from '../i18n.js';

export function loginView(onSignedIn) {
  let useToken = false;
  const error = h('p', { class: 'form-error', role: 'alert' });
  const codeInput = h('input', { name: 'code', inputmode: 'numeric', autocomplete: 'one-time-code', maxlength: 6, pattern: '\\d{6}', placeholder: '123456', class: 'code-input', required: true });
  const tokenInput = h('input', { name: 'token', type: 'password', autocomplete: 'current-password', required: true });
  const codeField = h('label', { class: 'field' }, h('span', { class: 'field-label' }, t('login_code')), codeInput, h('span', { class: 'field-hint' }, t('login_code_hint')));
  const tokenField = h('label', { class: 'field', hidden: true }, h('span', { class: 'field-label' }, t('login_token')), tokenInput, h('span', { class: 'field-hint' }, t('login_token_hint')));
  tokenInput.disabled = true;

  const toggle = h('button', { type: 'button', class: 'btn link' }, t('login_use_token'));
  toggle.addEventListener('click', () => {
    useToken = !useToken;
    codeField.hidden = useToken; codeInput.disabled = useToken;
    tokenField.hidden = !useToken; tokenInput.disabled = !useToken;
    (useToken ? tokenInput : codeInput).focus();
  });

  const form = h('form', { class: 'form' }, codeField, tokenField, error,
    h('button', { type: 'submit', class: 'btn primary block' }, t('login_action')), toggle);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    error.textContent = '';
    try {
      if (useToken) await api('/auth/login', { method: 'POST', body: { token: tokenInput.value } });
      else await api('/auth/pair', { method: 'POST', body: { code: codeInput.value.trim(), kind: 'web', device_name: '' } });
      location.hash = '#/';
      onSignedIn();
    } catch (err) {
      error.textContent = err.status === 429 ? t('login_rate') : t('login_failed');
    }
  });

  return h('div', { class: 'login' },
    h('div', { class: 'login-card card' },
      h('img', { src: '/icon.svg', alt: '', width: 56, height: 56 }),
      h('h1', null, t('login_title')),
      form));
}
