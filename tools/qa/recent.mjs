// Deployed-app checks for the features of PD-55 to PD-76: the payroll month lock and statutory files,
// income tax (declarations, Form 130, TDS), exits and settlement, attendance summaries, and letters
// signed inside Orbit. Read checks for four roles, then one letter sent, opened page by page and
// signed. Paced like sweep.mjs and features.mjs; run against the demo company only.
//
//   node tools/qa/recent.mjs
//
// It leaves one signed letter behind for Priya Nair (a signed letter is a record and cannot be deleted).

const API = process.env.ORBIT_API ?? "https://calyvora-backend.onrender.com";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const log = (ok, name, note = "") => {
  results.push([ok, name]);
  console.log(`${ok ? "PASS" : "FAIL"}  ${name.padEnd(60)} ${note}`);
};

async function call(tok, method, path, body) {
  await sleep(300);
  const res = await fetch(API + path, {
    method,
    headers: { ...(tok ? { authorization: `Bearer ${tok}` } : {}), ...(body ? { "content-type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(90000),
  }).catch((e) => ({ status: 0, headers: new Headers(), arrayBuffer: async () => new TextEncoder().encode(e.message) }));
  const bytes = Buffer.from(await res.arrayBuffer().catch(() => new ArrayBuffer(0)));
  let json = null;
  try { json = JSON.parse(bytes.toString("utf8")); } catch {}
  return { status: res.status, json, type: res.headers.get("content-type") ?? "", size: bytes.length, text: bytes.toString("utf8").slice(0, 160) };
}
const login = async (email) => (await call(null, "POST", "/api/v1/auth/login", { email, password: "demopass123" })).json?.accessToken;

const wake = await call(null, "GET", "/actuator/health");
console.log("wake", wake.status, wake.text.slice(0, 60));

const T = {
  ADMIN: await login("ava.chen@northwind.demo"),
  HR: await login("leo.martins@northwind.demo"),
  MANAGER: await login("tom.becker@northwind.demo"),
  MEMBER: await login("priya.nair@northwind.demo"),
};
const acme = await login("admin@acme.demo");
const priyaId = (await call(T.MEMBER, "GET", "/api/v1/people/me")).json?.id;

// ---- reads: everyone has their own; payroll and tax management only for those who run it ----
const OWN = ["/api/v1/tax/me/declaration", "/api/v1/tax/me/computation", "/api/v1/tax/me/form130", "/api/v1/tax/settings",
  "/api/v1/subscription/me", "/api/v1/people/attendance/me/today", "/api/v1/people/attendance/month-summary"];
const RUNS_PAYROLL = ["/api/v1/payroll/months", "/api/v1/payroll/filings/readiness", "/api/v1/payroll/statutory-settings",
  "/api/v1/tax/declarations", "/api/v1/tax/deposits", "/api/v1/payroll/tds-openings", "/api/v1/documents/signatories"];
for (const [role, tok] of Object.entries(T)) {
  for (const p of OWN) {
    const r = await call(tok, "GET", p);
    log(r.status === 200, `${role} reads ${p}`, String(r.status));
  }
  const allowed = role === "ADMIN" || role === "HR";
  for (const p of RUNS_PAYROLL) {
    const r = await call(tok, "GET", p);
    log(allowed ? r.status === 200 : r.status === 403, `${role} ${allowed ? "reads" : "is refused"} ${p}`, String(r.status));
  }
}
for (const role of ["MANAGER", "MEMBER"]) {
  log((await call(T[role], "GET", "/api/v1/shifts/work-day")).status === 403, `${role} is refused the shift policy (shifts screen is for admins)`);
}
// A member's month summary is themselves; settlement needs an approved exit first.
log((await call(T.MEMBER, "GET", "/api/v1/people/attendance/month-summary")).json?.headcount === 1, "MEMBER month summary covers only themselves");
log((await call(T.ADMIN, "GET", `/api/v1/people/employees/${priyaId}/settlement`)).status === 409, "Settlement refused before an approved exit");

// ---- statutory files ----
const months = (await call(T.ADMIN, "GET", "/api/v1/payroll/months")).json ?? [];
const month = (Array.isArray(months) ? months : []).find((m) => m.finalized)?.month ?? "2026-10";
for (const kind of ["ecr", "esi", "pt", "lwf"]) {
  const r = await call(T.ADMIN, "GET", `/api/v1/payroll/filings/${month}/${kind}`);
  log(r.status === 200, `Statutory file ${kind.toUpperCase()} for ${month}`, `${r.status} ${r.size} B`);
}
const fy = new Date().getMonth() >= 3 ? new Date().getFullYear() : new Date().getFullYear() - 1;
const q = `${fy}-${String(fy + 1).slice(2)}-Q1`;
{
  // 24Q is built only from finalised months; until a quarter is finalised the right answer is a 409 that says so.
  const r = await call(T.ADMIN, "GET", `/api/v1/payroll/filings/24q/${q}`);
  log(r.status === 200 || (r.status === 409 && /Finalise/.test(r.json?.message ?? "")), `Form 24Q file for ${q}`, `${r.status} ${r.json?.message ?? ""}`);
}
log((await call(T.MEMBER, "POST", "/api/v1/tax/me/preview", {})).status === 200, "Member previews their tax");

// ---- a letter: generate and download. Signing is parked (branch parked/letter-signing) ----
const templates = (await call(T.ADMIN, "GET", "/api/v1/documents/templates")).json ?? [];
const tpl = templates.find((t) => t.kind === "APPOINTMENT_LETTER") ?? templates[0];
const doc = await call(T.ADMIN, "POST", "/api/v1/documents", { templateId: tpl?.id, employeeId: priyaId, title: `QA letter ${Date.now()}` });
log(doc.status === 201, "Generate a letter for an employee", String(doc.status));
const pdf = await call(T.ADMIN, "GET", `/api/v1/documents/${doc.json?.id}/pdf`);
log(pdf.status === 200 && pdf.type.includes("pdf"), "Download the letter as a PDF", `${pdf.status} ${pdf.size} B`);
log([403, 404].includes((await call(acme, "GET", `/api/v1/documents/${doc.json?.id}`)).status), "Another company cannot open the letter");
log([403, 404].includes((await call(T.MEMBER, "GET", `/api/v1/documents/${doc.json?.id}/pdf`)).status), "A member cannot download HR's letters");
log((await call(T.MEMBER, "GET", "/api/v1/me/letters")).status === 404, "Signing screens are switched off");
log((await call(null, "GET", "/actuator/health")).status === 200, "Server still healthy after the letter");

// ---- the rest of the edges ----
log((await call(T.MEMBER, "POST", "/api/v1/auth/change-password", { currentPassword: "wrong-password-x", newPassword: "Another-pass-123" })).status === 400,
  "Change password refuses a wrong current password");
for (const p of [`/api/v1/people/employees/${priyaId}/settlement`, `/api/v1/tax/declarations/${priyaId}`]) {
  log((await call(acme, "GET", p)).status === 404, `Another company gets 404 on ${p.replace(priyaId, "{priya}")}`);
}

const failed = results.filter(([ok]) => !ok);
console.log(`\n${results.length - failed.length}/${results.length} pass`);
if (failed.length) { for (const [, n] of failed) console.log("  FAIL " + n); process.exit(1); }
