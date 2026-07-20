"use client";

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
import { mockBackend, type MailMessage } from "@/lib/mock/backend";

// Frontend-first: mock is the default. Set NEXT_PUBLIC_API_MODE=live to hit the real backend.
const LIVE = process.env.NEXT_PUBLIC_API_MODE === "live";
const BASE = "/api/v1";

// Access token lives in memory only (Sprint1 §10) — never localStorage.
let accessToken: string | null = null;
export const auth = {
  get: () => accessToken,
  set: (t: string | null) => {
    accessToken = t;
  },
};

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(init?.headers ?? {}),
    },
  });
  if (res.status === 204) return undefined as T;
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    throw new ApiError((body as ApiErrorBody) ?? { timestamp: "", status: res.status, code: "INTERNAL_ERROR", message: "Request failed" });
  }
  return body as T;
}

export const api = {
  // --- auth / registration ---
  register(input: { companyName: string; firstName: string; lastName: string; email: string; password: string }) {
    return LIVE ? http<void>("/auth/register", { method: "POST", body: JSON.stringify(input) }) : mockBackend.register(input);
  },
  verifyEmail(token: string) {
    return LIVE ? http<void>("/auth/verify-email", { method: "POST", body: JSON.stringify({ token }) }) : mockBackend.verifyEmail(token);
  },
  resendVerification(email: string) {
    return LIVE
      ? http<void>("/auth/resend-verification", { method: "POST", body: JSON.stringify({ email }) })
      : mockBackend.resendVerification(email);
  },
  async login(email: string, password: string): Promise<LoginResult> {
    const result = LIVE
      ? await http<LoginResult>("/auth/login", { method: "POST", body: JSON.stringify({ email, password }) })
      : await mockBackend.login(email, password);
    auth.set(result.accessToken);
    return result;
  },
  async refresh(): Promise<LoginResult> {
    const result = LIVE
      ? await http<LoginResult>("/auth/refresh", { method: "POST" })
      : await mockBackend.refresh();
    auth.set(result.accessToken);
    return result;
  },
  async logout() {
    try {
      if (LIVE) await http<void>("/auth/logout", { method: "POST" });
      else await mockBackend.logout(accessToken);
    } finally {
      auth.set(null);
    }
  },
  me(): Promise<Me> {
    return LIVE ? http<Me>("/auth/me") : mockBackend.me(accessToken);
  },

  // --- dashboard ---
  dashboardSummary(): Promise<DashboardSummary> {
    return LIVE ? http<DashboardSummary>("/dashboard/summary") : mockBackend.dashboardSummary(accessToken);
  },

  // --- company / settings ---
  getSettings(): Promise<CompanySettings> {
    return LIVE ? http<CompanySettings>("/company/settings") : mockBackend.getSettings(accessToken);
  },
  updateSettings(patch: { timezone: string; locale: string; logoUrl?: string }): Promise<CompanySettings> {
    return LIVE
      ? http<CompanySettings>("/company/settings", { method: "PATCH", body: JSON.stringify(patch) })
      : mockBackend.updateSettings(accessToken, patch);
  },
  listMembers(): Promise<Member[]> {
    return LIVE ? http<Member[]>("/company/members") : mockBackend.listMembers(accessToken);
  },

  // --- invitations ---
  listInvitations(): Promise<Invitation[]> {
    return LIVE ? http<Invitation[]>("/invitations") : mockBackend.listInvitations(accessToken);
  },
  createInvitation(email: string, role: "ADMIN" | "MEMBER"): Promise<Invitation> {
    return LIVE
      ? http<Invitation>("/invitations", { method: "POST", body: JSON.stringify({ email, role }) })
      : mockBackend.createInvitation(accessToken, email, role);
  },
  revokeInvitation(id: string): Promise<void> {
    return LIVE ? http<void>(`/invitations/${id}`, { method: "DELETE" }) : mockBackend.revokeInvitation(accessToken, id);
  },
  invitationPreview(token: string): Promise<{ email: string; companyName: string; role: Role }> {
    return LIVE
      ? http<{ email: string; companyName: string; role: Role }>(`/invitations/preview?token=${encodeURIComponent(token)}`)
      : mockBackend.invitationPreview(token);
  },
  acceptInvitation(input: { token: string; firstName: string; lastName: string; password: string }) {
    return LIVE
      ? http<void>("/invitations/accept", { method: "POST", body: JSON.stringify(input) })
      : mockBackend.acceptInvitation(input);
  },

  // --- People OS (employees) ---
  listEmployees(): Promise<Employee[]> {
    return LIVE ? http<Employee[]>("/people/employees") : mockBackend.listEmployees(accessToken);
  },
  getEmployee(id: string): Promise<Employee> {
    return LIVE ? http<Employee>(`/people/employees/${id}`) : mockBackend.getEmployee(accessToken, id);
  },
  getMyProfile(): Promise<Employee> {
    return LIVE ? http<Employee>("/people/me") : mockBackend.getMyProfile(accessToken);
  },
  updateEmployee(id: string, patch: Partial<Employee>): Promise<Employee> {
    return LIVE
      ? http<Employee>(`/people/employees/${id}`, { method: "PATCH", body: JSON.stringify(patch) })
      : mockBackend.updateEmployee(accessToken, id, patch);
  },
  updateMyProfile(patch: { phone?: string; workLocation?: string }): Promise<Employee> {
    return LIVE
      ? http<Employee>("/people/me", { method: "PATCH", body: JSON.stringify(patch) })
      : mockBackend.updateMyProfile(accessToken, patch);
  },

  // --- People OS (departments) ---
  listDepartments(): Promise<Department[]> {
    return LIVE ? http<Department[]>("/people/departments") : mockBackend.listDepartments(accessToken);
  },
  createDepartment(input: { name: string; parentId?: string; leadUserId?: string }): Promise<Department> {
    return LIVE
      ? http<Department>("/people/departments", { method: "POST", body: JSON.stringify(input) })
      : mockBackend.createDepartment(accessToken, input);
  },
  updateDepartment(id: string, patch: { name?: string; parentId?: string; leadUserId?: string }): Promise<Department> {
    return LIVE
      ? http<Department>(`/people/departments/${id}`, { method: "PATCH", body: JSON.stringify(patch) })
      : mockBackend.updateDepartment(accessToken, id, patch);
  },
  deleteDepartment(id: string): Promise<void> {
    return LIVE
      ? http<void>(`/people/departments/${id}`, { method: "DELETE" })
      : mockBackend.deleteDepartment(accessToken, id);
  },

  // --- People OS (onboarding) ---
  listOnboarding(employeeId: string): Promise<OnboardingTask[]> {
    return LIVE
      ? http<OnboardingTask[]>(`/people/employees/${employeeId}/onboarding`)
      : mockBackend.listOnboarding(accessToken, employeeId);
  },
  addOnboardingTask(employeeId: string, title: string): Promise<OnboardingTask> {
    return LIVE
      ? http<OnboardingTask>(`/people/employees/${employeeId}/onboarding`, { method: "POST", body: JSON.stringify({ title }) })
      : mockBackend.addOnboardingTask(accessToken, employeeId, title);
  },
  seedOnboardingDefaults(employeeId: string): Promise<OnboardingTask[]> {
    return LIVE
      ? http<OnboardingTask[]>(`/people/employees/${employeeId}/onboarding/seed-defaults`, { method: "POST" })
      : mockBackend.seedOnboardingDefaults(accessToken, employeeId);
  },
  toggleOnboardingTask(taskId: string, completed: boolean): Promise<OnboardingTask> {
    return LIVE
      ? http<OnboardingTask>(`/people/onboarding/${taskId}`, { method: "PATCH", body: JSON.stringify({ completed }) })
      : mockBackend.toggleOnboardingTask(accessToken, taskId, completed);
  },
  deleteOnboardingTask(taskId: string): Promise<void> {
    return LIVE
      ? http<void>(`/people/onboarding/${taskId}`, { method: "DELETE" })
      : mockBackend.deleteOnboardingTask(accessToken, taskId);
  },

  // --- People OS (leave / time-off) ---
  requestLeave(input: { type: string; startDate: string; endDate: string; reason?: string }): Promise<LeaveRequest> {
    return LIVE
      ? http<LeaveRequest>("/people/leave", { method: "POST", body: JSON.stringify(input) })
      : mockBackend.requestLeave(accessToken, input);
  },
  myLeave(): Promise<LeaveRequest[]> {
    return LIVE ? http<LeaveRequest[]>("/people/leave/mine") : mockBackend.myLeave(accessToken);
  },
  leaveBalance(): Promise<LeaveBalance> {
    return LIVE ? http<LeaveBalance>("/people/leave/balance") : mockBackend.leaveBalance(accessToken);
  },
  allLeave(): Promise<LeaveRequest[]> {
    return LIVE ? http<LeaveRequest[]>("/people/leave") : mockBackend.allLeave(accessToken);
  },
  approveLeave(id: string): Promise<LeaveRequest> {
    return LIVE ? http<LeaveRequest>(`/people/leave/${id}/approve`, { method: "POST" }) : mockBackend.decideLeave(accessToken, id, "APPROVED");
  },
  rejectLeave(id: string): Promise<LeaveRequest> {
    return LIVE ? http<LeaveRequest>(`/people/leave/${id}/reject`, { method: "POST" }) : mockBackend.decideLeave(accessToken, id, "REJECTED");
  },
  cancelLeave(id: string): Promise<LeaveRequest> {
    return LIVE ? http<LeaveRequest>(`/people/leave/${id}/cancel`, { method: "POST" }) : mockBackend.cancelLeave(accessToken, id);
  },

  // --- Work OS (projects) ---
  listProjects(): Promise<Project[]> {
    return LIVE ? http<Project[]>("/work/projects") : mockBackend.listProjects(accessToken);
  },
  getProject(id: string): Promise<Project> {
    return LIVE ? http<Project>(`/work/projects/${id}`) : mockBackend.getProject(accessToken, id);
  },
  createProject(input: { name: string; key: string; description?: string }): Promise<Project> {
    return LIVE
      ? http<Project>("/work/projects", { method: "POST", body: JSON.stringify(input) })
      : mockBackend.createProject(accessToken, input);
  },
  archiveProject(id: string): Promise<Project> {
    return LIVE ? http<Project>(`/work/projects/${id}/archive`, { method: "POST" }) : mockBackend.archiveProject(accessToken, id);
  },

  // --- Work OS (tasks) ---
  listTasks(projectId: string): Promise<Task[]> {
    return LIVE ? http<Task[]>(`/work/projects/${projectId}/tasks`) : mockBackend.listTasks(accessToken, projectId);
  },
  createTask(projectId: string, input: { title: string; description?: string; priority?: string; assigneeId?: string; dueDate?: string }): Promise<Task> {
    return LIVE
      ? http<Task>(`/work/projects/${projectId}/tasks`, { method: "POST", body: JSON.stringify(input) })
      : mockBackend.createTask(accessToken, projectId, input);
  },
  updateTask(id: string, patch: { title?: string; description?: string; status?: string; priority?: string; assigneeId?: string; dueDate?: string }): Promise<Task> {
    return LIVE
      ? http<Task>(`/work/tasks/${id}`, { method: "PATCH", body: JSON.stringify(patch) })
      : mockBackend.updateTask(accessToken, id, patch);
  },
  deleteTask(id: string): Promise<void> {
    return LIVE ? http<void>(`/work/tasks/${id}`, { method: "DELETE" }) : mockBackend.deleteTask(accessToken, id);
  },
  myTasks(): Promise<Task[]> {
    return LIVE ? http<Task[]>("/work/tasks/mine") : mockBackend.myTasks(accessToken);
  },

  // --- Knowledge OS (spaces) ---
  listSpaces(): Promise<Space[]> {
    return LIVE ? http<Space[]>("/knowledge/spaces") : mockBackend.listSpaces(accessToken);
  },
  getSpace(id: string): Promise<Space> {
    return LIVE ? http<Space>(`/knowledge/spaces/${id}`) : mockBackend.getSpace(accessToken, id);
  },
  createSpace(input: { name: string; key: string; description?: string }): Promise<Space> {
    return LIVE
      ? http<Space>("/knowledge/spaces", { method: "POST", body: JSON.stringify(input) })
      : mockBackend.createSpace(accessToken, input);
  },
  archiveSpace(id: string): Promise<Space> {
    return LIVE ? http<Space>(`/knowledge/spaces/${id}/archive`, { method: "POST" }) : mockBackend.archiveSpace(accessToken, id);
  },

  // --- Knowledge OS (pages) ---
  listPages(spaceId: string): Promise<PageSummary[]> {
    return LIVE ? http<PageSummary[]>(`/knowledge/spaces/${spaceId}/pages`) : mockBackend.listPages(accessToken, spaceId);
  },
  getPage(id: string): Promise<KnowledgePage> {
    return LIVE ? http<KnowledgePage>(`/knowledge/pages/${id}`) : mockBackend.getPage(accessToken, id);
  },
  createPage(spaceId: string, input: { title: string; body?: string; parentId?: string; linkedTaskId?: string }): Promise<KnowledgePage> {
    return LIVE
      ? http<KnowledgePage>(`/knowledge/spaces/${spaceId}/pages`, { method: "POST", body: JSON.stringify(input) })
      : mockBackend.createPage(accessToken, spaceId, input);
  },
  updatePage(id: string, patch: { title?: string; body?: string; status?: string; parentId?: string; linkedTaskId?: string }): Promise<KnowledgePage> {
    return LIVE
      ? http<KnowledgePage>(`/knowledge/pages/${id}`, { method: "PATCH", body: JSON.stringify(patch) })
      : mockBackend.updatePage(accessToken, id, patch);
  },
  deletePage(id: string): Promise<void> {
    return LIVE ? http<void>(`/knowledge/pages/${id}`, { method: "DELETE" }) : mockBackend.deletePage(accessToken, id);
  },
  myPages(): Promise<PageSummary[]> {
    return LIVE ? http<PageSummary[]>("/knowledge/pages/mine") : mockBackend.myPages(accessToken);
  },
  searchPages(q: string): Promise<PageSummary[]> {
    return LIVE
      ? http<PageSummary[]>(`/knowledge/search?q=${encodeURIComponent(q)}`)
      : mockBackend.searchPages(accessToken, q);
  },

  // --- dev mailbox (local dev only: verification/invite links, no SMTP needed) ---
  devMailbox(): Promise<MailMessage[]> {
    return LIVE ? http<MailMessage[]>("/dev/mailbox") : Promise.resolve(mockBackend.mailbox());
  },
  clearDevMailbox(): Promise<void> {
    if (LIVE) return http<void>("/dev/mailbox", { method: "DELETE" });
    mockBackend.reset();
    return Promise.resolve();
  },
};

export { ApiError };
