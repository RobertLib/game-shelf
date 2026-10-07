# Game Shelf – shared mobile app specification

Both native apps (Android in `android/`, iOS in `ios/`) implement the same product
against the REST API described in `openapi/openapi.yaml`. Keep them functionally
identical; follow each platform's own UI conventions (Material 3 / Human Interface Guidelines).

Both apps are **offline-first**: the collection is stored on the device, every screen reads it from
there and every change is saved locally first, then synced with the API when the server can be
reached. The sync protocol and the rules both apps follow are in [offline-sync.md](offline-sync.md).

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
- The list, its filters and the facets are computed **on the device** from the synced collection,
  with the same semantics as `GET games` and `GET games/facets` (the apps do not call those endpoints):
  - search `q`: every whitespace-separated word must be contained (case-insensitive) in the title,
    edition, developer, publisher, genre, product code, barcode or notes;
  - multi-value filters (platform, status, format, region, completeness, condition, play status, genre)
    match any of the selected values; genre compares case-insensitively; publisher, developer and
    storage location are case-insensitive "contains"; favorites only; with / without cover;
    inclusive ranges of release year, purchase date, purchase price, estimated value and minimum rating
    (games without a value never match a range);
  - sort by `GameSortField` ascending or descending, games without a value in the sorted field always
    last, then by title and id; `platform` sorts in the order of the Platform enum (manufacturer and
    generation), titles compare locale-aware and case-insensitive;
  - facets: distinct values with counts (platforms, statuses, genres, publishers, developers,
    storageLocations, releaseYearMin/Max, totalItems), sorted by count descending, then by value;
    text values that differ only in letter case are one facet labelled with the most common spelling.
- Sync (details in [offline-sync.md](offline-sync.md)): `GET games/changes?cursor=…` (change feed),
  `POST games` with a client-generated `id` (`CreateGameRequest`), `PATCH games/{id}` with only the
  changed fields (`UpdateGameRequest`), `DELETE games/{id}` (idempotent), `GET games/{id}`.
  `PUT games/{id}` (full replacement) still exists for older app versions.
- Barcode lookup: `GET lookup/barcode/{barcode}` → `BarcodeLookup` (title, platform, region, edition,
  genre, developer, publisher, release year, cover URL and the `sources` to show as attribution).
  `404 BARCODE_NOT_FOUND` = no database knows the code (not an error for the user),
  `503 LOOKUP_UNAVAILABLE` = the database can't be reached now. Unlike the collection it needs a
  connection; nothing is saved by it.

## Error messages (by `code`)

| code | message |
|---|---|
| INVALID_CREDENTIALS | Incorrect email or password. |
| EMAIL_ALREADY_REGISTERED | This email is already registered. |
| INVALID_CURRENT_PASSWORD | Current password is incorrect. (in the delete-account dialog: Incorrect password.) |
| VALIDATION_FAILED | Please check the entered data. (+ `details` if useful) |
| TOO_MANY_REQUESTS | Too many attempts. Please try again in a moment. |
| GAME_NOT_FOUND | Game not found. |
| LOOKUP_UNAVAILABLE | The game database isn't available right now. Try again later. |
| network failure / timeout | Can't connect to the server. Check your connection. |
| a synced change was rejected (see offline-sync.md) | Some changes were rejected by the server and have been undone. |
| anything else | Something went wrong. Please try again. |

Validate on the client before sending (same rules as the API): email format, password 8–128 chars,
password confirmation must match, title required (≤200), platform required, release year 1950–2100,
rating 1–10, quantity 1–999, prices ≥ 0 with max 2 decimals, barcode 8–14 digits, cover URL http(s),
currency 3 letters (default CZK).

## Screens

