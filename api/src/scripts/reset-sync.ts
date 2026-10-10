/**
 * Makes every app pull its whole collection again: gives every user a new sync
 * epoch, so change feed cursors issued before are answered with 410. Run it
 * after restoring the database from a backup.
 * Usage: npm run build && npm run db:reset-sync
 */
import 'dotenv/config';
import { PrismaPg } from '@prisma/adapter-pg';
import { PrismaClient } from '../generated/prisma/client.js';
import { resetSyncEpochs } from '../games/sync-cursor.js';

const prisma = new PrismaClient({
  adapter: new PrismaPg({ connectionString: process.env.DATABASE_URL }),
});

try {
  const users = await resetSyncEpochs(prisma);
  console.log(`New sync epoch for ${users} users`);
} finally {
  await prisma.$disconnect();
}
