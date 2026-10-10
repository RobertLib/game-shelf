import {
  CanActivate,
  ExecutionContext,
  HttpStatus,
  Injectable,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { accountDeleted, ApiException } from '../common/api-exception.js';
import type { AuthenticatedRequest } from '../common/current-user.decorator.js';
import { ErrorCode } from '../common/error-codes.js';
import { IS_PUBLIC_KEY } from '../common/public.decorator.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { passwordChangeStamp, TokenService } from './token.service.js';

/**
 * Global guard: every route needs a valid bearer token unless marked
 * `@Public()`. Tokens issued before the last password change are rejected.
 * A token of a deleted account is answered `ACCOUNT_DELETED` even when it has
 * expired: the apps send their stored access token first, so another device
 * of the user learns at its next request that it must wipe the collection,
 * instead of taking the rejected refresh for an expired session that keeps it.
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

    const verified = await this.tokens.readAccessToken(token);
    if (!verified) throw unauthorized();
    const { payload, expired } = verified;

    const user = await this.prisma.user.findUnique({
      where: { id: payload.sub },
      select: { id: true, passwordChangedAt: true },
    });
    // Signed by this server, and user ids are never reused: the account was deleted.
    if (!user) throw accountDeleted();
    if (expired) throw unauthorized();
    const issuedBeforePasswordChange =
      payload.pwc === undefined
        ? // Tokens issued before the claim existed: `iat` has whole seconds only.
          user.passwordChangedAt !== null &&
          payload.iat < Math.floor(user.passwordChangedAt.getTime() / 1000)
        : payload.pwc !== passwordChangeStamp(user);
    if (issuedBeforePasswordChange) throw unauthorized();

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
