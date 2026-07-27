// In-browser mock of the Calyvora backend (Sprint1 §7 contract).
// Used for frontend-first development: the whole golden path runs with no Java backend.
// State persists in localStorage; "emails" land in a mock mailbox (see /dev/mailbox).
// Swapped out for the real backend by setting NEXT_PUBLIC_API_MODE=live.

import {
  ApiError,
  type ApiErrorBody,
  type CompanySettings,
  type DashboardSummary,
  type SearchResponse,
  type SearchGroup,
  type SearchHit,
  type AssistantResponse,
  type TeamOverview,
  type Compensation,
  type Payslip,
  type PayrollRun,
  type WorkItem,
  type AnalyticsOverview,
  type BillingOverview,
  type PayslipComponent,
  type Page,
  type JobOpening,
  type JobOpeningInput,
  type Candidate,
  type CandidateInput,
  type HelpdeskTicket,
  type HelpdeskComment,
  type RaiseTicketInput,
  type UpdateTicketInput,
  type Shift,
  type ShiftInput,
  type Roster,
  type RosterEntry,
  type Goal,
  type ReviewCycle,
  type ReviewStatus,
  type HikeType,
  type PerformanceReview,
  type CreateCycleInput,
  type SelfAssessmentInput,
  type ManagerReviewInput,
  type Client,
  type ClientDetail,
  type ClientRequestItem,
  type AppNotification,
  type NotificationType,
  type Post,
  type PostInput,
  type SprintReport,
  type Velocity,
  type BurndownPoint,
  type MemberLoad,
  type PostKind,
  type PostVisibility,
  type ExpenseClaim,
  type ExpenseInput,
  type ExpenseSummary,
  type Holiday,
  type AttendanceDay,
  type AttendanceEntry,
  type AttendanceMonth,
  type Regularization,
  type RegularizationInput,
  type AttendanceStatus,
  type MarkAttendanceInput,
  type DocumentTemplate,
  type DocumentPreview,
  type GeneratedDoc,
  type GenerateDocInput,
  type MergeField,
  type Department,
  type Employee,
  type Invitation,
  type LeaveBalance,
  type LeaveRequest,
  type LoginResult,
  type OnboardingTask,
  type Project,
  type Task,
  type Sprint,
  type Board,
  type Ticket,
  type Space,
  type KnowledgePage,
  type PageSummary,
  type Me,
  type Member,
  type Role,
} from "@/lib/types";
import {
  KIND_LABELS, MERGE_FIELDS, STARTER_TEMPLATES, letterDate, placeholdersIn, renderTemplate, tenure,
} from "@/lib/documents";

const KEY = "calyvora_mock_db_v1";
const SESSION_COOKIE = "calyvora_session";

interface Company {
  id: string;
  name: string;
  slug: string;
  status: "PENDING" | "ACTIVE" | "SUSPENDED";
}
interface User {
  id: string;
  companyId: string;
  email: string;
  password: string | null;
  firstName: string;
  lastName: string;
  role: Role;
  status: "PENDING_VERIFICATION" | "INVITED" | "ACTIVE" | "DISABLED";
}
interface Token {
  token: string;
  userId?: string;
  companyId?: string;
  email?: string;
  role?: Role;
  kind: "verify" | "invite";
  invitedByEmail?: string;
  expiresAt: number;
  consumed: boolean;
  createdAt: number;
}
export interface MailMessage {
  to: string;
  subject: string;
  link: string;
  sentAt: number;
}
interface EmployeeRow {
  id: string;
  companyId: string;
  userId: string;
  employeeNo: string | null;
  jobTitle: string | null;
  employmentType: Employee["employmentType"];
  employmentStatus: Employee["employmentStatus"];
  departmentId: string | null;
  managerId: string | null;
  workLocation: string | null;
  phone: string | null;
  startDate: string | null;
  endDate?: string | null;
  skills?: string[];
  rating?: number | null;
}
interface DeptRow {
  id: string;
  companyId: string;
  name: string;
  parentId: string | null;
  leadUserId: string | null;
}
interface OnboardRow {
  id: string;
  companyId: string;
  employeeId: string;
  title: string;
  sortOrder: number;
  completed: boolean;
  completedAt: string | null;
}
interface LeaveRow {
  id: string;
  companyId: string;
  employeeId: string;
  type: LeaveRequest["type"];
  startDate: string;
  endDate: string;
  days: number;
  reason: string | null;
  status: LeaveRequest["status"];
  decidedAt: string | null;
  createdAt: string;
}
interface ProjectRow {
  id: string;
  companyId: string;
  name: string;
  key: string;
  description: string | null;
  status: Project["status"];
  leadUserId: string | null;
  createdAt: string;
}
interface TaskRow {
  id: string;
  companyId: string;
  projectId: string;
  number: number;
  title: string;
  description: string | null;
  status: Task["status"];
  priority: Task["priority"];
  assigneeId: string | null;
  sprintId: string | null;
  dueDate: string | null;
  sortOrder: number;
  createdAt: string;
  /** Estimate; null when unsized (V23). */
  storyPoints?: number | null;
}
interface SprintRow {
  id: string;
  companyId: string;
  projectId: string;
  name: string;
  goal: string | null;
  startDate: string | null;
  endDate: string | null;
  status: Sprint["status"];
  createdAt: string;
  /** What the team believes it can take on (V23). */
  capacityPoints?: number | null;
}
interface TicketRow {
  id: string;
  companyId: string;
  projectId: string;
  number: number;
  subject: string;
  description: string | null;
  requesterName: string | null;
  requesterEmail: string | null;
  status: Ticket["status"];
  priority: Ticket["priority"];
  assigneeId: string | null;
  createdBy: string;
  createdAt: string;
}
interface SpaceRow {
  id: string;
  companyId: string;
  name: string;
  key: string;
  description: string | null;
  status: Space["status"];
  createdBy: string;
  createdAt: string;
}
interface PageRow {
  id: string;
  companyId: string;
  spaceId: string;
  parentId: string | null;
  title: string;
  body: string | null;
  status: KnowledgePage["status"];
  authorId: string | null;
  linkedTaskId: string | null;
  sortOrder: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}
interface DB {
  companies: Company[];
  users: User[];
  tokens: Token[];
  invitations: Invitation[];
  settings: CompanySettings[];
  employees: EmployeeRow[];
  departments: DeptRow[];
  onboarding: OnboardRow[];
  leave: LeaveRow[];
  projects: ProjectRow[];
  tasks: TaskRow[];
  sprints: SprintRow[];
  tickets: TicketRow[];
  spaces: SpaceRow[];
  pages: PageRow[];
  sessions: Record<string, string>; // accessToken -> userId
  currentToken?: string; // the active session's token, so a page reload can rehydrate (mock stand-in for the refresh cookie)
  mailbox: MailMessage[];
}

function uuid(): string {
  return crypto.randomUUID();
}

function err(status: number, code: string, message: string, fields?: Record<string, string>): ApiError {
  const body: ApiErrorBody = {
    timestamp: new Date().toISOString(),
    status,
    code,
    message,
    correlationId: uuid(),
    errors: fields ? Object.entries(fields).map(([field, message]) => ({ field, message })) : undefined,
  };
  return new ApiError(body);
}

function load(): DB {
  if (typeof window === "undefined") {
    return { companies: [], users: [], tokens: [], invitations: [], settings: [], employees: [], departments: [], onboarding: [], leave: [], projects: [], tasks: [], sprints: [], tickets: [], spaces: [], pages: [], sessions: {}, mailbox: [] };
  }
  const raw = window.localStorage.getItem(KEY);
  if (!raw) {
    const fresh: DB = { companies: [], users: [], tokens: [], invitations: [], settings: [], employees: [], departments: [], onboarding: [], leave: [], projects: [], tasks: [], sprints: [], tickets: [], spaces: [], pages: [], sessions: {}, mailbox: [] };
    window.localStorage.setItem(KEY, JSON.stringify(fresh));
    return fresh;
  }
  return JSON.parse(raw) as DB;
}

function save(db: DB): void {
  window.localStorage.setItem(KEY, JSON.stringify(db));
}

function setSessionCookie(present: boolean): void {
  if (present) {
    document.cookie = `${SESSION_COOKIE}=1; path=/; SameSite=Lax`;
  } else {
    document.cookie = `${SESSION_COOKIE}=; path=/; Max-Age=0; SameSite=Lax`;
  }
}

function slugify(name: string): string {
  return (
    name
      .toLowerCase()
      .normalize("NFD")
      .replace(/[̀-ͯ]/g, "")
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/(^-+|-+$)/g, "") || "company"
  );
}

function uniqueSlug(db: DB, base: string): string {
  let slug = base;
  let n = 1;
  while (db.companies.some((c) => c.slug === slug)) {
    slug = `${base}-${++n}`;
  }
  return slug;
}

// simulate network latency so loading states are visible/verifiable
const delay = () => new Promise((r) => setTimeout(r, 250));

function pushMail(db: DB, to: string, subject: string, link: string): void {
  db.mailbox.unshift({ to, subject, link, sentAt: Date.now() });
}

function toMe(db: DB, user: User): Me {
  const company = db.companies.find((c) => c.id === user.companyId)!;
  return {
    user: {
      id: user.id,
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      role: user.role,
      status: user.status,
    },
    company: {
      id: company.id, name: company.name, slug: company.slug, status: company.status,
      currency: db.settings.find((s) => s.companyId === company.id)?.currency ?? "INR",
      timezone: db.settings.find((s) => s.companyId === company.id)?.timezone ?? "UTC",
    },
  };
}

function requireSession(db: DB, accessToken: string | null): User {
  const userId = accessToken ? db.sessions[accessToken] : undefined;
  const user = userId ? db.users.find((u) => u.id === userId) : undefined;
  if (!user) throw err(401, "UNAUTHORIZED", "Authentication required");
  return user;
}

function requireAdmin(user: User): void {
  if (user.role !== "OWNER" && user.role !== "ADMIN") {
    throw err(403, "FORBIDDEN", "You do not have permission to perform this action");
  }
}

