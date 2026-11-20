/* Reports – UC-04 Generate Reports (sales, consignments, inventory, commissions) */
'use strict';

Pages.reports = async (view) => {
  view.innerHTML = `
    <div class="d-flex flex-wrap justify-content-between align-items-end mb-3 gap-2">
      <div><h1 class="page-title">Reports</h1><div class="page-sub">Sales, consignment, inventory and commission reports</div></div>
    </div>
    <div class="card mb-3 no-print"><div class="card-body">
      <form class="row g-3 align-items-end" id="rForm">
        <div class="col-md-3"><label class="form-label fw-semibold">Report</label>
          <select class="form-select" name="type">
            <option value="sales">Sales report</option><option value="consignments">Consignment report</option>
            <option value="inventory">Inventory report</option><option value="commissions">Commission report</option></select></div>
        <div class="col-md-3 date-field"><label class="form-label fw-semibold">From</label>
          <input type="date" class="form-control" name="from" value="${FP.daysAgo(29)}" max="${FP.today()}"></div>
        <div class="col-md-3 date-field"><label class="form-label fw-semibold">To</label>
          <input type="date" class="form-control" name="to" value="${FP.today()}" max="${FP.today()}"></div>
        <div class="col-md-3 d-flex gap-2">
          <button class="btn btn-fp flex-grow-1" id="runBtn"><i class="bi bi-play-fill me-1"></i>Generate</button>
          <button class="btn btn-outline-secondary" type="button" id="csvBtn" title="Export CSV" disabled><i class="bi bi-filetype-csv"></i></button>
          <button class="btn btn-outline-secondary" type="button" id="printBtn" title="Print" disabled><i class="bi bi-printer"></i></button>
        </div>
      </form>
    </div></div>
    <div id="out"></div>`;

  const form = view.querySelector('#rForm');
  const out = view.querySelector('#out');
  let csvRows = null;
  let csvName = 'report.csv';

  form.type.addEventListener('change', () => {
    view.querySelectorAll('.date-field').forEach(el => el.classList.toggle('d-none', form.type.value === 'inventory'));
  });
  view.querySelector('#csvBtn').addEventListener('click', () => csvRows && FP.downloadCsv(csvName, csvRows));
  view.querySelector('#printBtn').addEventListener('click', () => window.print());

  const card = (title, inner) => `<div class="card mb-3"><div class="card-header">${title}</div>${inner}</div>`;
  const table = (head, rows, foot = '') => `<div class="table-responsive"><table class="table table-sm table-hover mb-0">
    <thead><tr>${head}</tr></thead><tbody>${rows || '<tr><td colspan="12" class="empty">No records for this period</td></tr>'}</tbody>${foot}</table></div>`;
  const stat = (label, value) => `<div class="col-6 col-md-3"><div class="card stat-card"><div><div class="stat-label">${label}</div><div class="stat-value">${value}</div></div></div></div>`;

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const { type, from, to } = FP.formData(form);
    if (type !== 'inventory' && from > to) return FP.toast('"From" must be on or before "To"', 'danger');
    const qs = type === 'inventory' ? '' : `?from=${from}&to=${to}`;
    out.innerHTML = FP.loading('Generating report…');
    try {
      const r = await FP.busy(view.querySelector('#runBtn'), () => FP.api('GET', `/api/reports/${type}${qs}`));
      const period = type === 'inventory' ? `as at ${FP.date(r.asOf)}` : `${FP.date(r.from)} – ${FP.date(r.to)}`;
      const header = `<div class="mb-3"><h2 class="h5 mb-0">${form.type.selectedOptions[0].text}</h2>
        <div class="text-muted small">${period} · generated ${FP.dateTime(new Date())} by ${FP.esc(FP.state.user.fullName)}</div></div>`;
      out.innerHTML = `<div class="print-area">${header}${renderers[type](r)}</div>`;
      out.querySelectorAll('[data-sale]').forEach(tr => tr.addEventListener('click', () => Pages.showSale(tr.dataset.sale)));
      csvName = `${type}-report-${type === 'inventory' ? FP.today() : from + '_to_' + to}.csv`;
      view.querySelector('#csvBtn').disabled = false;
      view.querySelector('#printBtn').disabled = false;
    } catch (err) { out.innerHTML = FP.errorBlock(err); }
  });

  const renderers = {
    sales(r) {
      csvRows = [['Receipt', 'Date', 'Agent', 'Products', 'Items', 'Total (R)', 'Commission (R)'],
        ...r.sales.map(s => [s.receiptNumber, FP.dateTime(s.saleDate), s.agentName, s.productsSummary, s.itemCount,
          Number(s.totalAmount).toFixed(2), Number(s.commissionAmount).toFixed(2)])];
      return `<div class="row g-3 mb-3">${stat('Transactions', r.transactions)}${stat('Revenue', FP.money(r.totalRevenue))}
          ${stat('Discounts', FP.money(r.totalDiscount))}${stat('Commission', FP.money(r.totalCommission))}</div>
        <div class="row g-3">
          <div class="col-lg-6">${card('Sales by product', table('<th>Product</th><th class="text-end">Quantity</th><th class="text-end">Revenue</th>',
            r.byProduct.map(p => `<tr><td>${FP.emoji(p.productName)} ${FP.esc(p.productName)}</td>
              <td class="text-end">${FP.qty(p.quantity, p.unitOfMeasure)}</td><td class="text-end">${FP.money(p.revenue)}</td></tr>`).join('')))}</div>
          <div class="col-lg-6">${card('Sales by day', `<div class="table-responsive" style="max-height:420px">${table('<th>Date</th><th class="text-end">Transactions</th><th class="text-end">Revenue</th>',
            [...r.byDay].reverse().map(d => `<tr><td>${FP.dayLabel(d.date)}</td><td class="text-end">${d.transactions}</td>
              <td class="text-end">${FP.money(d.revenue)}</td></tr>`).join(''))}</div>`)}</div>
        </div>
        ${card(`Transactions (${r.sales.length})`, `<div style="max-height:480px;overflow:auto">${table(
          '<th>Receipt</th><th>Date</th><th>Agent</th><th>Products</th><th class="text-end">Total</th><th class="text-end">Commission</th>',
          r.sales.map(s => `<tr class="clickable" data-sale="${s.id}"><td class="small">${FP.esc(s.receiptNumber)}</td>
            <td class="text-nowrap">${FP.dateTime(s.saleDate)}</td><td>${FP.esc(s.agentName)}</td>
            <td class="small text-truncate" style="max-width:260px">${FP.esc(s.productsSummary)}</td>
            <td class="text-end">${FP.money(s.totalAmount)}</td><td class="text-end">${FP.money(s.commissionAmount)}</td></tr>`).join(''))}</div>`)}`;
    },

    consignments(r) {
      csvRows = [['Reference', 'Received', 'Farmer', 'Product', 'Unit', 'Received qty', 'Sold', 'Adjusted', 'Remaining', 'Sales value (R)', 'Status'],
        ...r.consignments.map(c => [c.referenceNumber, c.dateReceived, c.farmerName, c.productName, c.unitOfMeasure,
          c.quantityReceived, c.quantitySold, c.quantityAdjusted, c.quantityRemaining, Number(c.salesValue).toFixed(2), c.status])];
      return `${card('Summary by farmer', table('<th>Farmer</th><th class="text-end">Consignments</th><th class="text-end">Quantity received</th><th class="text-end">Quantity sold</th><th class="text-end">Sales value</th>',
          r.byFarmer.map(f => `<tr><td>${FP.esc(f.farmerName)}</td><td class="text-end">${f.consignments}</td>
            <td class="text-end">${FP.num(f.quantityReceived)}</td><td class="text-end">${FP.num(f.quantitySold)}</td>
            <td class="text-end">${FP.money(f.salesValue)}</td></tr>`).join('')))}
        ${card(`Consignments received (${r.consignments.length})`, table(
          '<th>Reference</th><th>Received</th><th>Farmer</th><th>Product</th><th class="text-end">Received</th><th class="text-end">Sold</th><th class="text-end">Adjusted</th><th class="text-end">Remaining</th><th class="text-end">Sales value</th><th>Status</th>',
          r.consignments.map(c => `<tr><td><a href="#/consignments/ref/${encodeURIComponent(c.referenceNumber)}" class="text-fp">${FP.esc(c.referenceNumber)}</a></td>
            <td class="text-nowrap">${FP.date(c.dateReceived)}</td><td>${FP.esc(c.farmerName)}</td><td>${FP.esc(c.productName)}</td>
            <td class="text-end">${FP.qty(c.quantityReceived, c.unitOfMeasure)}</td><td class="text-end">${FP.num(c.quantitySold)}</td>
            <td class="text-end">${FP.num(c.quantityAdjusted)}</td><td class="text-end">${FP.num(c.quantityRemaining)}</td>
            <td class="text-end">${FP.money(c.salesValue)}</td><td>${FP.statusBadge(c.status)}</td></tr>`).join('')))}`;
    },

    inventory(r) {
      csvRows = [['Product', 'Category', 'Unit', 'Available', 'Active consignments', 'Oldest received', 'Selling price (R)', 'Stock value (R)'],
        ...r.products.map(p => [p.productName, p.category, p.unitOfMeasure, p.quantityAvailable, p.activeConsignments,
          p.oldestReceived || '', Number(p.sellingPrice).toFixed(2), Number(p.stockValue).toFixed(2)])];
      return `<div class="row g-3 mb-3">${stat('Total stock value', FP.money(r.totalStockValue))}
          ${stat('Products in stock', r.products.filter(p => Number(p.quantityAvailable) > 0).length)}
          ${stat('Active consignments', r.activeConsignments.length)}
          ${stat('Oldest stock (days)', r.activeConsignments.length ? Math.max(...r.activeConsignments.map(c => c.ageDays)) : 0)}</div>
        ${card('Stock by product', table('<th>Product</th><th>Category</th><th class="text-end">Available</th><th class="text-end">Consignments</th><th class="text-end">Stock value</th>',
          r.products.map(p => `<tr><td>${FP.emoji(p.productName)} ${FP.esc(p.productName)}</td><td>${FP.esc(p.category)}</td>
            <td class="text-end">${FP.qty(p.quantityAvailable, p.unitOfMeasure)}</td><td class="text-end">${p.activeConsignments}</td>
            <td class="text-end">${FP.money(p.stockValue)}</td></tr>`).join(''),
          `<tfoot><tr class="total-row"><td colspan="4">Total</td><td class="text-end">${FP.money(r.totalStockValue)}</td></tr></tfoot>`))}
        ${card('Stock age by consignment (oldest first – sold first under FIFO)', table(
          '<th>Reference</th><th>Product</th><th>Farmer</th><th>Received</th><th class="text-end">Age (days)</th><th class="text-end">Remaining</th>',
          r.activeConsignments.map(c => `<tr><td>${FP.esc(c.referenceNumber)}</td><td>${FP.esc(c.productName)}</td><td>${FP.esc(c.farmerName)}</td>
            <td>${FP.date(c.dateReceived)}</td><td class="text-end ${c.ageDays > 7 ? 'text-danger fw-semibold' : ''}">${c.ageDays}</td>
            <td class="text-end">${FP.qty(c.quantityRemaining, c.unitOfMeasure)}</td></tr>`).join('')))}`;
    },

    commissions(r) {
      csvRows = [['Agent', 'Sales', 'Sales value (R)', 'Commission (R)'],
        ...r.byAgent.map(a => [a.agentName, a.sales, Number(a.salesValue).toFixed(2), Number(a.commission).toFixed(2)])];
      return card(FP.isAdmin() ? 'Commission by agent' : 'My commission', table(
        '<th>Agent</th><th class="text-end">Sales</th><th class="text-end">Sales value</th><th class="text-end">Commission</th>',
        r.byAgent.map(a => `<tr><td>${FP.esc(a.agentName)}</td><td class="text-end">${a.sales}</td>
          <td class="text-end">${FP.money(a.salesValue)}</td><td class="text-end">${FP.money(a.commission)}</td></tr>`).join(''),
        `<tfoot><tr class="total-row"><td colspan="3">Total commission</td><td class="text-end">${FP.money(r.totalCommission)}</td></tr></tfoot>`));
    }
  };

  form.requestSubmit();
};
