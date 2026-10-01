"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AttendanceDaySummary, AttendanceMonthSummary } from "@/lib/types";
import { Alert } from "@/components/ui/alert";
import { MonthCalendar } from "@/components/ui/month-calendar";

/**
 * A group's month as a calendar: one cell per day, a bar per kind of day inside it.
 *
 * <p>The day sheet below it answers "who"; this answers "which day", which is the question somebody
 * actually arrives with — the Tuesday half the team was out, the week nobody marked anything. It
 * reads one endpoint that returns counts, not people, so drawing a month of a thousand-person
 * company costs the same as drawing a month of seven.
 *
 * <p>Scoped by the reporting tree on the server: the whole company for Owner/Admin/HR, their own
 * downline for a lead. Same component either way — only the numbers differ.
 */

/** Present is the baseline, so it is drawn first; the exceptions sit under it where they stand out. */
function barsFor(day: AttendanceDaySummary) {
    const bars: { color: string; label: string }[] = [];
    if (day.present > 0) bars.push({ color: "bg-emerald-500", label: "Present" });
    if (day.onLeave > 0) bars.push({ color: "bg-violet", label: "On leave" });
    if (day.absent > 0) bars.push({ color: "bg-red-500", label: "Absent" });
    // Only when there is nothing else to say: a grey bar beside a green one reads as a problem,
    // and "nobody marked the day" on a day people clearly worked is not one.
    if (bars.length === 0 && day.unmarked > 0) bars.push({ color: "bg-fg/20", label: "Not marked" });
    return bars;
}

function titleFor(day: AttendanceDaySummary) {
    const parts: string[] = [];
    if (day.holiday) parts.push(day.holidayName ?? "Holiday");
    if (day.present > 0) parts.push(`${day.present} in`);
    if (day.onLeave > 0) parts.push(`${day.onLeave} on leave`);
    if (day.absent > 0) parts.push(`${day.absent} absent`);
    if (day.unmarked > 0) parts.push(`${day.unmarked} not marked`);
    return `${day.date}${parts.length ? ` · ${parts.join(" · ")}` : ""}`;
}

export function AttendanceMonthGrid({
    month,
    onMonthChange,
    selected,
    onSelect,
    className,
}: {
    month: string;
    onMonthChange: (month: string) => void;
    /** The day the detail below is showing. */
    selected: string | null;
    onSelect: (date: string) => void;
    className?: string;
}) {
    const [data, setData] = useState<AttendanceMonthSummary | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        setData(null);
        setError(null);
        api.attendanceMonthSummary(month)
            .then((d) => { if (!cancelled) setData(d); })
            .catch((e) => { if (!cancelled) setError(e instanceof ApiError ? e.message : "Failed to load the month"); });
        return () => { cancelled = true; };
    }, [month]);

    const cells = useMemo(
        () =>
            (data?.days ?? []).map((d) => ({
                date: d.date,
                bars: barsFor(d),
                tint: d.holiday ? "bg-amber-400/10" : undefined,
                title: titleFor(d),
            })),
        [data],
    );

    // The legend names only what this month contains, so a quiet month does not carry four
    // labels for three things that never happened.
    const legend = useMemo(() => {
        const seen = new Map<string, { color: string; label: string }>();
        for (const d of data?.days ?? []) for (const b of barsFor(d)) seen.set(b.label, b);
        return [...seen.values()];
    }, [data]);

    return (
        <div className={className}>
            {error && <Alert tone="error" className="mb-3">{error}</Alert>}
            {data === null && !error ? (
                <div className="flex h-[400px] items-center justify-center">
                    <Loader2 className="h-5 w-5 animate-spin text-violet" />
                </div>
            ) : (
                <MonthCalendar
                    month={month}
                    onMonthChange={onMonthChange}
                    days={cells}
                    selected={selected}
                    onSelect={onSelect}
                    legend={legend}
                />
            )}
        </div>
    );
}
