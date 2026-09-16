"use client";

import { useMemo } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * The month grid, used by every calendar in Orbit.
 *
 * <p>One component so that attendance, holidays and time off are the same object to the person
 * reading them. Before this each screen drew its own: a dot grid here, a tinted grid there, a list
 * of cards somewhere else, three different ideas of where a week starts. Somebody looking at their
 * month and then at the company's had to work out twice what they were looking at.
 *
 * <p>The design is the one people already know from the calendar on their phone: a day is a cell,
 * what happened on it is a coloured bar under the number, and tapping a day shows the detail
 * underneath rather than in a popup. Bars rather than dots because a day usually has more than one
 * thing in it — four people on leave and one absent is two bars, and the shape of the month is
 * readable without reading a single number.
 *
 * <p>Deliberately dumb: it knows about dates, not about attendance. Each caller decides what a bar
 * means and what the detail panel says.
 */

/** One coloured bar inside a day cell. {@code color} is a Tailwind background class. */
export interface CalendarBar {
    color: string;
    label: string;
}

export interface CalendarDay {
    /** ISO yyyy-mm-dd. */
    date: string;
    bars: CalendarBar[];
    /** Optional Tailwind background class for the whole cell — a holiday, a weekend. */
    tint?: string;
    /** Tooltip; also read out to screen readers as the cell's description. */
    title?: string;
}

const WEEKDAYS = ["M", "T", "W", "T", "F", "S", "S"];

/** Every date in the grid: the month, padded to whole weeks, Monday first. */
function gridFor(month: string): { date: string; inMonth: boolean }[] {
    const [y, m] = month.split("-").map(Number);
    const first = new Date(y, m - 1, 1);
    const leading = (first.getDay() + 6) % 7;   // Monday-first
    const start = new Date(y, m - 1, 1 - leading);
    // Six weeks always. A grid that changes height as you page through the year makes the whole
    // screen jump, and the trailing days are greyed anyway.
    return Array.from({ length: 42 }, (_, i) => {
        const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
        return {
            date: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`,
            inMonth: d.getMonth() === m - 1,
        };
    });
}

export function shiftMonth(month: string, by: number): string {
    const [y, m] = month.split("-").map(Number);
    const d = new Date(y, m - 1 + by, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

export function monthLabel(month: string): string {
    const [y, m] = month.split("-").map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString(undefined, { month: "long", year: "numeric" });
}

/** "1 WED" — the heading over the detail panel, as on a phone calendar. */
export function dayLabel(date: string): { day: string; weekday: string } {
    const d = new Date(`${date}T00:00:00`);
    return {
        day: String(d.getDate()),
        weekday: d.toLocaleDateString(undefined, { weekday: "short" }).toUpperCase(),
    };
}

export function MonthCalendar({
    month,
    onMonthChange,
    days,
    selected,
    onSelect,
    legend,
    compact = false,
    className,
}: {
    month: string;
    onMonthChange?: (month: string) => void;
    days: CalendarDay[];
    selected?: string | null;
    onSelect?: (date: string) => void;
    legend?: CalendarBar[];
    /** Small enough for a dashboard card: no bars, a tinted cell instead. */
    compact?: boolean;
    className?: string;
}) {
    const byDate = useMemo(() => new Map(days.map((d) => [d.date, d])), [days]);
    const cells = useMemo(() => gridFor(month), [month]);
    const today = new Date().toLocaleDateString("sv");   // yyyy-mm-dd, local

    return (
        <div className={className}>
            {onMonthChange && (
                <div className="mb-3 flex items-center justify-between">
                    <button
                        type="button"
                        onClick={() => onMonthChange(shiftMonth(month, -1))}
                        aria-label="Previous month"
                        className="rounded-md p-1.5 text-fg/50 hover:bg-fg/5 hover:text-fg"
                    >
                        <ChevronLeft className="h-4 w-4" />
                    </button>
                    <p className={cn("font-semibold tracking-tight", compact ? "text-sm" : "text-base")}>
                        {monthLabel(month)}
                    </p>
                    <button
                        type="button"
                        onClick={() => onMonthChange(shiftMonth(month, 1))}
                        aria-label="Next month"
                        className="rounded-md p-1.5 text-fg/50 hover:bg-fg/5 hover:text-fg"
                    >
                        <ChevronRight className="h-4 w-4" />
                    </button>
                </div>
            )}

            <div className="grid grid-cols-7 gap-1">
                {WEEKDAYS.map((d, i) => (
                    <div
                        key={i}
                        className={cn(
                            "pb-1.5 text-center text-[11px] font-medium",
                            // Sunday reads as the odd one out, the way a printed calendar has it.
                            i === 6 ? "text-red-400/70" : "text-fg/30",
                        )}
                    >
                        {d}
                    </div>
                ))}

                {cells.map(({ date, inMonth }) => {
                    const day = byDate.get(date);
                    const isToday = date === today;
                    const isSelected = selected === date;
                    const sunday = new Date(`${date}T00:00:00`).getDay() === 0;
                    const clickable = !!onSelect && inMonth;

                    return (
                        <button
                            key={date}
                            type="button"
                            disabled={!clickable}
                            onClick={clickable ? () => onSelect(date) : undefined}
                            title={day?.title}
                            aria-label={day?.title ?? date}
                            aria-current={isToday ? "date" : undefined}
                            aria-pressed={onSelect ? isSelected : undefined}
                            className={cn(
                                "flex flex-col items-center rounded-lg border border-transparent px-1 pt-1.5 text-center transition-colors",
                                compact ? "h-11 gap-1" : "h-[62px] gap-1.5",
                                inMonth ? day?.tint ?? "bg-fg/[0.03]" : "opacity-30",
                                clickable && "hover:border-fg/15 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet",
                                // Today is outlined, the selection is filled. Two different questions
                                // — "where am I" and "what am I reading" — so two different marks.
                                isToday && !isSelected && "border-fg/30",
                                isSelected && "border-violet bg-violet/10",
                            )}
                        >
                            <span
                                className={cn(
                                    "text-xs tabular-nums",
                                    isToday || isSelected ? "font-semibold text-fg" : sunday ? "text-red-400/80" : "text-fg/70",
                                )}
                            >
                                {Number(date.slice(-2))}
                            </span>

                            {compact
                                ? null
                                : (day?.bars ?? []).slice(0, 3).map((bar, i) => (
                                    <span
                                        key={i}
                                        className={cn("h-[3px] w-full rounded-full", bar.color)}
                                        // The bar carries the meaning; without this a screen reader
                                        // gets a day number and nothing else.
                                        aria-hidden="true"
                                    />
                                ))}
                        </button>
                    );
                })}
            </div>

            {legend && legend.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1.5">
                    {legend.map((l) => (
                        <span key={l.label} className="flex items-center gap-1.5 text-[11px] text-fg/50">
                            <span className={cn("h-[3px] w-4 rounded-full", l.color)} />
                            {l.label}
                        </span>
                    ))}
                </div>
            )}
        </div>
    );
}

/** The "1 WED" heading that sits over a selected day's detail. */
export function DayHeading({ date, children }: { date: string; children?: React.ReactNode }) {
    const { day, weekday } = dayLabel(date);
    return (
        <div className="flex items-baseline justify-between gap-3">
            <p className="flex items-baseline gap-1.5">
                <span className="text-2xl font-semibold tracking-tight">{day}</span>
                <span className="text-xs font-medium uppercase tracking-wide text-fg/50">{weekday}</span>
            </p>
            {children}
        </div>
    );
}
