"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  Loader2, Plus, ChevronLeft, ChevronRight, Trash2, CalendarDays, LayoutGrid, ListTodo,
  Rocket, LifeBuoy, ArrowRight, Play, CheckCircle2,
} from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Employee, Project, Task, Sprint, Ticket, Board, TaskStatusT } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";
import { cn } from "@/lib/utils";

type View = "board" | "backlog" | "sprints" | "tickets";

const COLUMNS: { status: TaskStatusT; label: string }[] = [
  { status: "TODO", label: "To do" },
  { status: "IN_PROGRESS", label: "In progress" },
  { status: "DONE", label: "Done" },
];
const ORDER: TaskStatusT[] = ["TODO", "IN_PROGRESS", "DONE"];
const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"] as const;
const TICKET_STATUSES = ["OPEN", "PENDING", "RESOLVED", "CLOSED"] as const;
const priorityChip: Record<string, string> = {
  LOW: "bg-white/10 text-white/50",
  MEDIUM: "bg-sky-500/15 text-sky-300",
  HIGH: "bg-amber-500/15 text-amber-300",
  URGENT: "bg-red-500/15 text-red-300",
};
const sprintChip: Record<string, string> = {
  PLANNED: "bg-white/10 text-white/60",
  ACTIVE: "bg-emerald-500/15 text-emerald-300",
  COMPLETED: "bg-white/10 text-white/40",
};
const ticketChip: Record<string, string> = {
  OPEN: "bg-sky-500/15 text-sky-300",
  PENDING: "bg-amber-500/15 text-amber-300",
  RESOLVED: "bg-emerald-500/15 text-emerald-300",
  CLOSED: "bg-white/10 text-white/40",
};
const selectCls =
  "h-11 w-full rounded-lg border border-white/15 bg-white/5 px-3 text-sm text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

const NAV: { view: View; label: string; icon: typeof LayoutGrid }[] = [
  { view: "board", label: "Board", icon: LayoutGrid },
  { view: "backlog", label: "Backlog", icon: ListTodo },
  { view: "sprints", label: "Sprints", icon: Rocket },
  { view: "tickets", label: "Tickets", icon: LifeBuoy },
];

function initials(name: string) {
  return name.split(" ").map((n) => n[0]).join("").slice(0, 2);
}

export default function WorkspacePage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [view, setView] = useState<View>("board");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void Promise.all([api.getProject(projectId), api.listEmployees()])
      .then(([p, emps]) => { setProject(p); setEmployees(emps); })
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load project"));
  }, [projectId]);

  if (error) return <Alert tone="error">{error}</Alert>;
  if (!project) return <Card><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>;

  return (
    <div>
      <Link href="/work" className="text-sm text-white/50 hover:text-white">← Projects</Link>
      <h1 className="mt-1 flex items-center gap-2 text-2xl font-semibold tracking-tight">
        <span className="rounded-md bg-violet/20 px-2 py-0.5 text-sm font-semibold text-violet">{project.key}</span>
        {project.name}
      </h1>

      <div className="mt-6 grid gap-6 lg:grid-cols-[200px_1fr]">
        <aside className="space-y-1">
          {NAV.map((n) => (
            <button
              key={n.view}
              onClick={() => setView(n.view)}
              className={cn(
                "flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm transition-colors",
                view === n.view ? "bg-white/10 text-white" : "text-white/60 hover:bg-white/5 hover:text-white",
              )}
            >
              <n.icon className="h-4 w-4 shrink-0" />
              {n.label}
            </button>
          ))}
        </aside>

        <section className="min-w-0">
          {view === "board" && <BoardView projectId={projectId} employees={employees} />}
          {view === "backlog" && <BacklogView projectId={projectId} employees={employees} />}
          {view === "sprints" && <SprintsView projectId={projectId} />}
          {view === "tickets" && <TicketsView projectId={projectId} employees={employees} />}
        </section>
      </div>
    </div>
  );
}

// ============================ BOARD ============================