export const mockBackend = {
  async register(input: {
    companyName: string;
    firstName: string;
    lastName: string;
    email: string;
    password: string;
  }): Promise<void> {
    await delay();
    const db = load();
    const email = input.email.trim().toLowerCase();
    if (db.users.some((u) => u.email === email)) {
      throw err(409, "CONFLICT", "That email is already registered", { email: "already in use" });
    }
    const company: Company = {
      id: uuid(),
      name: input.companyName.trim(),
      slug: uniqueSlug(db, slugify(input.companyName)),
      status: "PENDING",
    };
    const user: User = {
      id: uuid(),
      companyId: company.id,
      email,
      password: input.password,
      firstName: input.firstName.trim(),
      lastName: input.lastName.trim(),
      role: "OWNER",
      status: "PENDING_VERIFICATION",
    };
    const token: Token = {
      token: uuid(),
      userId: user.id,
      kind: "verify",
      expiresAt: Date.now() + 24 * 3600 * 1000,
      consumed: false,
      createdAt: Date.now(),
    };
    db.companies.push(company);
    db.users.push(user);
    db.settings.push({ companyId: company.id, timezone: "Asia/Kolkata", locale: "en", currency: "INR", legalName: null, address: null, logoUrl: null });
    db.tokens.push(token);
    pushMail(db, email, "Verify your Calyvora email", `/verify-email?token=${token.token}`);
    save(db);
  },

  async verifyEmail(rawToken: string): Promise<void> {
    await delay();
    const db = load();
    const token = db.tokens.find((t) => t.token === rawToken && t.kind === "verify");
    if (!token) throw err(400, "VALIDATION_ERROR", "This verification link is invalid");
    if (token.consumed) throw err(410, "TOKEN_EXPIRED", "This link has already been used");
    if (token.expiresAt < Date.now()) throw err(410, "TOKEN_EXPIRED", "This link has expired");
    const user = db.users.find((u) => u.id === token.userId)!;
    user.status = "ACTIVE";
    const company = db.companies.find((c) => c.id === user.companyId)!;
    company.status = "ACTIVE";
    token.consumed = true;
    save(db);
  },

  async resendVerification(email: string): Promise<void> {
    await delay();
    const db = load();
    const user = db.users.find((u) => u.email === email.trim().toLowerCase());
    // Do not reveal whether the account exists (no enumeration).
    if (user && user.status === "PENDING_VERIFICATION") {
      const token: Token = {
        token: uuid(),
        userId: user.id,
        kind: "verify",
        expiresAt: Date.now() + 24 * 3600 * 1000,
        consumed: false,
        createdAt: Date.now(),
      };
      db.tokens.push(token);
      pushMail(db, user.email, "Verify your Calyvora email", `/verify-email?token=${token.token}`);
      save(db);
    }
  },

  async login(email: string, password: string): Promise<LoginResult> {
    await delay();
    const db = load();
    const user = db.users.find((u) => u.email === email.trim().toLowerCase());
    if (!user || user.password !== password) {
      throw err(401, "UNAUTHORIZED", "Invalid email or password"); // generic, no enumeration
    }
    if (user.status === "PENDING_VERIFICATION") {
      throw err(403, "FORBIDDEN", "Please verify your email before logging in");
    }
    if (user.status !== "ACTIVE") {
      throw err(403, "FORBIDDEN", "Your account is not active");
    }
    const accessToken = uuid();
    db.sessions[accessToken] = user.id;
    db.currentToken = accessToken;
    save(db);
    setSessionCookie(true);
    return { accessToken, me: toMe(db, user) };
  },

  // Mock stand-in for POST /auth/refresh: rehydrate the active session after a page reload.
  async refresh(): Promise<LoginResult> {
    const db = load();
    const token = db.currentToken;
    const userId = token ? db.sessions[token] : undefined;
    const user = userId ? db.users.find((u) => u.id === userId) : undefined;
    if (!token || !user) throw err(401, "UNAUTHORIZED", "No active session");
    return { accessToken: token, me: toMe(db, user) };
  },

  async logout(accessToken: string | null): Promise<void> {
    await delay();
    const db = load();
    if (accessToken) delete db.sessions[accessToken];
    db.currentToken = undefined;
    save(db);
    setSessionCookie(false);
  },

  async me(accessToken: string | null): Promise<Me> {
    const db = load();
    const user = requireSession(db, accessToken);
    return toMe(db, user);
  },

  async dashboardSummary(accessToken: string | null): Promise<DashboardSummary> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const company = db.companies.find((c) => c.id === user.companyId)!;
    const cid = user.companyId;
    const mine = <T extends { companyId: string }>(rows: T[]) => rows.filter((r) => r.companyId === cid);
    const activeSprintRow = mine(db.sprints).find((s) => s.status === "ACTIVE") ?? null;
    const activeSprint = activeSprintRow
      ? {
          name: activeSprintRow.name,
          total: db.tasks.filter((t) => t.sprintId === activeSprintRow.id).length,
          done: db.tasks.filter((t) => t.sprintId === activeSprintRow.id && t.status === "DONE").length,
        }
      : null;
    return {
      companyName: company.name,
      yourRole: user.role,
      memberCount: db.users.filter((u) => u.companyId === cid && u.status === "ACTIVE").length,
      pendingInviteCount: db.invitations.filter(
        (i) => i.status === "PENDING" && companyOfInvite(db, i.id) === cid,
      ).length,
      departmentCount: mine(db.departments).length,
      projectCount: mine(db.projects).length,
      openTaskCount: mine(db.tasks).filter((t) => t.status !== "DONE").length,
      doneTaskCount: mine(db.tasks).filter((t) => t.status === "DONE").length,
      openTicketCount: mine(db.tickets).filter((t) => t.status === "OPEN" || t.status === "PENDING").length,
      spaceCount: mine(db.spaces).length,
      pageCount: mine(db.pages).length,
      activeSprint,
    };
  },

  async compensation(accessToken: string | null, employeeId: string): Promise<Compensation> {
    await delay();
    const db = load();
    requireSession(db, accessToken);
    return buildCompensation(db, employeeId);
  },

  async addCompensation(accessToken: string | null, employeeId: string,
    input: { annualAmount: number; effectiveDate?: string; currency?: string; reason?: string }): Promise<Compensation> {
    await delay();
    const db = load();
    requireSession(db, accessToken);
    const existing = mockComp[employeeId] ?? [];
    const type = existing.length === 0 ? "INITIAL" : input.annualAmount > existing[0].annualAmount ? "HIKE" : "ADJUSTMENT";
    existing.unshift({
      id: crypto.randomUUID(),
      effectiveDate: input.effectiveDate || new Date().toISOString().slice(0, 10),
      annualAmount: input.annualAmount,
      changeType: type,
      reason: input.reason || null,
      currency: (input.currency || "USD").toUpperCase(),
    });
    mockComp[employeeId] = existing.sort((a, b) => b.effectiveDate.localeCompare(a.effectiveDate));
    return buildCompensation(db, employeeId);
  },

  async payslip(accessToken: string | null, employeeId: string, month?: string): Promise<Payslip> {
    await delay();
    const db = load();
    requireSession(db, accessToken);
    const comp = buildCompensation(db, employeeId);
    if (comp.currentAnnual == null) throw new ApiError({ timestamp: "", status: 404, code: "NOT_FOUND", message: "No salary on record" });
    const gross = round2(comp.currentAnnual / 12);
    const basic = round2(gross * 0.5), hra = round2(gross * 0.25), special = round2(gross - basic - hra);
    const pf = round2(basic * 0.12), tax = round2(gross * 0.1);
    const deductions = [{ label: "Provident fund", amount: pf }, { label: "Income tax", amount: tax }];

    // Attendance linkage (LOP) — mirror CompensationService.
    const mon = buildAttendanceMonth(db, employeeId, month);
    let workingDays = 0, lopDays = 0;
    for (const d of mon.days) {
      if (!d.status || d.status === "WEEK_OFF" || d.status === "HOLIDAY") continue;
      workingDays++;
      if (d.status === "ABSENT") lopDays += 1;
      else if (d.status === "HALF_DAY") lopDays += 0.5;
    }
    let totalDed = round2(pf + tax), net = round2(gross - pf - tax);
    if (lopDays > 0 && workingDays > 0) {
      const lop = round2((gross / workingDays) * lopDays);
      deductions.push({ label: `Loss of pay (${lopDays === Math.floor(lopDays) ? lopDays : lopDays} day${lopDays === 1 ? "" : "s"})`, amount: lop });
      totalDed = round2(totalDed + lop); net = round2(net - lop);
    }
    const settings = db.settings.find((s) => s.companyId === (db.users.find((u) => db.employees.find((e) => e.id === employeeId)?.userId === u.id)?.companyId));
    const company = db.companies.find((c) => c.id === settings?.companyId);
    return {
      employeeId, employeeName: comp.employeeName, month: month || new Date().toISOString().slice(0, 7), currency: comp.currency,
      companyName: settings?.legalName || company?.name || "", companyAddress: settings?.address ?? null,
      earnings: [{ label: "Basic", amount: basic }, { label: "House rent allowance", amount: hra }, { label: "Special allowance", amount: special }],
      deductions, gross, totalDeductions: totalDed, net,
      workingDays, lopDays, payableDays: Math.max(0, workingDays - lopDays),
    };
  },

  async myCompensation(accessToken: string | null): Promise<Compensation> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    return buildCompensation(db, myEmployeeId(db, user));
  },
  async myPayslip(accessToken: string | null, month?: string): Promise<Payslip> {
    const db = load();
    const user = requireSession(db, accessToken);
    return this.payslip(accessToken, myEmployeeId(db, user), month);
  },
  async payrollRun(accessToken: string | null, month?: string): Promise<PayrollRun> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const employees = db.employees.filter((e) => e.companyId === user.companyId);
    const rows: PayrollRun["rows"] = [];
    let totalGross = 0, totalNet = 0, totalLopDays = 0, currency = "INR";
    for (const e of employees) {
      const comp = buildCompensation(db, e.id);
      if (comp.currentAnnual == null) continue;
      const p = await this.payslip(accessToken, e.id, month);
      rows.push({ employeeId: e.id, name: p.employeeName, jobTitle: e.jobTitle ?? null, gross: p.gross, lopDays: p.lopDays, net: p.net });
      totalGross = round2(totalGross + p.gross); totalNet = round2(totalNet + p.net); totalLopDays += p.lopDays; currency = p.currency;
    }
    return { month: month || new Date().toISOString().slice(0, 7), currency, rows, totalGross, totalNet, totalLopDays, employees: rows.length };
  },

  async clients(accessToken: string | null): Promise<Client[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return (mockClients[user.companyId] ?? []).map((c) => withOpen(user.companyId, c));
  },
  async createClient(accessToken: string | null, input: Partial<Client> & { name: string }): Promise<Client> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const c: Client = {
      id: crypto.randomUUID(), name: input.name, contactName: input.contactName ?? null,
      contactEmail: input.contactEmail ?? null, phone: input.phone ?? null, website: input.website ?? null,
      status: input.status ?? "LEAD", notes: input.notes ?? null, createdAt: new Date().toISOString(), openRequests: 0,
    };
    mockClients[user.companyId] = [c, ...(mockClients[user.companyId] ?? [])];
    return c;
  },
  async client(accessToken: string | null, id: string): Promise<ClientDetail> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const c = (mockClients[user.companyId] ?? []).find((x) => x.id === id);
    if (!c) throw err(404, "NOT_FOUND", "Client not found");
    return { client: withOpen(user.companyId, c), requests: (mockClientReqs[id] ?? []).slice() };
  },
  async updateClient(accessToken: string | null, id: string, patch: Partial<Client>): Promise<Client> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const c = (mockClients[user.companyId] ?? []).find((x) => x.id === id);
    if (!c) throw err(404, "NOT_FOUND", "Client not found");
    Object.assign(c, patch);
    return withOpen(user.companyId, c);
  },
  async deleteClient(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    mockClients[user.companyId] = (mockClients[user.companyId] ?? []).filter((x) => x.id !== id);
    delete mockClientReqs[id];
  },
  async addClientRequest(accessToken: string | null, clientId: string, input: { title: string; description?: string }): Promise<ClientRequestItem> {
    await delay();
    const db = load();
    requireAdmin(requireSession(db, accessToken));
    const r: ClientRequestItem = { id: crypto.randomUUID(), title: input.title, description: input.description ?? null, status: "REQUESTED", createdAt: new Date().toISOString() };
    mockClientReqs[clientId] = [r, ...(mockClientReqs[clientId] ?? [])];
    return r;
  },
  async updateClientRequest(accessToken: string | null, clientId: string, requestId: string, patch: Partial<ClientRequestItem>): Promise<ClientRequestItem> {
    await delay();
    const db = load();
    requireAdmin(requireSession(db, accessToken));
    const r = (mockClientReqs[clientId] ?? []).find((x) => x.id === requestId);
    if (!r) throw err(404, "NOT_FOUND", "Request not found");
    Object.assign(r, patch);
    return r;
  },
  async deleteClientRequest(accessToken: string | null, clientId: string, requestId: string): Promise<void> {
    await delay();
    const db = load();
    requireAdmin(requireSession(db, accessToken));
    mockClientReqs[clientId] = (mockClientReqs[clientId] ?? []).filter((x) => x.id !== requestId);
  },

  async myEmployee(accessToken: string | null): Promise<Employee> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    employeeForUser(db, user);   // auto-provisions, matching the real endpoint
    return toEmployee(db, user);
  },

  // --- sprint reporting (mirrors SprintReportService) ---
  async sprintReport(accessToken: string | null, sprintId: string): Promise<SprintReport> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const sprint = db.sprints.find((s) => s.id === sprintId && s.companyId === user.companyId);
    if (!sprint) throw err(404, "NOT_FOUND", "Sprint not found");
    const tasks = db.tasks.filter((t) => t.sprintId === sprintId);

    let committed = 0, completed = 0, unestimated = 0, done = 0;
    for (const t of tasks) {
      const points = t.storyPoints ?? 0;
      if (t.storyPoints == null) unestimated++;
      committed += points;
      if (t.status === "DONE") { completed += points; done++; }
    }

    const start = sprint.startDate, end = sprint.endDate;
    const burndown: BurndownPoint[] = [];
    if (start && end) {
      const startMs = new Date(`${start}T00:00:00`).getTime();
      const endMs = new Date(`${end}T00:00:00`).getTime();
      const span = Math.max(1, Math.round((endMs - startMs) / 86_400_000));
      const today = todayIso();
      for (let i = 0; i <= span; i++) {
        const date = new Date(startMs + i * 86_400_000).toISOString().slice(0, 10);
        const ideal = Math.round((committed - (committed * i) / span) * 10) / 10;
        // The mock has no snapshot history, so the actual line is only drawn for today.
        const remaining = date === today ? committed - completed : null;
        burndown.push({ date, remainingPoints: remaining, ideal, projected: date > today });
      }
    }

    const loads = new Map<string, MemberLoad>();
    for (const t of tasks) {
      if (!t.assigneeId) continue;
      const emp = db.employees.find((e) => e.id === t.assigneeId);
      const u = emp && db.users.find((x) => x.id === emp.userId);
      const row = loads.get(t.assigneeId) ?? {
        employeeId: t.assigneeId, name: u ? `${u.firstName} ${u.lastName}` : "Unassigned",
        points: 0, tasks: 0, donePoints: 0,
      };
      row.points += t.storyPoints ?? 0;
      row.tasks += 1;
      if (t.status === "DONE") row.donePoints += t.storyPoints ?? 0;
      loads.set(t.assigneeId, row);
    }

    const daysTotal = burndown.length;
    return {
      sprintId, name: sprint.name, goal: sprint.goal, status: sprint.status,
      startDate: start, endDate: end, capacityPoints: sprint.capacityPoints ?? null,
      committedPoints: committed, completedPoints: completed, remainingPoints: committed - completed,
      totalTasks: tasks.length, doneTasks: done, unestimatedTasks: unestimated,
      daysTotal,
      daysElapsed: burndown.filter((p) => !p.projected).length,
      burndown,
      byAssignee: [...loads.values()].sort((a, b) => b.points - a.points),
    };
  },
  async velocity(accessToken: string | null, projectId: string): Promise<Velocity> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const sprints = db.sprints
      .filter((s) => s.projectId === projectId && s.companyId === user.companyId && s.status === "COMPLETED")
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt));

    const rows = sprints.map((s) => {
      let committed = 0, completed = 0;
      for (const t of db.tasks.filter((t) => t.sprintId === s.id)) {
        const points = t.storyPoints ?? 0;
        committed += points;
        if (t.status === "DONE") completed += points;
      }
      return { sprintId: s.id, name: s.name, endDate: s.endDate, committedPoints: committed, completedPoints: completed };
    });
    const average = rows.length
      ? Math.round((rows.reduce((sum, r) => sum + r.completedPoints, 0) / rows.length) * 10) / 10
      : 0;
    return { sprints: rows, averageVelocity: average, suggestedCommitment: Math.round(average) };
  },

  // --- company feed ---
  async feed(accessToken: string | null): Promise<Post[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const myDept = db.employees.find((e) => e.userId === user.id)?.departmentId ?? null;
    const admin = user.role === "OWNER" || user.role === "ADMIN";
    return (mockPosts[user.companyId] ?? [])
      .filter((p) => canSeePost(p, user.id, myDept, admin))
      .map((p) => renderPost(db, p, user))
      .sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.createdAt.localeCompare(a.createdAt));
  },
  async createPost(accessToken: string | null, input: PostInput): Promise<Post> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    if (!input.body?.trim()) throw err(400, "VALIDATION_ERROR", "Write something first");
    const visibility = input.visibility ?? "COMPANY";
    if (visibility === "DEPARTMENT" && !input.departmentId) {
      throw err(400, "VALIDATION_ERROR", "Pick a team for a team-only post");
    }
    const row: MockPost = {
      id: crypto.randomUUID(), authorId: user.id, kind: input.kind ?? "UPDATE",
      body: input.body.trim(), visibility, departmentId: input.departmentId ?? null,
      pinned: false, reactions: [], comments: [], createdAt: new Date().toISOString(),
    };
    mockPosts[user.companyId] = [row, ...(mockPosts[user.companyId] ?? [])];
    return renderPost(db, row, user);
  },
  async deletePost(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const post = (mockPosts[user.companyId] ?? []).find((p) => p.id === id);
    if (!post) throw err(404, "NOT_FOUND", "Post not found");
    const admin = user.role === "OWNER" || user.role === "ADMIN";
    if (post.authorId !== user.id && !admin) throw err(403, "FORBIDDEN", "You can only delete your own posts");
    mockPosts[user.companyId] = (mockPosts[user.companyId] ?? []).filter((p) => p.id !== id);
  },
  async pinPost(accessToken: string | null, id: string, pinned: boolean): Promise<Post> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const post = (mockPosts[user.companyId] ?? []).find((p) => p.id === id);
    if (!post) throw err(404, "NOT_FOUND", "Post not found");
    post.pinned = pinned;
    return renderPost(db, post, user);
  },
  async reactToPost(accessToken: string | null, id: string, emoji: string): Promise<Post> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const post = (mockPosts[user.companyId] ?? []).find((p) => p.id === id);
    if (!post) throw err(404, "NOT_FOUND", "Post not found");
    const existing = post.reactions.findIndex((r) => r.userId === user.id && r.emoji === emoji);
    if (existing >= 0) post.reactions.splice(existing, 1);
    else post.reactions.push({ userId: user.id, emoji });
    return renderPost(db, post, user);
  },
  async commentOnPost(accessToken: string | null, id: string, body: string): Promise<Post> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const post = (mockPosts[user.companyId] ?? []).find((p) => p.id === id);
    if (!post) throw err(404, "NOT_FOUND", "Post not found");
    if (!body?.trim()) throw err(400, "VALIDATION_ERROR", "Write something first");
    post.comments.push({
      id: crypto.randomUUID(), authorId: user.id, body: body.trim(), createdAt: new Date().toISOString(),
    });
    return renderPost(db, post, user);
  },
  async deletePostComment(accessToken: string | null, commentId: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const admin = user.role === "OWNER" || user.role === "ADMIN";
    for (const post of mockPosts[user.companyId] ?? []) {
      const c = post.comments.find((x) => x.id === commentId);
      if (!c) continue;
      if (c.authorId !== user.id && !admin) throw err(403, "FORBIDDEN", "You can only delete your own comments");
      post.comments = post.comments.filter((x) => x.id !== commentId);
      return;
    }
    throw err(404, "NOT_FOUND", "Comment not found");
  },

  // --- expense claims ---
  async myExpenses(accessToken: string | null): Promise<ExpenseSummary> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const me = employeeForUser(db, user);
    return summarizeExpenses(db, (mockExpenses[user.companyId] ?? []).filter((c) => c.employeeId === me.id));
  },
  async allExpenses(accessToken: string | null): Promise<ExpenseSummary> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return summarizeExpenses(db, mockExpenses[user.companyId] ?? []);
  },
  async submitExpense(accessToken: string | null, input: ExpenseInput): Promise<ExpenseClaim> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const me = employeeForUser(db, user);
    if (!input.title?.trim()) throw err(400, "VALIDATION_ERROR", "What was the expense for?");
    if (!(input.amount > 0)) throw err(400, "VALIDATION_ERROR", "Amount must be more than zero");
    const spentOn = input.spentOn || todayIso();
    if (spentOn > todayIso()) throw err(400, "VALIDATION_ERROR", "You can't claim for a future date");

    const claim: ExpenseClaim = {
      id: crypto.randomUUID(), employeeId: me.id, employeeName: `${user.firstName} ${user.lastName}`,
      title: input.title.trim(), category: input.category ?? "OTHER", amount: input.amount,
      currency: input.currency ?? "INR", spentOn, description: input.description ?? null,
      receiptUrl: input.receiptUrl ?? null, status: "SUBMITTED", decisionNote: null,
      decidedAt: null, reimbursedAt: null, createdAt: new Date().toISOString(),
    };
    mockExpenses[user.companyId] = [claim, ...(mockExpenses[user.companyId] ?? [])];

    for (const approverId of approversFor(db, me)) {
      notify(approverId, user.id, "ANNOUNCEMENT",
        `${claim.employeeName} claimed ${claim.currency} ${claim.amount}`,
        `${claim.title} · ${claim.category.toLowerCase()}`, "/expenses", "EXPENSE_CLAIM", claim.id);
    }
    return claim;
  },
  async withdrawExpense(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const me = employeeForUser(db, user);
    const claim = (mockExpenses[user.companyId] ?? []).find((c) => c.id === id);
    if (!claim) throw err(404, "NOT_FOUND", "Claim not found");
    if (claim.employeeId !== me.id) throw err(403, "FORBIDDEN", "You can only withdraw your own claims");
    if (claim.status !== "SUBMITTED") throw err(409, "CONFLICT", "This claim has already been decided");
    mockExpenses[user.companyId] = (mockExpenses[user.companyId] ?? []).filter((c) => c.id !== id);
  },
  async decideExpense(accessToken: string | null, id: string, action: "approve" | "reject" | "reimburse", note?: string): Promise<ExpenseClaim> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const claim = (mockExpenses[user.companyId] ?? []).find((c) => c.id === id);
    if (!claim) throw err(404, "NOT_FOUND", "Claim not found");

    if (action === "reimburse") {
      if (claim.status !== "APPROVED") throw err(409, "CONFLICT", "Only an approved claim can be reimbursed");
      claim.status = "REIMBURSED";
      claim.reimbursedAt = new Date().toISOString();
    } else {
      if (claim.status !== "SUBMITTED") throw err(409, "CONFLICT", "This claim has already been decided");
      claim.status = action === "approve" ? "APPROVED" : "REJECTED";
      claim.decidedAt = new Date().toISOString();
      claim.decisionNote = note?.trim() || null;
    }

    const claimant = db.employees.find((e) => e.id === claim.employeeId);
    const label = action === "reimburse" ? "reimbursed" : action === "approve" ? "approved" : "declined";
    notify(claimant?.userId, user.id, "ANNOUNCEMENT", `Your expense claim was ${label}`,
      `${claim.title} · ${claim.currency} ${claim.amount}`, "/me/expenses", "EXPENSE_CLAIM", claim.id);
    return claim;
  },

  // --- inbox / notifications (feedback D4/D5) ---
  async notifications(accessToken: string | null, unreadOnly: boolean): Promise<AppNotification[]> {
    await delay();
    const user = requireSession(load(), accessToken);
    const mine = (mockNotifications[user.id] ?? []).filter((n) => !unreadOnly || !n.read);
    return mine.slice();
  },
  async unreadCount(accessToken: string | null): Promise<{ count: number }> {
    await delay();
    const user = requireSession(load(), accessToken);
    return { count: (mockNotifications[user.id] ?? []).filter((n) => !n.read).length };
  },
  async markNotificationRead(accessToken: string | null, id: string): Promise<AppNotification> {
    await delay();
    const user = requireSession(load(), accessToken);
    const n = (mockNotifications[user.id] ?? []).find((x) => x.id === id);
    if (!n) throw err(404, "NOT_FOUND", "Notification not found");
    n.read = true;
    return n;
  },
  async markAllNotificationsRead(accessToken: string | null): Promise<{ marked: number }> {
    await delay();
    const user = requireSession(load(), accessToken);
    const unread = (mockNotifications[user.id] ?? []).filter((n) => !n.read);
    unread.forEach((n) => { n.read = true; });
    return { marked: unread.length };
  },

  // --- holiday calendar ---
  async holidays(accessToken: string | null, year?: number): Promise<Holiday[]> {
    await delay();
    const user = requireSession(load(), accessToken);
    const all = (mockHolidays[user.companyId] ?? []).slice().sort((a, b) => a.date.localeCompare(b.date));
    return (year ? all.filter((h) => h.date.startsWith(String(year))) : all).map(withDaysAway);
  },
  async upcomingHolidays(accessToken: string | null, limit: number): Promise<Holiday[]> {
    await delay();
    const user = requireSession(load(), accessToken);
    const today = todayIso();
    return (mockHolidays[user.companyId] ?? [])
      .filter((h) => h.date >= today)
      .sort((a, b) => a.date.localeCompare(b.date))
      .slice(0, limit)
      .map(withDaysAway);
  },
  async createHoliday(accessToken: string | null, input: { name: string; date: string; optional?: boolean; note?: string }): Promise<Holiday> {
    await delay();
    const user = requireSession(load(), accessToken);
    requireAdmin(user);
    const h: Holiday = {
      id: crypto.randomUUID(), name: input.name, date: input.date,
      optional: input.optional ?? false, note: input.note ?? null,
      weekday: weekdayOf(input.date), daysAway: 0,
    };
    mockHolidays[user.companyId] = [...(mockHolidays[user.companyId] ?? []), h];
    return withDaysAway(h);
  },
  async deleteHoliday(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const user = requireSession(load(), accessToken);
    requireAdmin(user);
    mockHolidays[user.companyId] = (mockHolidays[user.companyId] ?? []).filter((h) => h.id !== id);
  },
  async seedDefaultHolidays(accessToken: string | null): Promise<Holiday[]> {
    await delay();
    const user = requireSession(load(), accessToken);
    requireAdmin(user);
    if (!(mockHolidays[user.companyId] ?? []).length) {
      const year = new Date().getFullYear();
      const defaults: [string, string][] = [
        ["New Year's Day", `${year}-01-01`],
        ["Republic Day", `${year}-01-26`],
        ["Labour Day", `${year}-05-01`],
        ["Independence Day", `${year}-08-15`],
        ["Gandhi Jayanti", `${year}-10-02`],
        ["Christmas Day", `${year}-12-25`],
      ];
      mockHolidays[user.companyId] = defaults.map(([name, date]) => ({
        id: crypto.randomUUID(), name, date, optional: false, note: null,
        weekday: weekdayOf(date), daysAway: 0,
      }));
    }
    return this.holidays(accessToken);
  },

  // --- attendance: the daily record (feedback C.4) ---
  async attendanceToday(accessToken: string | null): Promise<AttendanceEntry> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const employeeId = myEmployeeId(db, user);
    return resolveAttendance(db, employeeId, todayIso());
  },
  async checkIn(accessToken: string | null): Promise<AttendanceEntry> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const employeeId = myEmployeeId(db, user);
    const row = attendanceRow(employeeId, todayIso(), "PRESENT");
    if (!row.checkIn) {
      row.checkIn = nowTime();
      if (row.status === "ABSENT") row.status = "PRESENT";
    }
    return resolveAttendance(db, employeeId, todayIso());
  },
  async checkOut(accessToken: string | null): Promise<AttendanceEntry> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const employeeId = myEmployeeId(db, user);
    const existing = mockAttendance[`${employeeId}|${todayIso()}`];
    if (!existing) throw err(400, "VALIDATION_ERROR", "Check in first");
    existing.checkOut = nowTime();
    return resolveAttendance(db, employeeId, todayIso());
  },
  async resetToday(accessToken: string | null): Promise<AttendanceEntry> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const employeeId = myEmployeeId(db, user);
    delete mockAttendance[`${employeeId}|${todayIso()}`];
    return resolveAttendance(db, employeeId, todayIso());
  },
  async raiseRegularization(accessToken: string | null, input: RegularizationInput): Promise<Regularization> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const employeeId = myEmployeeId(db, user);
    const r: Regularization = {
      id: crypto.randomUUID(), employeeId, employeeName: `${user.firstName} ${user.lastName}`.trim(),
      date: input.date, checkIn: input.checkIn || null, checkOut: input.checkOut || null,
      status: "PENDING", reason: input.reason || null, decisionNote: null, decidedAt: null,
      createdAt: new Date().toISOString(),
    };
    (mockRegs[user.companyId] ??= []).unshift(r);
    return r;
  },
  async myRegularizations(accessToken: string | null): Promise<Regularization[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const employeeId = myEmployeeId(db, user);
    return (mockRegs[user.companyId] ?? []).filter((r) => r.employeeId === employeeId);
  },
  async pendingRegularizations(accessToken: string | null): Promise<Regularization[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const list = (mockRegs[user.companyId] ?? []).filter((r) => r.status === "PENDING");
    if (isHelpdeskAgent(user)) return list;
    const myEmp = db.employees.find((e) => e.userId === user.id);
    return list.filter((r) => db.employees.find((e) => e.id === r.employeeId)?.managerId === myEmp?.id);
  },
  async decideRegularization(accessToken: string | null, id: string, approve: boolean, note?: string): Promise<Regularization> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const r = (mockRegs[user.companyId] ?? []).find((x) => x.id === id);
    if (!r) throw err(404, "NOT_FOUND", "Regularization not found");
    r.status = approve ? "APPROVED" : "REJECTED";
    r.decisionNote = note || null;
    r.decidedAt = new Date().toISOString();
    if (approve) {
      const row = attendanceRow(r.employeeId, r.date, "PRESENT");
      row.status = "PRESENT";
      if (r.checkIn) row.checkIn = r.checkIn;
      if (r.checkOut) row.checkOut = r.checkOut;
    }
    return r;
  },
  async myAttendance(accessToken: string | null, month?: string): Promise<AttendanceMonth> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    return buildAttendanceMonth(db, myEmployeeId(db, user), month);
  },
  async employeeAttendance(accessToken: string | null, employeeId: string, month?: string): Promise<AttendanceMonth> {
    await delay();
    const db = load();
    requireAdmin(requireSession(db, accessToken));
    return buildAttendanceMonth(db, employeeId, month);
  },
  async attendanceDay(accessToken: string | null, date?: string): Promise<AttendanceDay> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const on = date || todayIso();
    const entries = db.employees
      .filter((e) => e.companyId === user.companyId)
      .map((e) => resolveAttendance(db, e.id, on))
      .sort((a, b) => a.employeeName.localeCompare(b.employeeName));
    const count = (p: (s: AttendanceStatus) => boolean) =>
      entries.filter((e) => e.status && p(e.status)).length;
    return {
      date: on,
      headcount: entries.length,
      present: count((s) => s === "PRESENT" || s === "WORK_FROM_HOME" || s === "HALF_DAY"),
      onLeave: count((s) => s === "ON_LEAVE"),
      absent: count((s) => s === "ABSENT"),
      unmarked: entries.filter((e) => e.status === null).length,
      entries,
    };
  },
  async markAttendance(accessToken: string | null, input: MarkAttendanceInput): Promise<AttendanceEntry> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const date = input.date || todayIso();
    if (date > todayIso()) throw err(400, "VALIDATION_ERROR", "Attendance can't be marked for a future date");
    const row = attendanceRow(input.employeeId, date, input.status);
    row.status = input.status;
    if (input.checkIn !== undefined) row.checkIn = input.checkIn || null;
    if (input.checkOut !== undefined) row.checkOut = input.checkOut || null;
    if (input.note !== undefined) row.note = input.note || null;
    return resolveAttendance(db, input.employeeId, date);
  },

  // --- documents: templates + generated letters (feedback D2/D3) ---
  async docTemplates(accessToken: string | null): Promise<DocumentTemplate[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    if (!mockTemplates[user.companyId]) {
      // Same first-open path as the real backend: seed the starter library once.
      mockTemplates[user.companyId] = STARTER_TEMPLATES.map((s) => ({
        id: crypto.randomUUID(), name: s.name, kind: s.kind, description: s.description,
        body: s.body, builtIn: true, placeholders: placeholdersIn(s.body),
        updatedAt: new Date().toISOString(),
      }));
    }
    return mockTemplates[user.companyId].slice().sort((a, b) => a.name.localeCompare(b.name));
  },
  async createDocTemplate(accessToken: string | null, input: { name: string; kind: string; description?: string; body: string }): Promise<DocumentTemplate> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const t: DocumentTemplate = {
      id: crypto.randomUUID(), name: input.name, kind: (input.kind as DocumentTemplate["kind"]) ?? "CUSTOM",
      description: input.description ?? null, body: input.body, builtIn: false,
      placeholders: placeholdersIn(input.body), updatedAt: new Date().toISOString(),
    };
    mockTemplates[user.companyId] = [...(mockTemplates[user.companyId] ?? []), t];
    return t;
  },
  async updateDocTemplate(accessToken: string | null, id: string, patch: Partial<DocumentTemplate>): Promise<DocumentTemplate> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const t = (mockTemplates[user.companyId] ?? []).find((x) => x.id === id);
    if (!t) throw err(404, "NOT_FOUND", "Template not found");
    Object.assign(t, patch);
    t.placeholders = placeholdersIn(t.body);
    t.updatedAt = new Date().toISOString();
    return t;
  },
  async deleteDocTemplate(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    mockTemplates[user.companyId] = (mockTemplates[user.companyId] ?? []).filter((x) => x.id !== id);
  },
  async mergeFields(accessToken: string | null): Promise<MergeField[]> {
    await delay();
    requireAdmin(requireSession(load(), accessToken));
    return MERGE_FIELDS.slice();
  },
  async previewDoc(accessToken: string | null, input: GenerateDocInput): Promise<DocumentPreview> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const t = (mockTemplates[user.companyId] ?? []).find((x) => x.id === input.templateId);
    if (!t) throw err(404, "NOT_FOUND", "Template not found");
    const values = resolveMergeValues(db, user, input);
    const missing = placeholdersIn(t.body).filter((k) => !values[k] || !values[k].trim());
    return { title: docTitle(t.name, input, values), body: renderTemplate(t.body, values), values, missing };
  },
  async generateDoc(accessToken: string | null, input: GenerateDocInput): Promise<GeneratedDoc> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const t = (mockTemplates[user.companyId] ?? []).find((x) => x.id === input.templateId);
    if (!t) throw err(404, "NOT_FOUND", "Template not found");
    const values = resolveMergeValues(db, user, input);
    const doc: GeneratedDoc = {
      id: crypto.randomUUID(), title: docTitle(t.name, input, values), kind: t.kind,
      employeeId: input.employeeId ?? null, employeeName: values["employee.fullName"] ?? null,
      templateId: t.id, body: renderTemplate(t.body, values),   // frozen at issue time
      generatedBy: values["signatory.name"] ?? null, createdAt: new Date().toISOString(),
    };
    mockDocs[user.companyId] = [doc, ...(mockDocs[user.companyId] ?? [])];
    return doc;
  },
  async documents(accessToken: string | null, employeeId?: string): Promise<GeneratedDoc[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const all = mockDocs[user.companyId] ?? [];
    return employeeId ? all.filter((d) => d.employeeId === employeeId) : all.slice();
  },
  async document(accessToken: string | null, id: string): Promise<GeneratedDoc> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const doc = (mockDocs[user.companyId] ?? []).find((d) => d.id === id);
    if (!doc) throw err(404, "NOT_FOUND", "Document not found");
    return doc;
  },
  async deleteDocument(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    mockDocs[user.companyId] = (mockDocs[user.companyId] ?? []).filter((d) => d.id !== id);
  },

  async teamOverview(accessToken: string | null): Promise<TeamOverview> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const cid = user.companyId;
    const headcount = db.users.filter((u) => u.companyId === cid && u.status === "ACTIVE").length;
    const nameOf = (employeeId: string) => {
      const emp = db.employees.find((e) => e.id === employeeId);
      const u = emp && db.users.find((x) => x.id === emp.userId);
      return u ? `${u.firstName} ${u.lastName}` : "Someone";
    };
    const today = new Date().toISOString().slice(0, 10);
    const first = today.slice(0, 8) + "01";
    const lastDay = new Date(Number(today.slice(0, 4)), Number(today.slice(5, 7)), 0).getDate();
    const last = `${today.slice(0, 8)}${String(lastDay).padStart(2, "0")}`;
    const leaves = db.leave.filter((l) => l.companyId === cid);
    const outToday = leaves
      .filter((l) => l.status === "APPROVED" && l.startDate <= today && l.endDate >= today)
      .map((l) => ({ employeeName: nameOf(l.employeeId), type: l.type, reason: l.reason, startDate: l.startDate, endDate: l.endDate }));
    const monthLeaves = leaves
      .filter((l) => (l.status === "APPROVED" || l.status === "PENDING") && l.startDate <= last && l.endDate >= first)
      .map((l) => ({ employeeName: nameOf(l.employeeId), type: l.type, status: l.status, startDate: l.startDate, endDate: l.endDate }));
    return {
      headcount,
      onLeaveToday: outToday.length,
      presentToday: Math.max(0, headcount - outToday.length),
      unmarkedToday: db.employees.filter(
        (e) => e.companyId === cid && !mockAttendance[`${e.id}|${todayIso()}`],
      ).length,
      outToday,
      monthLeaves,
    };
  },

  // --- analytics (mirrors AnalyticsService) ---------------------------------
  async analyticsOverview(accessToken: string | null): Promise<AnalyticsOverview> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const cid = user.companyId;
    const title = (s: string) => { const t = s.replace(/_/g, " ").toLowerCase(); return t.charAt(0).toUpperCase() + t.slice(1); };
    const today = new Date();
    const year = today.getFullYear();

    const employees = db.employees.filter((e) => e.companyId === cid && e.employmentStatus === "ACTIVE");

    // People — by department
    const deptName = (id: string | null) => db.departments.find((d) => d.id === id)?.name ?? "Unassigned";
    const byDept = new Map<string, number>();
    employees.forEach((e) => byDept.set(deptName(e.departmentId), (byDept.get(deptName(e.departmentId)) ?? 0) + 1));

    // Headcount growth (12 months, cumulative by startDate)
    const headcountGrowth = Array.from({ length: 12 }, (_, k) => {
      const d = new Date(today.getFullYear(), today.getMonth() - (11 - k) + 1, 0); // month-end
      const label = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
      const count = employees.filter((e) => e.startDate && e.startDate <= d.toISOString().slice(0, 10)).length;
      return { label, value: count };
    });
    let newJoiners = 0, tenureSum = 0, tenured = 0;
    employees.forEach((e) => {
      if (!e.startDate) return;
      if (Number(e.startDate.slice(0, 4)) === year) newJoiners++;
      const start = new Date(e.startDate);
      tenureSum += (today.getFullYear() - start.getFullYear()) * 12 + (today.getMonth() - start.getMonth());
      tenured++;
    });

    const ratingDistribution = [1, 2, 3, 4, 5].map((r) => ({
      label: `${r}★`, value: employees.filter((e) => e.rating === r).length,
    }));

    // Leave by type (approved days this year) + on leave today
    const leaves = db.leave.filter((l) => l.companyId === cid);
    const todayIsoStr = today.toISOString().slice(0, 10);
    const leaveDays = new Map<string, number>();
    let onLeaveToday = 0;
    leaves.forEach((l) => {
      if (l.status === "APPROVED") {
        if (l.startDate.slice(0, 4) === String(year) || l.endDate.slice(0, 4) === String(year)) {
          leaveDays.set(l.type, (leaveDays.get(l.type) ?? 0) + l.days);
        }
        if (l.startDate <= todayIsoStr && l.endDate >= todayIsoStr) onLeaveToday++;
      }
    });
    const leaveByType = ["VACATION", "SICK", "PERSONAL", "UNPAID"].map((t) => ({ label: title(t), value: leaveDays.get(t) ?? 0 }));

    // Goals across the company
    const empIds = new Set(employees.map((e) => e.id));
    const goals = Object.entries(mockGoals).filter(([eid]) => empIds.has(eid)).flatMap(([, gs]) => gs);
    const goalsOpen = goals.filter((g) => g.status === "OPEN").length;
    const goalsAchieved = goals.filter((g) => g.status === "ACHIEVED").length;
    const goalsMissed = goals.filter((g) => g.status === "MISSED").length;
    const avgGoalProgress = goals.length === 0 ? 0 : Math.round((goals.reduce((s, g) => s + g.progress, 0) / goals.length) * 10) / 10;

    // Work
    const tasks = db.tasks.filter((t) => t.companyId === cid);
    const tasksByStatus = ["TODO", "IN_PROGRESS", "DONE"].map((s) => ({ label: title(s), value: tasks.filter((t) => t.status === s).length }));
    const tasksByPriority = ["LOW", "MEDIUM", "HIGH", "URGENT"].map((p) => ({ label: title(p), value: tasks.filter((t) => t.priority === p).length }));
    const tickets = db.tickets.filter((t) => t.companyId === cid);
    const ticketsByStatus = ["OPEN", "PENDING", "RESOLVED", "CLOSED"].map((s) => ({ label: title(s), value: tickets.filter((t) => t.status === s).length }));

    const sprints = db.sprints.filter((s) => s.companyId === cid);
    const activeSprintRow = sprints.find((s) => s.status === "ACTIVE");
    let activeSprint: AnalyticsOverview["work"]["activeSprint"] = null;
    if (activeSprintRow) {
      const st = tasks.filter((t) => t.sprintId === activeSprintRow.id);
      let committed = 0, done = 0, unestimated = 0;
      st.forEach((t) => {
        const pts = t.storyPoints ?? 0;
        if (t.storyPoints == null) unestimated++;
        committed += pts;
        if (t.status === "DONE") done += pts;
      });
      activeSprint = { name: activeSprintRow.name, committed, done, remaining: committed - done, unestimated };
    }
    const velocity = sprints
      .filter((s) => s.status === "COMPLETED")
      .sort((a, b) => (a.startDate ?? "").localeCompare(b.startDate ?? ""))
      .map((s) => ({
        label: (s.name.split("—")[0] ?? s.name).trim(),
        value: tasks.filter((t) => t.sprintId === s.id && t.status === "DONE" && t.storyPoints != null)
          .reduce((sum, t) => sum + (t.storyPoints ?? 0), 0),
      }));

    // Finance
    const claims = mockExpenses[cid] ?? [];
    let pending = 0, awaiting = 0, reimbursed = 0, currency = "INR";
    const byCategory = new Map<string, number>();
    claims.forEach((c) => {
      currency = c.currency;
      if (c.status === "SUBMITTED") pending += c.amount;
      else if (c.status === "APPROVED") awaiting += c.amount;
      else if (c.status === "REIMBURSED" && c.reimbursedAt && new Date(c.reimbursedAt).getFullYear() === year) reimbursed += c.amount;
      if (c.status !== "REJECTED") byCategory.set(title(c.category), (byCategory.get(title(c.category)) ?? 0) + c.amount);
    });

    return {
      people: {
        headcount: employees.length, newJoinersThisYear: newJoiners,
        avgTenureMonths: tenured === 0 ? 0 : Math.round((tenureSum / tenured) * 10) / 10,
        onLeaveToday, goalsOpen, goalsAchieved, goalsMissed, avgGoalProgress,
        byDepartment: [...byDept].map(([label, value]) => ({ label, value })),
        headcountGrowth, ratingDistribution, leaveByType,
      },
      work: {
        projects: db.projects.filter((p) => p.companyId === cid).length,
        tasksByStatus, tasksByPriority, ticketsByStatus, activeSprint, velocity,
      },
      finance: {
        currency, pending: Math.round(pending * 100) / 100,
        awaitingReimbursement: Math.round(awaiting * 100) / 100,
        reimbursedThisYear: Math.round(reimbursed * 100) / 100,
        byCategory: [...byCategory].map(([label, value]) => ({ label, value })),
      },
    };
  },

  // --- billing (mirrors BillingService) -------------------------------------
  async billingOverview(accessToken: string | null): Promise<BillingOverview> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return buildBilling(db, user.companyId);
  },
  async activateSubscription(accessToken: string | null): Promise<BillingOverview> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const sub = mockSubscription(user.companyId);
    sub.status = "ACTIVE";
    return buildBilling(db, user.companyId);
  },
  async payInvoice(accessToken: string | null, month: string): Promise<BillingOverview> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const sub = mockSubscription(user.companyId);
    if (month > new Date().toISOString().slice(0, 7)) throw err(400, "VALIDATION_ERROR", "Can't pay a future month");
    if (!sub.paidThrough || month > sub.paidThrough) sub.paidThrough = month;
    if (sub.status !== "ACTIVE") sub.status = "ACTIVE";
    return buildBilling(db, user.companyId);
  },

  // --- recruitment / ATS (mirrors RecruitService) ---------------------------
  async jobs(accessToken: string | null): Promise<JobOpening[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return (mockJobs[user.companyId] ?? []).map((j) => renderJob(db, j)).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  },
  async job(accessToken: string | null, id: string): Promise<JobOpening> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const j = (mockJobs[user.companyId] ?? []).find((x) => x.id === id);
    if (!j) throw err(404, "NOT_FOUND", "Job opening not found");
    return renderJob(db, j);
  },
  async createJob(accessToken: string | null, input: JobOpeningInput): Promise<JobOpening> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const j: MockJob = {
      id: crypto.randomUUID(), companyId: user.companyId, title: input.title.trim(),
      departmentId: input.departmentId || null, location: input.location || null,
      employmentType: input.employmentType || null, description: input.description || null,
      positions: input.positions && input.positions > 0 ? input.positions : 1,
      status: input.status ?? "OPEN", createdAt: new Date().toISOString(),
    };
    mockJobs[user.companyId] = [j, ...(mockJobs[user.companyId] ?? [])];
    return renderJob(db, j);
  },
  async updateJob(accessToken: string | null, id: string, input: Partial<JobOpeningInput>): Promise<JobOpening> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const j = (mockJobs[user.companyId] ?? []).find((x) => x.id === id);
    if (!j) throw err(404, "NOT_FOUND", "Job opening not found");
    Object.assign(j, {
      title: input.title?.trim() ?? j.title,
      departmentId: input.departmentId !== undefined ? (input.departmentId || null) : j.departmentId,
      location: input.location !== undefined ? (input.location || null) : j.location,
      employmentType: input.employmentType !== undefined ? (input.employmentType || null) : j.employmentType,
      description: input.description !== undefined ? (input.description || null) : j.description,
      positions: input.positions && input.positions > 0 ? input.positions : j.positions,
      status: input.status ?? j.status,
    });
    return renderJob(db, j);
  },
  async deleteJob(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    mockJobs[user.companyId] = (mockJobs[user.companyId] ?? []).filter((x) => x.id !== id);
    delete mockCandidates[id];
  },
  async candidates(accessToken: string | null, jobId: string): Promise<Candidate[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return (mockCandidates[jobId] ?? []).slice();
  },
  async addCandidate(accessToken: string | null, jobId: string, input: CandidateInput): Promise<Candidate> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const c: Candidate = {
      id: crypto.randomUUID(), jobId, name: input.name.trim(), email: input.email || null,
      phone: input.phone || null, resumeUrl: input.resumeUrl || null, source: input.source || null,
      stage: input.stage ?? "APPLIED", rating: input.rating ?? null, notes: input.notes || null,
      createdAt: new Date().toISOString(),
    };
    mockCandidates[jobId] = [...(mockCandidates[jobId] ?? []), c];
    return c;
  },
  async updateCandidate(accessToken: string | null, id: string, input: Partial<CandidateInput>): Promise<Candidate> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const c = findCandidate(id);
    if (!c) throw err(404, "NOT_FOUND", "Candidate not found");
    Object.assign(c, {
      name: input.name?.trim() ?? c.name,
      email: input.email !== undefined ? (input.email || null) : c.email,
      phone: input.phone !== undefined ? (input.phone || null) : c.phone,
      resumeUrl: input.resumeUrl !== undefined ? (input.resumeUrl || null) : c.resumeUrl,
      source: input.source !== undefined ? (input.source || null) : c.source,
      stage: input.stage ?? c.stage,
      rating: input.rating !== undefined ? input.rating : c.rating,
      notes: input.notes !== undefined ? (input.notes || null) : c.notes,
    });
    return c;
  },
  async moveCandidate(accessToken: string | null, id: string, stage: string): Promise<Candidate> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const c = findCandidate(id);
    if (!c) throw err(404, "NOT_FOUND", "Candidate not found");
    c.stage = stage as Candidate["stage"];
    return c;
  },
  async deleteCandidate(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    for (const jid of Object.keys(mockCandidates)) {
      mockCandidates[jid] = (mockCandidates[jid] ?? []).filter((x) => x.id !== id);
    }
  },

  // --- HR helpdesk (mirrors HelpdeskService) --------------------------------
  async raiseTicket(accessToken: string | null, input: RaiseTicketInput): Promise<HelpdeskTicket> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const now = new Date().toISOString();
    const t: HelpdeskTicket = {
      id: crypto.randomUUID(), category: input.category, subject: input.subject.trim(),
      description: input.description?.trim() || null, priority: input.priority ?? "MEDIUM", status: "OPEN",
      raisedById: user.id, raisedByName: `${user.firstName} ${user.lastName}`.trim(),
      assigneeId: null, assigneeName: null, commentCount: 0, createdAt: now, updatedAt: now, resolvedAt: null,
    };
    (mockTickets[user.companyId] ??= []).unshift(t);
    return t;
  },
  async myTickets(accessToken: string | null): Promise<HelpdeskTicket[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    return (mockTickets[user.companyId] ?? []).filter((t) => t.raisedById === user.id);
  },
  async helpdeskQueue(accessToken: string | null, status?: string): Promise<HelpdeskTicket[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return (mockTickets[user.companyId] ?? []).filter((t) => !status || t.status === status);
  },
  async helpdeskTicket(accessToken: string | null, id: string): Promise<HelpdeskTicket> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const t = (mockTickets[user.companyId] ?? []).find((x) => x.id === id);
    if (!t) throw err(404, "NOT_FOUND", "Ticket not found");
    if (t.raisedById !== user.id && !isHelpdeskAgent(user)) throw err(403, "FORBIDDEN", "You can't view this ticket");
    return t;
  },
  async helpdeskComments(accessToken: string | null, id: string): Promise<HelpdeskComment[]> {
    await delay();
    const db = load();
    requireSession(db, accessToken);
    return (mockComments[id] ?? []).slice();
  },
  async commentOnTicket(accessToken: string | null, id: string, body: string): Promise<HelpdeskComment> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const c: HelpdeskComment = {
      id: crypto.randomUUID(), authorId: user.id, authorName: `${user.firstName} ${user.lastName}`.trim(),
      body: body.trim(), createdAt: new Date().toISOString(),
    };
    (mockComments[id] ??= []).push(c);
    const t = (mockTickets[user.companyId] ?? []).find((x) => x.id === id);
    if (t) t.commentCount = (mockComments[id] ?? []).length;
    return c;
  },
  async updateHelpdeskTicket(accessToken: string | null, id: string, input: UpdateTicketInput): Promise<HelpdeskTicket> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const t = (mockTickets[user.companyId] ?? []).find((x) => x.id === id);
    if (!t) throw err(404, "NOT_FOUND", "Ticket not found");
    if (input.category) t.category = input.category;
    if (input.priority) t.priority = input.priority;
    if (input.assigneeId !== undefined) {
      t.assigneeId = input.assigneeId || null;
      const a = db.users.find((u) => u.id === input.assigneeId);
      t.assigneeName = a ? `${a.firstName} ${a.lastName}`.trim() : null;
    }
    if (input.status) {
      t.status = input.status;
      t.resolvedAt = input.status === "RESOLVED" || input.status === "CLOSED" ? new Date().toISOString() : null;
    }
    t.updatedAt = new Date().toISOString();
    return t;
  },

  // --- shift scheduling / rostering (mirrors ShiftService) ------------------
  async shifts(accessToken: string | null): Promise<Shift[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return (mockShifts[user.companyId] ?? []).slice().sort((a, b) => a.startTime.localeCompare(b.startTime));
  },
  async createShift(accessToken: string | null, input: ShiftInput): Promise<Shift> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const s: Shift = {
      id: crypto.randomUUID(), name: input.name.trim(),
      startTime: input.startTime, endTime: input.endTime, color: input.color || null,
    };
    mockShifts[user.companyId] = [...(mockShifts[user.companyId] ?? []), s];
    return s;
  },
  async updateShift(accessToken: string | null, id: string, input: Partial<ShiftInput>): Promise<Shift> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const s = (mockShifts[user.companyId] ?? []).find((x) => x.id === id);
    if (!s) throw err(404, "NOT_FOUND", "Shift not found");
    Object.assign(s, {
      name: input.name?.trim() ?? s.name,
      startTime: input.startTime ?? s.startTime,
      endTime: input.endTime ?? s.endTime,
      color: input.color !== undefined ? (input.color || null) : s.color,
    });
    return s;
  },
  async deleteShift(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    mockShifts[user.companyId] = (mockShifts[user.companyId] ?? []).filter((x) => x.id !== id);
    mockAssignments[user.companyId] = (mockAssignments[user.companyId] ?? []).filter((a) => a.shiftId !== id);
  },
  async roster(accessToken: string | null, weekStart?: string): Promise<Roster> {
    const employeesList = await this.listEmployees(accessToken);
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const start = mondayOf(weekStart ? new Date(weekStart) : new Date());
    const days = Array.from({ length: 7 }, (_, i) => isoDate(addDays(start, i)));
    const from = days[0], to = days[6];
    const shifts = (mockShifts[user.companyId] ?? []).slice().sort((a, b) => a.startTime.localeCompare(b.startTime));
    const assignments = (mockAssignments[user.companyId] ?? []).filter((a) => a.onDate >= from && a.onDate <= to);
    return {
      weekStart: from, days, shifts, assignments,
      employees: employeesList.map((e) => ({
        employeeId: e.id, name: `${e.firstName} ${e.lastName}`.trim(), jobTitle: e.jobTitle ?? null,
      })),
    };
  },
  async assignShift(accessToken: string | null, employeeId: string, onDate: string, shiftId: string): Promise<RosterEntry> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    if (!(mockShifts[user.companyId] ?? []).some((s) => s.id === shiftId)) throw err(404, "NOT_FOUND", "Shift not found");
    const list = (mockAssignments[user.companyId] ??= []);
    const existing = list.find((a) => a.employeeId === employeeId && a.onDate === onDate);
    if (existing) { existing.shiftId = shiftId; return existing; }
    const entry: RosterEntry = { id: crypto.randomUUID(), employeeId, shiftId, onDate };
    list.push(entry);
    return entry;
  },
  async unassignShift(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    mockAssignments[user.companyId] = (mockAssignments[user.companyId] ?? []).filter((a) => a.id !== id);
  },

  // --- payslip template (mirrors PayslipTemplateService) --------------------
  async payslipTemplate(accessToken: string | null): Promise<PayslipComponent[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return mockPayslipTemplate(user.companyId);
  },
  async savePayslipTemplate(accessToken: string | null, components: PayslipComponent[]): Promise<PayslipComponent[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    validatePayslipTemplate(components);
    mockTemplates2[user.companyId] = components.map((c, i) => ({ ...c, sortOrder: i }));
    return mockTemplates2[user.companyId];
  },

  async getSettings(accessToken: string | null): Promise<CompanySettings> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    return db.settings.find((s) => s.companyId === user.companyId)!;
  },

  async updateSettings(
    accessToken: string | null,
    patch: { timezone: string; locale: string; currency: string; legalName?: string; address?: string; logoUrl?: string },
  ): Promise<CompanySettings> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const settings = db.settings.find((s) => s.companyId === user.companyId)!;
    settings.timezone = patch.timezone;
    settings.locale = patch.locale;
    settings.currency = patch.currency;
    settings.legalName = patch.legalName && patch.legalName.trim() ? patch.legalName.trim() : null;
    settings.address = patch.address && patch.address.trim() ? patch.address.trim() : null;
    settings.logoUrl = patch.logoUrl && patch.logoUrl.length > 0 ? patch.logoUrl : null;
    save(db);
    return settings;
  },

  async listMembers(accessToken: string | null): Promise<Member[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return db.users
      .filter((u) => u.companyId === user.companyId)
      .map((u) => ({
        id: u.id,
        email: u.email,
        firstName: u.firstName,
        lastName: u.lastName,
        role: u.role,
        status: u.status,
      }));
  },

  async listInvitations(accessToken: string | null): Promise<Invitation[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return db.invitations.filter(
      (i) => i.status === "PENDING" && companyOfInvite(db, i.id) === user.companyId,
    );
  },

  async createInvitation(
    accessToken: string | null,
    email: string,
    role: "ADMIN" | "HR" | "MANAGER" | "MEMBER",
  ): Promise<Invitation> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const normalized = email.trim().toLowerCase();
    if (db.users.some((u) => u.companyId === user.companyId && u.email === normalized)) {
      throw err(409, "CONFLICT", "That person is already a member", { email: "already a member" });
    }
    const existing = db.invitations.find(
      (i) => i.email === normalized && i.status === "PENDING" && companyOfInvite(db, i.id) === user.companyId,
    );
    if (existing) {
      throw err(409, "CONFLICT", "An invitation is already pending for that email", {
        email: "already invited",
      });
    }
    const token: Token = {
      token: uuid(),
      companyId: user.companyId,
      email: normalized,
      role,
      kind: "invite",
      invitedByEmail: user.email,
      expiresAt: Date.now() + 7 * 24 * 3600 * 1000,
      consumed: false,
      createdAt: Date.now(),
    };
    const invitation: Invitation = {
      id: token.token,
      email: normalized,
      role,
      status: "PENDING",
      invitedByEmail: user.email,
      createdAt: new Date().toISOString(),
      expiresAt: new Date(token.expiresAt).toISOString(),
    };
    db.tokens.push(token);
    db.invitations.push(invitation);
    pushMail(db, normalized, `You're invited to join ${nameOf(db, user.companyId)} on Calyvora`, `/accept-invite?token=${token.token}`);
    save(db);
    return invitation;
  },

  async revokeInvitation(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const invitation = db.invitations.find((i) => i.id === id);
    if (!invitation || companyOfInvite(db, id) !== user.companyId) {
      throw err(404, "NOT_FOUND", "Invitation not found");
    }
    invitation.status = "REVOKED";
    const token = db.tokens.find((t) => t.token === id);
    if (token) token.consumed = true;
    save(db);
  },

  async invitationPreview(rawToken: string): Promise<{ email: string; companyName: string; role: Role }> {
    await delay();
    const db = load();
    const token = db.tokens.find((t) => t.token === rawToken && t.kind === "invite");
    if (!token) throw err(400, "VALIDATION_ERROR", "This invitation link is invalid");
    if (token.consumed) throw err(410, "TOKEN_EXPIRED", "This invitation has already been used");
    if (token.expiresAt < Date.now()) throw err(410, "TOKEN_EXPIRED", "This invitation has expired");
    return { email: token.email!, companyName: nameOf(db, token.companyId!), role: token.role! };
  },

  async acceptInvitation(input: {
    token: string;
    firstName: string;
    lastName: string;
    password: string;
  }): Promise<void> {
    await delay();
    const db = load();
    const token = db.tokens.find((t) => t.token === input.token && t.kind === "invite");
    if (!token) throw err(400, "VALIDATION_ERROR", "This invitation link is invalid");
    if (token.consumed) throw err(410, "TOKEN_EXPIRED", "This invitation has already been used");
    if (token.expiresAt < Date.now()) throw err(410, "TOKEN_EXPIRED", "This invitation has expired");
    const user: User = {
      id: uuid(),
      companyId: token.companyId!,
      email: token.email!,
      password: input.password,
      firstName: input.firstName.trim(),
      lastName: input.lastName.trim(),
      role: token.role!,
      status: "ACTIVE",
    };
    db.users.push(user);
    token.consumed = true;
    const invitation = db.invitations.find((i) => i.id === token.token);
    if (invitation) invitation.status = "ACCEPTED";
    save(db);
  },

  // --- People OS (employees) ---
  async employeeWork(accessToken: string | null, employeeId: string): Promise<WorkItem[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const today = new Date().toISOString().slice(0, 10);
    const projects = db.projects.filter((p) => p.companyId === user.companyId);
    return db.tasks
      .filter((t) => t.companyId === user.companyId && t.assigneeId === employeeId && t.status !== "DONE")
      .map((t) => {
        const p = projects.find((pr) => pr.id === t.projectId);
        return {
          ref: p ? `${p.key}-${t.number}` : `#${t.number}`, title: t.title, status: t.status, priority: t.priority,
          projectId: t.projectId, projectName: p?.name ?? null, dueDate: t.dueDate,
          overdue: !!t.dueDate && t.dueDate < today,
        };
      })
      .sort((a, b) => (a.dueDate ?? "9999").localeCompare(b.dueDate ?? "9999"));
  },

  async employeeGoals(accessToken: string | null, employeeId: string): Promise<Goal[]> {
    await delay();
    const db = load();
    requireSession(db, accessToken);
    return (mockGoals[employeeId] ?? []).slice();
  },
  async createGoal(accessToken: string | null, employeeId: string, input: { title: string; description?: string; targetDate?: string }): Promise<Goal> {
    await delay();
    const db = load();
    requireSession(db, accessToken);
    const goal: Goal = {
      id: crypto.randomUUID(), title: input.title, description: input.description || null,
      status: "OPEN", progress: 0, targetDate: input.targetDate || null, createdAt: new Date().toISOString(),
    };
    mockGoals[employeeId] = [goal, ...(mockGoals[employeeId] ?? [])];

    // Tell the employee their manager set them a goal (skipped when it's their own).
    const owner = db.employees.find((e) => e.id === employeeId);
    notify(owner?.userId, requireSession(db, accessToken).id, "GOAL_ASSIGNED",
      `New goal: ${goal.title}`, goal.targetDate ? `Target ${goal.targetDate}` : null,
      "/me/performance", "GOAL", goal.id);
    return goal;
  },
  async updateGoal(accessToken: string | null, employeeId: string, goalId: string, patch: Partial<Goal>): Promise<Goal> {
    await delay();
    const db = load();
    requireSession(db, accessToken);
    const list = mockGoals[employeeId] ?? [];
    const g = list.find((x) => x.id === goalId);
    if (!g) throw err(404, "NOT_FOUND", "Goal not found");
    Object.assign(g, patch);
    if (patch.progress === 100 && g.status === "OPEN") g.status = "ACHIEVED";
    if (patch.status === "ACHIEVED") g.progress = 100;
    return g;
  },
  async deleteGoal(accessToken: string | null, employeeId: string, goalId: string): Promise<void> {
    await delay();
    const db = load();
    requireSession(db, accessToken);
    mockGoals[employeeId] = (mockGoals[employeeId] ?? []).filter((x) => x.id !== goalId);
  },

  // --- performance reviews (mirrors PerformanceReviewService) ---------------
  async reviewCycles(accessToken: string | null): Promise<ReviewCycle[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return mockReviewCycles
      .filter((c) => c.companyId === user.companyId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map((c) => {
        const rs = mockReviews.filter((r) => r.cycleId === c.id);
        return {
          id: c.id, name: c.name, periodStart: c.periodStart, periodEnd: c.periodEnd, status: c.status,
          reviewCount: rs.length,
          submittedCount: rs.filter((r) => r.status === "SUBMITTED").length,
          approvedCount: rs.filter((r) => r.status === "APPROVED").length,
          createdAt: c.createdAt,
        };
      });
  },
  async createReviewCycle(accessToken: string | null, input: CreateCycleInput): Promise<ReviewCycle> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    if (input.periodEnd < input.periodStart) throw err(400, "VALIDATION_ERROR", "Period end must be on or after the start");
    const cycle: ReviewCycleRow = {
      id: crypto.randomUUID(), companyId: user.companyId, name: input.name.trim(),
      periodStart: input.periodStart, periodEnd: input.periodEnd, status: "OPEN",
      createdAt: new Date().toISOString(),
    };
    mockReviewCycles.push(cycle);
    // Fan out one review per active employee (auto-provision so everyone is covered).
    ensureProfiles(db, user.companyId, db.users.filter((u) => u.companyId === user.companyId));
    save(db);
    for (const emp of db.employees.filter((e) => e.companyId === user.companyId && e.employmentStatus === "ACTIVE")) {
      const review: ReviewRow = {
        id: crypto.randomUUID(), companyId: user.companyId, cycleId: cycle.id,
        employeeId: emp.id, managerId: emp.managerId, status: "PENDING_SELF",
        selfAssessment: null, selfSubmittedAt: null, rating: null, summary: null,
        strengths: null, improvements: null, hikeType: null, hikePercent: null,
        proposedSalary: null, hikeNote: null, managerSubmittedAt: null, decidedAt: null,
      };
      mockReviews.push(review);
      notify(emp.userId, user.id, "REVIEW_STARTED", `Review started: ${cycle.name}`,
        `Add your self-assessment for ${cycle.name}`, "/me/review", "REVIEW", review.id);
    }
    const rs = mockReviews.filter((r) => r.cycleId === cycle.id);
    return {
      id: cycle.id, name: cycle.name, periodStart: cycle.periodStart, periodEnd: cycle.periodEnd,
      status: cycle.status, reviewCount: rs.length, submittedCount: 0, approvedCount: 0, createdAt: cycle.createdAt,
    };
  },
  async cycleReviews(accessToken: string | null, cycleId: string): Promise<PerformanceReview[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return mockReviews
      .filter((r) => r.cycleId === cycleId && r.companyId === user.companyId)
      .map((r) => renderReview(db, r));
  },
  async closeReviewCycle(accessToken: string | null, cycleId: string): Promise<ReviewCycle> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const cycle = mockReviewCycles.find((c) => c.id === cycleId && c.companyId === user.companyId);
    if (!cycle) throw err(404, "NOT_FOUND", "Review cycle not found");
    cycle.status = "CLOSED";
    const rs = mockReviews.filter((r) => r.cycleId === cycle.id);
    return {
      id: cycle.id, name: cycle.name, periodStart: cycle.periodStart, periodEnd: cycle.periodEnd,
      status: cycle.status, reviewCount: rs.length,
      submittedCount: rs.filter((r) => r.status === "SUBMITTED").length,
      approvedCount: rs.filter((r) => r.status === "APPROVED").length, createdAt: cycle.createdAt,
    };
  },
  async myReviews(accessToken: string | null): Promise<PerformanceReview[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const me = db.employees.find((e) => e.userId === user.id && e.companyId === user.companyId);
    if (!me) return [];
    return mockReviews.filter((r) => r.employeeId === me.id).map((r) => renderReview(db, r));
  },
  async teamReviews(accessToken: string | null): Promise<PerformanceReview[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const me = db.employees.find((e) => e.userId === user.id && e.companyId === user.companyId);
    if (!me) return [];
    return mockReviews.filter((r) => r.managerId === me.id).map((r) => renderReview(db, r));
  },
  async getReview(accessToken: string | null, reviewId: string): Promise<PerformanceReview> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const r = requireReview(db, user, reviewId);
    if (!canViewReview(db, user, r)) throw err(403, "FORBIDDEN", "You can't view this review");
    return renderReview(db, r);
  },
  async saveSelfAssessment(accessToken: string | null, reviewId: string, input: SelfAssessmentInput): Promise<PerformanceReview> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const r = requireReview(db, user, reviewId);
    const emp = db.employees.find((e) => e.id === r.employeeId);
    if (!emp || emp.userId !== user.id) throw err(403, "FORBIDDEN", "Only you can write your own self-assessment");
    requireCycleOpen(r);
    if (r.status !== "PENDING_SELF" && r.status !== "PENDING_MANAGER") {
      throw err(400, "VALIDATION_ERROR", "This review is no longer open for self-assessment");
    }
    r.selfAssessment = input.selfAssessment?.trim() || null;
    if (input.submit) {
      r.selfSubmittedAt = new Date().toISOString();
      if (r.status === "PENDING_SELF") r.status = "PENDING_MANAGER";
      if (r.managerId) {
        const mgr = db.employees.find((e) => e.id === r.managerId);
        notify(mgr?.userId, user.id, "REVIEW_SELF_SUBMITTED", "Self-assessment submitted",
          `${user.firstName} ${user.lastName} submitted their self-assessment`,
          "/performance/team", "REVIEW", r.id);
      }
    }
    return renderReview(db, r);
  },
  async saveManagerReview(accessToken: string | null, reviewId: string, input: ManagerReviewInput): Promise<PerformanceReview> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const r = requireReview(db, user, reviewId);
    const admin = user.role === "OWNER" || user.role === "ADMIN";
    if (!admin && !isReviewManager(db, user, r)) {
      throw err(403, "FORBIDDEN", "Only the reporting manager or an admin can write this review");
    }
    requireCycleOpen(r);
    if (r.status === "APPROVED") throw err(400, "VALIDATION_ERROR", "This review has already been approved");

    if (input.rating != null) r.rating = input.rating;
    if (input.summary != null) r.summary = input.summary.trim() || null;
    if (input.strengths != null) r.strengths = input.strengths.trim() || null;
    if (input.improvements != null) r.improvements = input.improvements.trim() || null;
    if (input.hikeNote != null) r.hikeNote = input.hikeNote.trim() || null;
    if (input.hikeType) {
      r.hikeType = input.hikeType;
      if (input.hikeType === "PERCENT") { r.hikePercent = input.hikePercent ?? null; r.proposedSalary = null; }
      else if (input.hikeType === "NEW_SALARY") { r.proposedSalary = input.proposedSalary ?? null; r.hikePercent = null; }
      else { r.hikePercent = null; r.proposedSalary = null; }
    }

    if (input.submit) {
      if (r.rating == null) throw err(400, "VALIDATION_ERROR", "Give a rating before submitting the review");
      r.status = "SUBMITTED";
      r.managerSubmittedAt = new Date().toISOString();
      const emp = db.employees.find((e) => e.id === r.employeeId);
      notify(emp?.userId, user.id, "REVIEW_SUBMITTED", "Your review is ready",
        "Your manager submitted your review", "/me/review", "REVIEW", r.id);
      const empName = emp && db.users.find((u) => u.id === emp.userId);
      const label = empName ? `${empName.firstName} ${empName.lastName}` : "An employee";
      db.users
        .filter((u) => u.companyId === r.companyId && (u.role === "OWNER" || u.role === "ADMIN"))
        .forEach((u) => notify(u.id, user.id, "REVIEW_SUBMITTED", "Review awaiting approval",
          `${label}'s review is ready to approve`, "/performance", "REVIEW", r.id));
    }
    return renderReview(db, r);
  },
  async approveReview(accessToken: string | null, reviewId: string): Promise<PerformanceReview> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const r = requireReview(db, user, reviewId);
    if (r.status !== "SUBMITTED") throw err(400, "VALIDATION_ERROR", "Only a submitted review can be approved");
    const newAnnual = resolveReviewSalary(db, r);
    if (newAnnual != null && newAnnual > 0) {
      const existing = mockComp[r.employeeId] ?? [];
      const currency = existing[0]?.currency ?? "USD";
      const cycle = mockReviewCycles.find((c) => c.id === r.cycleId);
      mockComp[r.employeeId] = [
        { id: crypto.randomUUID(), effectiveDate: todayIso(), annualAmount: newAnnual,
          changeType: "HIKE", currency,
          reason: `Performance review: ${cycle?.name ?? "review"}${r.rating ? ` (rating ${r.rating}/5)` : ""}` },
        ...existing,
      ];
    }
    r.status = "APPROVED";
    r.decidedAt = new Date().toISOString();
    const emp = db.employees.find((e) => e.id === r.employeeId);
    notify(emp?.userId, user.id, "REVIEW_APPROVED", "Review approved",
      newAnnual == null ? "Your review is finalized" : "Your review is finalized and a raise was applied",
      "/me/review", "REVIEW", r.id);
    return renderReview(db, r);
  },

  async listEmployees(accessToken: string | null): Promise<Employee[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const companyUsers = db.users.filter((u) => u.companyId === user.companyId);
    ensureProfiles(db, user.companyId, companyUsers);
    save(db);
    return companyUsers
      .map((u) => toEmployee(db, u))
      .sort((a, b) => a.firstName.localeCompare(b.firstName));
  },

  async directoryPage(accessToken: string | null, q: string, page: number, size: number): Promise<Page<Employee>> {
    const all = await this.listEmployees(accessToken);
    const needle = (q ?? "").trim().toLowerCase();
    const filtered = !needle ? all : all.filter((e) =>
      [`${e.firstName} ${e.lastName}`, e.email, e.jobTitle ?? ""].join(" ").toLowerCase().includes(needle));
    const start = Math.max(page, 0) * size;
    return {
      content: filtered.slice(start, start + size),
      page: Math.max(page, 0), size,
      totalElements: filtered.length,
      totalPages: Math.max(1, Math.ceil(filtered.length / size)),
    };
  },

  async getEmployee(accessToken: string | null, id: string): Promise<Employee> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.employees.find((e) => e.id === id && e.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Employee not found");
    return toEmployee(db, db.users.find((u) => u.id === row.userId)!);
  },

  async getMyProfile(accessToken: string | null): Promise<Employee> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    ensureProfiles(db, user.companyId, [user]);
    save(db);
    return toEmployee(db, user);
  },

  async updateEmployee(accessToken: string | null, id: string, patch: Partial<Employee>): Promise<Employee> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const row = db.employees.find((e) => e.id === id && e.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Employee not found");
    applyPatch(row, patch);
    save(db);
    return toEmployee(db, db.users.find((u) => u.id === row.userId)!);
  },

  async updateMyProfile(accessToken: string | null, patch: { phone?: string; workLocation?: string }): Promise<Employee> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    ensureProfiles(db, user.companyId, [user]);
    const row = db.employees.find((e) => e.userId === user.id)!;
    if (patch.phone !== undefined) row.phone = patch.phone || null;
    if (patch.workLocation !== undefined) row.workLocation = patch.workLocation || null;
    save(db);
    return toEmployee(db, user);
  },

  // --- People OS (departments) ---
  async listDepartments(accessToken: string | null): Promise<Department[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    return db.departments
      .filter((d) => d.companyId === user.companyId)
      .map((d) => toDepartment(db, d))
      .sort((a, b) => a.name.localeCompare(b.name));
  },

  async createDepartment(
    accessToken: string | null,
    input: { name: string; parentId?: string; leadUserId?: string },
  ): Promise<Department> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const row: DeptRow = {
      id: uuid(),
      companyId: user.companyId,
      name: input.name.trim(),
      parentId: input.parentId || null,
      leadUserId: input.leadUserId || null,
    };
    db.departments.push(row);
    save(db);
    return toDepartment(db, row);
  },

  async updateDepartment(
    accessToken: string | null,
    id: string,
    patch: { name?: string; parentId?: string; leadUserId?: string },
  ): Promise<Department> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const row = db.departments.find((d) => d.id === id && d.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Department not found");
    if (patch.name !== undefined && patch.name.trim()) row.name = patch.name.trim();
    if (patch.parentId !== undefined) row.parentId = patch.parentId || null;
    if (patch.leadUserId !== undefined) row.leadUserId = patch.leadUserId || null;
    save(db);
    return toDepartment(db, row);
  },

  async deleteDepartment(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const row = db.departments.find((d) => d.id === id && d.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Department not found");
    db.employees.forEach((e) => { if (e.departmentId === id) e.departmentId = null; });
    db.departments.forEach((d) => { if (d.parentId === id) d.parentId = row.parentId; });
    db.departments = db.departments.filter((d) => d.id !== id);
    save(db);
  },

  // --- People OS (onboarding) ---
  async listOnboarding(accessToken: string | null, employeeId: string): Promise<OnboardingTask[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    onboardSelfOrAdmin(db, user, employeeId);
    return db.onboarding
      .filter((t) => t.employeeId === employeeId)
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map(toOnboarding);
  },
  async addOnboardingTask(accessToken: string | null, employeeId: string, title: string): Promise<OnboardingTask> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const order = db.onboarding.filter((t) => t.employeeId === employeeId).length;
    const row: OnboardRow = { id: uuid(), companyId: user.companyId, employeeId, title: title.trim(), sortOrder: order, completed: false, completedAt: null };
    db.onboarding.push(row);
    save(db);
    return toOnboarding(row);
  },
  async seedOnboardingDefaults(accessToken: string | null, employeeId: string): Promise<OnboardingTask[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const defaults = ["Sign employment paperwork", "Set up laptop & accounts", "Complete IT security training", "Meet your team", "Read the company handbook"];
    let order = db.onboarding.filter((t) => t.employeeId === employeeId).length;
    for (const title of defaults) {
      db.onboarding.push({ id: uuid(), companyId: user.companyId, employeeId, title, sortOrder: order++, completed: false, completedAt: null });
    }
    save(db);
    return db.onboarding.filter((t) => t.employeeId === employeeId).sort((a, b) => a.sortOrder - b.sortOrder).map(toOnboarding);
  },
  async toggleOnboardingTask(accessToken: string | null, taskId: string, completed: boolean): Promise<OnboardingTask> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.onboarding.find((t) => t.id === taskId && t.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Task not found");
    onboardSelfOrAdmin(db, user, row.employeeId);
    row.completed = completed;
    row.completedAt = completed ? new Date().toISOString() : null;
    save(db);
    return toOnboarding(row);
  },
  async deleteOnboardingTask(accessToken: string | null, taskId: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const row = db.onboarding.find((t) => t.id === taskId && t.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Task not found");
    db.onboarding = db.onboarding.filter((t) => t.id !== taskId);
    save(db);
  },

  // --- People OS (leave) ---
  async requestLeave(accessToken: string | null, input: { type: string; startDate: string; endDate: string; reason?: string }): Promise<LeaveRequest> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const emp = employeeForUser(db, user);
    const start = new Date(input.startDate);
    const end = new Date(input.endDate);
    if (end < start) throw err(400, "VALIDATION_ERROR", "End date must be on or after the start date");
    const days = Math.round((end.getTime() - start.getTime()) / 86400000) + 1;
    const row: LeaveRow = {
      id: uuid(), companyId: user.companyId, employeeId: emp.id,
      type: input.type as LeaveRow["type"], startDate: input.startDate, endDate: input.endDate,
      days, reason: input.reason || null, status: "PENDING", decidedAt: null, createdAt: new Date().toISOString(),
    };
    db.leave.push(row);
    save(db);

    // Route it to whoever has to act on it (mirrors LeaveService).
    const who = `${user.firstName} ${user.lastName}`;
    for (const approverId of approversFor(db, emp)) {
      notify(approverId, user.id, "LEAVE_REQUESTED",
        `${who} requested ${days} ${days === 1 ? "day" : "days"} off`,
        `${input.type.toLowerCase()} · ${input.startDate} → ${input.endDate}${input.reason ? ` · ${input.reason}` : ""}`,
        "/people/time-off", "LEAVE_REQUEST", row.id);
    }
    return toLeave(db, row);
  },
  async myLeave(accessToken: string | null): Promise<LeaveRequest[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const emp = employeeForUser(db, user);
    return db.leave.filter((l) => l.employeeId === emp.id).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).map((l) => toLeave(db, l));
  },
  async leaveBalance(accessToken: string | null): Promise<LeaveBalance> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const emp = employeeForUser(db, user);
    const year = new Date().getFullYear();
    let used = 0, pending = 0;
    for (const l of db.leave) {
      if (l.employeeId !== emp.id || l.type !== "VACATION" || new Date(l.startDate).getFullYear() !== year) continue;
      if (l.status === "APPROVED") used += l.days;
      else if (l.status === "PENDING") pending += l.days;
    }
    return { allowanceDays: 25, usedDays: used, remainingDays: Math.max(0, 25 - used), pendingDays: pending };
  },
  async allLeave(accessToken: string | null): Promise<LeaveRequest[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    return db.leave.filter((l) => l.companyId === user.companyId).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).map((l) => toLeave(db, l));
  },
  async decideLeave(accessToken: string | null, id: string, decision: "APPROVED" | "REJECTED"): Promise<LeaveRequest> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const row = db.leave.find((l) => l.id === id && l.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Request not found");
    if (row.status !== "PENDING") throw err(409, "CONFLICT", "This request has already been decided");
    row.status = decision;
    row.decidedAt = new Date().toISOString();
    save(db);

    // Tell the requester what was decided.
    const approved = decision === "APPROVED";
    const requester = db.employees.find((e) => e.id === row.employeeId);
    notify(requester?.userId, user.id, approved ? "LEAVE_APPROVED" : "LEAVE_REJECTED",
      `Your leave was ${approved ? "approved" : "declined"}`,
      `${row.type.toLowerCase()} · ${row.startDate} → ${row.endDate}`,
      "/people/time-off", "LEAVE_REQUEST", row.id);
    return toLeave(db, row);
  },
  async cancelLeave(accessToken: string | null, id: string): Promise<LeaveRequest> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const emp = employeeForUser(db, user);
    const row = db.leave.find((l) => l.id === id && l.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Request not found");
    if (row.employeeId !== emp.id) throw err(403, "FORBIDDEN", "You can only cancel your own requests");
    if (row.status !== "PENDING") throw err(409, "CONFLICT", "Only pending requests can be cancelled");
    row.status = "CANCELLED";
    save(db);
    return toLeave(db, row);
  },

  // --- Work OS (projects) ---
  async listProjects(accessToken: string | null): Promise<Project[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    return db.projects.filter((p) => p.companyId === user.companyId).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).map((p) => toProject(db, p));
  },
  async getProject(accessToken: string | null, id: string): Promise<Project> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const p = db.projects.find((x) => x.id === id && x.companyId === user.companyId);
    if (!p) throw err(404, "NOT_FOUND", "Project not found");
    return toProject(db, p);
  },
  async createProject(accessToken: string | null, input: { name: string; key: string; description?: string }): Promise<Project> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const key = input.key.trim().toUpperCase();
    if (db.projects.some((p) => p.companyId === user.companyId && p.key.toUpperCase() === key)) {
      throw err(409, "CONFLICT", "A project with that key already exists");
    }
    const row: ProjectRow = { id: uuid(), companyId: user.companyId, name: input.name.trim(), key, description: input.description || null, status: "ACTIVE", leadUserId: null, createdAt: new Date().toISOString() };
    db.projects.push(row);
    save(db);
    return toProject(db, row);
  },
  async archiveProject(accessToken: string | null, id: string): Promise<Project> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const p = db.projects.find((x) => x.id === id && x.companyId === user.companyId);
    if (!p) throw err(404, "NOT_FOUND", "Project not found");
    p.status = "ARCHIVED";
    save(db);
    return toProject(db, p);
  },

  // --- Work OS (tasks) ---
  async listTasks(accessToken: string | null, projectId: string): Promise<Task[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const project = db.projects.find((p) => p.id === projectId && p.companyId === user.companyId);
    if (!project) throw err(404, "NOT_FOUND", "Project not found");
    return db.tasks.filter((t) => t.projectId === projectId).sort((a, b) => a.sortOrder - b.sortOrder || a.number - b.number).map((t) => toTask(db, t, project));
  },
  async createTask(accessToken: string | null, projectId: string, input: { title: string; description?: string; priority?: string; assigneeId?: string; dueDate?: string }): Promise<Task> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const project = db.projects.find((p) => p.id === projectId && p.companyId === user.companyId);
    if (!project) throw err(404, "NOT_FOUND", "Project not found");
    const number = db.tasks.filter((t) => t.projectId === projectId).reduce((m, t) => Math.max(m, t.number), 0) + 1;
    const row: TaskRow = {
      id: uuid(), companyId: user.companyId, projectId, number, title: input.title.trim(),
      description: input.description || null, status: "TODO", priority: (input.priority as TaskRow["priority"]) || "MEDIUM",
      assigneeId: input.assigneeId || null, sprintId: null, dueDate: input.dueDate || null, sortOrder: number, createdAt: new Date().toISOString(),
    };
    db.tasks.push(row);
    save(db);
    return toTask(db, row, project);
  },
  async updateTask(accessToken: string | null, id: string, patch: { title?: string; description?: string; status?: string; priority?: string; assigneeId?: string; sprintId?: string; dueDate?: string }): Promise<Task> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.tasks.find((t) => t.id === id && t.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Task not found");
    if (patch.title !== undefined && patch.title) row.title = patch.title;
    if (patch.description !== undefined) row.description = patch.description || null;
    if (patch.status !== undefined) row.status = patch.status as TaskRow["status"];
    if (patch.priority !== undefined) row.priority = patch.priority as TaskRow["priority"];
    if (patch.assigneeId !== undefined) row.assigneeId = patch.assigneeId || null;
    if (patch.sprintId !== undefined) row.sprintId = patch.sprintId || null;
    if (patch.dueDate !== undefined) row.dueDate = patch.dueDate || null;
    save(db);
    const project = db.projects.find((p) => p.id === row.projectId)!;
    return toTask(db, row, project);
  },
  async deleteTask(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.tasks.find((t) => t.id === id && t.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Task not found");
    db.tasks = db.tasks.filter((t) => t.id !== id);
    save(db);
  },
  async myTasks(accessToken: string | null): Promise<Task[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const emp = employeeForUser(db, user);
    return db.tasks
      .filter((t) => t.assigneeId === emp.id && t.status !== "DONE")
      .map((t) => toTask(db, t, db.projects.find((p) => p.id === t.projectId)!))
      .sort((a, b) => (a.dueDate ?? "9999").localeCompare(b.dueDate ?? "9999"));
  },

  // --- Knowledge OS (spaces) ---
  async listSpaces(accessToken: string | null): Promise<Space[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    return db.spaces.filter((s) => s.companyId === user.companyId).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).map((s) => toSpace(db, s));
  },
  async getSpace(accessToken: string | null, id: string): Promise<Space> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const s = db.spaces.find((x) => x.id === id && x.companyId === user.companyId);
    if (!s) throw err(404, "NOT_FOUND", "Space not found");
    return toSpace(db, s);
  },
  async createSpace(accessToken: string | null, input: { name: string; key: string; description?: string }): Promise<Space> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const key = input.key.trim().toUpperCase();
    if (db.spaces.some((s) => s.companyId === user.companyId && s.key.toUpperCase() === key)) {
      throw err(409, "CONFLICT", "A space with that key already exists");
    }
    const row: SpaceRow = { id: uuid(), companyId: user.companyId, name: input.name.trim(), key, description: input.description || null, status: "ACTIVE", createdBy: user.id, createdAt: new Date().toISOString() };
    db.spaces.push(row);
    save(db);
    return toSpace(db, row);
  },
  async archiveSpace(accessToken: string | null, id: string): Promise<Space> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const s = db.spaces.find((x) => x.id === id && x.companyId === user.companyId);
    if (!s) throw err(404, "NOT_FOUND", "Space not found");
    s.status = "ARCHIVED";
    save(db);
    return toSpace(db, s);
  },

  // --- Knowledge OS (pages) ---
  async listPages(accessToken: string | null, spaceId: string): Promise<PageSummary[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const space = db.spaces.find((s) => s.id === spaceId && s.companyId === user.companyId);
    if (!space) throw err(404, "NOT_FOUND", "Space not found");
    return db.pages.filter((p) => p.spaceId === spaceId).sort((a, b) => a.sortOrder - b.sortOrder || a.createdAt.localeCompare(b.createdAt)).map((p) => toPageSummary(db, p, null));
  },
  async getPage(accessToken: string | null, id: string): Promise<KnowledgePage> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const p = db.pages.find((x) => x.id === id && x.companyId === user.companyId);
    if (!p) throw err(404, "NOT_FOUND", "Page not found");
    return toPage(db, p);
  },
  async createPage(accessToken: string | null, spaceId: string, input: { title: string; body?: string; parentId?: string; linkedTaskId?: string }): Promise<KnowledgePage> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const space = db.spaces.find((s) => s.id === spaceId && s.companyId === user.companyId);
    if (!space) throw err(404, "NOT_FOUND", "Space not found");
    if (input.linkedTaskId && !db.tasks.some((t) => t.id === input.linkedTaskId && t.companyId === user.companyId)) {
      throw err(404, "NOT_FOUND", "Linked task not found");
    }
    const author = employeeForUser(db, user); // provision the People profile if needed
    const sortOrder = db.pages.filter((p) => p.spaceId === spaceId).length;
    const now = new Date().toISOString();
    const row: PageRow = {
      id: uuid(), companyId: user.companyId, spaceId, parentId: input.parentId || null, title: input.title.trim(),
      body: input.body || null, status: "DRAFT", authorId: author.id, linkedTaskId: input.linkedTaskId || null,
      sortOrder, createdBy: user.id, createdAt: now, updatedAt: now,
    };
    db.pages.push(row);
    save(db);
    return toPage(db, row);
  },
  async updatePage(accessToken: string | null, id: string, patch: { title?: string; body?: string; status?: string; parentId?: string; linkedTaskId?: string }): Promise<KnowledgePage> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.pages.find((p) => p.id === id && p.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Page not found");
    if (patch.linkedTaskId && !db.tasks.some((t) => t.id === patch.linkedTaskId && t.companyId === user.companyId)) {
      throw err(404, "NOT_FOUND", "Linked task not found");
    }
    if (patch.title !== undefined && patch.title) row.title = patch.title;
    if (patch.body !== undefined) row.body = patch.body || null;
    if (patch.status !== undefined) row.status = patch.status as PageRow["status"];
    if (patch.parentId !== undefined) row.parentId = patch.parentId || null;
    if (patch.linkedTaskId !== undefined) row.linkedTaskId = patch.linkedTaskId || null;
    row.updatedAt = new Date().toISOString();
    save(db);
    return toPage(db, row);
  },
  async deletePage(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.pages.find((p) => p.id === id && p.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Page not found");
    for (const child of db.pages) if (child.parentId === id) child.parentId = null;
    db.pages = db.pages.filter((p) => p.id !== id);
    save(db);
  },
  async myPages(accessToken: string | null): Promise<PageSummary[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const emp = employeeForUser(db, user);
    return db.pages.filter((p) => p.authorId === emp.id).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).map((p) => toPageSummary(db, p, null));
  },
  async searchPages(accessToken: string | null, q: string): Promise<PageSummary[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const query = (q || "").trim().toLowerCase();
    if (!query) return [];
    return db.pages
      .filter((p) => p.companyId === user.companyId && (p.title.toLowerCase().includes(query) || (p.body || "").toLowerCase().includes(query)))
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      .map((p) => toPageSummary(db, p, knowledgeSnippet(p.body, query)));
  },

  async globalSearch(accessToken: string | null, q: string): Promise<SearchResponse> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const query = (q || "").trim().toLowerCase();
    if (query.length < 2) return { query, total: 0, groups: [] };
    const cid = user.companyId;
    const has = (s: string | null | undefined) => (s || "").toLowerCase().includes(query);
    const projects = db.projects.filter((p) => p.companyId === cid);
    const projOf = (id: string) => projects.find((p) => p.id === id);
    const spaces = db.spaces.filter((s) => s.companyId === cid);
    const spaceOf = (id: string) => spaces.find((s) => s.id === id);

    const people: SearchHit[] = db.users
      .filter((u) => u.companyId === cid && u.status !== "DISABLED" && (has(u.firstName) || has(u.lastName) || has(u.email)))
      .slice(0, 5)
      .map((u) => ({ kind: "person", title: `${u.firstName} ${u.lastName}`, subtitle: u.email, href: "/people" }));

    const work: SearchHit[] = [
      ...projects.filter((p) => has(p.name) || has(p.key)).slice(0, 5)
        .map((p): SearchHit => ({ kind: "project", title: p.name, subtitle: `Project · ${p.key}`, href: `/work/${p.id}` })),
      ...db.tasks.filter((t) => t.companyId === cid && has(t.title)).slice(0, 5).map((t): SearchHit => {
        const p = projOf(t.projectId);
        return { kind: "task", title: t.title, subtitle: p ? `${p.key}-${t.number} · ${p.name}` : "Task", href: `/work/${t.projectId}` };
      }),
      ...db.tickets.filter((t) => t.companyId === cid && has(t.subject)).slice(0, 5).map((t): SearchHit => {
        const p = projOf(t.projectId);
        return { kind: "ticket", title: t.subject, subtitle: p ? `${p.key}-T${t.number}` : "Ticket", href: `/work/${t.projectId}` };
      }),
    ];

    const knowledge: SearchHit[] = [
      ...spaces.filter((s) => has(s.name) || has(s.key)).slice(0, 5)
        .map((s): SearchHit => ({ kind: "space", title: s.name, subtitle: `Space · ${s.key}`, href: `/knowledge/${s.id}` })),
      ...db.pages.filter((p) => p.companyId === cid && (has(p.title) || has(p.body))).slice(0, 5).map((p): SearchHit => {
        const s = spaceOf(p.spaceId);
        return { kind: "page", title: p.title, subtitle: s ? s.name : "Page", href: `/knowledge/${p.spaceId}` };
      }),
    ];

    // Owner/Admin-only modules must not leak through the search box either.
    const admin = user.role === "OWNER" || user.role === "ADMIN";
    const clients: SearchHit[] = !admin ? [] : (mockClients[cid] ?? [])
      .filter((c) => has(c.name) || has(c.contactName) || has(c.contactEmail))
      .slice(0, 5)
      .map((c): SearchHit => ({ kind: "client", title: c.name, subtitle: c.contactName ?? "Client", href: `/clients/${c.id}` }));
    const documents: SearchHit[] = !admin ? [] : (mockDocs[cid] ?? [])
      .filter((d) => has(d.title))
      .slice(0, 5)
      .map((d): SearchHit => ({ kind: "document", title: d.title, subtitle: KIND_LABELS[d.kind], href: `/documents/${d.id}` }));

    const groups: SearchGroup[] = [];
    if (people.length) groups.push({ label: "People", hits: people });
    if (work.length) groups.push({ label: "Work", hits: work });
    if (knowledge.length) groups.push({ label: "Knowledge", hits: knowledge });
    if (clients.length) groups.push({ label: "Clients", hits: clients });
    if (documents.length) groups.push({ label: "Documents", hits: documents });
    return {
      query,
      total: people.length + work.length + knowledge.length + clients.length + documents.length,
      groups,
    };
  },

  async askAssistant(accessToken: string | null, question: string): Promise<AssistantResponse> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const cid = user.companyId;
    const q = (question || "").toLowerCase();
    const mine = <T extends { companyId: string }>(rows: T[]) => rows.filter((r) => r.companyId === cid);
    const has = (s: string | null | undefined) => (s || "").toLowerCase().includes(q);

    const metrics: Record<string, number> = {
      members: db.users.filter((u) => u.companyId === cid && u.status === "ACTIVE").length,
      departments: mine(db.departments).length,
      projects: mine(db.projects).length,
      openTasks: mine(db.tasks).filter((t) => t.status !== "DONE").length,
      openTickets: mine(db.tickets).filter((t) => t.status === "OPEN" || t.status === "PENDING").length,
      spaces: mine(db.spaces).length,
      pages: mine(db.pages).length,
    };
    const say = (n: number, noun: string) => `You have **${n}** ${noun}${n === 1 ? "" : "s"}.`;

    if (q.includes("how many") || q.includes("number of") || q.includes("count")) {
      if (q.includes("ticket")) return { answer: say(metrics.openTickets, "open support ticket"), mode: "local", sources: [] };
      if (q.includes("task")) return { answer: say(metrics.openTasks, "open task"), mode: "local", sources: [] };
      if (q.includes("project")) return { answer: say(metrics.projects, "project"), mode: "local", sources: [] };
      if (q.includes("page") || q.includes("doc")) return { answer: say(metrics.pages, "knowledge page"), mode: "local", sources: [] };
      if (q.includes("employee") || q.includes("people") || q.includes("member") || q.includes("team") || q.includes("staff"))
        return { answer: say(metrics.members, "team member"), mode: "local", sources: [] };
      if (q.includes("department")) return { answer: say(metrics.departments, "department"), mode: "local", sources: [] };
    }

    const pages = mine(db.pages).filter((p) => has(p.title) || has(p.body)).slice(0, 3);
    if (pages.length) {
      const spaces = mine(db.spaces);
      const answer =
        "Here's what I found in your Knowledge base:\n\n" +
        pages.slice(0, 2).map((p) => `**${p.title}**\n${(p.body || "").replace(/\s+/g, " ").slice(0, 240)}…`).join("\n\n");
      const sources = pages.map((p) => {
        void spaces;
        return { kind: "page", title: p.title, href: `/knowledge/${p.spaceId}` };
      });
      return { answer, mode: "local", sources };
    }

    return {
      answer:
        `Here's your company at a glance:\n\n- **${metrics.members}** team members across **${metrics.departments}** departments\n` +
        `- **${metrics.openTasks}** open tasks and **${metrics.openTickets}** open tickets in **${metrics.projects}** project(s)\n` +
        `- **${metrics.pages}** knowledge pages in **${metrics.spaces}** space(s)\n\nAsk me about a person, a project, a ticket, or anything in your docs.`,
      mode: "local",
      sources: [],
    };
  },

  // --- Work OS (board & backlog) ---
  async board(accessToken: string | null, projectId: string): Promise<Board> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const project = db.projects.find((p) => p.id === projectId && p.companyId === user.companyId);
    if (!project) throw err(404, "NOT_FOUND", "Project not found");
    const active = db.sprints.find((s) => s.projectId === projectId && s.status === "ACTIVE") || null;
    const rows = active
      ? db.tasks.filter((t) => t.sprintId === active.id)
      : db.tasks.filter((t) => t.projectId === projectId && !t.sprintId);
    const tasks = rows.sort((a, b) => a.sortOrder - b.sortOrder || a.number - b.number).map((t) => toTask(db, t, project));
    return { activeSprint: active ? toSprint(db, active) : null, tasks };
  },
  async backlog(accessToken: string | null, projectId: string): Promise<Task[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const project = db.projects.find((p) => p.id === projectId && p.companyId === user.companyId);
    if (!project) throw err(404, "NOT_FOUND", "Project not found");
    return db.tasks
      .filter((t) => t.projectId === projectId && !t.sprintId)
      .sort((a, b) => a.sortOrder - b.sortOrder || a.number - b.number)
      .map((t) => toTask(db, t, project));
  },

  // --- Work OS (sprints) ---
  async listSprints(accessToken: string | null, projectId: string): Promise<Sprint[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const project = db.projects.find((p) => p.id === projectId && p.companyId === user.companyId);
    if (!project) throw err(404, "NOT_FOUND", "Project not found");
    return db.sprints.filter((s) => s.projectId === projectId).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).map((s) => toSprint(db, s));
  },
  async createSprint(accessToken: string | null, projectId: string, input: { name: string; goal?: string; startDate?: string; endDate?: string }): Promise<Sprint> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const project = db.projects.find((p) => p.id === projectId && p.companyId === user.companyId);
    if (!project) throw err(404, "NOT_FOUND", "Project not found");
    const row: SprintRow = { id: uuid(), companyId: user.companyId, projectId, name: input.name.trim(), goal: input.goal || null, startDate: input.startDate || null, endDate: input.endDate || null, status: "PLANNED", createdAt: new Date().toISOString() };
    db.sprints.push(row);
    save(db);
    return toSprint(db, row);
  },
  async updateSprint(accessToken: string | null, id: string, patch: { name?: string; goal?: string; startDate?: string; endDate?: string }): Promise<Sprint> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.sprints.find((s) => s.id === id && s.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Sprint not found");
    if (patch.name !== undefined && patch.name) row.name = patch.name;
    if (patch.goal !== undefined) row.goal = patch.goal || null;
    if (patch.startDate !== undefined) row.startDate = patch.startDate || null;
    if (patch.endDate !== undefined) row.endDate = patch.endDate || null;
    save(db);
    return toSprint(db, row);
  },
  async startSprint(accessToken: string | null, id: string): Promise<Sprint> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.sprints.find((s) => s.id === id && s.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Sprint not found");
    if (row.status === "COMPLETED") throw err(409, "CONFLICT", "A completed sprint cannot be started");
    if (db.sprints.some((s) => s.projectId === row.projectId && s.status === "ACTIVE" && s.id !== row.id)) {
      throw err(409, "CONFLICT", "This project already has an active sprint");
    }
    row.status = "ACTIVE";
    save(db);
    return toSprint(db, row);
  },
  async completeSprint(accessToken: string | null, id: string): Promise<Sprint> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.sprints.find((s) => s.id === id && s.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Sprint not found");
    if (row.status !== "ACTIVE") throw err(409, "CONFLICT", "Only an active sprint can be completed");
    for (const t of db.tasks) if (t.sprintId === row.id && t.status !== "DONE") t.sprintId = null;
    row.status = "COMPLETED";
    save(db);
    return toSprint(db, row);
  },
  async deleteSprint(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.sprints.find((s) => s.id === id && s.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Sprint not found");
    for (const t of db.tasks) if (t.sprintId === id) t.sprintId = null;
    db.sprints = db.sprints.filter((s) => s.id !== id);
    save(db);
  },

  // --- Work OS (support tickets) ---
  async listTickets(accessToken: string | null, projectId: string): Promise<Ticket[]> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const project = db.projects.find((p) => p.id === projectId && p.companyId === user.companyId);
    if (!project) throw err(404, "NOT_FOUND", "Project not found");
    return db.tickets.filter((t) => t.projectId === projectId).sort((a, b) => b.number - a.number).map((t) => toTicket(db, t));
  },
  async createTicket(accessToken: string | null, projectId: string, input: { subject: string; description?: string; requesterName?: string; requesterEmail?: string; priority?: string; assigneeId?: string }): Promise<Ticket> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const project = db.projects.find((p) => p.id === projectId && p.companyId === user.companyId);
    if (!project) throw err(404, "NOT_FOUND", "Project not found");
    if (input.assigneeId && !db.employees.some((e) => e.id === input.assigneeId && e.companyId === user.companyId)) {
      throw err(404, "NOT_FOUND", "Assignee not found");
    }
    const number = db.tickets.filter((t) => t.projectId === projectId).reduce((m, t) => Math.max(m, t.number), 0) + 1;
    const row: TicketRow = {
      id: uuid(), companyId: user.companyId, projectId, number, subject: input.subject.trim(),
      description: input.description || null, requesterName: input.requesterName || null, requesterEmail: input.requesterEmail || null,
      status: "OPEN", priority: (input.priority as TicketRow["priority"]) || "MEDIUM", assigneeId: input.assigneeId || null,
      createdBy: user.id, createdAt: new Date().toISOString(),
    };
    db.tickets.push(row);
    save(db);
    return toTicket(db, row);
  },
  async updateTicket(accessToken: string | null, id: string, patch: { subject?: string; description?: string; requesterName?: string; requesterEmail?: string; status?: string; priority?: string; assigneeId?: string }): Promise<Ticket> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.tickets.find((t) => t.id === id && t.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Ticket not found");
    if (patch.assigneeId && !db.employees.some((e) => e.id === patch.assigneeId && e.companyId === user.companyId)) {
      throw err(404, "NOT_FOUND", "Assignee not found");
    }
    if (patch.subject !== undefined && patch.subject) row.subject = patch.subject;
    if (patch.description !== undefined) row.description = patch.description || null;
    if (patch.requesterName !== undefined) row.requesterName = patch.requesterName || null;
    if (patch.requesterEmail !== undefined) row.requesterEmail = patch.requesterEmail || null;
    if (patch.status !== undefined) row.status = patch.status as TicketRow["status"];
    if (patch.priority !== undefined) row.priority = patch.priority as TicketRow["priority"];
    if (patch.assigneeId !== undefined) row.assigneeId = patch.assigneeId || null;
    save(db);
    return toTicket(db, row);
  },
  async deleteTicket(accessToken: string | null, id: string): Promise<void> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    const row = db.tickets.find((t) => t.id === id && t.companyId === user.companyId);
    if (!row) throw err(404, "NOT_FOUND", "Ticket not found");
    db.tickets = db.tickets.filter((t) => t.id !== id);
    save(db);
  },

  mailbox(): MailMessage[] {
    return load().mailbox;
  },

  reset(): void {
    if (typeof window !== "undefined") window.localStorage.removeItem(KEY);
    setSessionCookie(false);
  },
};

