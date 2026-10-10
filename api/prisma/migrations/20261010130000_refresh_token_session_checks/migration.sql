-- AlterTable: a token voided by a retried refresh tells a leaked token from a signed-out one, and the
-- password change stamp kills tokens issued by a refresh that raced a password change.
ALTER TABLE "refresh_tokens" ADD COLUMN     "passwordChangedAt" TIMESTAMP(3),
ADD COLUMN     "voided" BOOLEAN NOT NULL DEFAULT false;

-- Existing sessions belong to the owner's current password.
UPDATE "refresh_tokens" t
SET "passwordChangedAt" = u."passwordChangedAt"
FROM "users" u
WHERE t."userId" = u."id";
