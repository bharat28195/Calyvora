"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, FileSignature, Sparkles, AlertTriangle, Plus, X, Pencil, RotateCcw, Eye, UserRound, UserPlus } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { DocumentPreview, DocumentTemplate, Employee, Letterhead, MergeField } from "@/lib/types";
import { KIND_LABELS } from "@/lib/documents";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { MemberSelect } from "@/components/ui/member-select";
import { LetterSheet } from "@/components/documents/letter";

/**
 * Generate a letter: pick a template, pick the person, and the merge fields fill themselves from the
 * People profile (feedback D2 — "fill in name → a proper document is generated"). The preview is
 * live, and anything the profile couldn't supply is called out *before* the letter is issued.
 *
 * <p>An offer letter goes to somebody who is not in the company yet, so there is no profile to read:
 * "Someone new" types the name, designation and the rest straight onto the letter. And whatever the
 * template says, the issuer can edit the finished text by hand before issuing it.
 */

/** Filled in by Orbit whoever the letter is for; still changeable under "Override a field". */
const AUTOMATIC = new Set(["company.name", "today", "signatory.name", "signatory.title", "employee.tenure"]);
/** Derived from the full name, so not asked for separately. */
const FROM_FULL_NAME = new Set(["employee.firstName", "employee.lastName"]);
const DATE_FIELDS = new Set(["employee.startDate", "employee.endDate", "salary.effectiveDate"]);

