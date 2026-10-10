/**
 * Machine-readable error codes returned in the `code` field of every error
 * response. Clients should branch on these, never on `message`.
 */
export const ErrorCode = {
  VALIDATION_FAILED: 'VALIDATION_FAILED',
  UNAUTHORIZED: 'UNAUTHORIZED',
  /** The access token (expired or not) belongs to an account that has been deleted. */
  ACCOUNT_DELETED: 'ACCOUNT_DELETED',
  INVALID_CREDENTIALS: 'INVALID_CREDENTIALS',
  INVALID_REFRESH_TOKEN: 'INVALID_REFRESH_TOKEN',
  INVALID_CURRENT_PASSWORD: 'INVALID_CURRENT_PASSWORD',
  EMAIL_ALREADY_REGISTERED: 'EMAIL_ALREADY_REGISTERED',
  GAME_NOT_FOUND: 'GAME_NOT_FOUND',
  /** The server has no record of the game at all (e.g. restored from an older backup). */
  GAME_UNKNOWN: 'GAME_UNKNOWN',
  SYNC_RESET_REQUIRED: 'SYNC_RESET_REQUIRED',
  BARCODE_NOT_FOUND: 'BARCODE_NOT_FOUND',
  LOOKUP_UNAVAILABLE: 'LOOKUP_UNAVAILABLE',
  /** The user has used up today's barcode lookups (until midnight UTC). */
  LOOKUP_QUOTA_EXCEEDED: 'LOOKUP_QUOTA_EXCEEDED',
  NOT_FOUND: 'NOT_FOUND',
  TOO_MANY_REQUESTS: 'TOO_MANY_REQUESTS',
  BAD_REQUEST: 'BAD_REQUEST',
  FORBIDDEN: 'FORBIDDEN',
  CONFLICT: 'CONFLICT',
  INTERNAL_ERROR: 'INTERNAL_ERROR',
} as const;

export type ErrorCode = (typeof ErrorCode)[keyof typeof ErrorCode];
