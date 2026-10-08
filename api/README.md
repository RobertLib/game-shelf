# Game Shelf API

REST API for game collectors – NestJS 12, Prisma 7, PostgreSQL. The OpenAPI specification is
generated from the code into [`../openapi/openapi.yaml`](../openapi/openapi.yaml).

## Getting started

```bash
cp .env.example .env            # adjust DATABASE_URL and JWT_SECRET
npm install                     # also generates the Prisma client
npx prisma migrate deploy       # or `npm run db:migrate` while changing the schema
npm run start:dev               # http://localhost:3000/api/v1, Swagger UI at /docs
```

Demo account with ~40 games: `npm run build && npm run db:seed` → `demo@example.com` / `demo-collector`.

## Scripts

| command | description |
|---|---|
| `npm run start:dev` | server with hot reload |
| `npm run build && npm run start:prod` | production build |
| `npm test` | unit tests (Vitest) |
| `npm run test:e2e` | e2e tests against the database from `.env.test` (copy `.env.test.example`; the database is wiped!) |
| `npm run lint` | oxlint |
| `npm run openapi` | regenerates `../openapi/openapi.yaml` – run it after every API change |
| `npm run db:migrate` | creates and applies a migration after changing `prisma/schema.prisma` |

## Structure

```
src/
  auth/       sign-up, sign-in, refresh tokens, password change, account deletion, global JWT guard
  games/      games CRUD, filters (games.query.ts), facets for filter UIs
  lookup/     barcode lookup and game search: UPCitemdb + IGDB clients, listing title parsing, in-memory caches
  common/     error format, decorators, query parameter transforms
  config/     environment validation
  prisma/     PrismaService (lazy connection via @prisma/adapter-pg)
  scripts/    OpenAPI export, demo data seed
prisma/       schema and migrations
test/         e2e tests (supertest)
```

## Design

- **Authentication:** short-lived JWT access token (HS256, 15 min by default) + opaque refresh token
  (30 days). Refresh tokens are stored only as SHA-256 hashes, rotate on every use, and reusing an
  old token revokes all sessions of the user. Changing the password signs out all other devices
  (their access tokens included). Passwords are hashed with scrypt from the Node standard library.
- **Privacy:** every games query is scoped to the signed-in user; another user's game behaves as
  if it did not exist (404).
- **Errors:** always `{ statusCode, code, message, details? }` – clients branch on `code`.
- **Rate limiting:** 300 requests/min per IP, 10/min for endpoints that take a password. Behind a
  reverse proxy set `TRUST_PROXY` (e.g. `1`), otherwise the proxy's IP address is limited.
- `PUT /games/{id}` fully replaces the record (omitted optional fields are cleared).
- **Barcode lookup** (`GET /lookup/barcode/{barcode}`): UPCitemdb maps the EAN / UPC to a shop
  listing; `product-title.ts` takes the game name, platform, edition and region out of its title;
  IGDB (when `IGDB_CLIENT_ID` / `IGDB_CLIENT_SECRET` are set) finds the game by name
  (`igdb-match.ts`: word similarity, preferring the scanned platform and full games over add-ons)
  and adds genre, developer, publisher, release year and cover. Results are cached in memory (found
  for 7 days, unknown codes for 6 hours) and concurrent lookups of one code share one request,
  because the free UPCitemdb plan allows 100 lookups a day. The endpoint is limited to 30 calls/min.
  A UPC-A scanned as EAN-13 (leading `0`) is looked up by its 12 digits.
- **Game search** (`GET /lookup/games?q=…&platform=…`): searches IGDB by title for a game typed in
  by hand. Editions of a game (IGDB versions) and add-ons (DLC, mods, episodes …) are left out; IGDB's
  order of relevance is kept, except that games on the platform chosen in the form come first and then
  a game with exactly the typed title. At most 20 results, cached in memory per query for a day. The
  apps search as the user types (debounced), so the endpoint allows 60 calls/min. Needs the IGDB
  credentials; without them, or when IGDB fails, it answers `503 LOOKUP_UNAVAILABLE`.
