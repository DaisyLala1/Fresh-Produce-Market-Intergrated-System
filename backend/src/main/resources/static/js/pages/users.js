/* Users – Manage Users (Administrator only) */
'use strict';

Pages.users = async (view) => {
  if (!FP.isAdmin()) {
    view.innerHTML = `<div class="alert alert-danger"><i class="bi bi-shield-lock me-2"></i>Access denied – user management is only available to Administrators.</div>`;
    return;
  }
  const users = await FP.api('GET', '/api/users');
  const me = FP.state.user;

  view.innerHTML = `
    <div class="d-flex flex-wrap justify-content-between align-items-end mb-3 gap-2">
      <div><h1 class="page-title">Users</h1><div class="page-sub">Agents and Administrators who can log in to the system</div></div>
      <button class="btn btn-fp" id="addUser"><i class="bi bi-person-plus me-1"></i>Add User</button>
    </div>
    <div class="card"><div class="table-responsive"><table class="table table-hover mb-0">
      <thead><tr><th>Username</th><th>Full name</th><th>Role</th><th>Status</th><th></th></tr></thead>
      <tbody>${users.map(u => `<tr>
        <td class="fw-semibold">${FP.esc(u.username)}${u.id === me.id ? ' <span class="badge text-bg-light">you</span>' : ''}</td>
        <td>${FP.esc(u.fullName)}</td>
        <td>${u.role === 'ADMINISTRATOR' ? '<span class="badge text-bg-dark">Administrator</span>' : '<span class="badge text-bg-info">Agent</span>'}</td>
        <td>${FP.statusBadge(u.status)}</td>
        <td class="text-end"><button class="btn btn-sm btn-light" data-edit="${u.id}"><i class="bi bi-pencil"></i></button></td>
      </tr>`).join('')}</tbody>
    </table></div></div>`;

  const reload = () => Pages.users(view);
  view.querySelector('#addUser').addEventListener('click', () => openForm(null));
  view.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () =>
    openForm(users.find(u => String(u.id) === b.dataset.edit))));

  function openForm(u) {
    const self = u && u.id === me.id;
    const m = FP.modal({
      title: u ? `Edit ${FP.esc(u.username)}` : 'Add User',
      body: `<form id="uForm" novalidate><div class="row g-3">
        ${u ? '' : `<div class="col-12"><label class="form-label">Username *</label>
          <input name="username" class="form-control" minlength="3" maxlength="50" required autocomplete="off"></div>`}
        <div class="col-12"><label class="form-label">Full name *</label>
          <input name="fullName" class="form-control" maxlength="100" required value="${FP.esc(u?.fullName || '')}"></div>
        <div class="col-6"><label class="form-label">Role *</label>
          <select name="role" class="form-select" ${self ? 'disabled' : ''}>
            <option value="AGENT" ${u?.role === 'AGENT' ? 'selected' : ''}>Agent</option>
            <option value="ADMINISTRATOR" ${u?.role === 'ADMINISTRATOR' ? 'selected' : ''}>Administrator</option></select></div>
        ${u ? `<div class="col-6"><label class="form-label">Status</label>
          <select name="status" class="form-select" ${self ? 'disabled' : ''}>
            <option ${u.status === 'ACTIVE' ? 'selected' : ''}>ACTIVE</option><option ${u.status === 'INACTIVE' ? 'selected' : ''}>INACTIVE</option></select></div>` : ''}
        <div class="col-12"><label class="form-label">${u ? 'Reset password (leave blank to keep)' : 'Password *'}</label>
          <input name="password" type="password" class="form-control" minlength="6" autocomplete="new-password">
          <div class="form-text">At least 6 characters. Passwords are stored as secure hashes.</div></div>
      </div></form>`,
      footer: `<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button><button class="btn btn-fp" data-save>Save</button>`
    });
    const form = m.el.querySelector('#uForm');
    m.el.querySelector('[data-save]').addEventListener('click', async (e) => {
      const btn = e.currentTarget;
      const d = FP.formData(form);
      FP.clearError(form);
      if (!u && (!d.username || d.username.trim().length < 3)) return FP.showError(form, new Error('Username must be at least 3 characters'));
      if (!d.fullName.trim()) return FP.showError(form, new Error('Full name is required'));
      if ((!u || d.password) && (d.password || '').length < 6) return FP.showError(form, new Error('Password must be at least 6 characters'));
      try {
        await FP.busy(btn, () => u
          ? FP.api('PUT', `/api/users/${u.id}`, {
              fullName: d.fullName.trim(), role: self ? u.role : d.role, status: self ? u.status : d.status,
              newPassword: d.password || null })
          : FP.api('POST', '/api/users', { username: d.username.trim(), password: d.password, fullName: d.fullName.trim(), role: d.role }));
        m.close();
        FP.toast(u ? 'User updated' : 'User created');
        reload();
      } catch (err) { FP.showError(form, err); }
    });
  }
};
