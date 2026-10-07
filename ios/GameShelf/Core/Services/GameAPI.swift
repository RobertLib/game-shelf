import Foundation

/// The game endpoints used by the sync engine. Screens never call the API for games; they read
/// and change the local collection (``GameRepository``).
protocol GameAPI: Sendable {
    /// `GET games/changes` – one page of the change feed; `nil` starts from the beginning.
    func changes(after cursor: String?, limit: Int) async throws -> GameChanges
    /// `POST games` with the id generated on the device.
    func create(id: Game.ID, _ values: SaveGameRequest) async throws -> CreateGameResult
    /// `PATCH games/{id}` with only `fields`.
    func update(id: Game.ID, fields: Set<GameField>, from values: SaveGameRequest) async throws -> Game
    /// `DELETE games/{id}`.
    func delete(id: Game.ID) async throws
    /// `GET games/{id}`.
    func game(id: Game.ID) async throws -> Game
}

enum CreateGameResult: Hashable, Sendable {
    /// `201`: the game was created with the values that were sent.
    case created(Game)
    /// `200`: a game with this id already existed and was left unchanged.
    case alreadyExisted(Game)
}

struct RemoteGameAPI: GameAPI {
    let api: APIClient

    func changes(after cursor: String?, limit: Int) async throws -> GameChanges {
        try await api.send(.gameChanges(cursor: cursor, limit: limit))
    }

    func create(id: Game.ID, _ values: SaveGameRequest) async throws -> CreateGameResult {
        let response = try await api.response(for: .createGame(CreateGameRequest(id: id, values: values)))
        return response.statusCode == 201 ? .created(response.value) : .alreadyExisted(response.value)
    }

    func update(id: Game.ID, fields: Set<GameField>, from values: SaveGameRequest) async throws -> Game {
        try await api.send(.updateGame(id: id, UpdateGameRequest(values: values, fields: fields)))
    }

    func delete(id: Game.ID) async throws {
        _ = try await api.send(.deleteGame(id: id))
    }

    func game(id: Game.ID) async throws -> Game {
        try await api.send(.game(id: id))
    }
}
