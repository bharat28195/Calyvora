"use client";

import type { ExpenseCategory, ExpenseStatus } from "@/lib/types";

/** Shared labels and chips for expense claims, used by both the Me view and the approvals queue. */

export const CATEGORY_LABEL: Record<ExpenseCategory, string> = {
  TRAVEL: "Travel",
  ACCOMMODATION: "Accommodation",
  MEALS: "Meals",
  SUPPLIES: "Supplies",
  TRAINING: "Training",
  OTHER: "Other",
};

export const STATUS_STYLE: Record<ExpenseStatus, string> = {
  SUBMITTED: "bg-amber-500/15 text-amber-400",
  APPROVED: "bg-sky-500/15 text-sky-400",
  REJECTED: "bg-red-500/15 text-red-400",
  REIMBURSED: "bg-emerald-500/15 text-emerald-400",
};

export const STATUS_LABEL: Record<ExpenseStatus, string> = {
  SUBMITTED: "Awaiting approval",
  APPROVED: "Approved — awaiting payment",
  REJECTED: "Declined",
  REIMBURSED: "Reimbursed",
};

export function StatusChip({ status }: { status: ExpenseStatus }) {
  return (
    <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs ${STATUS_STYLE[status]}`}>
      {STATUS_LABEL[status]}
    </span>
  );
}

/** Money with thousands separators — amounts are read at a glance, not parsed. */
export function money(amount: number, currency: string): string {
  return `${currency} ${amount.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}
