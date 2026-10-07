import SwiftUI

/// "Filters" sheet. Edits a draft; "Apply" applies it, "Reset" clears it.
struct FiltersView: View {
    let onApply: (GameFilter) -> Void

    @State private var draft: FilterDraft
    @Environment(FacetsStore.self) private var facetsStore
    @Environment(\.dismiss) private var dismiss

    init(filter: GameFilter, onApply: @escaping (GameFilter) -> Void) {
        self.onApply = onApply
        _draft = State(initialValue: FilterDraft(filter))
    }

    private var facets: GameFacets { facetsStore.facets }

    var body: some View {
        NavigationStack {
            Form {
                platformSection
                ChipSelectionSection(
                    title: "Status",
                    values: CollectionStatus.allCases,
                    selection: $draft.filter.statuses,
                    count: { facets.count(of: $0) }
                )
                ChipSelectionSection(title: "Format", values: GameFormat.allCases, selection: $draft.filter.formats)
                ChipSelectionSection(title: "Region", values: Region.allCases, selection: $draft.filter.regions)
                ChipSelectionSection(title: "Completeness", values: Completeness.allCases, selection: $draft.filter.completeness)
                ChipSelectionSection(title: "Condition", values: Condition.allCases, selection: $draft.filter.conditions)
                ChipSelectionSection(title: "Play status", values: PlayStatus.allCases, selection: $draft.filter.playStatuses)
                genreSection
                textSection
                flagsSection
                rangeSections
            }
            .accessibilityIdentifier("filters.form")
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("Filters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Reset") {
                        withAnimation { draft = FilterDraft() }
                    }
                    .disabled(draft.isEmpty)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Apply") {
                        guard let filter = draft.makeFilter() else { return }
                        onApply(filter)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(draft.makeFilter() == nil)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        // A dense form stays legible on an opaque background even at the medium detent.
        .presentationBackground(Color(.systemGroupedBackground))
    }

    // MARK: Sections

    private var platformSection: some View {
        Section {
            let inCollection = facets.platformCounts
            let extraSelected = Platform.allCases.filter { platform in
                draft.filter.platforms.contains(platform) && !inCollection.contains { $0.platform == platform }
            }
            if !inCollection.isEmpty || !extraSelected.isEmpty {
                FlowLayout {
                    ForEach(inCollection, id: \.platform) { entry in
                        platformChip(entry.platform, count: entry.count)
                    }
                    ForEach(extraSelected, id: \.self) { platform in
                        platformChip(platform, count: nil)
                    }
                }
                .padding(.vertical, 4)
            }
            NavigationLink {
                PlatformMultiSelectView(selection: $draft.filter.platforms, facets: facets)
            } label: {
                LabeledContent("All platforms") {
                    if !draft.filter.platforms.isEmpty {
                        Text("\(draft.filter.platforms.count) selected")
                    }
                }
            }
        } header: {
            Text("Platforms")
        } footer: {
            if !facets.platforms.isEmpty {
                Text("Platforms in your collection are shown first.")
            }
        }
    }

    private func platformChip(_ platform: Platform, count: Int?) -> some View {
        SelectableChip(title: platform.label, count: count, isSelected: draft.filter.platforms.contains(platform)) {
            draft.filter.platforms.toggleMembership(of: platform)
        }
    }

    @ViewBuilder
    private var genreSection: some View {
        if !facets.genres.isEmpty || !draft.filter.genres.isEmpty {
            Section("Genre") {
                let known = Set(facets.genres.map(\.value))
                FlowLayout {
                    ForEach(facets.genres, id: \.value) { genre in
                        SelectableChip(title: genre.value, count: genre.count, isSelected: draft.filter.genres.contains(genre.value)) {
                            draft.filter.genres.toggleMembership(of: genre.value)
                        }
                    }
                    ForEach(draft.filter.genres.subtracting(known).sorted(), id: \.self) { genre in
                        SelectableChip(title: genre, isSelected: true) {
                            draft.filter.genres.toggleMembership(of: genre)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    private var textSection: some View {
        Section {
            SuggestingTextField(title: "Publisher", text: $draft.filter.publisher, suggestions: facets.publishers.map(\.value), prompt: "Any")
            SuggestingTextField(title: "Developer", text: $draft.filter.developer, suggestions: facets.developers.map(\.value), prompt: "Any")
            SuggestingTextField(title: "Storage location", text: $draft.filter.storageLocation, suggestions: facets.storageLocations.map(\.value), prompt: "Any")
        } header: {
            Text("Publisher, developer & storage location")
        } footer: {
            Text("Partial matches are enough.")
        }
    }

    private var flagsSection: some View {
        Section {
            Toggle("Favorites only", isOn: $draft.filter.favoritesOnly)
            Picker("Cover", selection: $draft.filter.cover) {
                ForEach(GameFilter.CoverFilter.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .accessibilityLabel("Cover")
        }
    }

    @ViewBuilder
    private var rangeSections: some View {
        let errors = draft.errors
        Section {
            RangeInputRow(
                from: $draft.releaseYearFrom,
                to: $draft.releaseYearTo,
                fromPrompt: facets.releaseYearMin.map(String.init) ?? "1950",
                toPrompt: facets.releaseYearMax.map(String.init) ?? "2100",
                keyboard: .numberPad
            )
            FieldError(message: errors[.releaseYear])
        } header: {
            Text("Release year")
        }

        Section {
            RangeInputRow(from: $draft.purchasePriceMin, to: $draft.purchasePriceMax, keyboard: .decimalPad)
            FieldError(message: errors[.purchasePrice])
        } header: {
            Text("Purchase price")
        }

        Section {
            RangeInputRow(from: $draft.estimatedValueMin, to: $draft.estimatedValueMax, keyboard: .decimalPad)
            FieldError(message: errors[.estimatedValue])
        } header: {
            Text("Estimated value")
        }

        Section {
            OptionalDatePicker(title: "From", date: $draft.purchaseDateFrom)
            OptionalDatePicker(title: "To", date: $draft.purchaseDateTo)
            FieldError(message: errors[.purchaseDate])
        } header: {
            Text("Purchase date")
        }

        Section {
            Picker("Minimum rating", selection: $draft.filter.ratingMin) {
                Text("Any").tag(Int?.none)
                ForEach(Validation.ratingRange, id: \.self) { rating in
                    Text("\(rating)/10").tag(Int?.some(rating))
                }
            }
        }
    }
}

// MARK: - Pieces

/// Multi-select chips for one enum.
private struct ChipSelectionSection<Value: APIEnum>: View {
    let title: String
    let values: [Value]
    @Binding var selection: Set<Value>
    var count: (Value) -> Int? = { _ in nil }

    var body: some View {
        Section(title) {
            FlowLayout {
                ForEach(values, id: \.self) { value in
                    SelectableChip(title: value.label, count: count(value), isSelected: selection.contains(value)) {
                        selection.toggleMembership(of: value)
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }
}

private struct RangeInputRow: View {
    @Binding var from: String
    @Binding var to: String
    var fromPrompt = "from"
    var toPrompt = "to"
    let keyboard: UIKeyboardType

    var body: some View {
        HStack(spacing: 12) {
            field("From", text: $from, prompt: fromPrompt)
            Text("–")
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            field("To", text: $to, prompt: toPrompt)
        }
    }

    private func field(_ label: String, text: Binding<String>, prompt: String) -> some View {
        TextField(label, text: text, prompt: Text(prompt))
            .keyboardType(keyboard)
            .multilineTextAlignment(.center)
            .padding(.vertical, 6)
            .background(.fill.tertiary, in: .rect(cornerRadius: 8))
            .accessibilityLabel(label)
    }
}

extension Set {
    mutating func toggleMembership(of element: Element) {
        if contains(element) {
            remove(element)
        } else {
            insert(element)
        }
    }
}

#if DEBUG
#Preview {
    var filter = GameFilter()
    filter.platforms = [.ps2]
    filter.genres = ["RPG"]
    return Color.clear
        .sheet(isPresented: .constant(true)) {
            FiltersView(filter: filter) { _ in }
        }
        .environment(FacetsStore(repository: SyncEngine.preview().repository))
}
#endif
