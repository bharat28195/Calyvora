"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { Loader2, Plus, Trash2, Save, FileSignature, Lock, Braces, ChevronDown, Eye } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { DocumentKind, DocumentTemplate, Letterhead, MergeField } from "@/lib/types";
import { KIND_LABELS, renderTemplate, placeholdersIn } from "@/lib/documents";
import { FormatToolbar } from "@/components/documents/format-toolbar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card } from "@/components/ui/card";
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
  const [letterhead, setLetterhead] = useState<Letterhead | null>(null);
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
    // Separate and forgiving: the letterpad is live-only, and not having one is no reason to stop
    // someone editing their templates.
    try {
      setLetterhead(await api.letterhead());
    } catch {
      setLetterhead(null);
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
        // The list stays narrow — the editor and the letter beside it are what the screen is for.
        <div className="mt-8 grid gap-6 lg:grid-cols-[14rem_minmax(0,1fr)]">
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
              letterhead={letterhead}
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
  template, fields, letterhead, onSaved, onDelete,
}: {
  template: DocumentTemplate;
  fields: MergeField[];
  letterhead: Letterhead | null;
  onSaved: (t: DocumentTemplate) => void;
  onDelete: () => void;
}) {
  const [name, setName] = useState(template.name);
  const [kind, setKind] = useState<DocumentKind>(template.kind);
  const [description, setDescription] = useState(template.description ?? "");
  const [body, setBody] = useState(template.body);
  const [useLetterhead, setUseLetterhead] = useState(template.useLetterhead);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bodyRef = useRef<HTMLTextAreaElement>(null);

  const dirty =
    name !== template.name || kind !== template.kind || body !== template.body ||
    useLetterhead !== template.useLetterhead ||
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
      onSaved(await api.updateDocTemplate(template.id, { name, kind, description, body, useLetterhead }));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {error && <Alert tone="error">{error}</Alert>}

      {/* One bar for the whole template: what it is, and what you can do with it. */}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex min-w-0 flex-1 flex-wrap items-end gap-3">
          <Field label="Name" htmlFor="t-name" className="min-w-[14rem] flex-1">
            <Input id="t-name" value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="Kind" htmlFor="t-kind" className="w-52">
            <select id="t-kind" className={selectCls} value={kind} onChange={(e) => setKind(e.target.value as DocumentKind)}>
              {KINDS.map((k) => <option key={k} value={k} className="bg-surface">{KIND_LABELS[k]}</option>)}
            </select>
          </Field>
        </div>
        <div className="flex gap-2 pb-0.5">
          <Link href={`/documents/new?template=${template.id}`}>
            <Button variant="secondary"><FileSignature className="h-4 w-4" /> Use</Button>
          </Link>
          <Button onClick={save} disabled={!dirty || saving}>
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save
          </Button>
          <Button variant="ghost" onClick={onDelete} aria-label="Delete template">
            <Trash2 className="h-4 w-4 text-red-400/80" />
          </Button>
        </div>
      </div>

      {/* Editor beside the page it produces. Side by side rather than stacked: you are writing a
          letter, and the only question while writing is what it looks like. */}
      <div className="grid gap-5 xl:grid-cols-2">
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <FormatToolbar textareaRef={bodyRef} value={body} onChange={setBody} />
            <FieldPicker fields={fields} onPick={insert} />
          </div>

          <textarea
            ref={bodyRef}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            spellCheck
            aria-label="Letter body"
            className="min-h-[32rem] w-full flex-1 resize-y rounded-xl border border-fg/15 bg-fg/5 p-5 text-[15px] leading-[1.75] text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
          />

          <div className="flex flex-wrap items-center justify-between gap-3">
            <label className="flex items-center gap-2.5 text-sm text-fg/70">
              <input
                type="checkbox"
                checked={useLetterhead}
                onChange={(e) => setUseLetterhead(e.target.checked)}
                className="h-4 w-4 rounded border-fg/20 bg-fg/5 accent-violet"
              />
              Print on the company letterpad
              <Link href="/documents/letterhead" className="text-xs font-medium text-violet hover:underline">
                Edit
              </Link>
            </label>
            <span className="text-xs text-fg/35">
              **bold** · *italic* · # heading · - list · {"{{field}}"}
            </span>
          </div>

          <Field label="When to use this" htmlFor="t-desc">
            <Input id="t-desc" value={description} onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Sent once a candidate accepts verbally" />
          </Field>
        </div>

        {/* Sticky so the letter stays in view while you scroll a long body. */}
        <div className="xl:sticky xl:top-4 xl:self-start">
          <p className="mb-2 flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-fg/40">
            <Eye className="h-3.5 w-3.5" /> How it will look
          </p>
          <LetterSheet body={sample} letterhead={useLetterhead ? letterhead : null} />
          <p className="mt-2 text-xs text-fg/35">
            Filled in with sample values. The real names and figures come from the employee you pick
            when you issue it.
          </p>
        </div>
      </div>
    </div>
  );
}

/**
 * The merge fields, grouped and behind a menu.
 *
 * <p>They were twenty-two chips in a row above the editor, which read as noise and pushed the letter
 * itself off the screen. Grouping by what they describe — the person, their pay, the company — turns
 * scanning a list into picking from three short ones.
 */
function FieldPicker({ fields, onPick }: { fields: MergeField[]; onPick: (key: string) => void }) {
  const [open, setOpen] = useState(false);

  const groups = useMemo(() => {
    const by = new Map<string, MergeField[]>();
    for (const f of fields) {
      // "employee.jobTitle" -> "employee"; a key with no dot ("today") groups under Other.
      const prefix = f.key.includes(".") ? f.key.split(".")[0] : "other";
      (by.get(prefix) ?? by.set(prefix, []).get(prefix)!).push(f);
    }
    return [...by.entries()].sort((a, b) => GROUP_ORDER.indexOf(a[0]) - GROUP_ORDER.indexOf(b[0]));
  }, [fields]);

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex h-[38px] items-center gap-1.5 rounded-lg border border-fg/10 bg-fg/5 px-3 text-sm text-fg/70 transition-colors hover:border-violet/40 hover:text-violet"
      >
        <Braces className="h-4 w-4" /> Insert field
        <ChevronDown className={`h-3.5 w-3.5 transition-transform ${open ? "rotate-180" : ""}`} />
      </button>

      {open && (
        <>
          {/* Click-away, behind the menu but above everything else. */}
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} aria-hidden />
          <div className="absolute left-0 top-full z-20 mt-1 max-h-96 w-64 overflow-y-auto rounded-xl border border-fg/15 bg-surface p-1.5 shadow-xl">
            {groups.map(([prefix, list]) => (
              <div key={prefix} className="mb-1.5 last:mb-0">
                <p className="px-2 py-1 text-[11px] font-semibold uppercase tracking-wide text-fg/35">
                  {GROUP_LABELS[prefix] ?? prefix}
                </p>
                {list.map((f) => (
                  <button
                    key={f.key}
                    onClick={() => { onPick(f.key); setOpen(false); }}
                    title={`{{${f.key}}}`}
                    className="block w-full rounded-md px-2 py-1.5 text-left text-sm text-fg/75 hover:bg-violet/10 hover:text-violet"
                  >
                    {f.label}
                  </button>
                ))}
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

const GROUP_ORDER = ["employee", "salary", "company", "signatory", "other"];
const GROUP_LABELS: Record<string, string> = {
  employee: "The person",
  salary: "Their pay",
  company: "Your company",
  signatory: "Who signs it",
  other: "Other",
};

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
