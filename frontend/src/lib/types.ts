// API contract types (Sprint1 §7). Kept in sync with backend DTOs.

// OWNER is the platform vendor; AGENCY_OWNER is a customer running several companies (PD-18). Both
// sit above a single company — the rest are roles within one.
export type Role = "OWNER" | "AGENCY_OWNER" | "ADMIN" | "HR" | "MANAGER" | "MEMBER";
export type UserStatus = "PENDING_VERIFICATION" | "INVITED" | "ACTIVE" | "DISABLED";
export type CompanyStatus = "PENDING" | "ACTIVE" | "SUSPENDED";
export type InvitationStatus = "PENDING" | "ACCEPTED" | "REVOKED" | "EXPIRED";

export interface Me {
  user: {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    role: Role;
    status: UserStatus;
  };
  company: {
    id: string;
    name: string;
    slug: string;
    status: CompanyStatus;
    currency: string;
    timezone: string;
  };
}

export interface CompanySettings {
  companyId: string;
  timezone: string;
  locale: string;
  currency: string;
  legalName: string | null;
  address: string | null;
  logoUrl: string | null;
}

export interface Member {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  status: UserStatus;
}

export interface Invitation {
  id: string;
  email: string;
  role: Role;
  status: InvitationStatus;
  invitedByEmail: string;
  createdAt: string;
  expiresAt: string;
  /**
   * The joining link — present only in the response to creating or regenerating an invitation, since
   * the token is stored hashed and can't be read back. Surfacing it means adding a colleague never
   * depends on email being deliverable.
   */
  acceptUrl?: string | null;
}

export type EmploymentType = "FULL_TIME" | "PART_TIME" | "CONTRACT" | "INTERN";
/** NOTICE = exit started, last working day not yet reached (PD-20). */
export type EmploymentStatus = "ONBOARDING" | "ACTIVE" | "NOTICE" | "TERMINATED";

export interface Employee {
  id: string;
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  role: Role;
  employeeNo: string | null;
  jobTitle: string | null;
  employmentType: EmploymentType | null;
  employmentStatus: EmploymentStatus;
  departmentId: string | null;
  managerId: string | null;
  workLocation: string | null;
  phone: string | null;
  startDate: string | null;
  endDate: string | null;
  skills: string[];
  rating: number | null;
}

export interface Client {
  id: string;
  name: string;
  contactName: string | null;
  contactEmail: string | null;
  phone: string | null;
  website: string | null;
  status: "LEAD" | "ACTIVE" | "CHURNED";
  notes: string | null;
  createdAt: string;
  openRequests: number;
}
export interface ClientRequestItem {
  id: string;
  title: string;
  description: string | null;
  status: "REQUESTED" | "IN_PROGRESS" | "DELIVERED" | "DECLINED";
  createdAt: string;
}
export interface ClientDetail {
  client: Client;
  requests: ClientRequestItem[];
}

/** Sprint reporting — burndown, velocity, capacity and per-person load. */
export interface BurndownPoint {
  date: string;
  /** Recorded remaining points; null for days with no snapshot (future days). */
  remainingPoints: number | null;
  ideal: number;
  projected: boolean;
}
export interface MemberLoad {
  employeeId: string;
  name: string;
  points: number;
  tasks: number;
  donePoints: number;
}
export interface SprintReport {
  sprintId: string;
  name: string;
  goal: string | null;
  status: string;
  startDate: string | null;
  endDate: string | null;
  capacityPoints: number | null;
  committedPoints: number;
  completedPoints: number;
  remainingPoints: number;
  totalTasks: number;
  doneTasks: number;
  unestimatedTasks: number;
  daysTotal: number;
  daysElapsed: number;
  burndown: BurndownPoint[];
  byAssignee: MemberLoad[];
}
export interface SprintVelocity {
  sprintId: string;
  name: string;
  endDate: string | null;
  committedPoints: number;
  completedPoints: number;
}
export interface Velocity {
  sprints: SprintVelocity[];
  averageVelocity: number;
  suggestedCommitment: number;
}

/** Company feed — posts with per-post visibility, reactions and comments. */
export type PostKind = "UPDATE" | "ANNOUNCEMENT" | "CELEBRATION" | "QUESTION";
export type PostVisibility = "COMPANY" | "DEPARTMENT";

