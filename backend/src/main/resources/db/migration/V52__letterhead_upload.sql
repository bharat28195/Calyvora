-- An uploaded letterpad: the company's own stationery, printed as-is (PD-20 continued).
--
-- The designer that already exists composes a letterhead from fields — logo, heading, address,
-- colour. Most companies do not want one composed; they have a letterpad their printer produced,
-- and what they want is their letters to come out on it.
--
-- The bytes live here rather than in object storage because there is no object storage, and adding
-- it for one image per company would be the larger change. Capped in the application at 2 MB, and
-- there is exactly one row per company, so the table stays small. When a document store does
-- arrive, this column becomes the thing that gets migrated out of.
alter table letterheads
    add column background_image bytea,
    add column background_type varchar(64),
    add column background_name varchar(255),
    -- Separate from "is an image present" on purpose: somebody can upload their letterpad, turn it
    -- off to print a plain internal memo, and turn it back on without uploading again.
    add column use_background boolean not null default false;

-- An image that is switched on but absent would print a blank page over every letter.
alter table letterheads
    add constraint letterheads_background_present
        check (use_background = false or background_image is not null);
