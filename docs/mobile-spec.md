# Game Shelf – shared mobile app specification

Both native apps (Android in `android/`, iOS in `ios/`) implement the same product
against the REST API described in `openapi/openapi.yaml`. Keep them functionally
identical; follow each platform's own UI conventions (Material 3 / Human Interface Guidelines).

UI language: **English** (the only localization). App name: **Game Shelf**.
Numbers, prices and dates are formatted with the **device locale** (prices always in the game's
`currency`). Pluralization follows the platform's plural rules ("1 game", "2 games").

## API basics

- Base URL (debug): Android emulator `http://10.0.2.2:3000/api/v1/`, iOS simulator `http://localhost:3000/api/v1/`.
- JSON, camelCase. Dates: `createdAt`/`updatedAt` are ISO-8601 date-times, `purchaseDate` is `YYYY-MM-DD`.
- Every error body is `ErrorResponse { statusCode, code, message, details? }`. Branch on `code`, never on `message`.
- Auth: `POST auth/register|login` → `AuthResponse { accessToken, expiresIn, refreshToken, user }`.
  Send `Authorization: Bearer <accessToken>`. On **401 from any authenticated call**, call `POST auth/refresh`
  with the refresh token (single-flight: concurrent 401s must share one refresh), store the new pair
  (refresh tokens are single-use and rotate) and retry the original request once. If refresh fails → clear the
  session and show the sign-in screen.
- `POST auth/change-password` returns a new `AuthResponse` – store it (all other sessions are revoked).
- `POST auth/logout { refreshToken }` on sign-out (ignore failures), then clear the local session.
- `DELETE auth/me { password }` deletes the account (required by App Store guideline 5.1.1(v)).
- `GET games` – query params (all optional): `q`, multi-value `platform`, `status`, `format`, `region`,
  `completeness`, `condition`, `playStatus`, `genre` (send repeated params, e.g. `platform=PS2&platform=PS5`),
  `publisher`, `developer`, `storageLocation` (contains), `favorite`, `hasCover` (true/false),
  `releaseYearFrom/To`, `purchaseDateFrom/To` (YYYY-MM-DD), `purchasePriceMin/Max`, `estimatedValueMin/Max`,
  `ratingMin/Max`, `sort` (GameSortField), `order` (asc|desc), `page` (1-based), `pageSize` (≤100, use 25).
  Response `GamePage { items, page, pageSize, totalItems, totalPages }`.
- `GET games/facets` → distinct values with counts (platforms, statuses, genres, publishers, developers,
  storageLocations, releaseYearMin/Max, totalItems). Use for filter options and form autocomplete.
- `POST games` / `PUT games/{id}` take `SaveGameRequest`. PUT is a full replacement – always send every field
  (nulls for empty optional ones). `DELETE games/{id}` → 204.

## Error messages (by `code`)

| code | message |
|---|---|
| INVALID_CREDENTIALS | Incorrect email or password. |
| EMAIL_ALREADY_REGISTERED | This email is already registered. |
| INVALID_CURRENT_PASSWORD | Current password is incorrect. (in the delete-account dialog: Incorrect password.) |
| VALIDATION_FAILED | Please check the entered data. (+ `details` if useful) |
| TOO_MANY_REQUESTS | Too many attempts. Please try again in a moment. |
| GAME_NOT_FOUND | Game not found. |
| network failure / timeout | Can't connect to the server. Check your connection. |
| anything else | Something went wrong. Please try again. |

Validate on the client before sending (same rules as the API): email format, password 8–128 chars,
password confirmation must match, title required (≤200), platform required, release year 1950–2100,
rating 1–10, quantity 1–999, prices ≥ 0 with max 2 decimals, barcode 8–14 digits, cover URL http(s),
currency 3 letters (default CZK).

## Screens

1. **Sign in** – email, password (show/hide), "Sign in", link "Don't have an account? Create one".
2. **Create account** – email, display name (optional), password, confirm password, "Create account".
3. **My collection** (main list)
   - Header shows the result count ("132 games", "1 game").
   - Search field (debounce ~350 ms) → `q`.
   - Sort menu (field + ascending/descending). Filter button with a badge = number of active filters.
   - Active filters as removable chips under the search field + "Clear all".
   - Rows: cover thumbnail (`coverImageUrl`) or a placeholder with the platform short name; title; secondary line
     "Platform · Region · Year"; badges for status (only when not OWNED), completeness and condition;
     star when favorite; estimated value if present.
   - Infinite scroll (pageSize 25), pull-to-refresh, loading/error/empty states:
     empty collection → CTA "Add your first game"; no results → "No games match your filters" + reset button.
   - FAB / toolbar "+" (Add game) → new game form. Tap a row → detail.
   - Toolbar entry to the profile/settings screen.
4. **Filters** (bottom sheet / sheet, opened from the list) – edits a draft, "Apply" applies, "Reset" clears:
   - Platforms (multi-select, grouped by manufacturer; show counts from facets; platforms present in the
     collection first or highlighted), Status, Format, Region, Completeness, Condition, Play status (multi-select chips),
   - Genre (multi-select from facets), Publisher, Developer, Storage location (text with suggestions from facets),
   - Favorites only (toggle), Cover (any / with cover / without cover),
   - Release year from–to, Purchase price from–to, Estimated value from–to, Purchase date from–to, Minimum rating.
   - Filters + sort survive navigation to detail and back (keep them in the list view model).
5. **Game detail** – cover image (large, if any), title, all non-empty fields grouped in sections
   (Basics / Collector details / Purchase & value / Other), favorite toggle (PUT with the full object),
   "Edit", "Delete" (confirmation dialog). Prices formatted with the game's currency.
