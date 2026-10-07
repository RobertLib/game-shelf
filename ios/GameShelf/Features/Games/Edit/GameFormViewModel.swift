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
    @ObservationIgnored private let service: any GameService

    init(mode: GameFormMode, service: any GameService) {
        self.mode = mode
        self.service = service
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

    /// Saves the draft; returns the stored game on success.
    func save() async -> Game? {
        hasAttemptedSave = true
        guard !isSaving, let request = draft.makeRequest() else { return nil }
        isSaving = true
        defer { isSaving = false }
        do {
            switch mode {
            case .create:
                return try await service.create(request)
            case .edit(let game):
                return try await service.update(id: game.id, with: request)
            }
        } catch {
            if !ErrorMessage.isCancellation(error) {
                saveError = ErrorMessage.message(for: error)
            }
            return nil
        }
    }
}
