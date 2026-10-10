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

## Configuration

Environment variables (see [`.env.example`](.env.example)); the server refuses to start with invalid values.

| variable | default | |
|---|---|---|
| `DATABASE_URL` | – | PostgreSQL connection string |
| `JWT_SECRET` | – | at least 32 characters; with `NODE_ENV=production` the example value from `.env.example` is refused |
| `NODE_ENV` | – | `production` turns the API docs off by default |
| `SWAGGER_ENABLED` | `true`, `false` in production | Swagger UI and the OpenAPI document at `/docs` |
| `ACCESS_TOKEN_TTL_SECONDS` | `900` | access token lifetime |
| `REFRESH_TOKEN_TTL_DAYS` | `30` | refresh token lifetime |
| `REFRESH_TOKEN_REUSE_GRACE_SECONDS` | `120` | grace period for retrying a refresh whose response was lost (see *Design*); `0` turns it off |
| `PORT` | `3000` | |
| `TRUST_PROXY` | – | Express `trust proxy` behind a reverse proxy, e.g. `1` |
| `THROTTLE_ENABLED` | `true` | rate limiting; switched off only by the e2e tests |
| `UPCITEMDB_USER_KEY` | – | key of a paid UPCitemdb plan; without it the free trial (100 lookups a day per server IP) is used |
| `UPCITEMDB_DAILY_LIMIT_PER_USER` | `20` | UPCitemdb lookups one user can cause per day (UTC); cached results do not count |
| `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` | – | Twitch application for IGDB (game details, search by title) |

In production install with `npm ci --omit=dev` (Prisma is a runtime dependency: `postinstall`
generates its client and `npm run db:deploy` applies migrations), then `npm run start:prod`.

## Restoring the database from a backup

After restoring, run the migrations if needed and then

```bash
npm run build && npm run db:reset-sync
```

It gives every user a new sync epoch. The apps' change feed cursors contain it, so every app gets
`410 SYNC_RESET_REQUIRED` and pulls its whole collection again. Without it, an app whose cursor lies
before the restored state of the counter would silently miss the changes made after the backup.

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
| `npm run db:reset-sync` | makes every app pull everything again – run after restoring a backup (needs `npm run build`) |

## Structure

```
src/
  auth/       sign-up, sign-in, refresh tokens, password change, account deletion, global JWT guard
  games/      games CRUD, filters (games.query.ts), facets for filter UIs
  lookup/     barcode lookup and game search: UPCitemdb + IGDB clients, listing title parsing, in-memory caches
  common/     error format, decorators, query parameter transforms, validation shared with the apps
  config/     environment validation
  prisma/     PrismaService (lazy connection via @prisma/adapter-pg)
  scripts/    OpenAPI export, demo data seed, sync reset after a restore
prisma/       schema and migrations
test/         e2e tests (supertest)
```

## Design

- **Authentication:** short-lived JWT access token (HS256, 15 min by default) + opaque refresh token
  (30 days). Refresh tokens are stored only as SHA-256 hashes and rotate on every use; the old token
  remembers its successor. Sending the old token again within `REFRESH_TOKEN_REUSE_GRACE_SECONDS`
  (2 min) while the successor is still unused is a retry of a refresh whose response was lost on a
  flaky network: the successor is revoked and a new pair returned. Any other reuse of a rotated token
  revokes all sessions of the user. A token revoked by signing out is just rejected (401), and logout
  also revokes the tokens it was already exchanged for. Concurrent refreshes with one token are
  serialised by a row lock, so at most one of the returned tokens stays valid. Expired tokens of a
  user are deleted whenever new tokens are issued to them. Changing the password signs out all other
  devices, their access tokens included: tokens carry the password change time in ms (`pwc` claim);
  tokens without it, from before this claim existed, are compared by `iat` in whole seconds.
  Passwords are hashed with scrypt from the Node standard library.
- **Validation** follows exactly the rules the apps use ([`docs/mobile-spec.md`](../docs/mobile-spec.md#validation)),
  so that a game saved offline is never rejected later: text lengths are counted in Unicode code
  points (`MaxChars` / `MinChars` in `common/char-length.decorator.ts`), the cover URL must match
  the shared pattern in `common/cover-url.ts`, and the currency is any 3 letters A–Z.
- **Change feed** (`GET /games/changes`, see [`docs/offline-sync.md`](../docs/offline-sync.md)): the
  cursor is `<syncEpoch>.<version>`, opaque to the apps. A cursor whose epoch is not the user's
  current one, or whose version is ahead of the server, is answered with `410 SYNC_RESET_REQUIRED`.
  Plain numeric cursors stored by older app versions are still accepted, without the epoch check.
  Deleted games stay as tombstones with every optional column cleared.
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
  and adds genre, developer, publisher, release year and cover. The free UPCitemdb plan allows 100
  lookups a day per server, so results are cached in memory (found for 7 days, unknown codes for
  6 hours, a result IGDB failed to complete for 5 minutes), concurrent lookups of one code share one
  request, a 12–14 digit code with a wrong GTIN check digit is answered `404 BARCODE_NOT_FOUND`
  without asking, and each user can cause at most `UPCITEMDB_DAILY_LIMIT_PER_USER` UPCitemdb
  lookups a day (then `429 TOO_MANY_REQUESTS`; counted in memory per process). The endpoint is
  limited to 30 calls/min. A UPC-A scanned as EAN-13 (leading `0`) is looked up by its 12 digits.
  Cover URLs are returned only when a game can be saved with them.
- **Game search** (`GET /lookup/games?q=…&platform=…`): searches IGDB by title for a game typed in
  by hand. Editions of a game (IGDB versions) and add-ons (DLC, mods, episodes …) are left out; IGDB's
  order of relevance is kept, except that games on the platform chosen in the form come first and then
  a game with exactly the typed title. At most 20 results, cached in memory per query for a day. The
  apps search as the user types (debounced), so the endpoint allows 60 calls/min. Needs the IGDB
  credentials; without them, or when IGDB fails, it answers `503 LOOKUP_UNAVAILABLE`.
