import SwiftUI

/// "Search game database", presented as a sheet from the game form: finds a game by its title. Picking
/// one closes the screen and hands it to `onPick`; closing it changes nothing.
struct GameSearchView: View {
    @State private var model: GameSearchViewModel
    /// A picked game that came out on several platforms, while the user chooses the one of their copy.
    @State private var choosingPlatform: GameSearchPick?
    @FocusState private var isSearchFieldFocused: Bool
    @Environment(\.gameSearch) private var gameSearch
    @Environment(\.dismiss) private var dismiss

    private let onPick: (GameSearchPick) -> Void

    /// `query`: the title typed in the form; `platform`: the platform chosen there.
    init(query: String, platform: Platform?, onPick: @escaping (GameSearchPick) -> Void) {
        _model = State(initialValue: GameSearchViewModel(query: query, platform: platform))
        self.onPick = onPick
    }

    var body: some View {
        NavigationStack {
            List {
                if case .results(let games, let sources, _) = model.content {
                    Section {
                        ForEach(games) { game in
                            Button {
                                pick(game, sources: sources)
                            } label: {
                                GameSearchRow(game: game)
                            }
                        }
                    } footer: {
                        if !sources.isEmpty {
                            Text("Source: \(sources.joined(separator: " · "))")
                        }
                    }
                }
            }
            .overlay { stateOverlay }
            .safeAreaInset(edge: .top, spacing: 0) { searchBar }
            .scrollDismissesKeyboard(.immediately)
            .navigationTitle("Search game database")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .task(id: model.searchTerm) {
            await model.searchTermChanged(using: gameSearch)
        }
        .onAppear {
            isSearchFieldFocused = true
        }
        .confirmationDialog(
            "Which platform is your copy for?",
            isPresented: Binding(get: { choosingPlatform != nil }, set: { if !$0 { choosingPlatform = nil } }),
            titleVisibility: .visible,
            presenting: choosingPlatform
        ) { pick in
            ForEach(pick.game.platforms, id: \.self) { platform in
                Button(platform.label) { finish(pick, platform: platform) }
            }
            Button("Other platform") { finish(pick, platform: nil) }
            Button("Cancel", role: .cancel) {}
        }
    }

    // MARK: Pieces

    /// A search field that stays visible above the results, with the navigation bar (title and
    /// "Cancel") shown while typing.
    private var searchBar: some View {
        HStack(spacing: 6) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            TextField("Game title", text: $model.query)
                .focused($isSearchFieldFocused)
                .submitLabel(.search)
                .autocorrectionDisabled()
                .accessibilityIdentifier("gameSearch.query")
            if case .results(_, _, isRefreshing: true) = model.content {
                ProgressView()
                    .controlSize(.small)
                    .accessibilityLabel("Searching…")
            }
            if !model.query.isEmpty {
                Button("Clear", systemImage: "xmark.circle.fill") {
                    model.query = ""
                    isSearchFieldFocused = true
                }
                .labelStyle(.iconOnly)
                .foregroundStyle(.secondary)
                .buttonStyle(.borderless)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(.fill.tertiary, in: .rect(cornerRadius: 10))
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(.bar, ignoresSafeAreaEdges: [])
    }

    @ViewBuilder
    private var stateOverlay: some View {
        switch model.content {
        case .prompt:
            ContentUnavailableView {
                Label {
                    EmptyView()
                } icon: {
                    Image(systemName: "magnifyingglass")
                }
            } description: {
                Text("Type the title of the game you're adding.")
            }
        case .searching:
            ProgressView("Searching…")
        case .noResults(let query):
            ContentUnavailableView {
                Label("No games found for “\(query)”.", systemImage: "magnifyingglass")
            } description: {
                Text("Check the spelling, or fill in the details yourself.")
            }
        case .failed(let message):
            ContentUnavailableView {
                Label("Couldn't search the game database.", systemImage: "exclamationmark.icloud")
            } description: {
                Text(message)
            } actions: {
                Button("Try again") {
                    Task { await model.retry(using: gameSearch) }
                }
                .buttonStyle(.borderedProminent)
            }
        case .results:
            EmptyView()
        }
    }

    // MARK: Picking

    private func pick(_ game: GameSearchResult, sources: [String]) {
        let pick = GameSearchPick(game: game, platform: nil, sources: sources)
        switch PickedPlatform(for: game, formPlatform: model.platform) {
        case .unchanged:
            finish(pick, platform: nil)
        case .platform(let platform):
            finish(pick, platform: platform)
        case .ask:
            isSearchFieldFocused = false
            choosingPlatform = pick
        }
    }

    private func finish(_ pick: GameSearchPick, platform: Platform?) {
        var pick = pick
        pick.platform = platform
        onPick(pick)
        dismiss()
    }
}

/// One game found: cover, title, release year and platforms, developer.
private struct GameSearchRow: View {
    let game: GameSearchResult

    @ScaledMetric(relativeTo: .headline) private var coverWidth: CGFloat = 40

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            CoverImageView(url: game.coverURL, platform: game.platforms.first ?? .other, cornerRadius: 4)
                .frame(width: coverWidth, height: coverWidth * 1.4)

            VStack(alignment: .leading, spacing: 2) {
                Text(game.title)
                    .font(.headline)
                    .foregroundStyle(Color.primary)
                    .lineLimit(2)
                if !game.yearAndPlatforms.isEmpty {
                    Text(game.yearAndPlatforms)
                        .font(.subheadline)
                        .foregroundStyle(Color.secondary)
                }
                if let developer = game.developer?.nilIfBlank {
                    Text(developer)
                        .font(.subheadline)
                        .foregroundStyle(Color.secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }
}

extension GameSearchResult {
    /// "2017 · Nintendo Switch, Wii U": the release year and the platforms, each only when known; more than
    /// 3 platforms are shortened to the first 3 and "+2" for the rest.
    var yearAndPlatforms: String {
        let shownPlatforms = 3
        var platformList = platforms.prefix(shownPlatforms).map(\.label).joined(separator: ", ")
        if platforms.count > shownPlatforms {
            platformList += " +\(platforms.count - shownPlatforms)"
        }
        return [releaseYear.map(String.init), platformList.nilIfBlank]
            .compactMap(\.self)
            .joined(separator: " · ")
    }
}

#if DEBUG
#Preview("Results") {
    GameSearchView(query: "Mario", platform: nil) { _ in }
        .environment(\.gameSearch, PreviewGameSearchService())
}

#Preview("Offline") {
    GameSearchView(query: "Mario", platform: .switch) { _ in }
}
#endif
