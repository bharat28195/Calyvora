"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { Loader2, ArrowLeft, Send, UserCheck } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { HelpdeskTicket, HelpdeskComment, TicketStatus } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { dateTime } from "@/lib/format";
import { STATUS_TONE, STATUS_LABEL, PRIORITY_TONE } from "@/lib/helpdesk";

/** One helpdesk ticket: details, the conversation thread, and (for HR) status/assignment controls. */
export default function TicketPage() {
  const { id } = useParams<{ id: string }>();
  const { me } = useSession();
  const isAgent = me?.user.role === "ADMIN" || me?.user.role === "HR" || me?.user.role === "OWNER";
  const [ticket, setTicket] = useState<HelpdeskTicket | null>(null);
  const [comments, setComments] = useState<HelpdeskComment[]>([]);
  const [reply, setReply] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api.helpdeskTicket(id).then(setTicket).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load ticket"));
    api.helpdeskComments(id).then(setComments).catch(() => setComments([]));
  }, [id]);
  useEffect(() => { load(); }, [load]);

  async function send() {
    if (!reply.trim()) return;
    setBusy(true); setError(null);
    try {
      await api.commentOnTicket(id, reply.trim());
      setReply("");
      load();
    } catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't send"); }
    finally { setBusy(false); }
  }

  async function patch(fn: () => Promise<unknown>) {
    setBusy(true); setError(null);
    try { await fn(); load(); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Update failed"); }
    finally { setBusy(false); }
  }

  if (!ticket) {
    return <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;
  }

  return (
    <div className="max-w-3xl">
      <Link href="/helpdesk" className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg"><ArrowLeft className="h-4 w-4" /> Helpdesk</Link>

      <div className="mt-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">{ticket.subject}</h1>
          <p className="mt-1 text-sm text-fg/50">
            {ticket.category.toLowerCase()} · <span className={PRIORITY_TONE[ticket.priority]}>{ticket.priority.toLowerCase()}</span>
            {" · raised by "}{ticket.raisedByName}
            {ticket.assigneeName ? ` · assigned to ${ticket.assigneeName}` : ""}
          </p>
        </div>
        <span className={cn("rounded-full px-2.5 py-1 text-xs font-medium", STATUS_TONE[ticket.status])}>{STATUS_LABEL[ticket.status]}</span>
      </div>

      {error && <Alert tone="error" className="mt-4">{error}</Alert>}

      {isAgent && (
        <Card className="mt-4">
          <div className="flex flex-wrap items-end gap-3">
            <label className="text-sm">
              <span className="block text-xs text-fg/50">Status</span>
              <select value={ticket.status} disabled={busy}
                onChange={(e) => patch(() => api.updateHelpdeskTicket(id, { status: e.target.value as TicketStatus }))}
                className="mt-1 h-9 rounded-lg border border-fg/15 bg-fg/5 px-2 text-sm text-fg">
                {(["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"] as const).map((s) => <option key={s} value={s} className="bg-surface">{STATUS_LABEL[s]}</option>)}
              </select>
            </label>
            {ticket.assigneeId !== me?.user.id && (
              <Button size="sm" variant="secondary" disabled={busy}
                onClick={() => patch(() => api.updateHelpdeskTicket(id, { assigneeId: me!.user.id }))}>
                <UserCheck className="h-4 w-4" /> Assign to me
              </Button>
            )}
          </div>
        </Card>
      )}

      {ticket.description && (
        <Card className="mt-4">
          <CardTitle>Details</CardTitle>
          <p className="mt-2 whitespace-pre-wrap text-sm text-fg/80">{ticket.description}</p>
          <p className="mt-3 text-xs text-fg/40">Raised {dateTime(ticket.createdAt)}</p>
        </Card>
      )}

      <Card className="mt-4">
        <CardTitle>Conversation</CardTitle>
        {comments.length === 0 ? (
          <p className="mt-3 text-sm text-fg/40">No replies yet.</p>
        ) : (
          <div className="mt-3 flex flex-col gap-3">
            {comments.map((c) => {
              const mine = c.authorId === me?.user.id;
              return (
                <div key={c.id} className={cn("flex flex-col", mine ? "items-end" : "items-start")}>
                  <div className={cn("max-w-[85%] rounded-2xl px-3.5 py-2 text-sm", mine ? "bg-violet/15 text-fg" : "bg-fg/5 text-fg/90")}>
                    {c.body}
                  </div>
                  <span className="mt-1 text-[11px] text-fg/40">{c.authorName} · {dateTime(c.createdAt)}</span>
                </div>
              );
            })}
          </div>
        )}

        <div className="mt-4 flex items-end gap-2">
          <textarea value={reply} onChange={(e) => setReply(e.target.value)} rows={2}
            onKeyDown={(e) => { if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) send(); }}
            placeholder="Write a reply…  (⌘/Ctrl+Enter to send)"
            className="min-h-[44px] flex-1 rounded-lg border border-fg/15 bg-fg/5 p-3 text-sm text-fg placeholder:text-fg/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet" />
          <Button onClick={send} disabled={busy || !reply.trim()}>{busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}</Button>
        </div>
      </Card>
    </div>
  );
}
