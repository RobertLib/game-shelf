import Foundation

protocol GameService: Sendable {
    func games(matching query: GameListQuery, page: Int) async throws -> GamePage
    func facets() async throws -> GameFacets
    func game(id: Game.ID) async throws -> Game
    func create(_ request: SaveGameRequest) async throws -> Game
    func update(id: Game.ID, with request: SaveGameRequest) async throws -> Game
    func delete(id: Game.ID) async throws
}

struct RemoteGameService: GameService {
    let api: APIClient

    func games(matching query: GameListQuery, page: Int) async throws -> GamePage {
        try await api.send(.games(query, page: page))
    }

    func facets() async throws -> GameFacets {
        try await api.send(.facets)
    }

    func game(id: Game.ID) async throws -> Game {
        try await api.send(.game(id: id))
    }

    func create(_ request: SaveGameRequest) async throws -> Game {
        try await api.send(.createGame(request))
    }

    func update(id: Game.ID, with request: SaveGameRequest) async throws -> Game {
        try await api.send(.updateGame(id: id, request))
    }

    func delete(id: Game.ID) async throws {
        _ = try await api.send(.deleteGame(id: id))
    }
}
