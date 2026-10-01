"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, Star, CircleDot, AlertTriangle } from "lucide-react";
import { api } from "@/lib/api";
import type { Employee, WorkItem } from "@/lib/types";

/** Richer profile: skills, rating, and what the employee is working on (founder feedback C4–C6). */
export function EmployeeProfileExtras({ employee }: { employee: Employee }) {
  const [work, setWork] = useState<WorkItem[] | null>(null);

  useEffect(() => {
    api.employeeWork(employee.id).then(setWork).catch(() => setWork([]));
  }, [employee.id]);

  return (
    <div className="mt-6 space-y-5">
      {/* Skills + rating */}
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <p className="mb-1.5 text-xs uppercase tracking-wide text-fg/40">Skills</p>
          {employee.skills.length > 0 ? (
            <div className="flex flex-wrap gap-1.5">
              {employee.skills.map((s) => (
                <span key={s} className="rounded-full bg-violet/10 px-2.5 py-0.5 text-xs font-medium text-violet">{s}</span>
              ))}
            </div>
          ) : (
            <p className="text-sm text-fg/40">No skills listed.</p>
          )}
        </div>
        <div>
          <p className="mb-1.5 text-xs uppercase tracking-wide text-fg/40">Rating</p>
          {employee.rating ? (
            <div className="flex items-center gap-0.5">
              {[1, 2, 3, 4, 5].map((n) => (
                <Star key={n} className={"h-4 w-4 " + (n <= employee.rating! ? "fill-amber-400 text-amber-400" : "text-fg/20")} />
              ))}
              <span className="ml-1 text-sm text-fg/50">{employee.rating}/5</span>
            </div>
          ) : (
            <p className="text-sm text-fg/40">Not rated yet.</p>
          )}
        </div>
      </div>

      {/* Working on */}
      <div>
        <p className="mb-2 text-xs uppercase tracking-wide text-fg/40">Working on</p>
        {work === null ? (
          <Loader2 className="h-4 w-4 animate-spin text-violet" />
        ) : work.length === 0 ? (
          <p className="text-sm text-fg/40">No open tasks assigned.</p>
        ) : (
          <div className="flex flex-col divide-y divide-fg/5">
            {work.slice(0, 8).map((w, i) => (
              <Link key={i} href={`/work/${w.projectId}`}
                className="flex items-center gap-2.5 py-2 -mx-2 px-2 rounded-lg hover:bg-fg/5">
                <CircleDot className="h-3.5 w-3.5 shrink-0 text-aqua" />
                <span className="font-mono text-xs text-fg/40 w-16 shrink-0">{w.ref}</span>
                <span className="flex-1 truncate text-sm">{w.title}</span>
                {w.overdue ? (
                  <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-red-500/15 px-2 py-0.5 text-xs text-red-400">
                    <AlertTriangle className="h-3 w-3" /> overdue
                  </span>
                ) : w.dueDate ? (
                  <span className="shrink-0 text-xs text-fg/40">due {w.dueDate.slice(5)}</span>
                ) : null}
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
