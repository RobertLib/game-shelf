import SwiftUI

/// "My collection" – searchable, filterable, infinitely scrolling list of games.
struct GameListView: View {
    @Bindable var model: GameListViewModel
    let games: any GameService

    @Environment(FacetsStore.self) private var facets

    var body: some View {
        List {
            if !model.games.isEmpty {
                Section {
                    ForEach(model.games) { game in
                        NavigationLink(value: MainRoute.game(game)) {
                            GameRowView(game: game)
                        }
                        .onAppear { model.loadMoreIfNeeded(after: game) }
                    }
                    if model.hasMorePages || model.nextPageError != nil {
                        NextPageRow(error: model.nextPageError) { model.retryNextPage() }
                            .onAppear { model.loadNextPage() }
                    }
                } header: {
                    ResultCountHeader(count: model.totalItems, isReloading: model.isReloading)
                }
            }
        }
        .listStyle(.plain)
        .overlay { stateOverlay }
        .safeAreaInset(edge: .top, spacing: 0) {
            ActiveFilterBar(filter: model.query.filter, onRemove: model.removeFilter, onClearAll: model.resetFilters)
        }
        .navigationTitle("My collection")
        .searchable(text: $model.searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search titles, developers, barcodes…")
        .task(id: model.searchText) {
            do {
                try await Task.sleep(for: .milliseconds(350))
            } catch {
                return
            }
            model.commitSearch()
        }
        .task(id: model.query) {
            await model.loadIfNeeded()
        }
        .refreshable {
            async let facetsReload: Void = facets.reload()
            await model.reload()
            await facetsReload
        }
        .toolbar { toolbar }
        .sheet(item: $model.presentedSheet) { sheet in
            switch sheet {
            case .filters:
                FiltersView(filter: model.query.filter, onApply: model.applyFilter)
            case .newGame:
                GameFormView(mode: .create, service: games) { game in
                    model.apply(.created(game))
                    Task { await facets.reload() }
                }
            }
        }
        .alert(
            "Couldn't refresh",
            isPresented: Binding(get: { model.refreshError != nil }, set: { if !$0 { model.refreshError = nil } }),
            actions: { Button("OK", role: .cancel) {} },
            message: { Text(model.refreshError ?? "") }
        )
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            NavigationLink(value: MainRoute.profile) {
                Label("Profile & settings", systemImage: "person.crop.circle")
            }
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            SortMenu(sort: model.query.sort, order: model.query.order, onSort: model.setSort, onOrder: model.setOrder)
            FilterButton(activeCount: model.query.filter.activeCount) {
                model.presentedSheet = .filters
            }
            Button {
                model.presentedSheet = .newGame
            } label: {
                Label("Add game", systemImage: "plus")
            }
        }
    }

    @ViewBuilder
    private var stateOverlay: some View {
        switch model.phase {
        case .idle, .loading:
            ProgressView("Loading collection…")
        case .failed(let message):
            ContentUnavailableView {
                Label("Couldn't load your collection", systemImage: "exclamationmark.icloud")
            } description: {
                Text(message)
            } actions: {
                Button("Try again") {
                    Task { await model.reload() }
                }
                .buttonStyle(.borderedProminent)
            }
        case .loaded where model.isCollectionEmpty:
            ContentUnavailableView {
                Label("Your collection is empty", systemImage: "square.stack.3d.up.slash")
            } description: {
                Text("Start by adding the first game to your collection.")
            } actions: {
                Button("Add your first game") {
                    model.presentedSheet = .newGame
                }
                .buttonStyle(.borderedProminent)
            }
        case .loaded where model.hasNoResults:
            ContentUnavailableView {
                Label("No games match your filters", systemImage: "magnifyingglass")
            } description: {
                Text("Try a different search or adjust the filters.")
            } actions: {
                Button("Reset filters", action: model.resetFilters)
                    .buttonStyle(.bordered)
            }
        case .loaded:
            EmptyView()
        }
    }
}

// MARK: - Pieces

private struct ResultCountHeader: View {
    let count: Int
    let isReloading: Bool

