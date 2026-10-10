import {
  HttpStatus,
  type INestApplication,
  ValidationError,
  ValidationPipe,
  VersioningType,
} from '@nestjs/common';
import type { NextFunction, Request, Response } from 'express';
import helmet from 'helmet';
import { ApiException } from './common/api-exception.js';
import { ErrorCode } from './common/error-codes.js';
import { HttpExceptionFilter } from './common/http-exception.filter.js';
import { StripNulPipe } from './common/strip-nul.pipe.js';

const flatten = (errors: ValidationError[]): string[] =>
  errors.flatMap((error) => [
    ...Object.values(error.constraints ?? {}),
    ...flatten(error.children ?? []),
  ]);

/** Global HTTP setup shared by the server, the e2e tests and the OpenAPI export. */
export function configureApp(app: INestApplication): INestApplication {
  app.setGlobalPrefix('api');
  app.enableVersioning({ type: VersioningType.URI, defaultVersion: '1' });
  // The API serves only JSON; CSP would just break the bundled Swagger UI.
  app.use(helmet({ contentSecurityPolicy: false }));
  // Responses carry the user's collection: no HTTP cache (the apps', a proxy's) may keep them.
  app.use('/api', (_req: Request, res: Response, next: NextFunction) => {
    res.setHeader('Cache-Control', 'no-store');
    next();
  });
  app.useGlobalPipes(
    new StripNulPipe(),
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      exceptionFactory: (errors) =>
        new ApiException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.VALIDATION_FAILED,
          'Validation failed',
          flatten(errors),
        ),
    }),
  );
  app.useGlobalFilters(new HttpExceptionFilter());
  app.enableShutdownHooks();
  return app;
}