export interface PostComment {
  id: string;
  authorId: string;
  authorName: string;
  body: string;
  canDelete: boolean;
  createdAt: string;
}
export interface Post {
  id: string;
  authorId: string;
  authorName: string;
  authorTitle: string | null;
  kind: PostKind;
  body: string;
  visibility: PostVisibility;
  departmentId: string | null;
  departmentName: string | null;
  pinned: boolean;
  /** emoji → how many people used it. */
  reactions: Record<string, number>;
  /** The emoji the viewer has used. */
  myReactions: string[];
  comments: PostComment[];
  canManage: boolean;
  createdAt: string;
}
export interface PostInput {
  body: string;
  kind?: PostKind;
  visibility?: PostVisibility;
  departmentId?: string;
}

/** Expense claims — travel and other out-of-pocket spend. */
export type ExpenseCategory = "TRAVEL" | "ACCOMMODATION" | "MEALS" | "SUPPLIES" | "TRAINING" | "OTHER";
export type ExpenseStatus = "SUBMITTED" | "APPROVED" | "REJECTED" | "REIMBURSED";

export interface ExpenseClaim {
  id: string;
  employeeId: string;
  employeeName: string | null;
  title: string;
  category: ExpenseCategory;
  amount: number;
  currency: string;
  spentOn: string;
  description: string | null;
  receiptUrl: string | null;
  status: ExpenseStatus;
  decisionNote: string | null;
  decidedAt: string | null;
  reimbursedAt: string | null;
  createdAt: string;
}
export interface ExpenseSummary {
  claims: ExpenseClaim[];
  pendingAmount: number;
  awaitingReimbursement: number;
  reimbursedThisYear: number;
  currency: string;
}
export interface ExpenseInput {
  title: string;
  category?: ExpenseCategory;
  amount: number;
  currency?: string;
  spentOn?: string;
  description?: string;
  receiptUrl?: string;
}

/** Inbox notifications (feedback D4/D5). */
export type NotificationType =
  | "LEAVE_REQUESTED"
  | "LEAVE_APPROVED"
  | "LEAVE_REJECTED"
  | "GOAL_ASSIGNED"
  | "DOCUMENT_ISSUED"
  | "REVIEW_STARTED"
  | "REVIEW_SELF_SUBMITTED"
  | "REVIEW_SUBMITTED"
  | "REVIEW_APPROVED"
  | "ANNOUNCEMENT";

export interface AppNotification {
  id: string;
  type: NotificationType;
  title: string;
  body: string | null;
  link: string | null;
  entityType: string | null;
  entityId: string | null;
  read: boolean;
  createdAt: string;
}

/** Company holiday calendar. */
export interface Holiday {
  id: string;
  name: string;
  date: string;
  optional: boolean;
  note: string | null;
  weekday: string;
  daysAway: number;
}

/** Daily attendance (feedback C.4). `status` is null when nobody has marked the day yet. */
export type AttendanceStatus =
  | "PRESENT"
  | "WORK_FROM_HOME"
  | "HALF_DAY"
  | "ABSENT"
  | "ON_LEAVE"
  | "HOLIDAY"
  | "WEEK_OFF";

export interface AttendanceEntry {
  employeeId: string;
  employeeName: string;
  jobTitle: string | null;
  department: string | null;
  date: string;
  status: AttendanceStatus | null;
  checkIn: string | null;
  checkOut: string | null;
  note: string | null;
  /** True when the status was inferred (approved leave or a weekend) rather than marked. */
  derived: boolean;
}
export interface AttendanceDay {
  date: string;
  headcount: number;
  present: number;
  onLeave: number;
  absent: number;
  unmarked: number;
  entries: AttendanceEntry[];
}
export type RegularizationStatus = "PENDING" | "APPROVED" | "REJECTED";
export interface Regularization {
  id: string;
  employeeId: string;
  employeeName: string;
  date: string;
  checkIn: string | null;
  checkOut: string | null;
  status: RegularizationStatus;
  reason: string | null;
  decisionNote: string | null;
  decidedAt: string | null;
  createdAt: string;
}
export interface RegularizationInput {
  date: string;
  checkIn?: string;
  checkOut?: string;
  reason?: string;
}
export interface AttendanceMonth {
  employeeId: string;
  employeeName: string;
  month: string;
  days: AttendanceEntry[];
  counts: Record<string, number>;
  workedDays: number;
  expectedDays: number;
  attendanceRate: number | null;
}
export interface MarkAttendanceInput {
  employeeId: string;
  date?: string;
  status: AttendanceStatus;
  checkIn?: string | null;
  checkOut?: string | null;
  note?: string | null;
}

