"use client";

import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import Link from "next/link";
import { Loader2, Plus, FileText, ArrowLeft, Trash2, Link2, Save, Eye, Pencil } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Space, KnowledgePage, PageSummary, Project, Task } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";
import { cn } from "@/lib/utils";

export default function SpacePageRoute() {
  return (
    <Suspense fallback={<Card><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>}>
      <SpacePage />
    </Suspense>
  );
}

function SpacePage() {
  const { spaceId } = useParams<{ spaceId: string }>();
  const search = useSearchParams();
  const [space, setSpace] = useState<Space | null>(null);
  const [pages, setPages] = useState<PageSummary[] | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(search.get("page"));
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const loadPages = useCallback(async () => {
    try {
      const list = await api.listPages(spaceId);
      setPages(list);
      setSelectedId((cur) => cur ?? (list.length > 0 ? list[0].id : null));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load pages");
    }
  }, [spaceId]);

  useEffect(() => {
    void api.getSpace(spaceId).then(setSpace).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load space"));
    void loadPages();
  }, [spaceId, loadPages]);

  const tree = useMemo(() => buildTree(pages ?? []), [pages]);

  async function createPage(title: string) {
    setCreating(true);
    try {
      const page = await api.createPage(spaceId, { title });
      await loadPages();
      setSelectedId(page.id);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to create page");
    } finally {
      setCreating(false);
    }
  }

  return (
    <div>
      <Link href="/knowledge" className="inline-flex items-center gap-1.5 text-sm text-white/50 hover:text-white">
        <ArrowLeft className="h-4 w-4" /> Spaces
      </Link>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          {space && <span className="rounded-md bg-violet/20 px-2 py-0.5 text-xs font-semibold text-violet">{space.key}</span>}
          <h1 className="text-2xl font-semibold tracking-tight">{space?.name ?? "…"}</h1>
        </div>
        <NewPageButton onCreate={createPage} busy={creating} />
      </div>
      {space?.description && <p className="mt-1 text-white/50">{space.description}</p>}

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      <div className="mt-6 grid gap-6 lg:grid-cols-[260px_1fr]">
        {/* page tree */}
        <aside className="space-y-1">
          {pages === null ? (
            <Card><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></Card>
          ) : pages.length === 0 ? (
            <p className="rounded-lg border border-dashed border-white/10 px-4 py-6 text-center text-sm text-white/40">
              No pages yet.
            </p>
          ) : (
            tree.map((node) => (
              <TreeRow key={node.page.id} node={node} depth={0} selectedId={selectedId} onSelect={setSelectedId} />
            ))
          )}
        </aside>

        {/* editor / reader */}
        <section>
          {selectedId ? (
            <PageEditor
              key={selectedId}
              pageId={selectedId}
              spaceId={spaceId}
              siblings={pages ?? []}
              onChanged={loadPages}
              onDeleted={() => { setSelectedId(null); void loadPages(); }}
            />
          ) : (
            <Card className="flex flex-col items-center gap-3 py-16 text-center">
              <FileText className="h-8 w-8 text-white/30" />
              <p className="text-sm text-white/50">Select a page, or create one to start writing.</p>
            </Card>
          )}
        </section>
      </div>
    </div>
  );
}

// ---- page tree ----

interface TreeNode {
  page: PageSummary;
  children: TreeNode[];
}

function buildTree(pages: PageSummary[]): TreeNode[] {
  const byId = new Map<string, TreeNode>();
  pages.forEach((p) => byId.set(p.id, { page: p, children: [] }));
  const roots: TreeNode[] = [];
  byId.forEach((node) => {
    const parent = node.page.parentId ? byId.get(node.page.parentId) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  });
  return roots;
}

function TreeRow({ node, depth, selectedId, onSelect }: { node: TreeNode; depth: number; selectedId: string | null; onSelect: (id: string) => void }) {
  return (
    <>
      <button
        onClick={() => onSelect(node.page.id)}
        style={{ paddingLeft: 12 + depth * 16 }}
        className={cn(
          "flex w-full items-center gap-2 rounded-md py-1.5 pr-2 text-left text-sm transition-colors",
          node.page.id === selectedId ? "bg-white/10 text-white" : "text-white/60 hover:bg-white/5 hover:text-white",
        )}
      >
        <FileText className="h-3.5 w-3.5 shrink-0 text-white/30" />
        <span className="truncate">{node.page.title}</span>
        {node.page.status === "DRAFT" && <span className="ml-auto shrink-0 text-[10px] uppercase tracking-wide text-amber-300/70">draft</span>}
      </button>
      {node.children.map((child) => (
        <TreeRow key={child.page.id} node={child} depth={depth + 1} selectedId={selectedId} onSelect={onSelect} />
      ))}
    </>
  );
}

// ---- editor ----

