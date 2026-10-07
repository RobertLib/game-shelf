import type { INestApplication } from '@nestjs/common';
import {
  DocumentBuilder,
  type OpenAPIObject,
  type SchemaObject,
  SwaggerModule,
} from '@nestjs/swagger';

export function createOpenApiDocument(app: INestApplication): OpenAPIObject {
  const config = new DocumentBuilder()
    .setTitle('Game Shelf API')
    .setDescription(
      'REST API of Game Shelf – a catalogue for collectors of computer and console games.\n\n' +
        'Authenticate with `POST /api/v1/auth/login` or `/register` and send the access token as ' +
        '`Authorization: Bearer <token>`. When it expires (401), exchange the refresh token at ' +
        '`POST /api/v1/auth/refresh`.\n\n' +
        'Every error has the shape of `ErrorResponse`; branch on its `code`.',
    )
    .setVersion('1.0.0')
    .addBearerAuth({ type: 'http', scheme: 'bearer', bearerFormat: 'JWT' })
    .build();

  const document = SwaggerModule.createDocument(app, config);
  // PATCH leaves omitted fields unchanged, so the defaults it inherits from SaveGameRequest do not apply.
  const update = document.components?.schemas?.UpdateGameRequest as
    SchemaObject | undefined;
  for (const property of Object.values(update?.properties ?? {})) {
    delete (property as SchemaObject).default;
  }
  return document;
}