/** Documents module (feedback D2/D3) — a template library and the letters generated from it. */
export type DocumentKind =
  | "OFFER_LETTER"
  | "JOINING_LETTER"
  | "RELIEVING_LETTER"
  | "EXPERIENCE_LETTER"
  | "PROMOTION_LETTER"
  | "CUSTOM";

export interface DocumentTemplate {
  id: string;
  name: string;
  kind: DocumentKind;
  description: string | null;
  body: string;
  builtIn: boolean;
  /** Print this one on the company letterpad. */
  useLetterhead: boolean;
  /** The merge fields this body actually references. */
  placeholders: string[];
  updatedAt: string;
}

/** Which typeface the letterpad prints in. Mapped to real font stacks in `lib/documents`. */
export type LetterheadFont = "SERIF" | "SANS" | "SLAB";

/**
 * The company letterpad. Always present — a company that has never opened the editor gets defaults
 * with its own name, so there is no "not configured" state to render.
 */
export interface Letterhead {
  logoUrl: string | null;
  /** Already resolved to the company name when it was left blank. */
  heading: string | null;
  addressLines: string | null;
  footerText: string | null;
  brandColor: string;
  fontFamily: LetterheadFont;
  showDivider: boolean;
  signatureName: string | null;
  signatureTitle: string | null;
  updatedAt: string;
}
export type LetterheadInput = Partial<Omit<Letterhead, "updatedAt">>;
export interface MergeField {
  key: string;
  label: string;
}
export interface GeneratedDoc {
  id: string;
  title: string;
  kind: DocumentKind;
  employeeId: string | null;
  employeeName: string | null;
  templateId: string | null;
  body: string;
  useLetterhead: boolean;
  generatedBy: string | null;
  createdAt: string;
}
export interface DocumentPreview {
  title: string;
  body: string;
  useLetterhead: boolean;
  values: Record<string, string>;
  /** Fields the profile couldn't fill — fix or override before issuing. */
  missing: string[];
}
export interface GenerateDocInput {
  templateId: string;
  employeeId?: string | null;
  title?: string;
  overrides?: Record<string, string>;
}

export interface Goal {
  id: string;
  title: string;
  description: string | null;
  status: "OPEN" | "ACHIEVED" | "MISSED";
  progress: number;
  targetDate: string | null;
  createdAt: string;
}

/** Performance review cycles (feedback C.7). */
export type ReviewStatus =
  | "PENDING_SELF"
  | "PENDING_MANAGER"
  | "SUBMITTED"
  | "APPROVED"
  | "CLOSED";
export type HikeType = "PERCENT" | "NEW_SALARY" | "NONE";

export interface ReviewCycle {
  id: string;
  name: string;
  periodStart: string;
  periodEnd: string;
  status: "OPEN" | "CLOSED";
  reviewCount: number;
  submittedCount: number;
  approvedCount: number;
  createdAt: string;
}

export interface PerformanceReview {
  id: string;
  cycleId: string;
  cycleName: string;
  periodStart: string | null;
  periodEnd: string | null;
  cycleStatus: "OPEN" | "CLOSED" | null;
  employeeId: string;
  employeeName: string;
  jobTitle: string | null;
  managerId: string | null;
  managerName: string | null;
  status: ReviewStatus;
  selfAssessment: string | null;
  selfSubmittedAt: string | null;
  rating: number | null;
  summary: string | null;
  strengths: string | null;
  improvements: string | null;
  hikeType: HikeType | null;
  hikePercent: number | null;
  proposedSalary: number | null;
  hikeNote: string | null;
  managerSubmittedAt: string | null;
  decidedAt: string | null;
  currency: string;
  currentSalary: number | null;
  goalsAchieved: number;
  goalsTotal: number;
  goals: Goal[];
}

