"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, Pencil, Search, Mail, Phone, MapPin, Briefcase, Building2, ListChecks, Plus, Trash2, CheckCircle2, Circle, Wallet } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { Department, Employee, OnboardingTask } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Modal } from "@/components/ui/modal";
import { EmployeeCompensation } from "@/components/people/employee-compensation";
import { EmployeeProfileExtras } from "@/components/people/employee-profile-extras";
import { EmployeeGoals } from "@/components/people/employee-goals";

const TYPES = ["FULL_TIME", "PART_TIME", "CONTRACT", "INTERN"] as const;
const STATUSES = ["ONBOARDING", "ACTIVE", "TERMINATED"] as const;
const typeLabel = (t: string | null) => (t ? t.replace(/_/g, " ").toLowerCase() : "—");

export default function PeoplePage() {
  const { me } = useSession();
  const isAdmin = me?.user.role === "OWNER" || me?.user.role === "ADMIN";

  const [employees, setEmployees] = useState<Employee[] | null>(null);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<Employee | null>(null);
  const [viewing, setViewing] = useState<Employee | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [emps, depts] = await Promise.all([api.listEmployees(), api.listDepartments()]);
      setEmployees(emps);
      setDepartments(depts);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load the directory");
    }
  }, []);

  const deptName = useCallback(
    (id: string | null) => departments.find((d) => d.id === id)?.name ?? null,
    [departments],
  );

  useEffect(() => {
    void load();
  }, [load]);

  const filtered = useMemo(() => {
    if (!employees) return [];
    const q = query.trim().toLowerCase();
    if (!q) return employees;
    return employees.filter((e) =>
      [`${e.firstName} ${e.lastName}`, e.email, e.jobTitle ?? "", e.workLocation ?? ""]
        .join(" ")
        .toLowerCase()
        .includes(q),
    );
  }, [employees, query]);

  const canEdit = (e: Employee) => isAdmin || e.userId === me?.user.id;

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">People</h1>
          <p className="mt-1 flex flex-wrap gap-x-3 text-fg/50">
            Your company directory.
            <a href="/people/org" className="text-violet hover:underline">Org chart →</a>
            <a href="/people/time-off" className="text-violet hover:underline">Time off →</a>
          </p>
        </div>
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg/30" />
          <Input
            className="w-64 pl-9"
            placeholder="Search name, email, title…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search people"
          />
        </div>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {employees === null ? (
        <Card className="mt-8"><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>
      ) : filtered.length === 0 ? (
        <Card className="mt-8 text-center text-fg/50">No people match &ldquo;{query}&rdquo;.</Card>
      ) : (
        <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((e) => (
            <Card key={e.id} className="flex cursor-pointer flex-col gap-3 transition-colors hover:border-fg/20"
              onClick={() => setViewing(e)}>
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-violet/20 text-sm font-semibold text-violet">
                    {e.firstName[0]}{e.lastName[0]}
                  </div>
                  <div>
                    <p className="font-medium leading-tight">{e.firstName} {e.lastName}</p>
                    <p className="text-xs text-fg/50">{e.jobTitle ?? "No title yet"}</p>
                  </div>
                </div>
                {canEdit(e) && (
                  <button onClick={(ev) => { ev.stopPropagation(); setEditing(e); }} aria-label={`Edit ${e.firstName}`}
                    className="rounded-md p-1.5 text-fg/40 hover:bg-fg/5 hover:text-fg">
                    <Pencil className="h-4 w-4" />
                  </button>
                )}
              </div>

              <div className="flex flex-wrap gap-1.5">
                <Badge value={e.role} />
                <Badge value={e.employmentStatus} />
                {e.employmentType && (
                  <span className="rounded-full bg-fg/10 px-2 py-0.5 text-xs capitalize text-fg/70">
                    {typeLabel(e.employmentType)}
                  </span>
                )}
              </div>

              <dl className="flex flex-col gap-1.5 text-sm text-fg/60">
                <div className="flex items-center gap-2"><Mail className="h-3.5 w-3.5 text-fg/30" />{e.email}</div>
                {deptName(e.departmentId) && <div className="flex items-center gap-2"><Building2 className="h-3.5 w-3.5 text-fg/30" />{deptName(e.departmentId)}</div>}
                {e.phone && <div className="flex items-center gap-2"><Phone className="h-3.5 w-3.5 text-fg/30" />{e.phone}</div>}
                {e.workLocation && <div className="flex items-center gap-2"><MapPin className="h-3.5 w-3.5 text-fg/30" />{e.workLocation}</div>}
                {e.startDate && <div className="flex items-center gap-2"><Briefcase className="h-3.5 w-3.5 text-fg/30" />Started {e.startDate}</div>}
              </dl>
            </Card>
          ))}
        </div>
      )}

      {editing && (
        <EditEmployeeDialog
          employee={editing}
          admin={isAdmin}
          departments={departments}
          coworkers={(employees ?? []).filter((c) => c.id !== editing.id)}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void load();
          }}
        />
      )}

      {viewing && (
        <EmployeeDetailModal
          employee={viewing}
          admin={isAdmin}
          isSelf={viewing.userId === me?.user.id}
          departmentName={deptName(viewing.departmentId)}
          onClose={() => setViewing(null)}
        />
      )}
    </div>
  );
}

