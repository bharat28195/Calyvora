import type { Me, Role } from "@/lib/types";

/**
 * What the signed-in person may do (PD-54). Mirrors com.calyvora.access.Permission. The server is
 * what enforces; this decides what the menu and the buttons offer, so nobody is shown a screen that
 * would only answer 403.
 */
export type PermissionKey =
  | "MEMBERS_MANAGE" | "COMPANY_SETTINGS" | "BILLING_MANAGE" | "FEED_MODERATE"
  | "PEOPLE_MANAGE" | "ORG_VIEW_ALL" | "PEOPLE_PRIVATE_VIEW" | "LEAVE_POLICY_MANAGE" | "EXITS_MANAGE" | "EXITS_APPROVE"
  | "LEAVE_APPROVE" | "ATTENDANCE_MANAGE" | "SHIFTS_MANAGE"
  | "SALARY_VIEW" | "PAYROLL_MANAGE" | "TAX_MANAGE" | "EXPENSES_APPROVE" | "EXPENSES_REIMBURSE"
  | "RECRUITMENT_MANAGE" | "PERFORMANCE_MANAGE" | "DOCUMENTS_ISSUE" | "DOCUMENTS_PUBLISH"
  | "HELPDESK_MANAGE" | "INSIGHTS_VIEW";

export type PermissionScope = "COMPANY" | "TEAM";
export type Grants = Partial<Record<PermissionKey, PermissionScope>>;

const ALL: PermissionKey[] = [
  "MEMBERS_MANAGE", "COMPANY_SETTINGS", "BILLING_MANAGE", "FEED_MODERATE",
  "PEOPLE_MANAGE", "ORG_VIEW_ALL", "PEOPLE_PRIVATE_VIEW", "LEAVE_POLICY_MANAGE", "EXITS_MANAGE", "EXITS_APPROVE",
  "LEAVE_APPROVE", "ATTENDANCE_MANAGE", "SHIFTS_MANAGE",
  "SALARY_VIEW", "PAYROLL_MANAGE", "TAX_MANAGE", "EXPENSES_APPROVE", "EXPENSES_REIMBURSE",
  "RECRUITMENT_MANAGE", "PERFORMANCE_MANAGE", "DOCUMENTS_ISSUE", "DOCUMENTS_PUBLISH",
  "HELPDESK_MANAGE", "INSIGHTS_VIEW",
];
const ADMIN_ONLY: PermissionKey[] = ["MEMBERS_MANAGE", "COMPANY_SETTINGS", "BILLING_MANAGE", "FEED_MODERATE"];
const TEAM_APPROVALS: PermissionKey[] = ["LEAVE_APPROVE", "EXPENSES_APPROVE", "ATTENDANCE_MANAGE"];

/** The built-in defaults for a role — mirrors BuiltinRole.defaults() on the server. */
export function builtinGrants(role: Role | undefined | null): Grants {
  const out: Grants = {};
  if (role === "OWNER" || role === "ADMIN") ALL.forEach((p) => { out[p] = "COMPANY"; });
  else if (role === "HR") ALL.filter((p) => !ADMIN_ONLY.includes(p)).forEach((p) => { out[p] = "COMPANY"; });
  else if (role === "MANAGER" || role === "MEMBER") TEAM_APPROVALS.forEach((p) => { out[p] = "TEAM"; });
  return out;
}

/** The person's grants: from /me when present, else the defaults for their role. */
export function grantsOf(me: Me | null | undefined): Grants {
  if (!me) return {};
  return (me.permissions as Grants | undefined) ?? builtinGrants(me.user.role);
}

/** Holds the permission at any scope. */
export function can(me: Me | null | undefined, permission: PermissionKey): boolean {
  return grantsOf(me)[permission] !== undefined;
}

/** Holds it for the whole company, not only their own team. */
export function canCompanyWide(me: Me | null | undefined, permission: PermissionKey): boolean {
  return grantsOf(me)[permission] === "COMPANY";
}
