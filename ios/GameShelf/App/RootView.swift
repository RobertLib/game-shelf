import SwiftUI

/// Switches between the auth flow and the main flow.
struct RootView: View {
    let session: SessionStore
    let games: any GameService

    var body: some View {
        Group {
            switch session.state {
            case .signedOut:
                AuthFlowView()
                    .transition(.opacity)
            case .signedIn(let user):
                MainView(games: games)
                    .id(user.id)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: session.state)
        .environment(session)
        .task {
            await session.observeSessionExpiration()
        }
        #if DEBUG
        .task {
            await DebugLaunchOptions.current.autoLoginIfNeeded(session)
        }
        #endif
    }
}
