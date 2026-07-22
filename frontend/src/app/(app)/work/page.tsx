"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, Plus, FolderKanban, ListTodo, ArrowRight } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Project } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";

export default function WorkPage() {
  const [projects, setProjects] = useState<Project[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      setProjects(await api.listProjects());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load projects");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const active = projects?.filter((p) => p.status === "ACTIVE") ?? [];

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Work</h1>
          <p className="mt-1 text-fg/50">Projects, sprints and the work in flight.</p>
        </div>
        <Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" /> New project</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {projects === null ? (
        <Card className="mt-8"><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>
      ) : active.length === 0 ? (
        <Card className="mt-8 flex flex-col items-center gap-3 py-12 text-center">
          <FolderKanban className="h-8 w-8 text-fg/30" />
          <CardTitle>No projects yet</CardTitle>
          <p className="text-sm text-fg/50">Create your first project to start tracking work.</p>
          <Button onClick={() => setOpen(true)} className="mt-2"><Plus className="h-4 w-4" /> New project</Button>
        </Card>
      ) : (
        <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {active.map((p) => (
            <Link key={p.id} href={`/work/${p.id}`}>
              <Card className="group h-full transition-colors hover:border-fg/20">
                <div className="flex items-center justify-between">
                  <span className="rounded-md bg-violet/20 px-2 py-0.5 text-xs font-semibold text-violet">{p.key}</span>
                  <ArrowRight className="h-4 w-4 text-fg/20 transition-colors group-hover:text-fg/60" />
                </div>
                <h3 className="mt-3 font-medium">{p.name}</h3>
                {p.description && <p className="mt-1 line-clamp-2 text-sm text-fg/50">{p.description}</p>}
                <p className="mt-4 flex items-center gap-1.5 text-xs text-fg/40">
                  <ListTodo className="h-3.5 w-3.5" />
                  {p.openTaskCount} open · {p.taskCount} total
                </p>
              </Card>
            </Link>
          ))}
        </div>
      )}

      <NewProjectDialog open={open} onClose={() => setOpen(false)} onCreated={() => { setOpen(false); void load(); }} />
    </div>
  );
}

function NewProjectDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const [name, setName] = useState("");
  const [key, setKey] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [keyTouched, setKeyTouched] = useState(false);

  // auto-suggest a key from the name until the user edits it
  const suggestedKey = name.replace(/[^A-Za-z0-9]/g, "").slice(0, 4).toUpperCase();
  const effectiveKey = keyTouched ? key : suggestedKey;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (name.trim().length < 2 || effectiveKey.length < 1) {
      setError("Enter a name and a short key.");
      return;
    }
    setBusy(true);
    try {
      await api.createProject({ name: name.trim(), key: effectiveKey, description });
      setName(""); setKey(""); setDescription(""); setKeyTouched(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create project");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New project">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Name" htmlFor="p-name">
          <Input id="p-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Engineering" />
        </Field>
        <Field label="Key" htmlFor="p-key" hint="Short code that prefixes tasks, e.g. ENG-1.">
          <Input id="p-key" value={effectiveKey} maxLength={10}
            onChange={(e) => { setKeyTouched(true); setKey(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "")); }} />
        </Field>
        <Field label="Description (optional)" htmlFor="p-desc">
          <Input id="p-desc" value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Create</Button>
        </div>
      </form>
    </Modal>
  );
}
