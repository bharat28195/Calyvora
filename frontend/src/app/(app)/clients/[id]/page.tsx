"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Loader2, ArrowLeft, Plus, Trash2, Building2, Mail, Phone, Globe } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { ClientDetail, ClientRequestItem } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const REQ_STATUS = ["REQUESTED", "IN_PROGRESS", "DELIVERED", "DECLINED"] as const;
const REQ_STYLE: Record<string, string> = {
  REQUESTED: "bg-aqua/15 text-aqua",
  IN_PROGRESS: "bg-violet/15 text-violet",
  DELIVERED: "bg-emerald-500/15 text-emerald-400",
  DECLINED: "bg-red-500/15 text-red-400",
};

export default function ClientDetailPage() {
  const id = String(useParams().id);
  const [data, setData] = useState<ClientDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => api.client(id).then(setData).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load")), [id]);
  useEffect(() => { load(); }, [load]);

  async function addRequest(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    setBusy(true); setError(null);
    try {
      const r = await api.addClientRequest(id, { title: title.trim() });
      setData((d) => (d ? { ...d, requests: [r, ...d.requests] } : d));
      setTitle("");
    } catch (err) { setError(err instanceof ApiError ? err.message : "Failed to add"); }
    finally { setBusy(false); }
  }

  async function setStatus(r: ClientRequestItem, status: ClientRequestItem["status"]) {
    setData((d) => (d ? { ...d, requests: d.requests.map((x) => (x.id === r.id ? { ...x, status } : x)) } : d));
    try { await api.updateClientRequest(id, r.id, { status }); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Failed to update"); }
  }

  async function remove(r: ClientRequestItem) {
    setData((d) => (d ? { ...d, requests: d.requests.filter((x) => x.id !== r.id) } : d));
    try { await api.deleteClientRequest(id, r.id); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Failed to delete"); }
  }

  if (data === null) return <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;
  const c = data.client;

  return (
    <div>
      <Link href="/clients" className="inline-flex items-center gap-1.5 text-sm text-fg/50 hover:text-fg">
        <ArrowLeft className="h-4 w-4" /> Clients
      </Link>

      {error && <Alert tone="error" className="mt-4">{error}</Alert>}

      <div className="mt-3 flex items-start gap-3">
        <span className="grid h-12 w-12 shrink-0 place-items-center rounded-xl bg-violet/10 text-violet"><Building2 className="h-6 w-6" /></span>
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold tracking-tight">{c.name}</h1>
          <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-fg/50">
            {c.contactName && <span>{c.contactName}</span>}
            {c.contactEmail && <span className="inline-flex items-center gap-1"><Mail className="h-3.5 w-3.5" />{c.contactEmail}</span>}
            {c.phone && <span className="inline-flex items-center gap-1"><Phone className="h-3.5 w-3.5" />{c.phone}</span>}
            {c.website && <a href={c.website} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 hover:text-fg"><Globe className="h-3.5 w-3.5" />{c.website}</a>}
          </div>
        </div>
      </div>

      <Card className="mt-6">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">Requests</h2>
          <span className="text-sm text-fg/40">{c.openRequests} open</span>
        </div>

        <form onSubmit={addRequest} className="mt-4 flex gap-2">
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What has this client requested?" className="flex-1" />
          <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />}<Plus className="h-4 w-4" /> Add</Button>
        </form>

        <div className="mt-4 flex flex-col divide-y divide-fg/5">
          {data.requests.length === 0 ? (
            <p className="py-6 text-sm text-fg/40">No requests logged yet.</p>
          ) : (
            data.requests.map((r) => (
              <div key={r.id} className="flex items-center gap-3 py-3">
                <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs ${REQ_STYLE[r.status]}`}>{r.status.toLowerCase().replace("_", " ")}</span>
                <span className={"flex-1 text-sm " + (r.status === "DELIVERED" ? "text-fg/50 line-through" : "")}>{r.title}</span>
                <select value={r.status} onChange={(e) => setStatus(r, e.target.value as ClientRequestItem["status"])}
                  className="rounded-md border border-fg/15 bg-fg/5 px-2 py-1 text-xs text-fg">
                  {REQ_STATUS.map((s) => <option key={s} value={s} className="bg-surface">{s.toLowerCase().replace("_", " ")}</option>)}
                </select>
                <button onClick={() => remove(r)} className="text-fg/30 hover:text-red-400" aria-label="Delete request"><Trash2 className="h-4 w-4" /></button>
              </div>
            ))
          )}
        </div>
      </Card>

      {c.notes && (
        <Card className="mt-6">
          <h2 className="text-sm font-medium text-fg/70">Notes</h2>
          <p className="mt-2 whitespace-pre-wrap text-sm text-fg/80">{c.notes}</p>
        </Card>
      )}
    </div>
  );
}
