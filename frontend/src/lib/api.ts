"use client";

import {
  ApiError,
  type ApiErrorBody,
  type CompanySettings,
  type DashboardSummary,
  type TeamOverview,
  type AnalyticsOverview,
  type BillingOverview,
  type PayslipComponent,
  type Page,
  type JobOpening,
  type JobOpeningInput,
  type Candidate,
  type CandidateInput,
  type Shift,
  type ShiftInput,
  type Roster,
  type RosterEntry,
  type CompanySummary,
  type CreateCompanyInput,
  type SeatRequest,
  type SubscriptionView,
  type Compensation,
  type Payslip,
  type Department,
  type Employee,
  type WorkItem,
  type Goal,
  type ReviewCycle,
  type PerformanceReview,
  type CreateCycleInput,
  type SelfAssessmentInput,
  type ManagerReviewInput,
  type Client,
  type ClientDetail,
  type ClientRequestItem,
  type AppNotification,
  type Post,
  type PostInput,
  type SprintReport,
  type Velocity,
  type ExpenseClaim,
  type ExpenseInput,
  type ExpenseSummary,
  type Holiday,
  type AttendanceDay,
  type AttendanceEntry,
  type AttendanceMonth,
  type MarkAttendanceInput,
  type DocumentTemplate,
  type DocumentPreview,
  type GeneratedDoc,
  type GenerateDocInput,
  type MergeField,
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
  type SearchResponse,
  type AssistantResponse,
} from "@/lib/types";
import { mockBackend, type MailMessage } from "@/lib/mock/backend";

// Frontend-first: mock is the default. Set NEXT_PUBLIC_API_MODE=live to hit the real backend.
const LIVE = process.env.NEXT_PUBLIC_API_MODE === "live";
/** True when the app is wired to the real backend (enables demo seeding, dev mailbox, etc.). */
export const isLive = LIVE;
const BASE = "/api/v1";

