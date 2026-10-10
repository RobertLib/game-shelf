import Foundation

/// `ErrorCode` from the API contract.
enum APIErrorCode: String, Codable, Sendable {
    case validationFailed = "VALIDATION_FAILED"
    case unauthorized = "UNAUTHORIZED"
    case invalidCredentials = "INVALID_CREDENTIALS"
    case invalidRefreshToken = "INVALID_REFRESH_TOKEN"
    case invalidCurrentPassword = "INVALID_CURRENT_PASSWORD"
    case emailAlreadyRegistered = "EMAIL_ALREADY_REGISTERED"
    /// `401` to any access token of a deleted account, expired or not: never refreshed, the app signs out
    /// and wipes the local data (docs/offline-sync.md, "Local data").
    case accountDeleted = "ACCOUNT_DELETED"
    case gameNotFound = "GAME_NOT_FOUND"
    /// `PATCH`, `PUT` or `GET` of a game the server has no record of at all (restored from an older
    /// backup); handled by the sync engine, never shown.
    case gameUnknown = "GAME_UNKNOWN"
    case syncResetRequired = "SYNC_RESET_REQUIRED"
    case barcodeNotFound = "BARCODE_NOT_FOUND"
    case lookupUnavailable = "LOOKUP_UNAVAILABLE"
    /// `429`: the user has used up today's barcode lookups (until midnight UTC).
    case lookupQuotaExceeded = "LOOKUP_QUOTA_EXCEEDED"
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
    /// A `2xx` response whose body this app version can't decode – e.g. a game with a value a newer API
    /// sends in a form this version can't read, or a captive portal's page. For a pushed change it is a
    /// failure of that change only (docs/offline-sync.md, "Writes").
    case undecodable(statusCode: Int)
    /// The request couldn't be made, or the answer wasn't an HTTP response at all.
    case invalidResponse
    /// The session could not be renewed; the user has to sign in again.
    case sessionExpired

    var statusCode: Int? {
        switch self {
        case .server(let statusCode, _, _), .http(let statusCode), .undecodable(let statusCode): statusCode
        case .network, .invalidResponse, .sessionExpired: nil
        }
    }

    var code: APIErrorCode? {
        if case .server(_, let code, _) = self { code } else { nil }
    }

    /// `401` from the API, which the token refresh handles. A `401` without an API error body did not come
    /// from the API: it is an error of that request, never a reason to refresh or end the session.
    var isUnauthorized: Bool {
        if case .server(401, _, _) = self { true } else { false }
    }

    /// `401 ACCOUNT_DELETED`: the account was deleted (e.g. on another device). Never refreshed; the app
    /// signs out and wipes the local data like a sign-out.
    var isAccountDeleted: Bool {
        if case .server(401, .accountDeleted, _) = self { true } else { false }
    }

    /// The session is over: it couldn't be renewed, or the account was deleted.
    var isEndOfSession: Bool {
        self == .sessionExpired || isAccountDeleted
    }
}
