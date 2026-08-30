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
  type AgencySummary,
  type CreateAgencyInput,
  type AgencyOverview,
  type SeatRequest,
  type TrialRequest,
  type TrialRequestInput,
  type SubscriptionView,
  type HelpdeskTicket,
  type HelpdeskComment,
  type RaiseTicketInput,
  type UpdateTicketInput,
  type Compensation,
  type Payslip,
  type PriceListVersion,
  type EmployeeFinance,
  type PayrollRun,
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
  type Regularization,
  type RegularizationInput,
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
  type Letterhead,
  type LetterheadInput,
  type ExitView,
  type StartExitInput,
  type MakeOfferInput,
  type OfferResult,
  type HireInput,
  type HireResult,
} from "@/lib/types";
import { mockBackend, type MailMessage } from "@/lib/mock/backend";

// Frontend-first: mock is the default. Set NEXT_PUBLIC_API_MODE=live to hit the real backend.
const LIVE = process.env.NEXT_PUBLIC_API_MODE === "live";

/**
 * For features with no mock: say so, rather than resolving with something invented. Used where the
 * call has real-world weight — company stationery, an employment record, an invitation.
 */
function liveOnly<T>(what: string): Promise<T> {
  return Promise.reject(new ApiError({
    timestamp: "", status: 400, code: "VALIDATION_ERROR",
    message: `${what} needs the live backend. Set NEXT_PUBLIC_API_MODE=live and start the backend.`,
  }));
}
/** True when the app is wired to the real backend (enables demo seeding, dev mailbox, etc.). */
export const isLive = LIVE;
const BASE = "/api/v1";

/** Outcome of a signup: the account always exists, but the email may not have gone out. */
export type RegisterResult = { emailSent: boolean };

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

/**
 * How long to keep trying while the backend is waking up. The hosted backend sleeps when idle and
 * takes the better part of a minute to come back, during which the proxy cannot reach it at all.
 */
const WAKE_BACKOFF_MS = [1_000, 3_000, 6_000, 12_000, 20_000, 25_000];

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/**
 * Can this failure be retried safely, and is it worth retrying?
 *
 * <p>The signal is `unreachable` — a non-JSON error body. Every error the backend itself raises is
 * JSON, so a body the browser cannot parse did not come from the application: it is the Next proxy
 * reporting that it could not reach the backend at all. That distinction is what makes a retry safe
 * even for a POST, because a request that never arrived cannot have been half-applied.
 *
 * <p>A JSON 500 is a genuine application error and is never retried — repeating it just fails again.
 * Neither is 429: retrying a rate limit is what extends it.
 */
function shouldRetry(status: number, unreachable: boolean, attempt: number): boolean {
  if (attempt >= WAKE_BACKOFF_MS.length) return false;
  return unreachable && (status === 500 || status === 502 || status === 503 || status === 504);
}

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  for (let attempt = 0; ; attempt++) {
    let res: Response;
    try {
      res = await fetch(`${BASE}${path}`, {
        ...init,
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
          ...(init?.headers ?? {}),
        },
      });
    } catch {
      // The request never left, so nothing can have been applied — same reasoning as `unreachable`.
      if (!shouldRetry(503, true, attempt)) throw new ApiError(infrastructureError(0));
      await sleep(WAKE_BACKOFF_MS[attempt]);
      continue;
    }

    if (res.status === 204) return undefined as T;
    const body = await res.json().catch(() => null);
    if (res.ok) return body as T;

    if (shouldRetry(res.status, body === null, attempt)) {
      await sleep(WAKE_BACKOFF_MS[attempt]);
      continue;
    }
    throw new ApiError((body as ApiErrorBody) ?? infrastructureError(res.status));
  }
}

/**
 * What to say when the failure did not come from the app at all.
 *
 * <p>Every error the backend raises is JSON carrying its own message. A response the browser cannot
 * parse as JSON therefore came from something in front of it — the platform's rate limiter, a
 * gateway, or an instance that is still waking up — and those answer in plain text or HTML.
 *
 * <p>This used to collapse all of them into "Request failed", which is the least useful thing the
 * screen could say: the three causes need three different responses from the person reading it, and
 * two of them just mean "wait". Naming the cause is the difference between waiting a minute and
 * concluding the account is broken.
 */