function BoardView({ projectId, employees }: { projectId: string; employees: Employee[] }) {
  const [board, setBoard] = useState<Board | null>(null);
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [detail, setDetail] = useState<Task | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [b, s] = await Promise.all([api.board(projectId), api.listSprints(projectId)]);
      setBoard(b);
      setSprints(s);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load the board");
    }
  }, [projectId]);

  useEffect(() => { void load(); }, [load]);

  async function move(task: Task, dir: -1 | 1) {
    const idx = ORDER.indexOf(task.status) + dir;
    if (idx < 0 || idx >= ORDER.length) return;
    await api.updateTask(task.id, { status: ORDER[idx] });
    void load();
  }

  if (error) return <Alert tone="error">{error}</Alert>;
  if (!board) return <Card><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>;

  const active = board.activeSprint;

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          {active ? (
            <div className="flex items-center gap-2">
              <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase", sprintChip.ACTIVE)}>Active sprint</span>
              <h2 className="text-lg font-semibold">{active.name}</h2>
              <span className="text-xs text-white/40">{active.doneCount}/{active.taskCount} done</span>
            </div>
          ) : (
            <div>
              <h2 className="text-lg font-semibold">Board</h2>
              <p className="text-xs text-white/40">No active sprint — showing the backlog. Start a sprint from the Sprints tab.</p>
            </div>
          )}
        </div>
        <Button onClick={() => setAdding(true)}><Plus className="h-4 w-4" /> Add task</Button>
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-3">
        {COLUMNS.map((col) => {
          const items = board.tasks.filter((t) => t.status === col.status);
          return (
            <div key={col.status} className="rounded-2xl border border-white/10 bg-white/[0.02] p-3">
              <div className="mb-3 flex items-center justify-between px-1">
                <h3 className="text-sm font-medium text-white/70">{col.label}</h3>
                <span className="text-xs text-white/30">{items.length}</span>
              </div>
              <div className="flex flex-col gap-2">
                {items.map((t) => (
                  <Card key={t.id} className="cursor-pointer p-3 hover:border-white/20" onClick={() => setDetail(t)}>
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-medium text-white/40">{t.ref}</span>
                      <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-medium", priorityChip[t.priority])}>
                        {t.priority.toLowerCase()}
                      </span>
                    </div>
                    <p className={cn("mt-1.5 text-sm", t.status === "DONE" && "text-white/50 line-through")}>{t.title}</p>
                    <div className="mt-3 flex items-center justify-between">
                      <div className="flex items-center gap-2 text-xs text-white/40">
                        {t.assigneeName ? (
                          <span className="flex h-5 w-5 items-center justify-center rounded-full bg-aqua/20 text-[9px] font-semibold text-aqua">
                            {initials(t.assigneeName)}
                          </span>
                        ) : <span className="text-white/25">Unassigned</span>}
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
        <TaskDialog title="Add task" employees={employees} sprints={sprints}
          onClose={() => setAdding(false)}
          onSubmit={async (data) => {
            const { sprintId, ...create } = data;
            const task = await api.createTask(projectId, create);
            if (sprintId) await api.updateTask(task.id, { sprintId });
            setAdding(false); void load();
          }} />
      )}
      {detail && (
        <TaskDetailDialog task={detail} employees={employees} sprints={sprints}
          onClose={() => setDetail(null)}
          onSaved={() => { setDetail(null); void load(); }}
          onDeleted={() => { setDetail(null); void load(); }} />
      )}
    </div>
  );
}

// ============================ BACKLOG ============================

function BacklogView({ projectId, employees }: { projectId: string; employees: Employee[] }) {
  const [tasks, setTasks] = useState<Task[] | null>(null);
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [detail, setDetail] = useState<Task | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [b, s] = await Promise.all([api.backlog(projectId), api.listSprints(projectId)]);
      setTasks(b);
      setSprints(s.filter((x) => x.status !== "COMPLETED"));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load the backlog");
    }
  }, [projectId]);

  useEffect(() => { void load(); }, [load]);

  if (error) return <Alert tone="error">{error}</Alert>;
  if (!tasks) return <Card><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>;

  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Backlog</h2>
          <p className="text-xs text-white/40">{tasks.length} un-sprinted {tasks.length === 1 ? "task" : "tasks"}. Pull work into a sprint to plan it.</p>
        </div>
        <Button onClick={() => setAdding(true)}><Plus className="h-4 w-4" /> Add task</Button>
      </div>

      <div className="mt-6 space-y-2">
        {tasks.length === 0 ? (
          <Card className="py-10 text-center text-sm text-white/40">The backlog is empty.</Card>
        ) : tasks.map((t) => (
          <Card key={t.id} className="flex items-center gap-3 p-3">
            <button className="min-w-0 flex-1 text-left" onClick={() => setDetail(t)}>
              <div className="flex items-center gap-2">
                <span className="text-xs font-medium text-white/40">{t.ref}</span>
                <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-medium", priorityChip[t.priority])}>{t.priority.toLowerCase()}</span>
              </div>
              <p className="mt-1 truncate text-sm">{t.title}</p>
            </button>
            {t.assigneeName && (
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-aqua/20 text-[9px] font-semibold text-aqua" title={t.assigneeName}>
                {initials(t.assigneeName)}
              </span>
            )}
            <select
              className="h-9 rounded-lg border border-white/15 bg-white/5 px-2 text-xs text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
              value=""
              onChange={async (e) => { if (e.target.value) { await api.updateTask(t.id, { sprintId: e.target.value }); void load(); } }}
            >
              <option value="" className="bg-ink">Move to sprint…</option>
              {sprints.map((s) => <option key={s.id} value={s.id} className="bg-ink">{s.name}</option>)}
            </select>
          </Card>
        ))}
      </div>

      {adding && (
        <TaskDialog title="Add to backlog" employees={employees} sprints={[]}
          onClose={() => setAdding(false)}
          onSubmit={async (data) => {
            const { sprintId: _drop, ...create } = data;
            void _drop;
            await api.createTask(projectId, create);
            setAdding(false); void load();
          }} />
      )}
      {detail && (
        <TaskDetailDialog task={detail} employees={employees} sprints={sprints}
          onClose={() => setDetail(null)}
          onSaved={() => { setDetail(null); void load(); }}
          onDeleted={() => { setDetail(null); void load(); }} />
      )}
    </div>
  );
}

