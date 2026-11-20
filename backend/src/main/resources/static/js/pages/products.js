/* Products – UC-07 Manage Products (changes are Administrator-only) */
'use strict';

Pages.products = async (view) => {
  const admin = FP.isAdmin();
  const products = await FP.api('GET', '/api/products');

  view.innerHTML = `
    <div class="d-flex flex-wrap justify-content-between align-items-end mb-3 gap-2">
      <div><h1 class="page-title">Products</h1>
        <div class="page-sub">${admin ? 'Manage product names, categories, units and selling prices' : 'Product price list (read only – changes are made by an Administrator)'}</div></div>
      ${admin ? '<button class="btn btn-fp" id="addProduct"><i class="bi bi-plus-lg me-1"></i>Add Product</button>' : ''}
    </div>
    <div class="card"><div class="table-responsive"><table class="table table-hover mb-0">
      <thead><tr><th>Product</th><th>Category</th><th>Unit</th><th class="text-end">Selling price</th>
        <th class="text-end">In stock</th><th>Status</th>${admin ? '<th></th>' : ''}</tr></thead>
      <tbody>${products.length ? products.map(p => `<tr>
        <td>${FP.emoji(p.name)} <strong>${FP.esc(p.name)}</strong></td><td>${FP.esc(p.category)}</td><td>${FP.esc(p.unitOfMeasure)}</td>
        <td class="text-end">${FP.money(p.sellingPrice)}</td><td class="text-end">${FP.qty(p.quantityAvailable, p.unitOfMeasure)}</td>
        <td>${FP.statusBadge(p.status)}</td>
        ${admin ? `<td class="text-end text-nowrap">
          <button class="btn btn-sm btn-light" data-edit="${p.id}" title="Edit"><i class="bi bi-pencil"></i></button>
          <button class="btn btn-sm btn-light" data-toggle="${p.id}" title="${p.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}">
            <i class="bi bi-${p.status === 'ACTIVE' ? 'pause' : 'play'}-circle"></i></button></td>` : ''}
      </tr>`).join('') : `<tr><td colspan="7" class="empty">No products yet</td></tr>`}</tbody>
    </table></div></div>`;

  if (!admin) return;

  const reload = () => Pages.products(view);
  view.querySelector('#addProduct').addEventListener('click', () => openForm(null));
  view.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () =>
    openForm(products.find(p => String(p.id) === b.dataset.edit))));
  view.querySelectorAll('[data-toggle]').forEach(b => b.addEventListener('click', async () => {
    const p = products.find(x => String(x.id) === b.dataset.toggle);
    const status = p.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    if (status === 'INACTIVE' && !await FP.confirm('Deactivate product',
      `Deactivate <strong>${FP.esc(p.name)}</strong>? It will no longer be available for new consignments or sales.`, 'Deactivate', 'btn-danger')) return;
    try {
      await FP.api('PATCH', `/api/products/${p.id}/status`, { status });
      FP.toast(`${p.name} ${status === 'ACTIVE' ? 'activated' : 'deactivated'}`);
      reload();
    } catch (err) { FP.toast(err.message, 'danger'); }
  }));

  function openForm(p) {
    const m = FP.modal({
      title: p ? 'Edit Product' : 'Add Product',
      body: `<form id="pForm" novalidate><div class="row g-3">
        <div class="col-12"><label class="form-label">Product name *</label>
          <input name="name" class="form-control" maxlength="100" required value="${FP.esc(p?.name || '')}"></div>
        <div class="col-6"><label class="form-label">Category *</label>
          <input name="category" class="form-control" list="cats" maxlength="50" required value="${FP.esc(p?.category || '')}">
          <datalist id="cats"><option>Vegetables</option><option>Fruit</option><option>Herbs</option></datalist></div>
        <div class="col-6"><label class="form-label">Unit of measurement *</label>
          <input name="unitOfMeasure" class="form-control" list="units" maxlength="20" required value="${FP.esc(p?.unitOfMeasure || 'kg')}">
          <datalist id="units"><option>kg</option><option>bunch</option><option>box</option><option>bag</option><option>each</option></datalist></div>
        <div class="col-6"><label class="form-label">Selling price (R) *</label>
          <input name="sellingPrice" type="number" min="0.01" step="0.01" class="form-control" required value="${p ? Number(p.sellingPrice).toFixed(2) : ''}"></div>
      </div></form>`,
      footer: `<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button><button class="btn btn-fp" data-save>Save</button>`
    });
    const form = m.el.querySelector('#pForm');
    m.el.querySelector('[data-save]').addEventListener('click', async (e) => {
      const btn = e.currentTarget;
      const d = FP.formData(form);
      FP.clearError(form);
      if (!d.name.trim() || !d.category.trim() || !d.unitOfMeasure.trim()) return FP.showError(form, new Error('All fields are required'));
      if (!(Number(d.sellingPrice) > 0)) return FP.showError(form, new Error('Selling price must be greater than zero'));
      const body = { name: d.name.trim(), category: d.category.trim(), unitOfMeasure: d.unitOfMeasure.trim(), sellingPrice: Number(d.sellingPrice) };
      try {
        await FP.busy(btn, () => p ? FP.api('PUT', `/api/products/${p.id}`, body) : FP.api('POST', '/api/products', body));
        m.close();
        FP.toast(p ? 'Product updated' : 'Product added');
        reload();
      } catch (err) { FP.showError(form, err); }
    });
  }
};
