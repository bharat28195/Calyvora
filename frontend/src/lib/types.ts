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
    /** Someone else chose this password (a new company's first admin); the app asks for a new one first. */
    mustChangePassword?: boolean;
    /** What this person chose for themselves; null where they kept the default (V68). */
    preferences?: UserPreferences;
  };
  company: {
    id: string;
    name: string;
    slug: string;
    status: CompanyStatus;
    currency: string;
    timezone: string;
    /** Minutes of inactivity before this company signs people out; null means never. */
    sessionIdleMinutes: number | null;
  };
  /**
   * The zone this person's clocks run on: their own if they set one, else the company's. Resolved
   * by the same server code that stamps attendance, so the clock on screen and the punch cannot
   * disagree. Set the app-wide formatters from this, not from company.timezone.
   */
  timezone: string;
  /** The language to show the app in: theirs, else the company's, else English (V68). */
  language?: string;
  /**
   * What this person may do, permission key → "COMPANY" or "TEAM" (PD-54). Optional only for a
   * session cached before roles existed; read it through lib/permissions, which falls back to the
   * built-in defaults for the role.
   */
  permissions?: Record<string, "COMPANY" | "TEAM">;
}

export type DateFormatPref = "DMY" | "MDY" | "YMD";
export type TimeFormatPref = "H12" | "H24";

/** A person's own display choices. Null means "use the default". */
export interface UserPreferences {
  language: string | null;
  timezone: string | null;
  dateFormat: DateFormatPref | null;
  timeFormat: TimeFormatPref | null;
}

export interface CompanySettings {
  companyId: string;
  timezone: string;
  locale: string;
  currency: string;
  legalName: string | null;
  address: string | null;
  logoUrl: string | null;
  /** Minutes of inactivity before a session ends; null means never. */
  sessionIdleMinutes: number | null;
}

export interface Member {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  status: UserStatus;
  /** The custom role they hold (PD-54); null means the built-in their `role` names. */
  companyRoleId?: string | null;
}

/** A company role and what it may do (PD-54). */
export interface CompanyRole {
  id: string;
  name: string;
  description: string | null;
  /** ADMIN | HR | MANAGER | EMPLOYEE for a built-in; null for a role the company made. */
  builtin: "ADMIN" | "HR" | "MANAGER" | "EMPLOYEE" | null;
  /** The Admin role: always every permission, cannot be edited. */
  locked: boolean;
  permissions: Record<string, "COMPANY" | "TEAM">;
  memberCount: number;
}

