/* Commissions – UC-10 Configure Commission Rates and agent commission summary */
'use strict';

Pages.commissions = async (view) => {
  const admin = FP.isAdmin();
  const from = FP.daysAgo(29);
  const to = FP.today();
  const [current, history, report] = await Promise.all([
    FP.api('GET', '/api/commission-rates/current').catch(() => null),
    FP.api('GET', '/api/commission-rates'),
    FP.api('GET', `/api/reports/commissions?from=${from}&to=${to}`)]);

  view.innerHTML = `
    <div class="mb-3"><h1 class="page-title">Commissions</h1>
      <div class="page-sub">Agent commission is calculated on every sale using the rate active at the time of the sale</div></div>
    <div class="row g-3 mb-3">
      <div class="col-md-5"><div class="card h-100"><div class="card-header">Current commission rate</div><div class="card-body">
        <div class="display-5 fw-bold text-fp">${current ? FP.num(current.ratePercent) + '%' : 'Not set'}</div>
        <div class="text-muted small mb-3">${current ? `Effective since ${FP.dateTime(current.effectiveFrom)}${current.setBy ? ' · set by ' + FP.esc(current.setBy) : ''}` : 'An Administrator must configure a rate before sales can be processed.'}</div>
        <div class="small text-muted mb-3">Example: a sale of R1,000.00 earns ${FP.money(1000 * (current ? Number(current.ratePercent) : 0) / 100)} commission.</div>
        ${admin ? `<form id="rateForm" class="d-flex gap-2 align-items-start" novalidate>
            <div class="input-group" style="max-width:200px"><input name="rate" type="number" min="0" max="100" step="0.01" class="form-control" placeholder="New rate" required>
            <span class="input-group-text">%</span></div>
            <button class="btn btn-fp">Update rate</button></form>
            <div class="form-text">The new rate applies to subsequent sales only. Past commissions are not changed.</div>`
          : '<div class="alert alert-light border small mb-0"><i class="bi bi-lock me-1"></i>Only Administrators can change the commission rate.</div>'}
      </div></div></div>
      <div class="col-md-7"><div class="card h-100"><div class="card-header">${admin ? 'Commission by agent' : 'My commission'} (last 30 days)</div>
        <div class="table-responsive"><table class="table mb-0">
          <thead><tr><th>Agent</th><th class="text-end">Sales</th><th class="text-end">Sales value</th><th class="text-end">Commission</th></tr></thead>
          <tbody>${report.byAgent.length ? report.byAgent.map(a => `<tr><td>${FP.esc(a.agentName)}</td><td class="text-end">${a.sales}</td>
            <td class="text-end">${FP.money(a.salesValue)}</td><td class="text-end fw-semibold">${FP.money(a.commission)}</td></tr>`).join('')
            : '<tr><td colspan="4" class="empty">No commission earned in this period</td></tr>'}</tbody>
          <tfoot><tr class="total-row"><td colspan="3">Total</td><td class="text-end">${FP.money(report.totalCommission)}</td></tr></tfoot>
        </table></div></div></div>
    </div>
    <div class="card"><div class="card-header">Rate history</div><div class="table-responsive"><table class="table mb-0">
      <thead><tr><th>Effective from</th><th class="text-end">Rate</th><th>Set by</th><th>Status</th></tr></thead>
      <tbody>${history.map(r => `<tr><td>${FP.dateTime(r.effectiveFrom)}</td><td class="text-end">${FP.num(r.ratePercent)}%</td>
        <td>${FP.esc(r.setBy || '–')}</td><td>${r.active ? FP.statusBadge('ACTIVE') : '<span class="badge text-bg-light">Replaced</span>'}</td></tr>`).join('')}</tbody>
    </table></div></div>`;

  const form = view.querySelector('#rateForm');
  if (form) {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const btn = form.querySelector('button');
      FP.clearError(form.parentElement);
      const rate = Number(form.rate.value);
      if (form.rate.value === '' || isNaN(rate) || rate < 0 || rate > 100) {
        return FP.showError(form.parentElement, new Error('Enter a rate between 0 and 100'));
      }
      if (!await FP.confirm('Change commission rate', `Change the commission rate to <strong>${FP.num(rate)}%</strong>? It applies to all sales from now on.`, 'Change rate')) return;
      try {
        await FP.busy(btn, () => FP.api('PUT', '/api/commission-rates', { ratePercent: rate }));
        FP.toast(`Commission rate changed to ${FP.num(rate)}%`);
        Pages.commissions(view);
      } catch (err) { FP.showError(form.parentElement, err); }
    });
  }
};
