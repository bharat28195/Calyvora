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
        <div className="mt-8 grid gap-6 lg:grid-cols-2">
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
            <div className="mt-3 flex flex-col gap-2">
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
      <div className="mb-3 flex flex-wrap items-center gap-2">
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

      <ul className="flex flex-col gap-0.5">
        {shown.map((e) => (
          <OrgNode
            key={e.id}
            employee={e}
            childrenOf={childrenOf}
            deptName={deptName}
            depth={0}
            expanded={openNow}
            onToggle={toggle}
            meId={me?.id}
            matches={matches}
          />
        ))}
      </ul>
    </div>
  );
}

function OrgNode({
  employee,
  childrenOf,
  deptName,
  depth,
  expanded,
  onToggle,
  meId,
  matches,
  isLast,
}: {
  employee: Employee;
  childrenOf: Map<string | null, Employee[]>;
  deptName: (id: string | null) => string | undefined;
  depth: number;
  expanded: Set<string>;
  onToggle: (id: string) => void;
  meId?: string;
  matches: Set<string> | null;
  isLast?: boolean;
}) {
  const reports = childrenOf.get(employee.id) ?? [];
  const hasReports = reports.length > 0;
  const isOpen = hasReports && expanded.has(employee.id);
  const isMe = employee.id === meId;
  const isMatch = matches?.has(employee.id) ?? false;

  return (
    // The connector lines are drawn in CSS rather than with indentation, because indentation alone
    // stops reading as a hierarchy past about two levels — at four you are counting pixels to work
    // out who reports to whom. The vertical rule runs the height of a branch and stops halfway down
    // its last child, which is what turns a stack of rows into a tree.
    <li
      className={cn(
        "relative",
        depth > 0 && "pl-5",
        depth > 0 && "before:absolute before:left-0 before:top-0 before:w-px before:bg-fg/15 before:content-['']",
        depth > 0 && (isLast ? "before:h-[18px]" : "before:h-full"),
        depth > 0 && "after:absolute after:left-0 after:top-[18px] after:h-px after:w-3.5 after:bg-fg/15 after:content-['']",
      )}
    >
      <div
        className={cn(
          "flex items-center gap-2 rounded-md py-1 pr-2",
          isMe && "bg-violet/10",
          isMatch && !isMe && "bg-amber-400/10",
        )}
      >
        {hasReports ? (
          <button
            type="button"
            onClick={() => onToggle(employee.id)}
            aria-expanded={isOpen}
            aria-label={`${isOpen ? "Collapse" : "Expand"} the team under ${employee.firstName} ${employee.lastName}`}
            className="flex h-5 w-5 shrink-0 items-center justify-center rounded text-fg/40 hover:bg-fg/10 hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
          >
            <ChevronRight className={cn("h-3.5 w-3.5 transition-transform", isOpen && "rotate-90")} />
          </button>
        ) : (
          // A dot rather than an empty box, so names still line up under their siblings.
          <span className="flex h-5 w-5 shrink-0 items-center justify-center text-fg/20">·</span>
        )}
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-violet/20 text-[10px] font-semibold text-violet">
          {employee.firstName[0]}{employee.lastName[0]}
        </span>
        <span className="truncate text-sm">
          {employee.firstName} {employee.lastName}
          {isMe && <span className="ml-1.5 text-xs text-violet">you</span>}
        </span>
        <span className="truncate text-xs text-fg/40">
          {employee.jobTitle ?? "—"}
          {deptName(employee.departmentId) ? ` · ${deptName(employee.departmentId)}` : ""}
        </span>
        {hasReports && !isOpen && (
          // What the click will cost you. Direct reports, not the whole branch: a badge reading 30
          // that opens to reveal four rows teaches people to distrust the badge.
          <span className="ml-auto shrink-0 rounded-full bg-fg/5 px-2 py-0.5 text-[11px] tabular-nums text-fg/40">
            {reports.length}
          </span>
        )}
      </div>
      {isOpen && (
        // ml-2.5 puts a child's trunk under the middle of its parent's chevron, so the line appears
        // to come out of the control that opened it.
        <ul className="ml-2.5 flex flex-col gap-0.5">
          {reports.map((r, i) => (
            <OrgNode
              key={r.id}
              employee={r}
              childrenOf={childrenOf}
              deptName={deptName}
              depth={depth + 1}
              expanded={expanded}
              onToggle={onToggle}
              meId={meId}
              matches={matches}
              isLast={i === reports.length - 1}
            />
          ))}
        </ul>
      )}
    </li>
  );
}
