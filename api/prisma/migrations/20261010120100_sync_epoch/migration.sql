-- AlterTable: every existing user gets their own random epoch (the default is evaluated per row).
ALTER TABLE "users" ADD COLUMN     "syncEpoch" TEXT NOT NULL DEFAULT substr(md5(random()::text), 1, 10);