// ============================ SPRINTS ============================

function SprintsView({ projectId }: { projectId: string }) {
  const [sprints, setSprints] = useState<Sprint[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try { setSprints(await api.listSprints(projectId)); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Failed to load sprints"); }
  }, [projectId]);

  useEffect(() => { void load(); }, [load]);

  async function act(id: string, fn: () => Promise<unknown>) {
    setBusyId(id); setError(null);
    try { await fn(); await load(); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Action failed"); }
    finally { setBusyId(null); }
  }

  if (error && !sprints) return <Alert tone="error">{error}</Alert>;
  if (!sprints) return <Card><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>;

  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Sprints</h2>
          <p className="text-xs text-white/40">Plan work into time-boxes. One sprint runs at a time.</p>
        </div>
        <Button onClick={() => setCreating(true)}><Plus className="h-4 w-4" /> New sprint</Button>
      </div>

      {error && <Alert tone="error" className="mt-4">{error}</Alert>}

      <div className="mt-6 space-y-3">
        {sprints.length === 0 ? (
          <Card className="py-10 text-center text-sm text-white/40">No sprints yet. Create one, then pull tasks in from the Backlog.</Card>
        ) : sprints.map((s) => (
          <Card key={s.id} className="p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="font-medium">{s.name}</h3>
                  <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase", sprintChip[s.status])}>{s.status.toLowerCase()}</span>
                </div>
                {s.goal && <p className="mt-1 text-sm text-white/50">{s.goal}</p>}
                <p className="mt-2 flex items-center gap-3 text-xs text-white/40">
                  <span>{s.doneCount}/{s.taskCount} done</span>
                  {(s.startDate || s.endDate) && <span className="flex items-center gap-1"><CalendarDays className="h-3 w-3" />{s.startDate ?? "?"} → {s.endDate ?? "?"}</span>}
                </p>
              </div>
              <div className="flex items-center gap-1.5">
                {s.status === "PLANNED" && (
                  <Button size="sm" onClick={() => act(s.id, () => api.startSprint(s.id))} disabled={busyId === s.id}>
                    {busyId === s.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />} Start
                  </Button>
                )}
                {s.status === "ACTIVE" && (
                  <Button size="sm" variant="secondary" onClick={() => act(s.id, () => api.completeSprint(s.id))} disabled={busyId === s.id}>
                    {busyId === s.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />} Complete
                  </Button>
                )}
                <button
                  onClick={() => { if (confirm("Delete this sprint? Its tasks return to the backlog.")) void act(s.id, () => api.deleteSprint(s.id)); }}
                  disabled={busyId === s.id}
                  className="rounded-md p-2 text-white/40 hover:bg-red-500/10 hover:text-red-300 disabled:opacity-50" aria-label="Delete sprint">
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          </Card>
        ))}
      </div>

      {creating && (
        <SprintDialog projectId={projectId} onClose={() => setCreating(false)} onCreated={() => { setCreating(false); void load(); }} />
      )}
    </div>
  );
}

function SprintDialog({ projectId, onClose, onCreated }: { projectId: string; onClose: () => void; onCreated: () => void }) {
  const [name, setName] = useState("");
  const [goal, setGoal] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) { setError("Enter a sprint name."); return; }
    setBusy(true);
    try {
      await api.createSprint(projectId, { name: name.trim(), goal, startDate, endDate });
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create sprint");
      setBusy(false);
    }
  }

  return (
    <Modal open onClose={onClose} title="New sprint">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Name" htmlFor="sp-name"><Input id="sp-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Sprint 1" autoFocus /></Field>
        <Field label="Goal (optional)" htmlFor="sp-goal"><Input id="sp-goal" value={goal} onChange={(e) => setGoal(e.target.value)} placeholder="What should this sprint achieve?" /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Start (optional)" htmlFor="sp-start"><Input id="sp-start" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} /></Field>
          <Field label="End (optional)" htmlFor="sp-end"><Input id="sp-end" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} /></Field>
        </div>
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Create</Button>
        </div>
      </form>
    </Modal>
  );
}

