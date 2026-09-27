const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Exercise the production request helper without starting the storefront or a server.
const source = fs.readFileSync(path.join(__dirname, '../storefront.js'), 'utf8');
const helper = source.slice(source.indexOf('async function api('), source.indexOf('\nfunction art('));
function fixture(status = 200) {
  const requests = [], events = [];
  const context = vm.createContext({
    adminSession: 'test-session', Event,
    window: { dispatchEvent: event => events.push(event.type) },
    fetch: async (url, options) => {
      requests.push({ url, options });
      return { ok: status === 200, status, json: async () => ({ message: 'test response' }) };
    }
  });
  vm.runInContext(helper, context);
  return { api: context.api, requests, events };
}

test('GET and HEAD never send a request body', async () => {
  const { api, requests } = fixture();
  await api('/api/website');
  await api('/api/website', { method: 'GET', body: { ignored: true } });
  await api('/api/website', { method: 'HEAD', body: { ignored: true } });
  for (const { options } of requests) {
    assert.equal(Object.hasOwn(options, 'body'), false);
    assert.equal(options.headers['Content-Type'], undefined);
  }
});

test('admin writes preserve JSON, authentication, and cancellation', async () => {
  const { api, requests } = fixture();
  const signal = new AbortController().signal;
  await api('/api/admin/config', { method: 'POST', body: { useTimeInflation: true }, auth: true, signal });
  const { options } = requests[0];
  assert.equal(options.body, '{"useTimeInflation":true}');
  assert.equal(options.headers['Content-Type'], 'application/json');
  assert.equal(options.headers['X-Session-Token'], 'test-session');
  assert.equal(options.signal, signal);
});

test('an unauthorized admin response expires the session and rejects the write', async () => {
  const { api, events } = fixture(401);
  await assert.rejects(api('/api/admin/config', { auth: true }), error => error.status === 401);
  assert.deepEqual(events, ['admin-expired']);
});
