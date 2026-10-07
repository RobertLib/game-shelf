#if DEBUG
import Foundation

/// Game API for previews: the first pull returns `games`; every change is accepted.
struct PreviewGameAPI: GameAPI {
    var games: [Game] = PreviewData.games

    func changes(after cursor: String?, limit: Int) async throws -> GameChanges {
        GameChanges(games: cursor == nil ? games : [], deletedIds: [], cursor: "1", hasMore: false)
    }

    func create(id: Game.ID, _ values: SaveGameRequest) async throws -> CreateGameResult {
        .created(Game(id: id, values: values, createdAt: .now))
    }

    func update(id: Game.ID, fields: Set<GameField>, from values: SaveGameRequest) async throws -> Game {
        Game(id: id, values: values, createdAt: .now)
    }

    func delete(id: Game.ID) async throws {}

    func game(id: Game.ID) async throws -> Game {
        guard let game = games.first(where: { $0.id == id }) else {
            throw APIError.server(statusCode: 404, code: .gameNotFound, details: [])
        }
        return game
    }
}

extension SyncEngine {
    /// An engine signed in as the preview user whose collection syncs `games` from ``PreviewGameAPI``.
    @MainActor
    static func preview(games: [Game] = PreviewData.games) -> SyncEngine {
        guard let store = try? GameStore(url: nil) else {
            preconditionFailure("SQLite can't open an in-memory database.")
        }
        let engine = SyncEngine(
            repository: GameRepository(store: store, status: SyncStatus()),
            api: PreviewGameAPI(games: games)
        )
        Task {
            await engine.activate(ownerID: PreviewData.user.id)
        }
        return engine
    }
}

struct PreviewAuthService: AuthService {
    func login(email: String, password: String) async throws -> AuthResponse {
        try await Task.sleep(for: .milliseconds(600))
        throw APIError.server(statusCode: 401, code: .invalidCredentials, details: [])
    }

    func register(email: String, password: String, displayName: String?) async throws -> AuthResponse {
        try await Task.sleep(for: .milliseconds(600))
        throw APIError.server(statusCode: 409, code: .emailAlreadyRegistered, details: [])
    }

    func currentUser() async throws -> User {
        PreviewData.user
    }

    func changePassword(currentPassword: String, newPassword: String) async throws -> AuthResponse {
        throw APIError.server(statusCode: 400, code: .invalidCurrentPassword, details: [])
    }

    func logout(refreshToken: String) async throws {}

    func deleteAccount(password: String) async throws {
        throw APIError.server(statusCode: 400, code: .invalidCurrentPassword, details: [])
    }
}
#endif
