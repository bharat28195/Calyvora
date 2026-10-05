"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, Lock, Plus, Save, ShieldCheck, Trash2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { CompanyRole, PermissionInfo } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";
import { cn } from "@/lib/utils";

type Grants = Record<string, "COMPANY" | "TEAM">;

const selectCls =
  "h-8 rounded-md border border-fg/15 bg-fg/5 px-2 text-xs text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

/**
 * Roles & permissions (PD-54): the company decides what each role may do. A permission says what;
 * for the scoped ones, "Their team" limits it to the people below the holder in the org chart.
 */
export default function RolesPage() {
  const [roles, setRoles] = useState<CompanyRole[] | null>(null);
  const [catalogue, setCatalogue] = useState<PermissionInfo[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async (select?: string) => {
    try {
      const [r, c] = await Promise.all([api.roles(), api.permissionCatalogue()]);
      setRoles(r);
      setCatalogue(c);
      setSelectedId((cur) => select ?? cur ?? r[0]?.id ?? null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load roles");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const selected = roles?.find((r) => r.id === selectedId) ?? null;

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Roles &amp; permissions</h1>
          <p className="mt-1 max-w-2xl text-fg/50">
            Decide what each role can do. &ldquo;Their team&rdquo; limits a permission to the people who report to
            the person, directly or further down the org chart.
          </p>
        </div>
        <Button onClick={() => setCreating(true)} disabled={!roles}><Plus className="h-4 w-4" /> New role</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {note && <Alert tone="success" className="mt-6">{note}</Alert>}

      {roles === null ? (
        <Card className="mt-6"><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>
      ) : (
        <div className="mt-6 grid gap-6 lg:grid-cols-[260px_1fr]">
          <aside className="flex flex-col gap-1">
            {roles.map((r) => (
              <button key={r.id} onClick={() => { setSelectedId(r.id); setNote(null); setError(null); }}
                className={cn(
                  "flex items-center justify-between gap-2 rounded-lg px-3 py-2 text-left text-sm transition-colors",
                  r.id === selectedId ? "bg-violet/15 text-fg" : "text-fg/70 hover:bg-fg/5",
                )}>
                <span className="flex min-w-0 items-center gap-2">
                  {r.locked ? <Lock className="h-3.5 w-3.5 shrink-0 text-fg/40" /> : <ShieldCheck className="h-3.5 w-3.5 shrink-0 text-fg/40" />}
                  <span className="truncate">{r.name}</span>
                </span>
                <span className="shrink-0 text-xs text-fg/40">{r.memberCount}</span>
              </button>
            ))}
          </aside>

          {selected && (
            <RoleEditor key={selected.id} role={selected} catalogue={catalogue}
              onSaved={(m) => { setNote(m); setError(null); void load(selected.id); }}
              onDeleted={(m) => { setNote(m); setSelectedId(null); void load(); }}
              onError={(m) => { setError(m); setNote(null); }} />
          )}
        </div>
      )}

      {roles && (
        <NewRoleDialog open={creating} roles={roles} onClose={() => setCreating(false)}
          onCreated={(r) => { setCreating(false); setNote(`${r.name} created. Choose what it can do, then assign it on the Members page.`); void load(r.id); }}
          onError={setError} />
      )}
    </div>
  );
}

function RoleEditor({ role, catalogue, onSaved, onDeleted, onError }: {
  role: CompanyRole; catalogue: PermissionInfo[];
  onSaved: (m: string) => void; onDeleted: (m: string) => void; onError: (m: string) => void;
}) {
  const [grants, setGrants] = useState<Grants>(role.permissions);
  const [name, setName] = useState(role.name);
  const [description, setDescription] = useState(role.description ?? "");
  const [busy, setBusy] = useState(false);

  const groups = useMemo(() => {
    const out: { group: string; items: PermissionInfo[] }[] = [];
    for (const p of catalogue) {
      const g = out.find((x) => x.group === p.group);
      if (g) g.items.push(p); else out.push({ group: p.group, items: [p] });
    }
    return out;
  }, [catalogue]);

  const dirty = JSON.stringify(grants) !== JSON.stringify(role.permissions)
    || name.trim() !== role.name || (description.trim() || null) !== (role.description ?? null);

  function toggle(p: PermissionInfo, on: boolean) {
    setGrants((g) => {
      const next = { ...g };
      if (on) next[p.key] = p.scoped && role.builtin !== "ADMIN" ? (g[p.key] ?? "COMPANY") : "COMPANY";
      else delete next[p.key];
      return next;
    });
  }

  async function save() {
    setBusy(true);
    try {
      await api.updateRole(role.id, {
        ...(role.builtin ? {} : { name: name.trim() }),
        description: description.trim(),
        permissions: grants,
      });
      onSaved(`${name.trim() || role.name} saved. It applies to everyone with this role straight away.`);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : "Could not save the role");
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!confirm(`Delete the role "${role.name}"?`)) return;
    setBusy(true);
    try {
      await api.deleteRole(role.id);
      onDeleted(`${role.name} deleted.`);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : "Could not delete the role");
      setBusy(false);
    }
  }

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-fg/10 pb-4">
        <div className="min-w-0 flex-1">
          {role.builtin ? (
            <h2 className="text-lg font-semibold">{role.name}</h2>
          ) : (
            <Field label="Name" htmlFor="r-name">
              <Input id="r-name" value={name} maxLength={60} onChange={(e) => setName(e.target.value)} />
            </Field>
          )}
          <div className="mt-2">
            <Field label="Description" htmlFor="r-desc">
              <Input id="r-desc" value={description} maxLength={300} disabled={role.locked}
                onChange={(e) => setDescription(e.target.value)} />
            </Field>
          </div>
          <p className="mt-2 text-xs text-fg/40">
            {role.memberCount} {role.memberCount === 1 ? "person has" : "people have"} this role
            {role.builtin ? " · built-in" : " · made by your company"}
          </p>
        </div>
        {!role.locked && (
          <div className="flex items-center gap-2">
            <Button onClick={save} disabled={busy || !dirty}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save
            </Button>
            {!role.builtin && (
              <button onClick={remove} disabled={busy} aria-label={`Delete ${role.name}`}
                className="rounded-md p-2 text-fg/40 hover:bg-red-500/10 hover:text-red-600 dark:hover:text-red-300 disabled:opacity-50">
                <Trash2 className="h-4 w-4" />
              </button>
            )}
          </div>
        )}
      </div>

      {role.locked && (
        <Alert tone="info" className="mt-4">
          The Admin role always has every permission, so your company can never lock itself out of its own
          settings. Make a new role if you want a narrower kind of administrator.
        </Alert>
      )}

      <div className="mt-4 flex flex-col gap-6">
        {groups.map(({ group, items }) => (
          <section key={group}>
            <h3 className="text-xs font-medium uppercase tracking-wide text-fg/40">{group}</h3>
            <ul className="mt-2 divide-y divide-fg/5">
              {items.map((p) => {
                const on = grants[p.key] !== undefined;
                return (
                  <li key={p.key} className="flex flex-wrap items-center justify-between gap-3 py-2.5">
                    <label className="flex min-w-0 flex-1 items-start gap-2.5 text-sm">
                      <input type="checkbox" className="mt-1" checked={on} disabled={role.locked}
                        onChange={(e) => toggle(p, e.target.checked)} />
                      <span>
                        <span className="font-medium">{p.label}</span>
                        <span className="mt-0.5 block text-xs text-fg/50">{p.description}</span>
                      </span>
                    </label>
                    {p.scoped && on && (
                      <select className={selectCls} aria-label={`${p.label}: for whom`} disabled={role.locked}
                        value={grants[p.key]}
                        onChange={(e) => setGrants((g) => ({ ...g, [p.key]: e.target.value as "COMPANY" | "TEAM" }))}>
                        <option value="COMPANY" className="bg-surface">Whole company</option>
                        <option value="TEAM" className="bg-surface">Their team</option>
                      </select>
                    )}
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
      </div>
    </Card>
  );
}

function NewRoleDialog({ open, roles, onClose, onCreated, onError }: {
  open: boolean; roles: CompanyRole[]; onClose: () => void;
  onCreated: (r: CompanyRole) => void; onError: (m: string) => void;
}) {
  const [name, setName] = useState("");
  const [from, setFrom] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setBusy(true);
    try {
      const base = roles.find((r) => r.id === from);
      const created = await api.createRole({ name: name.trim(), permissions: base ? base.permissions : {} });
      setName(""); setFrom("");
      onCreated(created);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Could not create the role");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New role">
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field label="Name" htmlFor="nr-name" hint="For example: Finance, Team lead, Payroll officer.">
          <Input id="nr-name" value={name} maxLength={60} onChange={(e) => setName(e.target.value)} autoFocus />
        </Field>
        <Field label="Start from" htmlFor="nr-from" hint="Copies that role's permissions; you can change them next.">
          <select id="nr-from" value={from} onChange={(e) => setFrom(e.target.value)}
            className="h-10 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg">
            <option value="" className="bg-surface">Nothing (no permissions)</option>
            {roles.map((r) => <option key={r.id} value={r.id} className="bg-surface">{r.name}</option>)}
          </select>
        </Field>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy || !name.trim()}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />} Create
          </Button>
        </div>
      </form>
    </Modal>
  );
}