// --- helpers ---
// An invitation's id is its token string, and the token carries the companyId.
function companyOfInvite(db: DB, invitationId: string): string | undefined {
  return db.tokens.find((t) => t.token === invitationId)?.companyId;
}
function nameOf(db: DB, companyId: string): string {
  return db.companies.find((c) => c.id === companyId)?.name ?? "your company";
}

// --- People OS helpers ---
function ensureProfiles(db: DB, companyId: string, users: User[]): void {
  for (const u of users) {
    if (!db.employees.some((e) => e.userId === u.id)) {
      db.employees.push({
        id: uuid(),
        companyId,
        userId: u.id,
        employeeNo: null,
        jobTitle: null,
        employmentType: null,
        employmentStatus: "ACTIVE",
        departmentId: null,
        managerId: null,
        workLocation: null,
        phone: null,
        startDate: null,
      });
    }
  }
}

function toEmployee(db: DB, user: User): Employee {
  const row = db.employees.find((e) => e.userId === user.id)!;
  return {
    id: row.id,
    userId: user.id,
    firstName: user.firstName,
    lastName: user.lastName,
    email: user.email,
    role: user.role,
    employeeNo: row.employeeNo,
    jobTitle: row.jobTitle,
    employmentType: row.employmentType,
    employmentStatus: row.employmentStatus,
    departmentId: row.departmentId,
    managerId: row.managerId,
    workLocation: row.workLocation,
    phone: row.phone,
    startDate: row.startDate,
    endDate: row.endDate ?? null,
    skills: row.skills ?? [],
    rating: row.rating ?? null,
  };
}

