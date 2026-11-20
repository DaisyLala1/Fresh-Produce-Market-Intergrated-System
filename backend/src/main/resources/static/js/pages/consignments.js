/* Consignments – UC-01 Record Consignment, UC-09 Search Consignment, consignment traceability */
'use strict';

Pages.consignments = async (view, params) => {
  if (params[0] === 'new') return renderNew(view);
  if (params[0] === 'ref') return renderDetail(view, `/api/consignments/search?reference=${encodeURIComponent(params[1] || '')}`);
  if (params[0] === 'farmer') return renderList(view, { farmerId: params[1] });
  if (params[0] === 'product') return renderList(view, { productId: params[1] });
  if (params[0]) return renderDetail(view, `/api/consignments/${encodeURIComponent(params[0])}`);
  return renderList(view, {});

  // ------------------------------------------------------------------ list
  async function renderList(view, filters) {
    const [products, farmers] = await Promise.all([
      FP.api('GET', '/api/products'), FP.api('GET', '/api/farmers')]);
    const farmer = filters.farmerId ? farmers.find(f => String(f.id) === String(filters.farmerId)) : null;

    view.innerHTML = `
      <div class="d-flex flex-wrap justify-content-between align-items-end mb-3 gap-2">
        <div><h1 class="page-title">Consignments</h1>
          <div class="page-sub">${farmer ? `Consignments supplied by <strong>${FP.esc(farmer.name)}</strong> · <a href="#/consignments">show all</a>`
            : 'Produce received from farmers, traceable by reference number'}</div></div>
        <a class="btn btn-fp" href="#/consignments/new"><i class="bi bi-plus-lg me-1"></i>New Consignment</a>
      </div>
      <div class="card mb-3"><div class="card-body">
        <form class="row g-2 align-items-end" id="refSearch">
          <div class="col-md-5"><label class="form-label small fw-semibold">Search by consignment reference</label>
            <input class="form-control" name="ref" placeholder="e.g. CON-2026-00001" required></div>
          <div class="col-auto"><button class="btn btn-outline-fp"><i class="bi bi-search me-1"></i>Search</button></div>
          <div class="col-md-2 ms-md-auto"><label class="form-label small fw-semibold">Status</label>
            <select class="form-select" id="fStatus"><option value="">All</option><option>ACTIVE</option><option>DEPLETED</option></select></div>
          <div class="col-md-3"><label class="form-label small fw-semibold">Product</label>
            <select class="form-select" id="fProduct"><option value="">All products</option>
              ${products.map(p => `<option value="${p.id}">${FP.esc(p.name)}</option>`).join('')}</select></div>
        </form>
      </div></div>
      <div class="card"><div class="table-responsive"><table class="table table-hover mb-0">
        <thead><tr><th>Reference</th><th>Received</th><th>Farmer</th><th>Product</th>
          <th class="text-end">Received qty</th><th class="text-end">Remaining</th><th>Status</th></tr></thead>
        <tbody id="rows"><tr><td colspan="7">${FP.loading()}</td></tr></tbody></table></div></div>`;

    const rows = view.querySelector('#rows');
    async function load() {
      const q = new URLSearchParams();
      const status = view.querySelector('#fStatus').value;
      const productId = view.querySelector('#fProduct').value;
      if (status) q.set('status', status);
      if (productId) q.set('productId', productId);
      if (filters.farmerId) q.set('farmerId', filters.farmerId);
      const list = await FP.api('GET', `/api/consignments?${q}`);
      rows.innerHTML = list.length ? list.map(c => `
        <tr class="clickable" data-id="${c.id}">
          <td class="fw-semibold text-fp">${FP.esc(c.referenceNumber)}</td>
          <td>${FP.date(c.dateReceived)}</td>
          <td>${FP.esc(c.farmerName)}</td>
          <td>${FP.emoji(c.productName)} ${FP.esc(c.productName)}</td>
          <td class="text-end">${FP.qty(c.quantityReceived, c.unitOfMeasure)}</td>
          <td class="text-end">${FP.qty(c.quantityRemaining, c.unitOfMeasure)}</td>
          <td>${FP.statusBadge(c.status)}</td></tr>`).join('')
        : '<tr><td colspan="7" class="empty">No consignments found</td></tr>';
      rows.querySelectorAll('[data-id]').forEach(tr =>
        tr.addEventListener('click', () => { location.hash = `#/consignments/${tr.dataset.id}`; }));
    }
    if (filters.productId) view.querySelector('#fProduct').value = filters.productId;
    view.querySelector('#fStatus').addEventListener('change', load);
    view.querySelector('#fProduct').addEventListener('change', load);
    view.querySelector('#refSearch').addEventListener('submit', (e) => {
      e.preventDefault();
      const ref = e.target.ref.value.trim();
      if (ref) location.hash = `#/consignments/ref/${encodeURIComponent(ref)}`;
    });
    await load();
  }

  // ------------------------------------------------------------------ new consignment form
  async function renderNew(view) {
    const [farmers, products] = await Promise.all([
      FP.api('GET', '/api/farmers'), FP.api('GET', '/api/products?activeOnly=true')]);
    const activeFarmers = farmers.filter(f => f.status === 'ACTIVE');

    view.innerHTML = `
      <div class="mb-3"><h1 class="page-title">Record New Consignment</h1>
        <div class="page-sub"><a href="#/consignments" class="text-decoration-none">Consignments</a> › New Consignment</div></div>
      <div class="row g-3">
        <div class="col-lg-8"><div class="card">
          <div class="card-header green">Consignment Details</div>
          <div class="card-body">
            <form id="cForm" novalidate>
              <div class="row g-3">
                <div class="col-md-6"><label class="form-label">Farmer *</label>
                  <select name="farmerId" class="form-select" required><option value="">Select farmer…</option>
                    ${activeFarmers.map(f => `<option value="${f.id}">${FP.esc(f.name)} (${FP.esc(f.farmerCode)})</option>`).join('')}</select>
                  <div class="form-text"><a href="#/farmers">Register a new farmer</a></div></div>
                <div class="col-md-6"><label class="form-label">Consignment reference</label>
                  <input class="form-control" value="Generated automatically on save" disabled></div>
                <div class="col-md-6"><label class="form-label">Product *</label>
                  <select name="productId" class="form-select" required><option value="">Select product…</option>
                    ${products.map(p => `<option value="${p.id}">${FP.emoji(p.name)} ${FP.esc(p.name)}</option>`).join('')}</select></div>
                <div class="col-md-6"><label class="form-label">Grade</label>
                  <select name="grade" class="form-select"><option>Grade A</option><option>Grade B</option><option>Grade C</option></select></div>
                <div class="col-md-4"><label class="form-label">Quantity *</label>
                  <input name="quantityReceived" type="number" min="0.01" step="0.01" class="form-control" required></div>
                <div class="col-md-2"><label class="form-label">Unit</label>
                  <input id="unit" class="form-control" value="–" disabled></div>
                <div class="col-md-6"><label class="form-label">Unit price (R) *</label>
                  <input name="unitPrice" type="number" min="0.01" step="0.01" class="form-control" required></div>
                <div class="col-md-6"><label class="form-label">Received date *</label>
                  <input name="dateReceived" type="date" class="form-control" required value="${FP.today()}" max="${FP.today()}"></div>
                <div class="col-md-6"><label class="form-label">Notes</label>
                  <textarea name="notes" class="form-control" rows="2" maxlength="500" placeholder="e.g. Fresh tomatoes delivered in good condition."></textarea></div>
              </div>
              <div class="d-flex justify-content-between mt-4">
                <a href="#/consignments" class="btn btn-light">Cancel</a>
                <button class="btn btn-fp" id="saveBtn"><i class="bi bi-save me-1"></i>Save Consignment</button>
              </div>
            </form>
          </div></div></div>
        <div class="col-lg-4">
          <div class="card mb-3"><div class="card-header">Product Information</div><div class="card-body" id="pInfo">
            <div class="text-muted small">Select a product</div></div></div>
          <div class="card"><div class="card-header">Farmer Information</div><div class="card-body" id="fInfo">
            <div class="text-muted small">Select a farmer</div></div></div>
        </div>
      </div>`;

    const form = view.querySelector('#cForm');
    const info = (label, value) => `<div class="d-flex justify-content-between small py-1 border-bottom"><span class="text-muted">${label}</span><span class="fw-semibold">${value}</span></div>`;

    form.productId.addEventListener('change', () => {
      const p = products.find(x => String(x.id) === form.productId.value);
      view.querySelector('#unit').value = p ? p.unitOfMeasure : '–';
      if (p && !form.unitPrice.value) form.unitPrice.value = Number(p.sellingPrice).toFixed(2);
      view.querySelector('#pInfo').innerHTML = p
        ? `<div class="text-center" style="font-size:3.5rem">${FP.emoji(p.name)}</div>
           ${info('Product name', FP.esc(p.name))}${info('Category', FP.esc(p.category))}
           ${info('Unit', FP.esc(p.unitOfMeasure))}${info('Selling price', FP.money(p.sellingPrice))}
           ${info('Current stock', FP.qty(p.quantityAvailable, p.unitOfMeasure))}`
        : '<div class="text-muted small">Select a product</div>';
    });
    form.farmerId.addEventListener('change', () => {
      const f = activeFarmers.find(x => String(x.id) === form.farmerId.value);
      view.querySelector('#fInfo').innerHTML = f
        ? `${info('Name', FP.esc(f.name))}${info('Contact', FP.esc(f.contactNumber || '–'))}
           ${info('Location', FP.esc(f.address || '–'))}${info('Total consignments', f.totalConsignments)}
           ${info('Total quantity supplied', FP.num(f.totalQuantitySupplied))}`
        : '<div class="text-muted small">Select a farmer</div>';
    });

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      FP.clearError(form);
      const d = FP.formData(form);
      const problems = [];
      if (!d.farmerId) problems.push('Select a farmer');
      if (!d.productId) problems.push('Select a product');
      if (!(Number(d.quantityReceived) > 0)) problems.push('Quantity must be greater than zero');
      if (!(Number(d.unitPrice) > 0)) problems.push('Unit price must be greater than zero');
      if (!d.dateReceived) problems.push('Received date is required');
      else if (d.dateReceived > FP.today()) problems.push('Received date cannot be in the future');
      if (problems.length) return FP.showError(form, new Error(problems.join('. ') + '.'));

      const body = {
        farmerId: Number(d.farmerId), productId: Number(d.productId), quantityReceived: Number(d.quantityReceived),
        unitPrice: Number(d.unitPrice), dateReceived: d.dateReceived, grade: d.grade, notes: d.notes
      };
      try {
        const saved = await FP.busy(view.querySelector('#saveBtn'), () => FP.api('POST', '/api/consignments', body));
        const m = FP.modal({
          title: '<i class="bi bi-check-circle-fill text-success me-2"></i>Consignment saved',
          body: `<p>The consignment was recorded with reference</p>
                 <div class="fs-3 fw-bold text-fp mb-2">${FP.esc(saved.referenceNumber)}</div>
                 <p class="mb-0 small text-muted">${FP.qty(saved.quantityReceived, saved.unitOfMeasure)} of ${FP.esc(saved.productName)}
                 from ${FP.esc(saved.farmerName)}, received ${FP.date(saved.dateReceived)}.</p>`,
          footer: `<button class="btn btn-light" data-another>Record another</button>
                   <button class="btn btn-fp" data-view>View consignment</button>`
        });
        m.el.querySelector('[data-another]').addEventListener('click', () => { m.close(); renderNew(view); });
        m.el.querySelector('[data-view]').addEventListener('click', () => { m.close(); location.hash = `#/consignments/${saved.id}`; });
      } catch (err) { FP.showError(form, err); }
    });
  }

  // ------------------------------------------------------------------ detail / traceability
  async function renderDetail(view, url) {
    let d;
    try {
      d = await FP.api('GET', url);
    } catch (err) {
      if (err.status === 404) {
        view.innerHTML = `<div class="alert alert-warning"><i class="bi bi-search me-2"></i>${FP.esc(err.message)}.
          <a href="#/consignments">Back to consignments</a></div>`;
        return;
      }
      throw err;
    }
    const c = d.consignment;
    const u = c.unitOfMeasure;
    view.innerHTML = `
      <div class="d-flex flex-wrap justify-content-between align-items-end mb-3 gap-2">
        <div><h1 class="page-title">${FP.esc(c.referenceNumber)} ${FP.statusBadge(c.status)}</h1>
          <div class="page-sub"><a href="#/consignments" class="text-decoration-none">Consignments</a> › Traceability</div></div>
        <div class="d-flex gap-2">
          ${c.status === 'ACTIVE' ? `<button class="btn btn-outline-fp" id="adjustBtn"><i class="bi bi-dash-circle me-1"></i>Record adjustment</button>` : ''}
          <button class="btn btn-light" onclick="window.print()"><i class="bi bi-printer"></i></button>
        </div>
      </div>
      <div class="print-area">
      <div class="row g-3 mb-3">
        ${[['Received', FP.qty(c.quantityReceived, u), 'bg-fp-blue', 'box-arrow-in-down'],
           ['Sold', FP.qty(d.quantitySold, u), 'bg-fp-green', 'cart-check'],
           ['Adjusted', FP.qty(d.quantityAdjusted, u), 'bg-fp-amber', 'dash-circle'],
           ['Remaining', FP.qty(c.quantityRemaining, u), 'bg-fp-purple', 'boxes']].map(([l, v, bg, ic]) => `
          <div class="col-6 col-xl-3"><div class="card stat-card"><div class="stat-icon ${bg}"><i class="bi bi-${ic}"></i></div>
            <div><div class="stat-label">${l}</div><div class="stat-value">${v}</div></div></div></div>`).join('')}
      </div>
      <div class="row g-3 mb-3">
        <div class="col-md-6"><div class="card h-100"><div class="card-header">Consignment</div><div class="card-body small">
          <div class="d-flex align-items-center gap-3 mb-2"><span style="font-size:2.5rem">${FP.emoji(c.productName)}</span>
            <div><div class="fw-bold fs-6">${FP.esc(c.productName)}</div><div class="text-muted">${FP.esc(c.grade || '')}</div></div></div>
          <div>Received: <strong>${FP.date(c.dateReceived)}</strong></div>
          <div>Unit price: <strong>${FP.money(c.unitPrice)}</strong> per ${FP.esc(u)}</div>
          <div>Sales value to date: <strong>${FP.money(d.salesValue)}</strong></div>
          <div>Recorded by: ${FP.esc(c.recordedBy || '–')}</div>
          ${c.notes ? `<div class="mt-2 text-muted">${FP.esc(c.notes)}</div>` : ''}
        </div></div></div>
        <div class="col-md-6"><div class="card h-100"><div class="card-header">Farmer</div><div class="card-body small">
          <div class="fw-bold fs-6">${FP.esc(c.farmerName)}</div>
          <div class="text-muted mb-2">${FP.esc(c.farmerCode || '')}</div>
          <div>Contact: ${FP.esc(d.farmerContact || '–')}</div>
          <div>Address: ${FP.esc(d.farmerAddress || '–')}</div>
          <a href="#/consignments/farmer/${c.farmerId}" class="no-print">All consignments from this farmer</a>
        </div></div></div>
      </div>
      <div class="card mb-3"><div class="card-header">Sales history (FIFO allocations)</div>
        <div class="table-responsive"><table class="table mb-0">
          <thead><tr><th>Date</th><th>Receipt</th><th>Agent</th><th class="text-end">Quantity</th><th class="text-end">Price</th><th class="text-end">Value</th></tr></thead>
          <tbody>${d.salesHistory.length ? d.salesHistory.map(s => `<tr>
            <td>${FP.dateTime(s.saleDate)}</td><td>${FP.esc(s.receiptNumber)}</td><td>${FP.esc(s.agentName)}</td>
            <td class="text-end">${FP.qty(s.quantity, u)}</td><td class="text-end">${FP.money(s.unitPrice)}</td>
            <td class="text-end">${FP.money(s.value)}</td></tr>`).join('')
            : '<tr><td colspan="6" class="empty">No sales from this consignment yet</td></tr>'}</tbody>
        </table></div></div>
      <div class="card"><div class="card-header">Stock movement history</div>
        <div class="table-responsive"><table class="table mb-0">
          <thead><tr><th>Date</th><th>Type</th><th>Reference</th><th>Reason</th><th>User</th><th class="text-end">Quantity</th><th class="text-end">Balance</th></tr></thead>
          <tbody>${d.movements.map(m => `<tr>
            <td>${FP.dateTime(m.movementDate)}</td>
            <td>${FP.statusBadge(m.movementType)} ${m.adjustmentType ? `<span class="small text-muted">${FP.esc(m.adjustmentType)}</span>` : ''}</td>
            <td class="small">${FP.esc(m.reference || '')}</td><td class="small">${FP.esc(m.reason || '')}</td>
            <td class="small">${FP.esc(m.userName || '')}</td>
            <td class="text-end ${m.quantity < 0 ? 'text-danger' : 'text-success'}">${m.quantity > 0 ? '+' : ''}${FP.num(m.quantity)}</td>
            <td class="text-end fw-semibold">${FP.num(m.balanceAfter)}</td></tr>`).join('')}</tbody>
        </table></div></div>
      </div>`;

    const adjustBtn = view.querySelector('#adjustBtn');
    if (adjustBtn) {
      adjustBtn.addEventListener('click', () => Pages.openAdjustment(c, () => renderDetail(view, `/api/consignments/${c.id}`)));
    }
  }
};
