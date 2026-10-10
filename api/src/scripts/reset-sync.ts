/**
 * Run after restoring the database from a backup (see api/README.md):
 * - makes every app pull its whole collection again: gives every user a new
 *   sync epoch, so change feed cursors issued before are answered with 410;
 * - signs everybody out (revokes every refresh token): the backup brought
 *   back sessions that had been signed out or revoked as stolen since.
 * Usage: npm run build && npm run db:reset-sync
 */
import 'dotenv/config';
import { PrismaPg } from '@prisma/adapter-pg';
import { revokeAllSessions } from '../auth/token.service.js';
import { PrismaClient } from '../generated/prisma/client.js';
import { resetSyncEpochs } from '../games/sync-cursor.js';

const prisma = new PrismaClient({
  adapter: new PrismaPg({ connectionString: process.env.DATABASE_URL }),
});

try {
  const users = await resetSyncEpochs(prisma);
  console.log(`New sync epoch for ${users} users`);
  const tokens = await revokeAllSessions(prisma);
  console.log(`Revoked ${tokens} refresh tokens`);
} finally {
  await prisma.$disconnect();
}