function employeeForUser(db: DB, user: User): EmployeeRow {
  ensureProfiles(db, user.companyId, [user]);
  save(db);
  return db.employees.find((e) => e.userId === user.id)!;
}

function onboardSelfOrAdmin(db: DB, user: User, employeeId: string): void {
  const isAdmin = user.role === "OWNER" || user.role === "ADMIN";
  const emp = db.employees.find((e) => e.id === employeeId);
  const isSelf = emp?.userId === user.id;
  if (!isAdmin && !isSelf) {
    throw err(403, "FORBIDDEN", "You cannot access this checklist");
  }
}

function toOnboarding(row: OnboardRow): OnboardingTask {
  return {
    id: row.id,
    employeeId: row.employeeId,
    title: row.title,
    sortOrder: row.sortOrder,
    completed: row.completed,
    completedAt: row.completedAt,
  };
}

function toLeave(db: DB, row: LeaveRow): LeaveRequest {
  const emp = db.employees.find((e) => e.id === row.employeeId);
  const user = emp ? db.users.find((u) => u.id === emp.userId) : undefined;
  return {
    id: row.id,
    employeeId: row.employeeId,
    employeeName: user ? `${user.firstName} ${user.lastName}` : "Unknown",
    type: row.type,
    startDate: row.startDate,
    endDate: row.endDate,
    days: row.days,
    reason: row.reason,
    status: row.status,
    decidedAt: row.decidedAt,
    createdAt: row.createdAt,
  };
}

