"use client";

import { useMemo, useState } from "react";
import { Loader2, Search } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AttendanceStatus } from "@/lib/types";
import { Modal } from "@/components/ui/modal";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";
import { STATUS } from "@/components/attendance/self";

const SELECT = "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";
const STATUSES: AttendanceStatus[] = ["PRESENT", "WORK_FROM_HOME", "HALF_DAY", "ABSENT", "ON_LEAVE"];

/** Every date from `from` to `to`, optionally without Saturdays and Sundays. */
function datesBetween(from: string, to: string, skipWeekends: boolean): string[] {
  const out: string[] = [];
  const d = new Date(from + "T00:00:00");
  const end = new Date(to + "T00:00:00");
  while (d <= end && out.length < 62) {
    const dow = d.getDay();
    if (!skipWeekends || (dow !== 0 && dow !== 6)) out.push(d.toLocaleDateString("sv"));
    d.setDate(d.getDate() + 1);
  }
  return out;
}

/**
 * A manager or HR sets attendance for several people over several days in one go, applied
 * immediately — they hold the right, so there is no approval step. The reason is required and kept
 * on every day it touches. Days set here are exempt from the automatic absent and half-day rules.
 */
export function CorrectAttendance({ open, onClose, people, preselected, onDone }: {
  open: boolean;
  onClose: () => void;
  people: { id: string; name: string }[];
  preselected?: string[];
  onDone: () => void;
}) {
  const today = new Date().toLocaleDateString("sv");
  const [chosen, setChosen] = useState<Set<string>>(() => new Set(preselected ?? []));
  const [query, setQuery] = useState("");
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(today);
  const [skipWeekends, setSkipWeekends] = useState(true);
  const [status, setStatus] = useState<AttendanceStatus>("PRESENT");
  const [checkIn, setCheckIn] = useState("");
  const [checkOut, setCheckOut] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dates = useMemo(() => (from && to && from <= to ? datesBetween(from, to, skipWeekends) : []), [from, to, skipWeekends]);
  const shown = people.filter((p) => !query || p.name.toLowerCase().includes(query.toLowerCase()));
  const timed = status === "PRESENT" || status === "WORK_FROM_HOME" || status === "HALF_DAY";

  function toggle(id: string) {
    setChosen((cur) => {
      const next = new Set(cur);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (chosen.size === 0) { setError("Pick at least one person."); return; }
    if (dates.length === 0) { setError("Pick a date range with at least one working day."); return; }
    if (!reason.trim()) { setError("Give a reason for the correction."); return; }
    setBusy(true);
    try {
      await api.correctAttendance({
        employeeIds: [...chosen], dates, status, reason: reason.trim(),
        checkIn: timed && checkIn ? checkIn : undefined,
        checkOut: timed && checkOut ? checkOut : undefined,
      });
      onDone();
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't save the correction");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Correct attendance">
      <form onSubmit={submit} className="flex flex-col gap-4">
        {error && <Alert tone="error">{error}</Alert>}

        <div>
          <div className="flex items-center justify-between text-sm">
            <span className="font-medium">People <span className="text-fg/40">({chosen.size} selected)</span></span>
            <button type="button" className="text-xs text-violet hover:underline"
              onClick={() => setChosen(chosen.size === shown.length ? new Set() : new Set(shown.map((p) => p.id)))}>
              {chosen.size === shown.length && shown.length > 0 ? "Clear" : "Select all"}
            </button>
          </div>
          {people.length > 8 && (
            <div className="relative mt-2">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg/30" />
              <Input className="pl-9" placeholder="Find someone…" value={query} onChange={(e) => setQuery(e.target.value)} />
            </div>
          )}
          <div className="mt-2 max-h-44 overflow-y-auto rounded-lg border border-fg/10">
            {shown.map((p) => (
              <label key={p.id} className="flex cursor-pointer items-center gap-2 px-3 py-2 text-sm hover:bg-fg/[0.03]">
                <input type="checkbox" checked={chosen.has(p.id)} onChange={() => toggle(p.id)} className="accent-violet" />
                {p.name}
              </label>
            ))}
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="From" htmlFor="c-from"><Input id="c-from" type="date" max={today} value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
          <Field label="To" htmlFor="c-to"><Input id="c-to" type="date" max={today} value={to} onChange={(e) => setTo(e.target.value)} /></Field>
        </div>
        <label className="-mt-2 flex items-center gap-2 text-xs text-fg/60">
          <input type="checkbox" checked={skipWeekends} onChange={(e) => setSkipWeekends(e.target.checked)} className="accent-violet" />
          Skip Saturdays and Sundays · {dates.length} day{dates.length === 1 ? "" : "s"}
        </label>

        <Field label="Mark as" htmlFor="c-status">
          <select id="c-status" className={SELECT} value={status} onChange={(e) => setStatus(e.target.value as AttendanceStatus)}>
            {STATUSES.map((s) => <option key={s} value={s} className="bg-surface">{STATUS[s].label}</option>)}
          </select>
        </Field>

        {timed && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Check in (optional)" htmlFor="c-in"><Input id="c-in" type="time" value={checkIn} onChange={(e) => setCheckIn(e.target.value)} /></Field>
            <Field label="Check out (optional)" htmlFor="c-out"><Input id="c-out" type="time" value={checkOut} onChange={(e) => setCheckOut(e.target.value)} /></Field>
          </div>
        )}

        <Field label="Reason (required)" htmlFor="c-reason">
          <Input id="c-reason" required value={reason} onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. client visit — confirmed with the team lead" />
        </Field>

        <div className="flex gap-2">
          <Button type="submit" disabled={busy || chosen.size === 0 || dates.length === 0 || !reason.trim()}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />}
            Apply to {chosen.size * dates.length} day{chosen.size * dates.length === 1 ? "" : "s"}
          </Button>
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
        </div>
      </form>
    </Modal>
  );
}
