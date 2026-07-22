"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { Loader2, Plus, Trash2, Save, FileSignature, Lock } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { DocumentKind, DocumentTemplate, MergeField } from "@/lib/types";
import { KIND_LABELS, renderTemplate, placeholdersIn } from "@/lib/documents";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { LetterSheet } from "@/components/documents/letter";

const KINDS = Object.keys(KIND_LABELS) as DocumentKind[];
const selectCls =
  "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

/**
 * The template library (feedback D3). Starter templates are seeded on first open and are fully
 * editable — a company's letters should read like theirs. Clicking a merge field inserts it at the
 * cursor, and the sample preview shows the shape of the letter as you type.
 */
export default function TemplatesPage() {
  const [templates, setTemplates] = useState<DocumentTemplate[] | null>(null);
  const [fields, setFields] = useState<MergeField[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (keep?: string) => {
    try {
      const [t, f] = await Promise.all([api.docTemplates(), api.mergeFields()]);
      setTemplates(t);
      setFields(f);
      setSelectedId((cur) => keep ?? cur ?? t[0]?.id ?? null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load templates");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const selected = templates?.find((t) => t.id === selectedId) ?? null;

  async function create() {
    const t = await api.createDocTemplate({
      name: "Untitled template",
      kind: "CUSTOM",
      body: "{{company.name}}\n\n{{today}}\n\nDear {{employee.firstName}},\n\n…\n\n{{signatory.name}}\n",
    });
    await load(t.id);
  }

  async function remove(t: DocumentTemplate) {
    if (!confirm(`Delete "${t.name}"? Letters already issued from it are unaffected.`)) return;
    await api.deleteDocTemplate(t.id);
    setSelectedId(null);
    await load();
  }

  return (
    <div>
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Templates</h1>
          <p className="mt-1 text-fg/50">The letters your company can issue. Edit them to sound like you.</p>
        </div>
        <Button onClick={create}><Plus className="h-4 w-4" /> New template</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {templates === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <div className="mt-8 grid gap-6 lg:grid-cols-[18rem_1fr]">
          <div className="flex flex-col gap-1.5">
            {templates.map((t) => (
              <button
                key={t.id}
                onClick={() => setSelectedId(t.id)}
                className={`rounded-lg border px-3 py-2 text-left text-sm transition-colors ${
                  t.id === selectedId
                    ? "border-violet/40 bg-violet/10 text-violet"
                    : "border-fg/10 text-fg/70 hover:bg-fg/5"
                }`}
              >
                <span className="flex items-center gap-1.5 font-medium">
                  {t.name}
                  {t.builtIn && <Lock className="h-3 w-3 text-fg/30" aria-label="Starter template" />}
                </span>
                <span className="block text-xs text-fg/40">{KIND_LABELS[t.kind]}</span>
              </button>
            ))}
          </div>

          {selected ? (
            <TemplateEditor
              key={selected.id}
              template={selected}
              fields={fields}
              onSaved={(t) => load(t.id)}
              onDelete={() => remove(selected)}
            />
          ) : (
            <Card className="text-sm text-fg/50">Select a template to edit it.</Card>
          )}
        </div>
      )}
    </div>
  );
}

function TemplateEditor({
  template, fields, onSaved, onDelete,
}: {
  template: DocumentTemplate;
  fields: MergeField[];
  onSaved: (t: DocumentTemplate) => void;
  onDelete: () => void;
}) {
  const [name, setName] = useState(template.name);
  const [kind, setKind] = useState<DocumentKind>(template.kind);
  const [description, setDescription] = useState(template.description ?? "");
  const [body, setBody] = useState(template.body);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bodyRef = useRef<HTMLTextAreaElement>(null);

  const dirty =
    name !== template.name || kind !== template.kind || body !== template.body ||
    description !== (template.description ?? "");

  /** Sample values so the preview reads like a letter instead of a form. */
  const sample = useMemo(() => {
    const values: Record<string, string> = {};
    for (const key of placeholdersIn(body)) values[key] = SAMPLE[key] ?? `«${key}»`;
    return renderTemplate(body, values);
  }, [body]);

  function insert(key: string) {
    const el = bodyRef.current;
    const token = `{{${key}}}`;
    if (!el) {
      setBody((b) => b + token);
      return;
    }
    const start = el.selectionStart ?? body.length;
    const end = el.selectionEnd ?? start;
    const next = body.slice(0, start) + token + body.slice(end);
    setBody(next);
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(start + token.length, start + token.length);
    });
  }

  async function save() {
    setSaving(true);
    setError(null);
    try {
      onSaved(await api.updateDocTemplate(template.id, { name, kind, description, body }));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {error && <Alert tone="error">{error}</Alert>}

      <Card>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Name" htmlFor="t-name">
            <Input id="t-name" value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="Kind" htmlFor="t-kind">
            <select id="t-kind" className={selectCls} value={kind} onChange={(e) => setKind(e.target.value as DocumentKind)}>
              {KINDS.map((k) => <option key={k} value={k} className="bg-surface">{KIND_LABELS[k]}</option>)}
            </select>
          </Field>
        </div>
        <div className="mt-3">
          <Field label="Description" htmlFor="t-desc">
            <Input id="t-desc" value={description} onChange={(e) => setDescription(e.target.value)}
              placeholder="When should someone reach for this template?" />
          </Field>
        </div>
      </Card>

      <Card>
        <div className="flex items-center justify-between gap-3">
          <CardTitle>Body</CardTitle>
          <div className="flex gap-2">
            <Link href={`/documents/new?template=${template.id}`}>
              <Button variant="secondary" size="sm"><FileSignature className="h-4 w-4" /> Use</Button>
            </Link>
            <Button size="sm" onClick={save} disabled={!dirty || saving}>
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save
            </Button>
            <Button variant="ghost" size="sm" onClick={onDelete} aria-label="Delete template">
              <Trash2 className="h-4 w-4 text-red-400/80" />
            </Button>
          </div>
        </div>

        <p className="mt-3 text-xs text-fg/40">Click a field to insert it at the cursor:</p>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {fields.map((f) => (
            <button
              key={f.key}
              onClick={() => insert(f.key)}
              title={`{{${f.key}}}`}
              className="rounded-full border border-fg/10 px-2 py-0.5 text-xs text-fg/60 hover:border-violet/40 hover:bg-violet/10 hover:text-violet"
            >
              {f.label}
            </button>
          ))}
        </div>

        <textarea
          ref={bodyRef}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          spellCheck={false}
          className="mt-4 h-80 w-full rounded-lg border border-fg/15 bg-fg/5 p-3 font-mono text-xs leading-relaxed text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
        />
      </Card>

      <div>
        <p className="mb-2 text-sm font-medium uppercase tracking-wide text-fg/40">Sample</p>
        <LetterSheet body={sample} />
      </div>
    </div>
  );
}

/** Stand-in values for the editor preview — never used when a real letter is generated. */
const SAMPLE: Record<string, string> = {
  "employee.fullName": "Dana Scully",
  "employee.firstName": "Dana",
  "employee.lastName": "Scully",
  "employee.email": "dana@example.com",
  "employee.employeeNo": "EMP-0142",
  "employee.jobTitle": "Senior Engineer",
  "employee.department": "Engineering",
  "employee.manager": "Marcus Webb",
  "employee.employmentType": "Full time",
  "employee.workLocation": "Bengaluru",
  "employee.phone": "+91 90000 00000",
  "employee.startDate": "1 April 2024",
  "employee.endDate": "31 March 2026",
  "employee.tenure": "2 years",
  "salary.annual": "1,450,000",
  "salary.monthly": "120,833",
  "salary.currency": "INR",
  "salary.effectiveDate": "1 April 2026",
  "company.name": "Northwind Robotics",
  today: "22 July 2026",
  "signatory.name": "Ava Chen",
  "signatory.title": "Founder",
};
