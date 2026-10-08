import Foundation

enum HTTPMethod: String, Sendable {
    case get = "GET"
    case post = "POST"
    case patch = "PATCH"
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

extension Endpoint where Response == GameChanges {
    /// One page of the change feed; `cursor` is `nil` for the first call.
    static func gameChanges(cursor: String?, limit: Int) -> Self {
        var queryItems = [URLQueryItem(name: "limit", value: String(limit))]
        if let cursor {
            queryItems.insert(URLQueryItem(name: "cursor", value: cursor), at: 0)
        }
        return Endpoint(method: .get, path: "games/changes", queryItems: queryItems)
    }
}

extension Endpoint where Response == Game {
    static func game(id: Game.ID) -> Self {
        Endpoint(method: .get, path: "games/\(id.urlPathComponent)")
    }

    static func createGame(_ request: CreateGameRequest) -> Self {
        Endpoint(method: .post, path: "games", body: request)
    }

    static func updateGame(id: Game.ID, _ request: UpdateGameRequest) -> Self {
        Endpoint(method: .patch, path: "games/\(id.urlPathComponent)", body: request)
    }
}

// MARK: - Lookup

extension Endpoint where Response == BarcodeLookup {
    static func barcodeLookup(_ barcode: String) -> Self {
        Endpoint(method: .get, path: "lookup/barcode/\(barcode.urlPathComponent)")
    }
}

extension Endpoint where Response == GameSearchResponse {
    /// `platform`: the one chosen in the form; the games on it are listed first.
    static func gameSearch(query: String, platform: Platform?) -> Self {
        var queryItems = [URLQueryItem(name: "q", value: query)]
        if let platform {
            queryItems.append(URLQueryItem(name: "platform", value: platform.rawValue))
        }
        return Endpoint(method: .get, path: "lookup/games", queryItems: queryItems)
    }
}

private extension String {
    var urlPathComponent: String {
        addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/"))) ?? self
    }
}
