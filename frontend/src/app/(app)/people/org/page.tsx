"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, Plus, Trash2, Building2, Users, ChevronRight } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { Department, Employee } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

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
              <OrgTree employees={employees!} departments={departments!} />
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}

function OrgTree({ employees, departments }: { employees: Employee[]; departments: Department[] }) {
  const childrenOf = useMemo(() => {
    const map = new Map<string | null, Employee[]>();
    for (const e of employees) {
      const key = e.managerId ?? null;
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(e);
    }
    return map;
  }, [employees]);

  const deptName = (id: string | null) => departments.find((d) => d.id === id)?.name;
  const roots = childrenOf.get(null) ?? [];

  if (roots.length === employees.length) {
    return <p className="text-sm text-fg/50">No reporting lines set yet. Assign managers on the directory to build the chart.</p>;
  }

  return (
    <ul className="flex flex-col gap-1">
      {roots.map((e) => (
        <OrgNode key={e.id} employee={e} childrenOf={childrenOf} deptName={deptName} depth={0} />
      ))}
    </ul>
  );
}

function OrgNode({
  employee,
  childrenOf,
  deptName,
  depth,
}: {
  employee: Employee;
  childrenOf: Map<string | null, Employee[]>;
  deptName: (id: string | null) => string | undefined;
  depth: number;
}) {
  const reports = childrenOf.get(employee.id) ?? [];
  return (
    <li>
      <div className="flex items-center gap-2 rounded-md py-1" style={{ paddingLeft: depth * 20 }}>
        {reports.length > 0 ? <ChevronRight className="h-3.5 w-3.5 text-fg/30" /> : <span className="w-3.5" />}
        <span className="flex h-7 w-7 items-center justify-center rounded-full bg-violet/20 text-[10px] font-semibold text-violet">
          {employee.firstName[0]}{employee.lastName[0]}
        </span>
        <span className="text-sm">{employee.firstName} {employee.lastName}</span>
        <span className="text-xs text-fg/40">
          {employee.jobTitle ?? "—"}{deptName(employee.departmentId) ? ` · ${deptName(employee.departmentId)}` : ""}
        </span>
      </div>
      {reports.length > 0 && (
        <ul className="flex flex-col gap-1">
          {reports.map((r) => (
            <OrgNode key={r.id} employee={r} childrenOf={childrenOf} deptName={deptName} depth={depth + 1} />
          ))}
        </ul>
      )}
    </li>
  );
}
