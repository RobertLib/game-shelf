-- Tombstones of deleted games keep no content, the title included.
UPDATE "games" SET "title" = '' WHERE "deletedAt" IS NOT NULL;
