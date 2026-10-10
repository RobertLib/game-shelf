# Game Shelf API

REST API for game collectors – NestJS 12, Prisma 7, PostgreSQL. The OpenAPI specification is
generated from the code into [`../openapi/openapi.yaml`](../openapi/openapi.yaml).

## Getting started

```bash
cp .env.example .env            # adjust DATABASE_URL (and JWT_SECRET outside development)
npm install                     # also generates the Prisma client
npx prisma migrate deploy       # or `npm run db:migrate` while changing the schema
npm run start:dev               # http://localhost:3000/api/v1, Swagger UI at /docs
```

Demo account with ~40 games: `npm run build && npm run db:seed` → `demo@example.com` / `demo-collector`
(only with `NODE_ENV=development` or `test`, as in `.env.example`: the password is public).

## Configuration

Environment variables (see [`.env.example`](.env.example)); the server refuses to start with invalid values.

| variable | default | |
|---|---|---|
| `DATABASE_URL` | – | PostgreSQL connection string |
| `JWT_SECRET` | – | at least 32 characters; the example value from `.env.example` is accepted only with `NODE_ENV=development` or `test` |
| `NODE_ENV` | – | `production` turns the API docs off by default; `.env.example` sets `development` |
| `SWAGGER_ENABLED` | `true`, `false` in production | Swagger UI and the OpenAPI document at `/docs` |
| `ACCESS_TOKEN_TTL_SECONDS` | `900` | access token lifetime |
| `REFRESH_TOKEN_TTL_DAYS` | `30` | refresh token lifetime |
| `REFRESH_TOKEN_REUSE_GRACE_SECONDS` | `120` | grace period for retrying a refresh whose response was lost (see *Design*); `0` turns it off |
| `PORT` | `3000` | |
| `TRUST_PROXY` | – | Express `trust proxy` behind a reverse proxy, e.g. `1` |
| `THROTTLE_ENABLED` | `true` | rate limiting; switched off only by the e2e tests |
| `UPCITEMDB_USER_KEY` | – | key of a paid UPCitemdb plan; without it the free trial (100 lookups a day per server IP, shared by all users – a few accounts can use it up) is used |
| `UPCITEMDB_DAILY_LIMIT_PER_USER` | `20` | UPCitemdb lookups one user can cause per day (UTC); cached results do not count |
| `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` | – | Twitch application for IGDB (game details, search by title) |

In production install with `npm ci --omit=dev` (Prisma is a runtime dependency: `postinstall`
generates its client and `npm run db:deploy` applies migrations), then `npm run start:prod`.
`GET /api/v1/health` is the liveness probe, `GET /api/v1/health/ready` the readiness probe (it
answers `503` while the database cannot be reached).

Rate limits and the per-user barcode lookup counts are kept in the memory of the process. Run one
instance, or give the throttler a shared storage (e.g. Redis) before scaling out – otherwise every
instance multiplies the limits.

## Restoring the database from a backup

Keep the API stopped (or unreachable for the apps) until the last step:

```bash
# 1. restore the database, 2. apply migrations if needed:
npm run db:deploy
# 3. give every user a new sync epoch and sign everybody out:
npm run build && npm run db:reset-sync
# 4. set a new JWT_SECRET, 5. only now start the API again
```

The backup brings back what was revoked after it was made: sessions that were signed out, or revoked
because a token leaked, and passwords as they were then. So the reset also revokes every refresh token,
and a new `JWT_SECRET` makes the access tokens issued before the restore invalid at once. The apps
keep their data when the session ends this way; after signing in again they push back what the
restore lost. Without the new secret, the apps of accounts created after the backup would present
access tokens of a user that doesn't exist, be told `ACCOUNT_DELETED` and wipe their collection –
the only copy now. Tell users that password changes made after the backup are undone.

