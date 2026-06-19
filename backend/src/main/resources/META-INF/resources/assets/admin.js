const $ = id => document.getElementById(id);

let token = localStorage.getItem('checklist-token') || '';
let challengeId = '';
let currentUser = null;
let currentUsers = [];
let purchasePreview = null;
let purchaseSchema = null;
let salesPreview = null;
let salesSchema = null;

const deviceId = localStorage.getItem('checklist-device') || crypto.randomUUID();
localStorage.setItem('checklist-device', deviceId);

const requiredMappingFields = new Set(['purchaseDate', 'category', 'location', 'totalInCents']);
const mappingFields = [
  ['purchaseDate', 'Data'],
  ['category', 'Categoria'],
  ['location', 'Local'],
  ['totalInCents', 'Valor'],
  ['description', 'Mercadoria'],
  ['supplier', 'Fornecedor'],
  ['documentNumber', 'Documento'],
  ['quantity', 'Quantidade'],
  ['unit', 'Unidade'],
  ['unitPriceInCents', 'Valor unitário']
];
const requiredSalesMappingFields = new Set(['description', 'quantity']);
const salesMappingFields = [
  ['saleDate', 'Data'],
  ['description', 'Produto'],
  ['location', 'Local'],
  ['quantity', 'Quantidade'],
  ['category', 'Categoria'],
  ['totalInCents', 'Valor'],
  ['documentNumber', 'Documento'],
  ['unit', 'Unidade'],
  ['unitPriceInCents', 'Valor unitário']
];

const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, char => ({
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  "'": '&#39;',
  '"': '&quot;'
}[char]));

const money = value => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format((Number(value) || 0) / 100);

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || 'Não foi possível concluir a operação');
  return body;
}

function message(text = '') {
  $('message').textContent = text;
}

function userMessage(text = '', isOk = false) {
  $('userMessage').innerHTML = text ? `<span class="${isOk ? 'import-ok' : ''}">${escapeHtml(text)}</span>` : '';
}

function setLoggedIn(value) {
  $('login').classList.toggle('hidden', value);
  $('workspace').classList.toggle('hidden', !value);
  $('logout').style.visibility = value ? 'visible' : 'hidden';
}

function canManageUsers(user = currentUser) {
  return Boolean(user && (user.permissionLevel === 'ADMIN' || user.permissions?.canRegisterUsers || user.permissions?.canEditUsers));
}

function canCreateUsers(user = currentUser) {
  return Boolean(user && (user.permissionLevel === 'ADMIN' || user.permissions?.canRegisterUsers));
}

function canEditUsers(user = currentUser) {
  return Boolean(user && (user.permissionLevel === 'ADMIN' || user.permissions?.canEditUsers));
}

function canManageActivities(user = currentUser) {
  return Boolean(user && (user.permissionLevel === 'ADMIN' || user.permissions?.canCreateActivities));
}

function isAdmin(user = currentUser) {
  return user?.permissionLevel === 'ADMIN';
}

function accessibleViews(user = currentUser) {
  const views = [];
  if (isAdmin(user)) views.push('dashboard');
  if (canManageUsers(user)) views.push('users');
  if (canManageActivities(user)) views.push('activities');
  if (isAdmin(user)) views.push('purchases');
  if (isAdmin(user)) views.push('sales');
  return views;
}

function configureNavigation() {
  const allowed = new Set(accessibleViews());
  document.querySelectorAll('[data-view]').forEach(button => {
    const enabled = allowed.has(button.dataset.view);
    button.classList.toggle('hidden', !enabled);
  });
  if (!allowed.has(activeView())) activateView([...allowed][0] || 'dashboard');
  $('showUserForm').classList.toggle('hidden', !canCreateUsers());
  $('showActivityForm').classList.toggle('hidden', !canManageActivities());
}

function activeView() {
  return document.querySelector('[data-view].active')?.dataset.view;
}

function activateView(viewId) {
  document.querySelectorAll('[data-view]').forEach(button => button.classList.toggle('active', button.dataset.view === viewId));
  document.querySelectorAll('.view').forEach(view => view.classList.toggle('hidden', view.id !== viewId));
  const selectedButton = document.querySelector(`[data-view="${viewId}"]`);
  $('pageTitle').textContent = selectedButton?.textContent || 'Painel';
  if (viewId === 'purchases' && token) loadPurchases();
  if (viewId === 'sales' && token) loadSales();
}

