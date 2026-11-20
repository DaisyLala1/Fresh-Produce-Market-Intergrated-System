/* Fresh Produce Market POS – shared helpers (API client, formatting, UI utilities). */
'use strict';

const FP = (() => {
  const state = { user: null };

  // ---------------------------------------------------------------- API client
  function getCookie(name) {
    const match = document.cookie.split('; ').find(c => c.startsWith(name + '='));
    return match ? decodeURIComponent(match.split('=')[1]) : null;
  }

  /**
   * Calls the Spring Boot REST API. Sends the CSRF token on every state-changing request and
   * redirects to the login page when the session has expired.
   */
  async function api(method, url, body, opts = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== null && body !== undefined) headers['Content-Type'] = 'application/json';
    if (method !== 'GET') {
      let token = getCookie('XSRF-TOKEN');
      if (!token) {
        await fetch('/api/auth/csrf', { credentials: 'same-origin' }).catch(() => {});
        token = getCookie('XSRF-TOKEN');
      }
      if (token) headers['X-XSRF-TOKEN'] = token;
    }
    let res;
    try {
      res = await fetch(url, {
        method, headers, credentials: 'same-origin',
        body: body !== null && body !== undefined ? JSON.stringify(body) : undefined
      });
    } catch (e) {
      throw new Error('Cannot reach the server. Check your connection and try again.');
    }
    if (res.status === 401 && !opts.noRedirect) {
      location.replace('index.html');
      throw new Error('Your session has expired. Please log in again.');
    }
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
    if (!res.ok) {
      const err = new Error((data && data.message) || `Request failed (${res.status})`);
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  // ---------------------------------------------------------------- formatting
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[c]);

  const money = (n) => 'R ' + Number(n || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  const num = (n, digits = 2) => {
    const v = Number(n || 0);
    return v.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: digits });
  };

  const qty = (n, unit) => `${num(n)}${unit ? ' ' + esc(unit) : ''}`;

  const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

  function parseDate(v) {
    if (!v) return null;
    if (v instanceof Date) return v;
    // "2026-09-16" must be read as a local date, not UTC midnight.
    if (/^\d{4}-\d{2}-\d{2}$/.test(v)) {
      const [y, m, d] = v.split('-').map(Number);
      return new Date(y, m - 1, d);
    }
    return new Date(v);
  }

  const date = (v) => {
    const d = parseDate(v);
    return d ? `${String(d.getDate()).padStart(2, '0')} ${MONTHS[d.getMonth()]} ${d.getFullYear()}` : '';
  };
  const dayLabel = (v) => {
    const d = parseDate(v);
    return d ? `${String(d.getDate()).padStart(2, '0')} ${MONTHS[d.getMonth()]} (${DAYS[d.getDay()]})` : '';
  };
  const shortDate = (v) => {
    const d = parseDate(v);
    return d ? `${String(d.getDate()).padStart(2, '0')} ${MONTHS[d.getMonth()]}` : '';
  };
  const dateTime = (v) => {
    const d = parseDate(v);
    return d ? `${date(d)} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` : '';
  };

  const isoDate = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  const today = () => isoDate(new Date());
  const daysAgo = (n) => { const d = new Date(); d.setDate(d.getDate() - n); return isoDate(d); };

  const EMOJI = {
    tomato: '🍅', potato: '🥔', onion: '🧅', carrot: '🥕', pepper: '🫑', cabbage: '🥬', spinach: '🥬', lettuce: '🥬',
    banana: '🍌', apple: '🍎', orange: '🍊', avocado: '🥑', plantain: '🍌', grape: '🍇', lemon: '🍋', mango: '🥭',
    pear: '🍐', pineapple: '🍍', watermelon: '🍉', melon: '🍈', strawberr: '🍓', corn: '🌽', mealie: '🌽',
    broccoli: '🥦', cucumber: '🥒', garlic: '🧄', pumpkin: '🎃', butternut: '🎃', peach: '🍑', cherr: '🍒',
    chilli: '🌶️', chili: '🌶️', mushroom: '🍄', coconut: '🥥', kiwi: '🥝', bean: '🫘', pea: '🫛'
  };
  function emoji(name) {
    const n = String(name || '').toLowerCase();
    const key = Object.keys(EMOJI).find(k => n.includes(k));
    return key ? EMOJI[key] : '🧺';
  }

  function statusBadge(status) {
    const map = {
      ACTIVE: 'success', DEPLETED: 'secondary', INACTIVE: 'secondary', COMPLETED: 'success',
      RECEIPT: 'primary', SALE: 'success', ADJUSTMENT: 'warning', EARNED: 'success'
    };
    return `<span class="badge badge-status text-bg-${map[status] || 'light'}">${esc(status)}</span>`;
  }

  // ---------------------------------------------------------------- UI helpers
  function toast(message, type = 'success') {
    let box = document.getElementById('toastBox');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toastBox';
      box.className = 'toast-container position-fixed top-0 end-0 p-3';
      document.body.appendChild(box);
    }
    const el = document.createElement('div');
    const color = { success: 'text-bg-success', danger: 'text-bg-danger', warning: 'text-bg-warning', info: 'text-bg-info' }[type] || 'text-bg-dark';
    el.className = `toast align-items-center border-0 ${color}`;
    el.setAttribute('role', 'alert');
    el.innerHTML = `<div class="d-flex"><div class="toast-body">${esc(message)}</div>
      <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button></div>`;
    box.appendChild(el);
    const t = new bootstrap.Toast(el, { delay: type === 'danger' ? 7000 : 3500 });
    el.addEventListener('hidden.bs.toast', () => el.remove());
    t.show();
  }

  /** Opens a Bootstrap modal. Returns { el, close }. */
  function modal({ title, body, footer = '', size = '' }) {
    const wrap = document.createElement('div');
    wrap.className = 'modal fade';
    wrap.tabIndex = -1;
    wrap.innerHTML = `
      <div class="modal-dialog modal-dialog-scrollable ${size}">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">${title}</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button></div>
          <div class="modal-body">${body}</div>
          ${footer ? `<div class="modal-footer">${footer}</div>` : ''}
        </div>
      </div>`;
    document.body.appendChild(wrap);
    const m = new bootstrap.Modal(wrap);
    wrap.addEventListener('hidden.bs.modal', () => { m.dispose(); wrap.remove(); });
    m.show();
    return { el: wrap, close: () => m.hide() };
  }

  function confirm(title, message, okLabel = 'Confirm', okClass = 'btn-fp') {
    return new Promise(resolve => {
      let answered = false;
      const m = modal({
        title: esc(title), body: `<p class="mb-0">${message}</p>`,
        footer: `<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button>
                 <button class="btn ${okClass}" data-ok>${esc(okLabel)}</button>`
      });
      m.el.querySelector('[data-ok]').addEventListener('click', () => { answered = true; resolve(true); m.close(); });
      m.el.addEventListener('hidden.bs.modal', () => { if (!answered) resolve(false); });
    });
  }

  /** Shows a validation/server error inside a container (e.g. a form). */
  function showError(container, err) {
    let box = container.querySelector('.fp-error');
    if (!box) {
      box = document.createElement('div');
      box.className = 'alert alert-danger fp-error py-2';
      container.prepend(box);
    }
    box.textContent = err.message || String(err);
    box.scrollIntoView({ block: 'nearest' });
  }
  function clearError(container) {
    const box = container.querySelector('.fp-error');
    if (box) box.remove();
  }

  const loading = (text = 'Loading…') =>
    `<div class="text-center text-muted py-5"><div class="spinner-border spinner-border-sm text-success me-2"></div>${esc(text)}</div>`;

  const errorBlock = (err) => `<div class="alert alert-danger">${esc(err.message || err)}</div>`;

  /** Disables a button and shows a spinner while an async action runs. */
  async function busy(button, fn) {
    const html = button.innerHTML;
    button.disabled = true;
    button.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span>${button.textContent.trim()}`;
    try { return await fn(); } finally { button.disabled = false; button.innerHTML = html; }
  }

  function downloadCsv(filename, rows) {
    const csv = rows.map(r => r.map(v => {
      const s = String(v ?? '');
      return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    }).join(',')).join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 500);
  }

  const formData = (form) => Object.fromEntries(new FormData(form).entries());

  const isAdmin = () => state.user && state.user.role === 'ADMINISTRATOR';

  return {
    state, api, esc, money, num, qty, date, dayLabel, shortDate, dateTime, parseDate, today, daysAgo, isoDate,
    emoji, statusBadge, toast, modal, confirm, showError, clearError, loading, errorBlock, busy, downloadCsv,
    formData, isAdmin
  };
})();

/** Page renderers register themselves here: Pages.name = async (container, param) => {...} */
const Pages = {};
