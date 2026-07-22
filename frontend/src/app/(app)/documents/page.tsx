"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, FileText, Plus, ArrowRight, User } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { GeneratedDoc } from "@/lib/types";
import { KIND_LABELS } from "@/lib/documents";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/** Every letter this company has issued (feedback D2). Newest first. */
export default function DocumentsPage() {
  const [docs, setDocs] = useState<GeneratedDoc[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.documents()
      .then(setDocs)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load documents"));
  }, []);

  return (
    <div>
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Documents</h1>
          <p className="mt-1 text-fg/50">Letters you&apos;ve issued — joining, relieving, offers and more.</p>
        </div>
        <Link href="/documents/new"><Button><Plus className="h-4 w-4" /> Generate</Button></Link>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {docs === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : docs.length === 0 ? (
        <Card className="mt-8 text-center">
          <p className="text-sm text-fg/50">No documents yet.</p>
          <p className="mt-1 text-sm text-fg/40">
            Pick a template, pick a person, and Orbit writes the letter for you.
          </p>
          <Link href="/documents/new" className="mt-4 inline-block">
            <Button><Plus className="h-4 w-4" /> Generate your first document</Button>
          </Link>
        </Card>
      ) : (
        <div className="mt-8 flex flex-col gap-2">
          {docs.map((d) => (
            <Link key={d.id} href={`/documents/${d.id}`}>
              <Card className="flex items-center justify-between gap-3 p-4 transition-colors hover:border-fg/20">
                <div className="flex min-w-0 items-center gap-3">
                  <span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-violet/10 text-violet">
                    <FileText className="h-4 w-4" />
                  </span>
                  <div className="min-w-0">
                    <p className="truncate font-medium">{d.title}</p>
                    <p className="truncate text-xs text-fg/40">
                      {KIND_LABELS[d.kind]}
                      {d.employeeName && <> · <User className="inline h-3 w-3" /> {d.employeeName}</>}
                      {" · "}{new Date(d.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                </div>
                <ArrowRight className="h-4 w-4 shrink-0 text-fg/30" />
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
