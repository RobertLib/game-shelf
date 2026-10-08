import Foundation
import SwiftUI

/// Finds games in the game database by their title. Unlike the collection, it needs a connection.
protocol GameSearchService: Sendable {
    /// `GET lookup/games`; `platform` (the one chosen in the form) lists the games on it first.
    func search(_ query: String, platform: Platform?) async throws -> GameSearchResponse
}

struct RemoteGameSearchService: GameSearchService {
    let api: APIClient

    func search(_ query: String, platform: Platform?) async throws -> GameSearchResponse {
        try await api.send(.gameSearch(query: query, platform: platform))
    }
}

/// Used where no service is provided (previews): every search fails as if offline.
struct OfflineGameSearchService: GameSearchService {
    func search(_ query: String, platform: Platform?) async throws -> GameSearchResponse {
        throw APIError.network(.notConnectedToInternet)
    }
}

private struct GameSearchServiceKey: EnvironmentKey {
    static let defaultValue: any GameSearchService = OfflineGameSearchService()
}

extension EnvironmentValues {
    var gameSearch: any GameSearchService {
        get { self[GameSearchServiceKey.self] }
        set { self[GameSearchServiceKey.self] = newValue }
    }
}
