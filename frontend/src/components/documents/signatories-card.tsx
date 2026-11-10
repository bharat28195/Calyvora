"use client";

import { useEffect, useRef, useState } from "react";
import { Loader2, Plus, Star, Trash2, Upload } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Letterhead, Signatory } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";
import { SignaturePad } from "@/components/documents/signature-pad";

/**
 * Who signs the company's letters, with their signature, and the company seal (PD-71).
 *
 * <p>Every letter is signed by its template's signatory or the default one; their signature prints
 * just above their name, and the seal beside it. A signatory who leaves is switched off rather than
 * deleted, so the letters they already signed keep their signature.
 */
export function SignatoriesCard({
  letterhead,
  onLetterhead,
  onSignatories,
}: {
  letterhead: Letterhead;
  onLetterhead: (next: Letterhead) => void;
  /** Called whenever the list changes, so the preview beside this card can redraw. */
  onSignatories?: (list: Signatory[]) => void;
}) {
  const [list, setList] = useState<Signatory[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [title, setTitle] = useState("");
  const [signFor, setSignFor] = useState<Signatory | null>(null);
  const [drawn, setDrawn] = useState<string | null>(null);
  const [images, setImages] = useState<Record<string, string>>({});
  const [seal, setSeal] = useState<string | null>(null);
  const fileFor = useRef<string | null>(null);
  const file = useRef<HTMLInputElement>(null);

  async function load() {
    try {
      const l = await api.signatories();
      setList(l);
      onSignatories?.(l);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load signatories.");
    }
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, []);

  useEffect(() => {
    let live = true;
    (list ?? []).filter((s) => s.hasImage).forEach((s) => {
      api.markImage("signature", s.id, s.updatedAt).then((u) => { if (live) setImages((m) => ({ ...m, [s.id]: u })); }).catch(() => undefined);
    });
    return () => { live = false; };
  }, [list]);

  useEffect(() => {
    if (!letterhead.hasSeal) { setSeal(null); return; }
    api.markImage("seal", "seal", letterhead.updatedAt).then(setSeal).catch(() => setSeal(null));
  }, [letterhead.hasSeal, letterhead.updatedAt]);

  async function run<T>(work: () => Promise<T>, failure: string): Promise<T | null> {
    setBusy(true);
    setError(null);
    try {
      return await work();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : failure);
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function add() {
    const made = await run(() => api.createSignatory({ name: name.trim(), title: title.trim() || undefined }), "Could not add the signatory.");
    if (made) {
      setAdding(false);
      setName("");
      setTitle("");
      await load();
      setSignFor(made);
    }
  }

  async function saveDrawn() {
    if (!signFor || !drawn) return;
    const blob = await (await fetch(drawn)).blob();
    const ok = await run(() => api.uploadSignature(signFor.id, blob), "Could not save the signature.");
    if (ok) {
      setSignFor(null);
      setDrawn(null);
      await load();
    }
  }

  async function picked(f: File | undefined) {
    if (!f) return;
    const target = fileFor.current;
    if (target === "seal") {
      const next = await run(() => api.uploadSeal(f, f.name), "Could not upload the seal.");
      if (next) onLetterhead(next);
    } else if (target) {
      const ok = await run(() => api.uploadSignature(target, f, f.name), "Could not upload the signature.");
      if (ok) { setSignFor(null); await load(); }
    }
    if (file.current) file.current.value = "";
  }

  function pick(target: string) {
    fileFor.current = target;
    file.current?.click();
  }

  const active = (list ?? []).filter((s) => s.active);
  const inactive = (list ?? []).filter((s) => !s.active);

  return (
    <Card>
      <CardTitle>Signatures &amp; seal</CardTitle>
      <p className="mt-1 text-xs text-fg/50">
        Letters are signed by the default signatory unless a template names someone else. Their signature prints just
        above their name.
      </p>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}

      <div className="mt-4 flex flex-col gap-2">
        {list === null && <Loader2 className="h-4 w-4 animate-spin text-violet" />}
        {active.map((s) => (
          <div key={s.id} className="rounded-lg border border-fg/10 p-3">
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="text-sm font-medium">{s.name}{s.isDefault && <span className="ml-2 rounded bg-violet/15 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-violet">Default</span>}</p>
                <p className="text-xs text-fg/50">{s.title ?? "—"}</p>
              </div>
              <div className="flex gap-1">
                {!s.isDefault && (
                  <button type="button" title="Make default" disabled={busy}
                    onClick={() => void run(() => api.updateSignatory(s.id, { isDefault: true }), "Could not save.").then(load)}
                    className="rounded p-1 text-fg/40 hover:bg-fg/5 hover:text-violet"><Star className="h-4 w-4" /></button>
                )}
                <button type="button" title="Switch off (keeps their signature on letters already issued)" disabled={busy}
                  onClick={() => void run(() => api.updateSignatory(s.id, { active: false }), "Could not save.").then(load)}
                  className="rounded p-1 text-fg/40 hover:bg-fg/5 hover:text-red-400"><Trash2 className="h-4 w-4" /></button>
              </div>
            </div>
            <div className="mt-2 flex items-center gap-3">
              <div className="flex h-14 w-40 items-center justify-center rounded border border-dashed border-fg/15 bg-white">
                {s.hasImage && images[s.id]
                  // eslint-disable-next-line @next/next/no-img-element
                  ? <img src={images[s.id]} alt={`${s.name}'s signature`} className="max-h-12 max-w-[9.5rem] object-contain" />
                  : <span className="text-[11px] text-neutral-400">No signature yet</span>}
              </div>
              <Button size="sm" variant="secondary" disabled={busy} onClick={() => { setDrawn(null); setSignFor(s); }}>
                {s.hasImage ? "Replace" : "Add signature"}
              </Button>
            </div>
          </div>
        ))}
        {inactive.length > 0 && (
          <p className="text-xs text-fg/40">
            Switched off: {inactive.map((s) => s.name).join(", ")} ·{" "}
            {inactive.map((s) => (
              <button key={s.id} type="button" className="underline hover:text-fg" disabled={busy}
                onClick={() => void run(() => api.updateSignatory(s.id, { active: true }), "Could not save.").then(load)}>
                switch {s.name.split(" ")[0]} on
              </button>
            ))}
          </p>
        )}
        {adding ? (
          <div className="flex flex-col gap-2 rounded-lg border border-fg/10 p-3">
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Full name"
              className="rounded-md border border-fg/15 bg-fg/[0.03] px-3 py-1.5 text-sm" />
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Title, e.g. Head of People"
              className="rounded-md border border-fg/15 bg-fg/[0.03] px-3 py-1.5 text-sm" />
            <div className="flex gap-2">
              <Button size="sm" disabled={busy || !name.trim()} onClick={() => void add()}>Add</Button>
              <Button size="sm" variant="ghost" onClick={() => setAdding(false)}>Cancel</Button>
            </div>
          </div>
        ) : (
          <Button size="sm" variant="secondary" className="self-start" onClick={() => setAdding(true)}>
            <Plus className="h-4 w-4" /> Add a signatory
          </Button>
        )}
      </div>

      <div className="mt-5 border-t border-fg/10 pt-4">
        <p className="text-sm font-medium">Company seal</p>
        <p className="mt-0.5 text-xs text-fg/50">Optional. Printed beside the signature. A scan or photo of the stamp works; the background is removed.</p>
        <div className="mt-2 flex items-center gap-3">
          <div className="flex h-16 w-16 items-center justify-center rounded border border-dashed border-fg/15 bg-white">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            {seal ? <img src={seal} alt="Company seal" className="max-h-14 max-w-14 object-contain" /> : <span className="text-[10px] text-neutral-400">None</span>}
          </div>
          <Button size="sm" variant="secondary" disabled={busy} onClick={() => pick("seal")}>
            <Upload className="h-4 w-4" /> {letterhead.hasSeal ? "Replace" : "Upload"}
          </Button>
          {letterhead.hasSeal && (
            <Button size="sm" variant="ghost" disabled={busy}
              onClick={() => void run(() => api.removeSeal(), "Could not remove the seal.").then((l) => l && onLetterhead(l))}>
              Remove
            </Button>
          )}
        </div>
      </div>

      <input ref={file} type="file" hidden accept="image/png,image/jpeg,image/webp" onChange={(e) => void picked(e.target.files?.[0])} />

      <Modal open={!!signFor} onClose={() => setSignFor(null)} title={signFor ? `${signFor.name}'s signature` : "Signature"}>
        <p className="mb-3 text-sm text-fg/60">Draw it, type it, or upload a photo of it on white paper.</p>
        <SignaturePad onChange={setDrawn} defaultName={signFor?.name ?? ""} />
        <div className="mt-4 flex flex-wrap gap-2">
          <Button disabled={busy || !drawn} onClick={() => void saveDrawn()}>Save signature</Button>
          <Button variant="secondary" disabled={busy} onClick={() => signFor && pick(signFor.id)}>
            <Upload className="h-4 w-4" /> Upload a photo instead
          </Button>
        </div>
      </Modal>
    </Card>
  );
}
