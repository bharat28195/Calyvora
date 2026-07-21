"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Search, User, FolderKanban, CircleDot, LifeBuoy, BookOpen, FileText, Loader2 } from "lucide-react";
import { api } from "@/lib/api";
import type { SearchHit, SearchResponse } from "@/lib/types";

const ICON: Record<SearchHit["kind"], React.ReactNode> = {
  person: <User className="h-4 w-4 text-violet" />,
  project: <FolderKanban className="h-4 w-4 text-aqua" />,
  task: <CircleDot className="h-4 w-4 text-aqua" />,
  ticket: <LifeBuoy className="h-4 w-4 text-amber-400" />,
  space: <BookOpen className="h-4 w-4 text-emerald-400" />,
  page: <FileText className="h-4 w-4 text-emerald-400" />,
};

export function CommandBar() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const [results, setResults] = useState<SearchResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  // ⌘K / Ctrl+K toggles the palette anywhere in the app.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((v) => !v);
      } else if (e.key === "Escape") {
        setOpen(false);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 20);
    else { setQ(""); setResults(null); }
  }, [open]);

  // Debounced search as you type.
  useEffect(() => {
    if (q.trim().length < 2) { setResults(null); setLoading(false); return; }
    setLoading(true);
    const id = setTimeout(() => {
      api.search(q).then(setResults).catch(() => setResults(null)).finally(() => setLoading(false));
    }, 180);
    return () => clearTimeout(id);
  }, [q]);

  const go = useCallback((hit: SearchHit) => {
    setOpen(false);
    router.push(hit.href);
  }, [router]);

  const flat = results?.groups.flatMap((g) => g.hits) ?? [];

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="inline-flex h-9 items-center gap-2 rounded-md border border-fg/10 bg-fg/5 px-3 text-sm text-fg/40 hover:bg-fg/10 hover:text-fg/70"
        aria-label="Search"
      >
        <Search className="h-4 w-4" />
        <span className="hidden md:inline">Search…</span>
        <kbd className="hidden rounded bg-fg/10 px-1.5 py-0.5 text-[10px] text-fg/50 md:inline">⌘K</kbd>
      </button>

      {open && (
        <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/60 p-4 pt-[12vh] backdrop-blur-sm"
          onClick={() => setOpen(false)}>
          <div className="w-full max-w-xl overflow-hidden rounded-xl border border-fg/10 bg-surface shadow-2xl"
            onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center gap-3 border-b border-fg/10 px-4">
              <Search className="h-4 w-4 shrink-0 text-fg/40" />
              <input
                ref={inputRef}
                value={q}
                onChange={(e) => setQ(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter" && flat[0]) go(flat[0]); }}
                placeholder="Search people, projects, tasks, tickets, docs…"
                className="h-14 w-full bg-transparent text-sm text-fg placeholder:text-fg/30 focus:outline-none"
              />
              {loading && <Loader2 className="h-4 w-4 shrink-0 animate-spin text-fg/40" />}
            </div>

            <div className="max-h-[50vh] overflow-y-auto p-2">
              {q.trim().length < 2 ? (
                <p className="px-3 py-8 text-center text-sm text-fg/30">Type at least 2 characters.</p>
              ) : results && results.total > 0 ? (
                results.groups.map((group) => (
                  <div key={group.label} className="mb-2">
                    <p className="px-3 py-1 text-xs font-medium uppercase tracking-wide text-fg/30">{group.label}</p>
                    {group.hits.map((hit, i) => (
                      <button
                        key={`${group.label}-${i}`}
                        onClick={() => go(hit)}
                        className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left hover:bg-fg/5"
                      >
                        <span className="shrink-0">{ICON[hit.kind]}</span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm text-fg">{hit.title}</span>
                          <span className="block truncate text-xs text-fg/40">{hit.subtitle}</span>
                        </span>
                      </button>
                    ))}
                  </div>
                ))
              ) : !loading ? (
                <p className="px-3 py-8 text-center text-sm text-fg/30">No results for “{q}”.</p>
              ) : null}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