/** Credentials returned by the one-click demo seed. */
export type DemoCredentials = { companyName: string; email: string; password: string; alreadySeeded: boolean };

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
  /** One-click demo: provision a populated company (live backend), then sign in as its owner. */
  async seedDemo(): Promise<DemoCredentials> {
    if (!LIVE) {
      throw new ApiError({
        timestamp: "", status: 400, code: "VALIDATION_ERROR",
        message: "Demo data needs the live backend. Set NEXT_PUBLIC_API_MODE=live and start the backend.",
      });
    }
    const creds = await http<DemoCredentials>("/dev/seed-demo", { method: "POST" });
    const result = await http<LoginResult>("/auth/login", {
      method: "POST", body: JSON.stringify({ email: creds.email, password: creds.password }),
    });
    auth.set(result.accessToken);
    return creds;
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
  teamOverview(): Promise<TeamOverview> {
    return LIVE ? http<TeamOverview>("/dashboard/team") : mockBackend.teamOverview(accessToken);
  },
  analyticsOverview(): Promise<AnalyticsOverview> {
    return LIVE ? http<AnalyticsOverview>("/analytics/overview") : mockBackend.analyticsOverview(accessToken);
  },
  // --- billing (subscription: per employee, per month) ---
  billingOverview(): Promise<BillingOverview> {
    return LIVE ? http<BillingOverview>("/billing") : mockBackend.billingOverview(accessToken);
  },
  activateSubscription(): Promise<BillingOverview> {
    return LIVE ? http<BillingOverview>("/billing/activate", { method: "POST" }) : mockBackend.activateSubscription(accessToken);
  },
  payInvoice(month: string): Promise<BillingOverview> {
    return LIVE ? http<BillingOverview>(`/billing/invoices/${month}/pay`, { method: "POST" }) : mockBackend.payInvoice(accessToken, month);
  },
  // --- recruitment / ATS ---
  jobs(): Promise<JobOpening[]> {
    return LIVE ? http<JobOpening[]>("/recruit/jobs") : mockBackend.jobs(accessToken);
  },
  job(id: string): Promise<JobOpening> {
    return LIVE ? http<JobOpening>(`/recruit/jobs/${id}`) : mockBackend.job(accessToken, id);
  },
  createJob(input: JobOpeningInput): Promise<JobOpening> {
    return LIVE ? http<JobOpening>("/recruit/jobs", { method: "POST", body: JSON.stringify(input) }) : mockBackend.createJob(accessToken, input);
  },
  updateJob(id: string, input: Partial<JobOpeningInput>): Promise<JobOpening> {
    return LIVE ? http<JobOpening>(`/recruit/jobs/${id}`, { method: "PATCH", body: JSON.stringify(input) }) : mockBackend.updateJob(accessToken, id, input);
  },
  deleteJob(id: string): Promise<void> {
    return LIVE ? http<void>(`/recruit/jobs/${id}`, { method: "DELETE" }) : mockBackend.deleteJob(accessToken, id);
  },
  candidates(jobId: string): Promise<Candidate[]> {
    return LIVE ? http<Candidate[]>(`/recruit/jobs/${jobId}/candidates`) : mockBackend.candidates(accessToken, jobId);
  },
  addCandidate(jobId: string, input: CandidateInput): Promise<Candidate> {
    return LIVE ? http<Candidate>(`/recruit/jobs/${jobId}/candidates`, { method: "POST", body: JSON.stringify(input) }) : mockBackend.addCandidate(accessToken, jobId, input);
  },
  updateCandidate(id: string, input: Partial<CandidateInput>): Promise<Candidate> {
    return LIVE ? http<Candidate>(`/recruit/candidates/${id}`, { method: "PATCH", body: JSON.stringify(input) }) : mockBackend.updateCandidate(accessToken, id, input);
  },
  moveCandidate(id: string, stage: string): Promise<Candidate> {
    return LIVE ? http<Candidate>(`/recruit/candidates/${id}/move`, { method: "POST", body: JSON.stringify({ stage }) }) : mockBackend.moveCandidate(accessToken, id, stage);
  },
  deleteCandidate(id: string): Promise<void> {
    return LIVE ? http<void>(`/recruit/candidates/${id}`, { method: "DELETE" }) : mockBackend.deleteCandidate(accessToken, id);
  },

  // --- platform owner (vendor) console — live backend only ---
  platformCompanies(): Promise<CompanySummary[]> {
    return LIVE ? http<CompanySummary[]>("/platform/companies") : Promise.reject(new Error("The platform console requires the live backend."));
  },
  createCompany(input: CreateCompanyInput): Promise<CompanySummary> {
    return LIVE ? http<CompanySummary>("/platform/companies", { method: "POST", body: JSON.stringify(input) }) : Promise.reject(new Error("The platform console requires the live backend."));
  },
  endCompanySubscription(companyId: string): Promise<CompanySummary> {
    return LIVE ? http<CompanySummary>(`/platform/companies/${companyId}/end`, { method: "POST" }) : Promise.reject(new Error("live only"));
  },
  renewCompanySubscription(companyId: string, months: number): Promise<CompanySummary> {
    return LIVE ? http<CompanySummary>(`/platform/companies/${companyId}/renew`, { method: "POST", body: JSON.stringify({ months }) }) : Promise.reject(new Error("live only"));
  },
  setCompanySeats(companyId: string, seats: number): Promise<CompanySummary> {
    return LIVE ? http<CompanySummary>(`/platform/companies/${companyId}/seats`, { method: "POST", body: JSON.stringify({ seats }) }) : Promise.reject(new Error("live only"));
  },
  platformSeatRequests(): Promise<SeatRequest[]> {
    return LIVE ? http<SeatRequest[]>("/platform/seat-requests") : Promise.reject(new Error("live only"));
  },
  approveSeatRequest(id: string): Promise<CompanySummary> {
    return LIVE ? http<CompanySummary>(`/platform/seat-requests/${id}/approve`, { method: "POST" }) : Promise.reject(new Error("live only"));
  },
  declineSeatRequest(id: string): Promise<void> {
    return LIVE ? http<void>(`/platform/seat-requests/${id}/decline`, { method: "POST" }) : Promise.reject(new Error("live only"));
  },

  // --- a company's own subscription (admin read-only + app-lock check) ---
  mySubscription(): Promise<SubscriptionView> {
    return LIVE
      ? http<SubscriptionView>("/subscription/me")
      : Promise.resolve({ status: "NONE", seats: 0, seatsUsed: 0, endsAt: null, daysLeft: null, locked: false, pendingRequestSeats: null, pricePerEmployee: null, currency: "INR" });
  },
  requestSeats(seats: number, note?: string): Promise<SubscriptionView> {
    return LIVE ? http<SubscriptionView>("/subscription/request-seats", { method: "POST", body: JSON.stringify({ seats, note }) }) : Promise.reject(new Error("live only"));
  },

  // --- shift scheduling / rostering ---
  shifts(): Promise<Shift[]> {
    return LIVE ? http<Shift[]>("/shifts") : mockBackend.shifts(accessToken);
  },
  createShift(input: ShiftInput): Promise<Shift> {
    return LIVE ? http<Shift>("/shifts", { method: "POST", body: JSON.stringify(input) }) : mockBackend.createShift(accessToken, input);
  },
  updateShift(id: string, input: Partial<ShiftInput>): Promise<Shift> {
    return LIVE ? http<Shift>(`/shifts/${id}`, { method: "PATCH", body: JSON.stringify(input) }) : mockBackend.updateShift(accessToken, id, input);
  },
  deleteShift(id: string): Promise<void> {
    return LIVE ? http<void>(`/shifts/${id}`, { method: "DELETE" }) : mockBackend.deleteShift(accessToken, id);
  },
  roster(weekStart?: string): Promise<Roster> {
    const qs = weekStart ? `?weekStart=${weekStart}` : "";
    return LIVE ? http<Roster>(`/shifts/roster${qs}`) : mockBackend.roster(accessToken, weekStart);
  },
  assignShift(employeeId: string, onDate: string, shiftId: string): Promise<RosterEntry> {
    return LIVE
      ? http<RosterEntry>("/shifts/roster/assign", { method: "POST", body: JSON.stringify({ employeeId, onDate, shiftId }) })
      : mockBackend.assignShift(accessToken, employeeId, onDate, shiftId);
  },
  unassignShift(id: string): Promise<void> {
    return LIVE ? http<void>(`/shifts/roster/assign/${id}`, { method: "DELETE" }) : mockBackend.unassignShift(accessToken, id);
  },

  // --- payslip template ---
  payslipTemplate(): Promise<PayslipComponent[]> {
    return LIVE ? http<PayslipComponent[]>("/payroll/payslip-template") : mockBackend.payslipTemplate(accessToken);
  },
  savePayslipTemplate(components: PayslipComponent[]): Promise<PayslipComponent[]> {
    return LIVE
      ? http<PayslipComponent[]>("/payroll/payslip-template", { method: "PUT", body: JSON.stringify({ components }) })
      : mockBackend.savePayslipTemplate(accessToken, components);
  },

  // --- company / settings ---
  getSettings(): Promise<CompanySettings> {
    return LIVE ? http<CompanySettings>("/company/settings") : mockBackend.getSettings(accessToken);
  },
  updateSettings(patch: { timezone: string; locale: string; currency: string; logoUrl?: string }): Promise<CompanySettings> {
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
  createInvitation(email: string, role: "ADMIN" | "HR" | "MANAGER" | "MEMBER"): Promise<Invitation> {
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
  employeeWork(employeeId: string): Promise<WorkItem[]> {
    return LIVE ? http<WorkItem[]>(`/people/employees/${employeeId}/work`) : mockBackend.employeeWork(accessToken, employeeId);
  },
  employeeGoals(employeeId: string): Promise<Goal[]> {
    return LIVE ? http<Goal[]>(`/people/employees/${employeeId}/goals`) : mockBackend.employeeGoals(accessToken, employeeId);
  },
  createGoal(employeeId: string, input: { title: string; description?: string; targetDate?: string }): Promise<Goal> {
    return LIVE ? http<Goal>(`/people/employees/${employeeId}/goals`, { method: "POST", body: JSON.stringify(input) })
      : mockBackend.createGoal(accessToken, employeeId, input);
  },
  updateGoal(employeeId: string, goalId: string, patch: Partial<Pick<Goal, "title" | "description" | "status" | "progress" | "targetDate">>): Promise<Goal> {
    return LIVE ? http<Goal>(`/people/employees/${employeeId}/goals/${goalId}`, { method: "PATCH", body: JSON.stringify(patch) })
      : mockBackend.updateGoal(accessToken, employeeId, goalId, patch);
  },
  deleteGoal(employeeId: string, goalId: string): Promise<void> {
    return LIVE ? http<void>(`/people/employees/${employeeId}/goals/${goalId}`, { method: "DELETE" })
      : mockBackend.deleteGoal(accessToken, employeeId, goalId);
  },

  // --- Performance reviews (feedback C.7) ---
  reviewCycles(): Promise<ReviewCycle[]> {
    return LIVE ? http<ReviewCycle[]>("/performance/cycles") : mockBackend.reviewCycles(accessToken);
  },
  createReviewCycle(input: CreateCycleInput): Promise<ReviewCycle> {
    return LIVE ? http<ReviewCycle>("/performance/cycles", { method: "POST", body: JSON.stringify(input) })
      : mockBackend.createReviewCycle(accessToken, input);
  },
  cycleReviews(cycleId: string): Promise<PerformanceReview[]> {
    return LIVE ? http<PerformanceReview[]>(`/performance/cycles/${cycleId}/reviews`)
      : mockBackend.cycleReviews(accessToken, cycleId);
  },
  closeReviewCycle(cycleId: string): Promise<ReviewCycle> {
    return LIVE ? http<ReviewCycle>(`/performance/cycles/${cycleId}/close`, { method: "POST" })
      : mockBackend.closeReviewCycle(accessToken, cycleId);
  },
  myReviews(): Promise<PerformanceReview[]> {
    return LIVE ? http<PerformanceReview[]>("/performance/me/reviews") : mockBackend.myReviews(accessToken);
  },
  teamReviews(): Promise<PerformanceReview[]> {
    return LIVE ? http<PerformanceReview[]>("/performance/team/reviews") : mockBackend.teamReviews(accessToken);
  },
  getReview(reviewId: string): Promise<PerformanceReview> {
    return LIVE ? http<PerformanceReview>(`/performance/reviews/${reviewId}`) : mockBackend.getReview(accessToken, reviewId);
  },
  saveSelfAssessment(reviewId: string, input: SelfAssessmentInput): Promise<PerformanceReview> {
    return LIVE ? http<PerformanceReview>(`/performance/reviews/${reviewId}/self`, { method: "PATCH", body: JSON.stringify(input) })
      : mockBackend.saveSelfAssessment(accessToken, reviewId, input);
  },
  saveManagerReview(reviewId: string, input: ManagerReviewInput): Promise<PerformanceReview> {
    return LIVE ? http<PerformanceReview>(`/performance/reviews/${reviewId}/manager`, { method: "PATCH", body: JSON.stringify(input) })
      : mockBackend.saveManagerReview(accessToken, reviewId, input);
  },
  approveReview(reviewId: string): Promise<PerformanceReview> {
    return LIVE ? http<PerformanceReview>(`/performance/reviews/${reviewId}/approve`, { method: "POST" })
      : mockBackend.approveReview(accessToken, reviewId);
  },
  listEmployees(): Promise<Employee[]> {
    return LIVE ? http<Employee[]>("/people/employees") : mockBackend.listEmployees(accessToken);
  },
  /** Paged, server-searched directory — scales to large companies. */
  directoryPage(q: string, page: number, size = 24): Promise<Page<Employee>> {
    const qs = `?q=${encodeURIComponent(q)}&page=${page}&size=${size}`;
    return LIVE ? http<Page<Employee>>(`/people/employees/page${qs}`) : mockBackend.directoryPage(accessToken, q, page, size);
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
  createTask(projectId: string, input: { title: string; description?: string; priority?: string; assigneeId?: string; dueDate?: string; storyPoints?: number }) : Promise<Task> {
    return LIVE
      ? http<Task>(`/work/projects/${projectId}/tasks`, { method: "POST", body: JSON.stringify(input) })
      : mockBackend.createTask(accessToken, projectId, input);
  },
  updateTask(id: string, patch: { title?: string; description?: string; status?: string; priority?: string; assigneeId?: string; sprintId?: string; dueDate?: string; storyPoints?: number }): Promise<Task> {
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

  // --- Work OS (board & backlog) ---
  board(projectId: string): Promise<Board> {
    return LIVE ? http<Board>(`/work/projects/${projectId}/board`) : mockBackend.board(accessToken, projectId);
  },
  backlog(projectId: string): Promise<Task[]> {
    return LIVE ? http<Task[]>(`/work/projects/${projectId}/backlog`) : mockBackend.backlog(accessToken, projectId);
  },

  // --- Work OS (sprints) ---
  listSprints(projectId: string): Promise<Sprint[]> {
    return LIVE ? http<Sprint[]>(`/work/projects/${projectId}/sprints`) : mockBackend.listSprints(accessToken, projectId);
  },
  createSprint(projectId: string, input: { name: string; goal?: string; startDate?: string; endDate?: string }): Promise<Sprint> {
    return LIVE
      ? http<Sprint>(`/work/projects/${projectId}/sprints`, { method: "POST", body: JSON.stringify(input) })
      : mockBackend.createSprint(accessToken, projectId, input);
  },
  updateSprint(id: string, patch: { name?: string; goal?: string; startDate?: string; endDate?: string }): Promise<Sprint> {
    return LIVE
      ? http<Sprint>(`/work/sprints/${id}`, { method: "PATCH", body: JSON.stringify(patch) })
      : mockBackend.updateSprint(accessToken, id, patch);
  },
  startSprint(id: string): Promise<Sprint> {
    return LIVE ? http<Sprint>(`/work/sprints/${id}/start`, { method: "POST" }) : mockBackend.startSprint(accessToken, id);
  },
  completeSprint(id: string): Promise<Sprint> {
    return LIVE ? http<Sprint>(`/work/sprints/${id}/complete`, { method: "POST" }) : mockBackend.completeSprint(accessToken, id);
  },
  deleteSprint(id: string): Promise<void> {
    return LIVE ? http<void>(`/work/sprints/${id}`, { method: "DELETE" }) : mockBackend.deleteSprint(accessToken, id);
  },

  // --- Work OS (support tickets) ---
  listTickets(projectId: string): Promise<Ticket[]> {
    return LIVE ? http<Ticket[]>(`/work/projects/${projectId}/tickets`) : mockBackend.listTickets(accessToken, projectId);
  },
  createTicket(projectId: string, input: { subject: string; description?: string; requesterName?: string; requesterEmail?: string; priority?: string; assigneeId?: string }): Promise<Ticket> {
    return LIVE
      ? http<Ticket>(`/work/projects/${projectId}/tickets`, { method: "POST", body: JSON.stringify(input) })
      : mockBackend.createTicket(accessToken, projectId, input);
  },
  updateTicket(id: string, patch: { subject?: string; description?: string; requesterName?: string; requesterEmail?: string; status?: string; priority?: string; assigneeId?: string }): Promise<Ticket> {
    return LIVE
      ? http<Ticket>(`/work/tickets/${id}`, { method: "PATCH", body: JSON.stringify(patch) })
      : mockBackend.updateTicket(accessToken, id, patch);
  },
  deleteTicket(id: string): Promise<void> {
    return LIVE ? http<void>(`/work/tickets/${id}`, { method: "DELETE" }) : mockBackend.deleteTicket(accessToken, id);
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

  // --- compensation & payslips (People OS; admin only) ---
  compensation(employeeId: string): Promise<Compensation> {
    return LIVE ? http<Compensation>(`/people/employees/${employeeId}/compensation`)
      : mockBackend.compensation(accessToken, employeeId);
  },
  addCompensation(employeeId: string, input: { annualAmount: number; effectiveDate?: string; currency?: string; reason?: string }): Promise<Compensation> {
    return LIVE
      ? http<Compensation>(`/people/employees/${employeeId}/compensation`, { method: "POST", body: JSON.stringify(input) })
      : mockBackend.addCompensation(accessToken, employeeId, input);
  },
  payslip(employeeId: string, month?: string): Promise<Payslip> {
    const qs = month ? `?month=${encodeURIComponent(month)}` : "";
    return LIVE ? http<Payslip>(`/people/employees/${employeeId}/payslip${qs}`)
      : mockBackend.payslip(accessToken, employeeId, month);
  },
  // Self-service pay: an employee's own salary + payslip.
  myCompensation(): Promise<Compensation> {
    return LIVE ? http<Compensation>("/people/me/compensation") : mockBackend.myCompensation(accessToken);
  },
  myPayslip(month?: string): Promise<Payslip> {
    const qs = month ? `?month=${encodeURIComponent(month)}` : "";
    return LIVE ? http<Payslip>(`/people/me/payslip${qs}`) : mockBackend.myPayslip(accessToken, month);
  },
  searchPages(q: string): Promise<PageSummary[]> {
    return LIVE
      ? http<PageSummary[]>(`/knowledge/search?q=${encodeURIComponent(q)}`)
      : mockBackend.searchPages(accessToken, q);
  },

  // --- global search (across all three apps) ---
  search(q: string): Promise<SearchResponse> {
    return LIVE
      ? http<SearchResponse>(`/search?q=${encodeURIComponent(q)}`)
      : mockBackend.globalSearch(accessToken, q);
  },

  // --- clients (CRM module) ---
  clients(): Promise<Client[]> {
    return LIVE ? http<Client[]>("/clients") : mockBackend.clients(accessToken);
  },
  createClient(input: Partial<Client> & { name: string }): Promise<Client> {
    return LIVE ? http<Client>("/clients", { method: "POST", body: JSON.stringify(input) }) : mockBackend.createClient(accessToken, input);
  },
  client(id: string): Promise<ClientDetail> {
    return LIVE ? http<ClientDetail>(`/clients/${id}`) : mockBackend.client(accessToken, id);
  },
  updateClient(id: string, patch: Partial<Client>): Promise<Client> {
    return LIVE ? http<Client>(`/clients/${id}`, { method: "PATCH", body: JSON.stringify(patch) }) : mockBackend.updateClient(accessToken, id, patch);
  },
  deleteClient(id: string): Promise<void> {
    return LIVE ? http<void>(`/clients/${id}`, { method: "DELETE" }) : mockBackend.deleteClient(accessToken, id);
  },
  addClientRequest(clientId: string, input: { title: string; description?: string }): Promise<ClientRequestItem> {
    return LIVE ? http<ClientRequestItem>(`/clients/${clientId}/requests`, { method: "POST", body: JSON.stringify(input) }) : mockBackend.addClientRequest(accessToken, clientId, input);
  },
  updateClientRequest(clientId: string, requestId: string, patch: Partial<ClientRequestItem>): Promise<ClientRequestItem> {
    return LIVE ? http<ClientRequestItem>(`/clients/${clientId}/requests/${requestId}`, { method: "PATCH", body: JSON.stringify(patch) }) : mockBackend.updateClientRequest(accessToken, clientId, requestId, patch);
  },
  deleteClientRequest(clientId: string, requestId: string): Promise<void> {
    return LIVE ? http<void>(`/clients/${clientId}/requests/${requestId}`, { method: "DELETE" }) : mockBackend.deleteClientRequest(accessToken, clientId, requestId);
  },

  /** My own employee profile (auto-provisioned if missing) — the Me hub's anchor. */
  myEmployee(): Promise<Employee> {
    return LIVE ? http<Employee>("/people/me") : mockBackend.myEmployee(accessToken);
  },
  /** My goals, resolved through my own employee id. */
  async myGoals(): Promise<Goal[]> {
    const me = await this.myEmployee();
    return this.employeeGoals(me.id);
  },

  // --- sprint reporting ---
  sprintReport(sprintId: string): Promise<SprintReport> {
    return LIVE ? http<SprintReport>(`/work/sprints/${sprintId}/report`) : mockBackend.sprintReport(accessToken, sprintId);
  },
  velocity(projectId: string): Promise<Velocity> {
    return LIVE ? http<Velocity>(`/work/projects/${projectId}/velocity`) : mockBackend.velocity(accessToken, projectId);
  },

  // --- company feed ---
  feed(): Promise<Post[]> {
    return LIVE ? http<Post[]>("/feed") : mockBackend.feed(accessToken);
  },
  createPost(input: PostInput): Promise<Post> {
    return LIVE ? http<Post>("/feed", { method: "POST", body: JSON.stringify(input) }) : mockBackend.createPost(accessToken, input);
  },
  deletePost(id: string): Promise<void> {
    return LIVE ? http<void>(`/feed/${id}`, { method: "DELETE" }) : mockBackend.deletePost(accessToken, id);
  },
  pinPost(id: string, pinned: boolean): Promise<Post> {
    return LIVE ? http<Post>(`/feed/${id}/pin`, { method: "POST", body: JSON.stringify({ pinned }) }) : mockBackend.pinPost(accessToken, id, pinned);
  },
  reactToPost(id: string, emoji: string): Promise<Post> {
    return LIVE ? http<Post>(`/feed/${id}/react`, { method: "POST", body: JSON.stringify({ emoji }) }) : mockBackend.reactToPost(accessToken, id, emoji);
  },
  commentOnPost(id: string, body: string): Promise<Post> {
    return LIVE ? http<Post>(`/feed/${id}/comments`, { method: "POST", body: JSON.stringify({ body }) }) : mockBackend.commentOnPost(accessToken, id, body);
  },
  deletePostComment(commentId: string): Promise<void> {
    return LIVE ? http<void>(`/feed/comments/${commentId}`, { method: "DELETE" }) : mockBackend.deletePostComment(accessToken, commentId);
  },

  // --- expense claims ---
  myExpenses(): Promise<ExpenseSummary> {
    return LIVE ? http<ExpenseSummary>("/expenses/me") : mockBackend.myExpenses(accessToken);
  },
  allExpenses(): Promise<ExpenseSummary> {
    return LIVE ? http<ExpenseSummary>("/expenses") : mockBackend.allExpenses(accessToken);
  },
  submitExpense(input: ExpenseInput): Promise<ExpenseClaim> {
    return LIVE ? http<ExpenseClaim>("/expenses", { method: "POST", body: JSON.stringify(input) }) : mockBackend.submitExpense(accessToken, input);
  },
  withdrawExpense(id: string): Promise<void> {
    return LIVE ? http<void>(`/expenses/${id}`, { method: "DELETE" }) : mockBackend.withdrawExpense(accessToken, id);
  },
  decideExpense(id: string, action: "approve" | "reject" | "reimburse", note?: string): Promise<ExpenseClaim> {
    return LIVE
      ? http<ExpenseClaim>(`/expenses/${id}/${action}`, { method: "POST", body: JSON.stringify({ note: note ?? "" }) })
      : mockBackend.decideExpense(accessToken, id, action, note);
  },

  // --- inbox / notifications ---
  notifications(unreadOnly = false): Promise<AppNotification[]> {
    const qs = unreadOnly ? "?unreadOnly=true" : "";
    return LIVE ? http<AppNotification[]>(`/notifications${qs}`) : mockBackend.notifications(accessToken, unreadOnly);
  },
  unreadCount(): Promise<{ count: number }> {
    return LIVE ? http<{ count: number }>("/notifications/unread-count") : mockBackend.unreadCount(accessToken);
  },
  markNotificationRead(id: string): Promise<AppNotification> {
    return LIVE ? http<AppNotification>(`/notifications/${id}/read`, { method: "POST" }) : mockBackend.markNotificationRead(accessToken, id);
  },
  markAllNotificationsRead(): Promise<{ marked: number }> {
    return LIVE ? http<{ marked: number }>("/notifications/read-all", { method: "POST" }) : mockBackend.markAllNotificationsRead(accessToken);
  },

  // --- holiday calendar ---
  holidays(year?: number): Promise<Holiday[]> {
    const qs = year ? `?year=${year}` : "";
    return LIVE ? http<Holiday[]>(`/people/holidays${qs}`) : mockBackend.holidays(accessToken, year);
  },
  upcomingHolidays(limit = 5): Promise<Holiday[]> {
    return LIVE ? http<Holiday[]>(`/people/holidays/upcoming?limit=${limit}`) : mockBackend.upcomingHolidays(accessToken, limit);
  },
  createHoliday(input: { name: string; date: string; optional?: boolean; note?: string }): Promise<Holiday> {
    return LIVE ? http<Holiday>("/people/holidays", { method: "POST", body: JSON.stringify(input) }) : mockBackend.createHoliday(accessToken, input);
  },
  deleteHoliday(id: string): Promise<void> {
    return LIVE ? http<void>(`/people/holidays/${id}`, { method: "DELETE" }) : mockBackend.deleteHoliday(accessToken, id);
  },
  seedDefaultHolidays(): Promise<Holiday[]> {
    return LIVE ? http<Holiday[]>("/people/holidays/defaults", { method: "POST" }) : mockBackend.seedDefaultHolidays(accessToken);
  },

  // --- attendance (daily record) ---
  attendanceToday(): Promise<AttendanceEntry> {
    return LIVE ? http<AttendanceEntry>("/people/attendance/me/today") : mockBackend.attendanceToday(accessToken);
  },
  checkIn(): Promise<AttendanceEntry> {
    return LIVE ? http<AttendanceEntry>("/people/attendance/me/check-in", { method: "POST" }) : mockBackend.checkIn(accessToken);
  },
  checkOut(): Promise<AttendanceEntry> {
    return LIVE ? http<AttendanceEntry>("/people/attendance/me/check-out", { method: "POST" }) : mockBackend.checkOut(accessToken);
  },
  myAttendance(month?: string): Promise<AttendanceMonth> {
    const qs = month ? `?month=${month}` : "";
    return LIVE ? http<AttendanceMonth>(`/people/attendance/me${qs}`) : mockBackend.myAttendance(accessToken, month);
  },
  attendanceDay(date?: string): Promise<AttendanceDay> {
    const qs = date ? `?date=${date}` : "";
    return LIVE ? http<AttendanceDay>(`/people/attendance/day${qs}`) : mockBackend.attendanceDay(accessToken, date);
  },
  markAttendance(input: MarkAttendanceInput): Promise<AttendanceEntry> {
    return LIVE ? http<AttendanceEntry>("/people/attendance/mark", { method: "POST", body: JSON.stringify(input) }) : mockBackend.markAttendance(accessToken, input);
  },
  employeeAttendance(employeeId: string, month?: string): Promise<AttendanceMonth> {
    const qs = month ? `?month=${month}` : "";
    return LIVE ? http<AttendanceMonth>(`/people/attendance/employees/${employeeId}${qs}`) : mockBackend.employeeAttendance(accessToken, employeeId, month);
  },

  // --- documents (templates + generated letters) ---
  docTemplates(): Promise<DocumentTemplate[]> {
    return LIVE ? http<DocumentTemplate[]>("/documents/templates") : mockBackend.docTemplates(accessToken);
  },
  createDocTemplate(input: { name: string; kind: string; description?: string; body: string }): Promise<DocumentTemplate> {
    return LIVE ? http<DocumentTemplate>("/documents/templates", { method: "POST", body: JSON.stringify(input) }) : mockBackend.createDocTemplate(accessToken, input);
  },
  updateDocTemplate(id: string, patch: Partial<DocumentTemplate>): Promise<DocumentTemplate> {
    return LIVE ? http<DocumentTemplate>(`/documents/templates/${id}`, { method: "PATCH", body: JSON.stringify(patch) }) : mockBackend.updateDocTemplate(accessToken, id, patch);
  },
  deleteDocTemplate(id: string): Promise<void> {
    return LIVE ? http<void>(`/documents/templates/${id}`, { method: "DELETE" }) : mockBackend.deleteDocTemplate(accessToken, id);
  },
  mergeFields(): Promise<MergeField[]> {
    return LIVE ? http<MergeField[]>("/documents/fields") : mockBackend.mergeFields(accessToken);
  },
  previewDoc(input: GenerateDocInput): Promise<DocumentPreview> {
    return LIVE ? http<DocumentPreview>("/documents/preview", { method: "POST", body: JSON.stringify(input) }) : mockBackend.previewDoc(accessToken, input);
  },
  generateDoc(input: GenerateDocInput): Promise<GeneratedDoc> {
    return LIVE ? http<GeneratedDoc>("/documents", { method: "POST", body: JSON.stringify(input) }) : mockBackend.generateDoc(accessToken, input);
  },
  documents(employeeId?: string): Promise<GeneratedDoc[]> {
    const qs = employeeId ? `?employeeId=${employeeId}` : "";
    return LIVE ? http<GeneratedDoc[]>(`/documents${qs}`) : mockBackend.documents(accessToken, employeeId);
  },
  document(id: string): Promise<GeneratedDoc> {
    return LIVE ? http<GeneratedDoc>(`/documents/${id}`) : mockBackend.document(accessToken, id);
  },
  deleteDocument(id: string): Promise<void> {
    return LIVE ? http<void>(`/documents/${id}`, { method: "DELETE" }) : mockBackend.deleteDocument(accessToken, id);
  },

  // --- cross-app AI assistant ---
  askAssistant(question: string): Promise<AssistantResponse> {
    return LIVE
      ? http<AssistantResponse>("/assistant/ask", { method: "POST", body: JSON.stringify({ question }) })
      : mockBackend.askAssistant(accessToken, question);
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
