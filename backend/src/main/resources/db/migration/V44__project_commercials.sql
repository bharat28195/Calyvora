-- V44__project_commercials.sql — what a project is worth, and who it is for.
--
-- Groundwork for project profitability (PD-22, D2). We already hold what people
-- cost (compensation), what they delivered (work), and who the customers are
-- (clients) — but nothing joined delivery to a customer or to money, so the one
-- question a services business actually asks ("is this engagement making money?")
-- could not be answered at all.
--
-- Deliberately minimal: a project belongs to at most one client and carries one
-- agreed fee. Rate cards, milestones, and per-role pricing are real things we do
-- not have evidence we need yet.

alter table projects add column client_id uuid references clients(id);

-- Internal work (our own product, R&D, admin) is the default: most projects in a
-- young company are not sold to anyone, and defaulting to billable would invent
-- revenue that does not exist.
alter table projects add column billable boolean not null default false;

-- The agreed fee for the whole engagement, in the company's currency (Settings →
-- Localization). Null on a billable project simply means "not agreed yet" — the
-- cost side still reports, the margin side says it cannot.
alter table projects add column contract_value numeric(14, 2);

create index idx_projects_client on projects (client_id);
