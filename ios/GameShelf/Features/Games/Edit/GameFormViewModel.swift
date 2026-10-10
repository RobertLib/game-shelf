import Foundation
import Observation

enum GameFormMode: Hashable {
    case create
    case edit(Game)
}

/// Progress and outcome of filling the form from the game database: looking up a scanned barcode,
/// or (always `.found`) a game picked in the database search.
enum BarcodeLookupState: Equatable {
    case loading
    case found(sources: [String])
    case notFound
    case failed(String)
}

/// The add / edit form. Saving is local and works offline. A scanned barcode is looked up in the game
/// databases behind the API, which needs a connection; what they know fills the fields still empty.
/// A game picked in the database search (``GameSearchView``) replaces what the fields hold.
@Observable
@MainActor
final class GameFormViewModel {
    let mode: GameFormMode
    var draft: GameDraft
    var saveError: String?
    private(set) var isSaving = false
    private(set) var hasAttemptedSave = false
    private(set) var lookupState: BarcodeLookupState?
    /// Another game in the collection with the scanned barcode, or with the title and platform of the
    /// game picked in the database search.
    private(set) var duplicate: Game?

    @ObservationIgnored private let initialDraft: GameDraft
    @ObservationIgnored private let repository: GameRepository
    /// Scanned before the form opened; looked up once the form appears.
    @ObservationIgnored private var initialBarcode: String?
    /// Identifies the latest lookup, so that an older one finishing late changes nothing.
    @ObservationIgnored private var lookupGeneration = 0

    init(mode: GameFormMode, repository: GameRepository, scannedBarcode: String? = nil) {
        self.mode = mode
        self.repository = repository
        let draft = switch mode {
        case .create: GameDraft()
        case .edit(let game): GameDraft(game: game)
        }
        self.draft = draft
        initialDraft = draft
        initialBarcode = scannedBarcode
    }

    var title: String {
        switch mode {
        case .create: "Add game"
        case .edit: "Edit game"
        }
    }

    var hasChanges: Bool { draft != initialDraft }

    /// Values the form started with are not validated: they came from the server.
    var errors: [GameDraft.Field: String] {
        draft.errors(includingRequired: hasAttemptedSave, initial: initialDraft)
    }

    /// Looks up the barcode the form was opened with, the first time only.
    @discardableResult
    func startInitialLookup(using service: any BarcodeLookupService) -> Task<Void, Never>? {
        guard let barcode = initialBarcode else { return nil }
        initialBarcode = nil
        // Not tied to the view: navigating to the platform picker must not cancel it.
        return Task { await scanned(barcode, using: service) }
    }

    /// A barcode from the camera: fills it in and looks the game up.
    func scanned(_ code: String, using service: any BarcodeLookupService) async {
        let barcode = Barcode.normalized(code)
        draft.barcode = barcode
        guard Barcode.isValid(barcode) else {
            // A code typed by hand in the scanner; the field shows what is wrong with it.
            dismissLookup()
            return
        }
        await lookUp(barcode, using: service)
    }

    func retryLookup(using service: any BarcodeLookupService) async {
        let barcode = Barcode.normalized(draft.barcode)
        guard Barcode.isValid(barcode) else { return }
        await lookUp(barcode, using: service)
    }

    func dismissLookup() {
        lookupGeneration += 1
        lookupState = nil
        duplicate = nil
    }

    private func lookUp(_ barcode: String, using service: any BarcodeLookupService) async {
        lookupGeneration += 1
        let generation = lookupGeneration
        lookupState = .loading
        duplicate = repository.games.first { game in
            game.id != editedGameID && game.barcode.map { Barcode.sameProduct($0, barcode) } == true
        }

        let state: BarcodeLookupState
        do {
            let result = try await service.lookup(barcode: barcode)
            guard generation == lookupGeneration else { return }
            if let result {
                draft.fill(from: result)
                state = .found(sources: result.sources)
            } else {
                state = .notFound
            }
        } catch {
            guard generation == lookupGeneration else { return }
            state = .failed(ErrorMessage.message(for: error))
        }
        lookupState = state
    }

    /// Fills the form from a game picked in the database search: it replaces the details the database
    /// knows (see ``GameDraft/fill(fromSearch:platform:)``). A barcode lookup still in progress is cancelled.
    func fill(fromSearch pick: GameSearchPick) {
        lookupGeneration += 1
        draft.fill(fromSearch: pick.game, platform: pick.platform)
        lookupState = .found(sources: pick.sources)
        let platform = draft.platform
        let title = draft.title.trimmingCharacters(in: .whitespacesAndNewlines)
        duplicate = repository.games.first { game in
            game.id != editedGameID && game.platform == platform
                && game.title.trimmingCharacters(in: .whitespacesAndNewlines).caseInsensitiveCompare(title) == .orderedSame
        }
    }

    private var editedGameID: Game.ID? {
        if case .edit(let game) = mode { game.id } else { nil }
    }

    /// Saves the draft on the device (it works offline; the sync engine pushes it).
    /// Returns `true` when the form can close.
    func save() async -> Bool {
        hasAttemptedSave = true
        guard !isSaving, let request = draft.makeRequest(initial: initialDraft) else { return false }
        isSaving = true
        defer { isSaving = false }
        do {
            switch mode {
            case .create:
                try await repository.create(request)
            case .edit(let game):
                // Only the fields changed in this form are saved, on top of the current stored game.
                try await repository.update(id: game.id, with: request, basedOn: game)
            }
            return true
        } catch {
            saveError = ErrorMessage.message(for: error)
            return false
        }
    }
}
