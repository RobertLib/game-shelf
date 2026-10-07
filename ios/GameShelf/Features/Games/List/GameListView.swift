import SwiftUI

/// "My collection" – the searchable, filterable list of the local collection.
struct GameListView: View {
    @Bindable var model: GameListViewModel

    @State private var isScanning = false
    /// Set by the scanner; the new game opens once the scanner has closed.
    @State private var scannedBarcode: String?

    var body: some View {
        List {
            if !model.games.isEmpty {
                if let sections = model.sections {
                    Section {
                    } header: {
                        ResultCountHeader(count: model.games.count, status: model.syncStatus)
                    }
                    ForEach(sections) { section in
                        Section {
                            rows(section.games)
                        } header: {
                            PlatformSectionHeader(platform: section.platform, count: section.games.count)
                        }
                    }
                } else {
                    Section {
                        rows(model.games)
                    } header: {
                        ResultCountHeader(count: model.games.count, status: model.syncStatus)
                    }
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
        .task(id: model.resultsKey) {
            await model.updateResults()
        }
        .refreshable {
            await model.refresh()
        }
        .toolbar { toolbar }
        .sheet(item: $model.presentedSheet) { sheet in
            switch sheet {
            case .filters:
                FiltersView(filter: model.query.filter, onApply: model.applyFilter)
            case .newGame:
                GameFormView(mode: .create, repository: model.repository)
            case .scannedGame(let barcode):
                GameFormView(mode: .create, repository: model.repository, scannedBarcode: barcode)
            }
        }
        .fullScreenCover(isPresented: $isScanning, onDismiss: openScannedGame) {
            BarcodeScannerView { scannedBarcode = $0 }
        }
        .alert(
            "Couldn't refresh",
            isPresented: Binding(get: { model.refreshError != nil }, set: { if !$0 { model.refreshError = nil } }),
            actions: { Button("OK", role: .cancel) {} },
            message: { Text(model.refreshError ?? "") }
        )
    }

    private func rows(_ games: [Game]) -> some View {
        ForEach(games) { game in
            NavigationLink(value: MainRoute.game(game.id)) {
                GameRowView(game: game)
            }
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            NavigationLink(value: MainRoute.profile) {
                Label("Profile & settings", systemImage: "person.crop.circle")
            }
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            SortMenu(
                sort: model.query.sort,
                order: model.query.order,
                groupByPlatform: model.query.groupByPlatform,
                onSort: model.setSort,
                onOrder: model.setOrder,
                onGroupByPlatform: model.setGroupByPlatform
            )
            FilterButton(activeCount: model.query.filter.activeCount) {
                model.presentedSheet = .filters
            }
            Button {
                isScanning = true
            } label: {
                Label("Scan barcode", systemImage: "barcode.viewfinder")
            }
            Button {
                model.presentedSheet = .newGame
            } label: {
                Label("Add game", systemImage: "plus")
            }
        }
    }

    private func openScannedGame() {
        guard let barcode = scannedBarcode else { return }
        scannedBarcode = nil
        model.presentedSheet = .scannedGame(barcode: barcode)
    }

    @ViewBuilder
    private var stateOverlay: some View {
        switch model.phase {
        case .loading:
            ProgressView("Loading collection…")
        case .failed(let message):
            ContentUnavailableView {
                Label("Couldn't load your collection", systemImage: "exclamationmark.icloud")
            } description: {
                Text(message)
            } actions: {
                Button("Try again") {
                    Task { await model.retry() }
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

/// "132 games" and, at its end, the sync status.
private struct ResultCountHeader: View {
    let count: Int
    let status: SyncStatus

    var body: some View {
        HStack(spacing: 8) {
            Text(Pluralization.games(count))
                .contentTransition(.numericText())
            Spacer(minLength: 8)
            switch status.indicator {
            case .syncing:
                ProgressView()
                    .controlSize(.mini)
                    .accessibilityLabel("Syncing…")
            case .offline:
                Label("Offline", systemImage: "icloud.slash")
                    .labelStyle(.titleAndIcon)
                    .imageScale(.small)
            case .unsynced(let count):
                Text(Pluralization.unsyncedChanges(count))
            case nil:
                EmptyView()
            }
        }
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(.secondary)
        .textCase(nil)
    }
}

/// Platform name and its number of games; pinned while its games scroll past.
private struct PlatformSectionHeader: View {
    let platform: Platform
    let count: Int

    var body: some View {
        HStack(spacing: 8) {
            Text(platform.label)
                .font(.headline)
                // The header style is secondary, which a hierarchical `.primary` would follow.
                .foregroundStyle(Color.primary)
            Spacer(minLength: 8)
            Text(count, format: .number)
                .font(.subheadline.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(.secondary)
        }
        .textCase(nil)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(platform.label), \(Pluralization.games(count))")
        .accessibilityAddTraits(.isHeader)
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

/// Sort field and order, and whether the list is grouped by platform.
private struct SortMenu: View {
    let sort: GameSortField
    let order: SortOrder
    let groupByPlatform: Bool
    let onSort: @MainActor (GameSortField) -> Void
    let onOrder: @MainActor (SortOrder) -> Void
    let onGroupByPlatform: @MainActor (Bool) -> Void

    var body: some View {
        Menu {
            // First, so it is visible without scrolling the menu.
            Toggle("Group by platform", isOn: Binding(get: { groupByPlatform }, set: onGroupByPlatform))
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
        .accessibilityValue("\(sort.label), \(order.label.lowercased())\(groupByPlatform ? ", grouped by platform" : "")")
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
    let sync = SyncEngine.preview()
    NavigationStack {
        GameListView(model: GameListViewModel(sync: sync))
    }
    .environment(FacetsStore(repository: sync.repository))
}

#Preview("Empty collection") {
    let sync = SyncEngine.preview(games: [])
    NavigationStack {
        GameListView(model: GameListViewModel(sync: sync))
    }
    .environment(FacetsStore(repository: sync.repository))
}
#endif