1. **Sign in** – email, password (show/hide), "Sign in", link "Don't have an account? Create one".
2. **Create account** – email, display name (optional), password, confirm password, "Create account".
3. **My collection** (main list)
   - Header shows the result count ("132 games", "1 game") and, at its end, the sync status:
     a small progress indicator while syncing (accessibility label "Syncing…"), "Offline" with a
     cloud-off icon when the server can't be reached, otherwise "3 unsynced changes" while changes
     wait to be pushed; nothing when everything is synced.
   - Search field (debounce ~350 ms).
   - Sort menu (field + ascending/descending). Filter button with a badge = number of active filters.
   - Active filters as removable chips under the search field + "Clear all".
   - Rows: cover thumbnail (`coverImageUrl`) or a placeholder with the platform short name; title; secondary line
     "Platform · Region · Year"; badges for status (only when not OWNED), completeness and condition;
     star when favorite; estimated value if present.
   - The whole result is one list (no paging); it updates by itself when a sync brings changes.
   - Pull-to-refresh runs a sync; when it fails, show the error message (e.g. "Can't connect to the
     server. Check your connection.") and keep the list.
   - States: before the first sync has completed and with nothing stored → loading ("Loading
     collection…"); the first sync failed and nothing is stored → "Couldn't load your collection" +
     the error message + "Try again"; empty collection → CTA "Add your first game"; no results →
     "No games match your filters" + reset button.
   - FAB / toolbar "+" (Add game) → new game form. "Scan barcode" (Android: a small FAB above
     "Add game"; iOS: toolbar button `barcode.viewfinder` next to "+") → scanner → new game form
     opened with the scanned barcode (see "Scanning a barcode"). Tap a row → detail.
   - Toolbar entry to the profile/settings screen.
   - After a change is saved: "Game saved." / after a delete: "Game deleted." (Android snackbar;
     iOS shows the change in the list). When the sync engine undoes rejected changes, show
     "Some changes were rejected by the server and have been undone."
4. **Filters** (bottom sheet / sheet, opened from the list) – edits a draft, "Apply" applies, "Reset" clears:
   - Platforms (multi-select, grouped by manufacturer; show counts from facets; platforms present in the
     collection first or highlighted), Status, Format, Region, Completeness, Condition, Play status (multi-select chips),
   - Genre (multi-select from facets), Publisher, Developer, Storage location (text with suggestions from facets),
   - Favorites only (toggle), Cover (any / with cover / without cover),
   - Release year from–to, Purchase price from–to, Estimated value from–to, Purchase date from–to, Minimum rating.
   - Filters + sort survive navigation to detail and back (keep them in the list view model).
5. **Game detail** – cover image (large, if any), title, all non-empty fields grouped in sections
   (Basics / Collector details / Purchase & value / Other), favorite toggle (instant, saved locally),
   "Edit", "Delete" (confirmation dialog; the game disappears at once). Prices formatted with the game's
   currency. The screen follows the stored game, so a sync updates it; if the game is deleted on another
   device, show "Game not found."
6. **Add game / Edit game** – sections:
   - Basics: Title*, Platform* (picker grouped by manufacturer, searchable), Edition, Genre,
     Developer, Publisher, Release year, Cover image URL.
   - Collector details: Status (default Owned), Format (default Physical), Region, Completeness,
     Condition, Barcode (EAN/UPC), Product code, Quantity (default 1), Storage location.
   - Purchase & value: Purchase price, Estimated value, Currency (default CZK), Purchase date (date picker,
     clearable), Purchased from.
   - Rating & play: Play status, Rating 1–10 (clearable), Favorite, Notes (multiline).
   - Text fields offer suggestions from facets (genre, publisher, developer, storage location).
   - The Barcode field has a "Scan barcode" button (in add and edit mode).
   - Inline validation messages, ask before discarding unsaved changes ("Discard changes?").
     Saving is local and works offline; after save, go back – the list and detail update by themselves.
7. **Profile & settings** – email, display name; "Collection" (number of games and platforms, from the
   local data); "Sync":
   - Status: "Syncing…" / "Offline" / "3 unsynced changes" / "All changes synced" / "Not synced yet"
     (nothing synced and nothing waiting),
   - Last synced: relative time ("5 minutes ago") or "Never",
   - "Sync now" (disabled while syncing; a failure shows the error message);

   "Change password" (current, new, confirm → success message), "Sign out", "Delete account"
   (destructive, explains that the whole collection is deleted, asks for the password).
   - Sign out asks for confirmation. With unsynced changes the dialog warns: "1 change hasn't been
     synced yet. It will be lost if you sign out now." / "3 changes haven't been synced yet. They will
     be lost if you sign out now." with the destructive button "Sign out anyway"; otherwise "Your
     collection stays on the server. You can sign in again at any time." with "Sign out".
   - Signing out and deleting the account remove the collection from the device. When the session
     expires on its own, the local data and unsynced changes are kept for the next sign-in of the same
     user.

## Scanning a barcode

1. **Scanner** – a full-screen camera view reading EAN-13, EAN-8, UPC-A and UPC-E. It also lets the
   user type the number printed under the barcode (iOS: "Enter the number instead"; Android: the
   scanner's own manual input). On iOS typing is the only option where the device can't scan
   (simulator, older iPads), and a denied camera permission
   shows "Allow Game Shelf to use the camera in Settings to scan barcodes." + "Open Settings".
   Android uses Google's code scanner from Play services (no camera permission); when it can't
   start: "Couldn't open the barcode scanner. Try again in a moment." Closing the scanner does nothing.
2. The code is **normalized**: zeros padding a UPC-A to 13/14 digits are dropped (iOS reads UPC-A
   as EAN-13 with a leading `0`), so both platforms store the same 12 digits.
3. The form puts it into Barcode and calls `GET lookup/barcode/{barcode}`. A card above the form shows:
   - while waiting: progress + "Looking up the game…",
   - found: the lookup fills **only the fields that are still empty** (title, platform, edition,
     genre, developer, publisher, release year, cover URL, region), so nothing the user typed is
     overwritten – "Details filled in from the game database. Check them before saving." and
     "Source: UPCitemdb · IGDB" (the `sources`),
   - 404: "This barcode isn't in the game database. Fill in the details yourself.",
   - failure: "Couldn't look up the barcode. " + the error message + "Try again".
   - When another game in the collection has the same barcode (compared normalized):
     "Already in your collection: <title> (<platform>)" – a warning only, a second copy can be saved.
   The card can be dismissed (except while waiting). A typed code that is not 8–14 digits is put
   into the field (form validation reports it) and not looked up.
4. Nothing is saved until the user taps Save; leaving the form asks "Discard changes?".

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
