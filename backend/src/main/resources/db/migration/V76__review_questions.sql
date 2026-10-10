-- V76: structured reviews (PD-66) — each cycle carries its questions, each review the employee's and
-- the manager's answers, and the outcome a promotion and an effective date as well as a hike.
-- Schema only: nullable columns; cycles from before have no questions and keep the free-text form.

alter table review_cycles add column questions text;

alter table performance_reviews add column self_answers text;
alter table performance_reviews add column manager_answers text;
alter table performance_reviews add column new_title varchar(120);
alter table performance_reviews add column effective_date date;
