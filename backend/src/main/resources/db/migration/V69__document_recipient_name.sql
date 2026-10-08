-- V69__document_recipient_name.sql — who a letter is addressed to when they are not an employee.
--
-- An offer letter goes to somebody who has not joined: there is no employee row to point at, so the
-- issued-documents list showed the letter with no name on it. The name typed onto the letter is kept
-- here. Letters for employees leave it null and keep reading the name from the employee record.
alter table generated_documents add column recipient_name varchar(200);
