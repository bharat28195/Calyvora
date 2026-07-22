"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, Plus, Trash2, PartyPopper, Sparkles } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { Holiday } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/** The company holiday calendar. Everyone reads it; Owner/Admin edits it. */
export default function HolidaysPage() {
  const { me } = useSession();
  const isAdmin = me?.user.role === "OWNER" || me?.user.role === "ADMIN";

  const [year, setYear] = useState(() => new Date().getFullYear());
  const [holidays, setHolidays] = useState<Holiday[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ name: "", date: "", optional: false, note: "" });

  const load = useCallback(() => {
    setHolidays(null);
    api.holidays(year)
      .then(setHolidays)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load the calendar"));
  }, [year]);

  useEffect(() => load(), [load]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    if (!form.name.trim() || !form.date) return;
    setAdding(true);
    setError(null);
    try {
      await api.createHoliday({
        name: form.name.trim(), date: form.date, optional: form.optional, note: form.note.trim() || undefined,
      });
      setForm({ name: "", date: "", optional: false, note: "" });
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to add the holiday");
    } finally {
      setAdding(false);
    }
  }

  const { upcoming, past } = useMemo(() => {
    const list = holidays ?? [];
    return { upcoming: list.filter((h) => h.daysAway >= 0), past: list.filter((h) => h.daysAway < 0) };
  }, [holidays]);

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Holidays</h1>
          <p className="mt-1 text-fg/50">When the office is closed. Holidays fill everyone&apos;s attendance automatically.</p>
        </div>
        <select
          value={year}
          onChange={(e) => setYear(Number(e.target.value))}
          className="h-9 rounded-lg border border-fg/15 bg-fg/5 px-2 text-sm text-fg"
        >
          {[year - 1, year, year + 1].map((y) => <option key={y} value={y} className="bg-surface">{y}</option>)}
        </select>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {isAdmin && (
        <Card className="mt-6">
          <form onSubmit={add} className="grid gap-3 sm:grid-cols-[1fr_10rem_auto] sm:items-end">
            <Field label="Holiday" htmlFor="h-name">
              <Input id="h-name" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="e.g. Founders' Day" />
            </Field>
            <Field label="Date" htmlFor="h-date">
              <Input id="h-date" type="date" value={form.date}
                onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))} />
            </Field>
            <Button type="submit" disabled={adding}>
              {adding ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />} Add
            </Button>
          </form>
          <label className="mt-3 inline-flex items-center gap-2 text-sm text-fg/60">
            <input type="checkbox" checked={form.optional}
              onChange={(e) => setForm((f) => ({ ...f, optional: e.target.checked }))}
              className="h-4 w-4 rounded border-fg/20 bg-fg/5" />
            Optional — offered, but the office stays open
          </label>
        </Card>
      )}

      {holidays === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : holidays.length === 0 ? (
        <Card className="mt-8 text-center">
          <PartyPopper className="mx-auto h-8 w-8 text-fg/20" />
          <p className="mt-3 text-sm text-fg/50">No holidays in {year} yet.</p>
          {isAdmin && (
            <Button className="mt-4" variant="secondary" onClick={() => api.seedDefaultHolidays().then(load)}>
              <Sparkles className="h-4 w-4" /> Add a starter calendar
            </Button>
          )}
        </Card>
      ) : (
        <>
          <HolidayList title="Upcoming" items={upcoming} isAdmin={isAdmin} onDeleted={load} />
          <HolidayList title="Earlier this year" items={past} isAdmin={isAdmin} onDeleted={load} muted />
        </>
      )}
    </div>
  );
}

function HolidayList({
  title, items, isAdmin, onDeleted, muted,
}: { title: string; items: Holiday[]; isAdmin: boolean; onDeleted: () => void; muted?: boolean }) {
  if (items.length === 0) return null;
  return (
    <div className="mt-8">
      <h2 className="text-sm font-medium uppercase tracking-wide text-fg/40">{title}</h2>
      <div className={`mt-3 flex flex-col gap-2 ${muted ? "opacity-60" : ""}`}>
        {items.map((h) => (
          <Card key={h.id} className="flex items-center justify-between gap-3 p-4">
            <div className="flex min-w-0 items-center gap-3">
              <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-violet/10 text-center">
                <span className="text-xs font-semibold leading-none text-violet">{h.date.slice(8)}</span>
                <span className="text-[10px] leading-none text-violet/60">{h.weekday}</span>
              </span>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">
                  {h.name}
                  {h.optional && <span className="ml-2 rounded-full bg-fg/10 px-1.5 py-0.5 text-[10px] text-fg/50">optional</span>}
                </p>
                <p className="truncate text-xs text-fg/40">
                  {h.note ?? new Date(`${h.date}T00:00:00`).toLocaleDateString(undefined, { month: "long", day: "numeric" })}
                </p>
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-3">
              {h.daysAway >= 0 && (
                <span className="text-xs text-fg/40">
                  {h.daysAway === 0 ? "today" : `in ${h.daysAway} day${h.daysAway === 1 ? "" : "s"}`}
                </span>
              )}
              {isAdmin && (
                <button onClick={() => api.deleteHoliday(h.id).then(onDeleted)} aria-label={`Delete ${h.name}`}
                  className="rounded-md p-1.5 text-red-400/70 hover:bg-fg/5 hover:text-red-300">
                  <Trash2 className="h-4 w-4" />
                </button>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
