import Foundation
import Observation

@Observable
@MainActor
final class LoginViewModel {
    var email = ""
    var password = ""
    var errorMessage: String?
    private(set) var isSubmitting = false
    private(set) var hasAttemptedSubmit = false

    var emailError: String? { hasAttemptedSubmit ? Validation.email(email) : nil }
    var passwordError: String? { hasAttemptedSubmit ? Validation.requiredPassword(password) : nil }

    func submit(using session: SessionStore) async {
        hasAttemptedSubmit = true
        errorMessage = nil
        guard Validation.email(email) == nil, Validation.requiredPassword(password) == nil, !isSubmitting else { return }
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            try await session.signIn(email: email, password: password)
        } catch {
            if !ErrorMessage.isCancellation(error) {
                errorMessage = ErrorMessage.message(for: error)
            }
        }
    }
}

@Observable
@MainActor
final class RegisterViewModel {
    var email = ""
    var displayName = ""
    var password = ""
    var passwordConfirmation = ""
    var errorMessage: String?
    private(set) var isSubmitting = false
    private(set) var hasAttemptedSubmit = false

    var emailError: String? { hasAttemptedSubmit ? Validation.email(email) : nil }

    var displayNameError: String? { Validation.maxLength(displayName, 100) }

    var passwordError: String? {
        hasAttemptedSubmit || password.count > Validation.passwordLength.upperBound ? Validation.newPassword(password) : nil
    }

    var confirmationError: String? {
        guard hasAttemptedSubmit || !passwordConfirmation.isEmpty else { return nil }
        return Validation.passwordConfirmation(passwordConfirmation, matching: password)
    }

    private var isValid: Bool {
        Validation.email(email) == nil
            && displayNameError == nil
            && Validation.newPassword(password) == nil
            && Validation.passwordConfirmation(passwordConfirmation, matching: password) == nil
    }

    func submit(using session: SessionStore) async {
        hasAttemptedSubmit = true
        errorMessage = nil
        guard isValid, !isSubmitting else { return }
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            try await session.register(email: email, password: password, displayName: displayName)
        } catch {
            if !ErrorMessage.isCancellation(error) {
                errorMessage = ErrorMessage.message(for: error)
            }
        }
    }
}
