"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, FileSignature, FileText } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { GeneratedDoc } from "@/lib/types";
import { KIND_LABELS } from "@/lib/documents";

/**
 * The letters issued for one person, shown on their profile (feedback D2). Closes the loop between
 * People and Documents: you find the employee, you see their paperwork, you issue the next one.
 */
export function EmployeeDocuments({ employeeId }: { employeeId: string }) {
  const [docs, setDocs] = useState<GeneratedDoc[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.documents(employeeId)
      .then(setDocs)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load documents"));
  }, [employeeId]);

  return (
    <div className="mt-6">
      <div className="flex items-center justify-between">
        <h3 className="flex items-center gap-1.5 text-sm font-medium text-fg/80">
          <FileText className="h-4 w-4 text-amber-400" /> Documents
        </h3>
        <Link
          href={`/documents/new?employee=${employeeId}`}
          className="inline-flex items-center gap-1 text-xs text-violet hover:underline"
        >
          <FileSignature className="h-3.5 w-3.5" /> Generate
        </Link>
      </div>

      {error ? (
        <p className="mt-2 text-sm text-red-400">{error}</p>
      ) : docs === null ? (
        <Loader2 className="mt-3 h-5 w-5 animate-spin text-violet" />
      ) : docs.length === 0 ? (
        <p className="mt-3 rounded-lg border border-fg/10 bg-fg/5 p-3 text-sm text-fg/50">
          No letters issued yet.
        </p>
      ) : (
        <ul className="mt-3 flex flex-col gap-1.5">
          {docs.map((d) => (
            <li key={d.id}>
              <Link
                href={`/documents/${d.id}`}
                className="flex items-center justify-between gap-2 rounded-lg border border-fg/10 bg-fg/5 px-3 py-2 text-sm hover:border-fg/20"
              >
                <span className="min-w-0 truncate">{d.title}</span>
                <span className="shrink-0 text-xs text-fg/40">
                  {KIND_LABELS[d.kind]} · {new Date(d.createdAt).toLocaleDateString()}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
