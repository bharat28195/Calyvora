/**
 * Merge-field rendering and the starter template library — the frontend twin of the backend's
 * `MergeFields` / `StarterTemplates` (feedback D2/D3). Kept in sync deliberately: the mock backend
 * has to produce the same letter the real one would, or the demo lies about the product.
 */
import type { DocumentKind, Letterhead, LetterheadFont, MergeField } from "@/lib/types";

const TOKEN = /\{\{\s*([\w.]+)\s*\}\}/g;

/** Shown when a field has no value — a letter should never go out with a raw {{token}} in it. */
export const EMPTY = "—";

export function renderTemplate(body: string, values: Record<string, string>): string {
  if (!body) return "";
  return body.replace(TOKEN, (_m, key: string) => {
    const v = values[key];
    return v == null || v.trim() === "" ? EMPTY : v;
  });
}

/** The distinct fields a body references, in first-seen order. */
export function placeholdersIn(body: string): string[] {
  const found: string[] = [];
  for (const m of (body ?? "").matchAll(TOKEN)) {
    if (!found.includes(m[1])) found.push(m[1]);
  }
  return found;
}

/** "4 March 2026" — the way a letter reads. */
export function letterDate(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const d = new Date(iso.length <= 10 ? `${iso}T00:00:00` : iso);
  if (Number.isNaN(d.getTime())) return null;
  return `${d.getDate()} ${d.toLocaleString("en", { month: "long" })} ${d.getFullYear()}`;
}

/** Human tenure between two dates ("2 years 3 months"). */
export function tenure(from: string | null, to: string | null): string | null {
  if (!from) return null;
  const start = new Date(`${from}T00:00:00`);
  const end = to ? new Date(`${to}T00:00:00`) : new Date();
  if (Number.isNaN(start.getTime()) || end < start) return null;
  let months = (end.getFullYear() - start.getFullYear()) * 12 + (end.getMonth() - start.getMonth());
  if (end.getDate() < start.getDate()) months -= 1;
  const years = Math.floor(months / 12);
  const rest = months % 12;
  if (years === 0 && rest === 0) {
    const days = Math.round((end.getTime() - start.getTime()) / 86_400_000);
    return `${days} ${days === 1 ? "day" : "days"}`;
  }
  return [
    years > 0 ? `${years} ${years === 1 ? "year" : "years"}` : "",
    rest > 0 ? `${rest} ${rest === 1 ? "month" : "months"}` : "",
  ].filter(Boolean).join(" ");
}

/** The catalogue offered in the template editor (mirrors the backend's `/documents/fields`). */
export const MERGE_FIELDS: MergeField[] = [
  { key: "employee.fullName", label: "Full name" },
  { key: "employee.firstName", label: "First name" },
  { key: "employee.lastName", label: "Last name" },
  { key: "employee.email", label: "Work email" },
  { key: "employee.employeeNo", label: "Employee ID" },
  { key: "employee.jobTitle", label: "Job title" },
  { key: "employee.department", label: "Department" },
  { key: "employee.manager", label: "Reporting manager" },
  { key: "employee.employmentType", label: "Employment type" },
  { key: "employee.workLocation", label: "Work location" },
  { key: "employee.phone", label: "Phone" },
  { key: "employee.startDate", label: "Start date" },
  { key: "employee.endDate", label: "Last working day" },
  { key: "employee.tenure", label: "Tenure (computed)" },
  { key: "salary.annual", label: "Annual compensation" },
  { key: "salary.monthly", label: "Monthly compensation" },
  { key: "salary.currency", label: "Currency" },
  { key: "salary.effectiveDate", label: "Compensation effective from" },
  { key: "company.name", label: "Company name" },
  { key: "today", label: "Today's date" },
  { key: "signatory.name", label: "Signed by (you)" },
  { key: "signatory.title", label: "Signatory's title" },
];

/**
 * The three faces a letterpad can print in. Deliberately system stacks rather than webfonts: a
 * letter is printed and emailed as often as it is viewed, and a font that fails to load would change
 * the document rather than the page.
 */
export const LETTERHEAD_FONTS: Record<LetterheadFont, { label: string; stack: string; note: string }> = {
  SERIF: {
    label: "Serif",
    stack: 'Georgia, "Times New Roman", "Nimbus Roman", serif',
    note: "Traditional and formal — the usual choice for letters",
  },
  SANS: {
    label: "Sans",
    stack: '"Segoe UI", Inter, Helvetica, Arial, sans-serif',
    note: "Clean and modern, matches most brand guidelines",
  },
  SLAB: {
    label: "Slab",
    stack: '"Rockwell", "Roboto Slab", "Courier New", Georgia, serif',
    note: "Heavier and more distinctive — good with a strong logo",
  },
};