// ============================ TICKETS ============================

function TicketsView({ projectId, employees }: { projectId: string; employees: Employee[] }) {
  const [tickets, setTickets] = useState<Ticket[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [detail, setDetail] = useState<Ticket | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try { setTickets(await api.listTickets(projectId)); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Failed to load tickets"); }
  }, [projectId]);

  useEffect(() => { void load(); }, [load]);

  if (error && !tickets) return <Alert tone="error">{error}</Alert>;
  if (!tickets) return <Card><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>;

  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Support tickets</h2>
          <p className="text-xs text-white/40">Requests from customers or teammates, assigned to your people.</p>
        </div>
        <Button onClick={() => setCreating(true)}><Plus className="h-4 w-4" /> New ticket</Button>
      </div>

      <div className="mt-6 space-y-2">
        {tickets.length === 0 ? (
          <Card className="py-10 text-center text-sm text-white/40">No tickets yet.</Card>
        ) : tickets.map((t) => (
          <Card key={t.id} className="flex cursor-pointer items-center gap-3 p-3 hover:border-white/20" onClick={() => setDetail(t)}>
            <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase", ticketChip[t.status])}>{t.status.toLowerCase()}</span>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="text-xs font-medium text-white/40">{t.ref}</span>
                <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-medium", priorityChip[t.priority])}>{t.priority.toLowerCase()}</span>
              </div>
              <p className="mt-0.5 truncate text-sm">{t.subject}</p>
              {t.requesterName && <p className="text-xs text-white/40">from {t.requesterName}</p>}
            </div>
            {t.assigneeName ? (
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-aqua/20 text-[9px] font-semibold text-aqua" title={t.assigneeName}>{initials(t.assigneeName)}</span>
            ) : <span className="text-xs text-white/25">Unassigned</span>}
            <ArrowRight className="h-4 w-4 text-white/20" />
          </Card>
        ))}
      </div>

      {creating && (
        <TicketDialog title="New ticket" employees={employees}
          onClose={() => setCreating(false)}
          onSubmit={async (data) => { await api.createTicket(projectId, data); setCreating(false); void load(); }} />
      )}
      {detail && (
        <TicketDialog title={`${detail.ref} · edit`} employees={employees} initial={detail}
          onClose={() => setDetail(null)}
          onSubmit={async (data) => { await api.updateTicket(detail.id, data); setDetail(null); void load(); }}
          onDelete={async () => { await api.deleteTicket(detail.id); setDetail(null); void load(); }} />
      )}
    </div>
  );
}

interface TicketForm {
  subject: string; description: string; requesterName: string; requesterEmail: string;
  status: string; priority: string; assigneeId: string;
}