function PageEditor({ pageId, spaceId, siblings, onChanged, onDeleted }: {
  pageId: string; spaceId: string; siblings: PageSummary[]; onChanged: () => Promise<void>; onDeleted: () => void;
}) {
  const [page, setPage] = useState<KnowledgePage | null>(null);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [linkOpen, setLinkOpen] = useState(false);

  useEffect(() => {
    setEditing(false);
    void api.getPage(pageId).then((p) => {
      setPage(p);
      setTitle(p.title);
      setBody(p.body ?? "");
    }).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load page"));
  }, [pageId]);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      const updated = await api.updatePage(pageId, { title, body });
      setPage(updated);
      setEditing(false);
      await onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to save");
    } finally {
      setBusy(false);
    }
  }

  async function togglePublish() {
    if (!page) return;
    setBusy(true);
    try {
      const updated = await api.updatePage(pageId, { status: page.status === "PUBLISHED" ? "DRAFT" : "PUBLISHED" });
      setPage(updated);
      await onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to update status");
    } finally {
      setBusy(false);
    }
  }

  async function linkTask(taskId: string | null) {
    setBusy(true);
    try {
      const updated = await api.updatePage(pageId, { linkedTaskId: taskId ?? "" });
      setPage(updated);
      setLinkOpen(false);
      await onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to link task");
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!confirm("Delete this page? This cannot be undone.")) return;
    setBusy(true);
    try {
      await api.deletePage(pageId);
      onDeleted();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to delete");
      setBusy(false);
    }
  }

  if (!page) return <Card><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>;

  return (
    <Card className="min-h-[24rem]">
      {error && <Alert tone="error" className="mb-4">{error}</Alert>}

      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 pb-4">
        <div className="flex items-center gap-2 text-xs text-white/40">
          <StatusChip status={page.status} />
          {page.authorName && <span>· by {page.authorName}</span>}
          {page.linkedTaskRef && (
            <span className="inline-flex items-center gap-1 rounded bg-aqua/15 px-1.5 py-0.5 font-medium text-aqua">
              <Link2 className="h-3 w-3" /> {page.linkedTaskRef}
            </span>
          )}
        </div>
        <div className="flex items-center gap-1.5">
          <Button variant="ghost" size="sm" onClick={() => setLinkOpen(true)}><Link2 className="h-4 w-4" /> Link task</Button>
          <Button variant="ghost" size="sm" onClick={togglePublish} disabled={busy}>
            {page.status === "PUBLISHED" ? "Unpublish" : "Publish"}
          </Button>
          {editing ? (
            <Button size="sm" onClick={save} disabled={busy}>{busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save</Button>
          ) : (
            <Button variant="secondary" size="sm" onClick={() => setEditing(true)}><Pencil className="h-4 w-4" /> Edit</Button>
          )}
          <button onClick={remove} disabled={busy} aria-label="Delete page"
            className="rounded-md p-2 text-white/40 hover:bg-red-500/10 hover:text-red-300 disabled:opacity-50">
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </div>

      {editing ? (
        <div className="mt-4 space-y-4">
          <Field label="Title" htmlFor="p-title">
            <Input id="p-title" value={title} onChange={(e) => setTitle(e.target.value)} />
          </Field>
          <Field label="Body (Markdown)" htmlFor="p-body">
            <textarea
              id="p-body"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              rows={16}
              placeholder="# Heading&#10;&#10;Write with **Markdown**. Use `-` for lists, `code`, and ## sub-headings."
              className="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 font-mono text-sm text-white placeholder:text-white/30 focus:border-violet focus:outline-none"
            />
          </Field>
          <div className="flex items-center gap-2">
            <Button onClick={save} disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Save</Button>
            <Button variant="ghost" onClick={() => { setEditing(false); setTitle(page.title); setBody(page.body ?? ""); }}>Cancel</Button>
          </div>
        </div>
      ) : (
        <article className="mt-4">
          <h2 className="text-xl font-semibold tracking-tight">{page.title}</h2>
          <div className="mt-4">
            {page.body ? <Markdown source={page.body} /> : (
              <p className="flex items-center gap-2 text-sm text-white/40">
                <Eye className="h-4 w-4" /> This page is empty. <button onClick={() => setEditing(true)} className="text-violet hover:underline">Add content →</button>
              </p>
            )}
          </div>
        </article>
      )}

      <LinkTaskDialog
        open={linkOpen}
        onClose={() => setLinkOpen(false)}
        current={page.linkedTaskId}
        onPick={linkTask}
      />
    </Card>
  );
}

function StatusChip({ status }: { status: "DRAFT" | "PUBLISHED" }) {
  return (
    <span className={cn(
      "rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide",
      status === "PUBLISHED" ? "bg-emerald-500/15 text-emerald-300" : "bg-amber-500/15 text-amber-300",
    )}>
      {status}
    </span>
  );
}

