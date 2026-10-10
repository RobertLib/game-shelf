import type { PrismaClient } from '../generated/prisma/client.js';

/**
 * Change feed cursors are `<epoch>.<version>`: the user's sync epoch and the
 * number of the last change the client has. The epoch is replaced after a
 * restore from a backup, which makes every older cursor fail with 410 even
 * once new changes have pushed the counter past it. Plain `<version>` cursors
 * of older app versions are still accepted, without the epoch check.
 */
export const CURSOR_REGEX = /^(?:([A-Za-z0-9_-]{1,32})\.)?([0-9]{1,10})$/;

export interface SyncCursor {
  /** `null` for a cursor in the old format without an epoch. */
  epoch: string | null;
  version: number;
}

/** Reads a cursor that has passed {@link CURSOR_REGEX}; none means the beginning. */
export function parseCursor(cursor: string | undefined): SyncCursor {
  const match = cursor === undefined ? null : CURSOR_REGEX.exec(cursor);
  if (!match) return { epoch: null, version: 0 };
  return { epoch: match[1] ?? null, version: Number(match[2]) };
}

export const formatCursor = (epoch: string, version: number) =>
  `${epoch}.${version}`;

/**
 * Gives every user a new sync epoch (the column default draws a random one per
 * row), so that the apps answer 410 by pulling everything again. Needed after
 * the database was restored from a backup.
 */
export function resetSyncEpochs(prisma: PrismaClient): Promise<number> {
  return prisma.$executeRaw`UPDATE "users" SET "syncEpoch" = DEFAULT`;
}
