-- Tombstones of deleted games keep no content: the required columns get fixed values too.
UPDATE "games"
SET "platform" = 'OTHER', "status" = 'OWNED', "format" = 'PHYSICAL', "quantity" = 1,
    "currency" = 'CZK', "favorite" = false
WHERE "deletedAt" IS NOT NULL;