The apps' change feed cursors contain the epoch, so every app gets `410 SYNC_RESET_REQUIRED`, pulls
its whole collection again and pushes back the games the restored database doesn't have (created
after the backup, see *Reset* in [`docs/offline-sync.md`](../docs/offline-sync.md)). Without the reset,
an app whose cursor points *past* the restored counter would silently miss the changes others make
until the counter catches up, and one whose cursor points before it would never learn what the
restore took away. While the API runs before step 3, apps would continue their old cursors against
the restored data in the same way.

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
| `npm run db:reset-sync` | makes every app pull everything again and signs everybody out – run after restoring a backup (needs `npm run build`) |

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
  serialised by a row lock; the later one counts as a retry. A successor voided by such a retry
  never reached the app that asked, so presenting it means it leaked: all sessions are revoked.
  Expired tokens of a user are deleted whenever new tokens are issued to them. Changing the password
  signs out all other devices, their access tokens included: tokens carry the password change time
  in ms (`pwc` claim); tokens without it, from before this claim existed, are compared by `iat` in
  whole seconds. Refresh tokens store the same time, so even a refresh that raced the change (and
  issued a successor the revocation could not see yet) leaves no working session behind.
  Passwords are hashed with scrypt from the Node standard library.
- **Validation** follows exactly the rules the apps use ([`docs/mobile-spec.md`](../docs/mobile-spec.md#validation)),
  so that a game saved offline is never rejected later: text lengths are counted in Unicode code
  points (`MaxChars` / `MinChars` in `common/char-length.decorator.ts`), the cover URL must match
  the shared pattern in `common/cover-url.ts`, and the currency is any 3 letters A–Z.
- **Change feed** (`GET /games/changes`, see [`docs/offline-sync.md`](../docs/offline-sync.md)): the
  cursor is `<syncEpoch>.<version>`, opaque to the apps. A cursor whose epoch is not the user's
  current one, or whose version is ahead of the server, is answered with `410 SYNC_RESET_REQUIRED`.
  So is a plain numeric cursor stored by an older app version: without the epoch it cannot be
  checked. Deleted games stay as tombstones with an empty title and every optional column cleared.
  Deleting an id the server has never seen stores a tombstone too, so that a delayed create with
  that id cannot bring the game back; `PATCH` / `PUT` of such an id answer `404 GAME_UNKNOWN`
  (deleted games answer `404 GAME_NOT_FOUND`), and the apps push the game again as a new one.
- **Privacy:** every games query is scoped to the signed-in user; another user's game behaves as
  if it did not exist (404).
- **Errors:** always `{ statusCode, code, message, details? }` – clients branch on `code`.
  U+0000 is removed from every text the API receives (PostgreSQL cannot store it), and prices are
  checked for decimal places also when JSON writes them in exponent notation, so no input ends in a
  500 – which an app's sync would retry forever.
- **Responses** are sent with `Cache-Control: no-store`: they contain the user's collection, which
  must not stay in an HTTP cache on the device after sign-out.
- **Rate limiting:** 300 requests/min per route for every signed-in user (per IP address before
  sign-in), 10/min for endpoints that take a password, and sign-in additionally 20 per 15 minutes per
  account (by e-mail), so that guessing one account's password from many addresses is slow too.
  Counting per user keeps the users of a mobile network, who share a public IP address, from
  throttling each other. Behind a reverse proxy set `TRUST_PROXY` (e.g. `1`), otherwise the proxy's
  IP address is limited.
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
  lookups a day (then `429 TOO_MANY_REQUESTS`; counted in memory per process; a lookup UPCitemdb
  failed to answer doesn't count). The endpoint is
  limited to 30 calls/min. A UPC-A scanned as EAN-13 (leading `0`) is looked up by its 12 digits.
  Cover URLs are returned only when a game can be saved with them.
- **Game search** (`GET /lookup/games?q=…&platform=…`): searches IGDB by title for a game typed in
  by hand. Editions of a game (IGDB versions) and add-ons (DLC, mods, episodes …) are left out; IGDB's
  order of relevance is kept, except that games on the platform chosen in the form come first and then
  a game with exactly the typed title. At most 20 results, cached in memory per query for a day. The
  apps search as the user types (debounced), so the endpoint allows 60 calls/min. Needs the IGDB
  credentials; without them, or when IGDB fails, it answers `503 LOOKUP_UNAVAILABLE`.
