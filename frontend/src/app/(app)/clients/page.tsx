"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, Plus, Building2, ArrowRight, Mail } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Client } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";

const STATUS_STYLE: Record<Client["status"], string> = {
  LEAD: "bg-aqua/15 text-aqua",
  ACTIVE: "bg-emerald-500/15 text-emerald-400",
  CHURNED: "bg-fg/10 text-fg/50",
};

export default function ClientsPage() {
  const [clients, setClients] = useState<Client[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const load = () => api.clients().then(setClients).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load clients"));
  useEffect(() => { load(); }, []);

  return (
    <div>
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Clients</h1>
          <p className="mt-1 text-fg/50">Your customers and everything they&apos;ve requested.</p>
        </div>
        <Button onClick={() => setCreating(true)}><Plus className="h-4 w-4" /> New client</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {clients === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : clients.length === 0 ? (
        <Card className="mt-8 text-center text-sm text-fg/50">
          No clients yet. Add your first one to track what they need.
        </Card>
      ) : (
        <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {clients.map((c) => (
            <Link key={c.id} href={`/clients/${c.id}`}>
              <Card className="h-full transition-colors hover:border-fg/20">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-violet/10 text-violet">
                      <Building2 className="h-4 w-4" />
                    </span>
                    <div className="min-w-0">
                      <p className="truncate font-medium">{c.name}</p>
                      {c.contactName && <p className="truncate text-xs text-fg/40">{c.contactName}</p>}
                    </div>
                  </div>
                  <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs ${STATUS_STYLE[c.status]}`}>{c.status.toLowerCase()}</span>
                </div>
                <div className="mt-3 flex items-center justify-between text-xs text-fg/40">
                  <span className="inline-flex items-center gap-1 truncate">
                    {c.contactEmail && <><Mail className="h-3 w-3" /> {c.contactEmail}</>}
                  </span>
                  <span className="inline-flex items-center gap-1 text-fg/60">
                    {c.openRequests} open <ArrowRight className="h-3 w-3" />
                  </span>
                </div>
              </Card>
            </Link>
          ))}
        </div>
      )}

      {creating && <NewClientDialog onClose={() => setCreating(false)} onCreated={() => { setCreating(false); load(); }} />}
    </div>
  );
}

function NewClientDialog({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [form, setForm] = useState({ name: "", contactName: "", contactEmail: "", phone: "", website: "", status: "LEAD", notes: "" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!form.name.trim()) { setError("Enter a client name."); return; }
    setBusy(true); setError(null);
    try {
      await api.createClient({ ...form, status: form.status as Client["status"] });
      onCreated();
    } catch (err) { setError(err instanceof ApiError ? err.message : "Failed to create"); setBusy(false); }
  }

  return (
    <Modal open onClose={onClose} title="New client">
      <form onSubmit={submit} className="flex flex-col gap-3" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Company name" htmlFor="cl-name"><Input id="cl-name" value={form.name} onChange={set("name")} autoFocus placeholder="Globex Corporation" /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Contact name" htmlFor="cl-contact"><Input id="cl-contact" value={form.contactName} onChange={set("contactName")} /></Field>
          <Field label="Contact email" htmlFor="cl-email"><Input id="cl-email" type="email" value={form.contactEmail} onChange={set("contactEmail")} /></Field>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Phone" htmlFor="cl-phone"><Input id="cl-phone" value={form.phone} onChange={set("phone")} /></Field>
          <Field label="Status" htmlFor="cl-status">
            <select id="cl-status" value={form.status} onChange={set("status")}
              className="h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet">
              {["LEAD", "ACTIVE", "CHURNED"].map((s) => <option key={s} value={s} className="bg-surface">{s.toLowerCase()}</option>)}
            </select>
          </Field>
        </div>
        <Field label="Website" htmlFor="cl-web"><Input id="cl-web" value={form.website} onChange={set("website")} placeholder="https://…" /></Field>
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Create</Button>
        </div>
      </form>
    </Modal>
  );
}
