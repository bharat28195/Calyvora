/**
 * Functional pass over every module — the write paths, not just the reads.
 *
 * The earlier sweeps proved screens LOAD. That is a weaker claim than it sounds: a module can list
 * fine and still be unable to create anything, which is the half a customer actually notices. So this
 * creates, changes and deletes real objects, and checks the result rather than the status code alone.
 *
 * Request shapes are taken from the DTO records rather than guessed — a guessed field name produces a
 * 400 that reads exactly like a broken feature.
 */
const API = process.env.ORBIT_API ?? "https://calyvora-backend.onrender.com";
const PLATFORM_PW = process.env.PLATFORM_OWNER_PASSWORD;   // never committed — see README
const PLATFORM_EMAIL = process.env.PLATFORM_OWNER_EMAIL ?? "bharat28195@calyvora.in";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const results = [];
let token = null;

async function call(method, path, body, tok = token) {
  await sleep(160);
  const res = await fetch(API + path, {
    method,
    headers: { ...(body ? { "content-type": "application/json" } : {}), ...(tok ? { authorization: `Bearer ${tok}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(90000),
  }).catch((e) => ({ status: 0, text: async () => e.message }));
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch {}
  return { status: res.status, json, text };
}

/** Run one feature; `fn` returns a string on success or throws/returns null to fail. */
async function feature(name, fn) {
  try {
    const note = await fn();
    results.push([name, "PASS", note ?? ""]);
    console.log(`PASS  ${name.padEnd(30)} ${note ?? ""}`);
  } catch (e) {
    results.push([name, "FAIL", e.message]);
    console.log(`FAIL  ${name.padEnd(30)} ${e.message}`);
  }
}

const must = (r, what) => {
  if (r.status < 200 || r.status >= 300) {
    throw new Error(`${what} -> ${r.status} ${(r.json?.message ?? r.text ?? "").toString().slice(0, 90)}`);
  }
  return r.json;
};

// --- wake, then sign in -----------------------------------------------------------------------
console.log("waking the backend (free tier hibernates; this can take two minutes)...");
const t0 = Date.now();
await fetch(API + "/actuator/health/readiness", { signal: AbortSignal.timeout(180000) }).catch(() => {});
console.log(`awake after ${Date.now() - t0}ms\n`);

const login = async (email, password) =>
  (await call("POST", "/api/v1/auth/login", { email, password }, null)).json?.accessToken;

token = await login("ava.chen@northwind.demo", "demopass123");
if (!token) { console.log("owner login failed — stopping"); process.exit(1); }
const memberTok = await login("priya.nair@northwind.demo", "demopass123");
const platformTok = await login(PLATFORM_EMAIL, PLATFORM_PW);
const agencyTok = await login("owner@vertexgroup.demo", "demopass123");

const employees = must(await call("GET", "/api/v1/people/employees"), "employees");
const empId = employees[0].id;
const stamp = Date.now();

// --- the modules ------------------------------------------------------------------------------

await feature("Auth — session", async () => {
  const me = must(await call("GET", "/api/v1/auth/me"), "me");
  return `${me.user?.email ?? me.email} / ${me.user?.role ?? me.role}`;
});

await feature("People — departments CRUD", async () => {
  const created = must(await call("POST", "/api/v1/people/departments", { name: `QA Dept ${stamp}` }), "create");
  must(await call("PATCH", `/api/v1/people/departments/${created.id}`, { name: `QA Dept ${stamp} renamed` }), "rename");
  const list = must(await call("GET", "/api/v1/people/departments"), "list");
  const found = list.find((d) => d.id === created.id);
  if (!found || !found.name.includes("renamed")) throw new Error("rename did not persist");
  must(await call("DELETE", `/api/v1/people/departments/${created.id}`), "delete");
  return "create, rename, list, delete";
});

await feature("People — edit my profile", async () => {
  must(await call("PATCH", "/api/v1/people/me", { phone: `+91 90000 ${String(stamp).slice(-5)}` }), "patch");
  const me = must(await call("GET", "/api/v1/people/me"), "read back");
  if (!String(me.phone ?? "").includes(String(stamp).slice(-5))) throw new Error("phone did not persist");
  return "changed and read back";
});

await feature("Attendance — check in / out", async () => {
  const inR = await call("POST", "/api/v1/people/attendance/me/check-in", {}, memberTok);
  const outR = await call("POST", "/api/v1/people/attendance/me/check-out", {}, memberTok);
  const today = must(await call("GET", "/api/v1/people/attendance/me/today", null, memberTok), "today");
  if (inR.status >= 400 && outR.status >= 400) throw new Error(`in ${inR.status} / out ${outR.status}`);
  return `in ${inR.status}, out ${outR.status}, today ${today?.status ?? "recorded"}`;
});

await feature("Leave — request then approve", async () => {
  const req = must(await call("POST", "/api/v1/people/leave", {
    type: "CASUAL", startDate: "2026-12-22", endDate: "2026-12-23", reason: `QA ${stamp}`,
  }, memberTok), "member requests");
  const inbox = must(await call("GET", "/api/v1/people/leave"), "approver inbox");
  if (!inbox.find((l) => l.id === req.id)) throw new Error("request not visible to the approver");
  const done = must(await call("POST", `/api/v1/people/leave/${req.id}/approve`, {}), "approve");
  if (done.status !== "APPROVED") throw new Error(`status is ${done.status}`);
  return `requested by member, approved by owner (${req.days} days)`;
});

await feature("Payroll — run and payslip", async () => {
  const run = must(await call("GET", "/api/v1/payroll/run"), "run");
  const slip = must(await call("GET", `/api/v1/people/employees/${empId}/payslip`), "payslip");
  const n = run.lines?.length ?? run.employees?.length ?? (Array.isArray(run) ? run.length : 0);
  return `${n} lines, payslip for ${slip.employeeName ?? "employee"}`;
});

await feature("Expenses — claim, approve, reimburse", async () => {
  const claim = must(await call("POST", "/api/v1/expenses", {
    title: `QA claim ${stamp}`, category: "TRAVEL", amount: 1250.5, currency: "INR",
    spentOn: "2026-09-01", description: "Feature test",
  }, memberTok), "raise");
  must(await call("POST", `/api/v1/expenses/${claim.id}/approve`, {}), "approve");
  const paid = must(await call("POST", `/api/v1/expenses/${claim.id}/reimburse`, {}), "reimburse");
  return `raised, approved, reimbursed (${paid.status ?? "ok"})`;
});

await feature("Documents — templates and preview", async () => {
  const templates = must(await call("GET", "/api/v1/documents/templates"), "templates");
  const fields = must(await call("GET", "/api/v1/documents/fields"), "fields");
  if (!templates.length) throw new Error("no templates seeded");
  const preview = must(await call("POST", "/api/v1/documents/preview", {
    templateId: templates[0].id, employeeId: empId, title: "QA preview", overrides: {},
  }), "preview");
  return `${templates.length} templates, ${fields.length ?? Object.keys(fields).length} fields, preview "${String(preview.title).slice(0, 24)}"`;
});

await feature("Recruitment — job, candidate, pipeline", async () => {
  const job = must(await call("POST", "/api/v1/recruit/jobs", {
    title: `QA Engineer ${stamp}`, department: "Engineering", location: "Remote",
    employmentType: "FULL_TIME", description: "Feature test", openings: 1,
  }), "create job");
  const cand = must(await call("POST", `/api/v1/recruit/jobs/${job.id}/candidates`, {
    firstName: "Test", lastName: "Candidate", email: `qa.cand.${stamp}@example.test`, phone: "+911234567890",
  }), "add candidate");
  const moved = must(await call("POST", `/api/v1/recruit/candidates/${cand.id}/move`, { stage: "INTERVIEW" }), "move stage");
  must(await call("DELETE", `/api/v1/recruit/jobs/${job.id}`), "delete job");
  return `job + candidate, moved to ${moved.stage ?? "INTERVIEW"}, cleaned up`;
});

await feature("Work — project, task, sprint", async () => {
  const proj = must(await call("POST", "/api/v1/work/projects", {
    name: `QA Project ${stamp}`, key: `Q${String(stamp).slice(-4)}`, description: "Feature test",
  }), "create project");
  const task = must(await call("POST", `/api/v1/work/projects/${proj.id}/tasks`, {
    title: `QA task ${stamp}`, description: "Feature test", priority: "MEDIUM", storyPoints: 3,
  }), "create task");
  const sprint = must(await call("POST", `/api/v1/work/projects/${proj.id}/sprints`, {
    name: `QA Sprint ${stamp}`, goal: "Ship the test", startDate: "2026-09-05", endDate: "2026-09-19", capacityPoints: 20,
  }), "create sprint");
  const board = must(await call("GET", `/api/v1/work/projects/${proj.id}/board`), "board");
  must(await call("DELETE", `/api/v1/work/tasks/${task.id}`), "delete task");
  must(await call("DELETE", `/api/v1/work/sprints/${sprint.id}`), "delete sprint");
  must(await call("POST", `/api/v1/work/projects/${proj.id}/archive`, {}), "archive project");
  return "project, task, sprint, board; archived";
});

await feature("Knowledge — space, page, search", async () => {
  const space = must(await call("POST", "/api/v1/knowledge/spaces", {
    name: `QA Space ${stamp}`, key: `QS${String(stamp).slice(-4)}`, description: "Feature test",
  }), "create space");
  const page = must(await call("POST", `/api/v1/knowledge/spaces/${space.id}/pages`, {
    title: `QA page ${stamp}`, body: "Searchable marker qaqaqa",
  }), "create page");
  must(await call("PATCH", `/api/v1/knowledge/pages/${page.id}`, { title: `QA page ${stamp} v2` }), "edit page");
  const found = must(await call("GET", "/api/v1/knowledge/search?q=qaqaqa"), "search");
  must(await call("DELETE", `/api/v1/knowledge/pages/${page.id}`), "delete page");
  must(await call("POST", `/api/v1/knowledge/spaces/${space.id}/archive`, {}), "archive space");
  return `space + page, search returned ${Array.isArray(found) ? found.length : "?"}`;
});

await feature("Helpdesk — ticket and comment", async () => {
  const t = must(await call("POST", "/api/v1/helpdesk/tickets", {
    category: "IT", subject: `QA ticket ${stamp}`, description: "Feature test", priority: "MEDIUM",
  }, memberTok), "raise");
  must(await call("POST", `/api/v1/helpdesk/tickets/${t.id}/comments`, { body: "Looking into it" }), "comment");
  const upd = must(await call("PATCH", `/api/v1/helpdesk/tickets/${t.id}`, { status: "RESOLVED" }), "resolve");
  return `raised by member, commented, ${upd.status ?? "resolved"}`;
});

await feature("Feed — post, comment, react", async () => {
  const post = must(await call("POST", "/api/v1/feed", { body: `QA post ${stamp}`, kind: "UPDATE", visibility: "COMPANY" }), "post");
  must(await call("POST", `/api/v1/feed/${post.id}/comments`, { body: "Nice" }, memberTok), "comment");
  must(await call("POST", `/api/v1/feed/${post.id}/react`, { emoji: "👍" }, memberTok), "react");
  must(await call("POST", `/api/v1/feed/${post.id}/pin`, {}), "pin");
  must(await call("DELETE", `/api/v1/feed/${post.id}`), "delete");
  return "post, comment, react, pin, delete";
});

await feature("Performance — cycle and reviews", async () => {
  const cycle = must(await call("POST", "/api/v1/performance/cycles", {
    name: `QA Cycle ${stamp}`, periodStart: "2026-01-01", periodEnd: "2026-12-31",
  }), "create cycle");
  const reviews = must(await call("GET", `/api/v1/performance/cycles/${cycle.id}/reviews`), "reviews");
  return `cycle created, ${Array.isArray(reviews) ? reviews.length : 0} reviews generated`;
});

await feature("Shifts — shift and roster", async () => {
  const shift = must(await call("POST", "/api/v1/shifts", {
    name: `QA Shift ${stamp}`, startTime: "09:00", endTime: "18:00", color: "#7c5cff",
  }), "create shift");
  const assign = await call("POST", "/api/v1/shifts/roster/assign", {
    employeeId: empId, onDate: "2026-09-10", shiftId: shift.id,
  });
  must(await call("DELETE", `/api/v1/shifts/${shift.id}`), "delete shift");
  return `shift created, roster assign ${assign.status}`;
});

await feature("Clients — client and request", async () => {
  const c = must(await call("POST", "/api/v1/clients", {
    name: `QA Client ${stamp}`, contactName: "Test Contact", contactEmail: `qa.client.${stamp}@example.test`, status: "ACTIVE",
  }), "create client");
  const req = must(await call("POST", `/api/v1/clients/${c.id}/requests`, {
    title: `QA requirement ${stamp}`, description: "2 developers", status: "OPEN",
  }), "add request");
  must(await call("DELETE", `/api/v1/clients/${c.id}/requests/${req.id}`), "delete request");
  must(await call("DELETE", `/api/v1/clients/${c.id}`), "delete client");
  return "client + requirement, both removed";
});

await feature("Notifications", async () => {
  const list = must(await call("GET", "/api/v1/notifications"), "list");
  const count = must(await call("GET", "/api/v1/notifications/unread-count"), "count");
  must(await call("POST", "/api/v1/notifications/read-all", {}), "read all");
  return `${Array.isArray(list) ? list.length : "?"} notifications, unread was ${count.count ?? count.unread ?? "?"}`;
});

await feature("AI assistant", async () => {
  const a = must(await call("POST", "/api/v1/assistant/ask", { question: "How many people are on leave this month?" }), "ask");
  return String(a.answer ?? a.reply ?? JSON.stringify(a)).slice(0, 70).replace(/\s+/g, " ");
});

await feature("Search", async () => {
  const s = must(await call("GET", "/api/v1/search?q=a"), "search");
  const n = Array.isArray(s) ? s.length : (s.results?.length ?? Object.keys(s).length);
  return `${n} result groups`;
});

await feature("Analytics + dashboard", async () => {
  const a = must(await call("GET", "/api/v1/analytics/overview"), "analytics");
  const d = must(await call("GET", "/api/v1/dashboard/summary"), "dashboard");
  return `headcount ${a.headcount ?? d.headcount ?? "?"}`;
});

await feature("Subscription (tenant view)", async () => {
  const s = must(await call("GET", "/api/v1/subscription/me"), "subscription");
  return `${s.status} · ${s.seats} seats · ${s.currency ?? ""}${s.monthlyCost ?? s.pricePerEmployee ?? ""}`;
});

await feature("Agency console", async () => {
  const o = must(await call("GET", "/api/v1/agency/overview", null, agencyTok), "overview");
  const cs = must(await call("GET", "/api/v1/agency/companies", null, agencyTok), "companies");
  return `${cs.length} companies, spend ${o.monthlySpend ?? o.monthlyCost ?? "?"}`;
});

await feature("Platform console", async () => {
  const cs = must(await call("GET", "/api/v1/platform/companies", null, platformTok), "companies");
  const pricing = must(await call("GET", "/api/v1/platform/pricing", null, platformTok), "pricing");
  const trials = must(await call("GET", "/api/v1/platform/trial-requests", null, platformTok), "trials");
  const agencies = must(await call("GET", "/api/v1/platform/agencies", null, platformTok), "agencies");
  return `${cs.length} companies, ${pricing.length ?? 1} price lists, ${trials.length} trials, ${agencies.length} agencies`;
});

await feature("Trial request (public signup)", async () => {
  const r = must(await call("POST", "/api/v1/trial-requests", {
    companyName: `QA Trial ${stamp}`, contactName: "QA Tester", email: `qa.trial.${stamp}@example.test`,
    phone: "+911234567890", teamSize: "11-50", note: "Feature test", source: "qa",
  }, null), "submit");
  return `received=${r.received}, emailSent=${r.emailSent}`;
});

// --- summary ------------------------------------------------------------------------------------
const pass = results.filter((r) => r[1] === "PASS").length;
console.log(`\n================  ${pass}/${results.length} features working  ================`);
const failed = results.filter((r) => r[1] === "FAIL");
if (failed.length) {
  console.log("\nNOT WORKING:");
  for (const [n, , why] of failed) console.log(`  ${n.padEnd(30)} ${why}`);
}