export interface CreateCycleInput {
  name: string;
  periodStart: string;
  periodEnd: string;
}
export interface SelfAssessmentInput {
  selfAssessment: string;
  submit: boolean;
}
export interface ManagerReviewInput {
  rating?: number;
  summary?: string;
  strengths?: string;
  improvements?: string;
  hikeType?: HikeType;
  hikePercent?: number;
  proposedSalary?: number;
  hikeNote?: string;
  submit: boolean;
}

/** Recruitment / ATS. */
export type JobStatus = "OPEN" | "ON_HOLD" | "CLOSED";
export type CandidateStage = "APPLIED" | "SCREENING" | "INTERVIEW" | "OFFER" | "HIRED" | "REJECTED";
export interface JobOpening {
  id: string;
  title: string;
  departmentId: string | null;
  department: string | null;
  location: string | null;
  employmentType: string | null;
  description: string | null;
  positions: number;
  status: JobStatus;
  candidateCount: number;
  hiredCount: number;
  createdAt: string;
}
export interface Candidate {
  id: string;
  jobId: string;
  name: string;
  email: string | null;
  phone: string | null;
  resumeUrl: string | null;
  source: string | null;
  stage: CandidateStage;
  rating: number | null;
  notes: string | null;
  createdAt: string;
}
export interface JobOpeningInput {
  title: string;
  departmentId?: string;
  location?: string;
  employmentType?: string;
  description?: string;
  positions?: number;
  status?: JobStatus;
}
export interface CandidateInput {
  name: string;
  email?: string;
  phone?: string;
  resumeUrl?: string;
  source?: string;
  stage?: CandidateStage;
  rating?: number | null;
  notes?: string;
}

// --- shift scheduling / rostering ---
export interface Shift {
  id: string;
  name: string;
  startTime: string;
  endTime: string;
  color: string | null;
}
export interface ShiftInput {
  name: string;
  startTime: string;
  endTime: string;
  color?: string;
}
export interface RosterEmployee {
  employeeId: string;
  name: string;
  jobTitle: string | null;
}
export interface RosterEntry {
  id: string;
  employeeId: string;
  shiftId: string;
  onDate: string;
}
export interface Roster {
  weekStart: string;
  days: string[];
  shifts: Shift[];
  employees: RosterEmployee[];
  assignments: RosterEntry[];
}

// --- platform owner (vendor) console + subscriptions ---
export interface CompanySummary {
  companyId: string;
  name: string;
  slug: string;
  status: string;
  adminName: string;
  adminEmail: string;
  headcount: number;
  seats: number;
  subscriptionStatus: string;
  endsAt: string | null;
  daysLeft: number | null;
  locked: boolean;
  pricePerEmployee: number | null;
  /** True when this rate was agreed with the customer, so publishing a new price list won't move it. */
  customPrice: boolean;
  monthlyRevenue: number | null;
  currency: string;
  createdAt: string | null;
  /** Null for a company sold direct; set when it belongs to an agency. */
  agencyId: string | null;
  agencyName: string | null;
}

/** An agency (a customer running several companies) as the platform owner sees it. */
export interface AgencySummary {
  agencyId: string;
  name: string;
  slug: string;
  ownerName: string;
  ownerEmail: string;
  companyCount: number;
  headcount: number;
  monthlyRevenue: number | null;
  currency: string;
  createdAt: string | null;
}
export interface CreateAgencyInput {
  agencyName: string;
  ownerFirstName: string;
  ownerLastName: string;
  ownerEmail: string;
  password: string;
}

/** The agency's own headline figures. `monthlySpend` is what they are billed, not what they earn. */
export interface AgencyOverview {
  agencyName: string;
  companies: number;
  headcount: number;
  seats: number;
  lockedCompanies: number;
  monthlySpend: number | null;
  currency: string;
}

export interface CreateCompanyInput {
  companyName: string;
  adminFirstName: string;
  adminLastName: string;
  adminEmail: string;
  password: string;
  seats: number;
  months: number;
  /** Owner console only: file the company under an agency. Omit to sell direct. */
  agencyId?: string | null;
  /** What this customer is billed in; decides which price list applies. Omit for INR. */
  currency?: string | null;
}
/**
 * Someone who asked for a free trial (PD-21). Not a customer yet — until the vendor approves it there
 * is no company and no login, which is the whole point of the type existing.
 */
