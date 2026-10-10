import { HttpStatus, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { createHash, randomBytes } from 'node:crypto';
import { ApiException } from '../common/api-exception.js';
import { ErrorCode } from '../common/error-codes.js';
import type { Env } from '../config/env.js';
import type { Prisma, RefreshToken, User } from '../generated/prisma/client.js';
import { PrismaService } from '../prisma/prisma.service.js';

export interface AccessTokenPayload {
  sub: string;
  iat: number;
  /**
   * The user's password change stamp when the token was issued (see
   * {@link passwordChangeStamp}). Missing in tokens issued before it existed.
   */
  pwc?: number;
}

export interface TokenPair {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
}

/** The part of the user that the tokens depend on. */
type TokenUser = Pick<User, 'id' | 'passwordChangedAt'>;

type Db = Prisma.TransactionClient;

/**
 * `passwordChangedAt` in milliseconds, 0 when the password was never changed.
 * Access tokens carry it, so a password change invalidates even tokens issued
 * in the same second (`iat` has only second precision).
 */
export const passwordChangeStamp = (
  user: Pick<User, 'passwordChangedAt'>,
): number => user.passwordChangedAt?.getTime() ?? 0;

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Signs every user out on every device: revokes all refresh tokens. Part of
 * restoring the database from a backup, which brings back sessions that were
 * signed out, or revoked as stolen, after the backup was made. Returns the
 * number of tokens revoked.
 */
export function revokeAllSessions(
  prisma: Pick<PrismaService, 'refreshToken'>,
): Promise<number> {
  return prisma.refreshToken
    .updateMany({ where: { revokedAt: null }, data: { revokedAt: new Date() } })
    .then(({ count }) => count);
}

const sha256 = (value: string) =>
  createHash('sha256').update(value).digest('hex');

const invalidRefreshToken = () =>
  new ApiException(
    HttpStatus.UNAUTHORIZED,
    ErrorCode.INVALID_REFRESH_TOKEN,
    'Invalid refresh token',
  );

@Injectable()
export class TokenService {
  private readonly accessTtlSeconds: number;
  private readonly refreshTtlMs: number;
  private readonly reuseGraceMs: number;

  constructor(
    private readonly prisma: PrismaService,
    private readonly jwt: JwtService,
    config: ConfigService<Env, true>,
  ) {
    this.accessTtlSeconds = config.get('ACCESS_TOKEN_TTL_SECONDS', {
      infer: true,
    });
    this.refreshTtlMs =
      config.get('REFRESH_TOKEN_TTL_DAYS', { infer: true }) * DAY_MS;
    this.reuseGraceMs =
      config.get('REFRESH_TOKEN_REUSE_GRACE_SECONDS', { infer: true }) * 1000;
  }

  async issue(user: TokenUser): Promise<TokenPair> {
    return (await this.create(this.prisma, user)).tokens;
  }

  /**
   * The payload of an access token this server signed, also of an expired one
   * (with `expired` set); `null` when the signature or format is wrong.
   */
  async readAccessToken(
    token: string,
  ): Promise<{ payload: AccessTokenPayload; expired: boolean } | null> {
    try {
      const payload = await this.jwt.verifyAsync<
        AccessTokenPayload & { exp?: number }
      >(token, { ignoreExpiration: true });
      // The same test as jsonwebtoken's own expiry check.
      const expired =
        payload.exp !== undefined &&
        Math.floor(Date.now() / 1000) >= payload.exp;
      return { payload, expired };
    } catch {
      return null;
    }
  }

  /**
   * Exchanges a refresh token for a new pair (rotation); the old token records
   * its successor. Presenting it again within the grace period while the
   * successor is still unused is a retry of a refresh whose response was lost:
   * the successor is voided and replaced by a new one. Any other reuse of a
   * rotated token, and any use of a voided one (its pair never reached the
   * client that asked for it), means it leaked, so every session of its owner
   * is revoked. A token revoked by signing out, or from before the owner's
   * last password change, is just rejected.
   */
  async rotate(
    refreshToken: string,
  ): Promise<{ user: User; tokens: TokenPair }> {
    const result = await this.prisma.$transaction(async (tx) => {
      // Concurrent refreshes with one token run one after another, so they
      // cannot both rotate it: the later one sees the rotation.
      const id = await this.lockToken(tx, refreshToken);
      if (!id) return null;
      const stored = await tx.refreshToken.findUniqueOrThrow({
        where: { id },
        include: { user: true },
      });

      const now = new Date();
      if (stored.expiresAt <= now) return null;
      // Also a successor issued by a refresh that raced the password change,
      // which the revocation of the change could not see yet.
      if (passwordChangeStamp(stored) !== passwordChangeStamp(stored.user)) {
        return null;
      }
      if (!stored.revokedAt) return this.replace(tx, stored, now);
      if (stored.voided) {
        await this.revokeAll(stored.userId, tx);
        return null;
      }
      // Revoked by signing out (logout, password change), not by a rotation.
      if (!stored.replacedById) return null;

      if (now.getTime() - stored.revokedAt.getTime() <= this.reuseGraceMs) {
        const { count } = await tx.refreshToken.updateMany({
          where: {
            id: stored.replacedById,
            revokedAt: null,
            expiresAt: { gt: now },
          },
          data: { revokedAt: now, voided: true },
        });
        // The successor was unused, so its pair never arrived: hand out a new one.
        if (count > 0) return this.replace(tx, stored, stored.revokedAt);
      }
      await this.revokeAll(stored.userId, tx);
      return null;
    });
    if (!result) throw invalidRefreshToken();
    return result;
  }

  /**
   * Signs the session out. When the token was already rotated (a refresh
   * raced the logout, or its response was lost), its successors are revoked
   * too, so that no valid token of the session is left behind.
   */
  async revoke(refreshToken: string): Promise<void> {
    await this.prisma.$transaction(async (tx) => {
      // Waits for a refresh with this token that is in progress.
      const id = await this.lockToken(tx, refreshToken);
      if (!id) return;
      const chain = await tx.$queryRaw<{ id: string }[]>`
        WITH RECURSIVE "chain" AS (
          SELECT "id", "replacedById" FROM "refresh_tokens" WHERE "id" = ${id}::uuid
          UNION
          SELECT t."id", t."replacedById" FROM "refresh_tokens" t
          JOIN "chain" c ON t."id" = c."replacedById"
        )
        SELECT "id" FROM "chain"`;
      await tx.refreshToken.updateMany({
        where: { id: { in: chain.map(({ id }) => id) }, revokedAt: null },
        data: { revokedAt: new Date() },
      });
    });
  }

  async revokeAll(userId: string, db: Db = this.prisma): Promise<void> {
    await db.refreshToken.updateMany({
      where: { userId, revokedAt: null },
      data: { revokedAt: new Date() },
    });
  }

  /**
   * Locks a refresh token's row for the rest of the transaction and returns
   * its id, or `null` when there is no such token (any more).
   *
   * The owner's row is locked first (`FOR KEY SHARE`: it only keeps the account
   * from being deleted meanwhile). Deleting the account and changing the
   * password lock the user's row before they touch the tokens, so taking the
   * token's row first would let a refresh or a sign-out deadlock with them –
   * e.g. a refresh holding the token and waiting for the user's row to insert
   * the successor, while the deletion holds the user's row and waits for the
   * token to delete it.
   */
  private async lockToken(
    tx: Db,
    refreshToken: string,
  ): Promise<string | null> {
    const tokenHash = sha256(refreshToken);
    const [token] = await tx.$queryRaw<{ userId: string }[]>`
      SELECT "userId" FROM "refresh_tokens" WHERE "tokenHash" = ${tokenHash}`;
    if (!token) return null;
    await tx.$queryRaw`
      SELECT 1 FROM "users" WHERE "id" = ${token.userId}::uuid FOR KEY SHARE`;
    // Gone by now when the account was deleted while this waited for it.
    const [row] = await tx.$queryRaw<{ id: string }[]>`
      SELECT "id" FROM "refresh_tokens" WHERE "tokenHash" = ${tokenHash}
      FOR UPDATE`;
    return row?.id ?? null;
  }

  /** Issues the successor of a refresh token and records it as its replacement. */
  private async replace(
    tx: Db,
    stored: RefreshToken & { user: User },
    revokedAt: Date,
  ): Promise<{ user: User; tokens: TokenPair }> {
    const { id, tokens } = await this.create(tx, stored.user);
    await tx.refreshToken.update({
      where: { id: stored.id },
      data: { revokedAt, replacedById: id },
    });
    return { user: stored.user, tokens };
  }

  /** A new access token and refresh token; `id` is the refresh token's. */
  private async create(
    db: Db,
    user: TokenUser,
  ): Promise<{ id: string; tokens: TokenPair }> {
    const accessToken = await this.jwt.signAsync(
      { sub: user.id, pwc: passwordChangeStamp(user) },
      { expiresIn: this.accessTtlSeconds },
    );
    const refreshToken = randomBytes(32).toString('base64url');
    const now = Date.now();
    // Expired tokens are useless, so the table does not grow forever.
    await db.refreshToken.deleteMany({
      where: { userId: user.id, expiresAt: { lt: new Date(now) } },
    });
    const { id } = await db.refreshToken.create({
      data: {
        userId: user.id,
        tokenHash: sha256(refreshToken),
        expiresAt: new Date(now + this.refreshTtlMs),
        passwordChangedAt: user.passwordChangedAt,
      },
      select: { id: true },
    });
    return {
      id,
      tokens: { accessToken, expiresIn: this.accessTtlSeconds, refreshToken },
    };
  }
}
