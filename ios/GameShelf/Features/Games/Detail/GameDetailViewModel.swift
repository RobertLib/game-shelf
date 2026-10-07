import Foundation
import Observation

@Observable
@MainActor
final class GameDetailViewModel {
    private(set) var game: Game
    private(set) var isMissing = false
    private(set) var isUpdatingFavorite = false
    private(set) var isDeleting = false
    var errorMessage: String?

    @ObservationIgnored private let service: any GameService
    @ObservationIgnored private let onChange: @MainActor (GameChange) -> Void

    init(game: Game, service: any GameService, onChange: @escaping @MainActor (GameChange) -> Void) {
        self.game = game
        self.service = service
        self.onChange = onChange
    }

    /// Reloads the game. Background refreshes fail silently because the list's copy is still shown.
    func refresh(userInitiated: Bool) async {
        do {
            let fresh = try await service.game(id: game.id)
            if fresh != game {
                game = fresh
                onChange(.updated(fresh))
            }
        } catch let error as APIError where error.code == .gameNotFound {
            isMissing = true
            onChange(.deleted(game.id))
        } catch {
            if userInitiated, !ErrorMessage.isCancellation(error) {
                errorMessage = ErrorMessage.message(for: error)
            }
        }
    }

    /// Optimistically flips the favorite flag; `PUT` needs the full object.
    func toggleFavorite() async {
        guard !isUpdatingFavorite else { return }
        let previous = game
        var request = SaveGameRequest(game: game)
        request.favorite.toggle()
        game.favorite = request.favorite
        isUpdatingFavorite = true
        defer { isUpdatingFavorite = false }
        do {
            game = try await service.update(id: game.id, with: request)
            onChange(.updated(game))
        } catch {
            game = previous
            if !ErrorMessage.isCancellation(error) {
                errorMessage = ErrorMessage.message(for: error)
            }
        }
    }

    /// Returns `true` when the game is gone and the screen should close.
    func delete() async -> Bool {
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await service.delete(id: game.id)
        } catch let error as APIError where error.code == .gameNotFound {
            // Already deleted elsewhere – same outcome.
        } catch {
            if !ErrorMessage.isCancellation(error) {
                errorMessage = ErrorMessage.message(for: error)
            }
            return false
        }
        onChange(.deleted(game.id))
        return true
    }

    func didSave(_ game: Game) {
        self.game = game
        onChange(.updated(game))
    }
}