export interface TrialRequest {
  id: string;
  companyName: string;
  contactName: string;
  email: string;
  phone: string | null;
  teamSize: string | null;
  note: string | null;
  status: "NEW" | "APPROVED" | "DECLINED";
  source: string | null;
  createdAt: string;
  decidedAt: string | null;
  companyId: string | null;
}
export interface TrialRequestInput {
  companyName: string;
  contactName: string;
  email: string;
  phone?: string;
  teamSize?: string;
  note?: string;
  source?: string;
}
export interface SeatRequest {
  id: string;
  companyId: string;
  companyName: string;
  currentSeats: number;
  requestedSeats: number;
  status: string;
  note: string | null;
  createdAt: string;
}
export interface SubscriptionView {
  status: string;
  seats: number;
  seatsUsed: number;
  endsAt: string | null;
  daysLeft: number | null;
  locked: boolean;
  pendingRequestSeats: number | null;
  pricePerEmployee: number | null;
  /** What the company is billed this month at that rate — zero once the subscription has ended. */
  monthlyCharge: number | null;
  currency: string;
}

// --- HR Helpdesk ---
export type TicketCategory = "HR" | "PAYROLL" | "IT" | "FACILITIES" | "OTHER";
export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type TicketStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
export interface HelpdeskTicket {
  id: string;
  category: TicketCategory;
  subject: string;
  description: string | null;
  priority: TicketPriority;
  status: TicketStatus;
  raisedById: string;
  raisedByName: string;
  assigneeId: string | null;
  assigneeName: string | null;
  commentCount: number;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}
export interface HelpdeskComment {
  id: string;
  authorId: string;
  authorName: string;
  body: string;
  createdAt: string;
}
export interface RaiseTicketInput {
  category: TicketCategory;
  subject: string;
  description?: string;
  priority?: TicketPriority;
}
export interface UpdateTicketInput {
  status?: TicketStatus;
  assigneeId?: string;
  priority?: TicketPriority;
  category?: TicketCategory;
}

/** A page of results from a paginated list endpoint. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Subscription billing — per active employee, per month. */
export interface BillingOverview {
  plan: string;
  status: "TRIALING" | "ACTIVE" | "PAST_DUE" | "CANCELLED";
  pricePerEmployee: number;
  pricePerEmployeePerYear: number;
  currency: string;
  trialEndsAt: string | null;
  trialActive: boolean;
  billableEmployees: number;
  monthlyCharge: number;
  annualCharge: number;
  currentMonth: string;
  paidThrough: string | null;
  /**
   * The published volume tiers. Once a company crosses one, the bill is no longer headcount × a
   * single rate, so the UI shows the ladder instead of arithmetic that doesn't add up. Null for a
   * company on a negotiated flat rate, where there's nothing to explain.
   */
  tiers: { fromEmployee: number; toEmployee: number | null; rate: number }[] | null;
  /** The floor for this company, and whether it's what they're actually paying this month. */
  monthlyMinimum: number | null;
  minimumApplied: boolean;
  /** Cost of paying a year upfront, and what that saves against twelve monthly payments. */
  annualChargePrepaid: number;
  annualSaving: number;
  invoices: { month: string; headcount: number; amount: number; status: "PAID" | "DUE" | "OVERDUE" }[];
}

/**
 * One version of the platform's price list. Versioned by start date so a price change never rewrites
 * what a customer was already invoiced.
 */
export interface PriceListVersion {
  id: string;
  effectiveFrom: string;
  note: string | null;
  current: boolean;
  /** `toEmployee` is null on the final, open-ended tier. */
  tiers: { fromEmployee: number; toEmployee: number | null; rate: number }[];
  /** Floor a company pays regardless of headcount; 0 disables it. */
  monthlyMinimum: number;
  /** Months charged for an annual prepayment — 10 means two months free. */
  annualMonthsCharged: number;
  /** Which currency this list prices in. One published list per currency. */
  currency: string;
}

