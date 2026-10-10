-- AlterTable: a rotated token points to its successor, so a retried refresh can be told from a reused token.
ALTER TABLE "refresh_tokens" ADD COLUMN     "replacedById" UUID;
