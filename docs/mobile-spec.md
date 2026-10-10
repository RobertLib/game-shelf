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
  session and show the sign-in screen. Apply the result of a refresh (new tokens or a rejection) only while the
  session still holds the refresh token that was sent; a sign-out or a new sign-in during the refresh wins.
  A network failure during a refresh keeps the session and the old refresh token: if the lost request did
  reach the server, the API still accepts the old token for a short while (`REFRESH_TOKEN_REUSE_GRACE_SECONDS`,
  2 minutes by default) as long as the successor it issued has not been used.
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
- Game search: `GET lookup/games?q=…&platform=…` → `GameSearchResponse { items, sources }`, at most 20
  `GameSearchResult`s (IGDB id, title, platforms, genre, developer, publisher, release year, cover URL),
  the most likely first; an empty `items` is "nothing found". `q` is 2–100 characters; `platform` (optional)
  is the platform chosen in the form, games on it are listed first. `503 LOOKUP_UNAVAILABLE` = the database
  can't be reached now. Needs a connection; nothing is saved by it.

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

## Validation

The apps validate on the client with **exactly the rules of the API**. This matters: a game is saved
locally first, and a change the API rejects later is undone by the sync engine (see
[offline-sync.md](offline-sync.md)), so anything the form lets through must be accepted by the API.

- Text is trimmed; a blank optional text is sent as `null`. **Lengths are counted in Unicode code
  points** (Kotlin `codePointCount`, Swift `unicodeScalars.count`, JS `[...s].length`): `"😀"` is 1,
  `"🇨🇿"` is 2, a decomposed `"é"` (`e` + U+0301) is 2.
- Account: email format, password 8–128, display name ≤ 100, password confirmation must match.
- Game:

  | field | rule |
  |---|---|
  | title | required, ≤ 200 |
  | platform | required |
  | edition, genre, developer, publisher, purchase place, storage location | ≤ 100 |
  | product code | ≤ 50 |
  | notes | ≤ 5000 |
  | barcode | 8–14 ASCII digits |
  | release year | 1950–2100 |
  | quantity | 1–999 |
  | rating | 1–10 |
  | purchase price, estimated value | 0 – 9 999 999 999.99, at most 2 decimal places (as typed: `1,500` is fine, `1,5000` is not) |
  | currency | 3 letters A–Z (case-insensitive, stored upper-case; historic codes such as DEM or SKK are fine), default CZK |
  | cover image URL | ≤ 2048 and matches the cover URL pattern below |

- **Values the user did not change are not validated.** An edit sends only the changed fields, so a
  value that came from the server (e.g. an enum value this app version doesn't know, shown as
  "unknown") never blocks saving an edit of another field, and is never sent back.

### Cover URL pattern

ASCII only, identical on all three platforms (write character classes out, never `\d`, `\s` or `\S`,
whose meaning differs between regex engines):

```
^[Hh][Tt][Tt][Pp][Ss]?://[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?(:[0-9]{1,5})?([/?#][!-~]*)?$
```

| valid | invalid |
|---|---|
| `https://example.com/a.png` | `ftp://example.com/a.png` |
| `HTTPS://Example.COM/A.png` | `example.com/a.png` |
| `http://localhost/x.png` | `https://` |
| `https://nas/cover.jpg` | `https://-example.com/a.png` |
| `https://example.com:8443/a?b=c#d` | `https://example.com./a.png` |
| `https://example.com?x=1` | `https://exa mple.com/a.png` |
| `https://img.example.co.uk/a_b/%C3%A9.jpg` | `https://example.com/a b.png` |
| | `https://example.com/é.png` |
| | `https://user:pw@example.com/a.png` |
| | `https://my_host/a.png` |
| | `https://[::1]/a.png` |
| | `https://example.com:123456/a.png` |

### Decimal input

Prices and the price filter bounds accept a decimal point or a decimal comma and grouping, the same
way in every locale:

1. Remove all whitespace (including no-break spaces U+00A0, U+202F and U+2009).
2. What remains may only contain digits, `.` and `,`, with at least one digit.
3. Find the decimal separator:
   - both `.` and `,` occur → the one that occurs **last** is the decimal separator (it must occur
     once); the other one is grouping;
   - only one of them occurs, more than once → it is grouping (no decimals);
   - only one of them occurs, once → it is the decimal separator, **unless exactly 3 digits follow it**,
     then it is grouping (prices never have 3 decimals, so `1,500` and `1.500` are 1500).
4. With grouping, the part before the decimal separator must be 1–3 digits, not starting with `0`,
   followed by groups of exactly 3 digits (`12,345,678`); otherwise the input is invalid (so `0,500`
   and `0.001` are invalid, not 500 and 1).
5. Either the whole part or the decimal part may be empty, not both (`,5` = 0.5, `5,` = 5).
6. "At most 2 decimal places" counts the decimal digits **as typed** (`1,50` is fine, `1,500` is
   1500, `1,5000` has 4 decimal places).