function EmployeeDetailModal({
  employee,
  admin,
  isSelf,
  departmentName,
  onClose,
}: {
  employee: Employee;
  admin: boolean;
  isSelf: boolean;
  departmentName: string | null;
  onClose: () => void;
}) {
  const [tasks, setTasks] = useState<OnboardingTask[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [newTask, setNewTask] = useState("");
  const canManage = admin;
  const canToggle = admin || isSelf;

  const load = useCallback(async () => {
    try {
      setTasks(await api.listOnboarding(employee.id));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load onboarding");
    }
  }, [employee.id]);

  useEffect(() => {
    void load();
  }, [load]);

  const done = tasks?.filter((t) => t.completed).length ?? 0;
  const total = tasks?.length ?? 0;

  async function toggle(t: OnboardingTask) {
    await api.toggleOnboardingTask(t.id, !t.completed);
    void load();
  }
  async function add(e: React.FormEvent) {
    e.preventDefault();
    if (!newTask.trim()) return;
    await api.addOnboardingTask(employee.id, newTask.trim());
    setNewTask("");
    void load();
  }

  return (
    <Modal open onClose={onClose} title={`${employee.firstName} ${employee.lastName}`}>
      <div className="flex flex-wrap gap-1.5">
        <Badge value={employee.role} />
        <Badge value={employee.employmentStatus} />
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
        <Detail label="Title" value={employee.jobTitle} />
        <Detail label="Department" value={departmentName} />
        <Detail label="Email" value={employee.email} />
        <Detail label="Phone" value={employee.phone} />
        <Detail label="Location" value={employee.workLocation} />
        <Detail label="Employee no." value={employee.employeeNo} />
        <Detail label="Type" value={employee.employmentType ? typeLabel(employee.employmentType) : null} />
        <Detail label="Started" value={employee.startDate} />
        <Detail label="Ends" value={employee.endDate} />
      </dl>

      <EmployeeProfileExtras employee={employee} />

      <EmployeeGoals employeeId={employee.id} canEdit={admin || isSelf} />

      {admin && (
        <div className="mt-6">
          <h3 className="mb-3 flex items-center gap-1.5 text-sm font-medium text-fg/80">
            <Wallet className="h-4 w-4 text-emerald-400" /> Compensation
          </h3>
          <EmployeeCompensation employeeId={employee.id} />
        </div>
      )}

      <div className="mt-6">
        <div className="flex items-center justify-between">
          <h3 className="flex items-center gap-1.5 text-sm font-medium text-fg/80">
            <ListChecks className="h-4 w-4 text-aqua" /> Onboarding
          </h3>
          {total > 0 && <span className="text-xs text-fg/40">{done}/{total} done</span>}
        </div>
        {error && <Alert tone="error" className="mt-2">{error}</Alert>}

        {tasks === null ? (
          <Loader2 className="mt-3 h-5 w-5 animate-spin text-violet" />
        ) : tasks.length === 0 ? (
          <div className="mt-3 rounded-lg border border-fg/10 bg-fg/5 p-3 text-sm text-fg/50">
            No onboarding tasks yet.
            {canManage && (
              <button onClick={() => api.seedOnboardingDefaults(employee.id).then(load)}
                className="ml-2 text-violet hover:underline">Add default checklist</button>
            )}
          </div>
        ) : (
          <ul className="mt-3 flex flex-col gap-1.5">
            {tasks.map((t) => (
              <li key={t.id} className="flex items-center gap-2">
                <button disabled={!canToggle} onClick={() => toggle(t)} className="disabled:opacity-40">
                  {t.completed ? <CheckCircle2 className="h-5 w-5 text-emerald-400" /> : <Circle className="h-5 w-5 text-fg/30" />}
                </button>
                <span className={`flex-1 text-sm ${t.completed ? "text-fg/40 line-through" : "text-fg/80"}`}>{t.title}</span>
                {canManage && (
                  <button onClick={() => api.deleteOnboardingTask(t.id).then(load)} aria-label="Delete task"
                    className="text-fg/30 hover:text-red-300">
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}

        {canManage && (
          <form onSubmit={add} className="mt-3 flex gap-2">
            <Input value={newTask} onChange={(e) => setNewTask(e.target.value)} placeholder="Add a task…" />
            <Button type="submit" variant="secondary"><Plus className="h-4 w-4" /></Button>
          </form>
        )}
      </div>
    </Modal>
  );
}

function Detail({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <dt className="text-xs text-fg/40">{label}</dt>
      <dd className="text-fg/80">{value || "—"}</dd>
    </div>
  );
}

function EditEmployeeDialog({
  employee,
  admin,
  departments,
  coworkers,
  onClose,
  onSaved,
}: {
  employee: Employee;
  admin: boolean;
  departments: Department[];
  coworkers: Employee[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState({
    jobTitle: employee.jobTitle ?? "",
    employeeNo: employee.employeeNo ?? "",
    employmentType: employee.employmentType ?? "",
    employmentStatus: employee.employmentStatus,
    workLocation: employee.workLocation ?? "",
    phone: employee.phone ?? "",
    startDate: employee.startDate ?? "",
    endDate: employee.endDate ?? "",
    skills: employee.skills.join(", "),
    rating: employee.rating ? String(employee.rating) : "0",
    departmentId: employee.departmentId ?? "",
    managerId: employee.managerId ?? "",
  });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (admin) {
        await api.updateEmployee(employee.id, {
          jobTitle: form.jobTitle,
          employeeNo: form.employeeNo,
          employmentType: (form.employmentType || null) as Employee["employmentType"],
          employmentStatus: form.employmentStatus,
          workLocation: form.workLocation,
          phone: form.phone,
          startDate: form.startDate,
          endDate: form.endDate,
          skills: form.skills.split(",").map((s) => s.trim()).filter(Boolean),
          rating: form.rating ? Number(form.rating) : null,
          departmentId: form.departmentId,
          managerId: form.managerId,
        });
      } else {
        await api.updateMyProfile({ phone: form.phone, workLocation: form.workLocation });
      }
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save");
    } finally {
      setBusy(false);
    }
  }

  const selectCls =
    "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

  return (
    <Modal open onClose={onClose} title={`Edit ${employee.firstName} ${employee.lastName}`}>
      <form onSubmit={save} className="flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        {!admin && <Alert tone="info">You can update your own contact details.</Alert>}

        {admin && (
          <>
            <Field label="Job title" htmlFor="jobTitle">
              <Input id="jobTitle" value={form.jobTitle} onChange={set("jobTitle")} />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Employee no." htmlFor="employeeNo">
                <Input id="employeeNo" value={form.employeeNo} onChange={set("employeeNo")} />
              </Field>
              <Field label="Start date" htmlFor="startDate">
                <Input id="startDate" type="date" value={form.startDate} onChange={set("startDate")} />
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="End date" htmlFor="endDate">
                <Input id="endDate" type="date" value={form.endDate} onChange={set("endDate")} />
              </Field>
              <Field label="Rating" htmlFor="rating">
                <select id="rating" className={selectCls} value={form.rating} onChange={set("rating")}>
                  <option value="0" className="bg-surface">Not rated</option>
                  {[1, 2, 3, 4, 5].map((n) => <option key={n} value={String(n)} className="bg-surface">{n} / 5</option>)}
                </select>
              </Field>
            </div>
            <Field label="Skills (comma-separated)" htmlFor="skills">
              <Input id="skills" value={form.skills} onChange={set("skills")} placeholder="e.g. React, TypeScript, Design" />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Employment type" htmlFor="employmentType">
                <select id="employmentType" className={selectCls} value={form.employmentType} onChange={set("employmentType")}>
                  <option value="" className="bg-surface">—</option>
                  {TYPES.map((t) => <option key={t} value={t} className="bg-surface">{typeLabel(t)}</option>)}
                </select>
              </Field>
              <Field label="Status" htmlFor="employmentStatus">
                <select id="employmentStatus" className={selectCls} value={form.employmentStatus} onChange={set("employmentStatus")}>
                  {STATUSES.map((s) => <option key={s} value={s} className="bg-surface">{s.toLowerCase()}</option>)}
                </select>
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Department" htmlFor="departmentId">
                <select id="departmentId" className={selectCls} value={form.departmentId} onChange={set("departmentId")}>
                  <option value="" className="bg-surface">—</option>
                  {departments.map((d) => <option key={d.id} value={d.id} className="bg-surface">{d.name}</option>)}
                </select>
              </Field>
              <Field label="Manager" htmlFor="managerId">
                <select id="managerId" className={selectCls} value={form.managerId} onChange={set("managerId")}>
                  <option value="" className="bg-surface">—</option>
                  {coworkers.map((c) => (
                    <option key={c.id} value={c.id} className="bg-surface">{c.firstName} {c.lastName}</option>
                  ))}
                </select>
              </Field>
            </div>
          </>
        )}

        <div className="grid grid-cols-2 gap-3">
          <Field label="Phone" htmlFor="phone">
            <Input id="phone" value={form.phone} onChange={set("phone")} />
          </Field>
          <Field label="Work location" htmlFor="workLocation">
            <Input id="workLocation" value={form.workLocation} onChange={set("workLocation")} />
          </Field>
        </div>

        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />} Save
          </Button>
        </div>
      </form>
    </Modal>
  );
}
