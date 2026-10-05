"use client";

import { Star } from "lucide-react";
import type { Employee } from "@/lib/types";

/** Richer profile: skills and rating (founder feedback C4–C5). The "working on" list went with the work tracker. */
export function EmployeeProfileExtras({ employee }: { employee: Employee }) {
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

    </div>
  );
}
