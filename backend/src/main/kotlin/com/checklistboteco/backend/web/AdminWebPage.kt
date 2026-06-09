package com.checklistboteco.backend.web

fun adminWebPage(): String {
    return """
        <!doctype html>
        <html lang="pt-BR">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>Checklist Boteco Admin</title>
          <style>
            :root { color-scheme: light; --bg:#f7f8fa; --ink:#1f2937; --muted:#667085; --brand:#0f766e; --line:#dde3ea; --card:#fff; }
            * { box-sizing: border-box; }
            body { margin:0; font-family: Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background:var(--bg); color:var(--ink); }
            header { display:flex; align-items:center; justify-content:space-between; padding:16px 24px; border-bottom:1px solid var(--line); background:var(--card); position:sticky; top:0; z-index:2; }
            h1 { margin:0; font-size:20px; }
            main { max-width:1180px; margin:0 auto; padding:24px; display:grid; gap:18px; }
            section { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:18px; }
            h2 { margin:0 0 14px; font-size:18px; }
            .grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(220px, 1fr)); gap:12px; }
            .metric { border:1px solid var(--line); border-radius:8px; padding:14px; }
            .metric strong { display:block; font-size:28px; color:var(--brand); }
            label { display:block; font-size:13px; color:var(--muted); margin-bottom:6px; }
            input, select { width:100%; padding:10px 12px; border:1px solid var(--line); border-radius:6px; background:#fff; }
            button { border:0; background:var(--brand); color:#fff; padding:10px 14px; border-radius:6px; cursor:pointer; font-weight:600; }
            button.secondary { background:#475467; }
            table { width:100%; border-collapse:collapse; }
            th, td { text-align:left; border-bottom:1px solid var(--line); padding:10px; font-size:14px; }
            th { color:var(--muted); font-weight:600; }
            .row { display:flex; gap:10px; align-items:end; flex-wrap:wrap; }
            .row > div { flex:1 1 180px; }
            .hidden { display:none; }
            .error { color:#b42318; margin-top:8px; }
            @media (max-width: 700px) { header, main { padding:16px; } table { display:block; overflow-x:auto; white-space:nowrap; } }
          </style>
        </head>
        <body>
          <header>
            <h1>Checklist Boteco Admin</h1>
            <button class="secondary" onclick="logout()">Sair</button>
          </header>
          <main>
            <section id="login">
              <h2>Entrar</h2>
              <div class="row">
                <div><label>Email</label><input id="email" value="admin@checklistboteco.com" /></div>
                <div><label>Senha</label><input id="password" type="password" value="admin123" /></div>
                <button onclick="login()">Entrar</button>
              </div>
              <div id="twoFactor" class="row hidden" style="margin-top:12px">
                <div><label>Código do dispositivo</label><input id="twoFactorCode" /></div>
                <button onclick="verifyDevice()">Confirmar dispositivo</button>
              </div>
              <div id="loginError" class="error"></div>
            </section>
            <div id="admin" class="hidden">
              <section>
                <h2>Dashboard</h2>
                <div class="grid">
                  <div class="metric"><span>Usuários</span><strong id="totalUsers">0</strong></div>
                  <div class="metric"><span>Atividades</span><strong id="totalActivities">0</strong></div>
                  <div class="metric"><span>Conclusões</span><strong id="totalCompletions">0</strong></div>
                  <div class="metric"><span>Sync pendente</span><strong id="pendingSync">0</strong></div>
                </div>
              </section>
              <section>
                <h2>Usuários e permissões</h2>
                <table>
                  <thead><tr><th>Nome</th><th>Email</th><th>Perfil</th><th>Cadastro</th><th>Atividades</th><th>Usuários</th></tr></thead>
                  <tbody id="users"></tbody>
                </table>
              </section>
              <section>
                <h2>Criar atividade</h2>
                <div class="row">
                  <div><label>Nome</label><input id="activityName" /></div>
                  <div><label>Área</label><select id="activityArea"><option>ATENDIMENTO</option><option>COZINHA</option><option>ESTOQUE</option><option>LIMPEZA</option></select></div>
                  <div><label>Frequência</label><select id="activityFrequency"><option>DIARIO</option><option>QUINZENAL</option><option>MENSAL</option></select></div>
                  <div><label>Esforço</label><input id="activityEffort" type="number" min="1" max="5" value="1" /></div>
                  <button onclick="createActivity()">Criar</button>
                </div>
              </section>
              <section>
                <h2>Atividades</h2>
                <table>
                  <thead><tr><th>Nome</th><th>Área</th><th>Frequência</th><th>Esforço</th></tr></thead>
                  <tbody id="activities"></tbody>
                </table>
              </section>
            </div>
          </main>
          <script>
            let token = localStorage.getItem('token') || '';
            let challengeId = '';
            let deviceId = localStorage.getItem('deviceId') || crypto.randomUUID();
            localStorage.setItem('deviceId', deviceId);
            const el = (id) => document.getElementById(id);
            const headers = () => ({ 'Content-Type':'application/json', 'Authorization':'Bearer ' + token });
            async function api(path, options = {}) {
              const res = await fetch(path, { ...options, headers: { ...headers(), ...(options.headers || {}) } });
              if (!res.ok) throw new Error((await res.json()).message || 'Erro');
              return res.json();
            }
            async function login() {
              try {
                const response = await fetch('/api/auth/login', {
                  method:'POST',
                  headers:{'Content-Type':'application/json'},
                  body:JSON.stringify({ email:el('email').value, password:el('password').value, deviceId, deviceName:navigator.userAgent })
                });
                if (!response.ok) throw new Error((await response.json()).message || 'Falha no login');
                const data = await response.json();
                if (data.requiresTwoFactor) {
                  challengeId = data.challengeId;
                  el('twoFactor').classList.remove('hidden');
                  el('loginError').textContent = data.developmentCode ? 'Código de desenvolvimento: ' + data.developmentCode : data.deliveryHint;
                  return;
                }
                token = data.token; localStorage.setItem('token', token);
                el('loginError').textContent = '';
                await load();
              } catch (error) { el('loginError').textContent = error.message; }
            }
            async function verifyDevice() {
              try {
                const response = await fetch('/api/auth/verify-device', {
                  method:'POST',
                  headers:{'Content-Type':'application/json'},
                  body:JSON.stringify({ challengeId, code:el('twoFactorCode').value, deviceId, deviceName:navigator.userAgent })
                });
                if (!response.ok) throw new Error((await response.json()).message || 'Código inválido');
                const data = await response.json();
                token = data.token; localStorage.setItem('token', token);
                el('twoFactor').classList.add('hidden');
                el('loginError').textContent = '';
                await load();
              } catch (error) { el('loginError').textContent = error.message; }
            }
            function logout() {
              token='';
              localStorage.removeItem('token');
              el('admin').classList.add('hidden');
              el('login').classList.remove('hidden');
            }
            async function load() {
              if (!token) return;
              const [dashboard, userList, activityList] = await Promise.all([
                api('/api/admin/dashboard'),
                api('/api/users'),
                api('/api/activities')
              ]);
              el('login').classList.add('hidden');
              el('admin').classList.remove('hidden');
              el('totalUsers').textContent = dashboard.totalUsers;
              el('totalActivities').textContent = dashboard.totalActivities;
              el('totalCompletions').textContent = dashboard.totalCompletions;
              el('pendingSync').textContent = dashboard.pendingSyncItems;
              el('users').innerHTML = userList.map(user => `
                <tr>
                  <td>${'$'}{user.name}</td><td>${'$'}{user.email}</td><td>${'$'}{user.permissionLevel}</td>
                  ${'$'}{permissionCell(user, 'canRegisterUsers')}
                  ${'$'}{permissionCell(user, 'canCreateActivities')}
                  ${'$'}{permissionCell(user, 'canEditUsers')}
                </tr>`).join('');
              el('activities').innerHTML = activityList.map(activity => `<tr><td>${'$'}{activity.name}</td><td>${'$'}{activity.area}</td><td>${'$'}{activity.frequency}</td><td>${'$'}{activity.effort}</td></tr>`).join('');
            }
            function permissionCell(user, key) {
              const checked = user.permissions && user.permissions[key] ? 'checked' : '';
              const disabled = user.permissionLevel === 'ADMIN' ? 'disabled' : '';
              return `<td><input type="checkbox" ${'$'}{checked} ${'$'}{disabled} onchange="togglePermission('${'$'}{user.id}', '${'$'}{key}', this.checked)" /></td>`;
            }
            async function togglePermission(userId, key, value) {
              const user = (await api('/api/users')).find(item => item.id === userId);
              const permissions = { ...user.permissions, [key]: value };
              await api('/api/users/' + userId + '/permissions', { method:'PATCH', body:JSON.stringify({ permissions }) });
              await load();
            }
            async function createActivity() {
              await api('/api/activities', {
                method:'POST',
                body:JSON.stringify({ name:el('activityName').value, area:el('activityArea').value, frequency:el('activityFrequency').value, effort:Number(el('activityEffort').value) })
              });
              el('activityName').value = '';
              await load();
            }
            load().catch(() => logout());
          </script>
        </body>
        </html>
    """.trimIndent()
}
