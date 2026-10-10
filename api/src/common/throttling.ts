import { type ExecutionContext, Injectable, SetMetadata } from '@nestjs/common';
import { ThrottlerGuard } from '@nestjs/throttler';
import type { AuthenticatedRequest } from './current-user.decorator.js';
import { stripNul } from './strip-nul.pipe.js';

const ACCOUNT_THROTTLE = 'accountThrottle';

/**
 * Also limits the route per account (the `account` throttler, keyed by the
 * e-mail in the body), so that guessing one account's password from many IP
 * addresses is slow too.
 */
export const AccountThrottle = () => SetMetadata(ACCOUNT_THROTTLE, true);

/** Whether the route handling `context` is marked with {@link AccountThrottle}. */
export const hasAccountThrottle = (context: ExecutionContext): boolean =>
  Reflect.getMetadata(ACCOUNT_THROTTLE, context.getHandler()) === true;

/**
 * The account a sign-in is for: its e-mail, normalised like the global pipes
 * and the DTOs do. Guards run before pipes, so the body is still raw here: a
 * U+0000 the pipe would strip must not give the same account a fresh limit.
 */
export function accountTracker(req: { body?: unknown; ip?: string }): string {
  const email = (req.body as { email?: unknown } | undefined)?.email;
  return typeof email === 'string'
    ? `account:${(stripNul(email) as string).trim().toLowerCase()}`
    : `ip:${req.ip}`;
}

/**
 * Counts signed-in requests per user and the rest per IP address. Many users
 * of a mobile network share one public address (carrier-grade NAT), so
 * counting their syncs per address would let them throttle each other. Runs
 * after the JWT guard, which sets `req.user`.
 */
@Injectable()
export class AppThrottlerGuard extends ThrottlerGuard {
  protected override async getTracker(
    req: Partial<AuthenticatedRequest>,
  ): Promise<string> {
    return req.user ? `user:${req.user.id}` : super.getTracker(req);
  }
}
