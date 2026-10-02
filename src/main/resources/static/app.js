'use strict';
let token = '';
let pendingCreation = null;
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
  const list = byId('requests');
  list.replaceChildren();
  if (!items.length) list.append(node('p', 'La bandeja está vacía.', 'empty'));
  for (const { request: r, overdue } of items) {
    const card = node('article', '', 'request');
    card.append(node('h3', r.title), node('span', `${r.status} · ${r.priority}${overdue ? ' · SLA vencido' : ''}`, 'badge'), node('p', r.description), node('p', `Vence: ${new Date(r.slaDueAt).toLocaleString()} · Versión ${r.version}`, 'meta'));
    const actions = node('div', '', 'actions');
    for (const status of nextStatuses[r.status]) {
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
      try { const events = await api(`/api/requests/${r.id}/history`); details.textContent = events.map(e => `${new Date(e.occurredAt).toLocaleString()} · ${e.action} · ${e.actor}`).join('\n'); details.hidden = !details.hidden; }
      catch (error) { message(error.message, true); }
    });
    actions.append(history); card.append(actions, details); list.append(card);
  }
}
byId('connect').addEventListener('submit', async event => {
  event.preventDefault(); token = byId('token').value.trim(); byId('token').value = ''; pendingCreation = null;
  try { await refresh(); message('Conexión activa.'); } catch (error) { token = ''; byId('requests').replaceChildren(); message(error.message, true); }
});
byId('disconnect').addEventListener('click', () => { token = ''; pendingCreation = null; byId('requests').replaceChildren(); message('Sesión desconectada.'); });
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
