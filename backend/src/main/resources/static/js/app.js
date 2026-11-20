/* Router and application shell. */
'use strict';

(() => {
  const view = document.getElementById('view');
  let charts = [];

  /** Charts register here so they are destroyed when the page changes. */
  FP.trackChart = (chart) => { charts.push(chart); return chart; };

  function parseHash() {
    const parts = (location.hash || '#/dashboard').replace(/^#\/?/, '').split('/').filter(Boolean);
    return { page: parts[0] || 'dashboard', params: parts.slice(1).map(decodeURIComponent) };
  }

  async function render() {
    const { page, params } = parseHash();
    charts.forEach(c => c.destroy());
    charts = [];
    document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('active', a.dataset.route === page));
    document.getElementById('sidebar').classList.remove('open');

    const renderer = Pages[page];
    if (!renderer) {
      view.innerHTML = `<div class="alert alert-warning">Page not found. <a href="#/dashboard">Go to dashboard</a></div>`;
      return;
    }
    view.innerHTML = FP.loading();
    window.scrollTo(0, 0);
    try {
      await renderer(view, params);
    } catch (err) {
      if (err.status === 403) {
        view.innerHTML = `<div class="alert alert-danger"><i class="bi bi-shield-lock me-2"></i>Access denied – this function is only available to Administrators.</div>`;
      } else {
        view.innerHTML = FP.errorBlock(err);
      }
    }
  }

  async function start() {
    try {
      FP.state.user = await FP.api('GET', '/api/auth/me');
    } catch (e) {
      return; // api() already redirected to the login page
    }
    const u = FP.state.user;
    document.getElementById('userName').textContent = u.fullName;
    document.getElementById('userRole').textContent = u.role === 'ADMINISTRATOR' ? 'Administrator' : 'Agent · Market Staff';
    if (FP.isAdmin()) document.querySelectorAll('[data-admin-only]').forEach(el => el.classList.remove('d-none'));

    document.getElementById('logoutLink').addEventListener('click', async (e) => {
      e.preventDefault();
      try { await FP.api('POST', '/api/auth/logout', null, { noRedirect: true }); } catch (err) { /* ignore */ }
      location.replace('index.html');
    });
    document.getElementById('menuBtn').addEventListener('click', () =>
      document.getElementById('sidebar').classList.toggle('open'));
    document.getElementById('globalSearch').addEventListener('submit', (e) => {
      e.preventDefault();
      const ref = document.getElementById('globalSearchInput').value.trim();
      if (ref) location.hash = `#/consignments/ref/${encodeURIComponent(ref)}`;
    });

    window.addEventListener('hashchange', render);
    render();
  }

  start();
})();
