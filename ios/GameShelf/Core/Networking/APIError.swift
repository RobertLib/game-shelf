import Foundation

/// `ErrorCode` from the API contract.
enum APIErrorCode: String, Codable, Sendable {
    case validationFailed = "VALIDATION_FAILED"
    case unauthorized = "UNAUTHORIZED"
    case invalidCredentials = "INVALID_CREDENTIALS"
    case invalidRefreshToken = "INVALID_REFRESH_TOKEN"
    case invalidCurrentPassword = "INVALID_CURRENT_PASSWORD"
    case emailAlreadyRegistered = "EMAIL_ALREADY_REGISTERED"
    case gameNotFound = "GAME_NOT_FOUND"
    case notFound = "NOT_FOUND"
    case tooManyRequests = "TOO_MANY_REQUESTS"
    case badRequest = "BAD_REQUEST"
    case forbidden = "FORBIDDEN"
    case conflict = "CONFLICT"
    case internalError = "INTERNAL_ERROR"
    case unknown

    init(from decoder: any Decoder) throws {
        let rawValue = try decoder.singleValueContainer().decode(String.self)
        self = APIErrorCode(rawValue: rawValue) ?? .unknown
    }
}

/// `ErrorResponse` from the API contract.
struct ErrorResponse: Codable, Hashable, Sendable {
    var statusCode: Int
    var code: APIErrorCode
    var message: String
    var details: [String]?
}

enum APIError: Error, Hashable, Sendable {
    /// The server answered with a non-2xx status.
    case server(statusCode: Int, code: APIErrorCode, details: [String])
    /// The server could not be reached (offline, timeout, DNS, TLS, …).
    case network(URLError.Code)
    /// The response could not be decoded.
    case invalidResponse
    /// The session could not be renewed; the user has to sign in again.
    case sessionExpired

    var statusCode: Int? {
        if case .server(let statusCode, _, _) = self { statusCode } else { nil }
    }

    var code: APIErrorCode? {
        if case .server(_, let code, _) = self { code } else { nil }
    }

    var isUnauthorized: Bool { statusCode == 401 }
}
