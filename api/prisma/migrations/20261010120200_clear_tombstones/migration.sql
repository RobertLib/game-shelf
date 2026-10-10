-- Tombstones of deleted games keep only what the change feed needs; their content is cleared.
UPDATE "games"
SET "region" = NULL,
    "edition" = NULL,
    "completeness" = NULL,
    "condition" = NULL,
    "playStatus" = NULL,
    "genre" = NULL,
    "developer" = NULL,
    "publisher" = NULL,
    "releaseYear" = NULL,
    "barcode" = NULL,
    "productCode" = NULL,
    "purchasePrice" = NULL,
    "purchaseDate" = NULL,
    "purchasePlace" = NULL,
    "estimatedValue" = NULL,
    "storageLocation" = NULL,
    "rating" = NULL,
    "coverImageUrl" = NULL,
    "notes" = NULL
WHERE "deletedAt" IS NOT NULL;
