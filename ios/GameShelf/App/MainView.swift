import SwiftUI

enum MainRoute: Hashable {
    case game(Game)
    case profile
    case changePassword
    case deleteAccount
}

/// Signed-in flow: the collection list as the root of a value-based navigation stack.
struct MainView: View {
    let games: any GameService

    @State private var path: [MainRoute] = []
    @State private var list: GameListViewModel
    @State private var facets: FacetsStore

    init(games: any GameService) {
        self.games = games
        _list = State(initialValue: GameListViewModel(service: games))
        _facets = State(initialValue: FacetsStore(service: games))
    }

    var body: some View {
        NavigationStack(path: $path) {
            GameListView(model: list, games: games)
                .navigationDestination(for: MainRoute.self, destination: destination)
        }
        .environment(facets)
        .task {
            await facets.reload()
        }
        #if DEBUG
        .task {
            await DebugLaunchOptions.current.openInitialScreen(list: list, path: $path)
        }
        #endif
    }

    @ViewBuilder
    private func destination(for route: MainRoute) -> some View {
        switch route {
        case .game(let game):
            GameDetailView(game: game, service: games) { change in
                list.apply(change)
                Task { await facets.reload() }
            }
        case .profile:
            ProfileView()
        case .changePassword:
            ChangePasswordView()
        case .deleteAccount:
            DeleteAccountView()
        }
    }
}
