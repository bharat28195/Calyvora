"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, FileSignature, Sparkles, AlertTriangle, Plus, X } from "lucide-react";
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
 */
export default function GenerateDocumentPage() {
  const router = useRouter();

  const [templates, setTemplates] = useState<DocumentTemplate[] | null>(null);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [fields, setFields] = useState<MergeField[]>([]);
  const [templateId, setTemplateId] = useState("");
  // Deep links from People ("generate a letter for this person") arrive as ?employee=…
  const [employeeId, setEmployeeId] = useState("");
  const [title, setTitle] = useState("");
  const [overrides, setOverrides] = useState<Record<string, string>>({});
  const [preview, setPreview] = useState<DocumentPreview | null>(null);
  const [letterhead, setLetterhead] = useState<Letterhead | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [issuing, setIssuing] = useState(false);

  useEffect(() => {
    const q = new URLSearchParams(window.location.search);
    if (q.get("employee")) setEmployeeId(q.get("employee")!);
    const wanted = q.get("template");
    Promise.all([api.docTemplates(), api.listEmployees(), api.mergeFields()])
      .then(([t, e, f]) => {
        setTemplates(t);
        setEmployees(e);
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

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const template = useMemo(() => templates?.find((t) => t.id === templateId) ?? null, [templates, templateId]);
  const missing = preview?.missing ?? [];

  async function issue() {
    if (!templateId) return;
    setIssuing(true);
    try {
      const doc = await api.generateDoc({ templateId, employeeId: employeeId || null, title, overrides });
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
              <div className="mt-3 flex flex-col gap-3">
                <Field label="Employee" htmlFor="doc-emp">
                  <MemberSelect
                    employees={employees}
                    value={employeeId}
                    onChange={setEmployeeId}
                    placeholder="Nobody selected"
                  />
                </Field>
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
                  Not on this profile yet. Fill them in below, or update the person in People.
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
              <Button onClick={issue} disabled={issuing || !templateId}>
                {issuing ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileSignature className="h-4 w-4" />}
                Issue document
              </Button>
            </div>
            {preview ? (
              <LetterSheet body={preview.body} letterhead={preview.useLetterhead ? letterhead : null} />
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
