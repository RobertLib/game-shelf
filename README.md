# Game Shelf

A catalogue for collectors of computer and console games. A monorepo with three apps:

| folder | what it is | technology |
|---|---|---|
| [`api/`](api/) | REST API | NestJS 12, Prisma 7, PostgreSQL, OpenAPI 3 (Swagger) |
| [`android/`](android/) | native Android app | Kotlin, Jetpack Compose, Material 3 |
| [`ios/`](ios/) | native iOS app | Swift 6, SwiftUI |
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | contract between the API and the apps | generated from the API code |
| [`docs/mobile-spec.md`](docs/mobile-spec.md) | shared spec of screens, texts and behaviour of both apps | |
| [`docs/offline-sync.md`](docs/offline-sync.md) | how the apps work offline and sync with the API | |

## Features

- Sign-up, sign-in, password change, sign-out and account deletion. Every user sees only their own collection.
- Games: add, edit, delete, detail. For every game you can record:
  title, platform (50 consoles and computers from the ZX Spectrum to the PS5), collection status
  (owned / wishlist / pre-ordered / lent out / for sale / sold), format, region (PAL / NTSC-U / NTSC-J …),
  edition, completeness (sealed, CIB, loose …), condition, genre, developer, publisher, release year,
  barcode, product code, quantity, purchase price, purchase date and place, estimated value, currency,
  storage location, rating 1–10, favorite, cover image URL, play status and notes.
