"use client";

import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import Link from "next/link";
import { Loader2, Plus, FileText, ArrowLeft, Trash2, Save, Eye, Pencil, Upload, Download, Paperclip } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Space, KnowledgePage, PageSummary, CompanyFile } from "@/lib/types";
import { useSession } from "@/hooks/useSession";
import { canPublishDocuments } from "@/lib/document-access";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";
import { KnowledgeSearch } from "@/components/knowledge/knowledge-search";
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
  const { me } = useSession();
  const publisher = canPublishDocuments(me?.user.role);
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
      <Link href="/knowledge" className="inline-flex items-center gap-1.5 text-sm text-fg/50 hover:text-fg">
        <ArrowLeft className="h-4 w-4" /> Company documents
      </Link>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          {space && <span className="rounded-md bg-violet/20 px-2 py-0.5 text-xs font-semibold text-violet">{space.key}</span>}
          <h1 className="text-2xl font-semibold tracking-tight">{space?.name ?? "…"}</h1>
        </div>
        {publisher && <NewPageButton onCreate={createPage} busy={creating} />}
      </div>
      {space?.description && <p className="mt-1 text-fg/50">{space.description}</p>}

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      <KnowledgeSearch className="mt-6" />

      <FilesSection spaceId={spaceId} publisher={publisher} />

      <div className="mt-6 grid gap-6 lg:grid-cols-[260px_1fr]">
        {/* page tree */}
        <aside className="space-y-1">
          {pages === null ? (
            <Card><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></Card>
          ) : pages.length === 0 ? (
            <p className="rounded-lg border border-dashed border-fg/10 px-4 py-6 text-center text-sm text-fg/40">
              {publisher ? "No pages yet. Write one with New page." : "No pages here yet."}
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
              publisher={publisher}
              onChanged={loadPages}
              onDeleted={() => { setSelectedId(null); void loadPages(); }}
            />
          ) : (
            <Card className="flex flex-col items-center gap-3 py-16 text-center">
              <FileText className="h-8 w-8 text-fg/30" />
              <p className="text-sm text-fg/50">{publisher ? "Select a page, or create one to start writing." : "Select a page to read it."}</p>
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
          node.page.id === selectedId ? "bg-fg/10 text-fg" : "text-fg/60 hover:bg-fg/5 hover:text-fg",
        )}
      >
        <FileText className="h-3.5 w-3.5 shrink-0 text-fg/30" />
        <span className="truncate">{node.page.title}</span>
        {node.page.status === "DRAFT" && <span className="ml-auto shrink-0 text-[10px] uppercase tracking-wide text-amber-700/70 dark:text-amber-300/70">draft</span>}
      </button>
      {node.children.map((child) => (
        <TreeRow key={child.page.id} node={child} depth={depth + 1} selectedId={selectedId} onSelect={onSelect} />
      ))}
    </>
  );
}

// ---- editor ----

function PageEditor({ pageId, spaceId, siblings, publisher, onChanged, onDeleted }: {
  pageId: string; spaceId: string; siblings: PageSummary[]; publisher: boolean;
  onChanged: () => Promise<void>; onDeleted: () => void;
}) {
  const [page, setPage] = useState<KnowledgePage | null>(null);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-fg/10 pb-4">
        <div className="flex items-center gap-2 text-xs text-fg/40">
          <StatusChip status={page.status} />
          {page.authorName && <span>· by {page.authorName}</span>}
        </div>
        {publisher && <div className="flex items-center gap-1.5">
          <Button variant="ghost" size="sm" onClick={togglePublish} disabled={busy}>
            {page.status === "PUBLISHED" ? "Unpublish" : "Publish"}
          </Button>
          {editing ? (
            <Button size="sm" onClick={save} disabled={busy}>{busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save</Button>
          ) : (
            <Button variant="secondary" size="sm" onClick={() => setEditing(true)}><Pencil className="h-4 w-4" /> Edit</Button>
          )}
          <button onClick={remove} disabled={busy} aria-label="Delete page"
            className="rounded-md p-2 text-fg/40 hover:bg-red-500/10 hover:text-red-600 dark:hover:text-red-300 disabled:opacity-50">
            <Trash2 className="h-4 w-4" />
          </button>
        </div>}
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
              className="w-full rounded-lg border border-fg/10 bg-fg/5 px-3 py-2 font-mono text-sm text-fg placeholder:text-fg/30 focus:border-violet focus:outline-none"
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
              <p className="flex items-center gap-2 text-sm text-fg/40">
                <Eye className="h-4 w-4" /> This page is empty.
                {publisher && <button onClick={() => setEditing(true)} className="text-violet hover:underline">Add content →</button>}
              </p>
            )}
          </div>
        </article>
      )}
    </Card>
  );
}

// ---- files ----

function sizeLabel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Uploaded files in this folder: policy PDFs, forms, spreadsheets. Everyone can open or download;
 * publishers upload and delete. Downloads go through fetch so the request carries the sign-in.
 */
