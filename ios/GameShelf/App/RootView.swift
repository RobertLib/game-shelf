import SwiftUI

/// Switches between the auth flow and the main flow.
struct RootView: View {
    let session: SessionStore
    let sync: SyncEngine

    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            switch session.state {
            case .signedOut:
                AuthFlowView()
                    .transition(.opacity)
            case .signedIn(let user):
                MainView(sync: sync)
                    .id(user.id)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: session.state)
        .environment(session)
        .onChange(of: scenePhase, initial: true) { _, phase in
            // Coming to the foreground syncs; backoff retries only run in the foreground.
            sync.setAppActive(phase == .active)
        }
        .task {
            await session.resumeSession()
        }
        .task {
            await session.observeSessionExpiration()
        }
        .task {
            await sync.observeNetwork()
        }
        #if DEBUG
        .task {
            await DebugLaunchOptions.current.autoLoginIfNeeded(session)
        }
        #endif
    }
}