async function load() {
  currentUser = await api('/api/me');
  configureNavigation();

  if (isAdmin()) {
    renderDashboard(await api('/api/admin/dashboard'));
  } else {
    $('metrics').innerHTML = '';
    $('areaBars').innerHTML = '<p>Disponível apenas para administradores.</p>';
  }

  if (canManageUsers()) {
    currentUsers = await api('/api/users');
    renderUsers(currentUsers);
  } else {
    currentUsers = [];
    $('usersBody').innerHTML = '';
  }

  if (canManageActivities()) {
    renderActivities(await api('/api/activities'));
  } else {
    $('activityCards').innerHTML = '<p>Sem permissão para gerenciar atividades.</p>';
  }

  setLoggedIn(true);
}

function renderDashboard(data) {
  const values = [
    ['Pessoas', data.totalUsers],
    ['Atividades', data.totalActivities],
    ['Conclusões', data.totalCompletions],
    ['Sync pendente', data.pendingSyncItems]
  ];
  $('metrics').innerHTML = values.map(([label, value]) => `<div class="metric"><span>${label}</span><strong>${value}</strong></div>`).join('');
  const entries = Object.entries(data.activitiesByArea || {});
  const max = Math.max(1, ...entries.map(([, count]) => count));
  $('areaBars').innerHTML = entries.map(([name, count]) => `
    <div class="bar-row">
      <span>${escapeHtml(name)}</span>
      <div class="bar"><i style="width:${count / max * 100}%"></i></div>
      <strong>${count}</strong>
    </div>`).join('') || '<p>Nenhuma atividade cadastrada.</p>';
}

function renderUsers(users) {
  const allowPermissionEdit = isAdmin();
  $('usersBody').innerHTML = users.map(user => `
    <tr>
      <td>
        <strong>${escapeHtml(user.name)}</strong>
        <small>${escapeHtml(user.email)}</small>
        <small>${escapeHtml(user.workSector || '')}</small>
      </td>
      <td>${user.permissionLevel}</td>
      ${['canRegisterUsers', 'canCreateActivities', 'canEditUsers'].map(key => `
        <td><input type="checkbox" data-user="${user.id}" data-key="${key}" ${user.permissions?.[key] ? 'checked' : ''} ${!allowPermissionEdit || user.permissionLevel === 'ADMIN' ? 'disabled' : ''}></td>
      `).join('')}
      <td class="user-actions">
        ${canEditUsers() ? `<button type="button" class="secondary" data-user-edit="${user.id}">Editar</button>` : ''}
        ${canEditUsers() ? `<button type="button" class="secondary" data-user-reset="${user.id}">Resetar senha</button>` : ''}
        ${canEditUsers() && user.permissionLevel !== 'ADMIN' ? `<button type="button" class="danger" data-user-delete="${user.id}">Remover</button>` : ''}
      </td>
    </tr>
  `).join('');

  document.querySelectorAll('[data-user]').forEach(input => {
    input.addEventListener('change', () => changePermission(users, input));
  });
  document.querySelectorAll('[data-user-edit]').forEach(button => {
    button.addEventListener('click', () => startEditUser(button.dataset.userEdit));
  });
  document.querySelectorAll('[data-user-reset]').forEach(button => {
    button.addEventListener('click', () => resetPassword(button.dataset.userReset));
  });
  document.querySelectorAll('[data-user-delete]').forEach(button => {
    button.addEventListener('click', () => deleteUser(button.dataset.userDelete));
  });
}

async function changePermission(users, input) {
  const user = users.find(value => value.id === input.dataset.user);
  const permissions = { ...user.permissions, [input.dataset.key]: input.checked };
  try {
    await api(`/api/users/${user.id}/permissions`, { method: 'PATCH', body: JSON.stringify({ permissions }) });
    await load();
  } catch (error) {
    alert(error.message);
    input.checked = !input.checked;
  }
}

function renderActivities(values) {
  $('activityCards').innerHTML = values.map(value => `
    <div class="activity">
      <h3>${escapeHtml(value.name)}</h3>
      <footer><span>${value.area}</span><span>${value.frequency} · esforço ${value.effort}</span></footer>
    </div>
  `).join('');
}