function TicketDialog({
  title, employees, initial, onClose, onSubmit, onDelete,
}: {
  title: string;
  employees: Employee[];
  initial?: Ticket;
  onClose: () => void;
  onSubmit: (data: TicketForm) => Promise<void>;
  onDelete?: () => Promise<void>;
}) {
  const [form, setForm] = useState<TicketForm>({
    subject: initial?.subject ?? "",
    description: initial?.description ?? "",
    requesterName: initial?.requesterName ?? "",
    requesterEmail: initial?.requesterEmail ?? "",
    status: initial?.status ?? "OPEN",
    priority: initial?.priority ?? "MEDIUM",
    assigneeId: initial?.assigneeId ?? "",
  });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof TicketForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!form.subject.trim()) { setError("Enter a subject."); return; }
    setBusy(true);
    try { await onSubmit(form); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Failed to save"); setBusy(false); }
  }

  return (
    <Modal open onClose={onClose} title={title}>
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Subject" htmlFor="tk-subj"><Input id="tk-subj" value={form.subject} onChange={set("subject")} /></Field>
        <Field label="Description" htmlFor="tk-desc"><Input id="tk-desc" value={form.description} onChange={set("description")} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Requester name" htmlFor="tk-rn"><Input id="tk-rn" value={form.requesterName} onChange={set("requesterName")} /></Field>
          <Field label="Requester email" htmlFor="tk-re"><Input id="tk-re" type="email" value={form.requesterEmail} onChange={set("requesterEmail")} /></Field>
        </div>
        <div className="grid grid-cols-2 gap-3">
          {initial && (
            <Field label="Status" htmlFor="tk-status">
              <select id="tk-status" className={selectCls} value={form.status} onChange={set("status")}>
                {TICKET_STATUSES.map((s) => <option key={s} value={s} className="bg-ink">{s.toLowerCase()}</option>)}
              </select>
            </Field>
          )}
          <Field label="Priority" htmlFor="tk-pri">
            <select id="tk-pri" className={selectCls} value={form.priority} onChange={set("priority")}>
              {PRIORITIES.map((p) => <option key={p} value={p} className="bg-ink">{p.toLowerCase()}</option>)}
            </select>
          </Field>
        </div>
        <Field label="Assignee" htmlFor="tk-assignee">
          <select id="tk-assignee" className={selectCls} value={form.assigneeId} onChange={set("assigneeId")}>
            <option value="" className="bg-ink">Unassigned</option>
            {employees.map((e) => <option key={e.id} value={e.id} className="bg-ink">{e.firstName} {e.lastName}</option>)}
          </select>
        </Field>
        <div className="mt-2 flex items-center justify-between gap-2">
          {onDelete ? (
            <button type="button" onClick={() => onDelete()} className="inline-flex items-center gap-1 text-sm text-red-400/80 hover:text-red-300">
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

// ============================ TASK DIALOGS (shared) ============================

interface TaskForm {
  title: string;
  description: string;
  priority: string;
  assigneeId: string;
  sprintId: string;
  dueDate: string;
}

function TaskDialog({
  title, employees, sprints, initial, onClose, onSubmit, onDelete,
}: {
  title: string;
  employees: Employee[];
  sprints: Sprint[];
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
    sprintId: initial?.sprintId ?? "",
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
    try { await onSubmit(form); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Failed to save"); setBusy(false); }
  }

  const showSprint = sprints.length > 0;

  return (
    <Modal open onClose={onClose} title={title}>
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Title" htmlFor="t-title"><Input id="t-title" value={form.title} onChange={set("title")} /></Field>
        <Field label="Description" htmlFor="t-desc"><Input id="t-desc" value={form.description} onChange={set("description")} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Priority" htmlFor="t-pri">
            <select id="t-pri" className={selectCls} value={form.priority} onChange={set("priority")}>
              {PRIORITIES.map((p) => <option key={p} value={p} className="bg-ink">{p.toLowerCase()}</option>)}
            </select>
          </Field>
          <Field label="Due date" htmlFor="t-due"><Input id="t-due" type="date" value={form.dueDate} onChange={set("dueDate")} /></Field>
        </div>
        <div className={cn("grid gap-3", showSprint ? "grid-cols-2" : "grid-cols-1")}>
          <Field label="Assignee" htmlFor="t-assignee">
            <select id="t-assignee" className={selectCls} value={form.assigneeId} onChange={set("assigneeId")}>
              <option value="" className="bg-ink">Unassigned</option>
              {employees.map((e) => <option key={e.id} value={e.id} className="bg-ink">{e.firstName} {e.lastName}</option>)}
            </select>
          </Field>
          {showSprint && (
            <Field label="Sprint" htmlFor="t-sprint">
              <select id="t-sprint" className={selectCls} value={form.sprintId} onChange={set("sprintId")}>
                <option value="" className="bg-ink">Backlog</option>
                {sprints.map((s) => <option key={s.id} value={s.id} className="bg-ink">{s.name}</option>)}
              </select>
            </Field>
          )}
        </div>
        <div className="mt-2 flex items-center justify-between gap-2">
          {onDelete ? (
            <button type="button" onClick={() => onDelete()} className="inline-flex items-center gap-1 text-sm text-red-400/80 hover:text-red-300">
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
  task, employees, sprints, onClose, onSaved, onDeleted,
}: {
  task: Task;
  employees: Employee[];
  sprints: Sprint[];
  onClose: () => void;
  onSaved: () => void;
  onDeleted: () => void;
}) {
  return (
    <TaskDialog
      title={`${task.ref} · edit`}
      employees={employees}
      sprints={sprints}
      initial={{
        title: task.title,
        description: task.description ?? "",
        priority: task.priority,
        assigneeId: task.assigneeId ?? "",
        sprintId: task.sprintId ?? "",
        dueDate: task.dueDate ?? "",
      }}
      onClose={onClose}
      onSubmit={async (data) => { await api.updateTask(task.id, data); onSaved(); }}
      onDelete={async () => { await api.deleteTask(task.id); onDeleted(); }}
    />
  );
}
