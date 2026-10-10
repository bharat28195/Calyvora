"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Loader2, ArrowLeft, Printer, Copy, Check, Trash2, Download, Mail, CheckCircle2, XCircle } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { DocumentEmailState, GeneratedDoc, Letterhead } from "@/lib/types";
import { KIND_LABELS } from "@/lib/documents";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { Card, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { LetterSheet } from "@/components/documents/letter";
import { formatDate } from "@/lib/format";

/**
 * A single issued letter — frozen at generation time, ready to print, download as PDF or email to
 * the person it is for (feedback D2, PD-64).
 */
export default function DocumentPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [doc, setDoc] = useState<GeneratedDoc | null>(null);
  const [letterhead, setLetterhead] = useState<Letterhead | null>(null);
  const [mail, setMail] = useState<DocumentEmailState | null>(null);
  const [sending, setSending] = useState(false);
  const [form, setForm] = useState({ to: "", subject: "", message: "" });
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    api.document(id)
      .then(setDoc)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load the document"));
    // The letterpad is current, not frozen: the words someone signed are fixed, the stationery is
    // whatever the company uses today.
    api.letterhead().then(setLetterhead).catch(() => setLetterhead(null));
    api.documentEmail(id).then(setMail).catch(() => setMail(null));
  }, [id]);

  async function copy() {
    if (!doc) return;
    await navigator.clipboard.writeText(doc.body);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  async function download() {
    if (!doc) return;
    setBusy(true); setError(null);
    try {
      const blob = await api.documentPdf(doc.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${doc.title.replace(/[\\/:*?"<>|]/g, " ").trim() || "Letter"}.pdf`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not make the PDF");
    } finally {
      setBusy(false);
    }
  }

  function openSend() {
    if (!mail) return;
    setForm({ to: mail.to ?? "", subject: mail.subject, message: mail.message });
    setOpen(true);
  }

  async function send() {
    if (!doc) return;
    setSending(true); setError(null); setNotice(null);
    try {
      const next = await api.sendDocumentEmail(doc.id, form);
      setMail(next);
      setOpen(false);
      setNotice(`Sent to ${form.to}, with the letter attached as a PDF.`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not send the letter");
      api.documentEmail(doc.id).then(setMail).catch(() => undefined);
    } finally {
      setSending(false);
    }
  }

  async function remove() {
    if (!doc || !confirm("Delete this document? The letter itself can be regenerated from its template.")) return;
    await api.deleteDocument(doc.id);
    router.push("/documents");
  }

  if (error && !doc) return <Alert tone="error">{error}</Alert>;
  if (!doc) return <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;

  return (
    <div>
      <Link href="/documents" className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg print:hidden">
        <ArrowLeft className="h-4 w-4" /> Documents
      </Link>

      <div className="mt-4 flex flex-wrap items-start justify-between gap-3 print:hidden">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{doc.title}</h1>
          <p className="mt-1 text-sm text-fg/50">
            {KIND_LABELS[doc.kind]}
            {doc.employeeName && <> · {doc.employeeName}</>}
            {" · issued "}{formatDate(doc.createdAt)}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onClick={openSend} disabled={!mail}>
            <Mail className="h-4 w-4" /> Send by email
          </Button>
          <Button variant="secondary" onClick={() => void download()} disabled={busy}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />} Download PDF
          </Button>
          <Button variant="secondary" onClick={() => window.print()}>
            <Printer className="h-4 w-4" /> Print
          </Button>
          <Button variant="ghost" onClick={copy} aria-label="Copy the text">
            {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
          </Button>
          <Button variant="ghost" onClick={remove} aria-label="Delete document">
            <Trash2 className="h-4 w-4 text-red-400/80" />
          </Button>
        </div>
      </div>

      {error && <Alert tone="error" className="mt-4 print:hidden">{error}</Alert>}
      {notice && <Alert tone="success" className="mt-4 print:hidden">{notice}</Alert>}

      <LetterSheet body={doc.body} letterhead={doc.useLetterhead ? letterhead : null} className="mt-6" />

      {mail && mail.sent.length > 0 && (
        <Card className="mt-6 print:hidden">
          <CardTitle>Sent</CardTitle>
          <ul className="mt-3 divide-y divide-fg/5 text-sm">
            {mail.sent.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <span className="flex items-center gap-2">
                  {s.delivered ? <CheckCircle2 className="h-4 w-4 text-emerald-500" /> : <XCircle className="h-4 w-4 text-red-500" />}
                  <span className="font-medium">{s.to}</span>
                  <span className="text-fg/50">· {s.subject}</span>
                </span>
                <span className="text-xs text-fg/45">
                  {new Date(s.sentAt).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" })}
                  {!s.delivered && s.error ? ` · ${s.error}` : ""}
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Modal open={open} onClose={() => setOpen(false)} title="Send by email">
        <div className="flex flex-col gap-3">
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-fg/60">To</span>
            <Input type="email" value={form.to} placeholder="name@example.com" onChange={(e) => setForm({ ...form, to: e.target.value })} />
            <span className="text-[11px] text-fg/40">For someone not yet on Orbit, use their personal email.</span>
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-fg/60">Subject</span>
            <Input value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })} />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-fg/60">Message</span>
            <textarea rows={7} value={form.message} onChange={(e) => setForm({ ...form, message: e.target.value })}
              className="w-full rounded-lg border border-fg/15 bg-fg/5 p-3 text-sm leading-relaxed text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet" />
          </label>
          <p className="text-xs text-fg/50">
            The letter goes as a PDF on your letterpad. It is sent as &quot;your company via Orbit&quot;, and replies go to the email on your letterpad.
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button onClick={() => void send()} disabled={sending || !form.to.trim()}>
              {sending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Mail className="h-4 w-4" />} Send
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
