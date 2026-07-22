// API contract types (Sprint1 §7). Kept in sync with backend DTOs.

export type Role = "OWNER" | "ADMIN" | "MEMBER";
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
  };
}

export interface CompanySettings {
  companyId: string;
  timezone: string;
  locale: string;
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
}

export type EmploymentType = "FULL_TIME" | "PART_TIME" | "CONTRACT" | "INTERN";
export type EmploymentStatus = "ONBOARDING" | "ACTIVE" | "TERMINATED";

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

export interface Goal {
  id: string;
  title: string;
  description: string | null;
  status: "OPEN" | "ACHIEVED" | "MISSED";
  progress: number;
  targetDate: string | null;
  createdAt: string;
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

export interface OnboardingTask {
  id: string;
  employeeId: string;
  title: string;
  sortOrder: number;
  completed: boolean;
  completedAt: string | null;
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
  kind: "person" | "project" | "task" | "ticket" | "space" | "page" | "client";
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
  earnings: PayslipLine[];
  deductions: PayslipLine[];
  gross: number;
  totalDeductions: number;
  net: number;
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

export class ApiError extends Error {
  status: number;
  code: string;
  fieldErrors: Record<string, string>;

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.status = body.status;
    this.code = body.code;
    this.fieldErrors = Object.fromEntries((body.errors ?? []).map((e) => [e.field, e.message]));
  }
}