function toProject(db: DB, p: ProjectRow): Project {
  const lead = p.leadUserId ? db.users.find((u) => u.id === p.leadUserId) : undefined;
  const tasks = db.tasks.filter((t) => t.projectId === p.id);
  return {
    id: p.id,
    name: p.name,
    key: p.key,
    description: p.description,
    status: p.status,
    leadUserId: p.leadUserId,
    leadName: lead ? `${lead.firstName} ${lead.lastName}` : null,
    taskCount: tasks.length,
    openTaskCount: tasks.filter((t) => t.status !== "DONE").length,
    createdAt: p.createdAt,
  };
}

function toTask(db: DB, t: TaskRow, project: ProjectRow): Task {
  let assigneeName: string | null = null;
  if (t.assigneeId) {
    const emp = db.employees.find((e) => e.id === t.assigneeId);
    const user = emp ? db.users.find((u) => u.id === emp.userId) : undefined;
    assigneeName = user ? `${user.firstName} ${user.lastName}` : null;
  }
  return {
    id: t.id,
    projectId: t.projectId,
    ref: `${project.key}-${t.number}`,
    number: t.number,
    title: t.title,
    description: t.description,
    status: t.status,
    priority: t.priority,
    assigneeId: t.assigneeId,
    assigneeName,
    sprintId: t.sprintId,
    dueDate: t.dueDate,
    storyPoints: t.storyPoints ?? null,
    createdAt: t.createdAt,
  };
}

