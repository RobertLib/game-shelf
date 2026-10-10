import UIKit

/// Extra time to finish work that has started when the app leaves the foreground (a `UIApplication`
/// background task; not SwiftUI's `BackgroundTask`, which schedules work while the app isn't running).
///
/// A request that has reached the server must be allowed to complete: a token refresh rotates the
/// single-use refresh token, so its lost answer would end the session, and a sync run would be
/// suspended in the middle of a push.
enum AppBackgroundTask {
    /// Runs `body`, asking iOS to keep the app running until it has finished (as long as iOS allows).
    static func run<Value>(
        named name: String,
        isolation: isolated (any Actor)? = #isolation,
        _ body: () async throws -> Value
    ) async rethrows -> Value {
        let assertion = await Assertion(name: name)
        do {
            let value = try await body()
            await assertion.end()
            return value
        } catch {
            await assertion.end()
            throw error
        }
    }
}

/// One `UIApplication` background task, ended exactly once.
@MainActor
private final class Assertion {
    private var identifier = UIBackgroundTaskIdentifier.invalid

    init(name: String) {
        identifier = UIApplication.shared.beginBackgroundTask(withName: name) { [weak self] in
            // Out of time: the work is suspended with the app, which iOS would otherwise terminate.
            self?.end()
        }
    }

    func end() {
        guard identifier != .invalid else { return }
        UIApplication.shared.endBackgroundTask(identifier)
        identifier = .invalid
    }
}
