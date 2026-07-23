"use client";

import { useId } from "react";
import type { Slice } from "@/lib/types";

/**
 * Small, dependency-free SVG charts (no chart library — a strict CSP and one bundle to keep lean).
 * All are theme-aware: they use a fixed vivid palette that reads on both light and dark, and text
 * uses `currentColor` so it follows the foreground token.
 */

export const PALETTE = ["#8b5cf6", "#22d3ee", "#34d399", "#fbbf24", "#fb7185", "#38bdf8", "#a78bfa", "#94a3b8"];

function fmt(n: number): string {
  return Number.isInteger(n) ? n.toLocaleString() : n.toLocaleString(undefined, { maximumFractionDigits: 1 });
}

/** Donut with a centred total and a legend. Empty (all-zero) data renders a muted ring. */
export function Donut({ data, unit, size = 150 }: { data: Slice[]; unit?: string; size?: number }) {
  const total = data.reduce((s, d) => s + d.value, 0);
  const r = size / 2 - 10;
  const c = 2 * Math.PI * r;
  const cx = size / 2;
  let offset = 0;

  return (
    <div className="flex flex-wrap items-center gap-5">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="shrink-0">
        <circle cx={cx} cy={cx} r={r} fill="none" stroke="currentColor" strokeWidth={14} className="text-fg/10" />
        {total > 0 && data.map((d, i) => {
          if (d.value <= 0) return null;
          const len = (d.value / total) * c;
          const el = (
            <circle key={d.label} cx={cx} cy={cx} r={r} fill="none" stroke={PALETTE[i % PALETTE.length]}
              strokeWidth={14} strokeDasharray={`${len} ${c - len}`} strokeDashoffset={-offset}
              transform={`rotate(-90 ${cx} ${cx})`} strokeLinecap="butt" />
          );
          offset += len;
          return el;
        })}
        <text x={cx} y={cx - 2} textAnchor="middle" className="fill-current text-xl font-semibold" style={{ fontSize: 22 }}>
          {fmt(total)}
        </text>
        {unit && <text x={cx} y={cx + 16} textAnchor="middle" className="fill-current text-fg/40" style={{ fontSize: 11 }}>{unit}</text>}
      </svg>
      <ul className="space-y-1 text-sm">
        {data.map((d, i) => (
          <li key={d.label} className="flex items-center gap-2">
            <span className="inline-block h-2.5 w-2.5 shrink-0 rounded-sm" style={{ background: PALETTE[i % PALETTE.length] }} />
            <span className="text-fg/60">{d.label}</span>
            <span className="ml-auto pl-3 font-medium tabular-nums">{fmt(d.value)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Horizontal bars — good for categories, ratings, departments. Bars scale to the largest value. */
export function BarList({ data, color = PALETTE[0], unit }: { data: Slice[]; color?: string; unit?: string }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  return (
    <div className="space-y-2">
      {data.map((d) => (
        <div key={d.label} className="flex items-center gap-3 text-sm">
          <span className="w-28 shrink-0 truncate text-fg/60">{d.label}</span>
          <div className="h-5 flex-1 overflow-hidden rounded bg-fg/[0.06]">
            <div className="h-full rounded" style={{ width: `${(d.value / max) * 100}%`, background: color, minWidth: d.value > 0 ? 4 : 0 }} />
          </div>
          <span className="w-14 shrink-0 text-right font-medium tabular-nums text-fg/70">
            {fmt(d.value)}{unit ? <span className="text-fg/40"> {unit}</span> : null}
          </span>
        </div>
      ))}
    </div>
  );
}

/** Vertical bars, e.g. velocity per sprint. */
export function MiniBars({ data, color = PALETTE[0], unit }: { data: Slice[]; color?: string; unit?: string }) {
  if (data.length === 0) return <p className="text-sm text-fg/40">No completed sprints yet.</p>;
  const max = Math.max(1, ...data.map((d) => d.value));
  const avg = data.reduce((s, d) => s + d.value, 0) / data.length;
  return (
    <div>
      <div className="flex h-40 items-end gap-2">
        {data.map((d) => (
          <div key={d.label} className="flex min-w-0 flex-1 flex-col items-center gap-1">
            <span className="text-xs font-medium tabular-nums text-fg/60">{fmt(d.value)}</span>
            <div className="flex w-full items-end justify-center" style={{ height: 110 }}>
              <div className="w-full max-w-[36px] rounded-t" style={{ height: `${(d.value / max) * 100}%`, background: color, minHeight: d.value > 0 ? 3 : 0 }} />
            </div>
            <span className="w-full truncate text-center text-[11px] text-fg/40" title={d.label}>{d.label}</span>
          </div>
        ))}
      </div>
      <p className="mt-2 text-xs text-fg/40">Average {fmt(Math.round(avg * 10) / 10)}{unit ? ` ${unit}` : ""} per sprint</p>
    </div>
  );
}

/** Area/line trend, e.g. headcount over the last 12 months. */
export function TrendLine({ data, color = PALETTE[0], unit }: { data: Slice[]; color?: string; unit?: string }) {
  const gid = useId();
  const w = 520, h = 150, padX = 8, padY = 14;
  if (data.length < 2) return <p className="text-sm text-fg/40">Not enough history yet.</p>;
  const max = Math.max(1, ...data.map((d) => d.value));
  const min = Math.min(...data.map((d) => d.value));
  const span = Math.max(1, max - min);
  const x = (i: number) => padX + (i * (w - 2 * padX)) / (data.length - 1);
  const y = (v: number) => padY + (1 - (v - min) / span) * (h - 2 * padY);
  const line = data.map((d, i) => `${i === 0 ? "M" : "L"} ${x(i).toFixed(1)} ${y(d.value).toFixed(1)}`).join(" ");
  const area = `${line} L ${x(data.length - 1).toFixed(1)} ${h - padY} L ${x(0).toFixed(1)} ${h - padY} Z`;
  const last = data[data.length - 1];

  return (
    <div>
      <svg width="100%" viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" className="w-full">
        <defs>
          <linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.28" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={area} fill={`url(#${gid})`} />
        <path d={line} fill="none" stroke={color} strokeWidth={2.5} strokeLinejoin="round" />
        {data.map((d, i) => <circle key={i} cx={x(i)} cy={y(d.value)} r={2.5} fill={color} />)}
      </svg>
      <div className="mt-1 flex justify-between text-[11px] text-fg/40">
        <span>{data[0].label}</span>
        <span className="font-medium text-fg/60">now: {fmt(last.value)}{unit ? ` ${unit}` : ""}</span>
        <span>{last.label}</span>
      </div>
    </div>
  );
}