/** Configurable payslip template (feedback: "add template for creating payslip"). */
export type PayComponentKind = "EARNING" | "DEDUCTION";
export type PayComponentCalc = "PERCENT_OF_GROSS" | "PERCENT_OF_BASIC" | "FIXED" | "REMAINDER";
export interface PayslipComponent {
  id?: string;
  name: string;
  kind: PayComponentKind;
  calc: PayComponentCalc;
  value: number | null;
  basis: boolean;
  sortOrder?: number;
}

/** Analytics / Insights dashboard (Owner/Admin). A chart series is a list of these. */
export interface Slice {
  label: string;
  value: number;
}
export interface AnalyticsOverview {
  people: {
    headcount: number;
    newJoinersThisYear: number;
    avgTenureMonths: number;
    onLeaveToday: number;
    goalsOpen: number;
    goalsAchieved: number;
    goalsMissed: number;
    avgGoalProgress: number;
    byDepartment: Slice[];
    headcountGrowth: Slice[];
    ratingDistribution: Slice[];
    leaveByType: Slice[];
  };
  work: {
    projects: number;
    tasksByStatus: Slice[];
    tasksByPriority: Slice[];
    ticketsByStatus: Slice[];
    activeSprint: { name: string; committed: number; done: number; remaining: number; unestimated: number } | null;
    velocity: Slice[];
  };
  finance: {
    currency: string;
    pending: number;
    awaitingReimbursement: number;
    reimbursedThisYear: number;
    byCategory: Slice[];
  };
}

export interface WorkItem {
  ref: string;
  title: string;
  status: TaskStatusT;
  priority: TaskPriorityT;
  projectId: string;
  projectName: string | null;
  dueDate: string | null;
  overdue: boolean;
}

export type LeaveTypeT = "VACATION" | "SICK" | "PERSONAL" | "UNPAID";
export type LeaveStatusT = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export interface LeaveRequest {
  id: string;
  employeeId: string;
  employeeName: string;
  type: LeaveTypeT;
  startDate: string;
  endDate: string;
  days: number;
  reason: string | null;
  status: LeaveStatusT;
  decidedAt: string | null;
  createdAt: string;
}

export interface LeaveBalance {
  allowanceDays: number;
  usedDays: number;
  remainingDays: number;
  pendingDays: number;
}

export type ChecklistKind = "ONBOARDING" | "EXIT";

export interface OnboardingTask {
  id: string;
  employeeId: string;
  kind: ChecklistKind;
  title: string;
  sortOrder: number;
  completed: boolean;
  completedAt: string | null;
}

/** One person's exit: where they are, how far clearance has got, and what has been issued. */
export interface ExitView {
  employeeId: string;
  employeeName: string | null;
  employmentStatus: EmploymentStatus;
  lastWorkingDay: string | null;
  reason: string | null;
  exitStartedAt: string | null;
  managerName: string | null;
  tasksDone: number;
  tasksTotal: number;
  checklistComplete: boolean;
  checklist: OnboardingTask[];
  letters: { id: string; kind: DocumentKind; title: string; createdAt: string }[];
}
export interface StartExitInput {
  lastWorkingDay: string;
  reason?: string;
  seedChecklist?: boolean;
}

export interface MakeOfferInput {
  jobTitle?: string;
  startDate?: string;
  workLocation?: string;
  employmentType?: string;
  annualSalary?: number;
  currency?: string;
  departmentId?: string;
}
export interface OfferResult {
  candidate: Candidate;
  documentId: string | null;
  documentTitle: string | null;
  /** Set when no template of that kind exists, so the screen can say so instead of silently doing nothing. */
  letterNote: string | null;
}
export interface HireInput {
  role?: "ADMIN" | "HR" | "MANAGER" | "MEMBER";
  jobTitle?: string;
  startDate?: string;
  departmentId?: string;
  issueJoiningLetter?: boolean;
}
export interface HireResult extends OfferResult {
  invitationId: string;
  /** Returned as well as emailed — onboarding must not depend on mail being deliverable. */
  joinLink: string | null;
}

export type TaskStatusT = "TODO" | "IN_PROGRESS" | "DONE";
export type TaskPriorityT = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export interface Project {
  id: string;
  name: string;
  key: string;
  description: string | null;
  status: "ACTIVE" | "ARCHIVED";
  leadUserId: string | null;
  leadName: string | null;
  taskCount: number;
  openTaskCount: number;
  createdAt: string;
}

