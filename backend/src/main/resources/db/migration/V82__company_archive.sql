-- PD-74: a customer's workspace is archived, never simply deleted.
--
-- Archiving keeps every row and stops the company being used: its people cannot sign in and it is
-- not billed. Restoring undoes it. Deleting for good is a separate step, only for an archived
-- company, behind the platform owner's password (TenantDataService).
alter table companies add column archived_at    timestamptz;
alter table companies add column archived_by    uuid;
alter table companies add column archive_reason varchar(300);
