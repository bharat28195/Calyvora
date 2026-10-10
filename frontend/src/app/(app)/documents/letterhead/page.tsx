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
import { LetterpadUpload } from "@/components/documents/letterpad-upload";

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
        cin: draft.cin ?? "",
        gstin: draft.gstin ?? "",
        website: draft.website ?? "",
        email: draft.email ?? "",
        dateStyle: draft.dateStyle ?? "LONG",
        probationDays: draft.probationDays ?? 0,
        noticeProbation: draft.noticeProbation ?? "",
        noticePeriod: draft.noticePeriod ?? "",
        workingDays: draft.workingDays ?? "",
        workingHours: draft.workingHours ?? "",
        payDay: draft.payDay ?? "",
        jurisdiction: draft.jurisdiction ?? "",
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
          {/* First, because for most companies it is the whole answer: they have stationery and
              want letters on it, not a second design built field by field. */}
          <LetterpadUpload
            letterhead={draft}
            onChange={(next) => { setSaved(next); setDraft(next); setError(null); }}
          />

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
            <p className="mt-1 text-xs text-fg/45">Printed at the foot of every page.</p>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <Field label="CIN" htmlFor="lh-cin">
                <Input id="lh-cin" value={draft.cin ?? ""} placeholder="U62091GJ2025PTC000000" onChange={(e) => set("cin", e.target.value.toUpperCase())} />
              </Field>
              <Field label="GSTIN" htmlFor="lh-gstin">
                <Input id="lh-gstin" value={draft.gstin ?? ""} placeholder="24ABCDE1234F1Z5" onChange={(e) => set("gstin", e.target.value.toUpperCase())} />
              </Field>
              <Field label="Website" htmlFor="lh-web">
                <Input id="lh-web" value={draft.website ?? ""} placeholder="www.example.com" onChange={(e) => set("website", e.target.value)} />
              </Field>
              <Field label="Email" htmlFor="lh-email">
                <Input id="lh-email" value={draft.email ?? ""} placeholder="hr@example.com" onChange={(e) => set("email", e.target.value)} />
              </Field>
            </div>
            <div className="mt-3">
              <Field label="Anything else" htmlFor="lh-footer"
                hint="Registered office or other lines your letters must carry.">
                <textarea id="lh-footer" rows={2} className={textareaCls}
                  value={draft.footerText ?? ""}
                  placeholder={"Northwind Robotics Pvt Ltd · CIN U72900KA2019PTC000000\nconnect@northwind.example · northwind.example"}
                  onChange={(e) => set("footerText", e.target.value)} />
              </Field>
            </div>
          </Card>

          <Card>
            <CardTitle>Who signs</CardTitle>
            <p className="mt-1 text-xs text-fg/45">Printed under &quot;For {draft.heading ?? "the company"}&quot;. Leave blank to sign as whoever issues the letter.</p>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <Field label="Name" htmlFor="lh-sig-name">
                <Input id="lh-sig-name" value={draft.signatureName ?? ""} placeholder="e.g. Raunak Agarwal" onChange={(e) => set("signatureName", e.target.value)} />
              </Field>
              <Field label="Title" htmlFor="lh-sig-title">
                <Input id="lh-sig-title" value={draft.signatureTitle ?? ""} placeholder="e.g. Director" onChange={(e) => set("signatureTitle", e.target.value)} />
              </Field>
            </div>
          </Card>

          <Card>
            <CardTitle>Your standard terms</CardTitle>
            <p className="mt-1 text-xs text-fg/45">Written once, quoted by every letter that needs them — appointment, confirmation, internship.</p>
            <div className="mt-4 flex flex-col gap-3">
              <Field label="Dates on letters" htmlFor="lh-date">
                <div className="flex flex-wrap gap-1.5">
                  {([["LONG", "1 August 2026"], ["SHORT", "01 Aug, 2026"], ["NUMERIC", "01/08/2026"]] as const).map(([k, l]) => (
                    <button key={k} type="button" onClick={() => set("dateStyle", k)}
                      className={`rounded-lg border px-3 py-1.5 text-sm ${(draft.dateStyle ?? "LONG") === k ? "border-violet/40 bg-violet/10 font-medium" : "border-fg/10 hover:bg-fg/5"}`}>{l}</button>
                  ))}
                </div>
              </Field>
              <div className="grid grid-cols-2 gap-3">
                <Field label="Probation (days)" htmlFor="lh-prob">
                  <Input id="lh-prob" type="number" min={0} value={draft.probationDays ?? ""} placeholder="90"
                    onChange={(e) => set("probationDays", e.target.value === "" ? null : Number(e.target.value))} />
                </Field>
                <Field label="Notice in probation" htmlFor="lh-np">
                  <Input id="lh-np" value={draft.noticeProbation ?? ""} placeholder="ten (10) working days" onChange={(e) => set("noticeProbation", e.target.value)} />
                </Field>
                <Field label="Notice period" htmlFor="lh-n">
                  <Input id="lh-n" value={draft.noticePeriod ?? ""} placeholder="three (3) months" onChange={(e) => set("noticePeriod", e.target.value)} />
                </Field>
                <Field label="Working days" htmlFor="lh-wd">
                  <Input id="lh-wd" value={draft.workingDays ?? ""} placeholder="Monday to Friday" onChange={(e) => set("workingDays", e.target.value)} />
                </Field>
                <Field label="Working hours" htmlFor="lh-wh">
                  <Input id="lh-wh" value={draft.workingHours ?? ""} placeholder="8 to 9 hours a day" onChange={(e) => set("workingHours", e.target.value)} />
                </Field>
                <Field label="Salary paid" htmlFor="lh-pay">
                  <Input id="lh-pay" value={draft.payDay ?? ""} placeholder="on or before the 7th of the following month" onChange={(e) => set("payDay", e.target.value)} />
                </Field>
              </div>
              <Field label="Courts of (jurisdiction)" htmlFor="lh-jur">
                <Input id="lh-jur" value={draft.jurisdiction ?? ""} placeholder="Ahmedabad, Gujarat" onChange={(e) => set("jurisdiction", e.target.value)} />
              </Field>
              <p className="text-xs text-fg/45">Leave entitlement is read from your leave policies, so it never disagrees with them.</p>
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
- **Annual compensation:** INR 14,50,000.00 (Fourteen Lakh Fifty Thousand Rupees)

| EARNINGS | MONTHLY (INR) | YEARLY (INR) |
|---|---|---|
| Basic Salary | 60,416.67 | 7,25,000.00 |
| House Rent Allowance | 30,208.33 | 3,62,500.00 |
| Special Allowance | 30,208.33 | 3,62,500.00 |
| **TOTAL** | **1,20,833.33** | **14,50,000.00** |

Your appointment is subject to our standard terms of employment. Please confirm your acceptance
by signing and returning a copy of this letter.

Yours faithfully,

**For Northwind Robotics**

Ava Chen
Head of People
`;