function toSprint(db: DB, s: SprintRow): Sprint {
  const tasks = db.tasks.filter((t) => t.sprintId === s.id);
  return {
    id: s.id,
    projectId: s.projectId,
    name: s.name,
    goal: s.goal,
    startDate: s.startDate,
    endDate: s.endDate,
    status: s.status,
    capacityPoints: s.capacityPoints ?? null,
    taskCount: tasks.length,
    doneCount: tasks.filter((t) => t.status === "DONE").length,
    createdAt: s.createdAt,
  };
}

function toTicket(db: DB, t: TicketRow): Ticket {
  const project = db.projects.find((p) => p.id === t.projectId);
  let assigneeName: string | null = null;
  if (t.assigneeId) {
    const emp = db.employees.find((e) => e.id === t.assigneeId);
    const user = emp ? db.users.find((u) => u.id === emp.userId) : undefined;
    assigneeName = user ? `${user.firstName} ${user.lastName}` : null;
  }
  return {
    id: t.id,
    projectId: t.projectId,
    ref: `${project ? project.key : "?"}-T${t.number}`,
    number: t.number,
    subject: t.subject,
    description: t.description,
    requesterName: t.requesterName,
    requesterEmail: t.requesterEmail,
    status: t.status,
    priority: t.priority,
    assigneeId: t.assigneeId,
    assigneeName,
    createdAt: t.createdAt,
  };
}

