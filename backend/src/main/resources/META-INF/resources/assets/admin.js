const $ = id => document.getElementById(id);

let token = localStorage.getItem('checklist-token') || '';
let challengeId = '';
let currentUser = null;
let currentUsers = [];
let currentActivities = [];
let purchasePreview = null;
let purchaseSchema = null;
let salesPreview = null;
let salesSchema = null;
let countDraft = (() => { try { return JSON.parse(localStorage.getItem('inventory-count-draft') || '[]'); } catch (_) { return []; } })();
let adminCountDraft = (() => { try { return JSON.parse(localStorage.getItem('inventory-admin-count-draft') || '[]'); } catch (_) { return []; } })();
let inventoryCountMode = localStorage.getItem('inventory-count-mode') || 'daily';

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
const requiredSalesMappingFields = new Set(['saleDate', 'description', 'quantity']);
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

const semanticColumnKey = value => {
  const key = String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
  const aliases = {
    saleDate: ['data', 'data_venda', 'dt_venda', 'emissao', 'date', 'data_movimento', 'dia', 'dt', 'data_do_item', 'data_item', 'data_da_venda'],
    description: ['produto', 'item', 'mercadoria', 'descricao', 'description', 'nome'],
    category: ['categoria', 'grupo', 'departamento', 'category'],
    location: ['local', 'loja', 'unidade', 'pdv', 'location'],
    quantity: ['quantidade', 'qtd', 'qtde', 'quantity'],
    totalInCents: ['total', 'valor_total', 'valor', 'receita', 'faturamento', 'total_venda', 'valor_venda'],
    documentNumber: ['cupom', 'pedido', 'documento', 'numero_documento', 'cod_produto', 'codigo_produto'],
    unit: ['unidade', 'un', 'unit', 'tipo', 'tipo_preco'],
    unitPriceInCents: ['valor_unitario', 'preco_unitario', 'ticket_medio', 'unit_price', 'val_unit', 'vl_unit', 'val_unitario']
  };
  for (const [field, values] of Object.entries(aliases)) {
    if (values.includes(key)) return field;
  }
  return key;
};

const money = value => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format((Number(value) || 0) / 100);

function readApiError(body, status) {
  if (body?.message) return body.message;
  if (body?.attributeName) {
    const labels = {
      workSector: 'Setor',
      permissionLevel: 'Perfil',
      permissions: 'Permissões',
      name: 'Nome',
      email: 'Email'
    };
    const label = labels[body.attributeName] || body.attributeName;
    return `Valor inválido para ${label}.`;
  }
  if (status === 401) return 'Sessão expirada. Faça login novamente.';
  if (status === 403) return 'Você não tem permissão para esta operação.';
  if (status === 404) return 'Recurso não encontrado.';
  if (typeof body?.raw === 'string' && body.raw.trim()) return body.raw.trim();
  return `Não foi possível concluir a operação (HTTP ${status}).`;
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  });

  if (response.status === 204) {
    if (!response.ok) throw new Error(readApiError({}, response.status));
    return null;
  }

  const text = await response.text();
  const body = text
    ? (() => {
        try {
          return JSON.parse(text);
        } catch (_) {
          return { raw: text };
        }
      })()
    : {};

  if (!response.ok) {
    if (response.status === 401) {
      token = '';
      currentUser = null;
      localStorage.removeItem('checklist-token');
      setLoggedIn(false);
    }
    throw new Error(readApiError(body, response.status));
  }

  return body;
}

