-- V61__retire_work_and_clients_modules.sql — the work tracker and Clients leave the HR product.
--
-- Both modules are archived on branch archive/work-tracker-and-clients and their code is gone from
-- this one. Their feature names must go from the catalogue first: Plan and CompanyFeature map the
-- column as an enum, and a row naming WORK or CLIENTS would fail to load once the enum no longer has
-- them — taking every plan screen down with it.
--
-- The TABLES stay (projects, tasks, sprints, sprint_snapshots, tickets, clients, client_requests).
-- Dropping a customer's rows is not a code clean-up decision, the tenant export still includes them,
-- and the archive branch can be merged back onto them. plan_features and company_features carry no
-- row-level security (V46/V47), so plain deletes reach every row.
delete from plan_features   where feature in ('WORK', 'CLIENTS');
delete from company_features where feature in ('WORK', 'CLIENTS');

-- Say what each plan now holds, only where the text is still what V60 wrote.
update plans
   set description = 'Everything in Essentials, plus hiring, expenses, shifts and the helpdesk.',
       updated_at = now()
 where code = 'GROWTH'
   and description = 'Everything in Essentials, plus hiring, expenses, shifts, the helpdesk and client staffing.';

update plans
   set description = 'The whole platform: everything in Growth, plus performance reviews, company documents, insights and the AI assistant.',
       updated_at = now()
 where code = 'COMPLETE'
   and description = 'The whole platform: everything in Growth, plus performance reviews, the work tracker, knowledge base, insights and the AI assistant.';
