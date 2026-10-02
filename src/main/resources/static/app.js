'use strict';
let token = '';
let pendingCreation = null;
let identity = null;
let authConfig = null;
const byId = id => document.getElementById(id);
function message(text, error = false) {
  byId('message').textContent = text;
  byId('message').classList.toggle('error', error);
}
async function api(path, options = {}) {
  if (!token) throw new Error('Conecta una credencial primero.');
  const response = await fetch(path, { ...options, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...options.headers } });
  const data = await response.json();
  if (!response.ok) throw new Error(`${response.status}: ${data.detail || data.title || 'Error de solicitud'}`);
  return data;
}
function node(tag, text, className = '') {
  const element = document.createElement(tag);
  element.textContent = text;
  element.className = className;
  return element;
}
const nextStatuses = { OPEN: ['IN_PROGRESS', 'CANCELLED'], IN_PROGRESS: ['RESOLVED', 'CANCELLED'], RESOLVED: ['IN_PROGRESS', 'CLOSED'], CLOSED: [], CANCELLED: [] };
async function refresh() {
  const items = await api('/api/requests?limit=50');
  const operator = identity?.roles.includes('OPERATOR');
  const operators = operator ? await api('/api/operators') : [];
  byId('alerts-panel').hidden = !operator;
  if (operator) {
    const alerts = await api('/api/alerts');
    byId('alerts').replaceChildren();
    if (!alerts.length) byId('alerts').append(node('p', 'Sin alertas activas.', 'muted'));
    for (const alert of alerts) byId('alerts').append(node('p', `${alert.title} · ${alert.priority} · Venció ${new Date(alert.dueAt).toLocaleString()}`, 'meta'));
  }
  const list = byId('requests');
  list.replaceChildren();
  if (!items.length) list.append(node('p', 'La bandeja está vacía.', 'empty'));
  for (const { request: r, overdue } of items) {
    const card = node('article', '', 'request');
    const assignee = operators.find(item => item.subject === r.assignedTo);
    card.append(node('h3', r.title), node('span', `${r.status} · ${r.priority}${overdue ? ' · SLA vencido' : ''}`, 'badge'), node('p', r.description), node('p', `Vence: ${new Date(r.slaDueAt).toLocaleString()} · Versión ${r.version}`, 'meta'), node('p', `Asignado: ${assignee?.displayName || r.assignedTo || 'Sin asignar'}`, 'meta'));
    const actions = node('div', '', 'actions');
    for (const status of operator ? nextStatuses[r.status] : []) {
      const button = node('button', status, 'secondary');
      button.addEventListener('click', async () => {
        button.disabled = true;
        try { await api(`/api/requests/${r.id}/transitions`, { method: 'POST', headers: { 'If-Match': `"${r.version}"` }, body: JSON.stringify({ status }) }); await refresh(); message('Estado actualizado; evento registrado en el historial.'); }
        catch (error) { message(error.message, true); } finally { button.disabled = false; }
      });
      actions.append(button);
    }
    const history = node('button', 'Ver historial', 'secondary');
    const details = node('pre', '');
    details.hidden = true;
    history.addEventListener('click', async () => {
      try { const events = await api(`/api/requests/${r.id}/history`); details.textContent = events.map(e => `${new Date(e.occurredAt).toLocaleString()} · ${e.action} · ${e.actor}${e.detail ? ' · ' + e.detail : ''}`).join('\n'); details.hidden = !details.hidden; }
      catch (error) { message(error.message, true); }
    });
    if (operator && !['CLOSED', 'CANCELLED'].includes(r.status)) {
      const select = node('select', '');
      select.setAttribute('aria-label', `Asignar operador para ${r.title}`);
      for (const item of operators) { const option = node('option', item.displayName); option.value = item.subject; option.selected = item.subject === r.assignedTo; select.append(option); }
      const assign = node('button', 'Asignar', 'secondary');
      assign.addEventListener('click', async () => {
        try { await api(`/api/requests/${r.id}/assignment`, { method: 'POST', headers: { 'If-Match': `"${r.version}"` }, body: JSON.stringify({ operatorSubject: select.value }) }); await refresh(); message('Operador asignado; auditoría registrada.'); }
        catch (error) { message(error.message, true); }
      });
      actions.append(select, assign);
    }
    actions.append(history); card.append(actions, details); list.append(card);
  }
}
byId('connect').addEventListener('submit', async event => {
  event.preventDefault(); token = byId('token').value.trim(); byId('token').value = ''; pendingCreation = null;
  try { await connected(); } catch (error) { disconnect(); message(error.message, true); }
});
function disconnect() { token = ''; pendingCreation = null; identity = null; byId('identity').textContent = ''; byId('requests').replaceChildren(); byId('alerts').replaceChildren(); byId('alerts-panel').hidden = true; message('Sesión desconectada.'); }
byId('disconnect').addEventListener('click', disconnect);
byId('refresh').addEventListener('click', () => refresh().then(() => message('Bandeja actualizada.')).catch(error => message(error.message, true)));
byId('template').addEventListener('change', () => { const selected = !!byId('template').value; byId('priority').disabled = selected; byId('description').required = !selected; });
byId('create').addEventListener('submit', async event => {
  event.preventDefault();
  const template = byId('template').value;
  const path = template ? `/api/templates/${template}/requests` : '/api/requests';
  const data = { title: byId('title').value, description: byId('description').value || null };
  if (!template) data.priority = byId('priority').value;
  const body = JSON.stringify(data);
  if (!pendingCreation || pendingCreation.body !== body || pendingCreation.path !== path) pendingCreation = { path, body, key: crypto.randomUUID() };
  byId('submit').disabled = true;
  try {
    await api(path, { method: 'POST', headers: { 'Idempotency-Key': pendingCreation.key }, body });
    pendingCreation = null; byId('create').reset(); byId('priority').disabled = false; byId('description').required = true;
    await refresh(); message('Solicitud registrada.');
  } catch (error) { message(error.message, true); } finally { byId('submit').disabled = false; }
});