/** What a company that has never opened the editor prints on. */
export const DEFAULT_LETTERHEAD: Letterhead = {
  logoUrl: null,
  heading: null,
  addressLines: null,
  footerText: null,
  brandColor: "#7c5cff",
  fontFamily: "SERIF",
  showDivider: true,
  signatureName: null,
  signatureTitle: null,
  updatedAt: "",
};

/** Human labels for the document kinds. */
export const KIND_LABELS: Record<DocumentKind, string> = {
  OFFER_LETTER: "Offer letter",
  JOINING_LETTER: "Joining letter",
  RELIEVING_LETTER: "Relieving letter",
  EXPERIENCE_LETTER: "Experience certificate",
  PROMOTION_LETTER: "Promotion / increment",
  CUSTOM: "Custom",
};

export interface StarterTemplate {
  name: string;
  kind: DocumentKind;
  description: string;
  body: string;
}

export const STARTER_TEMPLATES: StarterTemplate[] = [
  {
    name: "Offer letter",
    kind: "OFFER_LETTER",
    description: "Extends a formal offer with role, start date and compensation.",
    body: `{{company.name}}

{{today}}

**Private & confidential**

Dear {{employee.firstName}},

We are delighted to offer you the position of **{{employee.jobTitle}}** at {{company.name}}.

- **Role:** {{employee.jobTitle}}
- **Department:** {{employee.department}}
- **Employment type:** {{employee.employmentType}}
- **Start date:** {{employee.startDate}}
- **Location:** {{employee.workLocation}}
- **Annual compensation:** {{salary.currency}} {{salary.annual}}

Your appointment is subject to our standard terms of employment. We believe your
experience will be a strong addition to the team and we look forward to working with you.

Please confirm your acceptance by signing and returning a copy of this letter.

Warm regards,

{{signatory.name}}
{{signatory.title}}
{{company.name}}
`,
  },
  {
    name: "Joining letter",
    kind: "JOINING_LETTER",
    description: "Confirms that an employee has joined, with role and start date.",
    body: `{{company.name}}

{{today}}

**To whom it may concern**

This is to confirm that **{{employee.fullName}}** (Employee ID {{employee.employeeNo}}) has
joined {{company.name}} as **{{employee.jobTitle}}** in the {{employee.department}} department,
effective **{{employee.startDate}}**.

{{employee.firstName}} is based at {{employee.workLocation}} and reports to {{employee.manager}}.

We warmly welcome {{employee.firstName}} to the team and wish them a successful tenure with us.

Sincerely,

{{signatory.name}}
{{signatory.title}}
{{company.name}}
`,
  },
  {
    name: "Relieving letter",
    kind: "RELIEVING_LETTER",
    description: "Issued on exit — confirms the last working day and clearance.",
    body: `{{company.name}}

{{today}}

**To whom it may concern**

This is to certify that **{{employee.fullName}}** (Employee ID {{employee.employeeNo}}) was
employed with {{company.name}} as **{{employee.jobTitle}}** from **{{employee.startDate}}**
to **{{employee.endDate}}**.

{{employee.firstName}} has been relieved of their duties with effect from the close of
business on {{employee.endDate}}. All company property has been returned and dues settled.

We thank {{employee.firstName}} for their contribution and wish them every success ahead.

Sincerely,

{{signatory.name}}
{{signatory.title}}
{{company.name}}
`,
  },
  {
    name: "Experience certificate",
    kind: "EXPERIENCE_LETTER",
    description: "Certifies tenure, role and conduct for a departing employee.",
    body: `{{company.name}}

{{today}}

**To whom it may concern**

This is to certify that **{{employee.fullName}}** served {{company.name}} as
**{{employee.jobTitle}}** in the {{employee.department}} department from
**{{employee.startDate}}** to **{{employee.endDate}}** ({{employee.tenure}}).

During this period we found {{employee.firstName}} to be diligent, professional and
well regarded by colleagues. Their conduct throughout the engagement was satisfactory.

This certificate is issued on request.

Sincerely,

{{signatory.name}}
{{signatory.title}}
{{company.name}}
`,
  },
  {
    name: "Promotion / increment letter",
    kind: "PROMOTION_LETTER",
    description: "Confirms a new title and revised compensation.",
    body: `{{company.name}}

{{today}}

Dear {{employee.firstName}},

In recognition of your performance and contribution, we are pleased to confirm your
revised role and compensation at {{company.name}}.

- **Revised designation:** {{employee.jobTitle}}
- **Department:** {{employee.department}}
- **Revised annual compensation:** {{salary.currency}} {{salary.annual}}
- **Effective from:** {{salary.effectiveDate}}

All other terms of your employment remain unchanged. Congratulations, and thank you for
the work you continue to put in.

Warm regards,

{{signatory.name}}
{{signatory.title}}
{{company.name}}
`,
  },
];
