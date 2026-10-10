import Foundation

/// Maps errors to the user-facing messages from the product spec. Branches on the
/// API `code`, never on the server's `message`.
enum ErrorMessage {
    static let generic = "Something went wrong. Please try again."
    static let network = "Can't connect to the server. Check your connection."
    static let sessionExpired = "Your session has expired. Please sign in again."
    /// The sync engine undid local changes the server rejected permanently.
    static let changesRejected = "Some changes were rejected by the server and have been undone."

    static func message(for error: any Error) -> String {
        switch error {
        case let error as APIError: message(for: error)
        case is URLError: network
        case LocalStoreError.gameNotFound: message(for: .gameNotFound)
        default: generic
        }
    }

    static func message(for error: APIError) -> String {
        switch error {
        case .server(let statusCode, let code, let details):
            message(for: code, statusCode: statusCode, details: details)
        case .http(let statusCode):
            message(for: .unknown, statusCode: statusCode)
        case .network:
            network
        case .sessionExpired:
            sessionExpired
        case .invalidResponse:
            generic
        }
    }

    static func message(for code: APIErrorCode, statusCode: Int = 0, details: [String] = []) -> String {
        switch code {
        case .invalidCredentials:
            "Incorrect email or password."
        case .emailAlreadyRegistered:
            "This email is already registered."
        case .invalidCurrentPassword:
            "Current password is incorrect."
        case .validationFailed:
            (["Please check the entered data."] + details).joined(separator: "\n")
        case .tooManyRequests:
            "Too many attempts. Please try again in a moment."
        case .gameNotFound:
            "Game not found."
        case .lookupUnavailable:
            "The game database isn't available right now. Try again later."
        case .unknown where statusCode == 429:
            "Too many attempts. Please try again in a moment."
        default:
            generic
        }
    }

    /// Cancellation is a normal part of SwiftUI task lifecycles and must never surface as an error.
    static func isCancellation(_ error: any Error) -> Bool {
        if error is CancellationError { return true }
        if let urlError = error as? URLError, urlError.code == .cancelled { return true }
        return false
    }
}
