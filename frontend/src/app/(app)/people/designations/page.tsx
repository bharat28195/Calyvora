"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, Plus, Trash2, Archive, ArchiveRestore } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Designation } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/**
 * The ladder this company uses — Intern, Junior, Senior, Lead, or whatever they call theirs.
 *
 * It is a label and nothing more. Access comes from the reporting tree and the role; neither reads
 * this list, which is exactly why it is safe for a customer to edit. The page says so, because
 * "designations" reads like permissions to anybody who has used another HR product.
 */
export default function DesignationsPage() {
  const [rows, setRows] = useState<Designation[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [level, setLevel] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setRows(await api.designations(true));
    } catch (e) {
      setRows([]);
      setError(e instanceof ApiError ? e.message : "Failed to load designations");
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setBusy(true);
    setError(null);
    try {
      // Default a new rung to ten past the highest, keeping the sparse spacing that lets somebody
      // slot a level in between two others later without renumbering everything below it.
      const next = level.trim() !== ""
        ? Number(level)
        : Math.max(0, ...(rows ?? []).map((r) => r.level)) + 10;
      await api.createDesignation({ name: name.trim(), level: next, archived: false });
      setName("");
      setLevel("");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to add the designation");
    } finally {
      setBusy(false);
    }
  }

  async function toggleArchive(d: Designation) {
    setError(null);
    try {
      await api.updateDesignation(d.id, { name: d.name, level: d.level, archived: !d.archived });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to update the designation");
    }
  }

  async function remove(d: Designation) {
    setError(null);
    try {
      await api.deleteDesignation(d.id);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to delete the designation");
    }
  }

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Designations</h1>
        <p className="mt-1 text-fg/50">
          The levels your company uses. Set them up however your org actually works.
        </p>
      </div>

      <Alert tone="info" className="mt-6">
        A designation is a label. It does not grant access to anything — who can see whose attendance,
        leave and reviews comes from the reporting line on each person&rsquo;s profile, and what someone
        can administer comes from their role under Members.
      </Alert>

      {error && <Alert tone="error" className="mt-4">{error}</Alert>}

      <Card className="mt-6">
        <CardTitle>Add a designation</CardTitle>
        <form onSubmit={add} className="mt-3 flex flex-wrap items-end gap-3">
          <div className="min-w-[220px] flex-1">
            <label className="text-xs text-fg/50">Name</label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Senior Engineer" />
          </div>
          <div className="w-28">
            <label className="text-xs text-fg/50">Level</label>
            <Input value={level} onChange={(e) => setLevel(e.target.value)} placeholder="auto" inputMode="numeric" />
          </div>
          <Button type="submit" disabled={busy || !name.trim()}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />} Add
          </Button>
        </form>
        <p className="mt-2 text-xs text-fg/40">
          Level orders the ladder, lowest first. Leave it blank and it goes at the top.
        </p>
      </Card>

      <div className="mt-6 flex flex-col gap-2">
        {rows === null ? (
          <div className="flex justify-center py-10"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
        ) : rows.length === 0 ? (
          <Card className="text-sm text-fg/50">
            No designations yet. Nothing breaks without them — people keep their job titles either way.
          </Card>
        ) : (
          rows.map((d) => (
            <Card key={d.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <div className={d.archived ? "font-medium text-fg/40 line-through" : "font-medium"}>{d.name}</div>
                <div className="text-xs text-fg/40">
                  level {d.level} · {d.headcount} {d.headcount === 1 ? "person" : "people"}
                  {d.archived ? " · archived" : ""}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Button variant="ghost" onClick={() => toggleArchive(d)}>
                  {d.archived
                    ? <><ArchiveRestore className="h-4 w-4" /> Restore</>
                    : <><Archive className="h-4 w-4" /> Archive</>}
                </Button>
                {/* Only offered while nobody holds it. The server refuses either way; showing a
                    button that always fails would just teach people to distrust the page. */}
                {d.headcount === 0 && (
                  <Button variant="ghost" onClick={() => remove(d)}>
                    <Trash2 className="h-4 w-4" /> Delete
                  </Button>
                )}
              </div>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
