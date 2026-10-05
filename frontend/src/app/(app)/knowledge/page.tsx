"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, Plus, FolderOpen, FileText, ArrowRight, CalendarCheck } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Space } from "@/lib/types";
import { useSession } from "@/hooks/useSession";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";
import { KnowledgeSearch } from "@/components/knowledge/knowledge-search";
import { can, canCompanyWide } from "@/lib/permissions";

/**
 * Company documents: the handbook, policies and forms everyone in the company can read. HR and admins
 * publish (folders, pages, uploaded files); everyone else reads what has been published.
 */
export default function CompanyDocumentsPage() {
  const { me } = useSession();
  const publisher = can(me, "DOCUMENTS_PUBLISH");
  const [spaces, setSpaces] = useState<Space[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      setSpaces(await api.listSpaces());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load company documents");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const active = spaces?.filter((s) => s.status === "ACTIVE") ?? [];

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Company documents</h1>
          <p className="mt-1 text-fg/50">
            Policies, handbooks and forms for everyone at {me?.company.name ?? "your company"}.
          </p>
        </div>
        {publisher && <Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" /> New folder</Button>}
      </div>

      <KnowledgeSearch className="mt-6" />

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {/* Always here: the live leave policy, read straight from the leave settings HR maintains, so it
            can never disagree with what the leave screens actually apply. */}
        <Link href="/policies/leave">
          <Card className="group h-full transition-colors hover:border-fg/20">
            <div className="flex items-center justify-between">
              <span className="rounded-md bg-emerald-500/15 px-2 py-0.5 text-xs font-semibold text-emerald-700 dark:text-emerald-300">Live</span>
              <ArrowRight className="h-4 w-4 text-fg/20 transition-colors group-hover:text-fg/60" />
            </div>
            <h3 className="mt-3 flex items-center gap-2 font-medium">
              <CalendarCheck className="h-4 w-4 text-violet" /> Leave policy
            </h3>
            <p className="mt-1 text-sm text-fg/50">How much leave you get, how it builds up and what carries forward.</p>
          </Card>
        </Link>

        {spaces === null ? (
          <Card><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>
        ) : (
          active.map((s) => (
            <Link key={s.id} href={`/knowledge/${s.id}`}>
              <Card className="group h-full transition-colors hover:border-fg/20">
                <div className="flex items-center justify-between">
                  <span className="rounded-md bg-violet/20 px-2 py-0.5 text-xs font-semibold text-violet">{s.key}</span>
                  <ArrowRight className="h-4 w-4 text-fg/20 transition-colors group-hover:text-fg/60" />
                </div>
                <h3 className="mt-3 flex items-center gap-2 font-medium">
                  <FolderOpen className="h-4 w-4 text-fg/40" /> {s.name}
                </h3>
                {s.description && <p className="mt-1 line-clamp-2 text-sm text-fg/50">{s.description}</p>}
                <p className="mt-4 flex items-center gap-1.5 text-xs text-fg/40">
                  <FileText className="h-3.5 w-3.5" />
                  {s.pageCount} {s.pageCount === 1 ? "page" : "pages"}
                </p>
              </Card>
            </Link>
          ))
        )}
      </div>

      {spaces !== null && active.length === 0 && (
        <Card className="mt-4 flex flex-col items-center gap-3 py-10 text-center">
          <FolderOpen className="h-8 w-8 text-fg/30" />
          <CardTitle>No folders yet</CardTitle>
          {publisher ? (
            <>
              <p className="text-sm text-fg/50">
                Create a folder such as &ldquo;Employee handbook&rdquo;, then write policies in it or upload PDFs.
              </p>
              <Button onClick={() => setOpen(true)} className="mt-2"><Plus className="h-4 w-4" /> New folder</Button>
            </>
          ) : (
            <p className="text-sm text-fg/50">HR hasn&apos;t published any documents yet.</p>
          )}
        </Card>
      )}

      {publisher && (
        <NewFolderDialog open={open} onClose={() => setOpen(false)} onCreated={() => { setOpen(false); void load(); }} />
      )}
    </div>
  );
}

function NewFolderDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const [name, setName] = useState("");
  const [key, setKey] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [keyTouched, setKeyTouched] = useState(false);

  const suggestedKey = name.replace(/[^A-Za-z0-9]/g, "").slice(0, 4).toUpperCase();
  const effectiveKey = keyTouched ? key : suggestedKey;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (name.trim().length < 2 || effectiveKey.length < 1) {
      setError("Enter a name and a short code.");
      return;
    }
    setBusy(true);
    try {
      await api.createSpace({ name: name.trim(), key: effectiveKey, description });
      setName(""); setKey(""); setDescription(""); setKeyTouched(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create the folder");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New folder">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Name" htmlFor="s-name">
          <Input id="s-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Employee handbook" />
        </Field>
        <Field label="Short code" htmlFor="s-key" hint="A few letters shown on the folder, such as HR or POL.">
          <Input id="s-key" value={effectiveKey} maxLength={10}
            onChange={(e) => { setKeyTouched(true); setKey(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "")); }} />
        </Field>
        <Field label="Description (optional)" htmlFor="s-desc">
          <Input id="s-desc" value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Create</Button>
        </div>
      </form>
    </Modal>
  );
}
