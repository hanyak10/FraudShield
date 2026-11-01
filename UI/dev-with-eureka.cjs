/**
 * FraudShield UI — dev launcher.
 * ------------------------------------------------------------
 * Starts the Vite dev server AND the Eureka registrar together, using only
 * Node built-ins (no 'concurrently' dependency).
 *
 *   npm run dev   ->   node dev-with-eureka.cjs
 *
 * - The Eureka registrar runs IN THIS process (require) — it registers
 *   "fraudshield-ui" with the Eureka server and heartbeats every 30s.
 * - Vite is spawned as a child and streams its output here.
 * - Ctrl+C stops both cleanly (the registrar deregisters on exit).
 *
 * Run the UI WITHOUT Eureka:  npm run dev:no-eureka
 */

const { spawn } = require('child_process');
const path = require('path');

console.log('Starting Vite + Eureka registrar...  (Ctrl+C to stop)');

// 1) Eureka registrar in-process (no child node, avoids Windows path issues).
let stopEureka = () => {};
try {
  const reg = require('./register-eureka.cjs');
  if (reg && typeof reg.stop === 'function') stopEureka = reg.stop;
} catch (e) {
  console.warn('[eureka] registrar failed to start:', e.message);
}

// 2) Vite as a child process. On Windows the local binary is vite.cmd, which
//    Node 20+ refuses to spawn without a shell. Use shell:true and quote the
//    path so spaces (e.g. "Program Files") are handled correctly.
const isWin = process.platform === 'win32';
const viteBin = path.join(__dirname, 'node_modules', '.bin', isWin ? 'vite.cmd' : 'vite');

const vite = spawn(`"${viteBin}"`, { cwd: __dirname, stdio: 'inherit', shell: true });

vite.on('error', (err) => {
  console.error('[vite] failed to start:', err.message);
});
vite.on('exit', (code) => {
  console.log(`[vite] exited with code ${code}`);
  shutdown();
});

function shutdown() {
  try { stopEureka(); } catch {}
  try { vite.kill(); } catch {}
  setTimeout(() => process.exit(0), 400);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
