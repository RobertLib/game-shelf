import SwiftUI

enum MainRoute: Hashable {
    case game(Game.ID)
    case profile
    case changePassword
    case deleteAccount
}

/// Signed-in flow: the collection list as the root of a value-based navigation stack.
struct MainView: View {
    let sync: SyncEngine

    @State private var path: [MainRoute] = []
    @State private var list: GameListViewModel
    @State private var facets: FacetsStore

    init(sync: SyncEngine) {
        self.sync = sync
        _list = State(initialValue: GameListViewModel(sync: sync))
        _facets = State(initialValue: FacetsStore(repository: sync.repository))
    }

    var body: some View {
        NavigationStack(path: $path) {
            GameListView(model: list)
                .navigationDestination(for: MainRoute.self, destination: destination)
        }
        .environment(facets)
        .alert(
            ErrorMessage.changesRejected,
            isPresented: Binding(
                get: { sync.status.hasUndoneRejectedChanges },
                set: { if !$0 { sync.repository.acknowledgeUndoneRejectedChanges() } }
            ),
            actions: { Button("OK", role: .cancel) {} }
        )
        #if DEBUG
        .task {
            await DebugLaunchOptions.current.openInitialScreen(list: list, path: $path)
        }
        #endif
    }

    @ViewBuilder
    private func destination(for route: MainRoute) -> some View {
        switch route {
        case .game(let id):
            GameDetailView(gameID: id, repository: sync.repository)
        case .profile:
            ProfileView(sync: sync)
        case .changePassword:
            ChangePasswordView()
        case .deleteAccount:
            DeleteAccountView()
        }
    }
}
