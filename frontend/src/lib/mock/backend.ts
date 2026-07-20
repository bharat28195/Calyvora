// In-browser mock of the Calyvora backend (Sprint1 §7 contract).
// Used for frontend-first development: the whole golden path runs with no Java backend.
// State persists in localStorage; "emails" land in a mock mailbox (see /dev/mailbox).
// Swapped out for the real backend by setting NEXT_PUBLIC_API_MODE=live.

import {
  ApiError,
  type ApiErrorBody,
  type CompanySettings,
  type DashboardSummary,
  type Department,
  type Employee,
  type Invitation,
  type LeaveBalance,
  type LeaveRequest,
  type LoginResult,
  type OnboardingTask,
  type Project,
  type Task,
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
  dueDate: string | null;
  sortOrder: number;
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
    return { companies: [], users: [], tokens: [], invitations: [], settings: [], employees: [], departments: [], onboarding: [], leave: [], projects: [], tasks: [], spaces: [], pages: [], sessions: {}, mailbox: [] };
  }
  const raw = window.localStorage.getItem(KEY);
  if (!raw) {
    const fresh: DB = { companies: [], users: [], tokens: [], invitations: [], settings: [], employees: [], departments: [], onboarding: [], leave: [], projects: [], tasks: [], spaces: [], pages: [], sessions: {}, mailbox: [] };
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
    return {
      companyName: company.name,
      memberCount: db.users.filter((u) => u.companyId === user.companyId && u.status === "ACTIVE").length,
      pendingInviteCount: db.invitations.filter(
        (i) => i.status === "PENDING" && companyOfInvite(db, i.id) === user.companyId,
      ).length,
      yourRole: user.role,
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
      assigneeId: input.assigneeId || null, dueDate: input.dueDate || null, sortOrder: number, createdAt: new Date().toISOString(),
    };
    db.tasks.push(row);
    save(db);
    return toTask(db, row, project);
  },
  async updateTask(accessToken: string | null, id: string, patch: { title?: string; description?: string; status?: string; priority?: string; assigneeId?: string; dueDate?: string }): Promise<Task> {
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
    dueDate: t.dueDate,
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
  ];
  for (const f of fields) {
    if (patch[f] !== undefined) {
      // @ts-expect-error index assignment across the shared keys is safe here
      row[f] = patch[f] === "" ? null : patch[f];
    }
  }
}
