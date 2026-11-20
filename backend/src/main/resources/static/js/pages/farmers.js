/* Farmers (UC-01 Register Farmer) */
'use strict';

Pages.farmers = async (view) => {
  view.innerHTML = `
    <div class="d-flex flex-wrap justify-content-between align-items-end mb-3 gap-2">
      <div><h1 class="page-title">Farmers</h1><div class="page-sub">Suppliers who deliver produce on consignment</div></div>
      <button class="btn btn-fp" id="addFarmer"><i class="bi bi-person-plus me-1"></i>Register Farmer</button>
    </div>
    <div class="card">
      <div class="card-body pb-0"><input class="form-control mb-3" id="farmerSearch" placeholder="Search by name, code or contact number…" style="max-width:380px"></div>
      <div class="table-responsive"><table class="table table-hover mb-0">
        <thead><tr><th>Code</th><th>Name</th><th>Contact</th><th>Address</th><th class="text-end">Consignments</th>
          <th class="text-end">Total supplied</th><th>Status</th><th></th></tr></thead>
        <tbody id="farmerRows"><tr><td colspan="8">${FP.loading()}</td></tr></tbody>
      </table></div>
    </div>`;

  const rows = view.querySelector('#farmerRows');
  let timer;

  async function load(q = '') {
    const farmers = await FP.api('GET', `/api/farmers${q ? `?q=${encodeURIComponent(q)}` : ''}`);
    rows.innerHTML = farmers.length ? farmers.map(f => `
      <tr>
        <td class="small text-muted">${FP.esc(f.farmerCode)}</td>
        <td class="fw-semibold">${FP.esc(f.name)}</td>
        <td>${FP.esc(f.contactNumber || '')}</td>
        <td class="small">${FP.esc(f.address || '')}</td>
        <td class="text-end">${f.totalConsignments}</td>
        <td class="text-end">${FP.num(f.totalQuantitySupplied)}</td>
        <td>${FP.statusBadge(f.status)}</td>
        <td class="text-end text-nowrap">
          <a class="btn btn-sm btn-light" href="#/consignments/farmer/${f.id}" title="Consignments"><i class="bi bi-box-seam"></i></a>
          <button class="btn btn-sm btn-light" data-edit="${f.id}" title="Edit"><i class="bi bi-pencil"></i></button>
          ${FP.isAdmin() ? `<button class="btn btn-sm btn-light" data-toggle="${f.id}" data-status="${f.status}"
              title="${f.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}"><i class="bi bi-${f.status === 'ACTIVE' ? 'pause' : 'play'}-circle"></i></button>` : ''}
        </td>
      </tr>`).join('') : '<tr><td colspan="8" class="empty">No farmers found</td></tr>';

    rows.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () =>
      openForm(farmers.find(f => String(f.id) === b.dataset.edit))));
    rows.querySelectorAll('[data-toggle]').forEach(b => b.addEventListener('click', async () => {
      const status = b.dataset.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      try {
        await FP.api('PATCH', `/api/farmers/${b.dataset.toggle}/status`, { status });
        FP.toast(`Farmer ${status === 'ACTIVE' ? 'activated' : 'deactivated'}`);
        load(view.querySelector('#farmerSearch').value.trim());
      } catch (err) { FP.toast(err.message, 'danger'); }
    }));
  }

  function openForm(farmer) {
    const m = FP.modal({
      title: farmer ? 'Edit Farmer' : 'Register Farmer',
      body: `<form id="farmerForm" novalidate>
        <div class="mb-3"><label class="form-label">Full name *</label>
          <input name="name" class="form-control" required maxlength="100" value="${FP.esc(farmer?.name || '')}"></div>
        <div class="mb-3"><label class="form-label">Contact number</label>
          <input name="contactNumber" class="form-control" maxlength="20" placeholder="e.g. 0821234567" value="${FP.esc(farmer?.contactNumber || '')}"></div>
        <div class="mb-1"><label class="form-label">Address / farm location</label>
          <input name="address" class="form-control" maxlength="255" value="${FP.esc(farmer?.address || '')}"></div>
      </form>`,
      footer: `<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button><button class="btn btn-fp" data-save>Save</button>`
    });
    const form = m.el.querySelector('#farmerForm');
    m.el.querySelector('[data-save]').addEventListener('click', async (e) => {
      const data = FP.formData(form);
      FP.clearError(form);
      if (!data.name.trim()) return FP.showError(form, new Error('Farmer name is required'));
      if (data.contactNumber && !/^[0-9+ ]{7,20}$/.test(data.contactNumber)) {
        return FP.showError(form, new Error('Contact number must contain 7 to 20 digits'));
      }
      try {
        await FP.busy(e.currentTarget, () => farmer
          ? FP.api('PUT', `/api/farmers/${farmer.id}`, data)
          : FP.api('POST', '/api/farmers', data));
        m.close();
        FP.toast(farmer ? 'Farmer updated' : 'Farmer registered');
        load();
      } catch (err) { FP.showError(form, err); }
    });
  }

  view.querySelector('#addFarmer').addEventListener('click', () => openForm(null));
  view.querySelector('#farmerSearch').addEventListener('input', (e) => {
    clearTimeout(timer);
    timer = setTimeout(() => load(e.target.value.trim()), 250);
  });
  await load();
};