function buildUserPermissions(user, key, value) {
  const base = user?.permissions || {};
  return {
    canRegisterUsers: Boolean(base.canRegisterUsers),
    canCreateActivities: Boolean(base.canCreateActivities),
    canEditUsers: Boolean(base.canEditUsers),
    canCreateInventoryCounts: Boolean(base.canCreateInventoryCounts),
    canViewInventoryInsights: Boolean(base.canViewInventoryInsights),
    canManageAdministrativeStock: Boolean(base.canManageAdministrativeStock),
    [key]: value
  };
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

function canCreateInventoryCounts(user = currentUser) {
  return Boolean(user && (isAdmin(user) || user.permissions?.canCreateInventoryCounts));
}

function canViewInventoryInsights(user = currentUser) {
  return Boolean(user && (isAdmin(user) || user.permissions?.canViewInventoryInsights));
}

function canManageAdministrativeStock(user = currentUser) {
  return Boolean(user && (isAdmin(user) || user.permissions?.canManageAdministrativeStock));
}

function accessibleViews(user = currentUser) {
  const views = [];
  if (isAdmin(user)) views.push('dashboard');
  if (canManageUsers(user)) views.push('users');
  if (canManageActivities(user)) views.push('activities');
  if (canCreateInventoryCounts(user) || canViewInventoryInsights(user) || canManageAdministrativeStock(user)) views.push('inventory');
  if (isAdmin(user)) views.push('purchases');
  if (isAdmin(user)) views.push('sales');
  if (isAdmin(user)) views.push('workclock');
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
  if (viewId === 'inventory' && token) loadCounts();
  if (viewId === 'workclock' && token) loadWorkClock();
}

async function load() {
  currentUser = await api('/api/me');
  configureNavigation();

  if (isAdmin()) {
    renderDashboard(await api('/api/admin/dashboard'));
    await loadChecklistOverview();
    renderChecklistSchedule(await api('/api/checklist/schedule'));
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
    currentActivities = await api('/api/activities');
    renderActivities(currentActivities);
    $('activityAssignees').innerHTML = currentUsers.filter(user => user.permissionLevel !== 'ADMIN').map(user => `<option value="${user.id}">${escapeHtml(user.name)}</option>`).join('');
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

const timingLabel = { GREEN: 'Dentro do prazo', YELLOW: 'No limite', RED: 'Atrasada', COMPLETED: 'Concluída' };
async function loadChecklistOverview() {
  if (!isAdmin()) return;
  const data = await api('/api/admin/checklist/overview');
  $('checklistOverviewMetrics').innerHTML = [['Dentro do prazo', data.green], ['No limite', data.yellow], ['Atrasadas', data.red], ['Concluídas', data.completed], ['Minutos restantes', data.totalRemainingMinutes]].map(([label, value]) => `<div class="metric"><span>${label}</span><strong>${value}</strong></div>`).join('');
  $('checklistOverviewBody').innerHTML = (data.occurrences || []).map(item => `<tr><td><strong>${escapeHtml(item.activityName)}</strong><small>${escapeHtml(item.executionPhase)} · ${item.estimatedDurationMinutes} min</small></td><td>${new Date(item.deadlineAt).toLocaleTimeString('pt-BR', {hour:'2-digit',minute:'2-digit'})}</td><td><span class="timing-status timing-${String(item.status).toLowerCase()}">${timingLabel[item.status] || item.status}</span></td><td>${escapeHtml((item.assigneeNames || []).join(', ') || 'Equipe do setor')}</td><td>${item.completion ? `${escapeHtml(item.completedByName || '')}<small>${new Date(item.completion.completedAt).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})}</small>` : '—'}</td></tr>`).join('') || '<tr><td colspan="5">Nenhuma atividade prevista para hoje.</td></tr>';
}

const weekdayLabels = { MONDAY:'Segunda', TUESDAY:'Terça', WEDNESDAY:'Quarta', THURSDAY:'Quinta', FRIDAY:'Sexta', SATURDAY:'Sábado', SUNDAY:'Domingo' };
function renderChecklistSchedule(schedule) {
  $('checklistScheduleDays').innerHTML = Object.entries(weekdayLabels).map(([day, label]) => {
    const value = schedule.days?.[day] || { active:false };
    return `<fieldset data-schedule-day="${day}"><legend>${label}</legend><label><input type="checkbox" data-field="active" ${value.active ? 'checked' : ''}> Em operação</label><label>Entrada<input type="time" data-field="entryTime" value="${value.entryTime || ''}"></label><label>Almoço<input type="time" data-field="lunchTime" value="${value.lunchTime || ''}"></label><label>Abertura<input type="time" data-field="openingTime" value="${value.openingTime || ''}"></label><label>Encerramento<input type="time" data-field="closingTime" value="${value.closingTime || ''}"></label><label>Evento<input data-field="eventLabel" value="${escapeHtml(value.eventLabel || '')}"></label></fieldset>`;
  }).join('');
}

async function saveChecklistSchedule(event) {
  event.preventDefault();
  const days = {};
  document.querySelectorAll('[data-schedule-day]').forEach(fieldset => {
    const read = field => fieldset.querySelector(`[data-field="${field}"]`);
    const day = fieldset.dataset.scheduleDay;
    days[day] = { dayOfWeek:day, active:read('active').checked, entryTime:read('entryTime').value || null, lunchTime:read('lunchTime').value || null, openingTime:read('openingTime').value || null, closingTime:read('closingTime').value || null, eventLabel:read('eventLabel').value.trim() || null };
  });
  renderChecklistSchedule(await api('/api/checklist/schedule', { method:'PUT', body:JSON.stringify({ timezone:'America/Fortaleza', days }) }));
  await loadChecklistOverview();
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
      ${['canRegisterUsers', 'canCreateActivities', 'canEditUsers', 'canCreateInventoryCounts', 'canViewInventoryInsights', 'canManageAdministrativeStock'].map(key => `
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
    input.addEventListener('change', () => changePermission(input));
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

async function changePermission(input) {
  const user = currentUsers.find(value => value.id === input.dataset.user);
  if (!user) {
    alert('Usuário não encontrado. Atualize a página.');
    input.checked = !input.checked;
    return;
  }
  const permissions = buildUserPermissions(user, input.dataset.key, input.checked);
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
      <p>${escapeHtml(value.executionPhase || 'BEFORE_LUNCH')} · ${value.estimatedDurationMinutes || 15} min</p>
      <p>${escapeHtml((value.assigneeIds || []).map(id => currentUsers.find(user => user.id === id)?.name).filter(Boolean).join(', ') || 'Equipe do setor')}</p>
      <footer><span>${value.area}</span><span>${value.frequency} · esforço ${value.effort}</span><button type="button" class="secondary" data-activity-edit="${value.id}">Editar</button></footer>
    </div>
  `).join('');
  document.querySelectorAll('[data-activity-edit]').forEach(button => button.addEventListener('click', () => editActivity(button.dataset.activityEdit)));
}

function editActivity(id) {
  const value = currentActivities.find(item => item.id === id); if (!value) return;
  $('activityForm').classList.remove('hidden'); $('activityId').value=value.id; $('activityName').value=value.name; $('activityArea').value=value.area; $('activityFrequency').value=value.frequency; $('activityEffort').value=value.effort; $('activityDuration').value=value.estimatedDurationMinutes || 15; $('activityPhase').value=value.executionPhase || 'BEFORE_LUNCH'; $('activityAnchor').value=value.recurrenceAnchorDate || '';
  [...$('activityAssignees').options].forEach(option => option.selected=(value.assigneeIds || []).includes(option.value));
  document.querySelectorAll('[name="activityWeekday"]').forEach(input => input.checked=(value.activeWeekdays || []).includes(input.value));
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
        body: JSON.stringify({ ...payload, password: $('userPassword').value, permissions: { canRegisterUsers: false, canCreateActivities: false, canEditUsers: false, canCreateInventoryCounts: false, canViewInventoryInsights: false, canManageAdministrativeStock: false } })
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
    const id = $('activityId').value;
    await api(id ? `/api/activities/${id}` : '/api/activities', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify({
        name: $('activityName').value,
        area: $('activityArea').value,
        frequency: $('activityFrequency').value,
        effort: Number($('activityEffort').value),
        estimatedDurationMinutes: Number($('activityDuration').value),
        executionPhase: $('activityPhase').value,
        assigneeIds: [...$('activityAssignees').selectedOptions].map(option => option.value),
        activeWeekdays: [...document.querySelectorAll('[name="activityWeekday"]:checked')].map(input => input.value),
        recurrenceAnchorDate: $('activityAnchor').value || null
      })
    });
    event.target.reset();
    $('activityId').value = '';
    event.target.classList.add('hidden');
    await load();
  } catch (error) {
    alert(error.message);
  }
});