    var body: some View {
        HStack(spacing: 8) {
            Text(Pluralization.games(count))
                .contentTransition(.numericText())
            if isReloading {
                ProgressView()
                    .controlSize(.mini)
            }
        }
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(.secondary)
        .textCase(nil)
        .accessibilityElement(children: .combine)
    }
}

private struct NextPageRow: View {
    let error: String?
    let onRetry: () -> Void

    var body: some View {
        HStack {
            Spacer()
            if let error {
                VStack(spacing: 8) {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                    Button("Try again", action: onRetry)
                        .buttonStyle(.bordered)
                }
            } else {
                ProgressView()
                    .accessibilityLabel("Loading more games")
            }
            Spacer()
        }
        .padding(.vertical, 8)
        .listRowSeparator(.hidden)
    }
}

/// Removable chips for active filters, shown under the search field.
///
/// Chips wrap instead of scrolling horizontally: every active filter stays visible, and a
/// nested horizontal scroll view next to the navigation bar is not drawn reliably on iOS 26
/// when it appears while a sheet is being dismissed.
private struct ActiveFilterBar: View {
    let filter: GameFilter
    let onRemove: (FilterChip.Key) -> Void
    let onClearAll: () -> Void

    var body: some View {
        let chips = filter.chips
        if !chips.isEmpty {
            FlowLayout(spacing: 6, lineSpacing: 6) {
                ForEach(chips) { chip in
                    RemovableChip(title: chip.label) {
                        withAnimation { onRemove(chip.key) }
                    }
                }
                Button("Clear all") {
                    withAnimation { onClearAll() }
                }
                .font(.footnote.weight(.semibold))
                .padding(.horizontal, 6)
                .padding(.vertical, 6)
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            // Keep the material out of the navigation bar area so the large title stays visible.
            .background(.bar, ignoresSafeAreaEdges: [])
            .overlay(alignment: .bottom) {
                Divider()
            }
        }
    }
}

private struct SortMenu: View {
    let sort: GameSortField
    let order: SortOrder
    let onSort: @MainActor (GameSortField) -> Void
    let onOrder: @MainActor (SortOrder) -> Void

    var body: some View {
        Menu {
            Section("Sort by") {
                Picker("Sort by", selection: Binding(get: { sort }, set: onSort)) {
                    ForEach(GameSortField.allCases, id: \.self) { field in
                        Text(field.label).tag(field)
                    }
                }
            }
            Section("Order") {
                Picker("Order", selection: Binding(get: { order }, set: onOrder)) {
                    Label(SortOrder.asc.label, systemImage: "arrow.up").tag(SortOrder.asc)
                    Label(SortOrder.desc.label, systemImage: "arrow.down").tag(SortOrder.desc)
                }
            }
        } label: {
            Label("Sort", systemImage: "arrow.up.arrow.down")
        }
        .accessibilityValue("\(sort.label), \(order.label.lowercased())")
    }
}

private struct FilterButton: View {
    let activeCount: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "line.3.horizontal.decrease")
                .overlay(alignment: .topTrailing) {
                    if activeCount > 0 {
                        Text(activeCount, format: .number)
                            .font(.caption2.weight(.bold))
                            .monospacedDigit()
                            .foregroundStyle(.white)
                            .padding(.horizontal, 4)
                            .frame(minWidth: 16, minHeight: 16)
                            .background(.red, in: .capsule)
                            .offset(x: 9, y: -9)
                            .fixedSize()
                    }
                }
        }
        .accessibilityLabel("Filters")
        .accessibilityValue(activeCount > 0 ? "\(activeCount) active" : "None")
    }
}

#if DEBUG
#Preview("Collection") {
    let service = PreviewGameService()
    NavigationStack {
        GameListView(model: GameListViewModel(service: service), games: service)
    }
    .environment(FacetsStore(service: service, facets: PreviewData.facets))
}

#Preview("Empty collection") {
    let service = PreviewGameService(games: [])
    NavigationStack {
        GameListView(model: GameListViewModel(service: service), games: service)
    }
    .environment(FacetsStore(service: service, facets: .empty))
}
#endif
