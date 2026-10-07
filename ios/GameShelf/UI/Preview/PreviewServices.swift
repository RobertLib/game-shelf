#if DEBUG
import Foundation

/// In-memory game service for previews; supports simple title search and paging.
struct PreviewGameService: GameService {
    var games: [Game]

    init(games: [Game] = PreviewData.games) {
        self.games = games
    }

    func games(matching query: GameListQuery, page: Int) async throws -> GamePage {
        let matching = games.filter { query.search.isEmpty || $0.title.localizedStandardContains(query.search) }
        let pageSize = GameListQuery.defaultPageSize
        let items = Array(matching.dropFirst((page - 1) * pageSize).prefix(pageSize))
        let totalPages = max(1, Int((Double(matching.count) / Double(pageSize)).rounded(.up)))
        return GamePage(items: items, page: page, pageSize: pageSize, totalItems: matching.count, totalPages: totalPages)
    }

    func facets() async throws -> GameFacets {
        PreviewData.facets
    }

    func game(id: Game.ID) async throws -> Game {
        guard let game = games.first(where: { $0.id == id }) else {
            throw APIError.server(statusCode: 404, code: .gameNotFound, details: [])
        }
        return game
    }

    func create(_ request: SaveGameRequest) async throws -> Game {
        makeGame(id: UUID().uuidString.lowercased(), from: request)
    }

    func update(id: Game.ID, with request: SaveGameRequest) async throws -> Game {
        makeGame(id: id, from: request)
    }

    func delete(id: Game.ID) async throws {}

    private func makeGame(id: Game.ID, from request: SaveGameRequest) -> Game {
        Game(
            id: id, title: request.title, platform: request.platform, status: request.status, format: request.format,
            region: request.region, edition: request.edition, completeness: request.completeness,
            condition: request.condition, playStatus: request.playStatus, genre: request.genre,
            developer: request.developer, publisher: request.publisher, releaseYear: request.releaseYear,
            barcode: request.barcode, productCode: request.productCode, quantity: request.quantity,
            purchasePrice: request.purchasePrice, purchaseDate: request.purchaseDate,
            purchasePlace: request.purchasePlace, estimatedValue: request.estimatedValue,
            currency: request.currency, storageLocation: request.storageLocation, rating: request.rating,
            favorite: request.favorite, coverImageUrl: request.coverImageUrl, notes: request.notes,
            createdAt: .now, updatedAt: .now
        )
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
