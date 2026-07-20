"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Loader2, Plus, BookOpen, FileText, ArrowRight, Search } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Space, PageSummary } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";

export default function KnowledgePage() {
  const [spaces, setSpaces] = useState<Space[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      setSpaces(await api.listSpaces());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load spaces");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const active = spaces?.filter((s) => s.status === "ACTIVE") ?? [];

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Knowledge</h1>
          <p className="mt-1 text-white/50">
            Docs &amp; wiki, linked to people and work. <Link href="/knowledge/mine" className="text-violet hover:underline">My pages →</Link>
          </p>
        </div>
        <Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" /> New space</Button>
      </div>

      <SearchBar />

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {spaces === null ? (
        <Card className="mt-8"><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>
      ) : active.length === 0 ? (
        <Card className="mt-8 flex flex-col items-center gap-3 py-12 text-center">
          <BookOpen className="h-8 w-8 text-white/30" />
          <CardTitle>No spaces yet</CardTitle>
          <p className="text-sm text-white/50">Create a space to start writing docs — a runbook, a handbook, meeting notes.</p>
          <Button onClick={() => setOpen(true)} className="mt-2"><Plus className="h-4 w-4" /> New space</Button>
        </Card>
      ) : (
        <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {active.map((s) => (
            <Link key={s.id} href={`/knowledge/${s.id}`}>
              <Card className="group h-full transition-colors hover:border-white/20">
                <div className="flex items-center justify-between">
                  <span className="rounded-md bg-violet/20 px-2 py-0.5 text-xs font-semibold text-violet">{s.key}</span>
                  <ArrowRight className="h-4 w-4 text-white/20 transition-colors group-hover:text-white/60" />
                </div>
                <h3 className="mt-3 font-medium">{s.name}</h3>
                {s.description && <p className="mt-1 line-clamp-2 text-sm text-white/50">{s.description}</p>}
                <p className="mt-4 flex items-center gap-1.5 text-xs text-white/40">
                  <FileText className="h-3.5 w-3.5" />
                  {s.pageCount} {s.pageCount === 1 ? "page" : "pages"}
                </p>
              </Card>
            </Link>
          ))}
        </div>
      )}

      <NewSpaceDialog open={open} onClose={() => setOpen(false)} onCreated={() => { setOpen(false); void load(); }} />
    </div>
  );
}

function SearchBar() {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<PageSummary[] | null>(null);
  const [busy, setBusy] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (timer.current) clearTimeout(timer.current);
    const term = q.trim();
    if (term.length < 2) {
      setResults(null);
      return;
    }
    timer.current = setTimeout(async () => {
      setBusy(true);
      try {
        setResults(await api.searchPages(term));
      } catch {
        setResults([]);
      } finally {
        setBusy(false);
      }
    }, 250);
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [q]);

  return (
    <div className="relative mt-6">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-white/30" />
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search all pages…"
        className="w-full rounded-lg border border-white/10 bg-white/5 py-2.5 pl-10 pr-4 text-sm text-white placeholder:text-white/30 focus:border-violet focus:outline-none"
      />
      {results !== null && (
        <div className="absolute z-10 mt-2 w-full rounded-lg border border-white/10 bg-ink shadow-xl">
          {busy && results.length === 0 ? (
            <p className="px-4 py-3 text-sm text-white/40">Searching…</p>
          ) : results.length === 0 ? (
            <p className="px-4 py-3 text-sm text-white/40">No pages match “{q.trim()}”.</p>
          ) : (
            results.map((r) => (
              <Link key={r.id} href={`/knowledge/${r.spaceId}?page=${r.id}`} onClick={() => setQ("")}
                className="block border-b border-white/5 px-4 py-3 last:border-0 hover:bg-white/5">
                <div className="flex items-center gap-2">
                  <FileText className="h-3.5 w-3.5 text-white/30" />
                  <span className="text-sm font-medium">{r.title}</span>
                  <span className="text-xs text-white/30">· {r.spaceName}</span>
                </div>
                {r.snippet && <p className="mt-1 line-clamp-1 pl-5 text-xs text-white/40">{r.snippet}</p>}
              </Link>
            ))
          )}
        </div>
      )}
    </div>
  );
}

function NewSpaceDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const [name, setName] = useState("");
  const [key, setKey] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [keyTouched, setKeyTouched] = useState(false);

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
      await api.createSpace({ name: name.trim(), key: effectiveKey, description });
      setName(""); setKey(""); setDescription(""); setKeyTouched(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create space");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New space">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Name" htmlFor="s-name">
          <Input id="s-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Engineering handbook" />
        </Field>
        <Field label="Key" htmlFor="s-key" hint="Short code that groups the space's docs.">
          <Input id="s-key" value={effectiveKey} maxLength={10}
            onChange={(e) => { setKeyTouched(true); setKey(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "")); }} />
        </Field>
        <Field label="Description (optional)" htmlFor="s-desc">
          <Input id="s-desc" value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Create</Button>
        </div>
      </form>
    </Modal>
  );
}