| input | value | | input | value |
|---|---|---|---|---|
| `1299.90` | 1299.90 | | `1,000` | 1000 |
| `1299,9` | 1299.9 | | `2.500` | 2500 |
| `1 299,90` | 1299.90 | | `1.234.567,89` | 1234567.89 |
| `1,299.90` | 1299.90 | | `12,345,678` | 12345678 |
| `1.299,90` | 1299.90 | | `,5` | 0.5 |
| `1,5` | 1.5 | | `5,` | 5 |
| `1,50` | 1.50 | | `1,5000` | 1.5000 (rejected: 4 decimals) |
| `1.23,45` | invalid | | `1,2,3` | invalid |
| `1.2.3` | invalid | | `.` | invalid |
| `1,2.3` | invalid | | `12a` | invalid |
| `0,500` | invalid | | `0.001` | invalid |

## Screens

1. **Sign in** – email, password (show/hide), "Sign in", link "Don't have an account? Create one".
2. **Create account** – email, display name (optional), password, confirm password, "Create account".
3. **My collection** (main list)
   - Header shows the result count ("132 games", "1 game") and, at its end, the sync status:
     a small progress indicator while syncing (accessibility label "Syncing…"), "Offline" with a
     cloud-off icon when the server can't be reached, otherwise "3 unsynced changes" while changes
     wait to be pushed; nothing when everything is synced.
   - Search field (debounce ~350 ms).
   - Sort menu: a "Group by platform" toggle first (on by default), then field + ascending/descending.
     Filter button with a badge = number of active filters.
   - Active filters as removable chips under the search field + "Clear all".
   - Rows: cover thumbnail (`coverImageUrl`) or a placeholder with the platform short name; title; secondary line
     "Platform · Region · Year"; badges for status (only when not OWNED), completeness and condition;
     star when favorite; estimated value if present.
   - Grouped by platform: one section per platform in the result, under a sticky header with the platform
     label and, at its end, its number of games (accessibility: one heading "PlayStation 2, 12 games").
     Sections follow the Platform enum order (reversed when sorted by platform descending); within a section
     games keep the chosen sort. The result count header comes before the first section. With grouping off,
     the result is one flat list in the chosen sort (e.g. the most valuable games across all platforms).
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
   - Filters, sort and grouping survive navigation to detail and back (keep them in the list view model).
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
   - The Title field has a "Search game database" button (in add and edit mode), see
     "Searching the game database".
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

## Searching the game database

Typing a game in by hand, the user can find it in the game database (IGDB, through the API) by its title
and have the form filled in from it.

1. **Entry** – the "Search game database" button at the end of the Title field (Android: a magnifier
   `IconButton` as the field's trailing icon; iOS: a borderless `magnifyingglass` icon button at the end of
   the Title row). It opens the search screen (Android: a full-screen dialog with a top app bar; iOS: a sheet
   with its own navigation stack). The search field is focused and holds the current title (trimmed); when
   that has at least 2 characters, it is searched right away.
2. **Search screen** – title "Search game database", a close button (Android: the X navigation icon
   "Close"; iOS: "Cancel"); closing changes nothing. A search field with the prompt "Game title":
   - searches as the user types: ~400 ms after the last change, only when the trimmed text has at least
     2 characters, with `platform` = the platform chosen in the form, if any. A newer search replaces an older
     one; an answer that comes late is ignored;
   - fewer than 2 characters: "Type the title of the game you're adding." and no results;
   - searching with nothing shown yet: progress + "Searching…"; searching again keeps the previous results
     on screen with a small progress indicator until the new ones arrive;
   - results: one row per game – cover thumbnail (`coverImageUrl`; a placeholder without it), title,
     a secondary line "2017 · Nintendo Switch, Wii U" (release year and platform labels, each only when
     known; more than 3 platforms are shortened to the first 3 + "+2"), and the developer on a third line
     when known. Under the list "Source: IGDB" (the `sources`). Each row reads as one accessibility element;
   - nothing found: "No games found for “<query>”." and "Check the spelling, or fill in the details
     yourself.";
   - failure: "Couldn't search the game database. " + the error message (see Error messages) + "Try again".
3. **Platform of the picked game** – tapping a result picks it:
   - the form already has a platform → it stays;
   - otherwise the game lists exactly one platform → that one;
   - it lists several → ask "Which platform is your copy for?" with a choice per platform (in the listed
     order), "Other platform" (leaves Platform empty) and "Cancel" (back to the results). Android: an
     `AlertDialog` with the platforms as a list and the two text buttons; iOS: a `confirmationDialog`;
   - it lists none → Platform stays empty.
4. **Filling the form** – the search screen closes and the picked game **replaces** the Title, Genre,
   Developer, Publisher, Release year and Cover image URL (the user chose this game, unlike a scanned
   barcode, which only fills empty fields); a value the database doesn't know leaves its field as it is.
   Platform follows point 3; no other field changes. The card above the form (the one used by barcode
   lookups) shows "Details filled in from the game database. Check them before saving." and
   "Source: IGDB" (the `sources`); a barcode lookup still in progress is cancelled. When another game in
   the collection has the same title (trimmed, ignoring letter case) and the same platform, the card also
   shows "Already in your collection: <title> (<platform>)" – a warning only.
5. Nothing is saved until the user taps Save; leaving the form asks "Discard changes?".

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
They are never sent back (see Validation), and after an app update the collection is pulled again so
that values the old version did not know are stored properly (see [offline-sync.md](offline-sync.md)).