/** "2026-11-01" → "1 November 2026", the way the server writes dates into letters. */
function letterDate(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  if (!y || !m || !d) return iso;
  return new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "long", year: "numeric", timeZone: "UTC" })
    .format(new Date(Date.UTC(y, m - 1, d)));
}
export default function GenerateDocumentPage() {
  const router = useRouter();

  const [templates, setTemplates] = useState<DocumentTemplate[] | null>(null);
  const [fields, setFields] = useState<MergeField[]>([]);
  const [templateId, setTemplateId] = useState("");
  // Deep links from People ("generate a letter for this person") arrive as ?employee=…
  const [employeeId, setEmployeeId] = useState("");
  const [title, setTitle] = useState("");
  // Somebody in the company, or somebody typed in by hand (a candidate getting an offer).
  const [mode, setMode] = useState<"employee" | "new">("employee");
  // Raw yyyy-mm-dd for the date pickers; the letter gets the written-out form via overrides.
  const [dates, setDates] = useState<Record<string, string>>({});
  // The letter text as edited by hand, once the issuer starts editing. Null = the template's text.
  const [customBody, setCustomBody] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [overrides, setOverrides] = useState<Record<string, string>>({});
  const [preview, setPreview] = useState<DocumentPreview | null>(null);
  const [letterhead, setLetterhead] = useState<Letterhead | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [issuing, setIssuing] = useState(false);

  useEffect(() => {
    const q = new URLSearchParams(window.location.search);
    if (q.get("employee")) setEmployeeId(q.get("employee")!);
    if (q.get("for") === "new") setMode("new");
    const wanted = q.get("template");
    // No employee list here: the picker searches the server as you type. Fetching every employee to
    // fill one dropdown was a whole-company download on a page that issues a single letter.
    Promise.all([api.docTemplates(), api.mergeFields()])
      .then(([t, f]) => {
        setTemplates(t);
        setFields(f);
        setTemplateId(t.find((x) => x.id === wanted)?.id ?? t[0]?.id ?? "");
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load templates"));
    // So the preview is the letter as it will actually print, stationery included.
    api.letterhead().then(setLetterhead).catch(() => setLetterhead(null));
  }, []);

  // Live preview — re-renders whenever the template, the person, or an override changes.
  const refresh = useCallback(async () => {
    if (!templateId) return;
    try {
      setPreview(await api.previewDoc({ templateId, employeeId: employeeId || null, title, overrides }));
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to render the preview");
    }
  }, [templateId, employeeId, title, overrides]);

  // Debounced: the preview is a network round-trip, and firing one on every keystroke in the title
  // or an override field makes typing feel like it stalls after a word — especially against a cold
  // or distant backend. Wait for a short pause, then render once.
  useEffect(() => {
    const t = setTimeout(() => void refresh(), 350);
    return () => clearTimeout(t);
  }, [refresh]);

  const template = useMemo(() => templates?.find((t) => t.id === templateId) ?? null, [templates, templateId]);
  // A new template is a new letter: hand edits to the old one don't carry over.
  useEffect(() => { setCustomBody(null); setEditing(false); }, [templateId]);

  // What to ask for when the person is typed in: the name always (it labels the letter), then every
  // field this template uses, in catalogue order, except those Orbit fills itself.
  const newPersonFields = useMemo(() => {
    const used = new Set(template?.placeholders ?? []);
    const rest = fields.filter((f) => used.has(f.key) && f.key !== "employee.fullName"
      && !AUTOMATIC.has(f.key) && !FROM_FULL_NAME.has(f.key));
    return [{ key: "employee.fullName", label: "Full name" }, ...rest];
  }, [template, fields]);
  const asked = new Set(mode === "new" ? newPersonFields.map((f) => f.key) : []);
  const missing = (preview?.missing ?? []).filter((k) => !asked.has(k) && !(mode === "new" && FROM_FULL_NAME.has(k)));

  function switchMode(next: "employee" | "new") {
    if (next === mode) return;
    setMode(next);
    setEmployeeId("");
    setOverrides({});
    setDates({});
  }

  function setField(key: string, value: string) {
    setOverrides((o) => ({ ...o, [key]: value }));
  }

  async function issue() {
    if (!templateId) return;
    if (mode === "new" && !overrides["employee.fullName"]?.trim()) {
      setError("Type the name of the person this letter is for.");
      return;
    }
    setIssuing(true);
    try {
      const doc = await api.generateDoc({
        templateId, employeeId: mode === "employee" ? employeeId || null : null, title, overrides,
        body: customBody ?? undefined,
      });
      router.push(`/documents/${doc.id}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to generate the document");
      setIssuing(false);
    }
  }

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Generate a document</h1>
        <p className="mt-1 text-fg/50">
          Pick a template and a person — everything else fills itself in from their profile.
        </p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {templates === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <div className="mt-8 grid gap-6 lg:grid-cols-[22rem_1fr]">
          {/* ---- controls ---- */}
          <div className="flex flex-col gap-4">
            <Card>
              <CardTitle>Template</CardTitle>
              <div className="mt-3 flex flex-col gap-1.5">
                {templates.map((t) => (
                  <button
                    key={t.id}
                    onClick={() => setTemplateId(t.id)}
                    className={`rounded-lg border px-3 py-2 text-left text-sm transition-colors ${
                      t.id === templateId
                        ? "border-violet/40 bg-violet/10 text-violet"
                        : "border-fg/10 text-fg/70 hover:bg-fg/5"
                    }`}
                  >
                    <span className="block font-medium">{t.name}</span>
                    <span className="block text-xs text-fg/40">{KIND_LABELS[t.kind]}</span>
                  </button>
                ))}
              </div>
            </Card>

            <Card>
              <CardTitle>For</CardTitle>
              <div className="mt-3 grid grid-cols-2 gap-1 rounded-lg bg-fg/5 p-1 text-sm">
                <button type="button" onClick={() => switchMode("employee")}
                  className={`inline-flex items-center justify-center gap-1.5 rounded-md px-2 py-1.5 ${mode === "employee" ? "bg-surface font-medium text-violet shadow-sm" : "text-fg/60 hover:text-fg"}`}>
                  <UserRound className="h-3.5 w-3.5" /> Employee
                </button>
                <button type="button" onClick={() => switchMode("new")}
                  className={`inline-flex items-center justify-center gap-1.5 rounded-md px-2 py-1.5 ${mode === "new" ? "bg-surface font-medium text-violet shadow-sm" : "text-fg/60 hover:text-fg"}`}>
                  <UserPlus className="h-3.5 w-3.5" /> Someone new
                </button>
              </div>
              <div className="mt-3 flex flex-col gap-3">
                {mode === "employee" ? (
                  <Field label="Employee" htmlFor="doc-emp">
                    <MemberSelect
                      value={employeeId}
                      onChange={setEmployeeId}
                      placeholder="Nobody selected"
                    />
                  </Field>
                ) : (
                  <>
                    <p className="text-xs text-fg/50">
                      For somebody who isn&apos;t in Orbit yet — a candidate getting an offer, say. Type the
                      details that go on the letter.
                    </p>
                    {newPersonFields.map((f) => (
                      <Field key={f.key} label={f.label} htmlFor={`new-${f.key}`}>
                        {DATE_FIELDS.has(f.key) ? (
                          <Input id={`new-${f.key}`} type="date" value={dates[f.key] ?? ""}
                            onChange={(e) => {
                              setDates((d) => ({ ...d, [f.key]: e.target.value }));
                              setField(f.key, e.target.value ? letterDate(e.target.value) : "");
                            }} />
                        ) : (
                          <Input id={`new-${f.key}`} value={overrides[f.key] ?? ""}
                            onChange={(e) => setField(f.key, e.target.value)}
                            placeholder={PLACEHOLDERS[f.key] ?? ""} />
                        )}
                      </Field>
                    ))}
                  </>
                )}
                <Field label="Title (optional)" htmlFor="doc-title">
                  <Input
                    id="doc-title"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder={preview?.title ?? "Auto-named from the template"}
                  />
                </Field>
              </div>
            </Card>

            {missing.length > 0 && (
              <Card className="border-amber-500/30 bg-amber-500/5">
                <p className="flex items-center gap-2 text-sm font-medium text-amber-500">
                  <AlertTriangle className="h-4 w-4" /> {missing.length} field{missing.length === 1 ? "" : "s"} unfilled
                </p>
                <p className="mt-1 text-xs text-fg/50">
                  {mode === "new"
                    ? "Fill them in below — they go on the letter only."
                    : "Not on this profile yet. Fill them in below, or update the person in People."}
                </p>
                <div className="mt-3 flex flex-col gap-2">
                  {missing.map((key) => (
                    <Field key={key} label={labelFor(fields, key)} htmlFor={`ov-${key}`}>
                      <Input
                        id={`ov-${key}`}
                        value={overrides[key] ?? ""}
                        onChange={(e) => setOverrides((o) => ({ ...o, [key]: e.target.value }))}
                        placeholder={key}
                      />
                    </Field>
                  ))}
                </div>
              </Card>
            )}

            <OverridePanel
              fields={fields}
              overrides={overrides}
              missing={missing}
              onChange={setOverrides}
            />
          </div>

          {/* ---- live preview ---- */}
          <div>
            <div className="mb-3 flex items-center justify-between gap-3">
              <p className="text-sm font-medium uppercase tracking-wide text-fg/40">
                <Sparkles className="mr-1 inline h-3.5 w-3.5" /> Live preview
              </p>
              <div className="flex flex-wrap items-center gap-2">
                {preview && !editing && (
                  <Button variant="secondary" onClick={() => { setCustomBody((b) => b ?? preview.body); setEditing(true); }}>
                    <Pencil className="h-4 w-4" /> Edit text
                  </Button>
                )}
                {editing && (
                  <Button variant="secondary" onClick={() => setEditing(false)}>
                    <Eye className="h-4 w-4" /> Preview
                  </Button>
                )}
                <Button onClick={issue} disabled={issuing || !templateId}>
                  {issuing ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileSignature className="h-4 w-4" />}
                  Issue document
                </Button>
              </div>
            </div>
            {customBody !== null && (
              <Alert tone="info" className="mb-3">
                <span className="flex flex-wrap items-center justify-between gap-2">
                  <span>Edited by hand — this text is issued exactly as written. Changing the fields on the left won&apos;t update it.</span>
                  <button type="button" className="inline-flex items-center gap-1 text-xs font-medium text-violet hover:underline"
                    onClick={() => { setCustomBody(null); setEditing(false); }}>
                    <RotateCcw className="h-3.5 w-3.5" /> Back to the template
                  </button>
                </span>
              </Alert>
            )}
            {editing && customBody !== null ? (
              <textarea
                aria-label="Letter text"
                value={customBody}
                onChange={(e) => setCustomBody(e.target.value)}
                className="min-h-[36rem] w-full rounded-xl border border-fg/15 bg-surface p-5 font-mono text-sm leading-relaxed text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
              />
            ) : preview ? (
              <LetterSheet body={customBody ?? preview.body} letterhead={preview.useLetterhead ? letterhead : null} />
            ) : (
              <Card className="text-sm text-fg/50">
                {template ? "Rendering…" : "Pick a template to see the letter."}
              </Card>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/** Example values, so the form says what kind of answer each field wants. */
const PLACEHOLDERS: Record<string, string> = {
  "employee.fullName": "e.g. Priya Sharma",
  "employee.jobTitle": "e.g. Senior Designer",
  "employee.department": "e.g. Design",
  "employee.manager": "e.g. Arjun Rao",
  "employee.employmentType": "e.g. Full time",
  "employee.workLocation": "e.g. Bengaluru",
  "employee.email": "e.g. priya@example.com",
  "employee.phone": "e.g. +91 98765 43210",
  "salary.annual": "e.g. 12,00,000",
  "salary.monthly": "e.g. 1,00,000",
  "salary.currency": "e.g. INR",
};

function labelFor(fields: MergeField[], key: string): string {
  return fields.find((f) => f.key === key)?.label ?? key;
}

/** Lets the issuer override any field the profile *did* fill — e.g. a different signatory. */
function OverridePanel({
  fields, overrides, missing, onChange,
}: {
  fields: MergeField[];
  overrides: Record<string, string>;
  missing: string[];
  onChange: (next: Record<string, string>) => void;
}) {
  const [open, setOpen] = useState(false);
  const extra = Object.keys(overrides).filter((k) => !missing.includes(k));

  return (
    <Card>
      <button onClick={() => setOpen((v) => !v)} className="flex w-full items-center justify-between text-left">
        <CardTitle>Override a field</CardTitle>
        <Plus className={`h-4 w-4 text-fg/40 transition-transform ${open ? "rotate-45" : ""}`} />
      </button>
      {open && (
        <div className="mt-3 grid gap-2">
          {fields.filter((f) => !missing.includes(f.key)).map((f) => (
            <Field key={f.key} label={f.label} htmlFor={`extra-${f.key}`}>
              <Input
                id={`extra-${f.key}`}
                value={overrides[f.key] ?? ""}
                onChange={(e) => onChange({ ...overrides, [f.key]: e.target.value })}
                placeholder="Leave blank to use the profile value"
              />
            </Field>
          ))}
        </div>
      )}
      {!open && extra.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {extra.map((k) => (
            <span key={k} className="inline-flex items-center gap-1 rounded-full bg-violet/10 px-2 py-0.5 text-xs text-violet">
              {k}
              <button
                onClick={() => {
                  const next = { ...overrides };
                  delete next[k];
                  onChange(next);
                }}
                aria-label={`Clear ${k}`}
              >
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
        </div>
      )}
    </Card>
  );
}