export interface PermissionInfo {
  key: string;
  group: string;
  label: string;
  description: string;
  /** Can be limited to the holder's own team. */
  scoped: boolean;
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

/**
 * One line in a person picker.
 *
 * Deliberately not an Employee: that carries phone, start date, employment status, skills and a
 * performance rating, none of which belongs in an assignee dropdown. A picker that cannot carry the
 * rating cannot leak it.
 */
export interface EmployeeOption {
  id: string;
  name: string;
  email: string;
  jobTitle: string | null;
}

export interface Employee {
  id: string;
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  role: Role;
  employeeNo: string | null;
  jobTitle: string | null;
  /** A rung on the company's own ladder. Grants nothing — see {@link Designation}. */
  designationId: string | null;
  employmentType: EmploymentType | null;
  employmentStatus: EmploymentStatus;
  departmentId: string | null;
  managerId: string | null;
  workLocation: string | null;
  /** Their own IANA zone, or null for "same as the company". */
  timezone: string | null;
  phone: string | null;
  startDate: string | null;
  endDate: string | null;
  skills: string[];
  rating: number | null;
}

/**
 * A rung on the ladder a company defines for itself — Intern, Junior, Senior, Lead.
 *
 * It is a label and nothing more. Access comes from the reporting tree and the role; neither reads
 * this. That is what makes it safe to let customers edit it.
 */
export interface Designation {
  id: string;
  name: string;
  /** Ascending, sparse by convention (0, 10, 20) so a rung can be slotted between two others. */
  level: number;
  archived: boolean;
  headcount: number;
}

/** One person on the "My team" roster. Deliberately carries no pay of any kind. */
export interface TeamMember {
  employeeId: string;
  userId: string | null;
  name: string;
  jobTitle: string | null;
  department: string | null;
  managerName: string | null;
  /** Reports to the viewer directly, as opposed to somewhere further down the tree. */
  direct: boolean;
  depth: number;
  employmentStatus: EmploymentStatus | null;
  todayStatus: AttendanceStatus | null;
  presentDays: number;
  absentDays: number;
  leaveDays: number;
  pendingLeaveRequests: number;
  openExpenseClaims: number;
  openExpenseAmount: number;
  rating: number | null;
  reviewStatus: ReviewStatus | null;
}

export interface TeamSummary {
  month: string;
  directCount: number;
  totalCount: number;
  presentToday: number;
  onLeaveToday: number;
  pendingLeaveRequests: number;
  openExpenseClaims: number;
  members: TeamMember[];
}

/** Whether the signed-in person leads anybody — what decides if "My team" is in the nav. */
export interface TeamStanding {
  leadsTeam: boolean;
  directCount: number;
  totalCount: number;
}


/** Sprint reporting — burndown, velocity, capacity and per-person load. */
export interface MemberLoad {
  employeeId: string;
  name: string;
  points: number;
  tasks: number;
  donePoints: number;
}
export interface SprintVelocity {
  sprintId: string;
  name: string;
  endDate: string | null;
  committedPoints: number;
  completedPoints: number;
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
// --- income tax (India) -----------------------------------------------------------------------

export type TaxRegime = "OLD" | "NEW";
export type AgeBand = "BELOW_60" | "SENIOR" | "SUPER_SENIOR";
export type ProofStatus = "NONE" | "SUBMITTED" | "ACCEPTED" | "PARTIAL" | "REJECTED";

/** One line the declaration form can show (Income-tax Act, 2025 numbering, the 1961 one alongside). */
export interface TaxCatalogEntry {
  key: string;
  section: string | null;
  oldSection: string | null;
  /** "Sec 123 (80C)" */
  sectionLabel: string;
  label: string;
  hint: string | null;
  group: string;
  /** Income to be taxed, rather than a relief. */
  income: boolean;
  allowedInNewRegime: boolean;
}

export interface TaxProofFile {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface TaxItem {
  key: string;
  amount: number;
  detail: string | null;
  proofStatus: ProofStatus;
  acceptedAmount: number | null;
  reviewNote: string | null;
  proofs: TaxProofFile[];
}

export interface TaxRent {
  id: string;
  fromMonth: string;
  toMonth: string;
  monthlyRent: number;
  city: string;
  metro: boolean;
  landlordName: string | null;
  landlordPan: string | null;
  landlordAddress: string | null;
  landlordRelationship: string | null;
  proofStatus: ProofStatus;
  acceptedRent: number | null;
  reviewNote: string | null;
  proofs: TaxProofFile[];
}

export interface TaxHouse {
  id: string;
  letOut: boolean;
  address: string | null;
  lenderName: string | null;
  lenderPan: string | null;
  lenderAddress: string | null;
  lenderType: LenderType | null;
  interest: number;
  annualRent: number;
  municipalTax: number;
  proofStatus: ProofStatus;
  acceptedInterest: number | null;
  reviewNote: string | null;
  proofs: TaxProofFile[];
}

export interface TaxPrevious {
  employerName: string | null;
  tan: string | null;
  income: number | null;
  tds: number | null;
  pf: number | null;
  pt: number | null;
  status: "NONE" | "SUBMITTED" | "ACCEPTED" | "REJECTED";
  reviewNote: string | null;
  proofs: TaxProofFile[];
}

export type LenderType = "FINANCIAL_INSTITUTION" | "EMPLOYER" | "OTHER";

export interface TaxDeclaration {
  employeeId: string;
  employeeName: string;
  /** Form 124 item 1, as the employee last certified it. */
  employeeAddress: string | null;
  employeePan: string | null;
  /** For "son / daughter of" in the verification. */
  parentName: string | null;
  designation: string | null;
  financialYear: string;
  regime: TaxRegime;
  status: "NOT_STARTED" | "DRAFT" | "SUBMITTED";
  submittedAt: string | null;
  /** Whether HR still accepts changes for this year (and so whether the regime can change). */
  windowOpen: boolean;
  proofsOpen: boolean;
  proofDeadline: string | null;
  /** Past the proof deadline: only what HR accepted reduces the tax now. */
  proofsDue: boolean;
  parentsSenior: boolean;
  ageBand: AgeBand;
  dateOfBirthKnown: boolean;
  salaryHasHra: boolean;
  salaryHasLta: boolean;
  /** Line key to the amount claimed (the older flat shape). */
  declared: Record<string, number>;
  items: TaxItem[];
  rent: TaxRent[];
  houses: TaxHouse[];
  previous: TaxPrevious | null;
  catalog: TaxCatalogEntry[];
}

export interface TaxRentInput {
  id?: string | null;
  fromMonth: string;
  toMonth: string;
  monthlyRent: number;
  city: string;
  landlordName?: string | null;
  landlordPan?: string | null;
  landlordAddress?: string | null;
  landlordRelationship?: string | null;
}

export interface TaxHouseInput {
  id?: string | null;
  letOut: boolean;
  address?: string | null;
  lenderName?: string | null;
  lenderPan?: string | null;
  lenderAddress?: string | null;
  lenderType?: LenderType | null;
  interest: number;
  annualRent?: number;
  municipalTax?: number;
}

export interface TaxPreviousInput {
  employerName?: string | null;
  tan?: string | null;
  income?: number;
  tds?: number;
  pf?: number;
  pt?: number;
}

/** What is saved. Every part is optional; a missing part is left as it is. */
export interface TaxDeclarationInput {
  regime?: TaxRegime;
  parentsSenior?: boolean;
  items?: { key: string; amount: number; detail?: string | null }[];
  rent?: TaxRentInput[];
  houses?: TaxHouseInput[];
  previous?: TaxPreviousInput;
  employeeAddress?: string | null;
}

export interface TaxBandRow {
  from: number;
  to: number | null;
  ratePercent: number;
  taxable: number;
  tax: number;
}

export interface TaxDeductionRow {
  key: string;
  section: string;
  label: string;
  /** What the calculation used: claimed until the proof deadline, approved after it. */
  declared: number;
  allowed: number;
  /** What the employee typed, and what HR approved (null until reviewed). Null for lines Orbit works out. */
  claimed?: number | null;
  approved?: number | null;
  proofStatus?: string | null;
  /** For a line inside a shared ceiling: the ceiling, and how much the lines above it used. */
  limit?: number | null;
  usedBefore?: number | null;
  /** Home-loan interest counted under Section 22 instead of here. */
  movedToHouse?: number | null;
}

export interface TaxGroupRow {
  group: string;
  claimed: number;
  cap: number;
  allowed: number;
}

export interface TaxMonthRow {
  month: string;
  source: "OPENING" | "LOCKED" | "PROJECTED" | "NONE";
  gross: number;
  tds: number | null;
  pt: number;
  /** The month's earnings line by line (Basic, HRA…); empty for months not paid here. */
  heads: { name: string; amount: number }[];
}

export interface TaxHraMonthRow {
  month: string;
  hraReceived: number;
  rentLessTenPercent: number;
  percentOfBasic: number;
  exempt: number;
}

export interface TaxRegimeComparison {
  oldRegimeTax: number;
  newRegimeTax: number;
  cheaper: TaxRegime;
  saving: number;
}

export interface TaxComputation {
  financialYear: string;
  regime: TaxRegime;
  ageBand: AgeBand;
  currency: string;
  grossSalary: number;
  previousEmployerIncome: number;
  exemptions: TaxDeductionRow[];
  standardDeduction: number;
  professionalTax: number;
  salaryIncome: number;
  houseProperty: number;
  otherIncome: number;
  grossTotalIncome: number;
  deductions: TaxDeductionRow[];
  groups: TaxGroupRow[];
  totalDeductions: number;
  taxableIncome: number;
  bands: TaxBandRow[];
  taxOnIncome: number;
  rebate: number;
  surcharge: number;
  cess: number;
  totalTax: number;
  monthlyTds: number;
  monthsElapsed: number;
  deductedSoFar: number;
  remainingTax: number;
  /** What the next pay run should withhold — what is left, over the months that are left. */
  projectedNextMonth: number;
  comparison: TaxRegimeComparison;
  /** False while the employer does not withhold income tax through Orbit — the figures are an estimate. */
  withheldByPayroll: boolean;
  proofsDue: boolean;
  months: TaxMonthRow[];
  hraMonths: TaxHraMonthRow[];
  /** Salary and tax from before this payroll: a previous employer or an opening balance. */
  priorIncome: number;
  priorTds: number;
  /** Home-loan interest declared under 130 / 131 that counts under Section 22 instead. */
  interestMovedToHouse: number;
}

export interface TaxDeclarationRow {
  employeeId: string;
  employeeName: string;
  regime: TaxRegime;
  status: string;
  totalDeclared: number;
  annualTax: number;
  proofs: number;
  awaitingReview: number;
}

export interface TaxSettings {
  declarationsOpen: boolean;
  proofsOpen: boolean;
  proofDeadline: string | null;
  /** The person responsible for deducting tax, who signs Forms 124 and 130. */
  signerName?: string | null;
  signerParent?: string | null;
  signerDesignation?: string | null;
  signerPlace?: string | null;
  /** The CIT (TDS) the TAN falls under — Form 130 Part A. */
  citTdsAddress?: string | null;
}

export interface TdsDepositMonth {
  month: string;
  finalised: boolean;
  tdsDeducted: number;
  bsrCode: string | null;
  depositDate: string | null;
  challanSerial: string | null;
  amount: number | null;
}

export interface TdsDeposits {
  financialYear: string;
  quarters: { quarter: string; label: string; receiptNo: string | null; months: TdsDepositMonth[] }[];
}

export interface TdsChallanInput {
  bsrCode: string;
  depositDate: string;
  challanSerial: string;
  amount?: number | null;
}

export interface TaxReviewInput {
  type: "ITEM" | "RENT" | "HOUSE" | "PREVIOUS";
  id?: string | null;
  status: "ACCEPTED" | "PARTIAL" | "REJECTED";
  acceptedAmount?: number | null;
  note?: string | null;
}

export interface Form130 {
  financialYear: string;
  employerName: string;
  employerAddress: string | null;
  employerPan: string | null;
  employerTan: string | null;
  citTdsAddress: string | null;
  employeeName: string;
  employeeAddress: string | null;
  employeePan: string | null;
  employeeNo: string | null;
  designation: string | null;
  periodFrom: string;
  periodTo: string;
  signer: { name: string | null; parent: string | null; designation: string | null; place: string | null };
  /** Part A summary — finalised months only. */
  quarters: { quarter: string; label: string; receiptNo: string | null; amountPaid: number; tds: number; deposited: number }[];
  /** Part A, section II — one row per month with tax; challan fields null until HR records it. */
  challans: { month: string; tds: number; bsrCode: string | null; depositDate: string | null; challanSerial: string | null }[];
  computation: TaxComputation;
}

export interface ExpenseSummary {
  claims: ExpenseClaim[];
  /** Where to continue from; null at the end. The totals below are for the whole set, not the page. */
  nextCursor: string | null;
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
  | "ANNOUNCEMENT"
  | "EXIT_REQUESTED"
  | "EXIT_DECIDED"
  | "CHECKOUT_MISSED";

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
  /** True when the status was inferred (leave, a weekend, or the absent/half-day rules) rather than marked. */
  derived: boolean;
  /** Each check-in/check-out pair; checkOut is null while it is still running (V70). */
  sessions?: { checkIn: string; checkOut: string | null }[];
  /** First check-in to last check-out, in minutes. */
  grossMinutes?: number | null;
  /** Time actually checked in (closed sessions), in minutes. */
  effectiveMinutes?: number | null;
  /** Set while checked in: when the running session started. */
  openSince?: string | null;
  /** Hours owed that day, from the rostered shift or the company standard (9 h by default). */
  requiredMinutes?: number | null;
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
  /** Every working day of the month that has already passed — not just the days with a record. */
  expectedDays: number;
  /** How many of those expected days have nothing recorded. Explains the gap to worked. */
  notRecorded: number;
  attendanceRate: number | null;
}
/** A month of attendance for a group, one row per day — what a calendar grid needs. */
export interface AttendanceMonthSummary {
  month: string;
  headcount: number;
  days: AttendanceDaySummary[];
}
export interface AttendanceDaySummary {
  date: string;
  present: number;
  onLeave: number;
  absent: number;
  unmarked: number;
  weekOff: number;
  holiday: boolean;
  holidayName: string | null;
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
  | "OFFER_LETTER" | "APPOINTMENT_LETTER" | "INTERNSHIP_OFFER" | "JOINING_LETTER"
  | "CONFIRMATION_LETTER" | "PROBATION_EXTENSION"
  | "INCREMENT_LETTER" | "PROMOTION_LETTER" | "TRANSFER_LETTER" | "BONUS_LETTER" | "APPRECIATION_LETTER"
  | "SALARY_CERTIFICATE" | "EMPLOYMENT_VERIFICATION" | "NOC"
  | "WARNING_LETTER" | "SHOW_CAUSE_NOTICE" | "PIP_LETTER" | "TERMINATION_LETTER"
  | "RESIGNATION_ACCEPTANCE" | "RELIEVING_LETTER" | "EXPERIENCE_LETTER" | "NO_DUES_CERTIFICATE"
  | "FNF_STATEMENT" | "INTERNSHIP_CERTIFICATE"
  | "CUSTOM";

export type LetterDateStyle = "LONG" | "SHORT" | "NUMERIC";

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
  /** A letterpad image has been uploaded. The bytes are fetched separately, never inlined here. */
  hasBackground: boolean;
  /** Print letters on that image rather than on the composed header and footer. */
  useBackground: boolean;
  backgroundName: string | null;
  updatedAt: string;
  /** The company's legal identity, printed in the footer of every page. */
  cin?: string | null;
  gstin?: string | null;
  website?: string | null;
  email?: string | null;
  dateStyle?: LetterDateStyle;
  /** Standard terms every letter quotes. */
  probationDays?: number | null;
  noticeProbation?: string | null;
  noticePeriod?: string | null;
  workingDays?: string | null;
  workingHours?: string | null;
  payDay?: string | null;
  jurisdiction?: string | null;
  /**
   * PD-69 — page two onwards and the writing area. The continuation sheet came from page 2 of the
   * uploaded file, was uploaded on its own, or was made from page one with the header cleared.
   */
  continuationSource?: "PAGE2" | "UPLOADED" | "DERIVED" | null;
  /** CONTINUATION: the continuation sheet; SAME: the full letterpad on every page. */
  laterPages?: "CONTINUATION" | "SAME";
  /** Where the letter is written, in millimetres on A4. */
  firstTopMm?: number;
  firstBottomMm?: number;
  laterTopMm?: number;
  laterBottomMm?: number;
  sideMm?: number;
}
export type LetterheadInput = Partial<
  Omit<Letterhead, "updatedAt" | "hasBackground" | "backgroundName" | "continuationSource">
> & { remeasure?: boolean };
export interface MergeField {
  key: string;
  label: string;
}
/** An issued letter's email form defaults and every time it has been sent (PD-64). */
export interface DocumentEmailState {
  to: string | null;
  subject: string;
  message: string;
  sent: { id: string; to: string; subject: string; sentAt: string; delivered: boolean; error: string | null }[];
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
  /** The letter as edited by hand; issued exactly as written. */
  body?: string;
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
  questions?: ReviewQuestion[];
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
  /** PD-66: the cycle's questions (empty for an older free-text cycle) and both sides' answers. */
  questions?: ReviewQuestion[];
  selfAnswers?: Record<string, ReviewAnswer>;
  managerAnswers?: Record<string, ReviewAnswer>;
  newTitle?: string | null;
  effectiveDate?: string | null;
}

/** One question a review cycle asks: a 1–5 rating or a written answer, of the employee, the manager or both. */
export interface ReviewQuestion {
  id: string;
  text: string;
  kind: "RATING" | "TEXT";
  audience: "SELF" | "MANAGER" | "BOTH";
}
export interface ReviewAnswer {
  rating?: number | null;
  text?: string | null;
}

export interface CreateCycleInput {
  name: string;
  periodStart: string;
  periodEnd: string;
  questions?: ReviewQuestion[];
}
export interface SelfAssessmentInput {
  selfAssessment: string;
  submit: boolean;
  answers?: Record<string, ReviewAnswer>;
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
  answers?: Record<string, ReviewAnswer>;
  newTitle?: string;
  effectiveDate?: string;
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
  /** Hours of work the shift expects, in minutes (breaks excluded). */
  workMinutes?: number;
}
export interface ShiftInput {
  name: string;
  startTime: string;
  endTime: string;
  color?: string;
  workMinutes?: number;
}
/** The company's standard day and absence rule, set on the Shifts page (V70). */
export interface WorkDayPolicy {
  workDayMinutes: number;
  workDayStart: string;
  absentGraceMinutes: number;
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
  /** Only on the answer to a create: whether the new admin's welcome email went out. */
  welcomeEmailSent?: boolean | null;
  /** Only on the answer to a create, and only when the email did not go out: pass it on by hand. */
  temporaryPassword?: string | null;
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
  /** Optional — blank lets Orbit generate a temporary password. */
  password?: string;
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
  /** HRA or LTA when this earning is that allowance for income tax. */
  taxTag?: "HRA" | "LTA" | null;
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
  finance: {
    currency: string;
    pending: number;
    awaitingReimbursement: number;
    reimbursedThisYear: number;
    byCategory: Slice[];
  };
}


export type LeaveTypeT = "VACATION" | "SICK" | "PERSONAL" | "UNPAID" | "COMP_OFF";
export type LeaveAccrualT = "ANNUAL" | "MONTHLY";
export type CompOffStatusT = "PENDING" | "APPROVED" | "REJECTED" | "CONSUMED";
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

/**
 * A slice of a list that grows forever, and where to continue from.
 *
 * <p>Mirrors the backend's CursorPage. A null `nextCursor` means the end — a full page is not a
 * reliable signal, since the last page can be exactly full.
 */
export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}

export interface LeaveBalance {
  allowanceDays: number;
  usedDays: number;
  remainingDays: number;
  pendingDays: number;
}

/** One company rule for one leave type. */
export interface LeavePolicy {
  type: LeaveTypeT;
  paid: boolean;
  accrual: LeaveAccrualT;
  daysPerYear: number;
  carryForwardCap: number;
  compOffExpiryDays: number;
}

/**
 * A balance for one leave type, with the working shown.
 *
 * `availableDays` already has pending requests subtracted — two requests made before either is
 * decided must not both look affordable.
 */
export interface LeaveTypeBalance {
  type: LeaveTypeT;
  paid: boolean;
  accrual: LeaveAccrualT;
  entitlementPerYear: number;
  earnedThisYear: number;
  carriedForward: number;
  usedDays: number;
  pendingDays: number;
  availableDays: number;
}

/** A day worked that was not owed, earning a day off later. */
export interface CompOffCredit {
  id: string;
  employeeId: string;
  employeeName: string;
  workedOn: string;
  reason: string | null;
  status: CompOffStatusT;
  expiresOn: string | null;
  /** False for an approved credit past its expiry — the screen must not offer it. */
  spendable: boolean;
  createdAt: string;
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
  /** An exit asked for and waiting for an admin (PD-65). Null when there is none. */
  requestedLastDay?: string | null;
  requestedReason?: string | null;
  requestedByName?: string | null;
  requestedAt?: string | null;
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




export type SprintStatusT = "PLANNED" | "ACTIVE" | "COMPLETED";


/** The board view: the active sprint (null if none) and the tasks currently on the board. */

export type TicketStatusT = "OPEN" | "PENDING" | "RESOLVED" | "CLOSED";


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
/** A file in a company documents folder (policy PDF, form, spreadsheet). */
export interface CompanyFile {
  id: string;
  spaceId: string;
  title: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedByName: string | null;
  createdAt: string;
}

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
  // Company documents
  spaceCount: number;
  pageCount: number;
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
  /** Working today, not checked in yet, still inside shift start + grace. */
  unmarkedToday: number;
  /** No check-in by shift start + grace, and no approved leave. */
  absentToday?: number;
  absentees?: { employeeName: string; jobTitle: string | null; reason: string | null }[];
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
  /**
   * Statutory contributions, or null when the company does not have statutory payroll switched on or
   * this person is not enrolled. Null rather than zeroes: "PF: 0" reads as an error to the person
   * holding the payslip; an absent section reads as "not applicable", which is the truth.
   */
  statutory: PayslipStatutory | null;
  /** Income tax (TDS) withheld this month; null when the company does not withhold it here. */
  incomeTax?: number | null;
  /** From a finalised month — will never change. */
  finalized?: boolean;
}

/**
 * The employer's side of statutory contributions.
 *
 * <p>`employeePf` also appears as a deduction line and is already inside `net` — it is repeated here
 * so the statutory block can be read on its own.
 */
export interface PayslipStatutory {
  // Each scheme's fields are null when it does not apply to this person (not enrolled in PF, above
  // the ESI ceiling, a state without professional tax) — "not applicable", never "0".
  /** The wages PF was computed on, after any ceiling. Printed because it answers the most common
   *  payslip question in India: "why is my PF 1,800 when my basic is 50,000?" */
  pfWages: number | null;
  employeePf: number | null;
  employerEps: number | null;
  employerEpf: number | null;
  employerAdminCharges: number | null;
  employerEdli: number | null;
  /** Gross wages ESI was computed on, after loss of pay. */
  esiWages: number | null;
  /** Zero (not null) for a low earner whose own share is waived. */
  employeeEsi: number | null;
  employerEsi: number | null;
  professionalTax: number | null;
  /** Two-letter code of the state the professional tax was computed for. */
  ptState: string | null;
  /** Labour Welfare Fund, only in the state's collection month. */
  lwfEmployee: number | null;
  lwfEmployer: number | null;
  /** PF and ESI employer contributions together. */
  employerTotal: number;
}

/** A payroll month's lock. Once finalised, its payslips are read back as issued and never change. */
export interface PayrollMonthStatus {
  month: string;
  finalized: boolean;
  finalizedAt: string | null;
  employees: number | null;
  totalGross: number | null;
  totalNet: number | null;
  totalEmployer: number | null;
}

/** Income and TDS for one employee this financial year from before Orbit took over. */
export interface TdsOpening {
  employeeId: string;
  financialYear: string;
  /** Last month these figures cover, YYYY-MM. Orbit takes over from the month after. */
  coveredThrough: string;
  income: number;
  tds: number;
  note: string | null;
  /** Employee PF and professional tax in those months (count for Sections 123 and 19). */
  employeePf: number;
  professionalTax: number;
}

/** A statutory return file, with the people who could not be included and why. */
export interface FilingFile {
  filename: string;
  contentType: string;
  content: string;
  skipped: { employeeId: string; name: string; reason: string }[];
}

/** Something that will stop a return being filed. `employeeId` null = a company-level gap. */
export interface FilingIssue {
  employeeId: string | null;
  name: string;
  severity: "ERROR" | "WARNING";
  message: string;
}

/** ESI / professional-tax settings and the registration numbers statutory files carry. */
export interface StatutorySettings {
  /** The vendor's switch. Without it neither ESI nor PT has any effect. */
  statutoryEnabled: boolean;
  esiEnabled: boolean;
  esiEmployeeRate: number;
  esiEmployerRate: number;
  esiWageCeiling: number;
  ptEnabled: boolean;
  lwfEnabled: boolean;
  pfEstablishmentCode: string | null;
  esiEmployerCode: string | null;
  tan: string | null;
  companyPan: string | null;
  ptRegistrationNo: string | null;
}

/** One company's Provident Fund rates, plus whether statutory payroll is switched on at all. */
export interface PfSettings {
  /** The vendor's switch, not the customer's. The screen explains it rather than offering it. */
  enabled: boolean;
  wageCeiling: number;
  restrictToCeiling: boolean;
  employeeRate: number;
  employerRate: number;
  epsRate: number;
  adminChargeRate: number;
  edliRate: number;
}

/**
 * One capability, on or off for one company, and which rule decided it.
 *
 * `source` matters as much as `enabled`: "recruitment is off" is an unanswerable support question
 * without knowing whether that came from an override, an agency, a plan or a default — only one of
 * those four is a mistake.
 */
export interface FeatureState {
  feature: string;
  label: string;
  description: string;
  enabled: boolean;
  source: "COMPANY" | "AGENCY" | "PLAN" | "DEFAULT";
  /** The plan responsible, when source is PLAN. */
  planCode: string | null;
}

/** A named package of features with a price — what a customer buys. */
export interface Plan {
  code: string;
  name: string;
  description: string | null;
  /** Null means "charge the published price list rather than a plan price". */
  pricePerEmployee: number | null;
  sortOrder: number;
  active: boolean;
  features: string[];
}

export type BankFileFormatT = "GENERIC" | "HDFC" | "ICICI" | "AXIS";

/**
 * What the bank file would contain, before downloading it.
 *
 * <p>Carries no account numbers — the preview is for spotting missing details, and the download is
 * the only place unmasked numbers belong.
 */
export interface BankFilePreview {
  format: BankFileFormatT;
  /** How many people are actually in the file. */
  payable: number;
  /** The sum in the file — not the payroll total. They differ exactly when somebody is excluded. */
  total: number;
  excluded: { employeeId: string; name: string; reason: string }[];
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
  /** Read by professional tax — Maharashtra's slabs differ by it. */
  gender: "MALE" | "FEMALE" | "OTHER" | null;
}

export interface PayrollRunRow {
  employeeId: string;
  name: string;
  jobTitle: string | null;
  gross: number;
  lopDays: number;
  net: number;
  /** Deducted from this person, already inside `net`. Zero when statutory payroll is off. */
  employeePf: number;
  /** Paid by the company on top — outside both `gross` and `net`. */
  employerContribution: number;
  /** Deducted from this person, already inside `net`. */
  employeeEsi: number;
  /** Deducted from this person, already inside `net`. */
  professionalTax: number;
}
/** A payroll run in flight or finished. `result` only for DONE, `error` only for FAILED. */
export interface PayrollJob {
  jobId: string;
  month: string;
  status: "RUNNING" | "DONE" | "FAILED";
  startedAt: string;
  finishedAt: string | null;
  result: PayrollRun | null;
  error: string | null;
}

export interface PayrollRun {
  month: string;
  currency: string;
  rows: PayrollRunRow[];
  totalGross: number;
  totalNet: number;
  totalLopDays: number;
  employees: number;
  /**
   * Employer statutory contributions for the month. Neither gross nor net — the company pays it on
   * top of salary and the employee never sees it. What a month actually costs is gross + this.
   */
  totalEmployerContribution: number;
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