$('showActivityForm').addEventListener('click', () => $('activityForm').classList.toggle('hidden'));
$('refreshChecklistOverview').addEventListener('click', loadChecklistOverview);
$('checklistScheduleForm').addEventListener('submit', saveChecklistSchedule);
setInterval(() => { if (token && isAdmin() && activeView() === 'dashboard') loadChecklistOverview().catch(() => {}); }, 30_000);
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
  const requiredSalesFields = new Set(requiredSalesMappingFields);
  $('salesMapping').innerHTML = salesMappingFields.map(([key, label]) => `
    <label>${label} <small>${requiredSalesFields.has(key) ? '(obrigatório)' : '(opcional)'}</small>
      <select data-sales-map="${key}">
        <option value="">Não mapear</option>
        ${data.headers.map(header => `<option value="${escapeHtml(header)}" ${data.suggestedMapping?.[key] === header ? 'selected' : ''}>${escapeHtml(header)}</option>`).join('')}
      </select>
    </label>
  `).join('');
  $('salesPreviewHead').innerHTML = `<tr>${data.headers.map(header => `<th>${escapeHtml(header)}</th>`).join('')}</tr>`;
  $('salesPreviewBody').innerHTML = data.sampleRows.map(row => `<tr>${data.headers.map(header => `<td>${escapeHtml(row[header])}</td>`).join('')}</tr>`).join('');
  const period = data.coverageFrom && data.coverageTo ? `Período: ${data.coverageFrom} até ${data.coverageTo}. ` : '';
  const summary = `Novas: ${data.newRows || 0}. Duplicadas: ${data.duplicateRows || 0} (${data.inFileDuplicateRows || 0} no arquivo, ${data.existingDuplicateRows || 0} já existentes). Rejeitadas: ${data.rejectedRows || 0}.`;
  const warnings = data.validationWarnings?.length ? ` ${data.validationWarnings.join(' · ')}` : '';
  const errors = data.errors?.length ? ` ${data.errors.map(error => error.message).join(' · ')}` : '';
  $('salesImportMessage').textContent = `${period}${summary}${warnings}${errors}`.trim();
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
  const normalizedKeys = ['saleDate', 'description', 'category', 'location', 'quantity', 'unit', 'unitPriceInCents', 'totalInCents'];
  const usedDynamicKeys = new Set();
  const dynamic = (schema.fields || [])
    .filter(field => !field.normalized)
    .filter(field => !normalizedKeys.includes(semanticColumnKey(field.key)))
    .filter(field => {
      const semantic = semanticColumnKey(field.key);
      if (usedDynamicKeys.has(semantic)) return false;
      usedDynamicKeys.add(semantic);
      return true;
    })
    .slice(0, 12);
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
      body: JSON.stringify({ fileName: file?.name || 'vendas-coladas.csv', csv: file ? await file.text() : pasted, datasetId: 'sales' })
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
  const requiredSalesFields = new Set(requiredSalesMappingFields);
  const missing = [...requiredSalesFields].filter(key => !mapping[key]);
  if (missing.length) {
    $('salesImportMessage').textContent = 'Mapeie data, produto e quantidade antes de importar.';
    return;
  }
  $('salesImportMessage').textContent = 'Importando…';
  try {
    const data = await api(`/api/sales/imports/${salesPreview.id}/commit`, {
      method: 'POST',
      body: JSON.stringify({ datasetId: 'sales', mapping, preserveColumns: salesPreview.headers })
    });
    $('salesImportMessage').innerHTML = `<span class="import-ok">${data.importedRows} linhas importadas, ${data.duplicateRows} duplicadas (${data.inFileDuplicateRows || 0} no arquivo, ${data.existingDuplicateRows || 0} já existentes) e ${data.rejectedRows} rejeitadas.</span>`;
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

const emptyCountItem = () => ({ name: '', quantity: 0, category: 'ALCOOLICO', volume: 600, volumeUnit: 'ML', salePrice: '', costPrice: '', condition: 'GELADO' });
const isAdminCountMode = () => inventoryCountMode === 'admin';
const activeCountDraft = () => isAdminCountMode() ? adminCountDraft : countDraft;
const saveCountDraft = () => {
  if (isAdminCountMode()) localStorage.setItem('inventory-admin-count-draft', JSON.stringify(adminCountDraft));
  else localStorage.setItem('inventory-count-draft', JSON.stringify(countDraft));
};
const setInventoryCountMode = (mode) => {
  inventoryCountMode = mode;
  localStorage.setItem('inventory-count-mode', mode);
  $('countModeDaily')?.classList.toggle('active', mode === 'daily');
  $('countModeAdmin')?.classList.toggle('active', mode === 'admin');
  const adminMode = mode === 'admin';
  $('countSectionEyebrow').textContent = adminMode ? 'ESTOQUE ADMINISTRATIVO' : 'ABERTURA DO BAR';
  $('countSectionTitle').textContent = adminMode ? 'Nova contagem administrativa' : 'Nova contagem';
  $('countSectionHint').textContent = adminMode
    ? 'Soma ao saldo acumulado. Após confirmar a auditoria, as vendas são abatidas.'
    : 'Monte a lista completa e envie todos os produtos de uma só vez.';
  renderCountDraft();
  updateCountActions();
};

function updateCountActions() {
  const adminMode = isAdminCountMode();
  const canSubmit = adminMode ? canManageAdministrativeStock() : canCreateInventoryCounts();
  $('addCountItem')?.classList.toggle('hidden', !canSubmit);
  $('submitCount')?.classList.toggle('hidden', !canSubmit);
  $('countModeDaily')?.classList.toggle('hidden', !canCreateInventoryCounts());
  $('countModeAdmin')?.classList.toggle('hidden', !canManageAdministrativeStock());
  if (!canCreateInventoryCounts() && canManageAdministrativeStock()) setInventoryCountMode('admin');
  if (canCreateInventoryCounts() && !canManageAdministrativeStock() && adminMode) setInventoryCountMode('daily');
}

function renderCountDraft() {
  const draft = activeCountDraft();
  $('countDraft').innerHTML = draft.map((item, index) => `
    <div class="count-row" data-count-row="${index}">
      <label>Produto<input data-count-field="name" value="${escapeHtml(item.name)}" placeholder="Ex.: Heineken 600ml"></label>
      <label>Quantidade<input data-count-field="quantity" type="number" min="0" step="0.01" value="${item.quantity}"></label>
      <label>Categoria<select data-count-field="category"><option value="ALCOOLICO" ${item.category === 'ALCOOLICO' ? 'selected' : ''}>Alcoólico</option><option value="NAO_ALCOOLICO" ${item.category === 'NAO_ALCOOLICO' ? 'selected' : ''}>Não alcoólico</option></select></label>
      <label>Volume<input data-count-field="volume" type="number" min="0.01" step="0.01" value="${item.volume}"></label>
      <label>Unidade<select data-count-field="volumeUnit"><option>ML</option><option ${item.volumeUnit === 'G' ? 'selected' : ''}>G</option></select></label>
      <label>Venda (R$)<input data-count-field="salePrice" inputmode="decimal" value="${escapeHtml(item.salePrice)}"></label>
      <label>Custo opcional<input data-count-field="costPrice" inputmode="decimal" value="${escapeHtml(item.costPrice)}"></label>
      <label>Conservação<select data-count-field="condition"><option value="GELADO" ${item.condition === 'GELADO' ? 'selected' : ''}>Gelado</option><option value="NATURAL" ${item.condition === 'NATURAL' ? 'selected' : ''}>Natural</option></select></label>
      <button type="button" class="danger" data-remove-count="${index}">Remover</button>
    </div>`).join('') || '<div class="empty-state">Adicione o primeiro produto para iniciar a contagem.</div>';
  document.querySelectorAll('[data-count-row]').forEach(row => row.querySelectorAll('[data-count-field]').forEach(input => input.addEventListener('input', () => { activeCountDraft()[Number(row.dataset.countRow)][input.dataset.countField] = input.value; saveCountDraft(); })));
  document.querySelectorAll('[data-remove-count]').forEach(button => button.addEventListener('click', () => { activeCountDraft().splice(Number(button.dataset.removeCount), 1); saveCountDraft(); renderCountDraft(); }));
}

async function loadCounts() {
  const now = new Date();
  $('countDate').value ||= new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  setInventoryCountMode(inventoryCountMode);
  updateCountActions();
  if (!canViewInventoryInsights() && !canManageAdministrativeStock()) return;
  try {
    const values = await api('/api/inventory/counts');
    $('countsBody').innerHTML = values.map(value => `<tr><td>${new Date(value.countedAt || value.submittedAt).toLocaleString('pt-BR')}</td><td><strong>${escapeHtml(value.createdByName)}</strong><small>${escapeHtml(value.createdBy)}</small></td><td>${value.items?.length || 0}</td><td>${new Date(value.submittedAt).toLocaleString('pt-BR')}</td><td>${isAdmin() ? `<button class="danger" data-delete-count="${value.id}">Apagar</button>` : 'Bloqueada'}</td></tr>`).join('');
    document.querySelectorAll('[data-delete-count]').forEach(button => button.addEventListener('click', async () => { if (!confirm('Apagar definitivamente esta contagem enviada?')) return; await api(`/api/inventory/counts/${button.dataset.deleteCount}`, { method: 'DELETE' }); await loadCounts(); }));
    await loadAdminStockBalances();
  } catch (error) { $('countMessage').textContent = error.message; }
}

async function loadAdminStockBalances() {
  if (!canManageAdministrativeStock()) {
    $('adminStockBody').innerHTML = '<tr><td colspan="4">Sem permissão para visualizar o estoque administrativo.</td></tr>';
    return;
  }
  try {
    const balances = await api('/api/inventory/admin-stock/balances');
    $('adminStockBody').innerHTML = balances.length
      ? balances.map(value => `<tr><td>${escapeHtml(value.productName)}</td><td>${escapeHtml(value.location)}</td><td>${value.quantity}</td><td>${value.updatedAt ? new Date(value.updatedAt).toLocaleString('pt-BR') : '-'}</td></tr>`).join('')
      : '<tr><td colspan="4">Nenhum saldo registrado ainda.</td></tr>';
  } catch (error) {
    $('adminStockBody').innerHTML = `<tr><td colspan="4">${escapeHtml(error.message)}</td></tr>`;
  }
}

async function submitCount() {
  const draft = activeCountDraft();
  if (!draft.length) return void ($('countMessage').textContent = 'Adicione ao menos um produto.');
  const items = draft.map(item => ({ name: item.name.trim(), quantity: Number(item.quantity), category: item.category, volume: Number(item.volume), volumeUnit: item.volumeUnit, salePriceInCents: currencyToCents(item.salePrice) || 0, costPriceInCents: item.costPrice ? currencyToCents(item.costPrice) : null, condition: item.condition }));
  const adminMode = isAdminCountMode();
  const confirmText = adminMode
    ? `Confirma o envio administrativo de ${items.length} itens? O saldo acumulado será atualizado.`
    : `Confirma o envio de ${items.length} itens? Após enviar, a contagem não poderá ser editada.`;
  if (!confirm(confirmText)) return;
  try {
    const countedAt = new Date($('countDate').value);
    const payload = { countDate: $('countDate').value.slice(0, 10), countedAt: countedAt.toISOString(), location: 'Beco da Praia', items };
    const endpoint = adminMode ? '/api/inventory/admin-stock/counts' : '/api/inventory/counts';
    await api(endpoint, { method: 'POST', body: JSON.stringify(payload) });
    if (adminMode) adminCountDraft = []; else countDraft = [];
    saveCountDraft(); renderCountDraft();
    $('countMessage').innerHTML = adminMode
      ? '<span class="import-ok">Contagem administrativa enviada e saldo atualizado.</span>'
      : '<span class="import-ok">Contagem enviada e bloqueada para edição.</span>';
    await loadCounts();
  } catch (error) { $('countMessage').textContent = error.message; }
}

async function runInventoryAudit() {
  try {
    const data = await api('/api/inventory/audit/daily', { method: 'POST', body: JSON.stringify({ date: $('countDate').value.slice(0, 10), location: 'Beco da Praia' }) });
    $('inventoryMetrics').innerHTML = `<span>Contado: <strong>${data.totalOpening}</strong></span><span>Vendido: <strong>${data.totalSold}</strong></span><span>Saldo: <strong>${data.totalRemaining}</strong></span>`;
    $('inventoryAuditBody').innerHTML = data.items.map(item => `<tr><td><strong>${escapeHtml(item.status)}</strong></td><td>${escapeHtml(item.product)}</td><td>${item.openingQuantity}</td><td>${item.soldQuantity}</td><td>${item.theoreticalRemaining}</td><td>${escapeHtml(item.notes)}</td></tr>`).join('');
  } catch (error) { $('countMessage').textContent = error.message; }
}

async function applyInventoryAudit() {
  if (!confirm('Confirmar auditoria e abater as vendas do estoque administrativo conforme a planilha?')) return;
  try {
    const data = await api('/api/inventory/audit/daily/apply', { method: 'POST', body: JSON.stringify({ date: $('countDate').value.slice(0, 10), location: 'Beco da Praia' }) });
    if (data.audit) {
      $('inventoryMetrics').innerHTML = `<span>Contado: <strong>${data.audit.totalOpening}</strong></span><span>Vendido: <strong>${data.audit.totalSold}</strong></span><span>Saldo: <strong>${data.audit.totalRemaining}</strong></span>`;
      $('inventoryAuditBody').innerHTML = data.audit.items.map(item => `<tr><td><strong>${escapeHtml(item.status)}</strong></td><td>${escapeHtml(item.product)}</td><td>${item.openingQuantity}</td><td>${item.soldQuantity}</td><td>${item.theoreticalRemaining}</td><td>${escapeHtml(item.notes)}</td></tr>`).join('');
    }
    $('countMessage').innerHTML = data.alreadyApplied
      ? '<span class="import-ok">Auditoria já havia sido aplicada ao estoque administrativo.</span>'
      : '<span class="import-ok">Vendas abatidas do estoque administrativo conforme planilha.</span>';
    await loadAdminStockBalances();
  } catch (error) { $('countMessage').textContent = error.message; }
}

const workClockTypeLabels = {
  ENTRADA: 'Entrada',
  ALMOCO_INICIO: 'Saída almoço',
  ALMOCO_FIM: 'Retorno almoço',
  DESCANSO_INICIO: 'Início descanso',
  DESCANSO_FIM: 'Fim descanso',
  SAIDA: 'Saída'
};

function initWorkClockDates() {
  const today = new Date();
  const monday = new Date(today);
  monday.setDate(today.getDate() - ((today.getDay() + 6) % 7));
  if (!$('workClockFrom').value) $('workClockFrom').value = monday.toISOString().slice(0, 10);
  if (!$('workClockTo').value) $('workClockTo').value = today.toISOString().slice(0, 10);
  if (!$('workClockExportYear').value) $('workClockExportYear').value = today.getFullYear();
  if (!$('workClockExportMonth').value) $('workClockExportMonth').value = today.getMonth() + 1;
}

function populateWorkClockUsers(users) {
  const options = users.map(user => `<option value="${escapeHtml(user.id)}">${escapeHtml(user.name)}</option>`).join('');
  $('workClockUser').innerHTML = `<option value="">Todos</option>${options}`;
  $('scheduleUser').innerHTML = options;
}

function selectedScheduleDays() {
  return [...document.querySelectorAll('.schedule-days input:checked')].map(input => Number(input.value));
}

function setScheduleDays(days) {
  document.querySelectorAll('.schedule-days input').forEach(input => {
    input.checked = days.includes(Number(input.value));
  });
}

async function loadWorkClockSchedule(userId) {
  if (!userId) return;
  const schedule = await api(`/api/work-clock/schedule/${userId}`);
  setScheduleDays(schedule.workingDaysOfWeek || [1, 2, 3, 4]);
}

async function loadWorkClockSummary() {
  initWorkClockDates();
  const from = $('workClockFrom').value;
  const to = $('workClockTo').value;
  const userId = $('workClockUser').value;
  const query = new URLSearchParams({ from, to });
  if (userId) query.set('userId', userId);
  const rows = await api(`/api/work-clock/summary?${query.toString()}`);
  $('workClockBody').innerHTML = rows.map(row => `<tr>
    <td>${escapeHtml(row.name)}</td>
    <td>${Number(row.workedHours).toFixed(2)} h</td>
    <td>${Number(row.overtimeHours).toFixed(2)} h</td>
    <td>${Number(row.missingHours).toFixed(2)} h</td>
    <td>${Number(row.breakHours).toFixed(2)} h</td>
    <td>${row.absenceDays}</td>
    <td><button type="button" class="secondary" data-workclock-user="${escapeHtml(row.userId)}" data-workclock-name="${escapeHtml(row.name)}">Ver</button></td>
  </tr>`).join('');
  document.querySelectorAll('[data-workclock-user]').forEach(button => {
    button.addEventListener('click', () => openWorkClockEntries(button.dataset.workclockUser, button.dataset.workclockName));
  });
}

async function openWorkClockEntries(userId, name) {
  const from = $('workClockFrom').value;
  const to = $('workClockTo').value;
  const query = new URLSearchParams({ userId, from, to });
  const entries = await api(`/api/work-clock/entries?${query.toString()}`);
  $('workClockEntriesTitle').textContent = `Marcações — ${name}`;
  $('workClockEntriesBody').innerHTML = entries.map(entry => `<tr>
    <td>${escapeHtml(workClockTypeLabels[entry.type] || entry.type)}</td>
    <td>${new Date(entry.registeredAt).toLocaleString('pt-BR')}</td>
    <td>${Number(entry.distanceFromWorkMeters).toFixed(1)}</td>
    <td>${entry.isLate ? 'Sim' : 'Não'}</td>
  </tr>`).join('') || '<tr><td colspan="4">Nenhuma marcação no período.</td></tr>';
  $('workClockEntriesDialog').showModal();
}

async function loadWorkClock() {
  if (!isAdmin()) return;
  try {
    $('workClockMessage').textContent = '';
    initWorkClockDates();
    if (!currentUsers.length) currentUsers = await api('/api/users');
    populateWorkClockUsers(currentUsers);
    if ($('scheduleUser').value) await loadWorkClockSchedule($('scheduleUser').value);
    await loadWorkClockSummary();
  } catch (error) {
    $('workClockMessage').textContent = error.message;
  }
}

async function saveWorkClockSchedule(event) {
  event.preventDefault();
  const userId = $('scheduleUser').value;
  if (!userId) return;
  try {
    await api(`/api/work-clock/schedule/${userId}`, {
      method: 'PUT',
      body: JSON.stringify({
        workingDaysOfWeek: selectedScheduleDays(),
        workDateExceptions: [],
        offDateExceptions: []
      })
    });
    $('scheduleMessage').innerHTML = '<span class="import-ok">Escala salva.</span>';
  } catch (error) {
    $('scheduleMessage').textContent = error.message;
  }
}

async function downloadWorkClockExport(kind) {
  const year = Number($('workClockExportYear').value);
  const month = Number($('workClockExportMonth').value);
  const path = kind === 'pdf' ? '/api/work-clock/export.pdf' : '/api/work-clock/export.csv';
  const response = await fetch(`${path}?year=${year}&month=${month}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  });
  if (!response.ok) throw new Error(readApiError({}, response.status));
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `ponto-${year}-${month}.${kind === 'pdf' ? 'pdf' : 'csv'}`;
  link.click();
  URL.revokeObjectURL(url);
}

$('addCountItem').addEventListener('click', () => { activeCountDraft().push(emptyCountItem()); saveCountDraft(); renderCountDraft(); });
$('clearCount').addEventListener('click', () => { const draft = activeCountDraft(); if (draft.length && !confirm('Descartar a contagem ainda não enviada?')) return; if (isAdminCountMode()) adminCountDraft = []; else countDraft = []; saveCountDraft(); renderCountDraft(); });
$('submitCount').addEventListener('click', submitCount);
$('refreshCounts').addEventListener('click', loadCounts);
$('refreshAdminStock').addEventListener('click', loadAdminStockBalances);
$('runInventoryAudit').addEventListener('click', runInventoryAudit);
$('applyInventoryAudit').addEventListener('click', applyInventoryAudit);
$('countModeDaily').addEventListener('click', () => setInventoryCountMode('daily'));
$('countModeAdmin').addEventListener('click', () => setInventoryCountMode('admin'));
$('refreshWorkClock').addEventListener('click', loadWorkClock);
$('workClockFilters').addEventListener('submit', event => { event.preventDefault(); loadWorkClockSummary().catch(error => { $('workClockMessage').textContent = error.message; }); });
$('workClockScheduleForm').addEventListener('submit', saveWorkClockSchedule);
$('scheduleUser').addEventListener('change', event => loadWorkClockSchedule(event.target.value).catch(error => { $('scheduleMessage').textContent = error.message; }));
$('closeWorkClockEntries').addEventListener('click', () => $('workClockEntriesDialog').close());
$('exportWorkClockCsv').addEventListener('click', () => downloadWorkClockExport('csv').catch(error => { $('workClockMessage').textContent = error.message; }));
$('exportWorkClockPdf').addEventListener('click', () => downloadWorkClockExport('pdf').catch(error => { $('workClockMessage').textContent = error.message; }));
renderCountDraft();
updateCountActions();

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
