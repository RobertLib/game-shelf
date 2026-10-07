import Foundation
import Observation

/// Detail of one game. Follows the stored game, so a sync updates the screen, and a game deleted
/// on another device turns into "Game not found.".
@Observable
@MainActor
final class GameDetailViewModel {
    let gameID: Game.ID
    let repository: GameRepository
    /// The user deleted the game here; the screen is closing.
    private(set) var isDeleted = false
    var errorMessage: String?

    init(gameID: Game.ID, repository: GameRepository) {
        self.gameID = gameID
        self.repository = repository
    }

    var game: Game? {
        repository.game(id: gameID)
    }

    /// Saved locally at once; the sync engine pushes it.
    func toggleFavorite() async {
        guard let game else { return }
        do {
            try await repository.setFavorite(!game.favorite, id: gameID)
        } catch {
            errorMessage = ErrorMessage.message(for: error)
        }
    }

    /// Removes the game at once. Returns `true` when the screen should close.
    func delete() async -> Bool {
        do {
            isDeleted = true
            try await repository.delete(id: gameID)
            return true
        } catch {
            isDeleted = false
            errorMessage = ErrorMessage.message(for: error)
            return false
        }
    }
}
