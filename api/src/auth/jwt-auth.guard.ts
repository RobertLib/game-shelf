import {
  CanActivate,
  ExecutionContext,
  HttpStatus,
  Injectable,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { ApiException } from '../common/api-exception.js';
import type { AuthenticatedRequest } from '../common/current-user.decorator.js';
import { ErrorCode } from '../common/error-codes.js';
import { IS_PUBLIC_KEY } from '../common/public.decorator.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { TokenService } from './token.service.js';

/**
 * Global guard: every route needs a valid bearer token unless marked
 * `@Public()`. Tokens of deleted users, or issued before the last password
 * change, are rejected.
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly tokens: TokenService,
    private readonly prisma: PrismaService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (isPublic) return true;

    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const [scheme, token] = request.headers.authorization?.split(' ') ?? [];
    if (scheme?.toLowerCase() !== 'bearer' || !token) throw unauthorized();

    const payload = await this.tokens.verifyAccessToken(token);
    if (!payload) throw unauthorized();

    const user = await this.prisma.user.findUnique({
      where: { id: payload.sub },
      select: { id: true, passwordChangedAt: true },
    });
    if (!user) throw unauthorized();
    if (
      user.passwordChangedAt &&
      payload.iat < Math.floor(user.passwordChangedAt.getTime() / 1000)
    ) {
      throw unauthorized();
    }

    request.user = { id: user.id };
    return true;
  }
}

const unauthorized = () =>
  new ApiException(
    HttpStatus.UNAUTHORIZED,
    ErrorCode.UNAUTHORIZED,
    'Unauthorized',
  );
