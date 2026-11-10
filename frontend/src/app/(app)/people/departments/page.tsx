"use client";

import { useEffect, useMemo, useState } from "react";
import { Building2, ChevronDown, ChevronRight, Loader2, Search, Users } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Department, Employee } from "@/lib/types";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Input } from "@/components/ui/input";
import { LinkButton } from "@/components/ui/button";
import { useSession } from "@/hooks/useSession";
import { can } from "@/lib/permissions";

/**
 * Departments as a list: who leads each one, how many people are in it, and — one click — who they
 * are. The dashboard's Departments tile used to open the org chart, which answers "who reports to
 * whom", not "what departments do we have". Editing the structure stays on the org chart.
 */
export default function DepartmentsPage() {
  const { me } = useSession();
  const [departments, setDepartments] = useState<Department[] | null>(null);
  const [people, setPeople] = useState<Employee[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.listDepartments()
      .then((d) => setDepartments([...d].sort((a, b) => b.memberCount - a.memberCount)))
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load departments"));
  }, []);

  // The roster is fetched the first time someone opens a department, not on arrival: most visits
  // only want the counts.
  function toggle(id: string) {
    setOpen((cur) => (cur === id ? null : id));
    if (people === null) api.listEmployees().then(setPeople).catch(() => setPeople([]));
  }

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (departments ?? []).filter((d) => !q || d.name.toLowerCase().includes(q)
      || (d.leadName ?? "").toLowerCase().includes(q));
  }, [departments, query]);

  const total = (departments ?? []).reduce((n, d) => n + d.memberCount, 0);

  return (
    <div className="max-w-3xl">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Departments</h1>
          <p className="mt-1 text-fg/50">
            {departments ? `${departments.length} departments · ${total} people` : "Your company's teams."}
          </p>
        </div>
        {can(me, "PEOPLE_MANAGE") && <LinkButton href="/people/org" variant="secondary">Edit structure</LinkButton>}
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {departments && departments.length > 6 && (
        <div className="relative mt-6">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg/30" />
          <Input className="pl-9" placeholder="Find a department or lead…" value={query}
            onChange={(e) => setQuery(e.target.value)} aria-label="Find a department" />
        </div>
      )}

      {departments === null ? (
        <Card className="mt-6"><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></Card>
      ) : departments.length === 0 ? (
        <Card className="mt-6 text-center text-sm text-fg/50">No departments yet.</Card>
      ) : (
        <Card className="mt-6 divide-y divide-fg/5 p-0">
          {shown.map((d) => {
            const isOpen = open === d.id;
            const members = (people ?? []).filter((p) => p.departmentId === d.id);
            return (
              <div key={d.id}>
                <button onClick={() => toggle(d.id)}
                  className="flex w-full items-center gap-3 px-5 py-4 text-left hover:bg-fg/[0.03]">
                  {isOpen ? <ChevronDown className="h-4 w-4 text-fg/40" /> : <ChevronRight className="h-4 w-4 text-fg/40" />}
                  <Building2 className="h-4 w-4 text-aqua" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{d.name}</p>
                    <p className="truncate text-xs text-fg/50">{d.leadName ? `Led by ${d.leadName}` : "No lead set"}</p>
                  </div>
                  <span className="flex items-center gap-1 text-sm tabular-nums text-fg/60">
                    <Users className="h-4 w-4 text-fg/30" />{d.memberCount}
                  </span>
                </button>
                {isOpen && (
                  <div className="px-5 pb-4 pl-14">
                    {people === null ? (
                      <Loader2 className="h-4 w-4 animate-spin text-violet" />
                    ) : members.length === 0 ? (
                      <p className="text-sm text-fg/40">Nobody in this department yet.</p>
                    ) : (
                      <ul className="grid gap-2 sm:grid-cols-2">
                        {members.map((p) => (
                          <li key={p.id} className="flex items-center gap-2 text-sm">
                            <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-violet/15 text-xs font-semibold text-violet">
                              {p.firstName[0]}{p.lastName[0]}
                            </span>
                            <span className="min-w-0">
                              <span className="block truncate">{p.firstName} {p.lastName}</span>
                              <span className="block truncate text-xs text-fg/40">{p.jobTitle ?? "—"}</span>
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </Card>
      )}
    </div>
  );
}
