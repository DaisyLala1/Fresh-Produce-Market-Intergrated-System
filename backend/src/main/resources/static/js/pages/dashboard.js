/* Dashboard */
'use strict';

Pages.dashboard = async (view) => {
  const d = await FP.api('GET', '/api/dashboard');
  const u = FP.state.user;
  const change = d.changePercent === null
    ? '<span class="text-muted">no sales yesterday</span>'
    : `<span class="${d.changePercent >= 0 ? 'text-success' : 'text-danger'}">
         <i class="bi bi-arrow-${d.changePercent >= 0 ? 'up' : 'down'}"></i> ${Math.abs(d.changePercent)}%</span> vs yesterday`;

  view.innerHTML = `
    <div class="d-flex flex-wrap justify-content-between align-items-end mb-3 gap-2">
      <div><h1 class="page-title">Dashboard</h1><div class="page-sub">Welcome back, ${FP.esc(u.fullName.split(' ')[0])}!</div></div>
      <span class="badge text-bg-light border p-2"><i class="bi bi-calendar3 me-1"></i>${FP.date(new Date())}</span>
    </div>

    <div class="row g-3 mb-3">
      <div class="col-sm-6 col-xl-3"><div class="card stat-card">
        <div class="stat-icon bg-fp-green"><i class="bi bi-cart3"></i></div>
        <div><div class="stat-label">Total Sales (Today)</div><div class="stat-value">${FP.money(d.salesToday)}</div>
        <div class="small">${change}</div></div></div></div>
      <div class="col-sm-6 col-xl-3"><div class="card stat-card">
        <div class="stat-icon bg-fp-blue"><i class="bi bi-box-seam"></i></div>
        <div><div class="stat-label">Active Consignments</div><div class="stat-value">${d.activeConsignments}</div>
        <div class="small text-muted">with stock remaining</div></div></div></div>
      <div class="col-sm-6 col-xl-3"><div class="card stat-card">
        <div class="stat-icon bg-fp-amber"><i class="bi bi-boxes"></i></div>
        <div><div class="stat-label">Products in Stock</div><div class="stat-value">${d.productsInStock}</div>
        <div class="small text-muted">in inventory</div></div></div></div>
      <div class="col-sm-6 col-xl-3"><div class="card stat-card">
        <div class="stat-icon bg-fp-red"><span style="font-weight: bold; font-family: sans-serif; color: white; font-size: 1.5rem;">R</span></div>
        <div><div class="stat-label">My Commission (Earned)</div><div class="stat-value">${FP.money(d.myCommissionToday)}</div>
        <div class="small text-muted">today</div></div></div></div>
    </div>

    <div class="row g-3 mb-3">
      <div class="col-lg-7"><div class="card h-100">
        <div class="card-header d-flex justify-content-between"><span>Recent Sales</span>
          <a href="#/reports" class="small text-fp text-decoration-none">View all <i class="bi bi-chevron-right"></i></a></div>
        <div class="table-responsive"><table class="table table-hover mb-0">
          <thead><tr><th>Date</th><th>Receipt</th><th>Products</th><th class="text-end">Total</th><th>Agent</th></tr></thead>
          <tbody>${d.recentSales.length ? d.recentSales.map(s => `
            <tr class="clickable" data-sale="${s.id}">
              <td class="text-nowrap">${FP.dateTime(s.saleDate)}</td>
              <td class="text-nowrap small">${FP.esc(s.receiptNumber)}</td>
              <td class="small text-truncate" style="max-width:160px" title="${FP.esc(s.productsSummary)}">${FP.esc(s.productsSummary)}</td>
              <td class="text-end">${FP.money(s.totalAmount)}</td>
              <td class="small text-nowrap">${FP.esc(s.agentName)}</td></tr>`).join('')
            : '<tr><td colspan="5" class="empty">No sales yet</td></tr>'}</tbody>
        </table></div></div></div>
      <div class="col-lg-5"><div class="card h-100">
        <div class="card-header">Sales Summary (Last 7 Days)</div>
        <div class="card-body"><div class="chart-box"><canvas id="weekChart"></canvas></div></div></div></div>
    </div>

    <div class="row g-3">
      <div class="col-sm-6 col-xl-3"><a class="quick-action tint-green" href="#/consignments/new">
        <i class="bi bi-box-arrow-in-down big text-success"></i><div><strong>New Consignment</strong><div class="small text-muted">Record incoming produce</div></div></a></div>
      <div class="col-sm-6 col-xl-3"><a class="quick-action tint-blue" href="#/pos">
        <i class="bi bi-cart-plus big text-primary"></i><div><strong>Process Sale</strong><div class="small text-muted">Sell products at the POS</div></div></a></div>
      <div class="col-sm-6 col-xl-3"><a class="quick-action tint-amber" href="#/inventory">
        <i class="bi bi-clipboard-data big text-warning"></i><div><strong>View Inventory</strong><div class="small text-muted">Stock levels and movements</div></div></a></div>
      <div class="col-sm-6 col-xl-3"><a class="quick-action tint-purple" href="#/forecasting">
        <i class="bi bi-graph-up-arrow big" style="color:var(--fp-purple)"></i><div><strong>Forecast Sales</strong><div class="small text-muted">AI demand predictions</div></div></a></div>
    </div>`;

  view.querySelectorAll('[data-sale]').forEach(tr =>
    tr.addEventListener('click', () => Pages.showSale(tr.dataset.sale)));

  FP.trackChart(new Chart(document.getElementById('weekChart'), {
    type: 'bar',
    data: {
      labels: d.lastSevenDays.map(x => FP.shortDate(x.date)),
      datasets: [{ label: 'Sales (R)', data: d.lastSevenDays.map(x => Number(x.total)), backgroundColor: '#2e9a5f', borderRadius: 4 }]
    },
    options: {
      maintainAspectRatio: false,
      plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => FP.money(c.parsed.y) } } },
      scales: { y: { beginAtZero: true, ticks: { callback: v => 'R' + Number(v).toLocaleString('en-US') } }, x: { grid: { display: false } } }
    }
  }));
};

