import http from 'node:http';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8080';
const VOUCHER = process.env.VOUCHER ?? '1';
const TOTAL = Number(process.argv[2] ?? 2000);
const CONCURRENCY = Number(process.argv[3] ?? 200);
const STOCK = Number(process.argv[4] ?? 100);

const base = new URL(BASE);
const agent = new http.Agent({ keepAlive: true, maxSockets: CONCURRENCY });

function seckill(userId) {
  return new Promise((resolve) => {
    const req = http.request({
      host: base.hostname,
      port: base.port || 80,
      path: `/api/seckill/${VOUCHER}`,
      method: 'POST',
      headers: { 'X-User-Id': String(userId), 'Content-Length': 0 },
      agent,
    }, (res) => {
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => resolve(body));
    });
    req.on('error', () => resolve('{"code":"ERR"}'));
    req.end();
  });
}

async function waitForOrders() {
  for (let i = 0; i < 30; i++) {
    const res = await fetch(`${BASE}/api/admin/stats/${VOUCHER}`);
    const data = (await res.json()).data;
    if (data.dbOrderCount === STOCK) return data;
    await new Promise((r) => setTimeout(r, 1000));
  }
  const res = await fetch(`${BASE}/api/admin/stats/${VOUCHER}`);
  return (await res.json()).data;
}

const reset = await fetch(`${BASE}/api/admin/reset/${VOUCHER}?stock=${STOCK}`, { method: 'POST' });
console.log('reset:', (await reset.json()).data);

let next = 1;
const codes = new Map();
const latencies = [];
const started = performance.now();

async function worker() {
  while (true) {
    const userId = next++;
    if (userId > TOTAL) return;
    const t0 = performance.now();
    const body = await seckill(userId);
    latencies.push(performance.now() - t0);
    let code = 'ERR';
    try { code = JSON.parse(body).code; } catch (e) { /* keep ERR */ }
    codes.set(code, (codes.get(code) ?? 0) + 1);
  }
}

await Promise.all(Array.from({ length: CONCURRENCY }, worker));
const elapsed = performance.now() - started;
latencies.sort((a, b) => a - b);
const pct = (q) => latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * q))];
const avg = latencies.reduce((a, b) => a + b, 0) / latencies.length;

console.log(`total=${TOTAL} concurrency=${CONCURRENCY} elapsed=${elapsed.toFixed(0)}ms rps=${(TOTAL / elapsed * 1000).toFixed(0)}`);
console.log(`latency avg=${avg.toFixed(1)}ms p50=${pct(0.5).toFixed(1)}ms p95=${pct(0.95).toFixed(1)}ms p99=${pct(0.99).toFixed(1)}ms max=${latencies[latencies.length - 1].toFixed(1)}ms`);
console.log('biz code:', Object.fromEntries([...codes.entries()].sort()));

console.log('stats:', await waitForOrders());
agent.destroy();
