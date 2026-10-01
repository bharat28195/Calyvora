"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, CheckCheck, Inbox as InboxIcon } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AppNotification } from "@/lib/types";
import { NOTIFICATION_ICON, notificationAge } from "@/lib/notifications";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/** The full inbox (feedback D5) — everything routed to you, newest first. */
export default function InboxPage() {
  const router = useRouter();
  const [items, setItems] = useState<AppNotification[] | null>(null);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setItems(null);
    api.notifications(unreadOnly)
      .then(setItems)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load your inbox"));
  }, [unreadOnly]);

  useEffect(() => load(), [load]);

  async function open(n: AppNotification) {
    if (!n.read) await api.markNotificationRead(n.id).catch(() => {});
    if (n.link) router.push(n.link);
    else load();
  }

  const unread = items?.filter((n) => !n.read).length ?? 0;

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Inbox</h1>
          <p className="mt-1 text-fg/50">
            Approvals waiting on you, decisions on your requests, and goals you&apos;ve been set.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setUnreadOnly((v) => !v)}
            className={`rounded-lg px-3 py-1.5 text-sm ${unreadOnly ? "bg-violet/10 text-violet" : "text-fg/60 hover:bg-fg/5"}`}
          >
            Unread only
          </button>
          {unread > 0 && (
            <Button variant="secondary" onClick={() => api.markAllNotificationsRead().then(load)}>
              <CheckCheck className="h-4 w-4" /> Mark all read
            </Button>
          )}
        </div>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {items === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : items.length === 0 ? (
        <Card className="mt-8 text-center">
          <InboxIcon className="mx-auto h-8 w-8 text-fg/20" />
          <p className="mt-3 text-sm text-fg/50">
            {unreadOnly ? "Nothing unread." : "Nothing here yet."}
          </p>
          <p className="mt-1 text-sm text-fg/40">
            Leave requests, approvals and new goals will show up here.
          </p>
        </Card>
      ) : (
        <div className="mt-8 flex flex-col gap-2">
          {items.map((n) => (
            <button key={n.id} onClick={() => open(n)} className="text-left">
              <Card className={`flex items-start gap-3 p-4 transition-colors hover:border-fg/20 ${n.read ? "opacity-60" : ""}`}>
                <span className="mt-0.5 shrink-0">{NOTIFICATION_ICON[n.type]}</span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{n.title}</p>
                  {n.body && <p className="truncate text-xs text-fg/50">{n.body}</p>}
                </div>
                <span className="shrink-0 text-xs text-fg/30">{notificationAge(n.createdAt)}</span>
                {!n.read && <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-violet" />}
              </Card>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
