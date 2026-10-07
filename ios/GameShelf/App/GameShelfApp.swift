import SwiftUI

@main
struct GameShelfApp: App {
    var body: some Scene {
        WindowGroup {
            if Self.isRunningUnitTests {
                // Unit tests are hosted by the app; keep it idle so it neither touches the
                // Keychain nor calls the API while tests run.
                Color.clear
            } else {
                AppRoot()
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

private struct AppRoot: View {
    @State private var container = AppContainer.live()

    var body: some View {
        RootView(session: container.session, sync: container.sync)
            .environment(\.barcodeLookup, container.barcodeLookup)
    }
}