function FilesSection({ spaceId, publisher }: { spaceId: string; publisher: boolean }) {
  const [files, setFiles] = useState<CompanyFile[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [picked, setPicked] = useState<File | null>(null);

  const load = useCallback(async () => {
    try {
      setFiles(await api.listCompanyFiles(spaceId));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load files");
    }
  }, [spaceId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function upload(e: React.FormEvent) {
    e.preventDefault();
    if (!picked) return;
    setBusy("upload");
    setError(null);
    try {
      await api.uploadCompanyFile(spaceId, picked, title.trim() || undefined);
      setTitle("");
      setPicked(null);
      const input = document.getElementById("cf-file") as HTMLInputElement | null;
      if (input) input.value = "";
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Upload failed");
    } finally {
      setBusy(null);
    }
  }

  async function open(f: CompanyFile, inline: boolean) {
    setBusy(f.id);
    setError(null);
    try {
      const blob = await api.downloadCompanyFile(f.id, inline);
      const url = URL.createObjectURL(blob);
      if (inline) {
        window.open(url, "_blank", "noopener");
      } else {
        const a = document.createElement("a");
        a.href = url;
        a.download = f.fileName;
        a.click();
      }
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not open the file");
    } finally {
      setBusy(null);
    }
  }

  async function remove(f: CompanyFile) {
    if (!confirm(`Delete "${f.title}"? Everyone loses access to it.`)) return;
    setBusy(f.id);
    try {
      await api.deleteCompanyFile(f.id);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete the file");
    } finally {
      setBusy(null);
    }
  }

  if (files !== null && files.length === 0 && !publisher) return null;

  return (
    <Card className="mt-6">
      <div className="flex items-center gap-2">
        <Paperclip className="h-4 w-4 text-fg/40" />
        <h2 className="text-sm font-medium">Files</h2>
      </div>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}

      {files === null ? (
        <Loader2 className="mt-3 h-5 w-5 animate-spin text-violet" />
      ) : files.length === 0 ? (
        <p className="mt-2 text-sm text-fg/40">No files yet. Upload a PDF, Word or Excel file below.</p>
      ) : (
        <ul className="mt-3 divide-y divide-fg/5">
          {files.map((f) => (
            <li key={f.id} className="flex flex-wrap items-center justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{f.title}</p>
                <p className="truncate text-xs text-fg/40">
                  {f.fileName} · {sizeLabel(f.sizeBytes)}{f.uploadedByName ? ` · by ${f.uploadedByName}` : ""}
                </p>
              </div>
              <div className="flex items-center gap-1">
                {(f.contentType === "application/pdf" || f.contentType.startsWith("image/")) && (
                  <Button variant="ghost" size="sm" disabled={busy === f.id} onClick={() => open(f, true)}>
                    <Eye className="h-4 w-4" /> View
                  </Button>
                )}
                <Button variant="ghost" size="sm" disabled={busy === f.id} onClick={() => open(f, false)}>
                  <Download className="h-4 w-4" /> Download
                </Button>
                {publisher && (
                  <button onClick={() => remove(f)} disabled={busy === f.id} aria-label={`Delete ${f.title}`}
                    className="rounded-md p-2 text-fg/40 hover:bg-red-500/10 hover:text-red-600 dark:hover:text-red-300 disabled:opacity-50">
                    <Trash2 className="h-4 w-4" />
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {publisher && (
        <form onSubmit={upload} className="mt-4 flex flex-wrap items-end gap-3 border-t border-fg/10 pt-4">
          <Field label="Title (optional)" htmlFor="cf-title">
            <Input id="cf-title" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Leave policy 2026" />
          </Field>
          <Field label="File" htmlFor="cf-file" hint="PDF, Word, Excel, PowerPoint, text or image, up to 10 MB.">
            <input id="cf-file" type="file"
              accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.png,.jpg,.jpeg"
              onChange={(e) => setPicked(e.target.files?.[0] ?? null)}
              className="block text-sm text-fg/70 file:mr-3 file:rounded-md file:border-0 file:bg-violet/15 file:px-3 file:py-1.5 file:text-sm file:text-violet" />
          </Field>
          <Button type="submit" disabled={!picked || busy === "upload"}>
            {busy === "upload" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />} Upload
          </Button>
        </form>
      )}
    </Card>
  );
}

function StatusChip({ status }: { status: "DRAFT" | "PUBLISHED" }) {
  return (
    <span className={cn(
      "rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide",
      status === "PUBLISHED" ? "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300" : "bg-amber-500/15 text-amber-700 dark:text-amber-300",
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


// ---- minimal, safe Markdown renderer (headings, bold, italics, inline code, lists) ----

function Markdown({ source }: { source: string }) {
  const html = useMemo(() => renderMarkdown(source), [source]);
  return <div className="prose-invert space-y-2 text-sm leading-relaxed text-fg/80" dangerouslySetInnerHTML={{ __html: html }} />;
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function inline(s: string): string {
  return escapeHtml(s)
    .replace(/`([^`]+)`/g, '<code class="rounded bg-fg/10 px-1 py-0.5 text-[0.85em]">$1</code>')
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
    if (/^###\s+/.test(line)) out.push(`<h4 class="mt-3 font-semibold text-fg">${inline(line.slice(4))}</h4>`);
    else if (/^##\s+/.test(line)) out.push(`<h3 class="mt-4 text-lg font-semibold text-fg">${inline(line.slice(3))}</h3>`);
    else if (/^#\s+/.test(line)) out.push(`<h2 class="mt-4 text-xl font-semibold text-fg">${inline(line.slice(2))}</h2>`);
    else if (line.trim() === "") out.push("");
    else out.push(`<p>${inline(line)}</p>`);
  }
  flush();
  return out.join("\n");
}
