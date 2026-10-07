import Foundation
import Observation

@Observable
@MainActor
final class ChangePasswordViewModel {
    var currentPassword = ""
    var newPassword = ""
    var confirmation = ""
    var errorMessage: String?
    var didSucceed = false
    private(set) var isSubmitting = false
    private(set) var hasAttemptedSubmit = false

    var currentPasswordError: String? {
        hasAttemptedSubmit ? Validation.requiredPassword(currentPassword) : nil
    }

    var newPasswordError: String? {
        guard hasAttemptedSubmit else { return nil }
        if let error = Validation.newPassword(newPassword) { return error }
        return newPassword == currentPassword ? "The new password must differ from the current one." : nil
    }

    var confirmationError: String? {
        guard hasAttemptedSubmit || !confirmation.isEmpty else { return nil }
        return Validation.passwordConfirmation(confirmation, matching: newPassword)
    }

    func submit(using session: SessionStore) async {
        hasAttemptedSubmit = true
        errorMessage = nil
        guard currentPasswordError == nil, newPasswordError == nil, confirmationError == nil, !isSubmitting else { return }
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            try await session.changePassword(currentPassword: currentPassword, newPassword: newPassword)
            didSucceed = true
        } catch {
            if !ErrorMessage.isCancellation(error) {
                errorMessage = ErrorMessage.message(for: error)
            }
        }
    }
}

@Observable
@MainActor
final class DeleteAccountViewModel {
    var password = ""
    var errorMessage: String?
    var isConfirming = false
    private(set) var isDeleting = false
    private(set) var hasAttemptedSubmit = false

    var passwordError: String? {
        hasAttemptedSubmit ? Validation.requiredPassword(password) : nil
    }

    func requestDeletion() {
        hasAttemptedSubmit = true
        errorMessage = nil
        if passwordError == nil {
            isConfirming = true
        }
    }

    func delete(using session: SessionStore) async {
        guard !isDeleting else { return }
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await session.deleteAccount(password: password)
        } catch let error as APIError where error.code == .invalidCurrentPassword {
            errorMessage = "Incorrect password."
        } catch {
            if !ErrorMessage.isCancellation(error) {
                errorMessage = ErrorMessage.message(for: error)
            }
        }
    }
}
