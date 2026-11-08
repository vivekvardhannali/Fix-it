-- One-off migration for databases created before Google sign-in was removed (2026-11-08).
-- New databases built from schema.sql already have these constraints; running this on them is harmless.
-- Apply with: psql -d fix_it -f db/migrate_2026-11-08_username_required.sql
--
-- If it fails with "column ... contains null values", some account has no username or password (only possible for
-- rows left behind by an interrupted test run). List them with:
--   select user_id, smail from users where username is null or password_hash is null;
-- and delete them (with their content) or give them a username and password before running this again.
BEGIN;
ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
COMMIT;
