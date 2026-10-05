-- V60__plan_descriptions_match_contents.sql — say what each plan actually includes.
--
-- The V47 descriptions undersold their own plans: Essentials includes the company feed and did not
-- say so, Growth includes Clients, and Complete — everything — named four modules out of twelve. A
-- prospect reading the plan picks by the sentence, not by the join table.
--
-- Only rewritten where the text is still exactly what V47 wrote: a description somebody has edited
-- since is theirs. No row-level security on plans (vendor catalogue, V47), so a plain UPDATE is right.
update plans
   set description = 'People, attendance, leave, payroll and the company feed. For a small team that wants the basics done properly.',
       updated_at = now()
 where code = 'ESSENTIALS'
   and description = 'People, attendance, leave and payroll. For a small team that wants the basics done properly.';

update plans
   set description = 'Everything in Essentials, plus hiring, expenses, shifts, the helpdesk and client staffing.',
       updated_at = now()
 where code = 'GROWTH'
   and description = 'Everything in Essentials, plus hiring, expenses, shifts and the helpdesk.';

update plans
   set description = 'The whole platform: everything in Growth, plus performance reviews, the work tracker, knowledge base, insights and the AI assistant.',
       updated_at = now()
 where code = 'COMPLETE'
   and description = 'The whole platform, including performance, the work tracker, knowledge base and AI assistant.';
