import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import type { Response } from 'express';
import { ApiException } from './api-exception.js';
import { ErrorCode } from './error-codes.js';
import { ErrorResponseDto } from './error-response.dto.js';

const codeByStatus: Partial<Record<number, ErrorCode>> = {
  [HttpStatus.BAD_REQUEST]: ErrorCode.BAD_REQUEST,
  [HttpStatus.UNAUTHORIZED]: ErrorCode.UNAUTHORIZED,
  [HttpStatus.FORBIDDEN]: ErrorCode.FORBIDDEN,
  [HttpStatus.NOT_FOUND]: ErrorCode.NOT_FOUND,
  [HttpStatus.METHOD_NOT_ALLOWED]: ErrorCode.NOT_FOUND,
  [HttpStatus.CONFLICT]: ErrorCode.CONFLICT,
  [HttpStatus.PAYLOAD_TOO_LARGE]: ErrorCode.VALIDATION_FAILED,
  [HttpStatus.UNSUPPORTED_MEDIA_TYPE]: ErrorCode.VALIDATION_FAILED,
  [HttpStatus.UNPROCESSABLE_ENTITY]: ErrorCode.VALIDATION_FAILED,
  [HttpStatus.TOO_MANY_REQUESTS]: ErrorCode.TOO_MANY_REQUESTS,
};

/** The code of an HTTP error that has none of its own: any other 4xx is a bad request. */
function codeForStatus(status: number): ErrorCode {
  if (status >= 400 && status < 500) {
    return codeByStatus[status] ?? ErrorCode.BAD_REQUEST;
  }
  return ErrorCode.INTERNAL_ERROR;
}

/**
 * Status of a client error raised by Express middleware rather than Nest, e.g.
 * body-parser's 413 (body too large) or 415 (unsupported charset). These are
 * `http-errors` objects whose `expose` flag says the message is safe to show.
 */
function middlewareClientErrorStatus(exception: unknown): number | null {
  if (!(exception instanceof Error)) return null;
  const { status, expose } = exception as Error & {
    status?: unknown;
    expose?: unknown;
  };
  return expose === true &&
    typeof status === 'number' &&
    status >= 400 &&
    status < 500
    ? status
    : null;
}

/** Renders every error as {@link ErrorResponseDto}. */
@Catch()
export class HttpExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(HttpExceptionFilter.name);

  catch(exception: unknown, host: ArgumentsHost) {
    const response = host.switchToHttp().getResponse<Response>();
    const body = this.toBody(exception);
    response.status(body.statusCode).json(body);
  }

  private toBody(exception: unknown): ErrorResponseDto {
    if (exception instanceof ApiException) {
      return {
        statusCode: exception.getStatus(),
        code: exception.code,
        message: exception.message,
        ...(exception.details && { details: exception.details }),
      };
    }

    if (exception instanceof HttpException) {
      const statusCode = exception.getStatus();
      return {
        statusCode,
        code: codeForStatus(statusCode),
        message: exception.message,
      };
    }

    const statusCode = middlewareClientErrorStatus(exception);
    if (statusCode) {
      return {
        statusCode,
        code: codeForStatus(statusCode),
        message: (exception as Error).message,
      };
    }

    this.logger.error(exception);
    return {
      statusCode: HttpStatus.INTERNAL_SERVER_ERROR,
      code: ErrorCode.INTERNAL_ERROR,
      message: 'Internal server error',
    };
  }
}
