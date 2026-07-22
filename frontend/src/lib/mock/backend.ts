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
  type WorkItem,
  type Goal,
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
    company: { id: company.id, name: company.name, slug: company.slug, status: company.status },
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
    db.settings.push({ companyId: company.id, timezone: "UTC", locale: "en", logoUrl: null });
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
    return {
      employeeId, employeeName: comp.employeeName, month: month || new Date().toISOString().slice(0, 7), currency: comp.currency,
      earnings: [{ label: "Basic", amount: basic }, { label: "House rent allowance", amount: hra }, { label: "Special allowance", amount: special }],
      deductions: [{ label: "Provident fund", amount: pf }, { label: "Income tax", amount: tax }],
      gross, totalDeductions: round2(pf + tax), net: round2(gross - pf - tax),
    };
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
      outToday,
      monthLeaves,
    };
  },

  async getSettings(accessToken: string | null): Promise<CompanySettings> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    return db.settings.find((s) => s.companyId === user.companyId)!;
  },

  async updateSettings(
    accessToken: string | null,
    patch: { timezone: string; locale: string; logoUrl?: string },
  ): Promise<CompanySettings> {
    await delay();
    const db = load();
    const user = requireSession(db, accessToken);
    requireAdmin(user);
    const settings = db.settings.find((s) => s.companyId === user.companyId)!;
    settings.timezone = patch.timezone;
    settings.locale = patch.locale;
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
    role: "ADMIN" | "MEMBER",
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

    const groups: SearchGroup[] = [];
    if (people.length) groups.push({ label: "People", hits: people });
    if (work.length) groups.push({ label: "Work", hits: work });
    if (knowledge.length) groups.push({ label: "Knowledge", hits: knowledge });
    return { query, total: people.length + work.length + knowledge.length, groups };
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
