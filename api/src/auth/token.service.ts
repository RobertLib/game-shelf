import { HttpStatus, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { createHash, randomBytes } from 'node:crypto';
import { ApiException } from '../common/api-exception.js';
import { ErrorCode } from '../common/error-codes.js';
import type { Env } from '../config/env.js';
import { PrismaService } from '../prisma/prisma.service.js';

export interface AccessTokenPayload {
  sub: string;
  iat: number;
}

export interface TokenPair {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
}

const DAY_MS = 24 * 60 * 60 * 1000;

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
  }

  async issue(userId: string): Promise<TokenPair> {
    const accessToken = await this.jwt.signAsync(
      { sub: userId },
      { expiresIn: this.accessTtlSeconds },
    );
    const refreshToken = randomBytes(32).toString('base64url');
    await this.prisma.refreshToken.create({
      data: {
        userId,
        tokenHash: sha256(refreshToken),
        expiresAt: new Date(Date.now() + this.refreshTtlMs),
      },
    });
    return { accessToken, expiresIn: this.accessTtlSeconds, refreshToken };
  }

  async verifyAccessToken(token: string): Promise<AccessTokenPayload | null> {
    try {
      return await this.jwt.verifyAsync<AccessTokenPayload>(token);
    } catch {
      return null;
    }
  }

  /**
   * Exchanges a refresh token for a new pair (rotation). Presenting a token
   * that was already used means it leaked, so every session of its owner is
   * revoked.
   */
  async rotate(
    refreshToken: string,
  ): Promise<{ userId: string; tokens: TokenPair }> {
    const stored = await this.prisma.refreshToken.findUnique({
      where: { tokenHash: sha256(refreshToken) },
    });
    if (!stored) throw invalidRefreshToken();

    if (stored.revokedAt) {
      await this.revokeAll(stored.userId);
      throw invalidRefreshToken();
    }
    if (stored.expiresAt <= new Date()) throw invalidRefreshToken();

    // Guarded update so two concurrent refreshes cannot both succeed.
    const { count } = await this.prisma.refreshToken.updateMany({
      where: { id: stored.id, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    if (count === 0) throw invalidRefreshToken();

    return { userId: stored.userId, tokens: await this.issue(stored.userId) };
  }

  async revoke(refreshToken: string): Promise<void> {
    await this.prisma.refreshToken.updateMany({
      where: { tokenHash: sha256(refreshToken), revokedAt: null },
      data: { revokedAt: new Date() },
    });
  }

  async revokeAll(userId: string): Promise<void> {
    await this.prisma.refreshToken.updateMany({
      where: { userId, revokedAt: null },
      data: { revokedAt: new Date() },
    });
  }
}
