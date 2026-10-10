import SwiftUI

/// Switches between the auth flow and the main flow. One per window; the session and the sync
/// engine are shared by all windows.
struct RootView: View {
    let session: SessionStore
    let sync: SyncEngine

    @Environment(\.scenePhase) private var scenePhase
    /// Identifies this window to the sync engine, which follows whether any window is active.
    @State private var sceneID = UUID()

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
            sync.setScene(sceneID, isActive: phase == .active)
        }
        .onDisappear {
            sync.setScene(sceneID, isActive: false)
        }
    }
}
