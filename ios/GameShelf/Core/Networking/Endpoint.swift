import Foundation

enum HTTPMethod: String, Sendable {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case delete = "DELETE"
}

/// Response type of endpoints that return `204 No Content`.
struct EmptyResponse: Decodable, Sendable {}

/// Description of one API call and its decoded response type.
struct Endpoint<Response: Decodable & Sendable>: Sendable {
    var method: HTTPMethod
    /// Path relative to the API base URL, without a leading slash.
    var path: String
    var queryItems: [URLQueryItem] = []
    var body: (any Encodable & Sendable)?
    /// Authenticated calls send the bearer token and transparently refresh it on `401`.
    var requiresAuthentication = true
}

// MARK: - Auth

extension Endpoint where Response == AuthResponse {
    static func login(_ request: LoginRequest) -> Self {
        Endpoint(method: .post, path: "auth/login", body: request, requiresAuthentication: false)
    }

    static func register(_ request: RegisterRequest) -> Self {
        Endpoint(method: .post, path: "auth/register", body: request, requiresAuthentication: false)
    }

    static func refresh(_ request: RefreshTokenRequest) -> Self {
        Endpoint(method: .post, path: "auth/refresh", body: request, requiresAuthentication: false)
    }

    static func changePassword(_ request: ChangePasswordRequest) -> Self {
        Endpoint(method: .post, path: "auth/change-password", body: request)
    }
}

extension Endpoint where Response == User {
    static var currentUser: Self {
        Endpoint(method: .get, path: "auth/me")
    }
}

extension Endpoint where Response == EmptyResponse {
    static func logout(_ request: RefreshTokenRequest) -> Self {
        Endpoint(method: .post, path: "auth/logout", body: request, requiresAuthentication: false)
    }

    static func deleteAccount(_ request: DeleteAccountRequest) -> Self {
        Endpoint(method: .delete, path: "auth/me", body: request)
    }

    static func deleteGame(id: Game.ID) -> Self {
        Endpoint(method: .delete, path: "games/\(id.urlPathComponent)")
    }
}

// MARK: - Games

extension Endpoint where Response == GamePage {
    static func games(_ query: GameListQuery, page: Int, pageSize: Int = GameListQuery.defaultPageSize) -> Self {
        Endpoint(method: .get, path: "games", queryItems: query.queryItems(page: page, pageSize: pageSize))
    }
}

extension Endpoint where Response == GameFacets {
    static var facets: Self {
        Endpoint(method: .get, path: "games/facets")
    }
}

extension Endpoint where Response == Game {
    static func game(id: Game.ID) -> Self {
        Endpoint(method: .get, path: "games/\(id.urlPathComponent)")
    }

    static func createGame(_ request: SaveGameRequest) -> Self {
        Endpoint(method: .post, path: "games", body: request)
    }

    static func updateGame(id: Game.ID, _ request: SaveGameRequest) -> Self {
        Endpoint(method: .put, path: "games/\(id.urlPathComponent)", body: request)
    }
}

private extension String {
    var urlPathComponent: String {
        addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/"))) ?? self
    }
}