function knowledgeAuthorName(db: DB, authorId: string | null): string | null {
  if (!authorId) return null;
  const emp = db.employees.find((e) => e.id === authorId);
  const user = emp ? db.users.find((u) => u.id === emp.userId) : undefined;
  return user ? `${user.firstName} ${user.lastName}` : null;
}

function knowledgeTaskRef(db: DB, taskId: string | null): string | null {
  if (!taskId) return null;
  const task = db.tasks.find((t) => t.id === taskId);
  if (!task) return null;
  const project = db.projects.find((p) => p.id === task.projectId);
  return project ? `${project.key}-${task.number}` : null;
}

function toSpace(db: DB, s: SpaceRow): Space {
  return {
    id: s.id,
    name: s.name,
    key: s.key,
    description: s.description,
    status: s.status,
    pageCount: db.pages.filter((p) => p.spaceId === s.id).length,
    createdAt: s.createdAt,
  };
}

function toPage(db: DB, p: PageRow): KnowledgePage {
  return {
    id: p.id,
    spaceId: p.spaceId,
    parentId: p.parentId,
    title: p.title,
    body: p.body,
    status: p.status,
    authorId: p.authorId,
    authorName: knowledgeAuthorName(db, p.authorId),
    linkedTaskId: p.linkedTaskId,
    linkedTaskRef: knowledgeTaskRef(db, p.linkedTaskId),
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
  };
}

function toPageSummary(db: DB, p: PageRow, snippet: string | null): PageSummary {
  const space = db.spaces.find((s) => s.id === p.spaceId);
  return {
    id: p.id,
    spaceId: p.spaceId,
    spaceName: space ? space.name : null,
    parentId: p.parentId,
    title: p.title,
    status: p.status,
    authorName: knowledgeAuthorName(db, p.authorId),
    linkedTaskRef: knowledgeTaskRef(db, p.linkedTaskId),
    snippet,
    updatedAt: p.updatedAt,
  };
}

function knowledgeSnippet(body: string | null, q: string): string | null {
  if (!body) return null;
  const at = body.toLowerCase().indexOf(q.toLowerCase());
  const R = 80;
  if (at < 0) return body.length <= R * 2 ? body : body.slice(0, R * 2) + "…";
  const start = Math.max(0, at - R);
  const end = Math.min(body.length, at + q.length + R);
  return (start > 0 ? "…" : "") + body.slice(start, end) + (end < body.length ? "…" : "");
}

function toDepartment(db: DB, d: DeptRow): Department {
  const lead = d.leadUserId ? db.users.find((u) => u.id === d.leadUserId) : undefined;
  return {
    id: d.id,
    name: d.name,
    parentId: d.parentId,
    leadUserId: d.leadUserId,
    leadName: lead ? `${lead.firstName} ${lead.lastName}` : null,
    memberCount: db.employees.filter((e) => e.departmentId === d.id).length,
  };
}

function applyPatch(row: EmployeeRow, patch: Partial<Employee>): void {
  const fields: (keyof EmployeeRow & keyof Employee)[] = [
    "employeeNo", "jobTitle", "employmentType", "employmentStatus",
    "workLocation", "phone", "startDate", "managerId", "departmentId",
    "endDate", "skills", "rating",
  ];
  for (const f of fields) {
    if (patch[f] !== undefined) {
      // @ts-expect-error index assignment across the shared keys is safe here
      row[f] = patch[f] === "" ? null : patch[f];
    }
  }
}

// --- compensation (mock, in-memory; resets on reload) -----------------------
type MockCompEntry = {
  id: string; effectiveDate: string; annualAmount: number;
  changeType: string; reason: string | null; currency: string;
};
const mockComp: Record<string, MockCompEntry[]> = {};

// --- billing (mock, in-memory) ---------------------------------------------
interface MockSub { status: BillingOverview["status"]; price: number; currency: string; paidThrough: string | null; trialEndsAt: string; }
const mockSubscriptions: Record<string, MockSub> = {};
function mockSubscription(companyId: string): MockSub {
  return (mockSubscriptions[companyId] ??= {
    status: "TRIALING", price: 100, currency: "INR", paidThrough: null,
    trialEndsAt: new Date(Date.now() + 14 * 86_400_000).toISOString(),
  });
}
function buildBilling(db: DB, companyId: string): BillingOverview {
  const sub = mockSubscription(companyId);
  const billable = db.users.filter((u) => u.companyId === companyId && u.status === "ACTIVE").length;
  const monthly = sub.price * billable;
  const now = new Date();
  const currentMonth = now.toISOString().slice(0, 7);
  const employees = db.employees.filter((e) => e.companyId === companyId);
  const invoices = Array.from({ length: 6 }, (_, k) => {
    const d = new Date(now.getFullYear(), now.getMonth() - (5 - k) + 1, 0);
    const month = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    const headcount = employees.filter((e) => e.startDate && e.startDate <= d.toISOString().slice(0, 10)).length;
    const status: "PAID" | "DUE" | "OVERDUE" =
      sub.paidThrough && month <= sub.paidThrough ? "PAID" : month < currentMonth ? "OVERDUE" : "DUE";
    return { month, headcount, amount: sub.price * headcount, status };
  });
  return {
    plan: "PER_EMPLOYEE", status: sub.status, pricePerEmployee: sub.price,
    pricePerEmployeePerYear: sub.price * 12, currency: sub.currency,
    trialEndsAt: sub.trialEndsAt, trialActive: sub.status === "TRIALING",
    billableEmployees: billable, monthlyCharge: monthly, annualCharge: monthly * 12,
    currentMonth, paidThrough: sub.paidThrough, invoices,
  };
}

// --- recruitment (mock, in-memory) -----------------------------------------
interface MockJob {
  id: string; companyId: string; title: string; departmentId: string | null; location: string | null;
  employmentType: string | null; description: string | null; positions: number;
  status: JobOpening["status"]; createdAt: string;
}
const mockJobs: Record<string, MockJob[]> = {};
const mockCandidates: Record<string, Candidate[]> = {};

// --- attendance regularization (mock, in-memory, keyed by companyId) -------
const mockRegs: Record<string, Regularization[]> = {};

// --- HR helpdesk (mock, in-memory) -----------------------------------------
const mockTickets: Record<string, HelpdeskTicket[]> = {};   // keyed by companyId
const mockComments: Record<string, HelpdeskComment[]> = {}; // keyed by ticketId
function isHelpdeskAgent(user: User): boolean {
  return user.role === "ADMIN" || user.role === "HR" || user.role === "OWNER";
}

// --- shift scheduling (mock, in-memory, keyed by companyId) ----------------
const mockShifts: Record<string, Shift[]> = {};
const mockAssignments: Record<string, RosterEntry[]> = {};
function addDays(d: Date, n: number): Date {
  const c = new Date(d);
  c.setDate(c.getDate() + n);
  return c;
}
function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}
function mondayOf(d: Date): Date {
  const c = new Date(d);
  const dow = (c.getDay() + 6) % 7; // Mon=0 … Sun=6
  return addDays(c, -dow);
}
function renderJob(db: DB, j: MockJob): JobOpening {
  const cands = mockCandidates[j.id] ?? [];
  return {
    id: j.id, title: j.title, departmentId: j.departmentId,
    department: db.departments.find((d) => d.id === j.departmentId)?.name ?? null,
    location: j.location, employmentType: j.employmentType, description: j.description,
    positions: j.positions, status: j.status,
    candidateCount: cands.length, hiredCount: cands.filter((c) => c.stage === "HIRED").length,
    createdAt: j.createdAt,
  };
}
function findCandidate(id: string): Candidate | undefined {
  for (const jid of Object.keys(mockCandidates)) {
    const c = (mockCandidates[jid] ?? []).find((x) => x.id === id);
    if (c) return c;
  }
  return undefined;
}