6. **Add game / Edit game** – sections:
   - Basics: Title*, Platform* (picker grouped by manufacturer, searchable), Edition, Genre,
     Developer, Publisher, Release year, Cover image URL.
   - Collector details: Status (default Owned), Format (default Physical), Region, Completeness,
     Condition, Barcode (EAN/UPC), Product code, Quantity (default 1), Storage location.
   - Purchase & value: Purchase price, Estimated value, Currency (default CZK), Purchase date (date picker,
     clearable), Purchased from.
   - Rating & play: Play status, Rating 1–10 (clearable), Favorite, Notes (multiline).
   - Text fields offer suggestions from facets (genre, publisher, developer, storage location).
   - Inline validation messages, save button disabled while saving, ask before discarding
     unsaved changes ("Discard changes?"). After save, go back and refresh the list / detail.
7. **Profile & settings** – email, display name, "Change password" (current, new, confirm → success message),
   "Sign out", "Delete account" (destructive, explains that the whole collection is deleted, asks for the password).

## Labels

Platforms (display name; group):

| enum | label | group |
|---|---|---|
| PC | PC | PC & Mac |
| MAC | Mac | PC & Mac |
| PS1 | PlayStation | Sony |
| PS2 | PlayStation 2 | Sony |
| PS3 | PlayStation 3 | Sony |
| PS4 | PlayStation 4 | Sony |
| PS5 | PlayStation 5 | Sony |
| PSP | PSP | Sony |
| PS_VITA | PS Vita | Sony |
| XBOX | Xbox | Microsoft |
| XBOX_360 | Xbox 360 | Microsoft |
| XBOX_ONE | Xbox One | Microsoft |
| XBOX_SERIES | Xbox Series X\|S | Microsoft |
| NES | NES | Nintendo |
| SNES | SNES | Nintendo |
| N64 | Nintendo 64 | Nintendo |
| GAMECUBE | GameCube | Nintendo |
| WII | Wii | Nintendo |
| WII_U | Wii U | Nintendo |
| SWITCH | Nintendo Switch | Nintendo |
| SWITCH_2 | Nintendo Switch 2 | Nintendo |
| GAME_BOY | Game Boy | Nintendo |
| GAME_BOY_COLOR | Game Boy Color | Nintendo |
| GAME_BOY_ADVANCE | Game Boy Advance | Nintendo |
| NINTENDO_DS | Nintendo DS | Nintendo |
| NINTENDO_3DS | Nintendo 3DS | Nintendo |
| VIRTUAL_BOY | Virtual Boy | Nintendo |
| MASTER_SYSTEM | Master System | Sega |
| MEGA_DRIVE | Mega Drive / Genesis | Sega |
| MEGA_CD | Mega-CD | Sega |
| SEGA_32X | 32X | Sega |
| SATURN | Saturn | Sega |
| DREAMCAST | Dreamcast | Sega |
| GAME_GEAR | Game Gear | Sega |
| ATARI_2600 | Atari 2600 | Atari |
| ATARI_7800 | Atari 7800 | Atari |
| ATARI_LYNX | Atari Lynx | Atari |
| ATARI_JAGUAR | Atari Jaguar | Atari |
| NEO_GEO | Neo Geo | Other consoles |
| NEO_GEO_POCKET | Neo Geo Pocket | Other consoles |
| PC_ENGINE | PC Engine / TurboGrafx-16 | Other consoles |
| THREE_DO | 3DO | Other consoles |
| ZX_SPECTRUM | ZX Spectrum | Home computers |
| COMMODORE_64 | Commodore 64 | Home computers |
| AMIGA | Amiga | Home computers |
| AMSTRAD_CPC | Amstrad CPC | Home computers |
| ATARI_8BIT | Atari 8-bit (XL/XE) | Home computers |
| ATARI_ST | Atari ST | Home computers |
| MSX | MSX | Home computers |
| OTHER | Other | Other |

- CollectionStatus: OWNED "Owned", WISHLIST "Wishlist", PREORDERED "Pre-ordered", LENT "Lent out", FOR_SALE "For sale", SOLD "Sold".
- GameFormat: PHYSICAL "Physical", DIGITAL "Digital".
- Region: PAL "PAL", NTSC_U "NTSC-U (Americas)", NTSC_J "NTSC-J (Japan)", REGION_FREE "Region free", OTHER "Other".
- Completeness: SEALED "Sealed", CIB "Complete in box (CIB)", GAME_AND_BOX "Game & box", GAME_AND_MANUAL "Game & manual", LOOSE "Loose", BOX_ONLY "Box only".
- Condition: MINT "Mint", NEAR_MINT "Near mint", VERY_GOOD "Very good", GOOD "Good", FAIR "Fair", POOR "Poor".
- PlayStatus: UNPLAYED "Unplayed", PLAYING "Playing", COMPLETED "Completed", ABANDONED "Abandoned".
- GameSortField: title "Title", platform "Platform", releaseYear "Release year", purchaseDate "Purchase date", purchasePrice "Purchase price", estimatedValue "Estimated value", rating "Rating", createdAt "Date added", updatedAt "Last modified".

Field labels: Title, Platform, Status, Format, Region, Edition, Completeness, Condition, Play status, Genre,
Developer, Publisher, Release year, Barcode (EAN/UPC), Product code, Quantity, Purchase price, Purchase date,
Purchased from, Estimated value, Currency, Storage location, Rating, Favorite, Cover image URL, Notes.

Unknown enum values coming from a newer API must not crash decoding (map to an "unknown"/OTHER fallback).