/** Shared sale-detail modal (used by the dashboard, reports and the POS receipt). */
Pages.showSale = async (id) => {
  const s = await FP.api('GET', `/api/sales/${id}`);
  Pages.showReceipt(s);
};

Pages.showReceipt = (s, isNew = false) => {
  const rows = s.items.map(i => `
    <tr><td>${FP.esc(i.productName)}<div class="text-muted" style="font-size:.72rem">
      ${i.allocations.map(a => `FIFO: ${FP.esc(a.consignmentReference)} (${FP.esc(a.farmerName)}) – ${FP.num(a.quantity)}`).join('<br>')}</div></td>
      <td class="text-end">${FP.num(i.quantity)} ${FP.esc(i.unitOfMeasure)}</td>
      <td class="text-end">${FP.money(i.unitPrice)}</td><td class="text-end">${FP.money(i.subtotal)}</td></tr>`).join('');
  const m = FP.modal({
    title: isNew ? '<i class="bi bi-check-circle-fill text-success me-2"></i>Sale completed' : 'Sale receipt',
    size: 'modal-lg',
    body: `<div class="print-area receipt">
      <div class="text-center"><strong>FRESH PRODUCE MARKET</strong><br>POS &amp; Consignment System</div><hr>
      <div class="d-flex justify-content-between"><span>Receipt: <strong>${FP.esc(s.receiptNumber)}</strong></span><span>${FP.dateTime(s.saleDate)}</span></div>
      <div>Agent: ${FP.esc(s.agentName)}</div><hr>
      <table class="table table-sm mb-0"><thead><tr><th>Item</th><th class="text-end">Qty</th><th class="text-end">Price</th><th class="text-end">Total</th></tr></thead>
      <tbody>${rows}</tbody></table><hr>
      <div class="d-flex justify-content-between"><span>Subtotal</span><span>${FP.money(s.subTotal)}</span></div>
      <div class="d-flex justify-content-between"><span>Discount</span><span>${FP.money(s.discount)}</span></div>
      <div class="d-flex justify-content-between fs-5 fw-bold"><span>TOTAL</span><span>${FP.money(s.totalAmount)}</span></div>
      ${s.commissionAmount !== null ? `<div class="d-flex justify-content-between text-muted small mt-1">
        <span>Agent commission (${FP.num(s.commissionRate)}%)</span><span>${FP.money(s.commissionAmount)}</span></div>` : ''}
      <hr><div class="text-center small">Thank you!</div></div>`,
    footer: `<button class="btn btn-light" data-bs-dismiss="modal">Close</button>
             <button class="btn btn-fp" data-print><i class="bi bi-printer me-1"></i>Print receipt</button>`
  });
  m.el.querySelector('[data-print]').addEventListener('click', () => window.print());
  return m;
};