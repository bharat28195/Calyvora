"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { CalendarDays, PartyPopper, Palmtree, ArrowRight } from "lucide-react";
import { api } from "@/lib/api";
import type { Holiday, LeaveRequest } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";

interface Item {
  key: string;
  kind: "holiday" | "leave";
  title: string;
  subtitle: string;
  date: string;
  daysAway: number;
}

/**
 * "What's coming up" — the next holidays and your own approved/pending leave, merged into one list.
 * Answers the question people actually open a dashboard for: when am I next off?
 */
export function WhatsComingUp() {
  const [items, setItems] = useState<Item[] | null>(null);

  useEffect(() => {
    Promise.all([api.upcomingHolidays(4), api.myLeave()])
      .then(([holidays, leave]) => setItems(merge(holidays, leave)))
      .catch(() => setItems([]));
  }, []);

  return (
    <Card>
      <div className="flex items-center justify-between">
        <CardTitle>What&apos;s coming up</CardTitle>
        <Link href="/people/holidays" className="text-xs text-fg/40 hover:text-fg">
          Calendar <ArrowRight className="inline h-3 w-3" />
        </Link>
      </div>

      {items === null ? (
        <div className="mt-4 space-y-2">
          {[0, 1, 2].map((i) => <div key={i} className="h-10 animate-pulse rounded bg-fg/5" />)}
        </div>
      ) : items.length === 0 ? (
        <p className="mt-4 text-sm text-fg/40">Nothing scheduled. Time to book a holiday?</p>
      ) : (
        <ul className="mt-3 flex flex-col divide-y divide-fg/5">
          {items.map((i) => (
            <li key={i.key} className="flex items-center gap-3 py-2.5">
              <span className="shrink-0">
                {i.kind === "holiday"
                  ? <PartyPopper className="h-4 w-4 text-violet" />
                  : <Palmtree className="h-4 w-4 text-amber-400" />}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm">{i.title}</span>
                <span className="block truncate text-xs text-fg/40">{i.subtitle}</span>
              </span>
              <span className="shrink-0 text-xs text-fg/40">
                <CalendarDays className="mr-1 inline h-3 w-3" />
                {i.daysAway === 0 ? "today" : i.daysAway === 1 ? "tomorrow" : `in ${i.daysAway}d`}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

/** Merge holidays and the viewer's own leave into one date-ordered list of what's next. */
function merge(holidays: Holiday[], leave: LeaveRequest[]): Item[] {
  const today = new Date().toISOString().slice(0, 10);
  const items: Item[] = holidays.map((h) => ({
    key: `h-${h.id}`,
    kind: "holiday" as const,
    title: h.name,
    subtitle: h.optional ? "Optional holiday" : "Company holiday",
    date: h.date,
    daysAway: h.daysAway,
  }));

  for (const l of leave) {
    if (l.endDate < today || l.status === "REJECTED" || l.status === "CANCELLED") continue;
    items.push({
      key: `l-${l.id}`,
      kind: "leave",
      title: `Your ${l.type.toLowerCase()} leave`,
      subtitle: `${l.days}d · ${l.status.toLowerCase()}`,
      date: l.startDate,
      daysAway: Math.round(
        (new Date(`${l.startDate}T00:00:00`).getTime() - new Date(`${today}T00:00:00`).getTime()) / 86_400_000,
      ),
    });
  }

  return items.sort((a, b) => a.date.localeCompare(b.date)).slice(0, 6);
}
