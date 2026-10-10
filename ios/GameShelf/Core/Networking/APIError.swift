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
    case syncResetRequired = "SYNC_RESET_REQUIRED"
    case barcodeNotFound = "BARCODE_NOT_FOUND"
    case lookupUnavailable = "LOOKUP_UNAVAILABLE"
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
///
/// A body is an *API error body* when it has a string `code` – also one this app version doesn't
/// know (docs/offline-sync.md); the other fields are not needed to recognize it.
struct ErrorResponse: Decodable, Hashable, Sendable {
    var statusCode: Int?
    var code: APIErrorCode
    var message: String?
    var details: [String]?

    private enum CodingKeys: String, CodingKey {
        case statusCode, code, message, details
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        code = try container.decode(APIErrorCode.self, forKey: .code)
        statusCode = try? container.decodeIfPresent(Int.self, forKey: .statusCode)
        message = try? container.decodeIfPresent(String.self, forKey: .message)
        details = try? container.decodeIfPresent([String].self, forKey: .details)
    }
}

enum APIError: Error, Hashable, Sendable {
    /// The API answered with a non-2xx status and an API error body (``ErrorResponse``).
    case server(statusCode: Int, code: APIErrorCode, details: [String])
    /// A non-2xx response without an API error body – an HTML page from a proxy, a `403` from a
    /// firewall, a misrouted request. It did not come from the Game Shelf API, so it says nothing
    /// about the request.
    case http(statusCode: Int)
    /// The server could not be reached (offline, timeout, DNS, TLS, …).
    case network(URLError.Code)
    /// The response could not be decoded.
    case invalidResponse
    /// The session could not be renewed; the user has to sign in again.
    case sessionExpired

    var statusCode: Int? {
        switch self {
        case .server(let statusCode, _, _), .http(let statusCode): statusCode
        case .network, .invalidResponse, .sessionExpired: nil
        }
    }

    var code: APIErrorCode? {
        if case .server(_, let code, _) = self { code } else { nil }
    }

    var isUnauthorized: Bool { statusCode == 401 }
}