// --- payslip template (mock, in-memory) ------------------------------------
const mockTemplates2: Record<string, PayslipComponent[]> = {};
function mockPayslipTemplate(companyId: string): PayslipComponent[] {
  return (mockTemplates2[companyId] ??= [
    { name: "Basic", kind: "EARNING", calc: "PERCENT_OF_GROSS", value: 50, basis: true, sortOrder: 0 },
    { name: "House rent allowance", kind: "EARNING", calc: "PERCENT_OF_GROSS", value: 25, basis: false, sortOrder: 1 },
    { name: "Special allowance", kind: "EARNING", calc: "REMAINDER", value: null, basis: false, sortOrder: 2 },
    { name: "Provident fund", kind: "DEDUCTION", calc: "PERCENT_OF_BASIC", value: 12, basis: false, sortOrder: 3 },
    { name: "Income tax", kind: "DEDUCTION", calc: "PERCENT_OF_GROSS", value: 10, basis: false, sortOrder: 4 },
  ]);
}
function validatePayslipTemplate(components: PayslipComponent[]): void {
  if (!components || components.length === 0) throw err(400, "VALIDATION_ERROR", "A payslip template needs at least one component");
  let earnings = 0, remainders = 0, bases = 0, usesBasis = false, earnPct = 0, dedPct = 0;
  for (const c of components) {
    if (!c.name?.trim()) throw err(400, "VALIDATION_ERROR", "Every component needs a name");
    if (c.calc === "REMAINDER") {
      if (c.kind !== "EARNING") throw err(400, "VALIDATION_ERROR", `"${c.name}": remainder is only valid on an earning`);
      remainders++;
    } else if (c.calc === "FIXED") {
      if (c.value == null || c.value < 0) throw err(400, "VALIDATION_ERROR", `"${c.name}": a fixed amount must be zero or more`);
    } else {
      if (c.value == null || c.value < 0 || c.value > 100) throw err(400, "VALIDATION_ERROR", `"${c.name}": a percentage must be between 0 and 100`);
    }
    if (c.kind === "EARNING") {
      earnings++;
      if (c.calc === "PERCENT_OF_BASIC") throw err(400, "VALIDATION_ERROR", `"${c.name}": an earning can't be a percent of basic`);
      if (c.calc === "PERCENT_OF_GROSS") earnPct += c.value ?? 0;
      if (c.basis) { bases++; if (c.calc === "REMAINDER") throw err(400, "VALIDATION_ERROR", `"${c.name}": the basis earning can't be the remainder`); }
    } else {
      if (c.calc === "PERCENT_OF_BASIC") usesBasis = true;
      if (c.calc === "PERCENT_OF_GROSS") dedPct += c.value ?? 0;
    }
  }
  if (earnings === 0) throw err(400, "VALIDATION_ERROR", "Add at least one earning");
  if (remainders > 1) throw err(400, "VALIDATION_ERROR", "Only one earning can be the remainder");
  if (bases > 1) throw err(400, "VALIDATION_ERROR", "Only one earning can be the basis for deductions");
  if (usesBasis && bases === 0) throw err(400, "VALIDATION_ERROR", "A percent-of-basic deduction needs one earning marked as the basis");
  if (earnPct > 100) throw err(400, "VALIDATION_ERROR", "Percentage earnings add up to more than 100% of gross");
  if (dedPct > 100) throw err(400, "VALIDATION_ERROR", "Percentage deductions add up to more than 100% of gross");
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

function buildCompensation(db: DB, employeeId: string): Compensation {
  const emp = db.employees.find((e) => e.id === employeeId);
  const u = emp && db.users.find((x) => x.id === emp.userId);
  const name = u ? `${u.firstName} ${u.lastName}` : "Employee";
  const records = (mockComp[employeeId] ?? []).slice().sort((a, b) => b.effectiveDate.localeCompare(a.effectiveDate));
  const history = records.map((r, i) => {
    const older = records[i + 1];
    const hikeAmount = older ? round2(r.annualAmount - older.annualAmount) : null;
    const hikePercent = older && older.annualAmount > 0 ? round2(((r.annualAmount - older.annualAmount) / older.annualAmount) * 100) : null;
    return { id: r.id, effectiveDate: r.effectiveDate, annualAmount: r.annualAmount, changeType: r.changeType, reason: r.reason, hikeAmount, hikePercent };
  });
  const current = records[0] ?? null;
  return {
    employeeId, employeeName: name, currency: current?.currency ?? "USD",
    currentAnnual: current?.annualAmount ?? null,
    currentMonthly: current ? round2(current.annualAmount / 12) : null,
    effectiveDate: current?.effectiveDate ?? null,
    history,
  };
}

// --- goals (mock, in-memory; resets on reload) ------------------------------
const mockGoals: Record<string, Goal[]> = {};

// --- performance reviews (mock, in-memory; mirrors PerformanceReviewService) -
interface ReviewCycleRow {
  id: string; companyId: string; name: string; periodStart: string; periodEnd: string;
  status: "OPEN" | "CLOSED"; createdAt: string;
}
interface ReviewRow {
  id: string; companyId: string; cycleId: string; employeeId: string; managerId: string | null;
  status: ReviewStatus;
  selfAssessment: string | null; selfSubmittedAt: string | null;
  rating: number | null; summary: string | null; strengths: string | null; improvements: string | null;
  hikeType: HikeType | null; hikePercent: number | null; proposedSalary: number | null; hikeNote: string | null;
  managerSubmittedAt: string | null; decidedAt: string | null;
}
const mockReviewCycles: ReviewCycleRow[] = [];
const mockReviews: ReviewRow[] = [];

function renderReview(db: DB, r: ReviewRow): PerformanceReview {
  const cycle = mockReviewCycles.find((c) => c.id === r.cycleId);
  const emp = db.employees.find((e) => e.id === r.employeeId);
  const empUser = emp && db.users.find((u) => u.id === emp.userId);
  const mgr = r.managerId ? db.employees.find((e) => e.id === r.managerId) : null;
  const mgrUser = mgr && db.users.find((u) => u.id === mgr.userId);
  const goals = mockGoals[r.employeeId] ?? [];
  const comp = buildCompensation(db, r.employeeId);
  return {
    id: r.id, cycleId: r.cycleId, cycleName: cycle?.name ?? "",
    periodStart: cycle?.periodStart ?? null, periodEnd: cycle?.periodEnd ?? null,
    cycleStatus: cycle?.status ?? null,
    employeeId: r.employeeId, employeeName: empUser ? `${empUser.firstName} ${empUser.lastName}` : "Employee",
    jobTitle: emp?.jobTitle ?? null,
    managerId: r.managerId, managerName: mgrUser ? `${mgrUser.firstName} ${mgrUser.lastName}` : null,
    status: r.status,
    selfAssessment: r.selfAssessment, selfSubmittedAt: r.selfSubmittedAt,
    rating: r.rating, summary: r.summary, strengths: r.strengths, improvements: r.improvements,
    hikeType: r.hikeType, hikePercent: r.hikePercent, proposedSalary: r.proposedSalary, hikeNote: r.hikeNote,
    managerSubmittedAt: r.managerSubmittedAt, decidedAt: r.decidedAt,
    currency: comp.currency, currentSalary: comp.currentAnnual,
    goalsAchieved: goals.filter((g) => g.status === "ACHIEVED").length,
    goalsTotal: goals.length, goals: goals.slice(),
  };
}

function requireReview(db: DB, user: User, reviewId: string): ReviewRow {
  const r = mockReviews.find((x) => x.id === reviewId && x.companyId === user.companyId);
  if (!r) throw err(404, "NOT_FOUND", "Review not found");
  return r;
}
function isReviewSelf(db: DB, user: User, r: ReviewRow): boolean {
  const emp = db.employees.find((e) => e.id === r.employeeId);
  return !!emp && emp.userId === user.id;
}
function isReviewManager(db: DB, user: User, r: ReviewRow): boolean {
  if (!r.managerId) return false;
  const mgr = db.employees.find((e) => e.id === r.managerId);
  return !!mgr && mgr.userId === user.id;
}
function canViewReview(db: DB, user: User, r: ReviewRow): boolean {
  const admin = user.role === "OWNER" || user.role === "ADMIN";
  return admin || isReviewSelf(db, user, r) || isReviewManager(db, user, r);
}
function requireCycleOpen(r: ReviewRow): void {
  const cycle = mockReviewCycles.find((c) => c.id === r.cycleId);
  if (cycle && cycle.status === "CLOSED") throw err(400, "VALIDATION_ERROR", "This review cycle is closed");
}

/** New annual salary a hike implies, or null when there's nothing to apply. */
function resolveReviewSalary(db: DB, r: ReviewRow): number | null {
  if (!r.hikeType || r.hikeType === "NONE") return null;
  if (r.hikeType === "NEW_SALARY") return r.proposedSalary ?? null;
  if (r.hikePercent == null) return null;
  const current = buildCompensation(db, r.employeeId).currentAnnual;
  if (!current || current <= 0) return null;
  return round2(current * (1 + r.hikePercent / 100));
}

// --- feed (mock, in-memory; resets on reload) -------------------------------
interface MockPost {
  id: string; authorId: string; kind: PostKind; body: string;
  visibility: PostVisibility; departmentId: string | null; pinned: boolean;
  reactions: { userId: string; emoji: string }[];
  comments: { id: string; authorId: string; body: string; createdAt: string }[];
  createdAt: string;
}
const mockPosts: Record<string, MockPost[]> = {};

/** Mirrors FeedService.canSee: company posts are open; team posts are for that team, the author and admins. */
function canSeePost(p: MockPost, userId: string, myDept: string | null, admin: boolean): boolean {
  if (p.visibility === "COMPANY") return true;
  return admin || p.authorId === userId || (myDept != null && myDept === p.departmentId);
}

function renderPost(db: DB, p: MockPost, viewer: User): Post {
  const nameOf = (userId: string) => {
    const u = db.users.find((x) => x.id === userId);
    return u ? `${u.firstName} ${u.lastName}` : "Someone";
  };
  const admin = viewer.role === "OWNER" || viewer.role === "ADMIN";
  const reactions: Record<string, number> = {};
  const myReactions: string[] = [];
  for (const r of p.reactions) {
    reactions[r.emoji] = (reactions[r.emoji] ?? 0) + 1;
    if (r.userId === viewer.id) myReactions.push(r.emoji);
  }
  const authorEmployee = db.employees.find((e) => e.userId === p.authorId);
  return {
    id: p.id,
    authorId: p.authorId,
    authorName: nameOf(p.authorId),
    authorTitle: authorEmployee?.jobTitle ?? null,
    kind: p.kind,
    body: p.body,
    visibility: p.visibility,
    departmentId: p.departmentId,
    departmentName: db.departments.find((d) => d.id === p.departmentId)?.name ?? null,
    pinned: p.pinned,
    reactions,
    myReactions,
    comments: p.comments.map((c) => ({
      id: c.id, authorId: c.authorId, authorName: nameOf(c.authorId), body: c.body,
      canDelete: c.authorId === viewer.id || admin, createdAt: c.createdAt,
    })),
    canManage: p.authorId === viewer.id || admin,
    createdAt: p.createdAt,
  };
}

// --- expenses (mock, in-memory; resets on reload) ---------------------------
const mockExpenses: Record<string, ExpenseClaim[]> = {};

function summarizeExpenses(db: DB, claims: ExpenseClaim[]): ExpenseSummary {
  const year = new Date().getFullYear();
  let pending = 0, awaiting = 0, reimbursed = 0;
  for (const c of claims) {
    if (c.status === "SUBMITTED") pending += c.amount;
    else if (c.status === "APPROVED") awaiting += c.amount;
    else if (c.status === "REIMBURSED" && c.reimbursedAt && new Date(c.reimbursedAt).getFullYear() === year) {
      reimbursed += c.amount;
    }
  }
  return {
    claims: claims.slice(),
    pendingAmount: pending,
    awaitingReimbursement: awaiting,
    reimbursedThisYear: reimbursed,
    currency: claims[0]?.currency ?? "INR",
  };
}

// --- notifications + holidays (mock, in-memory; resets on reload) -----------
/** Keyed by recipient user id, newest first. */
const mockNotifications: Record<string, AppNotification[]> = {};
const mockHolidays: Record<string, Holiday[]> = {};

/** Mirrors NotificationService.send: never notify someone about their own action. */
function notify(
  recipientUserId: string | null | undefined,
  actorUserId: string,
  type: NotificationType,
  title: string,
  body: string | null,
  link: string | null,
  entityType?: string,
  entityId?: string,
): void {
  if (!recipientUserId || recipientUserId === actorUserId) return;
  const n: AppNotification = {
    id: crypto.randomUUID(), type, title, body, link,
    entityType: entityType ?? null, entityId: entityId ?? null,
    read: false, createdAt: new Date().toISOString(),
  };
  mockNotifications[recipientUserId] = [n, ...(mockNotifications[recipientUserId] ?? [])];
}

/** Who approves this person's leave: their manager, else every Owner/Admin. */
function approversFor(db: DB, employee: EmployeeRow): string[] {
  const manager = db.employees.find((e) => e.id === employee.managerId);
  if (manager) return [manager.userId];
  return db.users
    .filter((u) => u.companyId === employee.companyId && (u.role === "OWNER" || u.role === "ADMIN"))
    .map((u) => u.id);
}

function weekdayOf(date: string): string {
  return new Date(`${date}T00:00:00`).toLocaleDateString("en", { weekday: "short" });
}
function withDaysAway(h: Holiday): Holiday {
  const ms = new Date(`${h.date}T00:00:00`).getTime() - new Date(`${todayIso()}T00:00:00`).getTime();
  return { ...h, weekday: weekdayOf(h.date), daysAway: Math.round(ms / 86_400_000) };
}

// --- attendance (mock, in-memory; resets on reload) -------------------------
type MockAttendance = {
  employeeId: string; date: string; status: AttendanceStatus;
  checkIn: string | null; checkOut: string | null; note: string | null;
};
/** Keyed "employeeId|date" — the same uniqueness the real table enforces. */
const mockAttendance: Record<string, MockAttendance> = {};

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
function nowTime(): string {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}
function attendanceRow(employeeId: string, date: string, fallback: AttendanceStatus): MockAttendance {
  const key = `${employeeId}|${date}`;
  return (mockAttendance[key] ??= {
    employeeId, date, status: fallback, checkIn: null, checkOut: null, note: null,
  });
}
function myEmployeeId(db: DB, user: User): string {
  const emp = db.employees.find((e) => e.userId === user.id && e.companyId === user.companyId);
  if (!emp) throw err(404, "NOT_FOUND", "No employee profile for this user");
  return emp.id;
}

/**
 * Mirrors `AttendanceService.resolve`: a marked row wins, then approved leave, then weekends,
 * else unmarked (null status).
 */
function resolveAttendance(db: DB, employeeId: string, date: string): AttendanceEntry {
  const emp = db.employees.find((e) => e.id === employeeId);
  const u = emp && db.users.find((x) => x.id === emp.userId);
  const base = {
    employeeId,
    employeeName: u ? `${u.firstName} ${u.lastName}` : "Employee",
    jobTitle: emp?.jobTitle ?? null,
    department: db.departments.find((d) => d.id === emp?.departmentId)?.name ?? null,
    date,
  };
  const marked = mockAttendance[`${employeeId}|${date}`];
  if (marked) {
    return { ...base, status: marked.status, checkIn: marked.checkIn, checkOut: marked.checkOut, note: marked.note, derived: false };
  }
  const leave = db.leave.find(
    (l) => l.employeeId === employeeId && l.status === "APPROVED" && l.startDate <= date && l.endDate >= date,
  );
  if (leave) {
    return {
      ...base, status: "ON_LEAVE", checkIn: null, checkOut: null,
      note: leave.type.toLowerCase() + (leave.reason ? ` · ${leave.reason}` : ""), derived: true,
    };
  }
  const dow = new Date(`${date}T00:00:00`).getDay();
  if (dow === 0 || dow === 6) {
    return { ...base, status: "WEEK_OFF", checkIn: null, checkOut: null, note: null, derived: true };
  }
  return { ...base, status: null, checkIn: null, checkOut: null, note: null, derived: true };
}

function buildAttendanceMonth(db: DB, employeeId: string, month?: string): AttendanceMonth {
  const ym = month || todayIso().slice(0, 7);
  const [year, mon] = ym.split("-").map(Number);
  const length = new Date(year, mon, 0).getDate();
  const today = todayIso();

  const days: AttendanceEntry[] = [];
  const counts: Record<string, number> = {};
  let worked = 0;
  let expected = 0;
  for (let d = 1; d <= length; d++) {
    const date = `${ym}-${String(d).padStart(2, "0")}`;
    const entry = resolveAttendance(db, employeeId, date);
    days.push(entry);
    if (!entry.status) continue;
    counts[entry.status] = (counts[entry.status] ?? 0) + 1;
    const nonWorkingDay = entry.status === "HOLIDAY" || entry.status === "WEEK_OFF";
    if (!nonWorkingDay && date <= today) {
      expected++;
      if (entry.status === "HALF_DAY") worked += 0.5;
      else if (entry.status === "PRESENT" || entry.status === "WORK_FROM_HOME") worked += 1;
    }
  }
  const emp = db.employees.find((e) => e.id === employeeId);
  const u = emp && db.users.find((x) => x.id === emp.userId);
  return {
    employeeId,
    employeeName: u ? `${u.firstName} ${u.lastName}` : "Employee",
    month: ym,
    days,
    counts,
    workedDays: Math.round(worked * 10) / 10,
    expectedDays: expected,
    attendanceRate: expected === 0 ? null : Math.round((worked * 1000) / expected) / 10,
  };
}

// --- documents (mock, in-memory; resets on reload) --------------------------
const mockTemplates: Record<string, DocumentTemplate[]> = {};
const mockDocs: Record<string, GeneratedDoc[]> = {};

/** Title falls back to "<template> — <person>", matching the backend. */
function docTitle(templateName: string, input: GenerateDocInput, values: Record<string, string>): string {
  if (input.title && input.title.trim()) return input.title.trim();
  const who = values["employee.fullName"];
  return who ? `${templateName} — ${who}` : templateName;
}

/**
 * Builds every merge value for a render (mirrors `DocumentService.resolve`): derived values first,
 * caller overrides last, so the issuer can always correct what the profile got wrong.
 */
function resolveMergeValues(db: DB, user: User, input: GenerateDocInput): Record<string, string> {
  const v: Record<string, string> = {};
  const put = (k: string, value: string | null | undefined) => {
    if (value != null && String(value).trim() !== "") v[k] = String(value);
  };

  put("today", letterDate(new Date().toISOString().slice(0, 10)));
  put("company.name", db.companies.find((c) => c.id === user.companyId)?.name);
  put("signatory.name", `${user.firstName} ${user.lastName}`);
  put("signatory.title", user.role === "OWNER" ? "Founder" : "People Operations");

  if (input.employeeId) {
    const e = db.employees.find((x) => x.id === input.employeeId && x.companyId === user.companyId);
    if (!e) throw err(404, "NOT_FOUND", "Employee not found");
    const u = db.users.find((x) => x.id === e.userId);
    if (u) {
      put("employee.fullName", `${u.firstName} ${u.lastName}`);
      put("employee.firstName", u.firstName);
      put("employee.lastName", u.lastName);
      put("employee.email", u.email);
    }
    put("employee.employeeNo", e.employeeNo);
    put("employee.jobTitle", e.jobTitle);
    put("employee.workLocation", e.workLocation);
    put("employee.phone", e.phone);
    put("employee.employmentType", e.employmentType ? prettyEnum(e.employmentType) : null);
    put("employee.startDate", letterDate(e.startDate));
    put("employee.endDate", letterDate(e.endDate ?? null));
    put("employee.tenure", tenure(e.startDate, e.endDate ?? null));
    put("employee.department", db.departments.find((d) => d.id === e.departmentId)?.name);
    const mgr = db.employees.find((x) => x.id === e.managerId);
    const mgrUser = mgr && db.users.find((x) => x.id === mgr.userId);
    if (mgrUser) put("employee.manager", `${mgrUser.firstName} ${mgrUser.lastName}`);

    const comp = buildCompensation(db, e.id);
    if (comp.currentAnnual != null) {
      put("salary.currency", comp.currency);
      put("salary.annual", comp.currentAnnual.toLocaleString("en-US"));
      put("salary.monthly", (comp.currentMonthly ?? 0).toLocaleString("en-US"));
      put("salary.effectiveDate", letterDate(comp.effectiveDate));
    }
  }

  for (const [k, value] of Object.entries(input.overrides ?? {})) put(k, value);
  return v;
}

/** FULL_TIME -> Full time. */
function prettyEnum(name: string): string {
  const s = name.replace(/_/g, " ").toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

// --- clients (mock, in-memory; resets on reload) ----------------------------
const mockClients: Record<string, Client[]> = {};
const mockClientReqs: Record<string, ClientRequestItem[]> = {};

function withOpen(companyId: string, c: Client): Client {
  void companyId;
  const reqs = mockClientReqs[c.id] ?? [];
  return { ...c, openRequests: reqs.filter((r) => r.status !== "DELIVERED" && r.status !== "DECLINED").length };
}
