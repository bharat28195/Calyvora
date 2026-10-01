"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Search, FileText } from "lucide-react";
import { api } from "@/lib/api";
import type { PageSummary } from "@/lib/types";

/**
 * Always-available Knowledge search (founder feedback A2 — "search should be present at all times").
 * Reused on the Knowledge index and inside every space, so a page is always findable.
 */
export function KnowledgeSearch({ className = "" }: { className?: string }) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<PageSummary[] | null>(null);
  const [busy, setBusy] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (timer.current) clearTimeout(timer.current);
    const term = q.trim();
    if (term.length < 2) {
      setResults(null);
      return;
    }
    timer.current = setTimeout(async () => {
      setBusy(true);
      try {
        setResults(await api.searchPages(term));
      } catch {
        setResults([]);
      } finally {
        setBusy(false);
      }
    }, 250);
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [q]);

  return (
    <div className={"relative " + className}>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg/30" />
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search all pages…"
        className="w-full rounded-lg border border-fg/10 bg-fg/5 py-2.5 pl-10 pr-4 text-sm text-fg placeholder:text-fg/30 focus:border-violet focus:outline-none"
      />
      {results !== null && (
        <div className="absolute z-20 mt-2 w-full rounded-lg border border-fg/10 bg-surface shadow-xl">
          {busy && results.length === 0 ? (
            <p className="px-4 py-3 text-sm text-fg/40">Searching…</p>
          ) : results.length === 0 ? (
            <p className="px-4 py-3 text-sm text-fg/40">No pages match “{q.trim()}”.</p>
          ) : (
            results.map((r) => (
              <Link key={r.id} href={`/knowledge/${r.spaceId}?page=${r.id}`} onClick={() => setQ("")}
                className="block border-b border-fg/5 px-4 py-3 last:border-0 hover:bg-fg/5">
                <div className="flex items-center gap-2">
                  <FileText className="h-3.5 w-3.5 text-fg/30" />
                  <span className="text-sm font-medium">{r.title}</span>
                  <span className="text-xs text-fg/30">· {r.spaceName}</span>
                </div>
                {r.snippet && <p className="mt-1 line-clamp-1 pl-5 text-xs text-fg/40">{r.snippet}</p>}
              </Link>
            ))
          )}
        </div>
      )}
    </div>
  );
}
