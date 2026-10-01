/**
 * Times the screens against a company big enough to hurt.
 *
 * Everything else in this directory answers "does it work". This answers "does it stay usable", which
 * is the question a 200-seat sale turns on and the one a seven-person demo cannot answer at all.
 *
 * The number to watch is `GET /team/mine`. The app shell requests it on EVERY page load, and it walks
 * the whole reporting tree — so whatever it costs is added to every navigation in the product.
 *
 *   node tools/qa/scale.mjs                  # measure (seeds 1000 if not already there)
 *   node tools/qa/scale.mjs --seed 200       # a company the size of the actual deal
 *   node tools/qa/scale.mjs --remove         # delete the scale tenant
 */
const API = process.env.ORBIT_API ?? "https://calyvora-backend.onrender.com";
const PW = "demopass123";

const arg = (name, fallback) => {
  const i = process.argv.indexOf(name);
  return i < 0 ? fallback : (process.argv[i + 1] ?? true);
};

async function req(method, path, { token, body } = {}) {
  const started = performance.now();
  const res = await fetch(API + path, {
    method,
    headers: { ...(body ? { "content-type": "application/json" } : {}), ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(180000),
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch {}
  return { ms: Math.round(performance.now() - started), status: res.status, json, text };
}

const login = async (email) => {
  const r = await req("POST", "/api/v1/auth/login", { body: { email, password: PW } });
  if (!r.json?.accessToken) throw new Error(`login ${email} -> ${r.status} ${r.text.slice(0, 120)}`);
  return r.json.accessToken;
};

if (arg("--remove", false)) {
  const r = await req("DELETE", "/api/v1/dev/seed-scale");
  console.log(`removed: ${JSON.stringify(r.json)} (${r.ms} ms)`);
  process.exit(0);
}

const size = Number(arg("--seed", 1000));
console.log(`waking backend…`);
await req("GET", "/actuator/health");

console.log(`seeding ${size} people (this is one big write — it can take a while)…`);
const seeded = await req("POST", `/api/v1/dev/seed-scale?employees=${size}&attendanceDays=14`);
if (seeded.status !== 200) {
  console.error(`seed failed -> ${seeded.status} ${seeded.text.slice(0, 400)}`);
  process.exit(1);
}
console.log(`  ${JSON.stringify(seeded.json)}`);
console.log(`  seed round-trip ${seeded.ms} ms\n`);

const adminTok = await login(seeded.json.adminEmail);
const headTok = await login(seeded.json.headEmail);

/** Runs a request three times and reports the middle value — one cold sample is mostly network. */
async function timed(label, method, path, token, note = "") {
  const runs = [];
  let last;
  for (let i = 0; i < 3; i++) {
    last = await req(method, path, { token });
    runs.push(last.ms);
  }
  runs.sort((a, b) => a - b);
  const median = runs[1];
  // 1s is where a screen stops feeling immediate; 3s is where somebody in a demo starts apologising.
  const flag = median > 3000 ? "  <== SLOW" : median > 1000 ? "  <== sluggish" : "";
  console.log(`${String(median).padStart(6)} ms  ${String(last.status).padEnd(4)} ${label.padEnd(42)} ${note}${flag}`);
  return { median, status: last.status, json: last.json };
}

console.log(`--- as the ADMIN (whole company in scope) ---`);
await timed("dashboard summary", "GET", "/api/v1/dashboard/summary", adminTok);
await timed("directory, page 1 of 25", "GET", "/api/v1/people/employees/page?page=0&size=25", adminTok);
await timed("directory, WHOLE list (no paging)", "GET", "/api/v1/people/employees", adminTok);
await timed("team/mine — ON EVERY PAGE LOAD", "GET", "/api/v1/team/mine", adminTok);
await timed("my team roster", "GET", "/api/v1/team", adminTok);
await timed("attendance day sheet", "GET", "/api/v1/people/attendance/day", adminTok);
await timed("leave inbox", "GET", "/api/v1/people/leave", adminTok);
await timed("analytics overview", "GET", "/api/v1/analytics/overview", adminTok);
await timed("payroll run", "GET", "/api/v1/payroll/run", adminTok);
await timed("org chart (departments)", "GET", "/api/v1/people/departments", adminTok);

console.log(`\n--- as a HEAD partway down the tree (transitive downline) ---`);
const standing = await timed("team/mine — ON EVERY PAGE LOAD", "GET", "/api/v1/team/mine", headTok);
console.log(`         (leads ${standing.json?.directCount} directly, ${standing.json?.totalCount} in total)`);
await timed("my team roster", "GET", "/api/v1/team", headTok);
await timed("team attendance", "GET", "/api/v1/team?direct=false", headTok);
await timed("team leave", "GET", "/api/v1/team/leave", headTok);
await timed("team expenses", "GET", "/api/v1/team/expenses", headTok);
await timed("team performance", "GET", "/api/v1/team/performance", headTok);

console.log(`\nRun with --remove to delete the scale tenant when you are done.`);