export interface Task {
  id: string;
  projectId: string;
  ref: string;
  number: number;
  title: string;
  description: string | null;
  status: TaskStatusT;
  priority: TaskPriorityT;
  assigneeId: string | null;
  assigneeName: string | null;
  sprintId: string | null;
  dueDate: string | null;
  /** Estimate; null when the task hasn't been sized. */
  storyPoints: number | null;
  createdAt: string;
}

export type SprintStatusT = "PLANNED" | "ACTIVE" | "COMPLETED";

export interface Sprint {
  id: string;
  projectId: string;
  name: string;
  goal: string | null;
  startDate: string | null;
  endDate: string | null;
  status: SprintStatusT;
  /** What the team believes it can take on this sprint. */
  capacityPoints: number | null;
  taskCount: number;
  doneCount: number;
  createdAt: string;
}

/** The board view: the active sprint (null if none) and the tasks currently on the board. */
export interface Board {
  activeSprint: Sprint | null;
  tasks: Task[];
}

export type TicketStatusT = "OPEN" | "PENDING" | "RESOLVED" | "CLOSED";

export interface Ticket {
  id: string;
  projectId: string;
  ref: string;
  number: number;
  subject: string;
  description: string | null;
  requesterName: string | null;
  requesterEmail: string | null;
  status: TicketStatusT;
  priority: TaskPriorityT;
  assigneeId: string | null;
  assigneeName: string | null;
  createdAt: string;
}

export type SpaceStatusT = "ACTIVE" | "ARCHIVED";
export type PageStatusT = "DRAFT" | "PUBLISHED";

export interface Space {
  id: string;
  name: string;
  key: string;
  description: string | null;
  status: SpaceStatusT;
  pageCount: number;
  createdAt: string;
}

