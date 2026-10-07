#if DEBUG
import SwiftUI

/// DEBUG-only launch arguments for screenshots and manual testing from the command line:
///
///     -uiTestingResetSession                    start signed out
///     -uiTestingAutoLogin <e-mail> <password>   sign in when there is no stored session
///     -uiTestingInitialScreen <screen>          list | detail | edit | form | filters | profile |
///                                               register | changePassword | deleteAccount
///     -uiTestingDemoFilters                     apply sample filters so the chips are visible
///     -uiTestingDemoFiltersDelayed              the same, applied like "Apply" in the filter sheet
@MainActor
final class DebugLaunchOptions {
    enum Screen: String {
        case list, detail, edit, form, filters, profile, register, changePassword, deleteAccount
    }

    static let current = DebugLaunchOptions(arguments: ProcessInfo.processInfo.arguments)

    let resetSession: Bool
    let credentials: (email: String, password: String)?
    let demoFilters: Bool
    let delaysDemoFilters: Bool
    private var pendingScreen: Screen?

    init(arguments: [String]) {
        func values(after flag: String, count: Int) -> [String]? {
            guard let index = arguments.firstIndex(of: flag), arguments.count > index + count else { return nil }
            return Array(arguments[(index + 1)...(index + count)])
        }
        resetSession = arguments.contains("-uiTestingResetSession")
        delaysDemoFilters = arguments.contains("-uiTestingDemoFiltersDelayed")
        demoFilters = arguments.contains("-uiTestingDemoFilters") || delaysDemoFilters
        credentials = values(after: "-uiTestingAutoLogin", count: 2).map { ($0[0], $0[1]) }
        pendingScreen = values(after: "-uiTestingInitialScreen", count: 1).flatMap { Screen(rawValue: $0[0]) }
    }

    /// Returns `true` exactly once when `screen` was requested at launch.
    func consume(_ screen: Screen) -> Bool {
        guard pendingScreen == screen else { return false }
        pendingScreen = nil
        return true
    }

    func autoLoginIfNeeded(_ session: SessionStore) async {
        guard let credentials, session.user == nil else { return }
        do {
            try await session.signIn(email: credentials.email, password: credentials.password)
        } catch {
            debugLog("Auto-login failed: \(error)")
        }
    }

    func openInitialScreen(list: GameListViewModel, path: Binding<[MainRoute]>) async {
        if demoFilters {
            if delaysDemoFilters {
                // Mimic "Apply" in the filter sheet: present it, apply, dismiss.
                try? await Task.sleep(for: .seconds(2))
                list.presentedSheet = .filters
                try? await Task.sleep(for: .seconds(1))
                list.presentedSheet = nil
            }
            var filter = GameFilter()
            filter.platforms = [.ps2, .snes]
            filter.favoritesOnly = true
            filter.releaseYearFrom = 1990
            list.applyFilter(filter)
        }
        guard let screen = pendingScreen else { return }
        switch screen {
        case .list, .register:
            break
        case .filters:
            pendingScreen = nil
            list.presentedSheet = .filters
        case .form:
            pendingScreen = nil
            list.presentedSheet = .newGame
        case .profile:
            pendingScreen = nil
            path.wrappedValue = [.profile]
        case .changePassword:
            pendingScreen = nil
            path.wrappedValue = [.profile, .changePassword]
        case .deleteAccount:
            pendingScreen = nil
            path.wrappedValue = [.profile, .deleteAccount]
        case .detail, .edit:
            // Wait for the collection, then open the most complete record.
            for _ in 0..<50 where list.games.isEmpty {
                try? await Task.sleep(for: .milliseconds(100))
            }
            let game = list.games.max { $0.filledFieldCount < $1.filledFieldCount }
            if screen == .detail { pendingScreen = nil }
            if let game {
                path.wrappedValue = [.game(game.id)]
            }
        }
    }
}

private extension Game {
    var filledFieldCount: Int {
        let optionals: [Any?] = [
            region, edition, completeness, condition, playStatus, genre, developer, publisher, releaseYear,
            barcode, productCode, purchasePrice, purchaseDate, purchasePlace, estimatedValue, storageLocation,
            rating, coverImageUrl, notes,
        ]
        return optionals.compactMap(\.self).count
    }
}
#endif
