/**
 * InfraWatch — serviço HTTP de demonstração.
 *
 * Um único binário, três comportamentos (DEMO_MODE):
 *   healthy  → responde 200 rapidamente
 *   degraded → responde 200 com latência artificial (DEMO_LATENCY_MS)
 *   flaky    → alterna entre 200 e 503 conforme estado controlado pelo painel /__control
 *
 * Nada aqui toca recursos externos. O estado é alterado apenas por chamadas explícitas ao painel de controle,
 * o que permite reproduzir deterministicamente cada cenário da demonstração.
 */
const http = require("node:http");

const name = process.env.DEMO_NAME || "demo";
const port = Number(process.env.PORT || 9100);
const mode = process.env.DEMO_MODE || "healthy";

const state = {
  name,
  mode,
  up: true,
  latencyMs: mode === "degraded" ? Number(process.env.DEMO_LATENCY_MS || 1800) : 0,
  failStatus: 503,
  // Em modo "flaky" é possível programar uma sequência determinística de resultados (ex.: "ok,ok,fail,fail").
  pattern: [],
  patternIndex: 0,
  requests: 0,
  startedAt: new Date().toISOString(),
};

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "content-length": Buffer.byteLength(payload),
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = "";
    req.on("data", (chunk) => {
      data += chunk;
      if (data.length > 10_000) req.destroy();
    });
    req.on("end", () => {
      try {
        resolve(data ? JSON.parse(data) : {});
      } catch {
        resolve({});
      }
    });
  });
}

function nextOutcome() {
  if (state.pattern.length > 0) {
    const step = state.pattern[state.patternIndex % state.pattern.length];
    state.patternIndex += 1;
    return step === "ok";
  }
  return state.up;
}

async function handleControl(req, res, url) {
  if (req.method === "GET" && url.pathname === "/__control") {
    return json(res, 200, state);
  }
  if (req.method !== "POST") return json(res, 405, { error: "method_not_allowed" });
  const body = await readBody(req);
  switch (url.pathname) {
    case "/__control/up":
      state.up = true;
      state.pattern = [];
      return json(res, 200, { ok: true, state });
    case "/__control/down":
      state.up = false;
      state.pattern = [];
      if (Number.isInteger(body.status)) state.failStatus = body.status;
      return json(res, 200, { ok: true, state });
    case "/__control/latency":
      state.latencyMs = Math.max(0, Math.min(30_000, Number(body.ms ?? 0)));
      return json(res, 200, { ok: true, state });
    case "/__control/pattern":
      state.pattern = Array.isArray(body.pattern)
        ? body.pattern.map((s) => (s === "fail" ? "fail" : "ok"))
        : String(body.pattern || "").split(",").filter(Boolean).map((s) => (s.trim() === "fail" ? "fail" : "ok"));
      state.patternIndex = 0;
      return json(res, 200, { ok: true, state });
    case "/__control/reset":
      state.up = true;
      state.pattern = [];
      state.patternIndex = 0;
      state.latencyMs = mode === "degraded" ? Number(process.env.DEMO_LATENCY_MS || 1800) : 0;
      return json(res, 200, { ok: true, state });
    default:
      return json(res, 404, { error: "not_found" });
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);

  if (url.pathname.startsWith("/__control")) {
    return handleControl(req, res, url);
  }

  if (url.pathname === "/health" || url.pathname === "/") {
    state.requests += 1;
    const ok = nextOutcome();
    const respond = () =>
      ok
        ? json(res, 200, { status: "UP", service: name, mode, uptimeSeconds: Math.round(process.uptime()) })
        : json(res, state.failStatus, { status: "DOWN", service: name, mode, reason: "falha controlada pela demonstração" });
    if (state.latencyMs > 0) setTimeout(respond, state.latencyMs);
    else respond();
    return;
  }

  return json(res, 404, { error: "not_found" });
});

server.listen(port, "0.0.0.0", () => {
  console.log(`[demo:${name}] modo=${mode} porta=${port} latência=${state.latencyMs}ms`);
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
