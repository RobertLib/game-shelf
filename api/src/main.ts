import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module.js';
import { configureApp } from './app.setup.js';
import type { Env } from './config/env.js';
import { createOpenApiDocument } from './openapi.js';

async function bootstrap() {
  const app = await NestFactory.create<NestExpressApplication>(AppModule);
  configureApp(app);
  const config = app.get(ConfigService<Env, true>);

  const trustProxy = config.get('TRUST_PROXY', { infer: true });
  if (trustProxy) {
    app.set(
      'trust proxy',
      /^\d+$/.test(trustProxy) ? Number(trustProxy) : trustProxy,
    );
  }

  const docs = config.get('SWAGGER_ENABLED', { infer: true });
  if (docs) {
    SwaggerModule.setup('docs', app, createOpenApiDocument(app), {
      jsonDocumentUrl: 'docs/openapi.json',
      yamlDocumentUrl: 'docs/openapi.yaml',
    });
  }

  const port = config.get('PORT', { infer: true });
  await app.listen(port);
  Logger.log(
    `API on http://localhost:${port}/api/v1` +
      (docs ? `, docs on http://localhost:${port}/docs` : ''),
  );
}
await bootstrap();
