"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronsUpDown, Loader2, Search, X } from "lucide-react";
import { api } from "@/lib/api";
import type { Employee, EmployeeOption } from "@/lib/types";
import { cn } from "@/lib/utils";

/**
 * Searchable member picker. `value` is an employee id ("" = unassigned).
 *
 * Two modes, chosen by whether the caller already has a list:
 *
 *  - **Given `employees`**, it filters them in the browser. Right for a screen that needed the roster
 *    for its own reasons anyway — no extra request to fill a dropdown it can already fill.
 *  - **Given none**, it asks the server as you type. Right for everyone else, and the reason this mode
 *    exists: two screens were downloading all thousand employees to populate a dropdown. The fix for
 *    that is not pagination — nobody pages through a dropdown — it is search.
 *
 * In remote mode the label for an already-selected person is resolved once on mount, because the list
 * that used to supply it is no longer in memory.
 */
export function MemberSelect({
  employees,
  value,
  onChange,
  placeholder = "Unassigned",
  allowClear = true,
}: {
  employees?: Employee[];
  value: string;
  onChange: (id: string) => void;
  placeholder?: string;
  allowClear?: boolean;
}) {
  const remote = employees === undefined;
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<EmployeeOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [labels, setLabels] = useState<Record<string, string>>({});
  const ref = useRef<HTMLDivElement>(null);

  const local: EmployeeOption[] = useMemo(
    () => (employees ?? []).map((e) => ({
      id: e.id,
      name: `${e.firstName} ${e.lastName}`.trim(),
      email: e.email,
      jobTitle: e.jobTitle ?? null,
    })),
    [employees],
  );

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, []);

  useEffect(() => { if (!open) setQuery(""); }, [open]);

  // Remote search, debounced. A request per keystroke would put a thousand-person company's database
  // under load the local-filter version never created — the point is fewer rows, not more requests.
  // A stale response is discarded rather than allowed to overwrite a newer one.
  useEffect(() => {
    if (!remote || !open) return;
    let cancelled = false;
    setLoading(true);
    const timer = setTimeout(() => {
      api.searchEmployees(query, 20)
        .then((list) => {
          if (cancelled) return;
          setResults(list);
          setLabels((prev) => {
            const next = { ...prev };
            list.forEach((o) => { next[o.id] = o.name; });
            return next;
          });
        })
        .catch(() => { if (!cancelled) setResults([]); })
        .finally(() => { if (!cancelled) setLoading(false); });
    }, 220);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [remote, open, query]);

  // The selected person's name, when the list that used to carry it is gone. One request, once.
  useEffect(() => {
    if (!remote || !value || labels[value]) return;
    let cancelled = false;
    api.getEmployee(value)
      .then((e) => {
        if (cancelled) return;
        setLabels((prev) => ({ ...prev, [value]: `${e.firstName} ${e.lastName}`.trim() }));
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [remote, value, labels]);

  const options = useMemo(() => {
    if (remote) return results;
    const q = query.trim().toLowerCase();
    if (!q) return local;
    return local.filter((o) =>
      `${o.name} ${o.email} ${o.jobTitle ?? ""}`.toLowerCase().includes(q));
  }, [remote, results, local, query]);

  const selectedLabel = remote
    ? labels[value] ?? (value ? "…" : "")
    : local.find((o) => o.id === value)?.name ?? "";

  const pick = useCallback((id: string) => { onChange(id); setOpen(false); }, [onChange]);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex h-11 w-full items-center justify-between gap-2 rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg hover:bg-fg/10"
      >
        <span className={cn("truncate", !value && "text-fg/40")}>
          {value ? selectedLabel : placeholder}
        </span>
        <span className="flex items-center gap-1">
          {value && allowClear && (
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
            {loading
              ? <Loader2 className="h-4 w-4 shrink-0 animate-spin text-fg/40" />
              : <Search className="h-4 w-4 shrink-0 text-fg/40" />}
            <input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={remote ? "Search by name or email…" : `Search ${local.length} members…`}
              className="h-10 w-full bg-transparent text-sm text-fg placeholder:text-fg/30 focus:outline-none"
            />
          </div>
          <div className="max-h-60 overflow-y-auto p-1">
            {allowClear && (
              <Option label={placeholder} selected={!value} onClick={() => pick("")} muted />
            )}
            {options.length === 0 ? (
              <p className="px-3 py-4 text-center text-sm text-fg/30">
                {loading ? "Searching…" : "No members found."}
              </p>
            ) : (
              options.map((o) => (
                <Option
                  key={o.id}
                  label={o.name}
                  subtitle={o.jobTitle || o.email}
                  selected={o.id === value}
                  onClick={() => pick(o.id)}
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
