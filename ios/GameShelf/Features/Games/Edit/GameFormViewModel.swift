import Foundation
import Observation

enum GameFormMode: Hashable {
    case create
    case edit(Game)
}

@Observable
@MainActor
final class GameFormViewModel {
    let mode: GameFormMode
    var draft: GameDraft
    var saveError: String?
    private(set) var isSaving = false
    private(set) var hasAttemptedSave = false

    @ObservationIgnored private let initialDraft: GameDraft
    @ObservationIgnored private let repository: GameRepository

    init(mode: GameFormMode, repository: GameRepository) {
        self.mode = mode
        self.repository = repository
        let draft = switch mode {
        case .create: GameDraft()
        case .edit(let game): GameDraft(game: game)
        }
        self.draft = draft
        initialDraft = draft
    }

    var title: String {
        switch mode {
        case .create: "Add game"
        case .edit: "Edit game"
        }
    }

    var hasChanges: Bool { draft != initialDraft }

    var errors: [GameDraft.Field: String] {
        draft.errors(includingRequired: hasAttemptedSave)
    }

    /// Saves the draft on the device (it works offline; the sync engine pushes it).
    /// Returns `true` when the form can close.
    func save() async -> Bool {
        hasAttemptedSave = true
        guard !isSaving, let request = draft.makeRequest() else { return false }
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
