"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, Plus, Trash2, Building2, Users, ChevronRight, Search, Eye, EyeOff } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { Department, Employee } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";

export default function OrgPage() {
  const { me } = useSession();
  const isAdmin = me?.user.role === "OWNER" || me?.user.role === "ADMIN";

  const [departments, setDepartments] = useState<Department[] | null>(null);
  const [employees, setEmployees] = useState<Employee[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [newDept, setNewDept] = useState("");
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [depts, emps] = await Promise.all([api.listDepartments(), api.listEmployees()]);
      setDepartments(depts);
      setEmployees(emps);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load the org");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function createDept(e: React.FormEvent) {
    e.preventDefault();
    if (!newDept.trim()) return;
    setCreating(true);
    try {
      await api.createDepartment({ name: newDept.trim() });
      setNewDept("");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create department");
    } finally {
      setCreating(false);
    }
  }

  async function removeDept(id: string) {
    await api.deleteDepartment(id);
    void load();
  }

  const loading = departments === null || employees === null;

  return (
    <div>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Org structure</h1>
          <p className="mt-1 text-fg/50">Departments and reporting lines.</p>
        </div>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {loading ? (
        <Card className="mt-8"><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>
      ) : (
        // Stacked rather than side by side. The chart draws cards in rows now, so half a width
        // wrapped it after two siblings — the shape of the org is the point of this screen, and
        // showing a shape needs the room.
        <div className="mt-8 flex flex-col gap-8">
          {/* Departments */}
          <div>
            <h2 className="flex items-center gap-2 text-sm font-medium uppercase tracking-wide text-fg/40">
              <Building2 className="h-4 w-4" /> Departments
            </h2>
            {isAdmin && (
              <form onSubmit={createDept} className="mt-3 flex gap-2">
                <Input placeholder="New department name" value={newDept} onChange={(e) => setNewDept(e.target.value)} />
                <Button type="submit" disabled={creating}>
                  {creating ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />} Add
                </Button>
              </form>
            )}
            {/* Full width now, so departments read across rather than as one tall column. */}
            <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {departments!.length === 0 ? (
                <Card className="text-sm text-fg/50">No departments yet.</Card>
              ) : (
                departments!.map((d) => (
                  <Card key={d.id} className="flex items-center justify-between p-4">
                    <div>
                      <p className="font-medium">{d.name}</p>
                      <p className="text-xs text-fg/50">
                        <Users className="mr-1 inline h-3 w-3" />{d.memberCount} member{d.memberCount === 1 ? "" : "s"}
                        {d.leadName && <> · led by {d.leadName}</>}
                      </p>
                    </div>
                    {isAdmin && (
                      <button onClick={() => removeDept(d.id)} aria-label={`Delete ${d.name}`}
                        className="rounded-md p-1.5 text-red-400/70 hover:bg-fg/5 hover:text-red-300">
                        <Trash2 className="h-4 w-4" />
                      </button>
                    )}
                  </Card>
                ))
              )}
            </div>
          </div>

          {/* Reporting tree */}
          <div>
            <h2 className="flex items-center gap-2 text-sm font-medium uppercase tracking-wide text-fg/40">
              <Users className="h-4 w-4" /> Reporting structure
            </h2>
            <Card className="mt-3">
              <OrgTree employees={employees!} departments={departments!} myUserId={me?.user.id} />
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * The reporting tree, with a real expansion model.
 *
 * It used to render every node in the company, always: the chevron on each row was a static icon with
 * no click handler and no state behind it anywhere. Nothing was failing to close — nothing had ever
 * been openable. At a thousand people that also meant a thousand rows drawn on load.
 *
 * Three things decide what you see:
 *
 *  - **Your line opens by default.** The path from the top of the company down to you is expanded and
 *    your own reports are showing; everything else is shut. An org chart is read from where you stand,
 *    and an intern should not have to hunt four levels down to find themselves.
 *  - **A collapsed row says how many people are under it**, because that is what decides whether
 *    opening it is worth the click.
 *  - **A collapsed row renders none of its subtree.** That is what keeps this cheap at a thousand
 *    people, and it is why offering the whole-company view costs nothing until someone opens a branch.
 */
function OrgTree({
  employees,
  departments,
  myUserId,
}: {
  employees: Employee[];
  departments: Department[];
  myUserId?: string;
}) {
  const childrenOf = useMemo(() => {
    const map = new Map<string | null, Employee[]>();
    for (const e of employees) {
      const key = e.managerId ?? null;
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(e);
    }
    for (const list of map.values()) {
      list.sort((a, b) => `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`));
    }
    return map;
  }, [employees]);

  const byId = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees]);
  const me = useMemo(
    () => (myUserId ? employees.find((e) => e.userId === myUserId) ?? null : null),
    [employees, myUserId],
  );

  /** Me, and every manager above me. Walked with a visited set: a cycle must not hang the page. */
  const myLine = useMemo(() => {
    const ids = new Set<string>();
    let cursor: Employee | null = me;
    while (cursor && !ids.has(cursor.id)) {
      ids.add(cursor.id);
      cursor = cursor.managerId ? byId.get(cursor.managerId) ?? null : null;
    }
    return ids;
  }, [me, byId]);

  // Memoised, and not for tidiness. A fresh array each render makes `topLevel` a new Set each
  // render, which makes the effect below fire on every render and wipe the expansion state — every
  // chevron click undone the instant it happened. The lint rule caught a real bug here.
  const roots = useMemo(() => childrenOf.get(null) ?? [], [childrenOf]);
  const topLevel = useMemo(() => new Set(roots.map((r) => r.id)), [roots]);

  const [mode, setMode] = useState<"mine" | "all">(me ? "mine" : "all");
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set(me ? myLine : topLevel));
  const [query, setQuery] = useState("");

  // Switching mode resets what is open rather than layering one view's state onto the other's. Two
  // modes that quietly share expansion state stop being two modes and become one confusing one.
  useEffect(() => {
    setExpanded(new Set(mode === "mine" ? myLine : topLevel));
  }, [mode, myLine, topLevel]);

  const term = query.trim().toLowerCase();
  const matches = useMemo(() => {
    if (term.length < 2) return null;
    const hit = new Set<string>();
    for (const e of employees) {
      const hay = `${e.firstName} ${e.lastName} ${e.email} ${e.jobTitle ?? ""}`.toLowerCase();
      if (hay.includes(term)) hit.add(e.id);
    }
    return hit;
  }, [employees, term]);

  // A search that only highlights is useless when the match sits four collapsed levels down, so every
  // match's managers are opened. Derived rather than written into state: typing must not permanently
  // rearrange what someone had open, and clearing the box must put it back exactly as it was.
  const openNow = useMemo(() => {
    if (!matches || matches.size === 0) return expanded;
    const open = new Set(expanded);
    for (const id of matches) {
      let cursor: Employee | null = byId.get(id) ?? null;
      const seen = new Set<string>();
      while (cursor && !seen.has(cursor.id)) {
        seen.add(cursor.id);
        if (cursor.managerId) open.add(cursor.managerId);
        cursor = cursor.managerId ? byId.get(cursor.managerId) ?? null : null;
      }
    }
    return open;
  }, [matches, expanded, byId]);

  const toggle = useCallback((id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const deptName = (id: string | null) => departments.find((d) => d.id === id)?.name;

  if (roots.length === employees.length) {
    return (
      <p className="text-sm text-fg/50">
        No reporting lines set yet. Assign managers on the directory to build the chart.
      </p>
    );
  }

  // In "my line" mode only the branch containing you is drawn from the top. A search overrides that,
  // because looking for someone you do not report to is the ordinary reason to search.
  const shown = mode === "mine" && me && !matches ? roots.filter((r) => myLine.has(r.id)) : roots;

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg/30" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Find a person…"
            className="pl-9"
            aria-label="Find a person in the org chart"
          />
        </div>
        {me && (
          <Button
            type="button"
            variant={mode === "all" ? "secondary" : "ghost"}
            size="sm"
            onClick={() => setMode((m) => (m === "mine" ? "all" : "mine"))}
            aria-pressed={mode === "all"}
          >
            {mode === "all" ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            {mode === "all" ? "My line" : "Whole org"}
          </Button>
        )}
      </div>

      {matches && (
        <p className="mb-2 text-xs text-fg/40">
          {matches.size === 0
            ? "Nobody matches that."
            : `${matches.size} ${matches.size === 1 ? "person" : "people"} matched — their managers are opened below.`}
        </p>
      )}

      {/* The chart is as wide as the widest open level, which at a thousand people is wider than any
          screen. It scrolls in its own box so the page around it never does. */}
      <div className="-mx-2 overflow-x-auto px-2 pb-2">
        {/* w-max, not just min-w-full: in a horizontal scroller justify-center on a child wider
            than its container clips the left overflow, and those cards can never be scrolled to. */}
        <div className="flex w-max min-w-full justify-center gap-6 pt-1">
          {shown.map((e) => (
            <OrgNode
              key={e.id}
              employee={e}
              childrenOf={childrenOf}
              deptName={deptName}
              expanded={openNow}
              onToggle={toggle}
              meId={me?.id}
              matches={matches}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

/** Stable per-department colour, so a branch reads as one team at a glance. */
const DEPT_TINTS = [
  "border-t-sky-400/70",
  "border-t-emerald-400/70",
  "border-t-amber-400/70",
  "border-t-violet/70",
  "border-t-rose-400/70",
  "border-t-teal-400/70",
];

function tintFor(departmentId: string | null) {
  if (!departmentId) return "border-t-fg/20";
  let h = 0;
  for (let i = 0; i < departmentId.length; i++) h = (h * 31 + departmentId.charCodeAt(i)) >>> 0;
  return DEPT_TINTS[h % DEPT_TINTS.length];
}

/**
 * One person, drawn as a card with their branch hanging beneath them.
 *
 * <p>Top-down rather than an indented list. An indented list is compact and reads as a file tree; a
 * chart like this reads as an organisation, which is what people expect when they ask to see one —
 * and it makes siblings visibly siblings instead of two rows at the same left margin.
 *
 * <p>The connectors are three plain divs per level: a stem down from the parent, one horizontal rule
 * spanning the children, and a stem down into each child. The first and last child clip that rule to
 * half width so it starts and stops under a card rather than hanging in the air.
 */
function OrgNode({
  employee,
  childrenOf,
  deptName,
  expanded,
  onToggle,
  meId,
  matches,
}: {
  employee: Employee;
  childrenOf: Map<string | null, Employee[]>;
  deptName: (id: string | null) => string | undefined;
  expanded: Set<string>;
  onToggle: (id: string) => void;
  meId?: string;
  matches: Set<string> | null;
}) {
  const reports = childrenOf.get(employee.id) ?? [];
  const hasReports = reports.length > 0;
  const isOpen = hasReports && expanded.has(employee.id);
  const isMe = employee.id === meId;
  const isMatch = matches?.has(employee.id) ?? false;
  const department = deptName(employee.departmentId);

  return (
    <div className="flex flex-col items-center">
      <div
        className={cn(
          "w-[232px] shrink-0 rounded-lg border border-fg/10 border-t-[3px] bg-surface p-3 shadow-sm transition-colors",
          tintFor(employee.departmentId),
          isMe && "ring-2 ring-violet",
          isMatch && !isMe && "ring-2 ring-amber-400/70",
        )}
      >
        <div className="flex items-start gap-2.5">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-violet/20 text-xs font-semibold text-violet">
            {employee.firstName[0]}{employee.lastName[0]}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium leading-tight">
              {employee.firstName} {employee.lastName}
              {isMe && <span className="ml-1.5 text-[11px] font-normal text-violet">you</span>}
            </p>
            <p className="truncate text-xs text-fg/50">{employee.jobTitle ?? "—"}</p>
          </div>
        </div>

        <div className="mt-2.5 flex flex-col gap-0.5 border-t border-fg/5 pt-2 text-[11px] text-fg/45">
          {department && <p className="truncate">{department}</p>}
          <p className="truncate">{employee.email}</p>
          {employee.workLocation && <p className="truncate">{employee.workLocation}</p>}
        </div>

        {hasReports && (
          <button
            type="button"
            onClick={() => onToggle(employee.id)}
            aria-expanded={isOpen}
            aria-label={`${isOpen ? "Collapse" : "Expand"} the team under ${employee.firstName} ${employee.lastName}`}
            className="mt-2 flex w-full items-center justify-center gap-1.5 rounded-md bg-fg/5 py-1 text-[11px] text-fg/60 hover:bg-fg/10 hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
          >
            <span className="tabular-nums">{reports.length}</span>
            <span>{reports.length === 1 ? "report" : "reports"}</span>
            <ChevronRight className={cn("h-3.5 w-3.5 transition-transform", isOpen && "rotate-90")} />
          </button>
        )}
      </div>

      {isOpen && (
        <>
          {/* Stem out of the bottom of this card. */}
          <div className="h-5 w-px bg-fg/15" />
          <div className="flex items-start">
            {reports.map((r, i) => (
              <div key={r.id} className="relative flex flex-col items-center px-3">
                {/* The rule joining the siblings. One child needs none; the outermost two are clipped
                    to half so the line begins and ends under a card. */}
                {reports.length > 1 && (
                  <div
                    className={cn(
                      "absolute top-0 h-px bg-fg/15",
                      i === 0 ? "left-1/2 right-0" : i === reports.length - 1 ? "left-0 right-1/2" : "left-0 right-0",
                    )}
                  />
                )}
                <div className="h-5 w-px bg-fg/15" />
                <OrgNode
                  employee={r}
                  childrenOf={childrenOf}
                  deptName={deptName}
                  expanded={expanded}
                  onToggle={onToggle}
                  meId={meId}
                  matches={matches}
                />
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
