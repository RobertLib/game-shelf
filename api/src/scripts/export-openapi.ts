/**
 * Writes the OpenAPI document to a file without starting the server or
 * touching the database. Usage: node dist/scripts/export-openapi.js <out.yaml>
 */
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { NestFactory } from '@nestjs/core';
import { stringify } from 'yaml';

// Only needed to pass env validation; nothing connects anywhere.
process.env.DATABASE_URL ??= 'postgresql://localhost/unused';
process.env.JWT_SECRET ??= 'x'.repeat(32);

const { AppModule } = await import('../app.module.js');
const { configureApp } = await import('../app.setup.js');
const { createOpenApiDocument } = await import('../openapi.js');

const app = configureApp(
  await NestFactory.create(AppModule, { logger: false, preview: true }),
);
const document = createOpenApiDocument(app);
await app.close();

const out = resolve(process.argv[2] ?? 'openapi.yaml');
writeFileSync(out, stringify(document, { aliasDuplicateObjects: false }));
console.log(`OpenAPI document written to ${out}`);
