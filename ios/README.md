# Game Shelf – iOS

Native iOS app for cataloguing a collection of computer and console games. It implements
`docs/mobile-spec.md` against the REST contract in `openapi/openapi.yaml`.

- SwiftUI, Swift 6 (complete strict concurrency), iOS 17+, no third-party dependencies
- `GameShelf.xcodeproj` uses the Xcode 16+ format: the `GameShelf/`, `GameShelfTests/` and
  `GameShelfUITests/` folders are synchronized groups, so new files are picked up automatically
- English is the only localization; numbers, prices (in each game's currency) and dates follow
  the device locale

## Build, run and test

```sh
# Build + unit tests (Swift Testing)
xcodebuild -project ios/GameShelf.xcodeproj -scheme GameShelf \
  -destination 'platform=iOS Simulator,name=iPhone 17' build test

# End-to-end UI walkthrough with screenshots (needs the running API and the demo account)
xcodebuild -project ios/GameShelf.xcodeproj -scheme GameShelfUITests \
  -destination 'platform=iOS Simulator,name=iPhone 17' test
```

`make ios` and `make ios-test` in the repository root run the first two steps.

In Xcode, open `ios/GameShelf.xcodeproj`, pick the **GameShelf** scheme and a simulator, then
Run (⌘R) or Test (⌘U). The Debug build expects the API at `http://localhost:3000/api/v1/` on the
simulator; for a physical device see [Running on a physical iPhone](#running-on-a-physical-iphone).

The UI tests (`GameShelfUITests`) live in a separate scheme because they talk to the live API:
they read the demo account `demo@example.com` (`make seed`) and create and delete their own
throwaway accounts for everything that changes data.

### Debug launch arguments (Debug builds only)

| Argument | Effect |
|---|---|
| `-uiTestingResetSession` | start signed out, without local data |
| `-uiTestingAutoLogin <email> <password>` | sign in when there is no stored session |
| `-uiTestingInitialScreen <screen>` | `list`, `detail`, `edit`, `form`, `filters`, `profile`, `register`, `changePassword`, `deleteAccount` |
| `-uiTestingDemoFilters` | apply sample filters (shows the filter chips) |
| `-uiTestingDemoFiltersDelayed` | the same, applied like "Apply" in the filter sheet (opens and closes it) |
| `-uiTestingScannedBarcode <digits>` | open "Add game" as if the barcode had been scanned (the simulator has no camera) |

Example: `xcrun simctl launch booted cz.gameshelf.app -uiTestingInitialScreen filters`.

## API base URL

The URL comes from the `API_BASE_URL` build setting (target *GameShelf* → Build Settings →
User-Defined) and reaches the app through `Info.plist` (`GSAPIBaseURL`):

| Configuration | `API_BASE_URL` |
|---|---|
| Debug (simulator) | `http://localhost:3000/api/v1/` |
| Debug (device) | `http://$(DEV_API_HOST):3000/api/v1/` |
| Release | none – **required**, the HTTPS URL of the production API |

Release builds (archives, but also Xcode's Profile action, which uses Release) stop with an error
until `API_BASE_URL` is set to a real `https://` URL – the *Check API base URL (Release)* build
phase rejects an empty value, `http://`, `localhost` and `example.*` placeholders. Pass it to
`xcodebuild` or set it for the Release configuration in the target's build settings:

```sh
xcodebuild -project ios/GameShelf.xcodeproj -scheme GameShelf -configuration Release \
  -destination 'generic/platform=iOS' API_BASE_URL=https://api.your-domain/api/v1/ archive
```

Debug builds use `GameShelf/Info-Debug.plist`, which adds App Transport Security's
`NSAllowsLocalNetworking` for the API on this Mac; Release builds use `GameShelf/Info.plist`
without it, so the production API must use HTTPS. The app's privacy manifest
(`GameShelf/PrivacyInfo.xcprivacy`) declares no tracking, the data the account and the collection
send to the API, and the one required-reason API the app uses (`UserDefaults`, reason `CA92.1`);
update it when adding such APIs (file timestamps, system boot time, disk space…).

### Running on a physical iPhone

On the phone, `localhost` is the phone itself, so a device build has to reach the Mac over Wi-Fi.
`DEV_API_HOST` comes from the git-ignored `Config/Local.xcconfig` (included by
`Config/Debug.xcconfig`); create it once from the repository root:

```sh
make ios-device-host                              # uses the Mac's Bonjour name, e.g. My-Mac.local
make ios-device-host DEV_API_HOST=192.168.0.10    # or an IP address, if .local names don't resolve
```

Then rebuild the app in Xcode. The phone and the Mac must be on the same network, and on first
launch the app asks for access to the local network – allow it (later: Settings → Privacy &
Security → Local Network). Without `Local.xcconfig` a device build stops at launch with a message
pointing to `make ios-device-host`.

## Architecture

- **App/** – entry point, composition root (`AppContainer`), `RootView` switching between the
  sign-in flow and the main flow based on `SessionStore`, and `MainView` with a value-based
  `NavigationStack` (`MainRoute`). The `App` builds one `AppContainer` per process, shared by every
  window (iPad multitasking): one session, one database and one sync engine. Process-wide work
  (restoring the session, session expiry, network monitoring) starts with the container; each
  window only reports its scene phase, and the app counts as being in the foreground while any
  window is active.
- **Offline-first** (`docs/offline-sync.md`): every screen reads the collection from the device and
  every change is saved there first.
  - **Core/Persistence** – `GameStore`, an actor owning a SQLite database (`import SQLite3`, file in
    Application Support, excluded from backups): `games`, `pending_changes` (at most one per game,
    coalesced per the doc) and `sync_state` (owner, cursor, the app version that stored it, last
    sync, an unseen "changes were rejected" notice). Every multi-step change is one transaction and
    checks the owner's `StoreAccess`, so nothing is written for a signed-out user. The schema is
    versioned with `PRAGMA user_version` (`GameStore.migrations`, one step per version; add a new
    step, never edit an old one). The file is opened by the first activation; only a damaged file
    (`SQLITE_CORRUPT` / `SQLITE_NOTADB`) is replaced – any other failure keeps it with its unsynced
    changes, and the list shows "Couldn't load your collection" with "Try again". After an app
    update the cursor is forgotten, so the whole collection is pulled again (values an older
    version didn't know were stored as its fallback). A stored game this version can't decode is
    never dropped together with its pending change.
  - **Core/Collection** – `GameRepository` (`@Observable @MainActor`): the in-memory collection the
    screens observe and the local changes (create / field-level edit / favorite / delete); the list
    query and facets are computed locally with the semantics of `GET games` / `GET games/facets`.
  - **Core/Sync** – `SyncEngine`: single-flight runs (push oldest first, then pull the change feed),
    backoff 2 s → 5 min in the foreground, triggers (sign-in, foreground, network, local change,
    pull-to-refresh / "Sync now"); `SyncStatus` for the UI. The engine talks to the API through the
    `GameAPI` protocol (`RemoteGameAPI`), so tests use a fake server. Only a `4xx` with an API error
    body (`ErrorResponse` with a string `code`) rejects a change; a response without one
    (`APIError.http`, e.g. a proxy's page) is temporary, and a `404` means "deleted" only with
    `GAME_NOT_FOUND`. A run (and a token refresh) is wrapped in a `UIApplication` background task
    (`Core/Lifecycle/AppBackgroundTask`), so a request that reached the server finishes when the app
    leaves the foreground.
  - `SessionStore` wipes the local data on sign-out, account deletion and sign-in of another user;
    an expired session keeps it (unsynced changes are pushed after the same user signs in again).
- **Core/Networking** – `APIClient` (Sendable, URLSession, async/await), endpoint descriptions,
  JSON coding for the contract (`purchaseDate` as `YYYY-MM-DD`, ISO-8601 timestamps with
  fractional seconds) and the mapping of error codes to user-facing messages.
- **Core/Auth** – tokens in the Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`),
  the `TokenManager` actor with a single shared refresh on 401 (single-flight, one retry) and
  `SessionStore` (`@Observable @MainActor`). A refresh's result – new tokens or a rejection – is
  applied only while the session it started from is still the current one (sign-out or another
  sign-in meanwhile makes it ignored).
- **Models/** – types matching the OpenAPI contract; unknown enum values decode to a fallback
  (`OTHER` / `unknown`), and `SaveGameRequest` sends empty values as explicit `null`.
- **Features/** – screens by feature (Auth, Games/List, Detail, Edit, Filters, Scan, Search, Profile), each
  with an `@Observable @MainActor` view model; filters and sort live in the list view model.
- **Barcode scanning** (`Features/Games/Scan`) uses VisionKit's `DataScannerViewController`
  (camera permission text: `NSCameraUsageDescription` in the target's build settings). The simulator
  and devices without a supported camera offer typing the number instead; for screenshots use
  `-uiTestingScannedBarcode`. The form looks the code up through `BarcodeLookupService`
  (`GET lookup/barcode/{barcode}`, passed in the SwiftUI environment) and fills only empty fields.
- **Game database search** (`Features/Games/Search`): the magnifier in the Title row opens a sheet
  that searches `GET lookup/games` through `GameSearchService` (also from the environment) as the
  user types (debounced with `.task(id:)`). The picked game replaces the form's title, genre,
  developer, publisher, release year and cover.
- **UI/Components** – shared views (cover with a platform placeholder, chips, flow layout,
  text fields with suggestions from facets, optional date picker…); **UI/Preview** – sample
  data for `#Preview`s (Debug only).
- Formatting uses the device locale; `Pluralization` builds count phrases such as "1 game" and
  "132 games". Prices are typed the same way in every locale (`NumberInput`, docs/mobile-spec.md
  "Decimal input"), and validation (`Core/Validation`) follows the API's rules exactly – lengths in
  Unicode code points, the shared cover URL pattern – skipping values the user didn't change.
