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
Run (⌘R) or Test (⌘U). The Debug build expects the API at `http://localhost:3000/api/v1/`.

The UI tests (`GameShelfUITests`) live in a separate scheme because they talk to the live API:
they read the demo account `demo@example.com` (`make seed`) and create and delete their own
throwaway accounts for everything that changes data.

### Debug launch arguments (Debug builds only)

| Argument | Effect |
|---|---|
| `-uiTestingResetSession` | start signed out |
| `-uiTestingAutoLogin <email> <password>` | sign in when there is no stored session |
| `-uiTestingInitialScreen <screen>` | `list`, `detail`, `edit`, `form`, `filters`, `profile`, `register`, `changePassword`, `deleteAccount` |
| `-uiTestingDemoFilters` | apply sample filters (shows the filter chips) |
| `-uiTestingDemoFiltersDelayed` | the same, applied like "Apply" in the filter sheet (opens and closes it) |

Example: `xcrun simctl launch booted cz.gameshelf.app -uiTestingInitialScreen filters`.

## API base URL

The URL comes from the `API_BASE_URL` build setting (target *GameShelf* → Build Settings →
User-Defined) and reaches the app through `Info.plist` (`GSAPIBaseURL`):

| Configuration | `API_BASE_URL` |
|---|---|
| Debug | `http://localhost:3000/api/v1/` |
| Release | `https://api.example.com/api/v1/` |

App Transport Security only allows local networking (`NSAllowsLocalNetworking`); the
production API must use HTTPS.

## Architecture

- **App/** – entry point, composition root (`AppContainer`), `RootView` switching between the
  sign-in flow and the main flow based on `SessionStore`, and `MainView` with a value-based
  `NavigationStack` (`MainRoute`).
- **Core/Networking** – `APIClient` (Sendable, URLSession, async/await), endpoint descriptions,
  JSON coding for the contract (`purchaseDate` as `YYYY-MM-DD`, ISO-8601 timestamps with
  fractional seconds) and the mapping of error codes to user-facing messages.
- **Core/Auth** – tokens in the Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`),
  the `TokenManager` actor with a single shared refresh on 401 (single-flight, one retry) and
  `SessionStore` (`@Observable @MainActor`).
- **Models/** – types matching the OpenAPI contract; unknown enum values decode to a fallback
  (`OTHER` / `unknown`), and `SaveGameRequest` sends empty values as explicit `null`.
- **Features/** – screens by feature (Auth, Games/List, Detail, Edit, Filters, Profile), each
  with an `@Observable @MainActor` view model; filters and sort live in the list view model.
- **UI/Components** – shared views (cover with a platform placeholder, chips, flow layout,
  text fields with suggestions from facets, optional date picker…); **UI/Preview** – sample
  data for `#Preview`s (Debug only).
- Formatting uses the device locale; `Pluralization` builds count phrases such as "1 game" and
  "132 games".