/** Full page detail (includes the Markdown body + resolved cross-app labels). */
export interface KnowledgePage {
  id: string;
  spaceId: string;
  parentId: string | null;
  title: string;
  body: string | null;
  status: PageStatusT;
  authorId: string | null;
  authorName: string | null;
  linkedTaskId: string | null;
  linkedTaskRef: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Lightweight page row for trees, "my pages", and search (no body; optional snippet). */
export interface PageSummary {
  id: string;
  spaceId: string;
  spaceName: string | null;
  parentId: string | null;
  title: string;
  status: PageStatusT;
  authorName: string | null;
  linkedTaskRef: string | null;
  snippet: string | null;
  updatedAt: string;
}

export interface Department {
  id: string;
  name: string;
  parentId: string | null;
  leadUserId: string | null;
  leadName: string | null;
  memberCount: number;
}

export interface DashboardSummary {
  companyName: string;
  yourRole: Role;
  // People
  memberCount: number;
  pendingInviteCount: number;
  departmentCount: number;
  // Work
  projectCount: number;
  openTaskCount: number;
  doneTaskCount: number;
  openTicketCount: number;
  // Knowledge
  spaceCount: number;
  pageCount: number;
  // Active sprint progress (null when none running)
  activeSprint: { name: string; total: number; done: number } | null;
}

export interface LoginResult {
  accessToken: string;
  me: Me;
}

export interface SearchHit {
  kind: "person" | "project" | "task" | "ticket" | "space" | "page" | "client" | "document";
  title: string;
  subtitle: string;
  href: string;
}
export interface SearchGroup {
  label: string;
  hits: SearchHit[];
}
export interface SearchResponse {
  query: string;
  total: number;
  groups: SearchGroup[];
}

export interface AssistantSource {
  kind: string;
  title: string;
  href: string;
}
export interface AssistantResponse {
  answer: string;
  mode: "claude" | "local";
  sources: AssistantSource[];
}

export interface LeaveTodayEntry {
  employeeName: string;
  type: string;
  reason: string | null;
  startDate: string;
  endDate: string;
}
export interface CalendarLeave {
  employeeName: string;
  type: string;
  status: string;
  startDate: string;
  endDate: string;
}
export interface TeamOverview {
  headcount: number;
  presentToday: number;
  onLeaveToday: number;
  /** How many of `presentToday` are assumed rather than recorded (nobody marked them). */
  unmarkedToday: number;
  outToday: LeaveTodayEntry[];
  monthLeaves: CalendarLeave[];
}

export interface CompensationEntry {
  id: string;
  effectiveDate: string;
  annualAmount: number;
  changeType: string;
  reason: string | null;
  hikeAmount: number | null;
  hikePercent: number | null;
}
export interface Compensation {
  employeeId: string;
  employeeName: string;
  currency: string;
  currentAnnual: number | null;
  currentMonthly: number | null;
  effectiveDate: string | null;
  history: CompensationEntry[];
}
export interface PayslipLine {
  label: string;
  amount: number;
}
export interface Payslip {
  employeeId: string;
  employeeName: string;
  month: string;
  currency: string;
  /** Company header — legal name, address and logo as configured in Settings → Payslip branding. */
  companyName: string;
  companyAddress: string | null;
  companyLogoUrl: string | null;
  /** Who the payslip is for. */
  employeeNo: string | null;
  dateJoined: string | null;
  department: string | null;
  designation: string | null;
  /** Statutory identifiers a payslip is expected to carry. PAN arrives masked. */
  paymentMode: string | null;
  uan: string | null;
  pfNumber: string | null;
  panMasked: string | null;
  earnings: PayslipLine[];
  deductions: PayslipLine[];
  gross: number;
  totalDeductions: number;
  net: number;
  /** The net spelled out, as a payslip conventionally states it. */
  netInWords: string | null;
  workingDays: number;
  lopDays: number;
  payableDays: number;
}

/**
 * An employee's bank / statutory / identity record ("My Finances"). The account number and PAN
 * arrive already masked — the full values never reach the browser.
 */
export interface EmployeeFinance {
  employeeId: string;
  employeeName: string;
  paymentMode: string;
  bankName: string | null;
  bankAccountMasked: string | null;
  bankIfsc: string | null;
  bankAccountName: string | null;
  bankBranch: string | null;
  pfStatus: "ENABLED" | "NOT_ELIGIBLE";
  pfNumber: string | null;
  uan: string | null;
  pfJoinDate: string | null;
  pfAccountName: string | null;
  esiStatus: "ELIGIBLE" | "NOT_ELIGIBLE";
  esiNumber: string | null;
  ptState: string | null;
  ptLocation: string | null;
  panMasked: string | null;
  panVerified: boolean;
  dateOfBirth: string | null;
  parentName: string | null;
}

export interface PayrollRunRow {
  employeeId: string;
  name: string;
  jobTitle: string | null;
  gross: number;
  lopDays: number;
  net: number;
}
export interface PayrollRun {
  month: string;
  currency: string;
  rows: PayrollRunRow[];
  totalGross: number;
  totalNet: number;
  totalLopDays: number;
  employees: number;
}

/** Shape of the one API error envelope (Sprint1 §13). */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  correlationId?: string;
  errors?: { field: string; message: string }[];
}

/** "bankIfsc" -> "Bank IFSC", "panNumber" -> "PAN number" — field names as the form labels them. */
function humanizeField(field: string): string {
  const words = field
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/^./, (c) => c.toUpperCase())
    .split(" ");
  const acronyms = new Set(["IFSC", "PAN", "UAN", "PF", "ESI", "PT", "NO", "URL", "ID"]);
  return words
    .map((w) => (acronyms.has(w.toUpperCase()) ? w.toUpperCase() : w.toLowerCase()))
    .join(" ")
    .replace(/^./, (c) => c.toUpperCase());
}

export class ApiError extends Error {
  status: number;
  code: string;
  fieldErrors: Record<string, string>;

  /**
   * The server answers a failed validation with a generic "Validation failed" plus a per-field list.
   * Showing only `message` told the user nothing about what to fix, so the readable summary is built
   * here — every form that renders `error.message` gets the detail for free.
   */
  constructor(body: ApiErrorBody) {
    const fields = body.errors ?? [];
    super(
      fields.length === 0
        ? body.message
        : fields.map((e) => `${humanizeField(e.field)} — ${e.message}`).join(". "),
    );
    this.status = body.status;
    this.code = body.code;
    this.fieldErrors = Object.fromEntries(fields.map((e) => [e.field, e.message]));
  }
}
