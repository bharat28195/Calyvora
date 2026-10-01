-- V41__letterhead_and_exit.sql — the letterpad, and exit formalities (PD-20).
--
-- Three changes, one theme: a letter should look like the company's own stationery, and the two
-- moments that produce letters — someone joining, someone leaving — should drive themselves rather
-- than sit in an HR person's head.
--
-- 1. `letterheads` — one row per company. The letterpad: logo, colours, type, the address block at
--    the top and the strip at the bottom. Held apart from `company_settings` because that table is
--    operational configuration (currency, timezone, locale) read on nearly every request, whereas
--    this is presentation read only when a letter is rendered. Separate lifetimes, separate rows.
-- 2. `document_templates.use_letterhead` — a per-template opt-out. Most letters want the letterpad;
--    an internal memo or a template that already carries its own heading does not.
-- 3. `onboarding_tasks.kind` — the same checklist machinery now runs exits. Deliberately NOT a new
--    table: a checklist item is a checklist item, and duplicating the table would duplicate the
--    repository, the service, the RLS policy and the UI to gain nothing. The default keeps every
--    existing row an onboarding task.

create table letterheads (
    company_id      uuid primary key references companies(id) on delete cascade,
    logo_url        varchar(500),
    -- Blank falls back to the company's own name at render time, so a company that never opens this
    -- screen still gets a correctly-headed letter.
    heading         varchar(160),
    address_lines   text,                                    -- one line per newline, under the heading
    footer_text     text,                                    -- e.g. CIN / GST / registered office
    brand_color     varchar(9)  not null default '#7c5cff',  -- #rgb, #rrggbb or #rrggbbaa
    font_family     varchar(24) not null default 'SERIF',    -- SERIF | SANS | SLAB
    show_divider    boolean     not null default true,
    signature_name  varchar(120),
    signature_title varchar(120),
    updated_at      timestamptz not null default now()
);

alter table letterheads enable row level security;
alter table letterheads force row level security;
-- Keyed on company_id, which is this table's primary key rather than a separate column — the policy
-- shape is otherwise identical to V12's.
create policy tenant_isolation on letterheads
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table document_templates add column use_letterhead boolean not null default true;
-- Copied onto the letter at issue time rather than followed back to the template, which may since
-- have been edited or deleted. The body is already frozen for that reason; how it was headed is
-- part of the same record.
alter table generated_documents add column use_letterhead boolean not null default true;

alter table onboarding_tasks add column kind varchar(16) not null default 'ONBOARDING';  -- | EXIT
create index idx_onboarding_tasks_employee_kind on onboarding_tasks (employee_id, kind);

-- Exit context. `end_date` on employees already records the last working day and stays the single
-- source of truth for it; these only add why and when the exit was started, which the relieving
-- letter and the checklist need and nothing else records.
alter table employees add column exit_reason varchar(200);
alter table employees add column exit_started_at timestamptz;

-- Hiring someone from the recruitment pipeline agrees their role and start date *before* they have a
-- login, and an employee row cannot exist without one (employees.user_id). So the agreed details
-- ride on the invitation and are applied when the profile is first created — see
-- EmployeeService.getOrCreate. Nullable throughout: an ordinary invitation carries none of this.
alter table invitations add column job_title varchar(120);
alter table invitations add column start_date date;
alter table invitations add column department_id uuid references departments(id);
alter table invitations add column onboarding_seeded boolean not null default false;
