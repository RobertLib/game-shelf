-- AlterTable
ALTER TABLE "users" ADD COLUMN "gamesVersion" INTEGER NOT NULL DEFAULT 0;

-- AlterTable: existing games get versions 1..n per user in the order they were added.
ALTER TABLE "games" ADD COLUMN "deletedAt" TIMESTAMP(3),
ADD COLUMN "version" INTEGER;

UPDATE "games" AS g
SET "version" = numbered.n
FROM (
    SELECT "id", ROW_NUMBER() OVER (PARTITION BY "userId" ORDER BY "createdAt", "id") AS n
    FROM "games"
) AS numbered
WHERE g."id" = numbered."id";

UPDATE "users" AS u
SET "gamesVersion" = counts.n
FROM (SELECT "userId", MAX("version") AS n FROM "games" GROUP BY "userId") AS counts
WHERE u."id" = counts."userId";

ALTER TABLE "games" ALTER COLUMN "version" SET NOT NULL;

-- CreateIndex
CREATE UNIQUE INDEX "games_userId_version_key" ON "games"("userId", "version");