async function connected() {
  identity = await api('/api/me');
  byId('identity').textContent = `${identity.displayName} · ${identity.roles.join(', ')}`;
  await refresh(); message('Conexión activa.');
}
function base64url(bytes) { return btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', ''); }
byId('login').addEventListener('click', async () => {
  try {
    if (authConfig?.mode !== 'oidc') throw new Error('Este entorno utiliza credenciales explícitas de desarrollo.');
    const verifier = base64url(crypto.getRandomValues(new Uint8Array(32)));
    const state = crypto.randomUUID();
    sessionStorage.setItem('oidc-pkce', JSON.stringify({ verifier, state }));
    const challenge = base64url(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))));
    const url = new URL(`${authConfig.issuer}/protocol/openid-connect/auth`);
    url.search = new URLSearchParams({ client_id: authConfig.clientId, response_type: 'code', scope: 'openid profile', redirect_uri: authConfig.redirectUri, state, code_challenge: challenge, code_challenge_method: 'S256' });
    location.assign(url.toString());
  } catch (error) { message(error.message, true); }
});
async function initializeIdentity() {
  try {
    authConfig = await (await fetch('/api/auth/config')).json();
    byId('login').hidden = authConfig.mode !== 'oidc';
    const query = new URLSearchParams(location.search);
    if (query.has('error')) throw new Error('El proveedor de identidad rechazó el ingreso.');
    if (!query.has('code')) return;
    const stored = JSON.parse(sessionStorage.getItem('oidc-pkce') || 'null');
    sessionStorage.removeItem('oidc-pkce');
    history.replaceState({}, '', '/');
    if (!stored || stored.state !== query.get('state')) throw new Error('Estado de autenticación inválido; vuelve a ingresar.');
    const response = await fetch(`${authConfig.issuer}/protocol/openid-connect/token`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'authorization_code', client_id: authConfig.clientId, redirect_uri: authConfig.redirectUri, code: query.get('code'), code_verifier: stored.verifier }) });
    const result = await response.json();
    if (!response.ok || !result.access_token) throw new Error('No se pudo completar la autenticación.');
    token = result.access_token;
    await connected();
  } catch (error) { sessionStorage.removeItem('oidc-pkce'); message(error.message, true); }
}
initializeIdentity();
