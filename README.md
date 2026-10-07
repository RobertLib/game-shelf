# Game Shelf

A catalogue for collectors of computer and console games. A monorepo with three apps:

| folder | what it is | technology |
|---|---|---|
| [`api/`](api/) | REST API | NestJS 12, Prisma 7, PostgreSQL, OpenAPI 3 (Swagger) |
| [`android/`](android/) | native Android app | Kotlin, Jetpack Compose, Material 3 |
| [`ios/`](ios/) | native iOS app | Swift 6, SwiftUI |
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | contract between the API and the apps | generated from the API code |
| [`docs/mobile-spec.md`](docs/mobile-spec.md) | shared spec of screens, texts and behaviour of both apps | |

## Features

- Sign-up, sign-in, password change, sign-out and account deletion. Every user sees only their own collection.
- Games: add, edit, delete, detail. For every game you can record:
  title, platform (50 consoles and computers from the ZX Spectrum to the PS5), collection status
  (owned / wishlist / pre-ordered / lent out / for sale / sold), format, region (PAL / NTSC-U / NTSC-J …),
  edition, completeness (sealed, CIB, loose …), condition, genre, developer, publisher, release year,
  barcode, product code, quantity, purchase price, purchase date and place, estimated value, currency,
  storage location, rating 1–10, favorite, cover image URL, play status and notes.
- List with full-text search, sorting, pagination (infinite scroll) and advanced filters:
  platforms, status, format, region, completeness, condition, play status, genre, publisher, developer,
  storage location, favorites, with / without cover, and ranges of release year, purchase price,
  estimated value, purchase date and rating. Filter options and form suggestions come from what is
  actually in the collection (`GET /games/facets`).

## Quick start

You need Node.js ≥ 24, PostgreSQL (local, or `docker compose up -d`), JDK 17 and the Android SDK
for Android, and Xcode 26+ for iOS.

```bash
make db             # PostgreSQL in Docker (or use your own and edit api/.env)
cp api/.env.example api/.env
make api-install    # dependencies + migrations
make seed           # demo account demo@example.com / demo-collector with ~40 games
make api-dev        # API at http://localhost:3000/api/v1, Swagger UI at http://localhost:3000/docs
```

Then run the mobile apps – their debug builds connect to the local API:

- **Android:** open `android/` in Android Studio, or run `make android` and install the APK on an
  emulator. The emulator reaches your computer at `10.0.2.2`. On Android 17+ the debug build asks
  for the local network permission on first launch – without it, it cannot reach the local API.
  Details in [android/README.md](android/README.md).
- **iOS:** open `ios/GameShelf.xcodeproj` in Xcode and run it on a simulator. Details in
  [ios/README.md](ios/README.md).

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
| POST | `/api/v1/games` | new game |
| GET / PUT / DELETE | `/api/v1/games/{id}` | detail / update / delete |

Errors always have the shape `{ statusCode, code, message, details? }`; the apps show their own
messages based on `code`.

## Tests

```bash
make api-test       # Vitest: unit + e2e (e2e needs api/.env.test, see api/.env.test.example)
make android-test   # JUnit
make ios-test       # Swift Testing
```

iOS also has UI tests that walk through the whole app against the running API and take screenshots
(scheme `GameShelfUITests`, see [ios/README.md](ios/README.md)).
