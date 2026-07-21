"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, CalendarDays } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Task } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const priorityChip: Record<string, string> = {
  LOW: "bg-fg/10 text-fg/50",
  MEDIUM: "bg-sky-500/15 text-sky-300",
  HIGH: "bg-amber-500/15 text-amber-300",
  URGENT: "bg-red-500/15 text-red-300",
};
const statusLabel: Record<string, string> = { TODO: "To do", IN_PROGRESS: "In progress", DONE: "Done" };

export default function MyWorkPage() {
  const [tasks, setTasks] = useState<Task[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.myTasks().then(setTasks).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load"));
  }, []);

  return (
    <div>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">My work</h1>
          <p className="mt-1 text-fg/50">Open tasks assigned to you across all projects.</p>
        </div>
        <Link href="/work" className="text-sm text-fg/60 hover:text-fg">← Projects</Link>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {tasks === null ? (
        <Card className="mt-8"><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>
      ) : tasks.length === 0 ? (
        <Card className="mt-8 py-12 text-center">
          <CardTitle>You&apos;re all clear</CardTitle>
          <p className="mt-1 text-sm text-fg/50">No open tasks are assigned to you.</p>
        </Card>
      ) : (
        <div className="mt-8 flex flex-col gap-2">
          {tasks.map((t) => (
            <Link key={t.id} href={`/work/${t.projectId}`}>
              <Card className="flex items-center justify-between p-4 transition-colors hover:border-fg/20">
                <div className="flex items-center gap-3">
                  <span className="text-xs font-medium text-fg/40">{t.ref}</span>
                  <span className="text-sm">{t.title}</span>
                </div>
                <div className="flex items-center gap-3 text-xs text-fg/40">
                  {t.dueDate && <span className="flex items-center gap-1"><CalendarDays className="h-3 w-3" />{t.dueDate}</span>}
                  <span className="text-fg/50">{statusLabel[t.status]}</span>
                  <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${priorityChip[t.priority]}`}>{t.priority.toLowerCase()}</span>
                </div>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
