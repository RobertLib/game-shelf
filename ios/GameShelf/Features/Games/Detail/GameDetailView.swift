import SwiftUI

struct GameDetailView: View {
    @State private var model: GameDetailViewModel
    @State private var isEditing = false
    @State private var isConfirmingDelete = false
    @Environment(\.dismiss) private var dismiss

    init(gameID: Game.ID, repository: GameRepository) {
        _model = State(initialValue: GameDetailViewModel(gameID: gameID, repository: repository))
    }

    var body: some View {
        Group {
            if let game = model.game {
                content(game)
            } else if !model.isDeleted {
                ContentUnavailableView(
                    "Game not found.",
                    systemImage: "questionmark.square.dashed",
                    description: Text("This game has been removed from your collection.")
                )
            }
        }
        .alert(
            "Something went wrong",
            isPresented: Binding(get: { model.errorMessage != nil }, set: { if !$0 { model.errorMessage = nil } }),
            actions: { Button("OK", role: .cancel) {} },
            message: { Text(model.errorMessage ?? "") }
        )
    }

    private func content(_ game: Game) -> some View {
        List {
            Section {
                GameDetailHeader(game: game)
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())

            DetailSection(title: "Basics", items: basicItems(game))
            DetailSection(title: "Collector details", items: collectorItems(game))
            DetailSection(title: "Purchase & value", items: purchaseItems(game))
            DetailSection(title: "Other", items: otherItems(game))

            Section {
                Button(role: .destructive) {
                    isConfirmingDelete = true
                } label: {
                    Label("Delete game", systemImage: "trash")
                        .foregroundStyle(.red)
                }
            } footer: {
                Text("Added \(AppFormat.timestamp(game.createdAt)) · Last modified \(AppFormat.timestamp(game.updatedAt))")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(game.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbar(game) }
        .confirmationDialog("Delete game?", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
            Button("Delete game", role: .destructive) {
                Task {
                    if await model.delete() {
                        dismiss()
                    }
                }
            }
        } message: {
            Text("“\(game.title)” will be permanently removed from your collection.")
        }
        .sheet(isPresented: $isEditing) {
            GameFormView(mode: .edit(game), repository: model.repository)
        }
        #if DEBUG
        .task {
            if DebugLaunchOptions.current.consume(.edit) {
                isEditing = true
            }
        }
        #endif
    }

    @ToolbarContentBuilder
    private func toolbar(_ game: Game) -> some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button {
                Task { await model.toggleFavorite() }
            } label: {
                Label(
                    game.favorite ? "Remove from favorites" : "Add to favorites",
                    systemImage: game.favorite ? "star.fill" : "star"
                )
            }
            .tint(game.favorite ? .yellow : nil)
            .sensoryFeedback(.selection, trigger: game.favorite)

            Button("Edit") {
                isEditing = true
            }
        }
    }

    // MARK: Sections

    private func basicItems(_ game: Game) -> [DetailItem] {
        [
            DetailItem("Platform", game.platform.label),
            DetailItem("Edition", game.edition),
            DetailItem("Genre", game.genre),
            DetailItem("Developer", game.developer),
            DetailItem("Publisher", game.publisher),
            DetailItem("Release year", game.releaseYear.map(String.init)),
        ]
    }

    private func collectorItems(_ game: Game) -> [DetailItem] {
        [
            DetailItem("Status", game.status.label),
            DetailItem("Format", game.format.label),
            DetailItem("Region", game.region?.label),
            DetailItem("Completeness", game.completeness?.label),
            DetailItem("Condition", game.condition?.label),
            DetailItem("Barcode (EAN/UPC)", game.barcode),
            DetailItem("Product code", game.productCode),
            DetailItem("Quantity", AppFormat.integer(game.quantity)),
            DetailItem("Storage location", game.storageLocation),
        ]
    }

    private func purchaseItems(_ game: Game) -> [DetailItem] {
        [
            DetailItem("Purchase price", game.purchasePrice.map { AppFormat.currency($0, code: game.currency) }),
            DetailItem("Estimated value", game.estimatedValue.map { AppFormat.currency($0, code: game.currency) }),
            DetailItem("Purchase date", game.purchaseDate.map { AppFormat.date($0) }),
            DetailItem("Purchased from", game.purchasePlace),
        ]
    }

    private func otherItems(_ game: Game) -> [DetailItem] {
        [
            DetailItem("Play status", game.playStatus?.label),
            DetailItem("Rating", game.rating.map { "\($0)/10" }),
            DetailItem("Favorite", game.favorite ? "Yes" : nil),
            DetailItem("Notes", game.notes, multiline: true),
        ]
    }
}

// MARK: - Pieces

struct DetailItem: Identifiable {
    let label: String
    let value: String?
    /// Long text shown under its label instead of beside it.
    var multiline = false

    var id: String { label }

    init(_ label: String, _ value: String?, multiline: Bool = false) {
        self.label = label
        self.value = value?.nilIfBlank
        self.multiline = multiline
    }
}

/// Section with label–value rows; empty values and empty sections are hidden.
private struct DetailSection: View {
    let title: String
    let items: [DetailItem]

    var body: some View {
        let visible = items.filter { $0.value != nil }
        if !visible.isEmpty {
            Section(title) {
                ForEach(visible) { item in
                    if item.multiline {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.label)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text(item.value ?? "")
                                .textSelection(.enabled)
                        }
                        .accessibilityElement(children: .combine)
                    } else {
                        LabeledContent(item.label) {
                            Text(item.value ?? "")
                                .multilineTextAlignment(.trailing)
                                .textSelection(.enabled)
                        }
                    }
                }
            }
        }
    }
}

private struct GameDetailHeader: View {
    let game: Game

    var body: some View {
        VStack(spacing: 12) {
            if let url = game.coverURL {
                CoverImageView(url: url, platform: game.platform, cornerRadius: 12)
                    .aspectRatio(5 / 7, contentMode: .fit)
                    .frame(maxWidth: 220, maxHeight: 300)
                    .shadow(color: .black.opacity(0.2), radius: 12, y: 6)
            }
            VStack(spacing: 4) {
                Text(game.title)
                    .font(.title2.bold())
                    .multilineTextAlignment(.center)
                if let edition = game.edition?.nilIfBlank {
                    Text(edition)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Text([game.platform.label, game.releaseYear.map(String.init)].compactMap(\.self).joined(separator: " · "))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 6) {
                BadgeView(text: game.status.label, tint: game.status.tint)
                if let completeness = game.completeness {
                    BadgeView(text: completeness.label, tint: completeness.tint)
                }
                if let condition = game.condition {
                    BadgeView(text: condition.label, tint: condition.tint)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
    }
}

#if DEBUG
#Preview {
    let sync = SyncEngine.preview()
    NavigationStack {
        GameDetailView(gameID: PreviewData.games[0].id, repository: sync.repository)
    }
    .environment(FacetsStore(repository: sync.repository))
}
#endif
