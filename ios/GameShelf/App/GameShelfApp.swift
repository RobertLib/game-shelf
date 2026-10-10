import SwiftUI

@main
struct GameShelfApp: App {
    /// The object graph, built once per process and shared by every window (iPad multitasking): one
    /// session, one local database and one sync engine. `nil` while unit tests run.
    private let container: AppContainer?

    init() {
        if Self.isRunningUnitTests {
            // Unit tests are hosted by the app; keep it idle so it neither touches the
            // Keychain nor calls the API while tests run.
            container = nil
        } else {
            let container = AppContainer.live()
            container.start()
            self.container = container
        }
    }

    var body: some Scene {
        WindowGroup {
            if let container {
                RootView(session: container.session, sync: container.sync)
                    .environment(\.barcodeLookup, container.barcodeLookup)
                    .environment(\.gameSearch, container.gameSearch)
            } else {
                Color.clear
            }
        }
    }

    private static var isRunningUnitTests: Bool {
        #if DEBUG
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
        #else
        false
        #endif
    }
}
