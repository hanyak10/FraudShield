/**
 * FraudShield UI — Eureka registrar (sidecar)
 * ------------------------------------------------------------
 * A browser SPA cannot register itself with Eureka (Eureka clients are JVM
 * apps that send heartbeats). This tiny Node script does it on the UI's behalf:
 * it registers "fraudshield-ui" with the Eureka server and sends a heartbeat
 * every 30s, so the UI shows up in the Eureka dashboard alongside the backend.
 *
 * It runs automatically with `npm run dev` (see package.json). On Ctrl+C it
 * deregisters cleanly. If Eureka is not reachable it logs a warning and keeps
 * retrying — it never blocks the Vite dev server.
 *
 * Uses only Node's built-in http module — no extra dependencies.
 */

const http = require('http');

const EUREKA_HOST = process.env.EUREKA_HOST || 'localhost';
const EUREKA_PORT = Number(process.env.EUREKA_PORT || 8761);
const APP_NAME = 'FRAUDSHIELD-UI';
const UI_PORT = Number(process.env.UI_PORT || 5173);
const HOSTNAME = process.env.UI_HOST || 'localhost';
const INSTANCE_ID = `${HOSTNAME}:fraudshield-ui:${UI_PORT}`;
const HEARTBEAT_MS = 30000;

const instance = {
  instance: {
    instanceId: INSTANCE_ID,
    hostName: HOSTNAME,
    app: APP_NAME,
    ipAddr: '127.0.0.1',
    status: 'UP',
    port: { $: UI_PORT, '@enabled': true },
    securePort: { $: 443, '@enabled': false },
    homePageUrl: `http://${HOSTNAME}:${UI_PORT}/`,
    statusPageUrl: `http://${HOSTNAME}:${UI_PORT}/`,
    healthCheckUrl: `http://${HOSTNAME}:${UI_PORT}/`,
    vipAddress: 'fraudshield-ui',
    secureVipAddress: 'fraudshield-ui',
    dataCenterInfo: {
      '@class': 'com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo',
      name: 'MyOwn',
    },
  },
};

function eurekaRequest(method, path, body) {
  return new Promise((resolve, reject) => {
    const payload = body ? JSON.stringify(body) : null;
    const req = http.request(
      {
        host: EUREKA_HOST,
        port: EUREKA_PORT,
        path,
        method,
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          ...(payload ? { 'Content-Length': Buffer.byteLength(payload) } : {}),
        },
        timeout: 4000,
      },
      (res) => {
        res.resume(); // drain
        resolve(res.statusCode);
      }
    );
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('error', reject);
    if (payload) req.write(payload);
    req.end();
  });
}

let registered = false;

async function register() {
  try {
    const code = await eurekaRequest('POST', `/eureka/apps/${APP_NAME}`, instance);
    if (code === 204 || code === 200) {
      if (!registered) console.log(`[eureka] registered ${APP_NAME} (${INSTANCE_ID}) → http://${EUREKA_HOST}:${EUREKA_PORT}`);
      registered = true;
    } else {
      console.warn(`[eureka] register returned HTTP ${code} — will retry`);
    }
  } catch (e) {
    console.warn(`[eureka] not reachable at ${EUREKA_HOST}:${EUREKA_PORT} (${e.message}) — will retry`);
    registered = false;
  }
}

async function heartbeat() {
  if (!registered) {
    await register();
    return;
  }
  try {
    const code = await eurekaRequest('PUT', `/eureka/apps/${APP_NAME}/${INSTANCE_ID}`);
    if (code === 404) {
      // Eureka forgot us (restart/eviction) — re-register.
      registered = false;
      await register();
    }
  } catch (e) {
    registered = false;
  }
}

async function deregister() {
  try {
    await eurekaRequest('DELETE', `/eureka/apps/${APP_NAME}/${INSTANCE_ID}`);
    console.log(`[eureka] deregistered ${APP_NAME}`);
  } catch {
    /* ignore on shutdown */
  }
}

// Initial registration + heartbeat loop.
register();
const timer = setInterval(heartbeat, HEARTBEAT_MS);

let stopped = false;
function stop() {
  if (stopped) return Promise.resolve();
  stopped = true;
  clearInterval(timer);
  return deregister();
}

// When run standalone (node register-eureka.cjs), handle signals + exit.
if (require.main === module) {
  const shutdown = () => stop().finally(() => process.exit(0));
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
} else {
  // When required by the dev launcher, deregister on process exit.
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);
}

module.exports = { stop };
