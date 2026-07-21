"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronsUpDown, Search, X } from "lucide-react";
import type { Employee } from "@/lib/types";
import { cn } from "@/lib/utils";

/**
 * Searchable member picker. Replaces the plain <select> assignee dropdowns so a company with hundreds
 * or thousands of members stays usable (founder feedback A1): it lists every member of the company and
 * filters by name/email/title as you type. `value` is an employee id ("" = unassigned).
 */
export function MemberSelect({
  employees,
  value,
  onChange,
  placeholder = "Unassigned",
  allowClear = true,
}: {
  employees: Employee[];
  value: string;
  onChange: (id: string) => void;
  placeholder?: string;
  allowClear?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const ref = useRef<HTMLDivElement>(null);

  const selected = employees.find((e) => e.id === value) || null;

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, []);

  useEffect(() => {
    if (!open) setQuery("");
  }, [open]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return employees;
    return employees.filter((e) =>
      `${e.firstName} ${e.lastName} ${e.email} ${e.jobTitle ?? ""}`.toLowerCase().includes(q),
    );
  }, [employees, query]);

  const name = (e: Employee) => `${e.firstName} ${e.lastName}`.trim();

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex h-11 w-full items-center justify-between gap-2 rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg hover:bg-fg/10"
      >
        <span className={cn("truncate", !selected && "text-fg/40")}>
          {selected ? name(selected) : placeholder}
        </span>
        <span className="flex items-center gap-1">
          {selected && allowClear && (
            <span
              role="button"
              tabIndex={-1}
              onClick={(e) => { e.stopPropagation(); onChange(""); }}
              className="rounded p-0.5 text-fg/40 hover:text-fg"
              aria-label="Clear"
            >
              <X className="h-3.5 w-3.5" />
            </span>
          )}
          <ChevronsUpDown className="h-4 w-4 shrink-0 text-fg/40" />
        </span>
      </button>

      {open && (
        <div className="absolute z-30 mt-1 w-full overflow-hidden rounded-lg border border-fg/10 bg-surface shadow-xl">
          <div className="flex items-center gap-2 border-b border-fg/10 px-3">
            <Search className="h-4 w-4 shrink-0 text-fg/40" />
            <input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={`Search ${employees.length} members…`}
              className="h-10 w-full bg-transparent text-sm text-fg placeholder:text-fg/30 focus:outline-none"
            />
          </div>
          <div className="max-h-60 overflow-y-auto p-1">
            {allowClear && (
              <Option label={placeholder} selected={!value} onClick={() => { onChange(""); setOpen(false); }} muted />
            )}
            {filtered.length === 0 ? (
              <p className="px-3 py-4 text-center text-sm text-fg/30">No members found.</p>
            ) : (
              filtered.map((e) => (
                <Option
                  key={e.id}
                  label={name(e)}
                  subtitle={e.jobTitle || e.email}
                  selected={e.id === value}
                  onClick={() => { onChange(e.id); setOpen(false); }}
                />
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function Option({
  label, subtitle, selected, muted, onClick,
}: { label: string; subtitle?: string | null; selected: boolean; muted?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex w-full items-center justify-between gap-2 rounded-md px-3 py-2 text-left text-sm hover:bg-fg/5",
        muted && "text-fg/50",
      )}
    >
      <span className="min-w-0">
        <span className="block truncate">{label}</span>
        {subtitle && <span className="block truncate text-xs text-fg/40">{subtitle}</span>}
      </span>
      {selected && <Check className="h-4 w-4 shrink-0 text-violet" />}
    </button>
  );
}
