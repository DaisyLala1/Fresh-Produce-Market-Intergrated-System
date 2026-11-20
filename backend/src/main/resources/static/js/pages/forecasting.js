/* AI Sales Forecasting – UC-05 View Sales Forecast */
'use strict';

Pages.forecasting = async (view, params) => {
  const products = await FP.api('GET', '/api/products?activeOnly=true');
  const preselect = params[0] || (products[0] && products[0].id);

  view.innerHTML = `
    <div class="mb-3"><h1 class="page-title">AI Sales Forecasting</h1>
      <div class="page-sub">Predict future sales using historical data and machine learning</div></div>
    <div class="card mb-3"><div class="card-body">
      <form class="row g-3 align-items-end" id="fForm">
        <div class="col-md-4"><label class="form-label fw-semibold">Product</label>
          <select class="form-select" name="productId">${products.map(p =>
            `<option value="${p.id}" ${String(p.id) === String(preselect) ? 'selected' : ''}>${FP.emoji(p.name)} ${FP.esc(p.name)}</option>`).join('')}</select></div>
        <div class="col-md-3"><label class="form-label fw-semibold">Forecast period</label>
          <select class="form-select" name="period"><option value="7">Next 7 days</option><option value="14">Next 14 days</option>
            <option value="30">Next 30 days</option></select></div>
        <div class="col-md-3"><label class="form-label fw-semibold">Model</label>
          <select class="form-select" disabled><option>Random Forest</option></select></div>
        <div class="col-md-2"><button class="btn btn-fp w-100" id="genBtn"><i class="bi bi-stars me-1"></i>Generate Forecast</button></div>
      </form>
    </div></div>
    <div id="result"><div class="card"><div class="card-body empty">
      <i class="bi bi-graph-up-arrow fs-1 d-block mb-2 text-fp"></i>Select a product and period, then click <strong>Generate Forecast</strong>.
      <div class="small mt-1">Forecasts support planning only – they never change stock or sales records.</div></div></div></div>
    <div class="card mt-3"><div class="card-header">Recent forecasts</div><div id="hist">${FP.loading()}</div></div>`;

  const form = view.querySelector('#fForm');
  const result = view.querySelector('#result');

  async function loadHistory() {
    const box = view.querySelector('#hist');
    try {
      const list = await FP.api('GET', '/api/forecast/history');
      box.innerHTML = list.length ? `<div class="table-responsive"><table class="table mb-0">
        <thead><tr><th>Generated</th><th>Product</th><th>Period</th><th class="text-end">Predicted</th>
          <th class="text-end">Expected revenue</th><th class="text-end">Model MAE</th><th class="text-end">Baseline MAE</th><th>By</th></tr></thead>
        <tbody>${list.map(f => `<tr><td class="text-nowrap">${FP.dateTime(f.createdDate)}</td><td>${FP.esc(f.productName)}</td>
          <td class="text-nowrap">${FP.shortDate(f.periodStart)} – ${FP.shortDate(f.periodEnd)}</td>
          <td class="text-end">${FP.qty(f.predictedQuantity, f.unitOfMeasure)}</td><td class="text-end">${FP.money(f.expectedRevenue)}</td>
          <td class="text-end">${f.modelMae ?? '–'}</td><td class="text-end">${f.baselineMae ?? '–'}</td>
          <td class="small">${FP.esc(f.requestedBy || '')}</td></tr>`).join('')}</tbody></table></div>`
        : '<div class="empty">No forecasts generated yet</div>';
    } catch (err) { box.innerHTML = FP.errorBlock(err); }
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const productId = Number(form.productId.value);
    const period = Number(form.period.value);
    if (!productId) return;
    if (!(period > 0)) return FP.toast('Forecast period must be greater than zero', 'danger');
    const product = products.find(p => p.id === productId);
    result.innerHTML = `<div class="card"><div class="card-body">${FP.loading('Training model and generating forecast…')}</div></div>`;
    try {
      const f = await FP.busy(view.querySelector('#genBtn'),
        () => FP.api('POST', '/api/forecast', { productId, forecastPeriodDays: period }));
      renderResult(f, product);
      loadHistory();
    } catch (err) {
      result.innerHTML = err.status === 503
        ? `<div class="alert alert-warning"><i class="bi bi-cloud-slash me-2"></i><strong>Forecasting unavailable.</strong> ${FP.esc(err.message)}</div>`
        : FP.errorBlock(err);
    }
  });

  function renderResult(f, product) {
    const unit = f.unitOfMeasure;
    const daily = f.dailyForecast || [];
    const history = f.recentHistory || [];
    const avgForecast = daily.length ? daily.reduce((s, x) => s + Number(x.quantity), 0) / daily.length : 0;
    const last7 = history.slice(-7);
    const avgRecent = last7.length ? last7.reduce((s, x) => s + Number(x.quantity), 0) / last7.length : 0;
    const change = avgRecent > 0 ? ((avgForecast - avgRecent) / avgRecent) * 100 : null;
    const peak = daily.reduce((best, x) => (!best || Number(x.quantity) > Number(best.quantity) ? x : best), null);
    const stock = Number(product.quantityAvailable);
    const predicted = Number(f.predictedQuantity);

    const insights = [];
    if (change !== null) {
      insights.push(`Average daily sales are expected to ${change >= 0 ? 'increase' : 'decrease'} by
        ${Math.abs(change).toFixed(0)}% compared with the last 7 days.`);
    }
    if (peak) insights.push(`Busiest forecast day: ${FP.dayLabel(peak.date)} with about ${FP.num(peak.quantity, 0)} ${FP.esc(unit)}.`);
    if (f.modelMae !== null && f.baselineMae !== null) {
      const better = Number(f.baselineMae) > 0 ? ((Number(f.baselineMae) - Number(f.modelMae)) / Number(f.baselineMae)) * 100 : 0;
      insights.push(better > 0
        ? `On the most recent 14 days the model's error (MAE ${f.modelMae}) was ${better.toFixed(0)}% lower than the simple baseline (MAE ${f.baselineMae}).`
        : `On the most recent 14 days the model (MAE ${f.modelMae}) did not beat the baseline (MAE ${f.baselineMae}) – treat this forecast with caution.`);
    }
    if (stock >= predicted) {
      insights.push(`Current stock of ${FP.num(stock)} ${FP.esc(unit)} covers the predicted demand for this period.`);
    } else {
      let covered = 0;
      let days = 0;
      for (const d of daily) { if (covered + Number(d.quantity) > stock) break; covered += Number(d.quantity); days++; }
      insights.push(`Current stock (${FP.num(stock)} ${FP.esc(unit)}) covers about ${days} day(s). Consider sourcing roughly
        ${FP.num(Math.ceil(predicted - stock), 0)} ${FP.esc(unit)} more to meet expected demand.`);
    }

    result.innerHTML = `
      <div class="row g-3">
        <div class="col-xl-5">
          <div class="card mb-3"><div class="card-header">Forecast Summary – ${FP.emoji(f.productName)} ${FP.esc(f.productName)}</div><div class="card-body">
            <div class="row g-2 text-center">
              <div class="col-6 col-md-3 col-xl-6"><div class="rounded p-2 tint-green"><i class="bi bi-bar-chart-fill text-success"></i>
                <div class="small text-muted">Predicted sales</div><div class="fw-bold fs-5">${FP.qty(f.predictedQuantity, unit)}</div></div></div>
              <div class="col-6 col-md-3 col-xl-6"><div class="rounded p-2 tint-blue"><i class="bi bi-graph-up text-primary"></i>
                <div class="small text-muted">Expected revenue</div><div class="fw-bold fs-5">${FP.money(f.expectedRevenue)}</div></div></div>
              <div class="col-6 col-md-3 col-xl-6"><div class="rounded p-2 tint-amber"><i class="bi bi-bullseye text-warning"></i>
                <div class="small text-muted">Model accuracy</div><div class="fw-bold fs-5">${f.accuracyPercent !== null ? FP.num(f.accuracyPercent, 1) + '%' : '–'}</div></div></div>
              <div class="col-6 col-md-3 col-xl-6"><div class="rounded p-2 tint-purple"><i class="bi bi-calendar-week" style="color:var(--fp-purple)"></i>
                <div class="small text-muted">Forecast period</div><div class="fw-bold fs-5">${f.forecastPeriodDays} Days</div></div></div>
            </div>
            <div class="small text-muted mt-2">Accuracy = 100% − weighted absolute percentage error on the last 14 days of known sales.</div>
          </div></div>
          <div class="card"><div class="card-header">Forecasted Sales Details</div><div class="table-responsive" style="max-height:360px">
            <table class="table table-sm mb-0"><thead><tr><th>Date</th><th class="text-end">Predicted (${FP.esc(unit)})</th>
              <th class="text-end">Price</th><th class="text-end">Revenue</th></tr></thead>
            <tbody>${daily.map(d => `<tr><td>${FP.dayLabel(d.date)}</td><td class="text-end">${FP.num(d.quantity, 1)}</td>
              <td class="text-end">${FP.money(f.sellingPrice)}</td><td class="text-end">${FP.money(Number(d.quantity) * Number(f.sellingPrice))}</td></tr>`).join('')}</tbody>
            <tfoot><tr class="total-row"><td>Total</td><td class="text-end">${FP.num(f.predictedQuantity, 1)}</td><td></td>
              <td class="text-end">${FP.money(f.expectedRevenue)}</td></tr></tfoot></table></div></div>
        </div>
        <div class="col-xl-7">
          <div class="card mb-3"><div class="card-header">Sales Forecast Chart</div><div class="card-body">
            <div class="chart-box"><canvas id="fChart"></canvas></div></div></div>
          <div class="card"><div class="card-header"><i class="bi bi-lightbulb text-warning me-1"></i>Key Insights</div><div class="card-body">
            ${insights.map(i => `<div class="insight"><i class="bi bi-check-circle-fill"></i><div>${i}</div></div>`).join('')}
            <div class="small text-muted mt-2">Model: ${FP.esc(f.modelType || 'Random Forest')} · Generated ${FP.dateTime(f.createdDate)}</div>
          </div></div>
        </div>
      </div>`;

    const labels = [...history.map(h => FP.shortDate(h.date)), ...daily.map(d => FP.shortDate(d.date))];
    const histData = [...history.map(h => Number(h.quantity)), ...daily.map(() => null)];
    const fcData = [...history.map((h, i) => (i === history.length - 1 ? Number(h.quantity) : null)), ...daily.map(d => Number(d.quantity))];
    FP.trackChart(new Chart(document.getElementById('fChart'), {
      type: 'line',
      data: {
        labels,
        datasets: [
          { label: 'Historical sales', data: histData, borderColor: '#9cc9ae', backgroundColor: 'rgba(156,201,174,.25)', fill: true, tension: .3, pointRadius: 2 },
          { label: 'Forecasted sales', data: fcData, borderColor: '#1f7a4d', borderDash: [6, 4], tension: .3, pointRadius: 3, pointBackgroundColor: '#1f7a4d' }
        ]
      },
      options: {
        maintainAspectRatio: false, spanGaps: false,
        interaction: { mode: 'index', intersect: false },
        scales: { y: { beginAtZero: true, title: { display: true, text: `Quantity (${unit})` } }, x: { grid: { display: false } } },
        plugins: { legend: { position: 'top' } }
      }
    }));
  }

  loadHistory();
  if (params[0]) form.requestSubmit();
};
