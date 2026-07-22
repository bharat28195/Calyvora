"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Loader2, ArrowLeft, Printer, Copy, Check, Trash2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { GeneratedDoc } from "@/lib/types";
import { KIND_LABELS } from "@/lib/documents";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { LetterSheet } from "@/components/documents/letter";

/** A single issued letter — frozen at generation time, ready to print or copy (feedback D2). */
export default function DocumentPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [doc, setDoc] = useState<GeneratedDoc | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    api.document(id)
      .then(setDoc)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load the document"));
  }, [id]);

  async function copy() {
    if (!doc) return;
    await navigator.clipboard.writeText(doc.body);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  async function remove() {
    if (!doc || !confirm("Delete this document? The letter itself can be regenerated from its template.")) return;
    await api.deleteDocument(doc.id);
    router.push("/documents");
  }

  if (error) return <Alert tone="error">{error}</Alert>;
  if (!doc) return <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;

  return (
    <div>
      <Link href="/documents" className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
        <ArrowLeft className="h-4 w-4" /> Documents
      </Link>

      <div className="mt-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{doc.title}</h1>
          <p className="mt-1 text-sm text-fg/50">
            {KIND_LABELS[doc.kind]}
            {doc.employeeName && <> · {doc.employeeName}</>}
            {" · issued "}{new Date(doc.createdAt).toLocaleDateString()}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={copy}>
            {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />} {copied ? "Copied" : "Copy"}
          </Button>
          <Button variant="secondary" onClick={() => window.print()}>
            <Printer className="h-4 w-4" /> Print / PDF
          </Button>
          <Button variant="ghost" onClick={remove} aria-label="Delete document">
            <Trash2 className="h-4 w-4 text-red-400/80" />
          </Button>
        </div>
      </div>

      <LetterSheet body={doc.body} className="mt-6" />
    </div>
  );
}
