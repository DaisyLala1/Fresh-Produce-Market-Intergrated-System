/* Inventory – UC-03 View Inventory and UC-08 Record Stock Adjustment */
'use strict';

Pages.inventory = async (view) => {
  const [stock, consignments] = await Promise.all([
    FP.api('GET', '/api/inventory'), FP.api('GET', '/api/consignments?status=ACTIVE')]);
  const totalValue = stock.reduce((s, x) => s + Number(x.stockValue), 0);
  const inStock = stock.filter(s => Number(s.quantityAvailable) > 0).length;
  const lowStock = stock.filter(s => Number(s.quantityAvailable) > 0 && s.activeConsignments <= 1).length;

  view.innerHTML = `
    <div class="d-flex flex-wrap justify-content-between align-items-end mb-3 gap-2">
      <div><h1 class="page-title">Inventory</h1><div class="page-sub">Current stock, consignment balances and stock movements</div></div>
      <button class="btn btn-fp" id="adjBtn"><i class="bi bi-dash-circle me-1"></i>Record Stock Adjustment</button>
    </div>
    <div class="row g-3 mb-3">
      <div class="col-sm-4"><div class="card stat-card"><div class="stat-icon bg-fp-green"><i class="bi bi-cash-stack"></i></div>
        <div><div class="stat-label">Stock value (selling price)</div><div class="stat-value">${FP.money(totalValue)}</div></div></div></div>
      <div class="col-sm-4"><div class="card stat-card"><div class="stat-icon bg-fp-blue"><i class="bi bi-boxes"></i></div>
        <div><div class="stat-label">Products in stock</div><div class="stat-value">${inStock} / ${stock.length}</div></div></div></div>
      <div class="col-sm-4"><div class="card stat-card"><div class="stat-icon bg-fp-amber"><i class="bi bi-box-seam"></i></div>
        <div><div class="stat-label">Active consignments</div><div class="stat-value">${consignments.length}</div>
        <div class="small text-muted">${lowStock} product(s) on their last consignment</div></div></div></div>
    </div>

    <ul class="nav nav-tabs mb-0" role="tablist">
      <li class="nav-item"><button class="nav-link active" data-bs-toggle="tab" data-bs-target="#tStock" type="button">Stock by product</button></li>
      <li class="nav-item"><button class="nav-link" data-bs-toggle="tab" data-bs-target="#tCons" type="button">Active consignments (FIFO order)</button></li>
      <li class="nav-item"><button class="nav-link" data-bs-toggle="tab" data-bs-target="#tAdj" type="button" id="adjTab">Adjustments</button></li>
      <li class="nav-item"><button class="nav-link" data-bs-toggle="tab" data-bs-target="#tMov" type="button" id="movTab">Recent movements</button></li>
    </ul>
    <div class="tab-content card border-top-0" style="border-top-left-radius:0">
      <div class="tab-pane fade show active" id="tStock"><div class="table-responsive"><table class="table table-hover mb-0">
        <thead><tr><th>Product</th><th>Category</th><th class="text-end">Available</th><th class="text-end">Consignments</th>
          <th>Oldest stock received</th><th class="text-end">Selling price</th><th class="text-end">Stock value</th></tr></thead>
        <tbody>${stock.map(s => `<tr class="clickable" data-product="${s.productId}">
          <td>${FP.emoji(s.productName)} <strong>${FP.esc(s.productName)}</strong></td><td>${FP.esc(s.category)}</td>
          <td class="text-end ${Number(s.quantityAvailable) <= 0 ? 'text-danger fw-semibold' : ''}">${FP.qty(s.quantityAvailable, s.unitOfMeasure)}</td>
          <td class="text-end">${s.activeConsignments}</td>
          <td>${s.oldestReceived ? FP.date(s.oldestReceived) : '–'}</td>
          <td class="text-end">${FP.money(s.sellingPrice)}</td><td class="text-end">${FP.money(s.stockValue)}</td></tr>`).join('')}</tbody>
      </table></div></div>

      <div class="tab-pane fade" id="tCons"><div class="table-responsive"><table class="table table-hover mb-0">
        <thead><tr><th>Reference</th><th>Product</th><th>Farmer</th><th>Received</th>
          <th class="text-end">Received qty</th><th class="text-end">Remaining</th><th></th></tr></thead>
        <tbody>${consignments.length ? [...consignments].sort((a, b) => a.productName.localeCompare(b.productName)
            || a.dateReceived.localeCompare(b.dateReceived) || a.id - b.id).map(c => `<tr>
          <td><a href="#/consignments/${c.id}" class="fw-semibold text-fp text-decoration-none">${FP.esc(c.referenceNumber)}</a></td>
          <td>${FP.emoji(c.productName)} ${FP.esc(c.productName)}</td><td>${FP.esc(c.farmerName)}</td><td>${FP.date(c.dateReceived)}</td>
          <td class="text-end">${FP.qty(c.quantityReceived, c.unitOfMeasure)}</td>
          <td class="text-end fw-semibold">${FP.qty(c.quantityRemaining, c.unitOfMeasure)}</td>
          <td class="text-end"><button class="btn btn-sm btn-outline-fp" data-adjust="${c.id}">Adjust</button></td></tr>`).join('')
          : '<tr><td colspan="7" class="empty">No active consignments</td></tr>'}</tbody>
      </table></div></div>

      <div class="tab-pane fade" id="tAdj"><div id="adjRows">${FP.loading()}</div></div>
      <div class="tab-pane fade" id="tMov"><div id="movRows">${FP.loading()}</div></div>
    </div>`;

  const reload = () => Pages.inventory(view);
  view.querySelector('#adjBtn').addEventListener('click', () => Pages.openAdjustment(null, reload));
  view.querySelectorAll('[data-adjust]').forEach(b => b.addEventListener('click', () =>
    Pages.openAdjustment(consignments.find(c => String(c.id) === b.dataset.adjust), reload)));
  view.querySelectorAll('[data-product]').forEach(tr => tr.addEventListener('click', () => {
    location.hash = `#/consignments/product/${tr.dataset.product}`;
  }));

  const movementTable = (list, showType) => list.length ? `<div class="table-responsive"><table class="table mb-0">
    <thead><tr><th>Date</th>${showType ? '<th>Type</th>' : '<th>Reason type</th>'}<th>Consignment</th><th>Product</th>
      <th>Reason / reference</th><th>User</th><th class="text-end">Quantity</th><th class="text-end">Balance</th></tr></thead>
    <tbody>${list.map(m => `<tr>
      <td class="text-nowrap">${FP.dateTime(m.movementDate)}</td>
      <td>${showType ? FP.statusBadge(m.movementType) : `<span class="badge text-bg-warning">${FP.esc(m.adjustmentType)}</span>`}</td>
      <td class="small">${FP.esc(m.consignmentReference)}</td><td>${FP.esc(m.productName)}</td>
      <td class="small">${FP.esc(m.movementType === 'ADJUSTMENT' ? m.reason : m.reference)}</td>
      <td class="small">${FP.esc(m.userName || '')}</td>
      <td class="text-end ${m.quantity < 0 ? 'text-danger' : 'text-success'}">${m.quantity > 0 ? '+' : ''}${FP.num(m.quantity)} ${FP.esc(m.unitOfMeasure)}</td>
      <td class="text-end">${FP.num(m.balanceAfter)}</td></tr>`).join('')}</tbody></table></div>`
    : '<div class="empty">Nothing recorded yet</div>';

  view.querySelector('#adjTab').addEventListener('shown.bs.tab', async () => {
    const box = view.querySelector('#adjRows');
    try { box.innerHTML = movementTable(await FP.api('GET', '/api/inventory/adjustments'), false); }
    catch (err) { box.innerHTML = FP.errorBlock(err); }
  }, { once: true });
  view.querySelector('#movTab').addEventListener('shown.bs.tab', async () => {
    const box = view.querySelector('#movRows');
    try { box.innerHTML = movementTable(await FP.api('GET', '/api/inventory/movements'), true); }
    catch (err) { box.innerHTML = FP.errorBlock(err); }
  }, { once: true });
};

