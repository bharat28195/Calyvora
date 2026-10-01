import type { TicketStatus, TicketPriority, TicketCategory } from "@/lib/types";

/** Shared helpdesk display maps, used by the list and thread views. */
export const STATUS_TONE: Record<TicketStatus, string> = {
  OPEN: "bg-sky-500/15 text-sky-400",
  IN_PROGRESS: "bg-amber-500/15 text-amber-300",
  RESOLVED: "bg-emerald-500/15 text-emerald-400",
  CLOSED: "bg-fg/10 text-fg/50",
};
export const STATUS_LABEL: Record<TicketStatus, string> = {
  OPEN: "Open", IN_PROGRESS: "In progress", RESOLVED: "Resolved", CLOSED: "Closed",
};
export const PRIORITY_TONE: Record<TicketPriority, string> = {
  LOW: "text-fg/40", MEDIUM: "text-sky-400", HIGH: "text-amber-400", URGENT: "text-red-400",
};
export const CATEGORIES: TicketCategory[] = ["HR", "PAYROLL", "IT", "FACILITIES", "OTHER"];
export const CATEGORY_LABEL: Record<TicketCategory, string> = {
  HR: "HR", PAYROLL: "Payroll", IT: "IT", FACILITIES: "Facilities", OTHER: "Other",
};
