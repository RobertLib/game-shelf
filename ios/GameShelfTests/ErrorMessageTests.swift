import Foundation
import Testing
@testable import GameShelf

@Suite("Error code → message")
struct ErrorMessageTests {
    private func serverError(_ status: Int, _ code: APIErrorCode, details: [String] = []) -> APIError {
        .server(statusCode: status, code: code, details: details)
    }

    @Test(arguments: [
        (APIErrorCode.invalidCredentials, 401, "Incorrect email or password."),
        (.emailAlreadyRegistered, 409, "This email is already registered."),
        (.invalidCurrentPassword, 400, "Current password is incorrect."),
        (.validationFailed, 400, "Please check the entered data."),
        (.tooManyRequests, 429, "Too many attempts. Please try again in a moment."),
        (.gameNotFound, 404, "Game not found."),
        (.internalError, 500, "Something went wrong. Please try again."),
        (.conflict, 409, "Something went wrong. Please try again."),
        (.unknown, 502, "Something went wrong. Please try again."),
    ])
    func messagesByCode(code: APIErrorCode, status: Int, expected: String) {
        #expect(ErrorMessage.message(for: serverError(status, code)) == expected)
    }

    @Test func validationDetailsAreAppended() {
        let message = ErrorMessage.message(for: serverError(400, .validationFailed, details: ["email must be an email"]))
        #expect(message == "Please check the entered data.\nemail must be an email")
    }

    @Test func rateLimitWithoutKnownCodeUsesStatus() {
        #expect(ErrorMessage.message(for: serverError(429, .unknown)) == "Too many attempts. Please try again in a moment.")
    }

    @Test func networkFailures() {
        let expected = "Can't connect to the server. Check your connection."
        #expect(ErrorMessage.message(for: APIError.network(.timedOut)) == expected)
        #expect(ErrorMessage.message(for: APIError.network(.notConnectedToInternet)) == expected)
        #expect(ErrorMessage.message(for: URLError(.cannotConnectToHost)) == expected)
    }

    @Test func localErrors() {
        #expect(ErrorMessage.message(for: LocalStoreError.gameNotFound) == "Game not found.")
        #expect(ErrorMessage.message(for: LocalStoreError.accessRevoked) == "Something went wrong. Please try again.")
        #expect(ErrorMessage.changesRejected == "Some changes were rejected by the server and have been undone.")
    }

    @Test func otherErrorsAreGeneric() {
        struct Unexpected: Error {}
        #expect(ErrorMessage.message(for: Unexpected()) == "Something went wrong. Please try again.")
        #expect(ErrorMessage.message(for: APIError.invalidResponse) == "Something went wrong. Please try again.")
    }

    @Test func unknownErrorCodeDecodesToFallback() throws {
        let body = try JSONDecoder.api().decode(ErrorResponse.self, from: Fixtures.errorJSON(status: 418, code: "BRAND_NEW_CODE"))
        #expect(body.code == .unknown)
        #expect(body.details == nil)
    }

    @Test @MainActor func deleteAccountReportsAWrongPasswordAsIncorrectPassword() async {
        // The preview auth service rejects every deletion with INVALID_CURRENT_PASSWORD.
        let model = DeleteAccountViewModel()
        model.password = "not-my-password"
        await model.delete(using: PreviewData.sessionStore(signedIn: true))
        #expect(model.errorMessage == "Incorrect password.")
    }

    @Test func cancellationIsRecognized() {
        #expect(ErrorMessage.isCancellation(CancellationError()))
        #expect(ErrorMessage.isCancellation(URLError(.cancelled)))
        #expect(!ErrorMessage.isCancellation(URLError(.timedOut)))
    }
}