- **Adding a game by its barcode:** point the phone's camera at the barcode on the box; the API looks
  it up in [UPCitemdb](https://www.upcitemdb.com) (barcode → product) and [IGDB](https://www.igdb.com)
  (genre, developer, publisher, release year, cover) and the form is prefilled with what they know.
  It also warns when the game is already in the collection. See [Barcode lookup](#barcode-lookup).
- **Finding a game by its title:** typing a game in by hand, tap the magnifier in the Title field,
  type the title and pick the game from the list of matches in [IGDB](https://www.igdb.com); the form
  is filled in with its title, genre, developer, publisher, release year, cover and platform. See
  [Game search](#game-search).
- List grouped by platform (can be turned off in the sort menu), with full-text search, sorting
  and advanced filters: platforms, status, format, region, completeness, condition, play status, genre, publisher, developer,
  storage location, favorites, with / without cover, and ranges of release year, purchase price,
  estimated value, purchase date and rating. Filter options and form suggestions come from what is
  actually in the collection.
- **Offline-first mobile apps:** the collection is stored on the device, so browsing, searching and
  adding, editing or deleting games work without a connection. Changes sync with the API in both
  directions whenever the server is reachable; edits of different fields on two devices are merged.
  See [docs/offline-sync.md](docs/offline-sync.md).

## Quick start

You need Node.js ≥ 24, PostgreSQL (in Docker, or a local install such as Homebrew), a JDK 17+ and
the Android SDK for Android (Gradle downloads the JDK 25 its daemon runs on by itself, see
[android/README.md](android/README.md)), and Xcode 26+ for iOS.

```bash
make db             # PostgreSQL in Docker (without Docker see below)
cp api/.env.example api/.env
make api-install    # dependencies + migrations
make seed           # demo account demo@example.com / demo-collector with ~40 games
make api-dev        # API at http://localhost:3000/api/v1, Swagger UI at http://localhost:3000/docs
```

**Without Docker** (PostgreSQL from Homebrew or another local install): run `make db-local` instead
of `make db`. It creates the `game_shelf` and `game_shelf_test` databases owned by your user. Then
set `DATABASE_URL` in `api/.env` (and in `api/.env.test` for the e2e tests) to
`postgresql://<your user name>@localhost:5432/game_shelf` (or `…/game_shelf_test`); Homebrew's
PostgreSQL uses your macOS user name and needs no password.

Then run the mobile apps – their debug builds connect to the local API:

- **Android:** open `android/` in Android Studio, or run `make android` and install the APK on an
  emulator. The emulator reaches your computer at `10.0.2.2`. On Android 17+ the debug build asks
  for the local network permission on first launch – without it, it cannot reach the local API.
  Details in [android/README.md](android/README.md).
- **iOS:** open `ios/GameShelf.xcodeproj` in Xcode and run it on a simulator. For a physical
  iPhone, run `make ios-device-host` first so the Debug build reaches the API on your Mac over
  Wi-Fi instead of `localhost`. Details in [ios/README.md](ios/README.md).

`make help` lists all shortcuts (tests, builds, OpenAPI regeneration …).

## API contract

The API code is the source of truth; `openapi/openapi.yaml` is generated from it with `make openapi`
and is committed, so every contract change shows up in the diff, and CI checks that the file matches
the code. The mobile apps have small hand-written networking layers that follow the spec. If you
prefer generated clients, the spec is ready for it (named schemas, `operationId`s, enums as separate
components) – e.g. Apple's swift-openapi-generator or OpenAPI Generator for Kotlin.

In short:

| method | path | description |
|---|---|---|
| POST | `/api/v1/auth/register` | sign up → tokens |
| POST | `/api/v1/auth/login` | sign in → tokens |
| POST | `/api/v1/auth/refresh` | exchange the refresh token (rotation) |
| POST | `/api/v1/auth/logout` | revoke the refresh token |
| GET | `/api/v1/auth/me` | profile |
| POST | `/api/v1/auth/change-password` | change password, signs out other devices |
| DELETE | `/api/v1/auth/me` | delete the account including the collection |
| GET | `/api/v1/games` | list with filters, sorting and pagination |
| GET | `/api/v1/games/facets` | values for filters |
| GET | `/api/v1/games/changes` | change feed for offline sync (cursor-based) |
| POST | `/api/v1/games` | new game; idempotent with a client-generated `id` |
| GET / PUT / PATCH / DELETE | `/api/v1/games/{id}` | detail / replace / change some fields / delete |
| GET | `/api/v1/lookup/barcode/{barcode}` | game details for a scanned EAN / UPC |
| GET | `/api/v1/lookup/games?q=…` | games matching a title, to prefill a game typed in by hand |

Errors always have the shape `{ statusCode, code, message, details? }`; the apps show their own
messages based on `code`.

The apps validate input with exactly the rules of the API (listed in
[docs/mobile-spec.md](docs/mobile-spec.md#validation)): a game is saved on the device first, and a
change the API rejected later would have to be undone.

## Barcode lookup

The apps never call the external databases themselves; the API does (`api/src/lookup`) and caches
the answers in memory:

- **UPCitemdb** turns the code into a shop listing ("Mario Kart 8 Deluxe - Nintendo Switch"), from
  which the API takes the title, platform, edition and region. Without configuration it uses the free
  trial endpoint: **100 lookups a day per server IP address**. For more, buy a plan and set
  `UPCITEMDB_USER_KEY` in `api/.env`. So that one account can't use the quota up for everyone, each
  user gets at most `UPCITEMDB_DAILY_LIMIT_PER_USER` (default 20) UPCitemdb lookups a day; answers
  from the cache don't count, and codes with a wrong check digit are answered as unknown without
  asking UPCitemdb.
- **IGDB** (optional, free) adds genre, developer, publisher, release year and a cover. Register an
  application at [dev.twitch.tv/console](https://dev.twitch.tv/console) (a Twitch account with
  two-factor authentication; IGDB does not use the OAuth redirect URL, `http://localhost` will do),
  generate a client secret and set `IGDB_CLIENT_ID` and `IGDB_CLIENT_SECRET` in `api/.env`.

Coverage of European (PAL) and older games in UPCitemdb is patchy; when a code is not found, the
form keeps the barcode and the rest is filled in by hand.

## Game search

Searching by title uses only IGDB, so it **needs `IGDB_CLIENT_ID` and `IGDB_CLIENT_SECRET`** (see
above); without them the apps show "The game database isn't available right now". The API leaves out
DLC and other add-ons and editions of a game, lists games on the platform already chosen in the form
first, and caches results for a day. Picking a game replaces the title, genre, developer, publisher,
release year and cover in the form; when the game came out on several platforms and none is chosen
yet, the app asks which one the copy is for.

## Tests

```bash
make api-test       # Vitest: unit + e2e (e2e needs api/.env.test, see api/.env.test.example)
make android-test   # JUnit
make ios-test       # Swift Testing
```

iOS also has UI tests that walk through the whole app against the running API and take screenshots
(scheme `GameShelfUITests`, see [ios/README.md](ios/README.md)).
