import { HttpException, HttpStatus } from '@nestjs/common';
import { ErrorCode } from './error-codes.js';

export class ApiException extends HttpException {
  constructor(
    status: HttpStatus,
    readonly code: ErrorCode,
    message: string,
    readonly details?: string[],
  ) {
    super(message, status);
  }
}

/**
 * The signed-in user's account no longer exists. The apps sign out and wipe
 * their local copy of the collection, unlike when a session merely expires.
 */
export const accountDeleted = () =>
  new ApiException(
    HttpStatus.UNAUTHORIZED,
    ErrorCode.ACCOUNT_DELETED,
    'The account has been deleted',
  );
