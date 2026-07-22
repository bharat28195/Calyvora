import { CalendarClock, CheckCircle2, XCircle, Target, FileText, Megaphone } from "lucide-react";
import { createElement, type ReactNode } from "react";
import type { NotificationType } from "@/lib/types";

/** One icon per notification type, so the inbox is scannable without reading every line. */
export const NOTIFICATION_ICON: Record<NotificationType, ReactNode> = {
  LEAVE_REQUESTED: createElement(CalendarClock, { className: "h-4 w-4 text-amber-400" }),
  LEAVE_APPROVED: createElement(CheckCircle2, { className: "h-4 w-4 text-emerald-400" }),
  LEAVE_REJECTED: createElement(XCircle, { className: "h-4 w-4 text-red-400" }),
  GOAL_ASSIGNED: createElement(Target, { className: "h-4 w-4 text-violet" }),
  DOCUMENT_ISSUED: createElement(FileText, { className: "h-4 w-4 text-sky-400" }),
  ANNOUNCEMENT: createElement(Megaphone, { className: "h-4 w-4 text-fg/50" }),
};

/** "3m ago" / "2h ago" / "5d ago" — relative time reads better than a timestamp in an inbox. */
export function notificationAge(iso: string): string {
  const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60_000));
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(iso).toLocaleDateString();
}
