/* Point of Sale – UC-02 Process Sale (POS) with FIFO allocation */
'use strict';

Pages.pos = async (view) => {
  let products = await FP.api('GET', '/api/products?activeOnly=true');
  const cart = new Map(); // productId -> quantity
  const now = new Date();

  view.innerHTML = `
    <div class="d-flex flex-wrap justify-content-between align-items-end mb-3 gap-2">
      <div><h1 class="page-title">Point of Sale – New Sale</h1><div class="page-sub">POS Sales › New Sale</div></div>
      <span class="badge text-bg-light border p-2">Date: ${FP.date(now)} &nbsp; Time: ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}</span>
    </div>
    <div class="row g-3">
      <div class="col-lg-6"><div class="card h-100">
        <div class="card-header">Select Product</div>
        <div class="card-body">
          <input class="form-control mb-3" id="pSearch" placeholder="Search product…">
          <div class="product-grid" id="grid"></div>
        </div></div></div>
      <div class="col-lg-6"><div class="card h-100">
        <div class="card-header">Sale Items</div>
        <div class="card-body d-flex flex-column">
          <div id="saleError"></div>
          <div class="table-responsive flex-grow-1"><table class="table align-middle mb-3">
            <thead><tr><th>Product</th><th>Quantity</th><th class="text-end">Price (R)</th><th class="text-end">Total (R)</th><th></th></tr></thead>
            <tbody id="cartRows"></tbody></table></div>
          <div class="border rounded p-3 mb-3">
            <div class="fw-semibold mb-2">Sale Summary</div>
            <div class="d-flex justify-content-between mb-2"><span>Subtotal (R)</span><span id="subTotal">0.00</span></div>
            <div class="d-flex justify-content-between align-items-center mb-2"><span>Discount (R)</span>
              <input id="discount" type="number" min="0" step="0.01" value="0" class="form-control form-control-sm text-end" style="width:120px"></div>
            <div class="d-flex justify-content-between total-row rounded p-2"><span>Total (R)</span><span id="total">0.00</span></div>
          </div>
          <div class="d-flex justify-content-between gap-2">
            <button class="btn btn-danger" id="clearBtn"><i class="bi bi-trash me-1"></i>Clear Sale</button>
            <button class="btn btn-fp px-4" id="processBtn"><i class="bi bi-cart-check me-1"></i>Process Sale</button>
          </div>
        </div></div></div>
    </div>`;

  const grid = view.querySelector('#grid');
  const rows = view.querySelector('#cartRows');
  const discountInput = view.querySelector('#discount');
  const errorBox = view.querySelector('#saleError');
  const find = (id) => products.find(p => String(p.id) === String(id));

  function setError(message) {
    errorBox.innerHTML = message ? `<div class="alert alert-danger py-2">${FP.esc(message)}</div>` : '';
  }

  function renderGrid() {
    const q = view.querySelector('#pSearch').value.trim().toLowerCase();
    const list = products.filter(p => !q || p.name.toLowerCase().includes(q) || p.category.toLowerCase().includes(q));
    grid.innerHTML = list.length ? list.map(p => {
      const out = Number(p.quantityAvailable) <= 0;
      return `<div class="product-tile ${out ? 'out' : ''} ${cart.has(p.id) ? 'border-success' : ''}" data-id="${p.id}" title="${out ? 'Out of stock' : 'Add to sale'}">
        <div class="emoji">${FP.emoji(p.name)}</div>
        <div class="name">${FP.esc(p.name)}</div>
        <div class="price">${FP.money(p.sellingPrice)}/${FP.esc(p.unitOfMeasure)}</div>
        <div class="stock ${out ? 'text-danger' : ''}">${out ? 'Out of stock' : `${FP.num(p.quantityAvailable)} ${FP.esc(p.unitOfMeasure)} in stock`}</div>
      </div>`;
    }).join('') : '<div class="empty">No products match your search</div>';
    grid.querySelectorAll('.product-tile:not(.out)').forEach(t => t.addEventListener('click', () => {
      const id = Number(t.dataset.id);
      cart.set(id, (cart.get(id) || 0) + 1);
      setError('');
      renderCart();
      renderGrid();
    }));
  }

  function totals() {
    let sub = 0;
    cart.forEach((q, id) => { sub += q * Number(find(id).sellingPrice); });
    const discount = Math.max(0, Number(discountInput.value) || 0);
    return { sub, discount, total: Math.max(0, sub - discount) };
  }

  function renderCart() {
    rows.innerHTML = cart.size ? [...cart.entries()].map(([id, q]) => {
      const p = find(id);
      const over = q > Number(p.quantityAvailable);
      return `<tr>
        <td>${FP.emoji(p.name)} ${FP.esc(p.name)}${over ? `<div class="small text-danger">Only ${FP.num(p.quantityAvailable)} available</div>` : ''}</td>
        <td><div class="input-group input-group-sm cart-qty"><input type="number" class="form-control ${over ? 'is-invalid' : ''}" min="0.01" step="0.01"
             value="${q}" data-qty="${id}"><span class="input-group-text">${FP.esc(p.unitOfMeasure)}</span></div></td>
        <td class="text-end">${Number(p.sellingPrice).toFixed(2)}</td>
        <td class="text-end">${(q * Number(p.sellingPrice)).toFixed(2)}</td>
        <td class="text-end"><button class="btn btn-sm btn-outline-danger" data-remove="${id}" aria-label="Remove"><i class="bi bi-trash"></i></button></td>
      </tr>`;
    }).join('') : '<tr><td colspan="5" class="empty"><i class="bi bi-cart fs-3 d-block"></i>Select products to add them to the sale</td></tr>';

    rows.querySelectorAll('[data-qty]').forEach(inp => inp.addEventListener('change', () => {
      const v = Math.round(Number(inp.value) * 100) / 100;
      if (v > 0) cart.set(Number(inp.dataset.qty), v); else cart.delete(Number(inp.dataset.qty));
      renderCart(); renderGrid();
    }));
    rows.querySelectorAll('[data-remove]').forEach(b => b.addEventListener('click', () => {
      cart.delete(Number(b.dataset.remove)); renderCart(); renderGrid();
    }));
    const t = totals();
    view.querySelector('#subTotal').textContent = t.sub.toFixed(2);
    view.querySelector('#total').textContent = t.total.toFixed(2);
  }

  async function refreshStock() {
    products = await FP.api('GET', '/api/products?activeOnly=true');
    renderGrid();
    renderCart();
  }

  discountInput.addEventListener('input', renderCart);
  view.querySelector('#pSearch').addEventListener('input', renderGrid);
  view.querySelector('#clearBtn').addEventListener('click', async () => {
    if (!cart.size || await FP.confirm('Clear sale', 'Remove all items from this sale? Nothing has been saved.', 'Clear', 'btn-danger')) {
      cart.clear(); discountInput.value = 0; setError(''); renderCart(); renderGrid();
    }
  });

  view.querySelector('#processBtn').addEventListener('click', async (e) => {
    const btn = e.currentTarget; // capture before any await (currentTarget is cleared afterwards)
    setError('');
    if (!cart.size) return setError('Add at least one product to the sale.');
    for (const [id, q] of cart) {
      const p = find(id);
      if (q > Number(p.quantityAvailable)) {
        return setError(`Insufficient stock for ${p.name}: only ${FP.num(p.quantityAvailable)} ${p.unitOfMeasure} available. Adjust the quantity or remove the item.`);
      }
    }
    const t = totals();
    if (t.discount > t.sub) return setError('Discount cannot be more than the subtotal.');

    // UC-02 step 5: the Agent confirms the sale.
    const ok = await FP.confirm('Confirm sale',
      `Process this sale of <strong>${cart.size}</strong> item(s) for <strong>${FP.money(t.total)}</strong>? Stock will be allocated from the oldest consignments first (FIFO).`,
      'Confirm sale');
    if (!ok) return;

    try {
      const sale = await FP.busy(btn, () => FP.api('POST', '/api/sales', {
        items: [...cart.entries()].map(([productId, quantity]) => ({ productId, quantity })),
        discount: t.discount
      }));
      const soldIds = [...cart.keys()];
      cart.clear();
      discountInput.value = 0;
      FP.toast(`Sale ${sale.receiptNumber} completed`);
      const m = Pages.showReceipt(sale, true);
      // Optional: sales forecast for the sold product (sequence diagram "opt" fragment).
      const footer = m.el.querySelector('.modal-footer');
      const link = document.createElement('a');
      link.className = 'btn btn-outline-fp me-auto';
      link.href = `#/forecasting/${soldIds[0]}`;
      link.innerHTML = '<i class="bi bi-graph-up-arrow me-1"></i>Sales forecast';
      link.addEventListener('click', () => m.close());
      footer.prepend(link);
      await refreshStock();
    } catch (err) {
      setError(err.message);
      if (err.status === 400) refreshStock();
    }
  });

  renderGrid();
  renderCart();
};