function NewPageButton({ onCreate, busy }: { onCreate: (title: string) => void; busy: boolean }) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  return (
    <>
      <Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" /> New page</Button>
      <Modal open={open} onClose={() => setOpen(false)} title="New page">
        <form
          onSubmit={(e) => { e.preventDefault(); if (title.trim()) { onCreate(title.trim()); setTitle(""); setOpen(false); } }}
          className="flex flex-col gap-4"
        >
          <Field label="Title" htmlFor="np-title">
            <Input id="np-title" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Getting started" autoFocus />
          </Field>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={busy || !title.trim()}>Create</Button>
          </div>
        </form>
      </Modal>
    </>
  );
}

// ---- link-a-task dialog (cross-app into Work OS) ----

function LinkTaskDialog({ open, onClose, current, onPick }: {
  open: boolean; onClose: () => void; current: string | null; onPick: (taskId: string | null) => void;
}) {
  const [projects, setProjects] = useState<Project[] | null>(null);
  const [projectId, setProjectId] = useState<string>("");
  const [tasks, setTasks] = useState<Task[] | null>(null);

  useEffect(() => {
    if (open) void api.listProjects().then((p) => setProjects(p.filter((x) => x.status === "ACTIVE")));
  }, [open]);

  useEffect(() => {
    if (projectId) void api.listTasks(projectId).then(setTasks);
    else setTasks(null);
  }, [projectId]);

  return (
    <Modal open={open} onClose={onClose} title="Link a Work task">
      <div className="flex flex-col gap-4">
        <p className="text-sm text-white/50">Connect this doc to a task in Work OS — the doc↔task link that ties knowledge to delivery.</p>
        <Field label="Project" htmlFor="lt-project">
          <select
            id="lt-project"
            value={projectId}
            onChange={(e) => setProjectId(e.target.value)}
            className="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-white focus:border-violet focus:outline-none"
          >
            <option value="">Select a project…</option>
            {projects?.map((p) => <option key={p.id} value={p.id}>{p.key} · {p.name}</option>)}
          </select>
        </Field>
        {projectId && (
          <div className="max-h-56 space-y-1 overflow-y-auto">
            {tasks === null ? (
              <Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" />
            ) : tasks.length === 0 ? (
              <p className="text-sm text-white/40">No tasks in this project.</p>
            ) : (
              tasks.map((t) => (
                <button key={t.id} onClick={() => onPick(t.id)}
                  className={cn("flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm hover:bg-white/5",
                    t.id === current && "bg-violet/10")}>
                  <span className="font-mono text-xs text-white/40">{t.ref}</span>
                  <span className="truncate">{t.title}</span>
                </button>
              ))
            )}
          </div>
        )}
        <div className="flex justify-between">
          <Button variant="ghost" onClick={() => onPick(null)} disabled={!current}>Remove link</Button>
          <Button variant="ghost" onClick={onClose}>Close</Button>
        </div>
      </div>
    </Modal>
  );
}

// ---- minimal, safe Markdown renderer (headings, bold, italics, inline code, lists) ----

function Markdown({ source }: { source: string }) {
  const html = useMemo(() => renderMarkdown(source), [source]);
  return <div className="prose-invert space-y-2 text-sm leading-relaxed text-white/80" dangerouslySetInnerHTML={{ __html: html }} />;
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function inline(s: string): string {
  return escapeHtml(s)
    .replace(/`([^`]+)`/g, '<code class="rounded bg-white/10 px-1 py-0.5 text-[0.85em]">$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/(^|[^*])\*([^*]+)\*/g, "$1<em>$2</em>");
}

function renderMarkdown(md: string): string {
  const lines = md.replace(/\r\n/g, "\n").split("\n");
  const out: string[] = [];
  let list: string[] | null = null;
  const flush = () => {
    if (list) {
      out.push(`<ul class="ml-5 list-disc space-y-1">${list.join("")}</ul>`);
      list = null;
    }
  };
  for (const line of lines) {
    if (/^\s*[-*]\s+/.test(line)) {
      (list ??= []).push(`<li>${inline(line.replace(/^\s*[-*]\s+/, ""))}</li>`);
      continue;
    }
    flush();
    if (/^###\s+/.test(line)) out.push(`<h4 class="mt-3 font-semibold text-white">${inline(line.slice(4))}</h4>`);
    else if (/^##\s+/.test(line)) out.push(`<h3 class="mt-4 text-lg font-semibold text-white">${inline(line.slice(3))}</h3>`);
    else if (/^#\s+/.test(line)) out.push(`<h2 class="mt-4 text-xl font-semibold text-white">${inline(line.slice(2))}</h2>`);
    else if (line.trim() === "") out.push("");
    else out.push(`<p>${inline(line)}</p>`);
  }
  flush();
  return out.join("\n");
}
