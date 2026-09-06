-- V47__plans.sql — packages of features, priced.
--
-- Until now every customer got every module. That is the right default for a product finding its
-- feet and the wrong one for selling: a ten-person firm that wants attendance and payroll should not
-- be quoted the same as one that also wants recruitment, performance and the work tracker.
--
-- A plan is a named set of features with a price. Assigning one to a company answers "what did they
-- buy"; a per-company override (company_features, V46) answers "what did we agree with them anyway".
-- The override wins, because a promise made in a sales call outranks a table.
--
-- NOTHING CHANGES FOR ANY EXISTING CUSTOMER. Companies start with no plan, and a company with no plan
-- falls back to each feature's own default — and every module defaults on. A plan has to be assigned
-- deliberately before anybody loses a screen.

-- ---------------------------------------------------------------------------
-- plans — what you sell.
--
-- No row-level security, same reasoning as company_features: these are the vendor's catalogue, not
-- tenant data, and the owner reads and writes them from a session bound to the platform company.
-- ---------------------------------------------------------------------------
create table plans (
    code               varchar(32) primary key,
    name               varchar(80)  not null,
    description        varchar(300),
    -- Per employee per month, in the company's own currency. Null means "use the published price
    -- list" — a plan can restrict features without changing what anybody is charged, which is
    -- exactly what a grandfathered customer needs.
    price_per_employee numeric(10, 2),
    sort_order         int          not null default 0,
    active             boolean      not null default true,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now()
);

-- ---------------------------------------------------------------------------
-- plan_features — which features a plan includes.
--
-- A join table rather than a comma-separated column so "which plans include recruitment?" is a query
-- rather than a string search, and so a typo in a feature name cannot hide inside a longer string.
-- ON DELETE CASCADE because a plan's feature list has no meaning without the plan.
-- ---------------------------------------------------------------------------
create table plan_features (
    plan_code varchar(32) not null references plans(code) on delete cascade,
    feature   varchar(48) not null,
    primary key (plan_code, feature)
);

-- The plan a company is on. Null = no plan, so feature defaults apply.
--
-- On COMPANIES, not subscriptions, and the first attempt got this wrong. Subscriptions look like the
-- natural home — seats, price and end date already live there — but a subscription row is created
-- LAZILY (BillingService.getOrCreate) the first time billing is read, so a company that
-- self-registered has none. Putting the plan there meant a brand-new customer could not be given one
-- until somebody happened to open their billing page, which the tests found immediately.
--
-- The deeper reason it belongs here: entitlement is not a billing fact. What a company may use has to
-- be answerable on every request, including for a trial that has never been invoiced.
alter table companies add column plan_code varchar(32) references plans(code);

-- ---------------------------------------------------------------------------
-- Three plans to start, matching how the product is actually pitched.
--
-- Essentials is the honest floor: attendance, leave, people and payroll are what an HR product IS,
-- and they are not listed here because they are not switchable at all. So Essentials includes
-- nothing extra — its value is what it leaves out, and its price reflects that.
-- ---------------------------------------------------------------------------
insert into plans (code, name, description, price_per_employee, sort_order) values
    ('ESSENTIALS', 'Essentials',
     'People, attendance, leave and payroll. For a small team that wants the basics done properly.',
     99, 1),
    ('GROWTH', 'Growth',
     'Everything in Essentials, plus hiring, expenses, shifts and the helpdesk.',
     149, 2),
    ('COMPLETE', 'Complete',
     'The whole platform, including performance, the work tracker, knowledge base and AI assistant.',
     199, 3);

insert into plan_features (plan_code, feature) values
    -- Essentials: payroll only. Everything else is deliberately absent.
    ('ESSENTIALS', 'PAYROLL'),
    ('ESSENTIALS', 'FEED'),

    ('GROWTH', 'PAYROLL'),
    ('GROWTH', 'FEED'),
    ('GROWTH', 'RECRUITMENT'),
    ('GROWTH', 'EXPENSES'),
    ('GROWTH', 'SHIFTS'),
    ('GROWTH', 'HELPDESK'),
    ('GROWTH', 'CLIENTS'),

    ('COMPLETE', 'PAYROLL'),
    ('COMPLETE', 'FEED'),
    ('COMPLETE', 'RECRUITMENT'),
    ('COMPLETE', 'EXPENSES'),
    ('COMPLETE', 'SHIFTS'),
    ('COMPLETE', 'HELPDESK'),
    ('COMPLETE', 'CLIENTS'),
    ('COMPLETE', 'PERFORMANCE'),
    ('COMPLETE', 'WORK'),
    ('COMPLETE', 'KNOWLEDGE'),
    ('COMPLETE', 'ASSISTANT'),
    ('COMPLETE', 'ANALYTICS');

-- STATUTORY_PAYROLL is in no plan on purpose. It is switched on per customer once their numbers have
-- been checked, not sold as part of a package — see V46.
