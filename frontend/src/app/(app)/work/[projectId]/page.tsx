"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Loader2, Plus, ChevronLeft, ChevronRight, Trash2, CalendarDays } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Employee, Project, Task, TaskStatusT } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";

const COLUMNS: { status: TaskStatusT; label: string }[] = [
  { status: "TODO", label: "To do" },
  { status: "IN_PROGRESS", label: "In progress" },
  { status: "DONE", label: "Done" },
];
const ORDER: TaskStatusT[] = ["TODO", "IN_PROGRESS", "DONE"];
const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"] as const;
const priorityChip: Record<string, string> = {
  LOW: "bg-white/10 text-white/50",
  MEDIUM: "bg-sky-500/15 text-sky-300",
  HIGH: "bg-amber-500/15 text-amber-300",
  URGENT: "bg-red-500/15 text-red-300",
};
const selectCls =
  "h-11 w-full rounded-lg border border-white/15 bg-white/5 px-3 text-sm text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

export default function BoardPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [tasks, setTasks] = useState<Task[] | null>(null);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [detail, setDetail] = useState<Task | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [p, t, emps] = await Promise.all([api.getProject(projectId), api.listTasks(projectId), api.listEmployees()]);
      setProject(p);
      setTasks(t);
      setEmployees(emps);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load the board");
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function move(task: Task, dir: -1 | 1) {
    const idx = ORDER.indexOf(task.status) + dir;
    if (idx < 0 || idx >= ORDER.length) return;
    await api.updateTask(task.id, { status: ORDER[idx] });
    void load();
  }

  if (error) return <Alert tone="error">{error}</Alert>;
  if (project === null || tasks === null) {
    return <Card><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>;
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <Link href="/work" className="text-sm text-white/50 hover:text-white">← Projects</Link>
          <h1 className="mt-1 flex items-center gap-2 text-2xl font-semibold tracking-tight">
            <span className="rounded-md bg-violet/20 px-2 py-0.5 text-sm font-semibold text-violet">{project.key}</span>
            {project.name}
          </h1>
        </div>
        <Button onClick={() => setAdding(true)}><Plus className="h-4 w-4" /> Add task</Button>
      </div>

      <div className="mt-8 grid gap-4 md:grid-cols-3">
        {COLUMNS.map((col) => {
          const items = tasks.filter((t) => t.status === col.status);
          return (
            <div key={col.status} className="rounded-2xl border border-white/10 bg-white/[0.02] p-3">
              <div className="mb-3 flex items-center justify-between px-1">
                <h2 className="text-sm font-medium text-white/70">{col.label}</h2>
                <span className="text-xs text-white/30">{items.length}</span>
              </div>
              <div className="flex flex-col gap-2">
                {items.map((t) => (
                  <Card key={t.id} className="cursor-pointer p-3 hover:border-white/20" onClick={() => setDetail(t)}>
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-medium text-white/40">{t.ref}</span>
                      <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${priorityChip[t.priority]}`}>
                        {t.priority.toLowerCase()}
                      </span>
                    </div>
                    <p className={`mt-1.5 text-sm ${t.status === "DONE" ? "text-white/50 line-through" : ""}`}>{t.title}</p>
                    <div className="mt-3 flex items-center justify-between">
                      <div className="flex items-center gap-2 text-xs text-white/40">
                        {t.assigneeName ? (
                          <span className="flex h-5 w-5 items-center justify-center rounded-full bg-aqua/20 text-[9px] font-semibold text-aqua">
                            {t.assigneeName.split(" ").map((n) => n[0]).join("").slice(0, 2)}
                          </span>
                        ) : (
                          <span className="text-white/25">Unassigned</span>
                        )}
                        {t.dueDate && <span className="flex items-center gap-1"><CalendarDays className="h-3 w-3" />{t.dueDate}</span>}
                      </div>
                      <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                        <button disabled={t.status === "TODO"} onClick={() => move(t, -1)}
                          className="rounded p-0.5 text-white/30 hover:text-white disabled:opacity-20" aria-label="Move left">
                          <ChevronLeft className="h-4 w-4" />
                        </button>
                        <button disabled={t.status === "DONE"} onClick={() => move(t, 1)}
                          className="rounded p-0.5 text-white/30 hover:text-white disabled:opacity-20" aria-label="Move right">
                          <ChevronRight className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  </Card>
                ))}
                {items.length === 0 && <p className="px-1 py-4 text-center text-xs text-white/25">Nothing here</p>}
              </div>
            </div>
          );
        })}
      </div>

      {adding && (
        <TaskDialog title="Add task" employees={employees}
          onClose={() => setAdding(false)}
          onSubmit={async (data) => { await api.createTask(projectId, data); setAdding(false); void load(); }} />
      )}
      {detail && (
        <TaskDetailDialog task={detail} employees={employees}
          onClose={() => setDetail(null)}
          onSaved={() => { setDetail(null); void load(); }}
          onDeleted={() => { setDetail(null); void load(); }} />
      )}
    </div>
  );
}

interface TaskForm {
  title: string;
  description: string;
  priority: string;
  assigneeId: string;
  dueDate: string;
}

function TaskDialog({
  title, employees, initial, onClose, onSubmit, onDelete,
}: {
  title: string;
  employees: Employee[];
  initial?: Partial<TaskForm>;
  onClose: () => void;
  onSubmit: (data: TaskForm) => Promise<void>;
  onDelete?: () => Promise<void>;
}) {
  const [form, setForm] = useState<TaskForm>({
    title: initial?.title ?? "",
    description: initial?.description ?? "",
    priority: initial?.priority ?? "MEDIUM",
    assigneeId: initial?.assigneeId ?? "",
    dueDate: initial?.dueDate ?? "",
  });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof TaskForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!form.title.trim()) { setError("Enter a title."); return; }
    setBusy(true);
    try {
      await onSubmit(form);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save");
      setBusy(false);
    }
  }

  return (
    <Modal open onClose={onClose} title={title}>
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Title" htmlFor="t-title">
          <Input id="t-title" value={form.title} onChange={set("title")} />
        </Field>
        <Field label="Description" htmlFor="t-desc">
          <Input id="t-desc" value={form.description} onChange={set("description")} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Priority" htmlFor="t-pri">
            <select id="t-pri" className={selectCls} value={form.priority} onChange={set("priority")}>
              {PRIORITIES.map((p) => <option key={p} value={p} className="bg-ink">{p.toLowerCase()}</option>)}
            </select>
          </Field>
          <Field label="Due date" htmlFor="t-due">
            <Input id="t-due" type="date" value={form.dueDate} onChange={set("dueDate")} />
          </Field>
        </div>
        <Field label="Assignee" htmlFor="t-assignee">
          <select id="t-assignee" className={selectCls} value={form.assigneeId} onChange={set("assigneeId")}>
            <option value="" className="bg-ink">Unassigned</option>
            {employees.map((e) => <option key={e.id} value={e.id} className="bg-ink">{e.firstName} {e.lastName}</option>)}
          </select>
        </Field>
        <div className="mt-2 flex items-center justify-between gap-2">
          {onDelete ? (
            <button type="button" onClick={() => onDelete()}
              className="inline-flex items-center gap-1 text-sm text-red-400/80 hover:text-red-300">
              <Trash2 className="h-4 w-4" /> Delete
            </button>
          ) : <span />}
          <div className="flex gap-2">
            <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
            <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Save</Button>
          </div>
        </div>
      </form>
    </Modal>
  );
}

function TaskDetailDialog({
  task, employees, onClose, onSaved, onDeleted,
}: {
  task: Task;
  employees: Employee[];
  onClose: () => void;
  onSaved: () => void;
  onDeleted: () => void;
}) {
  return (
    <TaskDialog
      title={`${task.ref} · edit`}
      employees={employees}
      initial={{
        title: task.title,
        description: task.description ?? "",
        priority: task.priority,
        assigneeId: task.assigneeId ?? "",
        dueDate: task.dueDate ?? "",
      }}
      onClose={onClose}
      onSubmit={async (data) => {
        await api.updateTask(task.id, data);
        onSaved();
      }}
      onDelete={async () => {
        await api.deleteTask(task.id);
        onDeleted();
      }}
    />
  );
}
