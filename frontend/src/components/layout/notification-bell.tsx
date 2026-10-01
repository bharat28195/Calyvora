"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Bell, CheckCheck } from "lucide-react";
import { api } from "@/lib/api";
import type { AppNotification } from "@/lib/types";
import { NOTIFICATION_ICON, notificationAge } from "@/lib/notifications";

/**
 * Header bell (feedback D4). Polls the cheap unread-count endpoint and only fetches the list when
 * opened — a badge shouldn't cost a full inbox query every 30 seconds.
 */
export function NotificationBell() {
  const router = useRouter();
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<AppNotification[] | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  const poll = useCallback(() => {
    api.unreadCount().then((r) => setCount(r.count)).catch(() => {});
  }, []);

  useEffect(() => {
    poll();
    const t = setInterval(poll, 30_000);
    return () => clearInterval(t);
  }, [poll]);

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, []);

  function toggle() {
    const next = !open;
    setOpen(next);
    if (next) {
      setItems(null);
      api.notifications().then((n) => setItems(n.slice(0, 8))).catch(() => setItems([]));
    }
  }

  async function openItem(n: AppNotification) {
    setOpen(false);
    if (!n.read) {
      await api.markNotificationRead(n.id).catch(() => {});
      poll();
    }
    if (n.link) router.push(n.link);
  }

  async function readAll() {
    await api.markAllNotificationsRead().catch(() => {});
    setItems((cur) => cur?.map((n) => ({ ...n, read: true })) ?? null);
    poll();
  }

  return (
    <div ref={ref} className="relative">
      <button
        onClick={toggle}
        aria-label={count > 0 ? `Inbox — ${count} unread` : "Inbox"}
        className="relative inline-flex h-9 w-9 items-center justify-center rounded-md text-fg/60 hover:bg-fg/5 hover:text-fg"
      >
        <Bell className="h-4 w-4" />
        {count > 0 && (
          <span className="absolute -right-0.5 -top-0.5 grid h-4 min-w-4 place-items-center rounded-full bg-violet px-1 text-[10px] font-semibold text-white">
            {count > 9 ? "9+" : count}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-2 w-80 overflow-hidden rounded-xl border border-fg/10 bg-surface shadow-xl">
          <div className="flex items-center justify-between border-b border-fg/10 px-3 py-2">
            <span className="text-sm font-medium">Inbox</span>
            {count > 0 && (
              <button onClick={readAll} className="inline-flex items-center gap-1 text-xs text-fg/50 hover:text-fg">
                <CheckCheck className="h-3.5 w-3.5" /> Mark all read
              </button>
            )}
          </div>

          <div className="max-h-96 overflow-y-auto">
            {items === null ? (
              <p className="px-3 py-6 text-center text-sm text-fg/40">Loading…</p>
            ) : items.length === 0 ? (
              <p className="px-3 py-6 text-center text-sm text-fg/40">Nothing here yet.</p>
            ) : (
              items.map((n) => (
                <button
                  key={n.id}
                  onClick={() => openItem(n)}
                  className={`flex w-full items-start gap-2.5 border-b border-fg/5 px-3 py-2.5 text-left last:border-0 hover:bg-fg/5 ${
                    n.read ? "opacity-60" : ""
                  }`}
                >
                  <span className="mt-0.5 shrink-0">{NOTIFICATION_ICON[n.type]}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm">{n.title}</span>
                    {n.body && <span className="block truncate text-xs text-fg/40">{n.body}</span>}
                    <span className="block text-[11px] text-fg/30">{notificationAge(n.createdAt)}</span>
                  </span>
                  {!n.read && <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-violet" />}
                </button>
              ))
            )}
          </div>

          <Link
            href="/inbox"
            onClick={() => setOpen(false)}
            className="block border-t border-fg/10 px-3 py-2 text-center text-sm text-violet hover:bg-fg/5"
          >
            Open inbox
          </Link>
        </div>
      )}
    </div>
  );
}
