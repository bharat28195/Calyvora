const API = process.env.ORBIT_API ?? "https://calyvora-backend.onrender.com";
const PLATFORM_PW = process.env.PLATFORM_OWNER_PASSWORD;   // never committed — see README
const PLATFORM_EMAIL = process.env.PLATFORM_OWNER_EMAIL ?? "bharat28195@calyvora.in";
const GAP = 260; // ms between requests — the edge limiter is per-IP and a fast sweep latches it
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let rateLimited = false;
async function call(token, method, path, body) {
  if (rateLimited) return { status: -429, body: null };
  await sleep(GAP);
  const res = await fetch(API + path, {
    method,
    headers: {
      ...(token ? { authorization: `Bearer ${token}` } : {}),
      ...(body ? { "content-type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(90000),
  }).catch((e) => ({ status: 0, text: async () => String(e.message) }));
  const text = await res.text().catch(() => "");
  if (res.status === 429) { rateLimited = true; console.log("!! 429 — stopping the sweep"); }
  let parsed = null;
  try { parsed = JSON.parse(text); } catch {}
  return { status: res.status, body: parsed, text: text.slice(0, 200) };
}

async function login(email, password) {
  const r = await call(null, "POST", "/api/v1/auth/login", { email, password });
  return r.status === 200 ? r.body.accessToken : null;
}

const first = (v) => (Array.isArray(v) ? v[0] : v?.content?.[0] ?? v?.items?.[0] ?? null);
const idOf = (o) => o?.id ?? o?.employeeId ?? o?.projectId ?? null;

/** Read endpoints, in the order a person would meet them. {x} placeholders come from `ids`. */
const READS = [
  "/api/v1/auth/me", "/api/v1/dashboard/summary", "/api/v1/dashboard/team",
  "/api/v1/notifications", "/api/v1/notifications/unread-count",
  "/api/v1/company", "/api/v1/company/members", "/api/v1/company/settings",
  "/api/v1/subscription/me", "/api/v1/search?q=a",
  "/api/v1/people/me", "/api/v1/people/me/compensation", "/api/v1/people/me/finance",
  "/api/v1/people/me/payslip", "/api/v1/people/employees", "/api/v1/people/employees/page",
  "/api/v1/people/departments", "/api/v1/people/holidays", "/api/v1/people/holidays/upcoming",
  "/api/v1/people/leave", "/api/v1/people/leave/mine", "/api/v1/people/leave/balance",
  "/api/v1/people/exits",
  "/api/v1/people/employees/{emp}", "/api/v1/people/employees/{emp}/compensation",
  "/api/v1/people/employees/{emp}/finance", "/api/v1/people/employees/{emp}/payslip",
  "/api/v1/people/employees/{emp}/onboarding", "/api/v1/people/employees/{emp}/goals",
  "/api/v1/people/employees/{emp}/work", "/api/v1/people/employees/{emp}/exit-checklist",
  "/api/v1/people/attendance/me", "/api/v1/people/attendance/me/today", "/api/v1/people/attendance/day",
  "/api/v1/people/attendance/employees/{emp}",
  "/api/v1/attendance/regularizations/mine", "/api/v1/attendance/regularizations/pending",
  "/api/v1/payroll/run", "/api/v1/payroll/payslip-template", "/api/v1/billing",
  "/api/v1/expenses", "/api/v1/expenses/me",
  "/api/v1/feed", "/api/v1/invitations", "/api/v1/shifts", "/api/v1/shifts/roster",
  "/api/v1/clients", "/api/v1/documents", "/api/v1/documents/templates",
  "/api/v1/documents/fields", "/api/v1/documents/letterhead",
  "/api/v1/helpdesk/tickets", "/api/v1/helpdesk/tickets/mine",
  "/api/v1/knowledge/spaces", "/api/v1/knowledge/pages/mine", "/api/v1/knowledge/search?q=a",
  "/api/v1/performance/cycles", "/api/v1/performance/me/reviews", "/api/v1/performance/team/reviews",
  "/api/v1/recruit/jobs", "/api/v1/recruit/jobs/{job}", "/api/v1/recruit/jobs/{job}/candidates",
  "/api/v1/work/projects", "/api/v1/work/tasks/mine",
  "/api/v1/work/projects/{proj}", "/api/v1/work/projects/{proj}/board",
  "/api/v1/work/projects/{proj}/backlog", "/api/v1/work/projects/{proj}/tasks",
  "/api/v1/work/projects/{proj}/tickets", "/api/v1/work/projects/{proj}/sprints",
  "/api/v1/work/projects/{proj}/velocity",
  "/api/v1/analytics/overview",
];

async function resolveIds(token) {
  const ids = {};
  const e = await call(token, "GET", "/api/v1/people/employees");
  ids.emp = idOf(first(e.body));
  const p = await call(token, "GET", "/api/v1/work/projects");
  ids.proj = idOf(first(p.body));
  const j = await call(token, "GET", "/api/v1/recruit/jobs");
  ids.job = idOf(first(j.body));
  return ids;
}

async function sweep(label, token, paths, ids) {
  console.log(`\n===== ${label} =====`);
  const rows = [];
  for (const raw of paths) {
    const path = raw.replace(/\{(\w+)\}/g, (_, k) => ids[k] ?? "MISSING");
    if (path.includes("MISSING")) { rows.push([raw, "skip", "no id to resolve"]); continue; }
    const r = await call(token, "GET", path);
    rows.push([raw, r.status, r.status >= 400 ? (r.body?.message ?? r.text) : ""]);
  }
  for (const [p, s, m] of rows) if (s !== 200 && s !== 204) console.log(`  ${String(s).padStart(4)}  ${p}  ${String(m).slice(0, 110)}`);
  const ok = rows.filter(([, s]) => s === 200 || s === 204).length;
  console.log(`  -- ${ok}/${rows.length} ok`);
  return rows;
}

// Wake it first: a cold Render instance takes the better part of a minute, and a login that lands
// during the wake fails for reasons that have nothing to do with the account.
const wake = await call(null, "GET", "/actuator/health");
console.log("wake", wake.status, wake.text.slice(0, 80));
let ownerTok = await login("ava.chen@northwind.demo", "demopass123");
if (!ownerTok) { await sleep(20000); ownerTok = await login("ava.chen@northwind.demo", "demopass123"); }
if (!ownerTok) { console.log("owner login failed — stopping"); process.exit(1); }
const ids = await resolveIds(ownerTok);
console.log("resolved ids:", ids);
await sweep("OWNER  ava.chen (Northwind)", ownerTok, READS, ids);

// A narrower set for the other roles: enough to catch a screen that 500s or a permission that is
// wrong in either direction, without a full sweep per role (the limiter is the constraint).
const NARROW = [
  "/api/v1/auth/me", "/api/v1/dashboard/summary", "/api/v1/people/me", "/api/v1/people/employees",
  "/api/v1/people/me/payslip", "/api/v1/people/leave/mine", "/api/v1/people/leave",
  "/api/v1/payroll/run", "/api/v1/expenses/me", "/api/v1/feed", "/api/v1/work/tasks/mine",
  "/api/v1/helpdesk/tickets/mine", "/api/v1/notifications", "/api/v1/analytics/overview",
  "/api/v1/company/members", "/api/v1/people/employees/{emp}/compensation",
];
for (const [label, email] of [
  ["ADMIN   marcus.reed", "marcus.reed@northwind.demo"],
  ["HR      leo.martins", "leo.martins@northwind.demo"],
  ["MANAGER tom.becker", "tom.becker@northwind.demo"],
  ["MEMBER  priya.nair", "priya.nair@northwind.demo"],
]) {
  const t = await login(email, "demopass123");
  if (!t) { console.log(`\n===== ${label} =====\n  LOGIN FAILED`); continue; }
  await sweep(label, t, NARROW, ids);
}

const plat = await login(PLATFORM_EMAIL, PLATFORM_PW);
await sweep("PLATFORM OWNER", plat, [
  "/api/v1/auth/me", "/api/v1/platform/companies", "/api/v1/platform/agencies",
  "/api/v1/platform/pricing", "/api/v1/platform/seat-requests", "/api/v1/platform/trial-requests",
  "/api/v1/dashboard/summary", "/api/v1/people/employees",
], ids);