function resetUserForm() {
  $('userForm').reset();
  $('userId').value = '';
  $('userPassword').required = false;
  $('userPassword').placeholder = 'Obrigatória ao criar';
  $('userForm').classList.add('hidden');
  userMessage('');
}

function openUserForm({ user = null } = {}) {
  $('userForm').classList.remove('hidden');
  $('userId').value = user?.id || '';
  $('userName').value = user?.name || '';
  $('userEmail').value = user?.email || '';
  $('userPassword').value = '';
  $('userPassword').required = !user;
  $('userWorkSector').value = user?.workSector || 'GERENTE';
  $('userPermissionLevel').value = user?.permissionLevel || 'USER';
  userMessage('');
}

function startEditUser(userId) {
  const user = currentUsers.find(value => value.id === userId);
  if (!user) return;
  openUserForm({ user });
}

async function saveUser(event) {
  event.preventDefault();
  const id = $('userId').value;
  const payload = {
    name: $('userName').value.trim(),
    email: $('userEmail').value.trim(),
    workSector: $('userWorkSector').value,
    permissionLevel: $('userPermissionLevel').value
  };

  try {
    if (id) {
      await api(`/api/users/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
      userMessage('Usuário atualizado com sucesso.', true);
    } else {
      await api('/api/users', {
        method: 'POST',
        body: JSON.stringify({ ...payload, password: $('userPassword').value, permissions: { canRegisterUsers: false, canCreateActivities: false, canEditUsers: false } })
      });
      userMessage('Usuário criado com sucesso.', true);
    }
    await load();
    resetUserForm();
  } catch (error) {
    userMessage(error.message);
  }
}

async function resetPassword(userId) {
  const user = currentUsers.find(value => value.id === userId);
  if (!user) return;
  const newPassword = prompt(`Nova senha para ${user.name}:`);
  if (!newPassword) return;
  try {
    await api(`/api/users/${userId}/reset-password`, { method: 'POST', body: JSON.stringify({ newPassword }) });
    userMessage(`Senha de ${user.name} redefinida com sucesso.`, true);
  } catch (error) {
    userMessage(error.message);
  }
}

async function deleteUser(userId) {
  const user = currentUsers.find(value => value.id === userId);
  if (!user) return;
  if (!confirm(`Remover o usuário ${user.name}?`)) return;
  try {
    await api(`/api/users/${userId}`, { method: 'DELETE' });
    userMessage(`Usuário ${user.name} removido.`, true);
    await load();
  } catch (error) {
    userMessage(error.message);
  }
}

$('loginForm').addEventListener('submit', async event => {
  event.preventDefault();
  message();
  try {
    const data = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        email: $('email').value,
        password: $('password').value,
        deviceId,
        deviceName: navigator.userAgent
      })
    });
    if (data.requiresTwoFactor) {
      challengeId = data.challengeId;
      $('challengeForm').classList.remove('hidden');
      message(data.developmentCode ? `Código local: ${data.developmentCode}` : data.deliveryHint);
      return;
    }
    token = data.token;
    localStorage.setItem('checklist-token', token);
    await load();
  } catch (error) {
    message(error.message);
  }
});

$('challengeForm').addEventListener('submit', async event => {
  event.preventDefault();
  try {
    const data = await api('/api/auth/verify-device', {
      method: 'POST',
      body: JSON.stringify({
        challengeId,
        code: $('code').value,
        deviceId,
        deviceName: navigator.userAgent
      })
    });
    token = data.token;
    localStorage.setItem('checklist-token', token);
    await load();
  } catch (error) {
    message(error.message);
  }
});

$('activityForm').addEventListener('submit', async event => {
  event.preventDefault();
  try {
    await api('/api/activities', {
      method: 'POST',
      body: JSON.stringify({
        name: $('activityName').value,
        area: $('activityArea').value,
        frequency: $('activityFrequency').value,
        effort: Number($('activityEffort').value)
      })
    });
    event.target.reset();
    event.target.classList.add('hidden');
    await load();
  } catch (error) {
    alert(error.message);
  }
});

$('showActivityForm').addEventListener('click', () => $('activityForm').classList.toggle('hidden'));
$('showUserForm').addEventListener('click', () => openUserForm());
$('userForm').addEventListener('submit', saveUser);
$('cancelUserForm').addEventListener('click', resetUserForm);

$('logout').addEventListener('click', () => {
  token = '';
  currentUser = null;
  localStorage.removeItem('checklist-token');
  setLoggedIn(false);
  resetUserForm();
});

const currencyToCents = value => {
  const raw = String(value || '').trim().replace(/[^0-9,.-]/g, '');
  if (!raw) return null;
  const normalized = raw.includes(',') ? raw.replace(/\./g, '').replace(',', '.') : raw;
  const number = Number(normalized);
  return Number.isFinite(number) ? Math.round(number * 100) : null;
};

const isoDate = value => value instanceof Date ? value.toISOString().slice(0, 10) : value;

function setPurchaseDates() {
  const now = new Date();
  const from = new Date(now);
  from.setFullYear(now.getFullYear() - 1);
  $('purchaseFrom').value = isoDate(from);
  $('purchaseTo').value = isoDate(now);
}

function setSalesDates() {
  const now = new Date();
  const from = new Date(now);
  from.setMonth(now.getMonth() - 1);
  $('salesFrom').value = isoDate(from);
  $('salesTo').value = isoDate(now);
}

function renderPurchasePreview(data) {
  purchasePreview = data;
  $('purchasePreview').classList.remove('hidden');
  $('purchasePreviewTitle').textContent = `${data.fileName} · ${data.totalRows} linhas`;
  $('purchaseMapping').innerHTML = mappingFields.map(([key, label]) => `
    <label>${label} <small>${requiredMappingFields.has(key) ? '(obrigatório)' : '(opcional)'}</small>
      <select data-purchase-map="${key}">
        <option value="">Não mapear</option>
        ${data.headers.map(header => `<option value="${escapeHtml(header)}" ${data.suggestedMapping?.[key] === header ? 'selected' : ''}>${escapeHtml(header)}</option>`).join('')}
      </select>
    </label>
  `).join('');
  $('purchasePreviewHead').innerHTML = `<tr>${data.headers.map(header => `<th>${escapeHtml(header)}</th>`).join('')}</tr>`;
  $('purchasePreviewBody').innerHTML = data.sampleRows.map(row => `<tr>${data.headers.map(header => `<td>${escapeHtml(row[header])}</td>`).join('')}</tr>`).join('');
  $('purchaseImportMessage').textContent = data.errors?.map(error => error.message).join(' · ') || 'Todas as colunas serão preservadas; os quatro campos destacados habilitam as consultas padronizadas.';
}

function renderSalesPreview(data) {
  salesPreview = data;
  $('salesPreview').classList.remove('hidden');
  $('salesPreviewTitle').textContent = `${data.fileName} · ${data.totalRows} linhas`;
  $('salesMapping').innerHTML = salesMappingFields.map(([key, label]) => `
    <label>${label} <small>${requiredSalesMappingFields.has(key) ? '(obrigatório)' : '(opcional)'}</small>
      <select data-sales-map="${key}">
        <option value="">Não mapear</option>
        ${data.headers.map(header => `<option value="${escapeHtml(header)}" ${data.suggestedMapping?.[key] === header ? 'selected' : ''}>${escapeHtml(header)}</option>`).join('')}
      </select>
    </label>
  `).join('');
  $('salesPreviewHead').innerHTML = `<tr>${data.headers.map(header => `<th>${escapeHtml(header)}</th>`).join('')}</tr>`;
  $('salesPreviewBody').innerHTML = data.sampleRows.map(row => `<tr>${data.headers.map(header => `<td>${escapeHtml(row[header])}</td>`).join('')}</tr>`).join('');
  $('salesImportMessage').textContent = data.errors?.map(error => error.message).join(' · ') || 'Mapeie ao menos produto e quantidade. Data e local podem ser preenchidos automaticamente quando não vierem no relatório.';
}

async function loadPurchaseSchema() {
  purchaseSchema = await api('/api/purchases/schema');
  return purchaseSchema;
}

async function loadSalesSchema() {
  salesSchema = await api('/api/sales/schema');
  return salesSchema;
}

function purchaseQuery(schema) {
  const query = {
    categories: $('purchaseCategory').value ? [$('purchaseCategory').value] : [],
    locations: $('purchaseLocation').value ? [$('purchaseLocation').value] : [],
    text: $('purchaseText').value,
    page: 0,
    pageSize: 100,
    sort: [{ field: schema.coverageFrom ? 'purchaseDate' : 'importedAt', direction: 'DESC' }]
  };
  const min = currencyToCents($('purchaseMinTotal').value);
  const max = currencyToCents($('purchaseMaxTotal').value);
  if (min !== null) query.minTotalInCents = min;
  if (max !== null) query.maxTotalInCents = max;
  if (schema.coverageFrom) {
    query.from = $('purchaseFrom').value;
    query.to = $('purchaseTo').value;
  }
  return query;
}

function salesQuery(schema) {
  const query = {
    categories: $('salesCategory').value ? [$('salesCategory').value] : [],
    locations: $('salesLocation').value ? [$('salesLocation').value] : [],
    text: $('salesText').value,
    page: 0,
    pageSize: 100,
    sort: [{ field: schema.coverageFrom ? 'saleDate' : 'importedAt', direction: 'DESC' }]
  };
  const min = currencyToCents($('salesMinTotal').value);
  const max = currencyToCents($('salesMaxTotal').value);
  if (min !== null) query.minTotalInCents = min;
  if (max !== null) query.maxTotalInCents = max;
  if (schema.coverageFrom) {
    query.from = $('salesFrom').value;
    query.to = $('salesTo').value;
  }
  return query;
}

async function loadPurchases() {
  try {
    $('purchaseFilterMessage').textContent = 'Buscando…';
    const schema = await loadPurchaseSchema();
    const data = await api('/api/purchases/query', { method: 'POST', body: JSON.stringify(purchaseQuery(schema)) });
    renderPurchases(schema, data);
    $('purchaseFilterMessage').textContent = data.filtersApplied?.length ? `Filtros ativos: ${data.filtersApplied.join(' · ')}` : '';
  } catch (error) {
    $('purchaseFilterMessage').textContent = error.message;
    $('purchasesBody').innerHTML = '';
    $('purchaseMetrics').innerHTML = '';
    $('purchasesEmpty').classList.remove('hidden');
  }
}

async function loadSales() {
  try {
    $('salesFilterMessage').textContent = 'Buscando…';
    const schema = await loadSalesSchema();
    const data = await api('/api/sales/query', { method: 'POST', body: JSON.stringify(salesQuery(schema)) });
    renderSales(schema, data);
    $('salesFilterMessage').textContent = data.filtersApplied?.length ? `Filtros ativos: ${data.filtersApplied.join(' · ')}` : '';
    await loadSalesAudit();
  } catch (error) {
    $('salesFilterMessage').textContent = error.message;
    $('salesBody').innerHTML = '';
    $('salesMetrics').innerHTML = '';
    $('salesEmpty').classList.remove('hidden');
  }
}

function renderPurchases(schema, data) {
  const dynamic = (schema.fields || []).filter(field => !field.normalized).slice(0, 12);
  const normalized = [
    ['purchaseDate', 'Data'],
    ['category', 'Categoria'],
    ['location', 'Local'],
    ['totalInCents', 'Valor'],
    ['description', 'Mercadoria'],
    ['supplier', 'Fornecedor'],
    ['quantity', 'Qtd.']
  ].filter(([key]) => (data.items || []).some(item => item[key] !== null && item[key] !== undefined && item[key] !== '' && (key !== 'totalInCents' || item[key] !== 0)));
  const columns = [...normalized, ...dynamic.map(field => [field.key, field.label])];
  $('purchasesHead').innerHTML = `<tr>${columns.map(column => `<th>${escapeHtml(column[1])}</th>`).join('')}</tr>`;
  $('purchasesBody').innerHTML = (data.items || []).map(item => `
    <tr>${columns.map(([key]) => {
      const value = key in item ? item[key] : item.attributes?.[key];
      return `<td class="${key === 'totalInCents' ? 'money' : ''}">${key === 'totalInCents' ? money(value) : escapeHtml(value)}</td>`;
    }).join('')}</tr>
  `).join('');
  $('purchasesEmpty').classList.toggle('hidden', data.totalItems > 0);
  $('purchaseMetrics').innerHTML = `
    <span><strong>${data.totalItems}</strong> registros</span>
    <span><strong>${money(data.totalInCents)}</strong> total mapeado</span>
    <span><strong>${dynamic.length}</strong> campos dinâmicos</span>
  `;
}

function renderSales(schema, data) {
  const dynamic = (schema.fields || []).filter(field => !field.normalized).slice(0, 12);
  const normalized = [
    ['saleDate', 'Data'],
    ['description', 'Produto'],
    ['category', 'Categoria'],
    ['location', 'Local'],
    ['quantity', 'Qtd.'],
    ['unit', 'Unidade'],
    ['unitPriceInCents', 'Valor unit.'],
    ['totalInCents', 'Valor']
  ].filter(([key]) => (data.items || []).some(item => item[key] !== null && item[key] !== undefined && item[key] !== '' && (!key.endsWith('InCents') || item[key] !== 0)));
  const columns = [...normalized, ...dynamic.map(field => [field.key, field.label])];
  $('salesHead').innerHTML = `<tr>${columns.map(column => `<th>${escapeHtml(column[1])}</th>`).join('')}</tr>`;
  $('salesBody').innerHTML = (data.items || []).map(item => `
    <tr>${columns.map(([key]) => {
      const value = key in item ? item[key] : item.attributes?.[key];
      return `<td class="${key.endsWith('InCents') ? 'money' : ''}">${key.endsWith('InCents') ? money(value) : escapeHtml(value)}</td>`;
    }).join('')}</tr>
  `).join('');
  $('salesEmpty').classList.toggle('hidden', data.totalItems > 0);
  $('salesMetrics').innerHTML = `
    <span><strong>${data.totalItems}</strong> registros</span>
    <span><strong>${money(data.totalInCents)}</strong> vendido</span>
    <span><strong>${dynamic.length}</strong> campos dinâmicos</span>
  `;
}

async function loadSalesAudit() {
  if (!$('salesFrom').value || !$('salesTo').value) return;
  try {
    $('salesAuditMessage').textContent = 'Gerando auditoria…';
    const body = {
      from: $('salesFrom').value,
      to: $('salesTo').value,
      purchaseDatasetId: 'purchases',
      salesDatasetId: 'sales',
      text: $('salesText').value || undefined,
      locations: $('salesLocation').value ? [$('salesLocation').value] : []
    };
    const data = await api('/api/sales/audit/stock', { method: 'POST', body: JSON.stringify(body) });
    $('salesAuditBody').innerHTML = (data.items || []).map(item => `
      <tr>
        <td><strong>${escapeHtml(item.status)}</strong></td>
        <td>${escapeHtml(item.description)}</td>
        <td>${escapeHtml(item.location)}</td>
        <td>${escapeHtml(item.stockedQuantity)}</td>
        <td>${escapeHtml(item.soldQuantity)}</td>
        <td>${escapeHtml(item.differenceQuantity)}</td>
        <td>${escapeHtml(item.notes)}</td>
      </tr>
    `).join('');
    $('salesAuditEmpty').classList.toggle('hidden', (data.totalItems || 0) > 0);
    $('salesAuditMessage').textContent = data.filtersApplied?.length ? `Auditoria com filtros: ${data.filtersApplied.join(' · ')}` : 'Auditoria atualizada.';
  } catch (error) {
    $('salesAuditMessage').textContent = error.message;
    $('salesAuditBody').innerHTML = '';
    $('salesAuditEmpty').classList.remove('hidden');
  }
}

$('purchaseImportForm').addEventListener('submit', async event => {
  event.preventDefault();
  const file = $('purchaseFile').files[0];
  const pasted = $('purchaseCsvText').value.trim();
  if (!file && !pasted) {
    $('purchaseImportMessage').textContent = 'Selecione um arquivo ou cole o conteúdo CSV.';
    return;
  }
  $('purchaseImportMessage').textContent = 'Analisando arquivo…';
  try {
    const data = await api('/api/purchases/imports/preview', {
      method: 'POST',
      body: JSON.stringify({ fileName: file?.name || 'compras-coladas.csv', csv: file ? await file.text() : pasted })
    });
    renderPurchasePreview(data);
  } catch (error) {
    $('purchaseImportMessage').textContent = error.message;
  }
});

$('commitPurchaseImport').addEventListener('click', async () => {
  if (!purchasePreview) return;
  const mapping = {};
  document.querySelectorAll('[data-purchase-map]').forEach(select => {
    if (select.value) mapping[select.dataset.purchaseMap] = select.value;
  });
  const missing = [...requiredMappingFields].filter(key => !mapping[key]);
  if (missing.length) {
    $('purchaseImportMessage').textContent = 'Mapeie data, categoria, local e valor antes de importar.';
    return;
  }
  $('purchaseImportMessage').textContent = 'Importando…';
  try {
    const data = await api(`/api/purchases/imports/${purchasePreview.id}/commit`, {
      method: 'POST',
      body: JSON.stringify({ datasetId: 'purchases', mapping, preserveColumns: purchasePreview.headers })
    });
    $('purchaseImportMessage').innerHTML = `<span class="import-ok">${data.importedRows} linhas importadas, ${data.duplicateRows} duplicadas e ${data.rejectedRows} rejeitadas.</span>`;
    $('purchasePreview').classList.add('hidden');
    purchasePreview = null;
    await loadPurchases();
  } catch (error) {
    $('purchaseImportMessage').textContent = error.message;
  }
});

$('salesImportForm').addEventListener('submit', async event => {
  event.preventDefault();
  const file = $('salesFile').files[0];
  const pasted = $('salesCsvText').value.trim();
  if (!file && !pasted) {
    $('salesImportMessage').textContent = 'Selecione um arquivo ou cole o conteúdo CSV.';
    return;
  }
  $('salesImportMessage').textContent = 'Analisando arquivo…';
  try {
    const data = await api('/api/sales/imports/preview', {
      method: 'POST',
      body: JSON.stringify({ fileName: file?.name || 'vendas-coladas.csv', csv: file ? await file.text() : pasted })
    });
    renderSalesPreview(data);
  } catch (error) {
    $('salesImportMessage').textContent = error.message;
  }
});

$('commitSalesImport').addEventListener('click', async () => {
  if (!salesPreview) return;
  const mapping = {};
  document.querySelectorAll('[data-sales-map]').forEach(select => {
    if (select.value) mapping[select.dataset.salesMap] = select.value;
  });
  const missing = [...requiredSalesMappingFields].filter(key => !mapping[key]);
  if (missing.length) {
    $('salesImportMessage').textContent = 'Mapeie produto e quantidade antes de importar.';
    return;
  }
  $('salesImportMessage').textContent = 'Importando…';
  try {
    const data = await api(`/api/sales/imports/${salesPreview.id}/commit`, {
      method: 'POST',
      body: JSON.stringify({ datasetId: 'sales', mapping, preserveColumns: salesPreview.headers })
    });
    $('salesImportMessage').innerHTML = `<span class="import-ok">${data.importedRows} linhas importadas, ${data.duplicateRows} duplicadas e ${data.rejectedRows} rejeitadas.</span>`;
    $('salesPreview').classList.add('hidden');
    salesPreview = null;
    await loadSales();
  } catch (error) {
    $('salesImportMessage').textContent = error.message;
  }
});

$('purchaseFilters').addEventListener('submit', event => {
  event.preventDefault();
  loadPurchases();
});
$('refreshPurchases').addEventListener('click', loadPurchases);
$('clearPurchaseFilters').addEventListener('click', () => {
  $('purchaseCategory').value = '';
  $('purchaseLocation').value = '';
  $('purchaseMinTotal').value = '';
  $('purchaseMaxTotal').value = '';
  $('purchaseText').value = '';
  setPurchaseDates();
  loadPurchases();
});

setPurchaseDates();
setSalesDates();

$('salesFilters').addEventListener('submit', event => {
  event.preventDefault();
  loadSales();
});
$('refreshSales').addEventListener('click', loadSales);
$('refreshSalesAudit').addEventListener('click', loadSalesAudit);
$('clearSalesFilters').addEventListener('click', () => {
  $('salesCategory').value = '';
  $('salesLocation').value = '';
  $('salesMinTotal').value = '';
  $('salesMaxTotal').value = '';
  $('salesText').value = '';
  setSalesDates();
  loadSales();
});

document.querySelectorAll('[data-view]').forEach(button => {
  button.addEventListener('click', () => activateView(button.dataset.view));
});

api('/api/health')
  .then(() => { $('apiStatus').textContent = 'API online'; })
  .catch(() => { $('apiStatus').textContent = 'API indisponível'; });

if (token) {
  load().catch(() => {
    token = '';
    currentUser = null;
    localStorage.removeItem('checklist-token');
    setLoggedIn(false);
  });
} else {
  setLoggedIn(false);
}
