"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, Save, RotateCcw } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Letterhead, LetterheadFont } from "@/lib/types";
import { DEFAULT_LETTERHEAD, LETTERHEAD_FONTS } from "@/lib/documents";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { LetterSheet } from "@/components/documents/letter";

const FONTS = Object.keys(LETTERHEAD_FONTS) as LetterheadFont[];
const textareaCls =
  "w-full rounded-lg border border-fg/15 bg-fg/5 p-3 text-sm leading-relaxed text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

/**
 * The company letterpad (PD-20) — set it once, and every letter comes out on it.
 *
 * <p>Edited against a live sample of a real letter rather than an abstract form, because the only
 * question that matters here is "does this look right on paper", and that cannot be answered by
 * looking at a colour picker.
 */
export default function LetterheadPage() {
  const [saved, setSaved] = useState<Letterhead | null>(null);
  const [draft, setDraft] = useState<Letterhead | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const l = await api.letterhead();
      setSaved(l);
      setDraft(l);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load the letterpad");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function set<K extends keyof Letterhead>(key: K, value: Letterhead[K]) {
    setDraft((d) => (d ? { ...d, [key]: value } : d));
    setNote(null);
  }

  const dirty = !!draft && !!saved && JSON.stringify(draft) !== JSON.stringify(saved);

  async function save() {
    if (!draft) return;
    setSaving(true);
    setError(null);
    try {
      const l = await api.saveLetterhead({
        logoUrl: draft.logoUrl ?? "",
        heading: draft.heading ?? "",
        addressLines: draft.addressLines ?? "",
        footerText: draft.footerText ?? "",
        brandColor: draft.brandColor,
        fontFamily: draft.fontFamily,
        showDivider: draft.showDivider,
        signatureName: draft.signatureName ?? "",
        signatureTitle: draft.signatureTitle ?? "",
      });
      setSaved(l);
      setDraft(l);
      setNote("Saved. Every letter from now on prints on this.");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  }

  if (!draft) {
    return (
      <div className="mt-10 flex justify-center">
        {error ? <Alert tone="error">{error}</Alert>
          : <Loader2 className="h-6 w-6 animate-spin text-violet" />}
      </div>
    );
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Letterpad</h1>
          <p className="mt-1 text-fg/50">
            Your company stationery. Set it once and every letter — offer, joining, relieving — prints on it.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={() => setDraft(saved)} disabled={!dirty}>
            <RotateCcw className="h-4 w-4" /> Discard
          </Button>
          <Button onClick={save} disabled={!dirty || saving}>
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save
          </Button>
        </div>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {note && <Alert tone="success" className="mt-6">{note}</Alert>}

      <div className="mt-8 grid gap-6 lg:grid-cols-[24rem_1fr]">
        <div className="flex flex-col gap-4">
          <Card>
            <CardTitle>The heading</CardTitle>
            <div className="mt-4 flex flex-col gap-3">
              <Field label="Logo URL" htmlFor="lh-logo"
                hint="Paste a link to your logo. A transparent PNG on a light background prints best.">
                <Input id="lh-logo" value={draft.logoUrl ?? ""} placeholder="https://…/logo.png"
                  onChange={(e) => set("logoUrl", e.target.value)} />
              </Field>
              <Field label="Company name" htmlFor="lh-heading"
                hint="Leave blank to use your company's name.">
                <Input id="lh-heading" value={draft.heading ?? ""}
                  onChange={(e) => set("heading", e.target.value)} />
              </Field>
              <Field label="Address" htmlFor="lh-address" hint="One line per line.">
                <textarea id="lh-address" rows={3} className={textareaCls}
                  value={draft.addressLines ?? ""}
                  placeholder={"42 MG Road, Indiranagar\nBengaluru 560038\n+91 80 4000 0000"}
                  onChange={(e) => set("addressLines", e.target.value)} />
              </Field>
            </div>
          </Card>

          <Card>
            <CardTitle>Type &amp; colour</CardTitle>
            <div className="mt-4 flex flex-col gap-3">
              <Field label="Typeface" htmlFor="lh-font">
                <div className="flex flex-col gap-1.5">
                  {FONTS.map((f) => (
                    <button
                      key={f}
                      type="button"
                      onClick={() => set("fontFamily", f)}
                      className={`rounded-lg border px-3 py-2 text-left transition-colors ${
                        draft.fontFamily === f
                          ? "border-violet/40 bg-violet/10"
                          : "border-fg/10 hover:bg-fg/5"
                      }`}
                    >
                      <span className="block text-sm font-medium"
                        style={{ fontFamily: LETTERHEAD_FONTS[f].stack }}>
                        {LETTERHEAD_FONTS[f].label}
                      </span>
                      <span className="block text-xs text-fg/40">{LETTERHEAD_FONTS[f].note}</span>
                    </button>
                  ))}
                </div>
              </Field>

              <Field label="Brand colour" htmlFor="lh-color">
                <div className="flex items-center gap-2">
                  <input
                    id="lh-color"
                    type="color"
                    value={normalizeForPicker(draft.brandColor)}
                    onChange={(e) => set("brandColor", e.target.value)}
                    className="h-11 w-14 cursor-pointer rounded-lg border border-fg/15 bg-fg/5 p-1"
                  />
                  <Input value={draft.brandColor} onChange={(e) => set("brandColor", e.target.value)} />
                </div>
              </Field>

              <label className="flex items-center gap-2.5 text-sm text-fg/70">
                <input
                  type="checkbox"
                  checked={draft.showDivider}
                  onChange={(e) => set("showDivider", e.target.checked)}
                  className="h-4 w-4 rounded border-fg/20 bg-fg/5 accent-violet"
                />
                Rule under the heading
              </label>
            </div>
          </Card>

          <Card>
            <CardTitle>The footer</CardTitle>
            <div className="mt-4">
              <Field label="Footer" htmlFor="lh-footer"
                hint="Registration number, GST, registered office — whatever your letters must carry.">
                <textarea id="lh-footer" rows={2} className={textareaCls}
                  value={draft.footerText ?? ""}
                  placeholder={"Northwind Robotics Pvt Ltd · CIN U72900KA2019PTC000000\nconnect@northwind.example · northwind.example"}
                  onChange={(e) => set("footerText", e.target.value)} />
              </Field>
            </div>
          </Card>
        </div>

        <div>
          <p className="mb-2 text-sm font-medium uppercase tracking-wide text-fg/40">
            How a letter will look
          </p>
          <LetterSheet body={SAMPLE_BODY} letterhead={draft} />
          <p className="mt-3 text-xs text-fg/40">
            Sample text — the letterpad is what you are editing here, not the words.
          </p>
        </div>
      </div>
    </div>
  );
}

/** `<input type="color">` only accepts #rrggbb, so anything else falls back rather than blanking. */
function normalizeForPicker(color: string): string {
  if (/^#[0-9a-f]{6}$/i.test(color)) return color;
  if (/^#[0-9a-f]{3}$/i.test(color)) {
    return `#${color.slice(1).split("").map((c) => c + c).join("")}`;
  }
  return DEFAULT_LETTERHEAD.brandColor;
}

const SAMPLE_BODY = `10 August 2026

**Private & confidential**

Dear Dana,

We are delighted to offer you the position of **Senior Engineer** at Northwind Robotics.

- **Role:** Senior Engineer
- **Department:** Engineering
- **Start date:** 1 September 2026
- **Annual compensation:** INR 1,450,000

Your appointment is subject to our standard terms of employment. Please confirm your acceptance
by signing and returning a copy of this letter.

Warm regards,

Ava Chen
Head of People
`;
