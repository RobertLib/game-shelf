import SwiftUI

/// "Add game" / "Edit game", presented as a sheet.
struct GameFormView: View {
    @State private var model: GameFormViewModel
    @State private var isConfirmingDiscard = false
    @FocusState private var focusedField: GameDraft.Field?
    @Environment(FacetsStore.self) private var facets
    @Environment(\.dismiss) private var dismiss

    private static let currencySuggestions = ["CZK", "EUR", "USD", "GBP", "JPY", "PLN"]

    init(mode: GameFormMode, repository: GameRepository) {
        _model = State(initialValue: GameFormViewModel(mode: mode, repository: repository))
    }

    var body: some View {
        NavigationStack {
            Form {
                basicSection
                collectorSection
                purchaseSection
                playSection
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(model.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbar }
        }
        .interactiveDismissDisabled(model.hasChanges)
        .confirmationDialog("Discard changes?", isPresented: $isConfirmingDiscard, titleVisibility: .visible) {
            Button("Discard changes", role: .destructive) { dismiss() }
            Button("Keep editing", role: .cancel) {}
        } message: {
            Text("Your changes will not be saved.")
        }
        .alert(
            "Couldn't save the game",
            isPresented: Binding(get: { model.saveError != nil }, set: { if !$0 { model.saveError = nil } }),
            actions: { Button("OK", role: .cancel) {} },
            message: { Text(model.saveError ?? "") }
        )
    }

    private var errors: [GameDraft.Field: String] { model.errors }

    // MARK: Sections

    private var basicSection: some View {
        Section("Basics") {
            ValidatedRow(error: errors[.title]) {
                TextField("Title (required)", text: $model.draft.title)
                    .font(.headline)
                    .focused($focusedField, equals: .title)
                    .accessibilityLabel("Title, required")
                    .accessibilityIdentifier("gameForm.title")
            }

            NavigationLink {
                PlatformPickerView(
                    selection: $model.draft.platform,
                    collectionPlatforms: facets.facets.platformCounts.map(\.platform)
                )
            } label: {
                ValidatedRow(error: errors[.platform]) {
                    LabeledContent("Platform (required)") {
                        Text(model.draft.platform?.label ?? "Choose")
                            .foregroundStyle(model.draft.platform == nil ? .tertiary : .secondary)
                    }
                }
            }

            ValidatedRow(error: errors[.edition]) {
                LabeledTextField(title: "Edition", text: $model.draft.edition, prompt: "e.g. Collector's Edition")
            }
            ValidatedRow(error: errors[.genre]) {
                SuggestingTextField(title: "Genre", text: $model.draft.genre, suggestions: facets.genres, prompt: "e.g. RPG")
            }
            ValidatedRow(error: errors[.developer]) {
                SuggestingTextField(title: "Developer", text: $model.draft.developer, suggestions: facets.developers, prompt: "Studio")
            }
            ValidatedRow(error: errors[.publisher]) {
                SuggestingTextField(title: "Publisher", text: $model.draft.publisher, suggestions: facets.publishers, prompt: "Company")
            }
            ValidatedRow(error: errors[.releaseYear]) {
                LabeledContent("Release year") {
                    TextField("e.g. 1998", text: $model.draft.releaseYear)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                        .focused($focusedField, equals: .releaseYear)
                }
            }
            ValidatedRow(error: errors[.coverImageUrl]) {
                HStack {
                    TextField("Cover image URL", text: $model.draft.coverImageUrl, prompt: Text("Cover image URL (https://…)"))
                        .keyboardType(.URL)
                        .textContentType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    if let coverPreviewURL {
                        CoverImageView(url: coverPreviewURL, platform: model.draft.platform ?? .other, cornerRadius: 4)
                            .frame(width: 30, height: 42)
                    }
                }
            }
        }
    }

    private var collectorSection: some View {
        Section("Collector details") {
            Picker("Status", selection: $model.draft.status) {
                ForEach(CollectionStatus.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            Picker("Format", selection: $model.draft.format) {
                ForEach(GameFormat.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            OptionalEnumPicker(title: "Region", selection: $model.draft.region)
            OptionalEnumPicker(title: "Completeness", selection: $model.draft.completeness)
            OptionalEnumPicker(title: "Condition", selection: $model.draft.condition)

            ValidatedRow(error: errors[.barcode]) {
                LabeledContent("Barcode (EAN/UPC)") {
                    TextField("8–14 digits", text: $model.draft.barcode)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                        .focused($focusedField, equals: .barcode)
                }
            }
            ValidatedRow(error: errors[.productCode]) {
                LabeledContent("Product code") {
                    TextField("e.g. NUS-NZLP-EUR", text: $model.draft.productCode)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .multilineTextAlignment(.trailing)
                }
            }
            Stepper(value: $model.draft.quantity, in: Validation.quantityRange) {
                LabeledContent("Quantity", value: AppFormat.integer(model.draft.quantity))
            }
            ValidatedRow(error: errors[.storageLocation]) {
                SuggestingTextField(title: "Storage location", text: $model.draft.storageLocation, suggestions: facets.storageLocations, prompt: "e.g. Shelf A")
            }
        }
    }

    private var purchaseSection: some View {
        Section("Purchase & value") {
            ValidatedRow(error: errors[.purchasePrice]) {
                priceField("Purchase price", text: $model.draft.purchasePrice, field: .purchasePrice)
            }
            ValidatedRow(error: errors[.estimatedValue]) {
                priceField("Estimated value", text: $model.draft.estimatedValue, field: .estimatedValue)
            }
            ValidatedRow(error: errors[.currency]) {
                SuggestingTextField(title: "Currency", text: $model.draft.currency, suggestions: Self.currencySuggestions, prompt: "CZK")
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
            }
            OptionalDatePicker(title: "Purchase date", date: $model.draft.purchaseDate)
            ValidatedRow(error: errors[.purchasePlace]) {
                LabeledTextField(title: "Purchased from", text: $model.draft.purchasePlace, prompt: "Shop, flea market…")
            }
        }
    }

    private var playSection: some View {
        Section("Rating & play") {
            OptionalEnumPicker(title: "Play status", selection: $model.draft.playStatus)
            Picker("Rating", selection: $model.draft.rating) {
                Text("No rating").tag(Int?.none)
                ForEach(Validation.ratingRange.reversed(), id: \.self) { rating in
                    Text("\(rating)/10").tag(Int?.some(rating))
                }
            }
            Toggle("Favorite", isOn: $model.draft.favorite)
            ValidatedRow(error: errors[.notes]) {
                Text("Notes")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
                TextField("Notes", text: $model.draft.notes, prompt: Text("Anything else about the game…"), axis: .vertical)
                    .lineLimit(3...10)
            }
        }
    }

    private var coverPreviewURL: URL? {
        guard !model.draft.coverImageUrl.isBlank, errors[.coverImageUrl] == nil else { return nil }
        return URL(string: model.draft.coverImageUrl.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    private func priceField(_ title: String, text: Binding<String>, field: GameDraft.Field) -> some View {
        LabeledContent(title) {
            HStack(spacing: 4) {
                TextField("0", text: text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .monospacedDigit()
                    .focused($focusedField, equals: field)
                    .accessibilityIdentifier("gameForm.\(field)")
                Text(model.draft.currency.isBlank ? "–" : model.draft.currency.uppercased())
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
            }
        }
    }

    // MARK: Toolbar

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") {
                if model.hasChanges {
                    isConfirmingDiscard = true
                } else {
                    dismiss()
                }
            }
        }
        ToolbarItem(placement: .confirmationAction) {
            Button("Save") {
                focusedField = nil
                Task {
                    if await model.save() {
                        dismiss()
                    }
                }
            }
            .fontWeight(.semibold)
            .disabled(model.isSaving)
        }
        ToolbarItemGroup(placement: .keyboard) {
            Spacer()
            Button("Done") { focusedField = nil }
        }
    }
}

// MARK: - Pieces

/// Picker for an optional enum with a "not specified" option.
private struct OptionalEnumPicker<Value: APIEnum>: View {
    let title: String
    @Binding var selection: Value?

    var body: some View {
        Picker(title, selection: $selection) {
            Text("Not specified").tag(Value?.none)
            ForEach(Value.allCases, id: \.self) { value in
                Text(value.label).tag(Value?.some(value))
            }
        }
    }
}

/// A form row with its inline validation message underneath.
private struct ValidatedRow<Content: View>: View {
    let error: String?
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            content
            FieldError(message: error)
        }
    }
}

#if DEBUG
#Preview("Add game") {
    let sync = SyncEngine.preview()
    GameFormView(mode: .create, repository: sync.repository)
        .environment(FacetsStore(repository: sync.repository))
}

#Preview("Edit game") {
    let sync = SyncEngine.preview()
    GameFormView(mode: .edit(PreviewData.games[0]), repository: sync.repository)
        .environment(FacetsStore(repository: sync.repository))
}
#endif
