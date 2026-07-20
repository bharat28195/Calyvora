"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, FileText, Link2, ArrowLeft } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { PageSummary } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

export default function MyPagesPage() {
  const [pages, setPages] = useState<PageSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void api.myPages().then(setPages).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load your pages"));
  }, []);

  return (
    <div>
      <Link href="/knowledge" className="inline-flex items-center gap-1.5 text-sm text-white/50 hover:text-white">
        <ArrowLeft className="h-4 w-4" /> Knowledge
      </Link>
      <h1 className="mt-3 text-2xl font-semibold tracking-tight">My pages</h1>
      <p className="mt-1 text-white/50">Everything you&apos;ve authored, across every space.</p>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {pages === null ? (
        <Card className="mt-8"><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>
      ) : pages.length === 0 ? (
        <Card className="mt-8 flex flex-col items-center gap-3 py-12 text-center">
          <FileText className="h-8 w-8 text-white/30" />
          <CardTitle>Nothing yet</CardTitle>
          <p className="text-sm text-white/50">Pages you write will show up here.</p>
        </Card>
      ) : (
        <div className="mt-8 space-y-2">
          {pages.map((p) => (
            <Link key={p.id} href={`/knowledge/${p.spaceId}?page=${p.id}`}>
              <Card className="flex items-center gap-3 transition-colors hover:border-white/20">
                <FileText className="h-4 w-4 shrink-0 text-white/30" />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="truncate font-medium">{p.title}</span>
                    {p.status === "DRAFT" && <span className="shrink-0 text-[10px] uppercase tracking-wide text-amber-300/70">draft</span>}
                  </div>
                  <p className="text-xs text-white/40">{p.spaceName}</p>
                </div>
                {p.linkedTaskRef && (
                  <span className="inline-flex shrink-0 items-center gap-1 rounded bg-aqua/15 px-1.5 py-0.5 text-xs font-medium text-aqua">
                    <Link2 className="h-3 w-3" /> {p.linkedTaskRef}
                  </span>
                )}
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