/**
 * Stock adjustment dialog (FR-08). Pass a consignment to adjust it directly, or null to choose one.
 */
Pages.openAdjustment = async (consignment, onDone) => {
  let options = [];
  if (!consignment) {
    options = await FP.api('GET', '/api/consignments?status=ACTIVE');
    if (!options.length) return FP.toast('There are no consignments with stock to adjust', 'warning');
  }
  const m = FP.modal({
    title: 'Record Stock Adjustment',
    body: `<form id="adjForm" novalidate>
      <p class="small text-muted">Use this for damaged, unsold or unsuitable produce. The balance is reduced and the
        adjustment is logged – no sale is created.</p>
      <div class="mb-3"><label class="form-label">Consignment *</label>
        ${consignment
          ? `<input class="form-control" disabled value="${FP.esc(consignment.referenceNumber)} – ${FP.esc(consignment.productName)} (${FP.num(consignment.quantityRemaining)} ${FP.esc(consignment.unitOfMeasure)} remaining)">`
          : `<select name="consignmentId" class="form-select" required><option value="">Select consignment…</option>
              ${options.map(c => `<option value="${c.id}">${FP.esc(c.referenceNumber)} – ${FP.esc(c.productName)} (${FP.num(c.quantityRemaining)} ${FP.esc(c.unitOfMeasure)})</option>`).join('')}</select>`}
      </div>
      <div class="row g-3">
        <div class="col-6"><label class="form-label">Quantity to remove *</label>
          <input name="quantity" type="number" min="0.01" step="0.01" class="form-control" required></div>
        <div class="col-6"><label class="form-label">Type *</label>
          <select name="adjustmentType" class="form-select"><option value="DAMAGED">Damaged</option>
            <option value="UNSOLD">Unsold / expired</option><option value="UNSUITABLE">Unsuitable for sale</option>
            <option value="OTHER">Other</option></select></div>
      </div>
      <div class="mt-3"><label class="form-label">Reason *</label>
        <input name="reason" class="form-control" maxlength="255" required placeholder="e.g. Bruised during offloading"></div>
    </form>`,
    footer: `<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button><button class="btn btn-fp" data-save>Save adjustment</button>`
  });
  const form = m.el.querySelector('#adjForm');
  m.el.querySelector('[data-save]').addEventListener('click', async (e) => {
    const btn = e.currentTarget;
    const d = FP.formData(form);
    FP.clearError(form);
    const consignmentId = consignment ? consignment.id : Number(d.consignmentId);
    const chosen = consignment || options.find(c => c.id === consignmentId);
    if (!consignmentId) return FP.showError(form, new Error('Select a consignment'));
    if (!(Number(d.quantity) > 0)) return FP.showError(form, new Error('Quantity must be greater than zero'));
    if (chosen && Number(d.quantity) > Number(chosen.quantityRemaining)) {
      return FP.showError(form, new Error(`Quantity cannot exceed the remaining balance of ${FP.num(chosen.quantityRemaining)}`));
    }
    if (!d.reason.trim()) return FP.showError(form, new Error('A reason is required'));
    try {
      const saved = await FP.busy(btn, () => FP.api('POST', '/api/inventory/adjustments', {
        consignmentId, quantity: Number(d.quantity), adjustmentType: d.adjustmentType, reason: d.reason.trim()
      }));
      m.close();
      FP.toast(`Adjustment recorded. New balance: ${FP.num(saved.balanceAfter)} ${saved.unitOfMeasure}`);
      if (onDone) onDone();
    } catch (err) { FP.showError(form, err); }
  });
};
