/**
 * What happens when N people use the app at the same moment.
 *
 * Different question from scale.mjs, and a different failure mode. scale.mjs asks "is one request
 * over a big company fast enough"; this asks "what happens when a hundred of them arrive together".
 * A system can pass the first and fall over on the second, because the limit is no longer the query
 * plan — it is the connection pool, the thread pool and the memory ceiling of one small instance.
 *
 * Models a real session rather than hammering one endpoint: each simulated person loads the shell
 * (which asks who they lead on EVERY navigation), their dashboard, their own attendance and their
 * team. Hammering a single URL measures that URL; it does not measure the product.
 *
 *   node tools/qa/load.mjs                 # 25 concurrent, 4 rounds
 *   node tools/qa/load.mjs --users 100     # the number the founder asked about
 */
const API = process.env.ORBIT_API ?? "https://calyvora-backend.onrender.com";
const PW = "demopass123";

const arg = (n, d) => { const i = process.argv.indexOf(n); return i < 0 ? d : process.argv[i + 1]; };
const USERS = Number(arg("--users", 25));
const ROUNDS = Number(arg("--rounds", 4));

async function call(path, token) {
  const t0 = performance.now();
  try {
    const res = await fetch(API + path, {
      headers: token ? { authorization: `Bearer ${token}` } : {},
      signal: AbortSignal.timeout(120000),
    });
    return { ms: performance.now() - t0, status: res.status, ok: res.ok };
  } catch (e) {
    return { ms: performance.now() - t0, status: 0, ok: false, err: e.name };
  }
}

async function login(email) {
  const res = await fetch(API + "/api/v1/auth/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password: PW }),
    signal: AbortSignal.timeout(120000),
  });
  const j = await res.json().catch(() => null);
  return j?.accessToken ?? null;
}

console.log(`waking backend…`);
await call("/actuator/health");

// Real accounts from the scale tenant, so every simulated person has their own session and their own
// downline. One shared token would exercise a single JWT and one cache line, not a hundred sessions.
const emails = [];
for (let i = 0; i < USERS; i++) {
  if (i === 0) emails.push("admin@scaleworks.demo");
  else if (i <= 8) emails.push(`head${i - 1}@scaleworks.demo`);
  else if (i <= 48) emails.push(`lead${i - 9}@scaleworks.demo`);
  else emails.push(`emp${i - 49}@scaleworks.demo`);
}

console.log(`signing in ${USERS} people…`);
const tokens = (await Promise.all(emails.map(login))).filter(Boolean);
console.log(`  ${tokens.length}/${USERS} signed in`);
if (tokens.length === 0) {
  console.error("nobody signed in — is the scale tenant seeded? node tools/qa/scale.mjs --seed 1000");
  process.exit(1);
}

/** One person's page load: the shell asks who they lead, then the screen itself. */
const journey = (tok) => [
  call("/api/v1/team/mine", tok),      // every navigation pays for this
  call("/api/v1/dashboard/summary", tok),
  call("/api/v1/people/attendance/me", tok),
  call("/api/v1/team", tok),
];

const all = [];
for (let round = 1; round <= ROUNDS; round++) {
  const t0 = performance.now();
  const results = (await Promise.all(tokens.flatMap(journey))).flat();
  const wall = Math.round(performance.now() - t0);
  const ok = results.filter((r) => r.ok).length;
  const failed = results.filter((r) => !r.ok);
  const times = results.map((r) => r.ms).sort((a, b) => a - b);
  const pct = (p) => Math.round(times[Math.floor(times.length * p)] ?? 0);
  console.log(`round ${round}: ${results.length} requests in ${wall} ms · ok ${ok}/${results.length}`
    + ` · p50 ${pct(0.5)} ms · p95 ${pct(0.95)} ms · max ${Math.round(times[times.length - 1])} ms`
    + (failed.length ? `  <== ${failed.length} FAILED (${[...new Set(failed.map((f) => f.err ?? f.status))].join(",")})` : ""));
  all.push(...results);
}

const times = all.map((r) => r.ms).sort((a, b) => a - b);
const pct = (p) => Math.round(times[Math.floor(times.length * p)] ?? 0);
const failed = all.filter((r) => !r.ok);
console.log(`\n=== ${USERS} concurrent users, ${all.length} requests ===`);
console.log(`  p50 ${pct(0.5)} ms   p95 ${pct(0.95)} ms   p99 ${pct(0.99)} ms   max ${Math.round(times[times.length - 1])} ms`);
console.log(`  failures: ${failed.length}` + (failed.length
  ? ` — ${[...new Set(failed.map((f) => f.err ?? `HTTP ${f.status}`))].join(", ")}`
  : " (none)"));
// p95 is the honest number for "how it feels": the median hides the tail, and the tail is what a
// room full of people actually notice.
if (pct(0.95) > 3000) console.log(`  VERDICT: p95 over 3s — this many concurrent users is too many for the current instance.`);
else if (pct(0.95) > 1000) console.log(`  VERDICT: p95 over 1s — usable, but the instance is working hard.`);
else console.log(`  VERDICT: comfortable at this concurrency.`);