function infrastructureError(status: number): ApiErrorBody {
  const message =
    status === 429
      ? "Too many attempts from this network. Wait a minute, then try again."
      : "The server could not be reached — it may still be starting up. Try again in a moment.";
  return { timestamp: "", status, code: "INFRASTRUCTURE", message };
}

export const api = {
  // --- auth / registration ---
  /**
   * The workspace is created even if its verification email couldn't be sent, so the result carries
   * `emailSent` — the signup screen must not promise a message that never left the server.
   */
  async register(input: { companyName: string; firstName: string; lastName: string; email: string; password: string }): Promise<RegisterResult> {
    if (!LIVE) {
      await mockBackend.register(input);
      return { emailSent: true };
    }
    const result = await http<RegisterResult | null>("/auth/register", { method: "POST", body: JSON.stringify(input) });
    // Older backends answered 201 with an empty body; treat that as "sent" rather than alarming.
    return { emailSent: result?.emailSent ?? true };
  },
  /**
   * Ask for a free trial (PD-21). Public — no session, and nothing here creates an account.
   *
   * <p>In mock mode there is no vendor to email, so it resolves as if the request went through: the
   * page under test is the form, and failing it locally would only train people to ignore the error.
   */
  async requestTrial(input: TrialRequestInput): Promise<{ received: boolean; emailSent: boolean }> {
    if (!LIVE) return { received: true, emailSent: false };
    const result = await http<{ received: boolean; emailSent: boolean } | null>(
      "/trial-requests", { method: "POST", body: JSON.stringify(input) });
    return { received: true, emailSent: result?.emailSent ?? false };
  },
  /** Can this deployment actually deliver mail? Cheap, read-only, and safe to call from a public
   *  page: it describes the deployment, never an account. Null when the endpoint is absent (prod). */
  async mailStatus(): Promise<{ provider: string; delivers: boolean } | null> {
    if (!LIVE) return null;
    try {
      return await http<{ provider: string; delivers: boolean }>("/dev/mail-status");
    } catch {
      return null;
    }
  },
  /**
   * Ask for a one-time reset code (PD-23). Always resolves, whether or not the address has an
   * account — the backend answers 202 either way so this endpoint cannot be used to discover who
   * banks here, and the UI must not undo that by behaving differently.
   */
  forgotPassword(email: string): Promise<void> {
    return LIVE
      ? http<void>("/auth/forgot-password", { method: "POST", body: JSON.stringify({ email }) })
      : Promise.resolve();
  },
  /** Spend the code and set the new password. Signs every existing session out. */
  resetPassword(input: { email: string; code: string; newPassword: string }): Promise<void> {
    return LIVE
      ? http<void>("/auth/reset-password", { method: "POST", body: JSON.stringify(input) })
      : liveOnly("Resetting a password");
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
  /**
   * Build the whole demo: the populated company and the sample companies that fill the owner
   * console. Used by /demo/seed.
   *
   * <p>It deliberately does NOT sign anyone in. Its predecessor did — the login screen's
   * "Explore the live demo" button seeded and then dropped you into the dashboard as the demo
   * owner — which made preparing data and taking on an identity the same action. Whoever is running
   * the demo picks the login they want to show afterwards.
   */
  async seedAll(): Promise<{ seeded: boolean; companies: string[] }> {
    if (!LIVE) {
      throw new ApiError({
        timestamp: "", status: 400, code: "VALIDATION_ERROR",
        message: "Demo data needs the live backend. Set NEXT_PUBLIC_API_MODE=live and start the backend.",
      });
    }
    const res = await http<{ seeded: boolean; companies: string[] } | null>("/dev/seed-all");
    return { seeded: true, companies: res?.companies ?? [] };
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
  /** Every version of the price list, newest first. Owner only. */
  platformPricing(currency = "INR"): Promise<PriceListVersion[]> {
    return LIVE ? http<PriceListVersion[]>(`/platform/pricing?currency=${encodeURIComponent(currency)}`) : Promise.reject(new Error("live only"));
  },
  /** Publish a new price list — live immediately, no deploy. Owner only. */
  publishPricing(input: { effectiveFrom: string; note?: string; tiers: { toEmployee: number | null; rate: number }[]; monthlyMinimum: number; annualMonthsCharged: number; currency: string }): Promise<PriceListVersion> {
    return LIVE
      ? http<PriceListVersion>("/platform/pricing", { method: "POST", body: JSON.stringify(input) })
      : Promise.reject(new Error("live only"));
  },
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
  setCompanyEndDate(companyId: string, endsAt: string): Promise<CompanySummary> {
    return LIVE ? http<CompanySummary>(`/platform/companies/${companyId}/end-date`, { method: "POST", body: JSON.stringify({ endsAt }) }) : Promise.reject(new Error("live only"));
  },
  /** A number agrees a rate with this customer; `null` puts them back on the published price list. */
  setCompanyPrice(companyId: string, price: number | null): Promise<CompanySummary> {
    return LIVE ? http<CompanySummary>(`/platform/companies/${companyId}/price`, { method: "POST", body: JSON.stringify({ price }) }) : Promise.reject(new Error("live only"));
  },
  platformAgencies(): Promise<AgencySummary[]> {
    return LIVE ? http<AgencySummary[]>("/platform/agencies") : Promise.reject(new Error("live only"));
  },
  createAgency(input: CreateAgencyInput): Promise<AgencySummary> {
    return LIVE ? http<AgencySummary>("/platform/agencies", { method: "POST", body: JSON.stringify(input) }) : Promise.reject(new Error("live only"));
  },

  // --- agency console (a customer running several companies — PD-18) ---
  agencyOverview(): Promise<AgencyOverview> {
    return LIVE ? http<AgencyOverview>("/agency/overview") : Promise.reject(new Error("The agency console requires the live backend."));
  },
  agencyCompanies(): Promise<CompanySummary[]> {
    return LIVE ? http<CompanySummary[]>("/agency/companies") : Promise.reject(new Error("The agency console requires the live backend."));
  },
  agencyCreateCompany(input: CreateCompanyInput): Promise<CompanySummary> {
    return LIVE ? http<CompanySummary>("/agency/companies", { method: "POST", body: JSON.stringify(input) }) : Promise.reject(new Error("live only"));
  },
  agencyRequestSeats(companyId: string, seats: number, note?: string): Promise<CompanySummary> {
    return LIVE
      ? http<CompanySummary>(`/agency/companies/${companyId}/request-seats`, { method: "POST", body: JSON.stringify({ seats, note }) })
      : Promise.reject(new Error("live only"));
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

  // --- trial requests: the queue behind the website's "free trial" button (PD-21) ---
  platformTrialRequests(): Promise<TrialRequest[]> {
    return LIVE ? http<TrialRequest[]>("/platform/trial-requests") : liveOnly("The trial queue");
  },
  /** Approving provisions the company on these terms — this is the moment a login starts existing. */
  approveTrialRequest(id: string, terms: { password: string; seats: number; months: number; currency?: string }): Promise<CompanySummary> {
    return LIVE
      ? http<CompanySummary>(`/platform/trial-requests/${id}/approve`, { method: "POST", body: JSON.stringify(terms) })
      : liveOnly("Approving a trial");
  },
  declineTrialRequest(id: string): Promise<TrialRequest> {
    return LIVE
      ? http<TrialRequest>(`/platform/trial-requests/${id}/decline`, { method: "POST" })
      : liveOnly("Declining a trial");
  },

  // --- a company's own subscription (admin read-only + app-lock check) ---
  mySubscription(): Promise<SubscriptionView> {
    return LIVE
      ? http<SubscriptionView>("/subscription/me")
      : Promise.resolve({ status: "NONE", seats: 0, seatsUsed: 0, endsAt: null, daysLeft: null, locked: false, pendingRequestSeats: null, pricePerEmployee: null, monthlyCharge: null, currency: "INR" });
  },
  requestSeats(seats: number, note?: string): Promise<SubscriptionView> {
    return LIVE ? http<SubscriptionView>("/subscription/request-seats", { method: "POST", body: JSON.stringify({ seats, note }) }) : Promise.reject(new Error("live only"));
  },

  // --- HR helpdesk ---
  raiseTicket(input: RaiseTicketInput): Promise<HelpdeskTicket> {
    return LIVE ? http<HelpdeskTicket>("/helpdesk/tickets", { method: "POST", body: JSON.stringify(input) }) : mockBackend.raiseTicket(accessToken, input);
  },
  myTickets(): Promise<HelpdeskTicket[]> {
    return LIVE ? http<HelpdeskTicket[]>("/helpdesk/tickets/mine") : mockBackend.myTickets(accessToken);
  },
  helpdeskQueue(status?: string): Promise<HelpdeskTicket[]> {
    const qs = status ? `?status=${status}` : "";
    return LIVE ? http<HelpdeskTicket[]>(`/helpdesk/tickets${qs}`) : mockBackend.helpdeskQueue(accessToken, status);
  },
  helpdeskTicket(id: string): Promise<HelpdeskTicket> {
    return LIVE ? http<HelpdeskTicket>(`/helpdesk/tickets/${id}`) : mockBackend.helpdeskTicket(accessToken, id);
  },
  helpdeskComments(id: string): Promise<HelpdeskComment[]> {
    return LIVE ? http<HelpdeskComment[]>(`/helpdesk/tickets/${id}/comments`) : mockBackend.helpdeskComments(accessToken, id);
  },
  commentOnTicket(id: string, body: string): Promise<HelpdeskComment> {
    return LIVE ? http<HelpdeskComment>(`/helpdesk/tickets/${id}/comments`, { method: "POST", body: JSON.stringify({ body }) }) : mockBackend.commentOnTicket(accessToken, id, body);
  },
  updateHelpdeskTicket(id: string, input: UpdateTicketInput): Promise<HelpdeskTicket> {
    return LIVE ? http<HelpdeskTicket>(`/helpdesk/tickets/${id}`, { method: "PATCH", body: JSON.stringify(input) }) : mockBackend.updateHelpdeskTicket(accessToken, id, input);
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
  updateSettings(patch: { timezone: string; locale: string; currency: string; legalName?: string; address?: string; logoUrl?: string }): Promise<CompanySettings> {
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
  /**
   * A fresh joining link for a pending invitation. The token is stored hashed, so the original link
   * can't be read back — only replaced. Regenerating invalidates the previous one.
   */
  invitationLink(id: string): Promise<Invitation> {
    return LIVE
      ? http<Invitation>(`/invitations/${id}/link`, { method: "POST" })
      : mockBackend.invitationLink(accessToken, id);
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
  /** My bank / statutory / identity record. Account number and PAN arrive masked. */
  myFinance(): Promise<EmployeeFinance> {
    return LIVE ? http<EmployeeFinance>("/people/me/finance") : mockBackend.myFinance(accessToken);
  },
  /** Employees maintain their own bank details and identity; PF/ESI/PT are rejected here (HR owns them). */
  updateMyFinance(patch: Partial<EmployeeFinance> & { bankAccountNo?: string; panNumber?: string }): Promise<EmployeeFinance> {
    return LIVE
      ? http<EmployeeFinance>("/people/me/finance", { method: "PATCH", body: JSON.stringify(patch) })
      : mockBackend.updateMyFinance(accessToken, patch);
  },
  /** Anyone's finance record — HR/admin only. */
  employeeFinance(employeeId: string): Promise<EmployeeFinance> {
    return LIVE
      ? http<EmployeeFinance>(`/people/employees/${employeeId}/finance`)
      : mockBackend.employeeFinance(accessToken, employeeId);
  },
  updateEmployeeFinance(employeeId: string, patch: Record<string, unknown>): Promise<EmployeeFinance> {
    return LIVE
      ? http<EmployeeFinance>(`/people/employees/${employeeId}/finance`, { method: "PATCH", body: JSON.stringify(patch) })
      : mockBackend.updateEmployeeFinance(accessToken, employeeId, patch);
  },
  payrollRun(month?: string): Promise<PayrollRun> {
    const qs = month ? `?month=${encodeURIComponent(month)}` : "";
    return LIVE ? http<PayrollRun>(`/payroll/run${qs}`) : mockBackend.payrollRun(accessToken, month);
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
  resetToday(): Promise<AttendanceEntry> {
    return LIVE ? http<AttendanceEntry>("/people/attendance/me/today", { method: "DELETE" }) : mockBackend.resetToday(accessToken);
  },
  // Regularization: fix a missed/incorrect day → manager/HR approves.
  raiseRegularization(input: RegularizationInput): Promise<Regularization> {
    return LIVE ? http<Regularization>("/attendance/regularizations", { method: "POST", body: JSON.stringify(input) }) : mockBackend.raiseRegularization(accessToken, input);
  },
  myRegularizations(): Promise<Regularization[]> {
    return LIVE ? http<Regularization[]>("/attendance/regularizations/mine") : mockBackend.myRegularizations(accessToken);
  },
  pendingRegularizations(): Promise<Regularization[]> {
    return LIVE ? http<Regularization[]>("/attendance/regularizations/pending") : mockBackend.pendingRegularizations(accessToken);
  },
  approveRegularization(id: string, note?: string): Promise<Regularization> {
    return LIVE ? http<Regularization>(`/attendance/regularizations/${id}/approve`, { method: "POST", body: JSON.stringify({ note }) }) : mockBackend.decideRegularization(accessToken, id, true, note);
  },
  rejectRegularization(id: string, note?: string): Promise<Regularization> {
    return LIVE ? http<Regularization>(`/attendance/regularizations/${id}/reject`, { method: "POST", body: JSON.stringify({ note }) }) : mockBackend.decideRegularization(accessToken, id, false, note);
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

  // --- letterhead, exits and hiring (PD-20) ---
  // Live-only, like demo seeding above: these read and write real company stationery and real
  // employment records, and a mock that pretended to would be worse than one that says so.
  letterhead(): Promise<Letterhead> {
    return LIVE ? http<Letterhead>("/documents/letterhead") : liveOnly("The letterpad");
  },
  saveLetterhead(input: LetterheadInput): Promise<Letterhead> {
    return LIVE
      ? http<Letterhead>("/documents/letterhead", { method: "PATCH", body: JSON.stringify(input) })
      : liveOnly("The letterpad");
  },

  exits(): Promise<ExitView[]> {
    return LIVE ? http<ExitView[]>("/people/exits") : liveOnly("Exits");
  },
  exit(employeeId: string): Promise<ExitView> {
    return LIVE ? http<ExitView>(`/people/employees/${employeeId}/exit`) : liveOnly("Exits");
  },
  startExit(employeeId: string, input: StartExitInput): Promise<ExitView> {
    return LIVE
      ? http<ExitView>(`/people/employees/${employeeId}/exit`, { method: "POST", body: JSON.stringify(input) })
      : liveOnly("Exits");
  },
  cancelExit(employeeId: string): Promise<ExitView> {
    return LIVE
      ? http<ExitView>(`/people/employees/${employeeId}/exit`, { method: "DELETE" })
      : liveOnly("Exits");
  },
  /** `force` skips the "clearance outstanding" guard — a deliberate act, never a default. */
  completeExit(employeeId: string, force = false): Promise<ExitView> {
    return LIVE
      ? http<ExitView>(`/people/employees/${employeeId}/exit/complete?force=${force}`, { method: "POST" })
      : liveOnly("Exits");
  },
  toggleExitTask(taskId: string, completed: boolean): Promise<OnboardingTask> {
    // Same route as onboarding on purpose: the task knows which checklist it is on.
    return LIVE
      ? http<OnboardingTask>(`/people/onboarding/${taskId}`, { method: "PATCH", body: JSON.stringify({ completed }) })
      : liveOnly("Exits");
  },
  addExitTask(employeeId: string, title: string): Promise<OnboardingTask> {
    return LIVE
      ? http<OnboardingTask>(`/people/employees/${employeeId}/exit-checklist`, { method: "POST", body: JSON.stringify({ title }) })
      : liveOnly("Exits");
  },

  makeOffer(candidateId: string, input: MakeOfferInput): Promise<OfferResult> {
    return LIVE
      ? http<OfferResult>(`/recruit/candidates/${candidateId}/offer`, { method: "POST", body: JSON.stringify(input) })
      : liveOnly("Offers");
  },
  hireCandidate(candidateId: string, input: HireInput): Promise<HireResult> {
    return LIVE
      ? http<HireResult>(`/recruit/candidates/${candidateId}/hire`, { method: "POST", body: JSON.stringify(input) })
      : liveOnly("Hiring");
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
  /**
   * Whether this backend captures mail links (every profile but prod). Asked at runtime rather than
   * guessed from NODE_ENV: a staging deployment is a production build of the frontend talking to a
   * non-prod backend, so the build flag pointed people away from the mailbox exactly where they
   * needed it — and would have offered it against a prod backend that has no such page.
   */
  async devMailboxAvailable(): Promise<boolean> {
    if (!LIVE) return true;
    try {
      await http<MailMessage[]>("/dev/mailbox");
      return true;
    } catch {
      return false;
    }
  },
};

export { ApiError };
